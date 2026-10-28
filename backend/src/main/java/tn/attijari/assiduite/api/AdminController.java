package tn.attijari.assiduite.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tn.attijari.assiduite.attendance.AttendanceRecomputeService;
import tn.attijari.assiduite.attendance.SettingsService;
import tn.attijari.assiduite.audit.AuditService;
import tn.attijari.assiduite.domain.*;
import tn.attijari.assiduite.repository.*;
import tn.attijari.assiduite.security.CurrentUser;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Administration RH / Admin : horaires, fériés, périodes spéciales, congés, paramètres, terminaux.
 * Toute modification relance le recalcul de {@code daily_attendance} et est tracée.
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Administration (RH / Admin)")
public class AdminController {

    private final EmployeeRepository employees;
    private final WorkScheduleRepository schedules;
    private final PublicHolidayRepository holidays;
    private final SpecialPeriodRepository periods;
    private final LeaveRepository leaves;
    private final TerminalRepository terminals;
    private final SettingsService settings;
    private final AttendanceRecomputeService recompute;
    private final AuditService audit;

    public AdminController(EmployeeRepository employees, WorkScheduleRepository schedules, PublicHolidayRepository holidays,
                           SpecialPeriodRepository periods, LeaveRepository leaves, TerminalRepository terminals,
                           SettingsService settings, AttendanceRecomputeService recompute, AuditService audit) {
        this.employees = employees;
        this.schedules = schedules;
        this.holidays = holidays;
        this.periods = periods;
        this.leaves = leaves;
        this.terminals = terminals;
        this.settings = settings;
        this.recompute = recompute;
        this.audit = audit;
    }

    private void changed(String action, String type, Object id, String details) {
        audit.log(CurrentUser.get().username(), action, type, id, details);
        recompute.recomputeAll();
    }

    // ---------- Horaires de référence ----------

    public record ScheduleRow(Long employeeId, String code, String displayName, String teamName, boolean synthetic,
                              String startTime, String endTime, int toleranceMinutes, String source, String updatedBy, Instant updatedAt) {
    }

    public record ScheduleUpdate(@NotNull LocalTime startTime, LocalTime endTime, @Min(0) @Max(120) int toleranceMinutes) {
    }

    @GetMapping("/schedules")
    @Operation(summary = "Horaires de référence par collaborateur (AUTO = déduit des données, RH = saisi)")
    @Transactional(readOnly = true)
    public List<ScheduleRow> schedules() {
        Map<Long, WorkSchedule> byEmp = schedules.findAll().stream().collect(Collectors.toMap(WorkSchedule::getEmployeeId, Function.identity()));
        return employees.findAllByOrderByDisplayNameAsc().stream().map(e -> {
            WorkSchedule ws = byEmp.get(e.getId());
            return new ScheduleRow(e.getId(), e.getCode(), e.getDisplayName(), e.getTeam() != null ? e.getTeam().getName() : null,
                    e.isSynthetic(), ws != null ? ws.getStartTime().toString() : null,
                    ws != null && ws.getEndTime() != null ? ws.getEndTime().toString() : null,
                    ws != null ? ws.getToleranceMinutes() : 10, ws != null ? ws.getSource() : null,
                    ws != null ? ws.getUpdatedBy() : null, ws != null ? ws.getUpdatedAt() : null);
        }).toList();
    }

    @PutMapping("/schedules/{employeeId}")
    @Operation(summary = "Modifier l'horaire de référence d'un collaborateur")
    public void updateSchedule(@PathVariable Long employeeId, @Valid @RequestBody ScheduleUpdate body) {
        employees.findById(employeeId).orElseThrow(() -> new NoSuchElementException("Collaborateur introuvable"));
        WorkSchedule ws = schedules.findByEmployeeId(employeeId).orElseGet(() -> {
            WorkSchedule n = new WorkSchedule();
            n.setEmployeeId(employeeId);
            return n;
        });
        ws.setStartTime(body.startTime());
        ws.setEndTime(body.endTime());
        ws.setToleranceMinutes(body.toleranceMinutes());
        ws.setSource("RH");
        ws.setUpdatedBy(CurrentUser.get().username());
        ws.setUpdatedAt(Instant.now());
        schedules.save(ws);
        changed("UPDATE_SCHEDULE", "work_schedule", employeeId, body.startTime() + " tol. " + body.toleranceMinutes());
    }

