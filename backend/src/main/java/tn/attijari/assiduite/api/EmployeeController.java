package tn.attijari.assiduite.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tn.attijari.assiduite.analytics.AnalyticsService;
import tn.attijari.assiduite.analytics.Kpis;
import tn.attijari.assiduite.audit.AuditService;
import tn.attijari.assiduite.domain.*;
import tn.attijari.assiduite.prediction.PredictionQueryService;
import tn.attijari.assiduite.repository.*;
import tn.attijari.assiduite.security.AccessScopeService;
import tn.attijari.assiduite.security.CurrentUser;

import java.time.LocalDate;
import java.time.Period;
import java.time.YearMonth;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/employees")
@Tag(name = "Collaborateurs", description = "Liste et fiche individuelle (chaque consultation de fiche est tracée)")
public class EmployeeController {

    private final AccessScopeService scope;
    private final AnalyticsService analytics;
    private final PredictionQueryService predictions;
    private final PeriodParams periods;
    private final AuditService audit;
    private final DailyAttendanceRepository daily;
    private final RawPunchRepository punches;
    private final TerminalRepository terminals;
    private final PublicHolidayRepository holidays;
    private final LeaveRepository leaves;
    private final WorkScheduleRepository schedules;

    public EmployeeController(AccessScopeService scope, AnalyticsService analytics, PredictionQueryService predictions,
                              PeriodParams periods, AuditService audit, DailyAttendanceRepository daily, RawPunchRepository punches,
                              TerminalRepository terminals, PublicHolidayRepository holidays, LeaveRepository leaves,
                              WorkScheduleRepository schedules) {
        this.scope = scope;
        this.analytics = analytics;
        this.predictions = predictions;
        this.periods = periods;
        this.audit = audit;
        this.daily = daily;
        this.punches = punches;
        this.terminals = terminals;
        this.holidays = holidays;
        this.leaves = leaves;
        this.schedules = schedules;
    }

    public record RiskBadge(String status, String riskLevel, Double probability, LocalDate targetDate) {
        static RiskBadge of(PredictionQueryService.PredictionView p) {
            return p == null ? null : new RiskBadge(p.status(), p.riskLevel(), p.probability(), p.targetDate());
        }
    }

    public record EmployeeRow(Long id, String code, String displayName, Long teamId, String teamName, boolean synthetic,
                              Kpis kpis, List<Double> weeklyPresence, RiskBadge riskD1, RiskBadge riskW1) {
    }

    public record ScheduleView(String startTime, String endTime, int toleranceMinutes, String source) {
    }

    public record EmployeeProfile(Long id, String code, String displayName, Long teamId, String teamName, boolean synthetic,
                                  LocalDate hireDate, boolean hireDateFictive, Double seniorityYears, ScheduleView schedule,
                                  LocalDate from, LocalDate to, Kpis kpis, Kpis previousKpis, int trackedWorkingDays) {
    }

    public record CalendarDay(LocalDate date, String kind, String status, boolean incomplete, String firstIn, String lastOut,
                              int lateMinutes, Integer workedMinutes, String label) {
    }

    public record PunchView(String time, String direction, String terminal, boolean boundary, boolean duplicate, String terminalRaw) {
    }

    public record DayDetail(LocalDate date, CalendarDay summary, List<PunchView> punches) {
    }

    public record ArrivalPoint(LocalDate date, Integer firstInMinutes, Integer referenceMinutes, boolean late) {
    }

    public record EmployeePredictions(PredictionQueryService.PredictionView latestD1, PredictionQueryService.PredictionView latestW1,
                                      List<PredictionQueryService.HistoryItem> historyD1, List<PredictionQueryService.HistoryItem> historyW1) {
    }

