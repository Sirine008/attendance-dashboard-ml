package tn.attijari.assiduite.imports;

import org.apache.poi.ss.usermodel.*;
import tn.attijari.assiduite.domain.Direction;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lecture du fichier de pointage (.xls ou .xlsx) : une feuille par collaborateur, en-tête repéré par la
 * présence de la colonne {@code _Time} dans les 10 premières lignes, colonnes détectées par leur nom.
 */
public final class ExcelPunchParser {

    static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final Pattern HIRE_NOTE = Pattern.compile("embauche\\D*(\\d{2}/\\d{2}/\\d{4})", Pattern.CASE_INSENSITIVE);
    private static final DataFormatter FORMATTER = new DataFormatter(Locale.FRANCE);

    private ExcelPunchParser() {
    }

    public record ParsedPunch(LocalDateTime time, Direction direction, String terminalCode, String terminalRaw) {
    }

    public record ParsedSheet(String sheetName, String employeeCode, String employeeName, LocalDate hireDate,
                              List<ParsedPunch> punches, List<String> rejectedRows) {
    }

    public record Terminal(Direction direction, String code) {
    }

    /** {@code "Sortie  SS1 B"} → (OUT, "SS1 B"). Le 1er mot donne le sens ; les espaces multiples sont normalisés. */
    public static Terminal normalizeTerminal(String raw) {
        String norm = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
        if (norm.isEmpty()) {
            throw new IllegalArgumentException("Terminal vide");
        }
        int space = norm.indexOf(' ');
        String verb = (space < 0 ? norm : norm.substring(0, space)).toLowerCase(Locale.ROOT);
        Direction dir;
        if (verb.startsWith("entr")) {
            dir = Direction.IN;
        } else if (verb.startsWith("sort")) {
            dir = Direction.OUT;
        } else {
            throw new IllegalArgumentException("Sens inconnu dans le terminal : " + raw);
        }
        String code = space < 0 ? "" : norm.substring(space + 1).toUpperCase(Locale.ROOT);
        if (code.isEmpty()) {
            throw new IllegalArgumentException("Porte absente dans le terminal : " + raw);
        }
        return new Terminal(dir, code);
    }

    public static List<ParsedSheet> parse(InputStream in) throws IOException {
        List<ParsedSheet> sheets = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(in)) {
            for (Sheet sheet : wb) {
                ParsedSheet parsed = parseSheet(sheet);
                if (parsed != null) {
                    sheets.add(parsed);
                }
            }
        }
        return sheets;
    }

    private static ParsedSheet parseSheet(Sheet sheet) {
        Map<String, Integer> columns = null;
        int headerRow = -1;
        for (int r = sheet.getFirstRowNum(); r <= Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + 10); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            Map<String, Integer> found = new HashMap<>();
            for (Cell c : row) {
                String v = FORMATTER.formatCellValue(c).trim();
                if (!v.isEmpty()) {
                    found.put(v, c.getColumnIndex());
                }
            }
            if (found.containsKey("_Time")) {
                columns = found;
                headerRow = r;
                break;
            }
        }
        if (columns == null || !columns.containsKey("Terminal_Name")) {
            return null; // feuille sans données de pointage
        }
        int cTime = columns.get("_Time");
        int cTerm = columns.get("Terminal_Name");
        Integer cUser = columns.get("User_ID");
        Integer cName = columns.get("User_Name");
        Set<Integer> known = new HashSet<>(columns.values());

        String code = null, name = null;
        LocalDate hire = null;
        List<ParsedPunch> punches = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (int r = headerRow + 1; r <= sheet.getLastRowNum(); r++) {
            Row row = sheet.getRow(r);
            if (row == null) {
                continue;
            }
            for (Cell c : row) { // notes libres hors colonnes connues (ex. « Date d'embauche le 06/08/2024 »)
                if (!known.contains(c.getColumnIndex())) {
                    Matcher m = HIRE_NOTE.matcher(FORMATTER.formatCellValue(c));
                    if (m.find()) {
                        hire = LocalDate.parse(m.group(1), DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    }
                }
            }
            Cell timeCell = row.getCell(cTime);
            String rawTime = timeCell == null ? "" : FORMATTER.formatCellValue(timeCell).trim();
            if (rawTime.isEmpty()) {
                continue;
            }
            if (code == null && cUser != null) {
                code = text(row, cUser);
                name = cName != null ? text(row, cName) : null;
            }
            try {
                LocalDateTime t = timeCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(timeCell)
                        ? timeCell.getLocalDateTimeCellValue().withNano(0)
                        : LocalDateTime.parse(rawTime, TIME_FORMAT);
                String rawTerminal = text(row, cTerm);
                Terminal term = normalizeTerminal(rawTerminal);
                punches.add(new ParsedPunch(t, term.direction(), term.code(), rawTerminal));
            } catch (DateTimeParseException | IllegalArgumentException ex) {
                rejected.add("Feuille " + sheet.getSheetName() + ", ligne " + (r + 1) + " : " + ex.getMessage());
            }
        }
        if (code == null || code.isBlank()) {
            code = sheet.getSheetName().trim();
        }
        return new ParsedSheet(sheet.getSheetName(), code, name, hire, punches, rejected);
    }

    private static String text(Row row, int col) {
        Cell c = row.getCell(col);
        return c == null ? "" : FORMATTER.formatCellValue(c).trim();
    }
}