    // ---------- Jours fériés ----------

    public record HolidayBody(@NotNull LocalDate holidayDate, @NotBlank String label, boolean religious, boolean toVerify) {
    }

    @GetMapping("/holidays")
    public List<PublicHoliday> holidays() {
        return holidays.findAllByOrderByHolidayDateAsc();
    }

    @PostMapping("/holidays")
    public PublicHoliday createHoliday(@Valid @RequestBody HolidayBody body) {
        PublicHoliday h = holidays.save(apply(new PublicHoliday(), body));
        changed("CREATE_HOLIDAY", "public_holiday", h.getId(), body.holidayDate() + " " + body.label());
        return h;
    }

    @PutMapping("/holidays/{id}")
    public PublicHoliday updateHoliday(@PathVariable Long id, @Valid @RequestBody HolidayBody body) {
        PublicHoliday h = holidays.save(apply(holidays.findById(id).orElseThrow(), body));
        changed("UPDATE_HOLIDAY", "public_holiday", id, body.holidayDate() + " " + body.label());
        return h;
    }

    @DeleteMapping("/holidays/{id}")
    public void deleteHoliday(@PathVariable Long id) {
        holidays.deleteById(id);
        changed("DELETE_HOLIDAY", "public_holiday", id, null);
    }

    private static PublicHoliday apply(PublicHoliday h, HolidayBody b) {
        h.setHolidayDate(b.holidayDate());
        h.setLabel(b.label());
        h.setReligious(b.religious());
        h.setToVerify(b.toVerify());
        return h;
    }

    // ---------- Périodes spéciales ----------

    public record PeriodBody(@NotNull PeriodKind kind, @NotBlank String label, @NotNull LocalDate startDate, @NotNull LocalDate endDate,
                             LocalTime refStartTime, LocalTime refEndTime) {
    }

    @GetMapping("/special-periods")
    public List<SpecialPeriod> specialPeriods() {
        return periods.findAll().stream().sorted(Comparator.comparing(SpecialPeriod::getStartDate)).toList();
    }

    @PostMapping("/special-periods")
    public SpecialPeriod createPeriod(@Valid @RequestBody PeriodBody body) {
        SpecialPeriod p = periods.save(apply(new SpecialPeriod(), body));
        changed("CREATE_SPECIAL_PERIOD", "special_period", p.getId(), body.label());
        return p;
    }

    @PutMapping("/special-periods/{id}")
    public SpecialPeriod updatePeriod(@PathVariable Long id, @Valid @RequestBody PeriodBody body) {
        SpecialPeriod p = periods.save(apply(periods.findById(id).orElseThrow(), body));
        changed("UPDATE_SPECIAL_PERIOD", "special_period", id, body.label());
        return p;
    }

    @DeleteMapping("/special-periods/{id}")
    public void deletePeriod(@PathVariable Long id) {
        periods.deleteById(id);
        changed("DELETE_SPECIAL_PERIOD", "special_period", id, null);
    }

    private static SpecialPeriod apply(SpecialPeriod p, PeriodBody b) {
        if (b.endDate().isBefore(b.startDate())) {
            throw new IllegalArgumentException("La fin de période précède le début");
        }
        p.setKind(b.kind());
        p.setLabel(b.label());
        p.setStartDate(b.startDate());
        p.setEndDate(b.endDate());
        p.setRefStartTime(b.refStartTime());
        p.setRefEndTime(b.refEndTime());
        return p;
    }

    // ---------- Congés déclarés ----------

