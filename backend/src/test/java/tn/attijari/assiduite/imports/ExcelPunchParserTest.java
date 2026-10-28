package tn.attijari.assiduite.imports;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import tn.attijari.assiduite.domain.Direction;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExcelPunchParserTest {

    @Test
    void normalizesTerminalWithMultipleSpaces() {
        assertThat(ExcelPunchParser.normalizeTerminal("Sortie  TB")).isEqualTo(new ExcelPunchParser.Terminal(Direction.OUT, "TB"));
        assertThat(ExcelPunchParser.normalizeTerminal(" Entrée   SS1  B ")).isEqualTo(new ExcelPunchParser.Terminal(Direction.IN, "SS1 B"));
        assertThat(ExcelPunchParser.normalizeTerminal("Entr�e TA").direction()).isEqualTo(Direction.IN);
        assertThatThrownBy(() -> ExcelPunchParser.normalizeTerminal("Badge TB")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsColumnsByNameAndReadsHireNote() throws Exception {
        byte[] bytes;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet a = wb.createSheet("AAAAAA");
            a.createRow(0); // ligne 1 vide
            Row h = a.createRow(1);
            String[] header = {"ordre", "_Time", "User_ID", "User_Name", "Terminal_Name"};
            for (int i = 0; i < header.length; i++) h.createCell(i).setCellValue(header[i]);
            Row r1 = a.createRow(2);
            r1.createCell(0).setCellValue(1);
            r1.createCell(1).setCellValue("06/08/2024 08:19:19");
            r1.createCell(2).setCellValue("AAAAAA");
            r1.createCell(3).setCellValue("COLLABORATEUR A");
            r1.createCell(4).setCellValue("Entrée TB");
            r1.createCell(6).setCellValue("Date d'embauche le 06/08/2024");
            Row r2 = a.createRow(3);
            r2.createCell(1).setCellValue("2024-08-06 bad");
            r2.createCell(2).setCellValue("AAAAAA");
            r2.createCell(4).setCellValue("Sortie  TB");

            Sheet b = wb.createSheet("BBBBBB"); // colonnes décalées, sans colonne 'ordre'
            b.createRow(0);
            Row hb = b.createRow(1);
            String[] headerB = {"_Time", "User_ID", "User_Name", "Terminal_Name"};
            for (int i = 0; i < headerB.length; i++) hb.createCell(i + 1).setCellValue(headerB[i]);
            Row rb = b.createRow(2);
            rb.createCell(1).setCellValue("02/01/2024 07:55:09");
            rb.createCell(2).setCellValue("BBBBBB");
            rb.createCell(4).setCellValue("Sortie  SS1 B");
            wb.createSheet("Vide");
            wb.write(out);
            bytes = out.toByteArray();
        }

        List<ExcelPunchParser.ParsedSheet> sheets = ExcelPunchParser.parse(new ByteArrayInputStream(bytes));
        assertThat(sheets).hasSize(2);
        ExcelPunchParser.ParsedSheet a = sheets.get(0);
        assertThat(a.employeeCode()).isEqualTo("AAAAAA");
        assertThat(a.hireDate()).isEqualTo(LocalDate.of(2024, 8, 6));
        assertThat(a.punches()).containsExactly(new ExcelPunchParser.ParsedPunch(LocalDateTime.of(2024, 8, 6, 8, 19, 19), Direction.IN, "TB", "Entrée TB"));
        assertThat(a.rejectedRows()).hasSize(1);
        ExcelPunchParser.ParsedSheet b = sheets.get(1);
        assertThat(b.punches().getFirst().terminalCode()).isEqualTo("SS1 B");
        assertThat(b.hireDate()).isNull();
    }
}
