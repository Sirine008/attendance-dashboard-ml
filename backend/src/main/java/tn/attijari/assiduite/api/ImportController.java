package tn.attijari.assiduite.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tn.attijari.assiduite.audit.AuditService;
import tn.attijari.assiduite.domain.ImportBatch;
import tn.attijari.assiduite.imports.ImportService;
import tn.attijari.assiduite.repository.ImportBatchRepository;
import tn.attijari.assiduite.security.CurrentUser;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/imports")
@Tag(name = "Import", description = "Import idempotent des fichiers de pointage (RH / Admin)")
public class ImportController {

    private final ImportService importService;
    private final ImportBatchRepository batches;
    private final AuditService audit;

    public ImportController(ImportService importService, ImportBatchRepository batches, AuditService audit) {
        this.importService = importService;
        this.batches = batches;
        this.audit = audit;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Importer un fichier .xls / .xlsx (une feuille par collaborateur) ; renvoie le rapport d'import")
    public ImportBatch upload(@RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "import.xls";
        if (!name.toLowerCase(Locale.ROOT).matches(".*\\.xlsx?$")) {
            throw new IllegalArgumentException("Seuls les fichiers .xls et .xlsx sont acceptés");
        }
        CurrentUser user = CurrentUser.get();
        ImportBatch batch = importService.importFile(file.getBytes(), name, user.username());
        audit.log(user.username(), "IMPORT", "import_batch", batch.getId(),
                name + " : " + batch.getRowsInserted() + " lignes insérées, " + batch.getRowsAlreadyKnown() + " déjà connues");
        return batch;
    }

    @GetMapping
    @Operation(summary = "Historique des imports")
    public List<ImportBatch> history() {
        return batches.findTop20ByOrderByStartedAtDesc();
    }
}