    public record LeaveBody(@NotNull Long employeeId, @NotNull LocalDate startDate, @NotNull LocalDate endDate, String leaveType, String comment) {
    }

    public record LeaveRow(Long id, Long employeeId, String employeeName, LocalDate startDate, LocalDate endDate, String leaveType,
                           String comment, String createdBy, Instant createdAt) {
    }

    @GetMapping("/leaves")
    @Transactional(readOnly = true)
    public List<LeaveRow> leaves() {
        Map<Long, String> names = employees.findAll().stream().collect(Collectors.toMap(Employee::getId, Employee::getDisplayName));
        return leaves.findAllByOrderByStartDateDesc().stream().map(l -> new LeaveRow(l.getId(), l.getEmployeeId(),
                names.get(l.getEmployeeId()), l.getStartDate(), l.getEndDate(), l.getLeaveType(), l.getComment(),
                l.getCreatedBy(), l.getCreatedAt())).toList();
    }

    @PostMapping("/leaves")
    public Leave createLeave(@Valid @RequestBody LeaveBody body) {
        employees.findById(body.employeeId()).orElseThrow(() -> new NoSuchElementException("Collaborateur introuvable"));
        if (body.endDate().isBefore(body.startDate())) {
            throw new IllegalArgumentException("La fin du congé précède le début");
        }
        Leave l = new Leave();
        l.setEmployeeId(body.employeeId());
        l.setStartDate(body.startDate());
        l.setEndDate(body.endDate());
        l.setLeaveType(body.leaveType() != null && !body.leaveType().isBlank() ? body.leaveType() : "CONGE_ANNUEL");
        l.setComment(body.comment());
        l.setCreatedBy(CurrentUser.get().username());
        l = leaves.save(l);
        changed("CREATE_LEAVE", "leave", l.getId(), "collaborateur " + body.employeeId() + " " + body.startDate() + "→" + body.endDate());
        return l;
    }

    @DeleteMapping("/leaves/{id}")
    public void deleteLeave(@PathVariable Long id) {
        leaves.deleteById(id);
        changed("DELETE_LEAVE", "leave", id, null);
    }

    // ---------- Paramètres ----------

    public record SettingBody(@NotBlank String value) {
    }

    @GetMapping("/settings")
    public List<AppSetting> settings() {
        return settings.findAll().stream().sorted(Comparator.comparing(AppSetting::getKey)).toList();
    }

    @PutMapping("/settings/{key}")
    public AppSetting updateSetting(@PathVariable String key, @Valid @RequestBody SettingBody body) {
        AppSetting s;
        try {
            s = settings.update(key, body.value());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valeur entière attendue");
        }
        changed("UPDATE_SETTING", "app_settings", key, body.value());
        return s;
    }

    // ---------- Terminaux ----------

    public record TerminalBody(@NotBlank String label, boolean buildingBoundary) {
    }

    @GetMapping("/terminals")
    public List<Terminal> terminals() {
        return terminals.findAll().stream().sorted(Comparator.comparing(Terminal::getCode)).toList();
    }

    @PutMapping("/terminals/{id}")
    @Operation(summary = "Modifier un terminal (porte frontière du bâtiment ou zone interne)")
    public Terminal updateTerminal(@PathVariable Long id, @Valid @RequestBody TerminalBody body) {
        Terminal t = terminals.findById(id).orElseThrow();
        t.setLabel(body.label());
        t.setBuildingBoundary(body.buildingBoundary());
        t = terminals.save(t);
        changed("UPDATE_TERMINAL", "terminal", id, t.getCode() + " frontière=" + body.buildingBoundary());
        return t;
    }

    @PostMapping("/recompute")
    @Operation(summary = "Relancer le recalcul complet de daily_attendance")
    public AttendanceRecomputeService.Summary recomputeNow() {
        audit.log(CurrentUser.get().username(), "RECOMPUTE", "daily_attendance", null, null);
        return recompute.recomputeAll();
    }
}
