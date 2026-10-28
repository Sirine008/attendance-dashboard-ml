package tn.attijari.assiduite.attendance;

import tn.attijari.assiduite.domain.AttendanceStatus;
import tn.attijari.assiduite.domain.DailyAttendance;
import tn.attijari.assiduite.domain.Direction;
import tn.attijari.assiduite.domain.Leave;
import tn.attijari.assiduite.domain.SpecialPeriod;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Calcul pur (sans accès base) des journées d'assiduité d'un collaborateur à partir de ses pointages bruts.
 * <p>
 * Règles (cf. SPEC §3) :
 * <ul>
 *   <li>doublon : même sens + même porte qu'un pointage retenu il y a moins de {@code duplicateWindowSeconds} ;</li>
 *   <li>présence : au moins un pointage ce jour-là (une entrée sur une porte frontière est attendue,
 *       sinon la journée est marquée incomplète) ;</li>
 *   <li>retard : 1re entrée frontière &gt; horaire de référence + tolérance ;</li>
 *   <li>heures : somme des paires IN→OUT successives sur portes frontière ; séquence impaire ou incohérente
 *       → {@code incomplete}, seules les paires valides sont comptées ;</li>
 *   <li>absence : jour ouvré sans pointage ni congé déclaré ; séries ≥ {@code longAbsenceMinDays} jours
 *       ouvrés consécutifs → ABSENCE_LONGUE, sinon ABSENCE_PONCTUELLE.</li>
 * </ul>
 */
public final class AttendanceCalculator {

    private AttendanceCalculator() {
    }

    public record PunchInput(Long id, LocalDateTime time, Direction direction, String terminalCode, boolean boundary) {
    }

    public record Rules(int duplicateWindowSeconds, int longAbsenceMinDays) {
        public static Rules defaults() {
            return new Rules(120, 3);
        }
    }

    public record Input(Long employeeId,
                        LocalDate from,
                        LocalDate to,
                        List<PunchInput> punches,
                        Set<LocalDate> holidays,
                        List<SpecialPeriod> specialPeriods,
                        List<Leave> leaves,
                        LocalTime referenceStart,
                        int toleranceMinutes,
                        Rules rules) {
    }

    public record Result(List<DailyAttendance> days, Set<Long> duplicatePunchIds) {
    }

    public static boolean isWorkingDay(LocalDate d, Set<LocalDate> holidays) {
        return d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY && !holidays.contains(d);
    }

    /** Marque les doublons proches ; renvoie les pointages retenus (triés) et alimente {@code duplicates}. */
    static List<PunchInput> dedupe(List<PunchInput> punches, int windowSeconds, List<PunchInput> duplicates) {
        List<PunchInput> sorted = new ArrayList<>(punches);
        sorted.sort(Comparator.comparing(PunchInput::time));
        List<PunchInput> kept = new ArrayList<>();
        PunchInput previous = null;
        for (PunchInput p : sorted) {
            if (previous != null
                    && previous.direction() == p.direction()
                    && previous.terminalCode().equals(p.terminalCode())
                    && Duration.between(previous.time(), p.time()).getSeconds() <= windowSeconds) {
                duplicates.add(p);
                continue;
            }
            kept.add(p);
            previous = p;
        }
        return kept;
    }

    /**
     * Horaire de référence par défaut : médiane de la 1re entrée frontière sur les {@code learningDays}
     * premiers jours (calendaires) de suivi, arrondie à {@code roundingMinutes}.
     */
    public static Optional<LocalTime> deduceReferenceStart(List<PunchInput> punches, LocalDate trackingStart,
                                                           int learningDays, int roundingMinutes) {
        Map<LocalDate, LocalTime> firstIns = new TreeMap<>();
        for (PunchInput p : punches) {
            if (p.boundary() && p.direction() == Direction.IN && !p.time().toLocalDate().isBefore(trackingStart)) {
                firstIns.merge(p.time().toLocalDate(), p.time().toLocalTime(), (a, b) -> a.isBefore(b) ? a : b);
            }
        }
        if (firstIns.isEmpty()) {
            return Optional.empty();
        }
        LocalDate windowStart = firstIns.keySet().iterator().next();
        LocalDate windowEnd = windowStart.plusDays(learningDays);
        List<Integer> seconds = firstIns.entrySet().stream()
                .filter(e -> e.getKey().isBefore(windowEnd))
                .map(e -> e.getValue().toSecondOfDay())
                .sorted()
                .toList();
        int n = seconds.size();
        double median = n % 2 == 1 ? seconds.get(n / 2) : (seconds.get(n / 2 - 1) + seconds.get(n / 2)) / 2.0;
        long step = roundingMinutes * 60L;
        long rounded = Math.round(median / step) * step;
        return Optional.of(LocalTime.ofSecondOfDay(Math.min(rounded, 23 * 3600 + 59 * 60)));
    }

