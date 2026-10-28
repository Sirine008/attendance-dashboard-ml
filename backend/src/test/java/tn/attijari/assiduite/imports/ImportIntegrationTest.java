package tn.attijari.assiduite.imports;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import tn.attijari.assiduite.AbstractIntegrationTest;
import tn.attijari.assiduite.TestData;
import tn.attijari.assiduite.domain.ImportBatch;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Import du vrai fichier {@code data/raw/Pointage_PFA.xls} : volumes, idempotence, et cohérence des absences
 * avec le profil indépendant calculé en Python ({@code docs/data_profile.md} §6).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ImportIntegrationTest extends AbstractIntegrationTest {

    static final Path FILE = Path.of("..", "data", "raw", "Pointage_PFA.xls");

    @Autowired
    ImportService importService;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    @Order(1)
    void importsRealFileAndComputesDailyAttendance() throws Exception {
        TestData.reset(jdbc);
        ImportBatch batch = importService.importFile(Files.readAllBytes(FILE), "Pointage_PFA.xls", "test");

        assertThat(batch.getStatus()).isEqualTo("SUCCESS");
        assertThat(batch.getRowsRead()).isEqualTo(11_367);
        assertThat(batch.getRowsRejected()).isZero();
        assertThat(batch.getRowsInserted() + batch.getRowsAlreadyKnown()).isEqualTo(11_367);
        assertThat(batch.getRowsAlreadyKnown()).isEqualTo(9); // doublons exacts présents dans le fichier

        Map<String, Integer> byStatus = jdbc.queryForList(
                        "SELECT status, count(*) AS n FROM daily_attendance WHERE working_day GROUP BY status")
                .stream().collect(Collectors.toMap(r -> (String) r.get("status"), r -> ((Number) r.get("n")).intValue()));
        int workingDays = byStatus.values().stream().mapToInt(Integer::intValue).sum();
        assertThat(workingDays).isEqualTo(2456);
        assertThat(byStatus.get("ABSENCE_PONCTUELLE")).isEqualTo(132);
        assertThat(byStatus.get("ABSENCE_LONGUE")).isEqualTo(166);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_attendance WHERE NOT working_day", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM daily_attendance WHERE incomplete", Integer.class)).isEqualTo(58);

        // dates d'embauche réelles lues dans le fichier
        assertThat(jdbc.queryForObject("SELECT hire_date_fictive FROM employee WHERE code = 'MMMMMM'", Boolean.class)).isFalse();
        // horaires de référence déduits (médiane 60 premiers jours arrondie au ¼ h)
        assertThat(jdbc.queryForObject("""
                SELECT to_char(ws.start_time, 'HH24:MI') FROM work_schedule ws JOIN employee e ON e.id = ws.employee_id
                WHERE e.code = 'XXXXXX'""", String.class)).isEqualTo("09:45");
    }

    @Test
    @Order(2)
    void reimportingTheSameFileIsIdempotent() throws Exception {
        long before = jdbc.queryForObject("SELECT count(*) FROM raw_punch", Long.class);
        ImportBatch batch = importService.importFile(Files.readAllBytes(FILE), "Pointage_PFA.xls", "test");
        assertThat(batch.getRowsInserted()).isZero();
        assertThat(batch.getRowsAlreadyKnown()).isEqualTo(11_367);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM raw_punch", Long.class)).isEqualTo(before);
    }
}
