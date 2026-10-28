package tn.attijari.assiduite.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.attijari.assiduite.domain.AttendanceStatus;
import tn.attijari.assiduite.domain.DailyAttendance;
import tn.attijari.assiduite.domain.Employee;
import tn.attijari.assiduite.repository.DailyAttendanceRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Agrégations descriptives sur {@code daily_attendance} (le périmètre est fourni par l'appelant, déjà filtré). */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {

    private final DailyAttendanceRepository daily;

    public AnalyticsService(DailyAttendanceRepository daily) {
        this.daily = daily;
    }

    public record KpiResponse(LocalDate from, LocalDate to, LocalDate previousFrom, LocalDate previousTo, Kpis current, Kpis previous) {
    }

    public record TrendPoint(LocalDate periodStart, String label, Double presenceRate, int absencesPonctuelles, int absencesLongues,
                             int lateDays, Double avgLateMinutes, Double avgWorkedHours) {
    }

    public record HeatCell(LocalDate weekStart, int isoWeek, int weekday, int expectedDays, int absences, int lateDays, Double absenceRate) {
    }

    public record Bin(String label, int count) {
    }

    public record ArrivalDistribution(List<Bin> arrivalTimes, List<Bin> deltaToReference, int days) {
    }

    public record EmployeeIndicators(Kpis kpis, List<Double> weeklyPresence) {
    }

    public List<DailyAttendance> rows(Collection<Long> employeeIds, LocalDate from, LocalDate to) {
        if (employeeIds.isEmpty()) {
            return List.of();
        }
        return daily.findByEmployeeIdInAndWorkDateBetween(employeeIds, from, to);
    }

    public KpiResponse kpis(List<Long> employeeIds, LocalDate from, LocalDate to) {
        long len = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(len - 1);
        return new KpiResponse(from, to, prevFrom, prevTo,
                Kpis.of(rows(employeeIds, from, to), employeeIds.size()),
                Kpis.of(rows(employeeIds, prevFrom, prevTo), employeeIds.size()));
    }

    public List<TrendPoint> trend(List<Long> employeeIds, LocalDate from, LocalDate to, String granularity) {
        String g = granularity != null ? granularity : autoGranularity(from, to);
        Function<LocalDate, LocalDate> bucket = switch (g) {
            case "day" -> d -> d;
            case "week" -> d -> d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            default -> d -> d.withDayOfMonth(1);
        };
        Map<LocalDate, List<DailyAttendance>> groups = rows(employeeIds, from, to).stream()
                .filter(DailyAttendance::isWorkingDay)
                .collect(Collectors.groupingBy(d -> bucket.apply(d.getWorkDate()), TreeMap::new, Collectors.toList()));
        List<TrendPoint> points = new ArrayList<>();
        groups.forEach((start, list) -> {
            Kpis k = Kpis.of(list, employeeIds.size());
            points.add(new TrendPoint(start, label(start, g), k.presenceRate(), k.absencesPonctuelles(), k.absencesLongues(),
                    k.lateDays(), k.avgLateMinutes(), k.avgWorkedHours()));
        });
        return points;
    }

    static String autoGranularity(LocalDate from, LocalDate to) {
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return days <= 45 ? "day" : days <= 200 ? "week" : "month";
    }

    private static String label(LocalDate d, String g) {
        return switch (g) {
            case "day" -> d.toString();
            case "week" -> "S" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) + " " + d.get(IsoFields.WEEK_BASED_YEAR);
            default -> String.format("%02d/%d", d.getMonthValue(), d.getYear());
        };
    }

    public List<HeatCell> heatmap(List<Long> employeeIds, LocalDate from, LocalDate to) {
        Map<LocalDate, Map<Integer, List<DailyAttendance>>> grid = rows(employeeIds, from, to).stream()
                .filter(DailyAttendance::isWorkingDay)
                .collect(Collectors.groupingBy(d -> d.getWorkDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), TreeMap::new,
                        Collectors.groupingBy(d -> d.getWorkDate().getDayOfWeek().getValue())));
        List<HeatCell> cells = new ArrayList<>();
        grid.forEach((week, byDay) -> byDay.forEach((dow, list) -> {
            Kpis k = Kpis.of(list, employeeIds.size());
            cells.add(new HeatCell(week, week.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR), dow, k.expectedDays(),
                    k.absencesPonctuelles(), k.lateDays(), k.expectedDays() == 0 ? null : Kpis.ratio(k.absencesPonctuelles(), k.expectedDays())));
        }));
        return cells;
    }

    public ArrivalDistribution arrivals(List<Long> employeeIds, LocalDate from, LocalDate to) {
        List<DailyAttendance> present = rows(employeeIds, from, to).stream()
                .filter(d -> d.isWorkingDay() && d.isPresent() && d.getFirstIn() != null)
                .toList();
        TreeMap<Integer, Integer> times = new TreeMap<>();
        TreeMap<Integer, Integer> deltas = new TreeMap<>();
        for (DailyAttendance d : present) {
            int minute = d.getFirstIn().toSecondOfDay() / 60;
            times.merge(Math.max(6 * 60, Math.min(11 * 60, minute / 15 * 15)), 1, Integer::sum);
            if (d.getReferenceStart() != null) {
                int delta = minute - d.getReferenceStart().toSecondOfDay() / 60;
                int b = Math.max(-60, Math.min(120, Math.floorDiv(delta, 10) * 10));
                deltas.merge(b, 1, Integer::sum);
            }
        }
        List<Bin> t = new ArrayList<>();
        for (int m = 6 * 60; m <= 11 * 60; m += 15) {
            String lbl = LocalTime.of(m / 60, m % 60).toString();
            if (m == 6 * 60) lbl = "≤ " + lbl;
            if (m == 11 * 60) lbl = "≥ " + lbl;
            t.add(new Bin(lbl, times.getOrDefault(m, 0)));
        }
        List<Bin> dl = new ArrayList<>();
        for (int b = -60; b <= 120; b += 10) {
            String lbl = b == -60 ? "≤ -60" : b == 120 ? "≥ +120" : (b >= 0 ? "+" : "") + b;
            dl.add(new Bin(lbl, deltas.getOrDefault(b, 0)));
        }
        return new ArrivalDistribution(t, dl, present.size());
    }

    /** Indicateurs par collaborateur + présence hebdomadaire (sparkline, 26 dernières semaines de la période au plus). */
    public Map<Long, EmployeeIndicators> perEmployee(List<Employee> employees, LocalDate from, LocalDate to) {
        Map<Long, List<DailyAttendance>> byEmp = rows(employees.stream().map(Employee::getId).toList(), from, to).stream()
                .collect(Collectors.groupingBy(DailyAttendance::getEmployeeId));
        LocalDate sparkFrom = to.minusWeeks(26).isAfter(from) ? to.minusWeeks(26) : from;
        Map<Long, EmployeeIndicators> result = new LinkedHashMap<>();
        for (Employee e : employees) {
            List<DailyAttendance> list = byEmp.getOrDefault(e.getId(), List.of());
            Map<LocalDate, List<DailyAttendance>> weeks = list.stream()
                    .filter(d -> !d.getWorkDate().isBefore(sparkFrom))
                    .collect(Collectors.groupingBy(d -> d.getWorkDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                            TreeMap::new, Collectors.toList()));
            List<Double> spark = weeks.values().stream().map(w -> Kpis.of(w, 1).presenceRate()).toList();
            result.put(e.getId(), new EmployeeIndicators(Kpis.of(list, 1), spark));
        }
        return result;
    }

    public static boolean isAbsence(AttendanceStatus s) {
        return s == AttendanceStatus.ABSENCE_PONCTUELLE || s == AttendanceStatus.ABSENCE_LONGUE;
    }
}