    public static Result compute(Input in) {
        List<PunchInput> duplicates = new ArrayList<>();
        List<PunchInput> kept = dedupe(in.punches(), in.rules().duplicateWindowSeconds(), duplicates);
        Map<LocalDate, List<PunchInput>> byDay = kept.stream()
                .collect(Collectors.groupingBy(p -> p.time().toLocalDate(), TreeMap::new, Collectors.toList()));
        Map<LocalDate, Long> dupByDay = duplicates.stream()
                .collect(Collectors.groupingBy(p -> p.time().toLocalDate(), Collectors.counting()));

        List<DailyAttendance> days = new ArrayList<>();
        for (LocalDate d = in.from(); !d.isAfter(in.to()); d = d.plusDays(1)) {
            boolean working = isWorkingDay(d, in.holidays());
            List<PunchInput> dayPunches = byDay.getOrDefault(d, List.of());
            final LocalDate day = d;
            SpecialPeriod period = in.specialPeriods().stream().filter(sp -> sp.contains(day)).findFirst().orElse(null);
            LocalTime reference = period != null && period.getRefStartTime() != null ? period.getRefStartTime() : in.referenceStart();

            if (dayPunches.isEmpty()) {
                if (!working) {
                    continue;
                }
                DailyAttendance a = base(in.employeeId(), d, true, period, reference);
                boolean onLeave = in.leaves().stream().anyMatch(l -> l.covers(day));
                // statut d'absence provisoire, affiné après détection des séries
                a.setStatus(onLeave ? AttendanceStatus.CONGE : AttendanceStatus.ABSENCE_PONCTUELLE);
                days.add(a);
                continue;
            }
            DailyAttendance a = base(in.employeeId(), d, working, period, reference);
            fillPresentDay(a, dayPunches, reference, in.toleranceMinutes(), working);
            a.setDuplicateCount(dupByDay.getOrDefault(d, 0L).intValue());
            days.add(a);
        }
        classifyAbsenceRuns(days, in.rules().longAbsenceMinDays());
        Set<Long> dupIds = duplicates.stream().map(PunchInput::id).filter(Objects::nonNull).collect(Collectors.toSet());
        return new Result(days, dupIds);
    }

    private static DailyAttendance base(Long employeeId, LocalDate d, boolean working, SpecialPeriod period, LocalTime reference) {
        DailyAttendance a = new DailyAttendance();
        a.setEmployeeId(employeeId);
        a.setWorkDate(d);
        a.setWorkingDay(working);
        a.setReferenceStart(reference);
        a.setSpecialPeriodKind(period != null ? period.getKind().name() : null);
        return a;
    }

    private static void fillPresentDay(DailyAttendance a, List<PunchInput> punches, LocalTime reference,
                                       int toleranceMinutes, boolean working) {
        List<PunchInput> boundary = punches.stream().filter(PunchInput::boundary).toList();
        a.setPunchCount(punches.size());
        a.setBoundaryPunchCount(boundary.size());

        LocalTime firstIn = boundary.stream().filter(p -> p.direction() == Direction.IN)
                .map(p -> p.time().toLocalTime()).min(Comparator.naturalOrder()).orElse(null);
        LocalTime lastOut = boundary.stream().filter(p -> p.direction() == Direction.OUT)
                .map(p -> p.time().toLocalTime()).max(Comparator.naturalOrder()).orElse(null);
        a.setFirstIn(firstIn);
        a.setLastOut(lastOut);

        int worked = 0;
        int pairs = 0;
        boolean irregular = boundary.size() % 2 == 1 || firstIn == null;
        int i = 0;
        while (i < boundary.size()) {
            PunchInput cur = boundary.get(i);
            if (cur.direction() == Direction.IN && i + 1 < boundary.size() && boundary.get(i + 1).direction() == Direction.OUT) {
                worked += (int) Duration.between(cur.time(), boundary.get(i + 1).time()).toMinutes();
                pairs++;
                i += 2;
            } else {
                irregular = true;
                i++;
            }
        }
        a.setIncomplete(irregular);
        a.setWorkedMinutes(pairs > 0 ? worked : null);

        boolean late = working && firstIn != null && reference != null
                && firstIn.isAfter(reference.plusMinutes(toleranceMinutes));
        a.setLateMinutes(late ? (int) Duration.between(reference, firstIn).toMinutes() : 0);
        a.setStatus(late ? AttendanceStatus.RETARD : AttendanceStatus.PRESENT);
    }

    /** Les jours sans pointage d'une même série (jours ouvrés consécutifs) deviennent ABSENCE_LONGUE si la série est longue. */
    private static void classifyAbsenceRuns(List<DailyAttendance> days, int longMinDays) {
        List<DailyAttendance> run = new ArrayList<>();
        // Les week-ends / fériés n'ont pas de ligne (sauf s'ils sont travaillés) : ils ne cassent donc pas une série,
        // alors qu'un samedi travaillé ou un congé déclaré la cassent.
        for (DailyAttendance a : days) {
            if (a.getStatus() == AttendanceStatus.ABSENCE_PONCTUELLE) {
                run.add(a);
            } else {
                closeRun(run, longMinDays);
            }
        }
        closeRun(run, longMinDays);
    }

    private static void closeRun(List<DailyAttendance> run, int longMinDays) {
        if (run.size() >= longMinDays) {
            run.forEach(a -> a.setStatus(AttendanceStatus.ABSENCE_LONGUE));
        }
        run.clear();
    }
}
