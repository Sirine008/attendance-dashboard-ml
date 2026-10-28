package tn.attijari.assiduite.imports;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import tn.attijari.assiduite.config.AppProperties;
import tn.attijari.assiduite.repository.RawPunchRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/** Import automatique au démarrage si la base ne contient encore aucun pointage. */
@Component
@Order(10)
public class StartupImportRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupImportRunner.class);

    private final AppProperties props;
    private final RawPunchRepository punches;
    private final ImportService importService;

    public StartupImportRunner(AppProperties props, RawPunchRepository punches, ImportService importService) {
        this.props = props;
        this.punches = punches;
        this.importService = importService;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!props.importConfig().onStartup()) {
            return;
        }
        if (punches.count() > 0) {
            log.info("Import au démarrage ignoré : la base contient déjà des pointages.");
            return;
        }
        List<Path> files = new ArrayList<>();
        Path dir = Path.of(props.importConfig().dir());
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> p.getFileName().toString().toLowerCase().matches(".*\\.xlsx?$")).sorted().forEach(files::add);
            }
        } else {
            log.warn("Dossier d'import introuvable : {}", dir.toAbsolutePath());
        }
        String synthetic = props.importConfig().syntheticFile();
        if (synthetic != null && !synthetic.isBlank() && Files.isRegularFile(Path.of(synthetic))) {
            files.add(Path.of(synthetic));
        }
        for (Path f : files) {
            log.info("Import au démarrage : {}", f.toAbsolutePath());
            importService.importFile(Files.readAllBytes(f), f.getFileName().toString(), "system");
        }
    }
}