    @GetMapping
    @Operation(summary = "Collaborateurs visibles avec indicateurs de la période et dernier niveau de risque")
    @Transactional(readOnly = true)
    public List<EmployeeRow> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) Long teamId,
                                  @RequestParam(defaultValue = "false") boolean synthetic) {
        PeriodParams.Period p = periods.resolve(from, to);
        List<Employee> emps = scope.visibleEmployees(CurrentUser.get(), teamId, synthetic);
        Map<Long, AnalyticsService.EmployeeIndicators> ind = analytics.perEmployee(emps, p.from(), p.to());
        List<Long> ids = emps.stream().map(Employee::getId).toList();
        Map<Long, PredictionQueryService.PredictionView> d1 = predictions.latest(ids, Horizon.D1);
        Map<Long, PredictionQueryService.PredictionView> w1 = predictions.latest(ids, Horizon.W1);
        return emps.stream().map(e -> new EmployeeRow(e.getId(), e.getCode(), e.getDisplayName(),
                e.getTeam() != null ? e.getTeam().getId() : null, e.getTeam() != null ? e.getTeam().getName() : null,
                e.isSynthetic(), ind.get(e.getId()).kpis(), ind.get(e.getId()).weeklyPresence(),
                RiskBadge.of(d1.get(e.getId())), RiskBadge.of(w1.get(e.getId())))).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Fiche collaborateur (consultation tracée dans audit_log)")
    @Transactional(readOnly = true)
    public EmployeeProfile profile(@PathVariable Long id,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        CurrentUser user = CurrentUser.get();
        Employee e = scope.requireVisible(user, id);
        audit.log(user.username(), "VIEW_EMPLOYEE", "employee", e.getId(), "Consultation de la fiche " + e.getCode());
        PeriodParams.Period p = periods.resolve(from, to);
        AnalyticsService.KpiResponse k = analytics.kpis(List.of(id), p.from(), p.to());
        WorkSchedule ws = schedules.findByEmployeeId(id).orElse(null);
        LocalDate ref = periods.dataEnd();
        Double seniority = e.getHireDate() == null ? null
                : Math.round(Period.between(e.getHireDate(), ref).toTotalMonths() / 12.0 * 10) / 10.0;
        int tracked = (int) daily.findByEmployeeIdOrderByWorkDateAsc(id).stream().filter(DailyAttendance::isWorkingDay).count();
        return new EmployeeProfile(e.getId(), e.getCode(), e.getDisplayName(),
                e.getTeam() != null ? e.getTeam().getId() : null, e.getTeam() != null ? e.getTeam().getName() : null,
                e.isSynthetic(), e.getHireDate(), e.isHireDateFictive(), seniority,
                ws == null ? null : new ScheduleView(ws.getStartTime().toString(), ws.getEndTime() != null ? ws.getEndTime().toString() : null,
                        ws.getToleranceMinutes(), ws.getSource()),
                p.from(), p.to(), k.current(), k.previous(), tracked);
    }

    @GetMapping("/{id}/calendar")
    @Operation(summary = "Calendrier mensuel coloré (month = AAAA-MM)")
    @Transactional(readOnly = true)
    public List<CalendarDay> calendar(@PathVariable Long id, @RequestParam(required = false) String month) {
        scope.requireVisible(CurrentUser.get(), id);
        YearMonth ym = month != null ? YearMonth.parse(month) : YearMonth.from(periods.dataEnd());
        LocalDate first = ym.atDay(1), last = ym.atEndOfMonth();
        Map<LocalDate, DailyAttendance> rows = daily.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(id, first, last).stream()
                .collect(Collectors.toMap(DailyAttendance::getWorkDate, Function.identity()));
        Map<LocalDate, String> hol = holidays.findAll().stream()
                .collect(Collectors.toMap(PublicHoliday::getHolidayDate, PublicHoliday::getLabel));
        List<Leave> lv = leaves.findByEmployeeId(id);
        List<CalendarDay> days = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
            DailyAttendance a = rows.get(d);
            if (a != null) {
                days.add(toCalendarDay(a, hol.get(d)));
            } else {
                final LocalDate day = d;
                String kind = hol.containsKey(d) ? "FERIE"
                        : d.getDayOfWeek().getValue() >= 6 ? "WEEKEND"
                        : lv.stream().anyMatch(l -> l.covers(day)) ? "CONGE"
                        : "HORS_SUIVI";
                days.add(new CalendarDay(d, kind, null, false, null, null, 0, null, hol.get(d)));
            }
        }
        return days;
    }

    private static CalendarDay toCalendarDay(DailyAttendance a, String holidayLabel) {
        return new CalendarDay(a.getWorkDate(), a.isWorkingDay() ? "JOUR_OUVRE" : "NON_OUVRE_TRAVAILLE", a.getStatus().name(),
                a.isIncomplete(), a.getFirstIn() != null ? a.getFirstIn().toString() : null,
                a.getLastOut() != null ? a.getLastOut().toString() : null, a.getLateMinutes(), a.getWorkedMinutes(), holidayLabel);
    }

    @GetMapping("/{id}/days/{date}")
    @Operation(summary = "Timeline des pointages d'une journée (entrées/sorties par porte)")
    @Transactional(readOnly = true)
    public DayDetail day(@PathVariable Long id, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        scope.requireVisible(CurrentUser.get(), id);
        Map<Long, Terminal> term = terminals.findAll().stream().collect(Collectors.toMap(Terminal::getId, Function.identity()));
        List<PunchView> list = punches.findByEmployeeIdAndPunchTimeBetweenOrderByPunchTimeAsc(id, date.atStartOfDay(), date.plusDays(1).atStartOfDay().minusNanos(1))
                .stream().map(p -> {
                    Terminal t = term.get(p.getTerminalId());
                    return new PunchView(p.getPunchTime().toLocalTime().toString(), p.getDirection().name(), t.getCode(),
                            t.isBuildingBoundary(), p.isDuplicate(), p.getTerminalRaw());
                }).toList();
        CalendarDay summary = daily.findByEmployeeIdAndWorkDate(id, date).map(a -> toCalendarDay(a, null)).orElse(null);
        return new DayDetail(date, summary, list);
    }

    @GetMapping("/{id}/arrivals")
    @Operation(summary = "Heure d'arrivée vs horaire de référence (minutes depuis minuit)")
    @Transactional(readOnly = true)
    public List<ArrivalPoint> arrivals(@PathVariable Long id,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        scope.requireVisible(CurrentUser.get(), id);
        PeriodParams.Period p = periods.resolve(from, to);
        return daily.findByEmployeeIdAndWorkDateBetweenOrderByWorkDateAsc(id, p.from(), p.to()).stream()
                .filter(d -> d.isWorkingDay() && d.isPresent())
                .map(d -> new ArrivalPoint(d.getWorkDate(),
                        d.getFirstIn() != null ? d.getFirstIn().toSecondOfDay() / 60 : null,
                        d.getReferenceStart() != null ? d.getReferenceStart().toSecondOfDay() / 60 : null,
                        d.isLate()))
                .toList();
    }

    @GetMapping("/{id}/predictions")
    @Operation(summary = "Dernières estimations J+1 / semaine et historique prédit vs réel")
    @Transactional(readOnly = true)
    public EmployeePredictions predictions(@PathVariable Long id) {
        scope.requireVisible(CurrentUser.get(), id);
        return new EmployeePredictions(
                predictions.latest(List.of(id), Horizon.D1).get(id),
                predictions.latest(List.of(id), Horizon.W1).get(id),
                predictions.history(id, Horizon.D1, 40),
                predictions.history(id, Horizon.W1, 12));
    }
}
