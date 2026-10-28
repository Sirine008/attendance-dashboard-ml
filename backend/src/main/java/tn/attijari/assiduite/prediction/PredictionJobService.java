package tn.attijari.assiduite.prediction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.attijari.assiduite.attendance.AttendanceCalculator;
import tn.attijari.assiduite.config.AppProperties;
import tn.attijari.assiduite.domain.*;
import tn.attijari.assiduite.repository.DailyAttendanceRepository;
import tn.attijari.assiduite.repository.EmployeeRepository;
import tn.attijari.assiduite.repository.PredictionRepository;
import tn.attijari.assiduite.repository.PublicHolidayRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Génère et historise les prédictions J+1 / semaine de tous les collaborateurs (job planifié + déclenchement manuel).
 * Si ml-service est indisponible : nouvelles tentatives, journalisation, et l'application continue de fonctionner
 * (le tableau de bord descriptif ne dépend pas des prédictions).
 */
@Service
public class PredictionJobService {

    private static final Logger log = LoggerFactory.getLogger(PredictionJobService.class);

    private final MlClient ml;
    private final EmployeeRepository employees;
    private final PredictionRepository predictions;
    private final DailyAttendanceRepository daily;
    private final PublicHolidayRepository holidays;
    private final AppProperties props;
    private final AtomicReference<RunStatus> lastRun = new AtomicReference<>();

    public PredictionJobService(MlClient ml, EmployeeRepository employees, PredictionRepository predictions,
                                DailyAttendanceRepository daily, PublicHolidayRepository holidays, AppProperties props) {
        this.ml = ml;
        this.employees = employees;
        this.predictions = predictions;
        this.daily = daily;
        this.holidays = holidays;
        this.props = props;
    }

    public record RunStatus(Instant at, boolean success, String message, int stored, String modelVersion, LocalDate referenceDate) {
    }

    public Optional<RunStatus> lastRun() {
        return Optional.ofNullable(lastRun.get());
    }

    @Scheduled(cron = "${app.prediction.cron:0 0 6 * * MON-FRI}", zone = "Africa/Tunis")
    public void scheduledRun() {
        log.info("Job planifié : génération des prédictions");
        runSafely(null, "planifié");
    }

    /** Ne lève jamais d'exception : le statut est consultable via {@link #lastRun()}. */
    public RunStatus runSafely(LocalDate reference, String trigger) {
        try {
            return run(reference);
        } catch (RuntimeException e) {
            log.error("Prédictions ({}) indisponibles : {}", trigger, e.getMessage());
            RunStatus s = new RunStatus(Instant.now(), false, "ml-service indisponible : " + e.getMessage(), 0, null, reference);
            lastRun.set(s);
            return s;
        }
    }

    /**
     * Date de référence par défaut : la veille (données complètes), bornée par la dernière journée importée
     * — avec des données historiques (fichier arrêté en mai 2025), c'est la dernière date du fichier.
     */
    public LocalDate defaultReferenceDate() {
        LocalDate max = daily.findMaxDate();
        LocalDate yesterday = LocalDate.now().minusDays(1);
        return max == null || yesterday.isBefore(max) ? yesterday : max;
    }

    @Transactional
    public RunStatus run(LocalDate reference) {
        LocalDate ref = reference != null ? reference : defaultReferenceDate();
        List<Long> ids = employees.findAll().stream().filter(Employee::isActive).map(Employee::getId).toList();
        if (ids.isEmpty()) {
            RunStatus s = new RunStatus(Instant.now(), true, "Aucun collaborateur", 0, null, ref);
            lastRun.set(s);
            return s;
        }
        MlClient.PredictResponse resp = ml.predict(ref, ids);
        Instant now = Instant.now();
        List<Prediction> rows = resp.predictions().stream().map(p -> toEntity(p, resp.modelVersion(), ref, now)).toList();
        predictions.saveAll(rows); // historisation : toujours de nouvelles lignes, jamais de mise à jour
        RunStatus s = new RunStatus(now, true, rows.size() + " prédictions enregistrées", rows.size(), resp.modelVersion(), ref);
        lastRun.set(s);
        log.info("Prédictions au {} : {} lignes (modèle {})", ref, rows.size(), resp.modelVersion());
        return s;
    }

    /**
     * Rejoue le job sur les {@code workingDays} derniers jours ouvrés (dates de référence passées) pour disposer
     * d'un historique « prédit vs réel » dès la mise en service.
     */
    public int backfill(int workingDays) {
        LocalDate end = daily.findMaxDate();
        if (end == null) {
            return 0;
        }
        Set<LocalDate> hol = holidays.findAll().stream().map(PublicHoliday::getHolidayDate).collect(Collectors.toSet());
        Deque<LocalDate> refs = new ArrayDeque<>();
        for (LocalDate d = end.minusDays(1); refs.size() < workingDays; d = d.minusDays(1)) {
            if (AttendanceCalculator.isWorkingDay(d, hol)) {
                refs.addFirst(d);
            }
        }
        int stored = 0;
        for (LocalDate ref : refs) {
            stored += run(ref).stored();
        }
        log.info("Historique de prédictions reconstitué sur {} dates de référence ({} lignes)", refs.size(), stored);
        return stored;
    }

    private static Prediction toEntity(MlClient.PredictionDto p, String version, LocalDate ref, Instant now) {
        Prediction e = new Prediction();
        e.setEmployeeId(p.employeeId());
        e.setReferenceDate(p.referenceDate() != null ? p.referenceDate() : ref);
        e.setTargetDate(p.targetDate() != null ? p.targetDate() : ref.plusDays(1));
        e.setTargetEnd(p.targetEnd());
        e.setHorizon(Horizon.valueOf(p.horizon()));
        e.setStatus(PredictionStatus.valueOf(p.status()));
        e.setProbability(p.probability() != null ? BigDecimal.valueOf(p.probability()).setScale(5, RoundingMode.HALF_UP) : null);
        e.setRiskLevel(p.riskLevel() != null ? RiskLevel.valueOf(p.riskLevel()) : null);
        e.setExpectedDays(p.expectedDays() != null ? BigDecimal.valueOf(p.expectedDays()).setScale(2, RoundingMode.HALF_UP) : null);
        e.setTopFactors(p.factors() != null ? p.factors() : List.of());
        e.setModelVersion(version);
        e.setCreatedAt(now);
        return e;
    }

    public long count() {
        return predictions.count();
    }

    public AppProperties.Prediction config() {
        return props.prediction();
    }
}
