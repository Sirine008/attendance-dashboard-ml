package tn.attijari.assiduite.imports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.attijari.assiduite.attendance.AttendanceRecomputeService;
import tn.attijari.assiduite.domain.Employee;
import tn.attijari.assiduite.domain.ImportBatch;
import tn.attijari.assiduite.domain.Team;
import tn.attijari.assiduite.domain.Terminal;
import tn.attijari.assiduite.repository.EmployeeRepository;
import tn.attijari.assiduite.repository.ImportBatchRepository;
import tn.attijari.assiduite.repository.TeamRepository;
import tn.attijari.assiduite.repository.TerminalRepository;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.util.*;

/**
 * Import idempotent d'un fichier de pointage : les lignes déjà connues (même collaborateur, horodatage, porte, sens)
 * sont ignorées grâce à la contrainte d'unicité de {@code raw_punch}. {@code daily_attendance} est ensuite recalculé.
 */
@Service
public class ImportService {

    public static final String SYNTHETIC_PREFIX = "SYN";
    public static final String SYNTHETIC_TEAM = "Équipe démo (données synthétiques)";
    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final EmployeeRepository employees;
    private final TeamRepository teams;
    private final TerminalRepository terminals;
    private final ImportBatchRepository batches;
    private final AttendanceRecomputeService recompute;
    private final JdbcTemplate jdbc;

    public ImportService(EmployeeRepository employees, TeamRepository teams, TerminalRepository terminals,
                         ImportBatchRepository batches, AttendanceRecomputeService recompute, JdbcTemplate jdbc) {
        this.employees = employees;
        this.teams = teams;
        this.terminals = terminals;
        this.batches = batches;
        this.recompute = recompute;
        this.jdbc = jdbc;
    }

    @Transactional
    public ImportBatch importFile(byte[] content, String fileName, String triggeredBy) {
        ImportBatch batch = new ImportBatch();
        batch.setSourceFile(fileName);
        batch.setFileSha256(sha256(content));
        batch.setStatus("RUNNING");
        batch.setTriggeredBy(triggeredBy);
        batch = batches.save(batch);

        List<ExcelPunchParser.ParsedSheet> sheets;
        try {
            sheets = ExcelPunchParser.parse(new ByteArrayInputStream(content));
        } catch (IOException | RuntimeException e) {
            throw new ImportException("Fichier illisible (formats acceptés : .xls, .xlsx) : " + e.getMessage(), e);
        }
        if (sheets.isEmpty()) {
            throw new ImportException("Aucune feuille de pointage trouvée (colonne _Time absente).", null);
        }

        int read = 0, inserted = 0, rejected = 0;
        List<String> anomalies = new ArrayList<>();
        List<String> newEmployees = new ArrayList<>();
        Set<String> newTerminals = new TreeSet<>();
        Map<String, Object> perSheet = new LinkedHashMap<>();
        Map<String, Terminal> terminalCache = new HashMap<>();

        for (ExcelPunchParser.ParsedSheet sheet : sheets) {
            Employee emp = resolveEmployee(sheet, newEmployees);
            read += sheet.punches().size() + sheet.rejectedRows().size();
            rejected += sheet.rejectedRows().size();
            anomalies.addAll(sheet.rejectedRows());

            List<Object[]> rows = new ArrayList<>(sheet.punches().size());
            int weekend = 0;
            for (ExcelPunchParser.ParsedPunch p : sheet.punches()) {
                Terminal t = terminalCache.computeIfAbsent(p.terminalCode(), code -> resolveTerminal(code, newTerminals));
                rows.add(new Object[]{emp.getId(), Timestamp.valueOf(p.time()), p.direction().name(), t.getId(), p.terminalRaw(), fileName, batch.getId()});
                DayOfWeek dow = p.time().getDayOfWeek();
                if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                    weekend++;
                }
            }
            int[] counts = jdbc.batchUpdate("""
                    INSERT INTO raw_punch (employee_id, punch_time, direction, terminal_id, terminal_raw, source_file, import_batch_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT ON CONSTRAINT uq_raw_punch DO NOTHING""", rows);
            int sheetInserted = Arrays.stream(counts).map(c -> Math.max(c, 0)).sum();
            inserted += sheetInserted;
            perSheet.put(sheet.sheetName(), Map.of("collaborateur", emp.getCode(), "lignes", sheet.punches().size(),
                    "insérées", sheetInserted, "pointagesWeekEnd", weekend));
        }
        newTerminals.forEach(t -> anomalies.add("Porte inconnue créée (non frontière par défaut, à vérifier) : " + t));

        AttendanceRecomputeService.Summary summary = recompute.recomputeAll();

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("feuilles", perSheet);
        report.put("nouveauxCollaborateurs", newEmployees);
        report.put("journeesIncompletes", summary.incompleteDays());
        report.put("doublonsProches", summary.duplicates());
        report.put("journeesNonOuvreesTravaillees", summary.weekendOrHolidayDays());
        report.put("anomalies", anomalies.size() > 200 ? anomalies.subList(0, 200) : anomalies);

        batch.setRowsRead(read);
        batch.setRowsInserted(inserted);
        batch.setRowsAlreadyKnown(read - rejected - inserted);
        batch.setRowsRejected(rejected);
        batch.setNearDuplicates(summary.duplicates());
        batch.setReport(report);
        batch.setStatus("SUCCESS");
        batch.setFinishedAt(Instant.now());
        log.info("Import {} : {} lues, {} insérées, {} déjà connues, {} rejetées", fileName, read, inserted, batch.getRowsAlreadyKnown(), rejected);
        return batches.save(batch);
    }

    private Employee resolveEmployee(ExcelPunchParser.ParsedSheet sheet, List<String> created) {
        Employee emp = employees.findByCode(sheet.employeeCode()).orElseGet(() -> {
            Employee e = new Employee();
            e.setCode(sheet.employeeCode());
            e.setDisplayName(displayName(sheet));
            if (sheet.employeeCode().startsWith(SYNTHETIC_PREFIX)) {
                e.setSynthetic(true);
                e.setTeam(teams.findByName(SYNTHETIC_TEAM).orElseGet(() -> teams.save(new Team(SYNTHETIC_TEAM, true))));
            }
            created.add(sheet.employeeCode());
            return e;
        });
        if (sheet.hireDate() != null && (emp.getHireDate() == null || emp.isHireDateFictive())) {
            emp.setHireDate(sheet.hireDate());
            emp.setHireDateFictive(false);
        }
        return employees.save(emp);
    }

    private static String displayName(ExcelPunchParser.ParsedSheet sheet) {
        String n = sheet.employeeName();
        if (n == null || n.isBlank()) {
            return sheet.employeeCode();
        }
        String lower = n.toLowerCase(Locale.FRENCH);
        StringBuilder sb = new StringBuilder();
        for (String w : lower.split(" ")) {
            if (!w.isEmpty()) {
                sb.append(sb.isEmpty() ? Character.toUpperCase(w.charAt(0)) + w.substring(1) : " " + (w.length() == 1 ? w.toUpperCase() : w));
            }
        }
        return sb.toString();
    }

    private Terminal resolveTerminal(String code, Set<String> created) {
        return terminals.findByCode(code).orElseGet(() -> {
            Terminal t = new Terminal();
            t.setCode(code);
            t.setLabel("Porte " + code + " (créée à l'import)");
            t.setBuildingBoundary(false);
            created.add(code);
            return terminals.save(t);
        });
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static class ImportException extends RuntimeException {
        public ImportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
