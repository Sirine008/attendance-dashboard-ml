package tn.attijari.assiduite.attendance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.attijari.assiduite.domain.*;
import tn.attijari.assiduite.repository.*;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Recalcule {@code daily_attendance} (et les drapeaux de doublon de {@code raw_punch}) depuis les pointages bruts. */
@Service
public class AttendanceRecomputeService {

    private static final Logger log = LoggerFactory.getLogger(AttendanceRecomputeService.class);

    private final EmployeeRepository employees;
    private final RawPunchRepository punches;
    private final TerminalRepository terminals;
    private final PublicHolidayRepository holidays;
    private final SpecialPeriodRepository periods;
    private final LeaveRepository leaves;
    private final WorkScheduleRepository schedules;
    private final SettingsService settings;
    private final JdbcTemplate jdbc;

    public AttendanceRecomputeService(EmployeeRepository employees, RawPunchRepository punches, TerminalRepository terminals,
                                      PublicHolidayRepository holidays, SpecialPeriodRepository periods, LeaveRepository leaves,
                                      WorkScheduleRepository schedules, SettingsService settings, JdbcTemplate jdbc) {
        this.employees = employees;
        this.punches = punches;
        this.terminals = terminals;
        this.holidays = holidays;
        this.periods = periods;
        this.leaves = leaves;
        this.schedules = schedules;
        this.settings = settings;
        this.jdbc = jdbc;
    }

    public record Summary(int employees, int days, int incompleteDays, int duplicates, int weekendOrHolidayDays) {
    }

    @Transactional
    public Summary recomputeAll() {
        LocalDateTime min = punches.findMinPunchTime();
        LocalDateTime max = punches.findMaxPunchTime();
        if (min == null) {
            return new Summary(0, 0, 0, 0, 0);
        }
        Context ctx = context(min.toLocalDate(), max.toLocalDate());
        int days = 0, incomplete = 0, dups = 0, nonWorking = 0, count = 0;
        for (Employee e : employees.findAll()) {
            AttendanceCalculator.Result r = recompute(e, ctx);
            if (r == null) {
                continue;
            }
            count++;
            days += r.days().size();
            incomplete += (int) r.days().stream().filter(DailyAttendance::isIncomplete).count();
            nonWorking += (int) r.days().stream().filter(d -> !d.isWorkingDay()).count();
            dups += r.duplicatePunchIds().size();
        }
        log.info("Recalcul assiduité : {} collaborateurs, {} journées ({} incomplètes), {} doublons", count, days, incomplete, dups);
        return new Summary(count, days, incomplete, dups, nonWorking);
    }

    private record Context(LocalDate dataStart, LocalDate dataEnd, Set<LocalDate> holidays, List<SpecialPeriod> periods,
                           Map<Long, Terminal> terminals, AttendanceCalculator.Rules rules) {
    }

    private Context context(LocalDate dataStart, LocalDate dataEnd) {
        Set<LocalDate> hol = holidays.findAll().stream().map(PublicHoliday::getHolidayDate).collect(Collectors.toSet());
        Map<Long, Terminal> term = terminals.findAll().stream().collect(Collectors.toMap(Terminal::getId, Function.identity()));
        return new Context(dataStart, dataEnd, hol, periods.findAll(), term, settings.rules());
    }

    private AttendanceCalculator.Result recompute(Employee e, Context ctx) {
        List<AttendanceCalculator.PunchInput> input = punches.findByEmployeeIdOrderByPunchTimeAsc(e.getId()).stream()
                .map(p -> {
                    Terminal t = ctx.terminals().get(p.getTerminalId());
                    return new AttendanceCalculator.PunchInput(p.getId(), p.getPunchTime(), p.getDirection(), t.getCode(), t.isBuildingBoundary());
                })
                .toList();
        if (input.isEmpty()) {
            return null;
        }
        LocalDate trackingStart = e.getHireDate() != null && e.getHireDate().isAfter(ctx.dataStart()) ? e.getHireDate() : ctx.dataStart();
        WorkSchedule schedule = schedules.findByEmployeeId(e.getId()).orElseGet(() -> deduceSchedule(e, input, trackingStart));
        LocalTime ref = schedule != null ? schedule.getStartTime() : null;
        int tolerance = schedule != null ? schedule.getToleranceMinutes() : settings.getInt(SettingsService.LATE_TOLERANCE, 10);

        AttendanceCalculator.Result result = AttendanceCalculator.compute(new AttendanceCalculator.Input(
                e.getId(), trackingStart, ctx.dataEnd(), input, ctx.holidays(), ctx.periods(),
                leaves.findByEmployeeId(e.getId()), ref, tolerance, ctx.rules()));
        persist(e.getId(), result);
        return result;
    }

    private WorkSchedule deduceSchedule(Employee e, List<AttendanceCalculator.PunchInput> input, LocalDate trackingStart) {
        return AttendanceCalculator.deduceReferenceStart(input, trackingStart,
                        settings.getInt(SettingsService.SCHEDULE_LEARNING_DAYS, 60), settings.getInt(SettingsService.SCHEDULE_ROUNDING, 15))
                .map(start -> {
                    WorkSchedule ws = new WorkSchedule();
                    ws.setEmployeeId(e.getId());
                    ws.setStartTime(start);
                    ws.setToleranceMinutes(settings.getInt(SettingsService.LATE_TOLERANCE, 10));
                    ws.setSource("AUTO");
                    ws.setUpdatedBy("system");
                    return schedules.save(ws);
                })
                .orElse(null);
    }

    private void persist(Long employeeId, AttendanceCalculator.Result result) {
        jdbc.update("DELETE FROM daily_attendance WHERE employee_id = ?", employeeId);
        jdbc.batchUpdate("""
                        INSERT INTO daily_attendance (employee_id, work_date, working_day, status, first_in, last_out, late_minutes,
                            worked_minutes, punch_count, boundary_punch_count, duplicate_count, incomplete, reference_start, special_period_kind)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                result.days(), 500, (ps, d) -> {
                    ps.setLong(1, d.getEmployeeId());
                    ps.setDate(2, Date.valueOf(d.getWorkDate()));
                    ps.setBoolean(3, d.isWorkingDay());
                    ps.setString(4, d.getStatus().name());
                    ps.setTime(5, d.getFirstIn() != null ? Time.valueOf(d.getFirstIn()) : null);
                    ps.setTime(6, d.getLastOut() != null ? Time.valueOf(d.getLastOut()) : null);
                    ps.setInt(7, d.getLateMinutes());
                    ps.setObject(8, d.getWorkedMinutes());
                    ps.setInt(9, d.getPunchCount());
                    ps.setInt(10, d.getBoundaryPunchCount());
                    ps.setInt(11, d.getDuplicateCount());
                    ps.setBoolean(12, d.isIncomplete());
                    ps.setTime(13, d.getReferenceStart() != null ? Time.valueOf(d.getReferenceStart()) : null);
                    ps.setString(14, d.getSpecialPeriodKind());
                });
        jdbc.update("UPDATE raw_punch SET is_duplicate = FALSE WHERE employee_id = ? AND is_duplicate", employeeId);
        if (!result.duplicatePunchIds().isEmpty()) {
            jdbc.batchUpdate("UPDATE raw_punch SET is_duplicate = TRUE WHERE id = ?",
                    new ArrayList<>(result.duplicatePunchIds()), 500, (ps, id) -> ps.setLong(1, id));
        }
    }
}
