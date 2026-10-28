package tn.attijari.assiduite;

import org.springframework.jdbc.core.JdbcTemplate;
import tn.attijari.assiduite.imports.ImportService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Jeu de données partagé entre classes de test : le vrai fichier de pointage, importé une seule fois. */
public final class TestData {

    public static final Path FILE = Path.of("..", "data", "raw", "Pointage_PFA.xls");

    private TestData() {
    }

    public static void ensureImported(JdbcTemplate jdbc, ImportService importService) throws IOException {
        Long n = jdbc.queryForObject("SELECT count(*) FROM raw_punch", Long.class);
        if (n == null || n == 0) {
            importService.importFile(Files.readAllBytes(FILE), "Pointage_PFA.xls", "test");
        }
    }

    /** Remet la base dans l'état « aucun pointage importé » (données de référence et seed conservés). */
    public static void reset(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE prediction, daily_attendance, raw_punch, import_batch, work_schedule, leave, audit_log RESTART IDENTITY");
    }
}
