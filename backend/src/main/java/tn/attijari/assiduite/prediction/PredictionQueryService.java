package tn.attijari.assiduite.prediction;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.attijari.assiduite.domain.*;
import tn.attijari.assiduite.repository.DailyAttendanceRepository;
import tn.attijari.assiduite.repository.PredictionRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PredictionQueryService {

    private final PredictionRepository predictions;
    private final DailyAttendanceRepository daily;

    public PredictionQueryService(PredictionRepository predictions, DailyAttendanceRepository daily) {
        this.predictions = predictions;
        this.daily = daily;
    }

    public record PredictionView(Long employeeId, String horizon, String status, LocalDate referenceDate, LocalDate targetDate,
                                 LocalDate targetEnd, Double probability, String riskLevel, Double expectedDays,
                                 List<Map<String, Object>> factors, String modelVersion, java.time.Instant createdAt) {
        static PredictionView of(Prediction p) {
            return new PredictionView(p.getEmployeeId(), p.getHorizon().name(), p.getStatus().name(), p.getReferenceDate(),
                    p.getTargetDate(), p.getTargetEnd(), toDouble(p.getProbability()),
                    p.getRiskLevel() != null ? p.getRiskLevel().name() : null, toDouble(p.getExpectedDays()),
                    p.getTopFactors() != null ? p.getTopFactors() : List.of(), p.getModelVersion(), p.getCreatedAt());
        }
    }

    /** Une prédiction passée confrontée à ce qui s'est réellement produit (null si le jour n'est pas encore connu). */
    public record HistoryItem(PredictionView prediction, Boolean actualAbsence, String actualDetail) {
    }

    private static Double toDouble(BigDecimal b) {
        return b == null ? null : b.doubleValue();
    }

    public Map<Long, PredictionView> latest(Collection<Long> employeeIds, Horizon horizon) {
        if (employeeIds.isEmpty()) {
            return Map.of();
        }
        return predictions.findLatest(horizon.name(), employeeIds).stream()
                .map(PredictionView::of)
                .collect(Collectors.toMap(PredictionView::employeeId, Function.identity()));
    }

    public List<HistoryItem> history(Long employeeId, Horizon horizon, int limit) {
        List<Prediction> list = predictions.findHistory(employeeId, horizon.name(), limit);
        if (list.isEmpty()) {
            return List.of();
        }
        LocalDate min = list.stream().map(Prediction::getTargetDate).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate max = list.stream().map(p -> p.getTargetEnd() != null ? p.getTargetEnd() : p.getTargetDate()).max(Comparator.naturalOrder()).orElseThrow();
        Map<LocalDate, DailyAttendance> days = daily.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(employeeId, min, max).stream()
                .collect(Collectors.toMap(DailyAttendance::getWorkDate, Function.identity()));
        LocalDate lastKnown = daily.findMaxDate();
        List<HistoryItem> items = new ArrayList<>();
        for (Prediction p : list) {
            LocalDate end = p.getTargetEnd() != null ? p.getTargetEnd() : p.getTargetDate();
            if (lastKnown == null || end.isAfter(lastKnown)) {
                items.add(new HistoryItem(PredictionView.of(p), null, "en attente des données"));
                continue;
            }
            List<DailyAttendance> window = days.values().stream()
                    .filter(d -> !d.getWorkDate().isBefore(p.getTargetDate()) && !d.getWorkDate().isAfter(end) && d.isWorkingDay())
                    .sorted(Comparator.comparing(DailyAttendance::getWorkDate))
                    .toList();
            long ponctuelles = window.stream().filter(d -> d.getStatus() == AttendanceStatus.ABSENCE_PONCTUELLE).count();
            boolean excluded = window.stream().allMatch(d -> d.getStatus() == AttendanceStatus.CONGE || d.getStatus() == AttendanceStatus.ABSENCE_LONGUE);
            String detail = window.isEmpty() ? "jour non suivi"
                    : excluded ? "congé / absence longue (hors périmètre)"
                    : ponctuelles == 0 ? "présent" : ponctuelles + " jour(s) d'absence ponctuelle";
            items.add(new HistoryItem(PredictionView.of(p), window.isEmpty() || excluded ? null : ponctuelles > 0, detail));
        }
        return items;
    }

    public java.time.Instant lastRunAt() {
        return predictions.findLastCreatedAt();
    }
}
