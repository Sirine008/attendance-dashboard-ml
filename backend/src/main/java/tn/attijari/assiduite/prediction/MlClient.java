package tn.attijari.assiduite.prediction;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tn.attijari.assiduite.config.AppProperties;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Client du microservice IA (FastAPI), avec nouvelles tentatives. */
@Component
public class MlClient {

    private static final Logger log = LoggerFactory.getLogger(MlClient.class);

    private final RestClient http;
    private final AppProperties.Ml cfg;

    /** Le builder fourni par Spring Boot porte l'ObjectMapper de l'application (dates ISO, pas de tableaux). */
    public MlClient(AppProperties props, RestClient.Builder builder) {
        this.cfg = props.ml();
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(3));
        f.setReadTimeout(cfg.readTimeout());
        this.http = builder.clone().baseUrl(cfg.url()).requestFactory(f).build();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Health(String status, @JsonProperty("model_loaded") boolean modelLoaded,
                         @JsonProperty("model_version") String modelVersion) {
    }

    public record PredictRequest(@JsonProperty("reference_date") LocalDate referenceDate,
                                 @JsonProperty("employee_ids") List<Long> employeeIds, List<String> horizons) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PredictionDto(@JsonProperty("employee_id") Long employeeId, String horizon, String status,
                                @JsonProperty("reference_date") LocalDate referenceDate,
                                @JsonProperty("target_date") LocalDate targetDate, @JsonProperty("target_end") LocalDate targetEnd,
                                Double probability, @JsonProperty("risk_level") String riskLevel,
                                @JsonProperty("expected_days") Double expectedDays, List<Map<String, Object>> factors) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PredictResponse(@JsonProperty("model_version") String modelVersion,
                                  @JsonProperty("reference_date") LocalDate referenceDate, List<PredictionDto> predictions) {
    }

    /** Vérification rapide, sans nouvelle tentative. */
    public Health health() {
        return http.get().uri("/health").retrieve().body(Health.class);
    }

    public PredictResponse predict(LocalDate reference, List<Long> employeeIds) {
        return withRetry("predict", () -> http.post().uri("/predict")
                .body(new PredictRequest(reference, employeeIds, List.of("D1", "W1")))
                .retrieve().body(PredictResponse.class));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> modelInfo() {
        return http.get().uri("/model/info").retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> backtest(int weeks, String horizon) {
        return http.get().uri(u -> u.path("/model/backtest").queryParam("weeks", weeks).queryParam("horizon", horizon).build())
                .retrieve().body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> train() {
        return http.post().uri("/train").header("X-Admin-Token", cfg.adminToken()).retrieve().body(Map.class);
    }

    private <T> T withRetry(String op, Supplier<T> call) {
        RestClientException last = null;
        for (int attempt = 1; attempt <= Math.max(1, cfg.maxAttempts()); attempt++) {
            try {
                return call.get();
            } catch (RestClientException e) {
                last = e;
                log.warn("ml-service {} : tentative {}/{} échouée ({})", op, attempt, cfg.maxAttempts(), e.getMessage());
                if (attempt < cfg.maxAttempts()) {
                    try {
                        Thread.sleep(cfg.retryDelay().toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        throw last != null ? last : new IllegalStateException("ml-service indisponible");
    }
}
