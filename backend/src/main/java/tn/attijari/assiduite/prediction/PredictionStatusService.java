package tn.attijari.assiduite.prediction;

import org.springframework.stereotype.Service;
import tn.attijari.assiduite.repository.PredictionRepository;

import java.time.Instant;

/** État des prédictions pour le bandeau de l'UI (« prédictions indisponibles »). Santé ml-service mise en cache 30 s. */
@Service
public class PredictionStatusService {

    private static final long CACHE_MS = 30_000;

    private final MlClient ml;
    private final PredictionJobService job;
    private final PredictionRepository predictions;
    private volatile MlClient.Health cachedHealth;
    private volatile long cachedAt;

    public PredictionStatusService(MlClient ml, PredictionJobService job, PredictionRepository predictions) {
        this.ml = ml;
        this.job = job;
        this.predictions = predictions;
    }

    public record Status(boolean available, boolean mlReachable, String modelVersion, Instant lastPredictionAt,
                         Instant lastRunAt, Boolean lastRunSuccess, String message) {
    }

    public MlClient.Health health() {
        long now = System.currentTimeMillis();
        if (now - cachedAt > CACHE_MS) {
            try {
                cachedHealth = ml.health();
            } catch (RuntimeException e) {
                cachedHealth = null;
            }
            cachedAt = now;
        }
        return cachedHealth;
    }

    public Status status() {
        MlClient.Health h = health();
        boolean reachable = h != null && h.modelLoaded();
        Instant last = predictions.findLastCreatedAt();
        var run = job.lastRun().orElse(null);
        boolean available = reachable && last != null && (run == null || run.success());
        String msg = available ? "Prédictions disponibles"
                : !reachable ? "Prédictions indisponibles : le service de prédiction ne répond pas. Les indicateurs descriptifs restent à jour."
                : last == null ? "Aucune prédiction générée pour le moment."
                : "Prédictions indisponibles : " + run.message();
        return new Status(available, reachable, h != null ? h.modelVersion() : null, last,
                run != null ? run.at() : null, run != null ? run.success() : null, msg);
    }
}
