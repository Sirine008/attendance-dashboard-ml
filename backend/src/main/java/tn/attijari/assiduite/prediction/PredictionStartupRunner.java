package tn.attijari.assiduite.prediction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Au démarrage (après l'import), en tâche de fond : attend ml-service puis, si aucune prédiction n'existe,
 * reconstitue un historique (backfill) et génère les prédictions courantes. Ne bloque jamais l'application.
 */
@Component
@Order(20)
public class PredictionStartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PredictionStartupRunner.class);
    private static final int MAX_WAIT_ATTEMPTS = 40;

    private final PredictionJobService job;
    private final MlClient ml;

    public PredictionStartupRunner(PredictionJobService job, MlClient ml) {
        this.job = job;
        this.ml = ml;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!job.config().runOnStartup()) {
            return;
        }
        Thread t = new Thread(this::initialize, "prediction-startup");
        t.setDaemon(true);
        t.start();
    }

    private void initialize() {
        for (int attempt = 1; attempt <= MAX_WAIT_ATTEMPTS; attempt++) {
            try {
                MlClient.Health h = ml.health();
                if (h != null && h.modelLoaded()) {
                    if (job.count() == 0) {
                        job.backfill(job.config().backfillWorkingDays());
                    }
                    job.runSafely(null, "démarrage");
                    return;
                }
                log.info("ml-service joignable mais sans modèle chargé (tentative {}/{})", attempt, MAX_WAIT_ATTEMPTS);
            } catch (RuntimeException e) {
                log.info("ml-service pas encore disponible (tentative {}/{}) : {}", attempt, MAX_WAIT_ATTEMPTS, e.getMessage());
            }
            try {
                Thread.sleep(15_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        log.warn("ml-service indisponible : prédictions non générées au démarrage (le tableau de bord reste disponible)");
        job.runSafely(null, "démarrage");
    }
}
