package tn.attijari.assiduite.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import tn.attijari.assiduite.audit.AuditService;
import tn.attijari.assiduite.domain.ModelRegistry;
import tn.attijari.assiduite.prediction.MlClient;
import tn.attijari.assiduite.prediction.PredictionJobService;
import tn.attijari.assiduite.prediction.PredictionStatusService;
import tn.attijari.assiduite.repository.ModelRegistryRepository;
import tn.attijari.assiduite.security.CurrentUser;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@Tag(name = "Santé du modèle (RH / Admin)")
public class ModelController {

    private final ModelRegistryRepository registry;
    private final MlClient ml;
    private final PredictionJobService job;
    private final PredictionStatusService status;
    private final AuditService audit;

    public ModelController(ModelRegistryRepository registry, MlClient ml, PredictionJobService job,
                           PredictionStatusService status, AuditService audit) {
        this.registry = registry;
        this.ml = ml;
        this.job = job;
        this.status = status;
        this.audit = audit;
    }

    public record RegistryRow(String version, Instant trainedAt, String algorithm, boolean active, String notes) {
        static RegistryRow of(ModelRegistry m) {
            return new RegistryRow(m.getVersion(), m.getTrainedAt(), m.getAlgorithm(), m.isActive(), m.getNotes());
        }
    }

    public record ModelInfo(PredictionStatusService.Status status, Map<String, Object> active, List<RegistryRow> history) {
    }

    @GetMapping("/api/predictions/status")
    @Operation(summary = "Disponibilité des prédictions (bandeau de l'interface)")
    public PredictionStatusService.Status predictionStatus() {
        return status.status();
    }

    @GetMapping("/api/model/info")
    @Operation(summary = "Version active, métriques vs baselines, seuils et historique des versions")
    public ModelInfo info() {
        List<ModelRegistry> all = registry.findAll(Sort.by(Sort.Direction.DESC, "trainedAt"));
        Map<String, Object> active = all.stream().filter(ModelRegistry::isActive).findFirst()
                .map(m -> Map.<String, Object>of("version", m.getVersion(), "trainedAt", m.getTrainedAt(),
                        "algorithm", m.getAlgorithm(), "metrics", m.getMetrics(), "thresholds", m.getThresholds()))
                .orElse(null);
        return new ModelInfo(status.status(), active, all.stream().map(RegistryRow::of).toList());
    }

    @GetMapping("/api/model/backtest")
    @Operation(summary = "Prédit vs réel sur les dernières semaines (via ml-service)")
    public Map<String, Object> backtest(@RequestParam(defaultValue = "8") int weeks, @RequestParam(defaultValue = "D1") String horizon) {
        try {
            return ml.backtest(weeks, horizon);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service de prédiction indisponible");
        }
    }

    @PostMapping("/api/model/train")
    @Operation(summary = "Réentraîner (nouvelle version dans model_registry) puis régénérer les prédictions")
    public Map<String, Object> train() {
        audit.log(CurrentUser.get().username(), "TRAIN_MODEL", "model_registry", null, null);
        try {
            Map<String, Object> result = ml.train();
            job.runSafely(null, "après réentraînement");
            return result;
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Service de prédiction indisponible");
        }
    }

    @PostMapping("/api/admin/predictions/run")
    @Operation(summary = "Déclencher manuellement le job de prédiction (date de référence optionnelle)")
    public PredictionJobService.RunStatus run(@RequestParam(required = false) LocalDate referenceDate) {
        audit.log(CurrentUser.get().username(), "RUN_PREDICTIONS", "prediction", null, referenceDate != null ? referenceDate.toString() : null);
        return job.runSafely(referenceDate, "manuel");
    }
}
