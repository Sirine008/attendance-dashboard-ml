package tn.attijari.assiduite.prediction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tn.attijari.assiduite.AbstractIntegrationTest;
import tn.attijari.assiduite.TestData;
import tn.attijari.assiduite.imports.ImportService;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Job de prédiction contre un faux ml-service : historisation, retry, et tolérance à la panne. */
@AutoConfigureMockMvc
class PredictionJobIntegrationTest extends AbstractIntegrationTest {

    static final AtomicBoolean ML_UP = new AtomicBoolean(true);
    static final AtomicInteger PREDICT_CALLS = new AtomicInteger();
    static final ObjectMapper JSON = new ObjectMapper();
    static HttpServer server;

    static {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/health", ex -> reply(ex, ML_UP.get() ? 200 : 503,
                "{\"status\":\"UP\",\"model_loaded\":true,\"model_version\":\"vfake\"}"));
        server.createContext("/predict", ex -> {
            PREDICT_CALLS.incrementAndGet();
            if (!ML_UP.get()) {
                reply(ex, 503, "{}");
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> req = JSON.readValue(ex.getRequestBody(), Map.class);
            String ref = (String) req.get("reference_date");
            StringBuilder sb = new StringBuilder("{\"model_version\":\"vfake\",\"reference_date\":\"" + ref + "\",\"predictions\":[");
            List<?> ids = (List<?>) req.get("employee_ids");
            for (int i = 0; i < ids.size(); i++) {
                sb.append(i > 0 ? "," : "").append("""
                        {"employee_id":%s,"horizon":"D1","status":"OK","reference_date":"%s","target_date":"2025-05-19",
                         "target_end":"2025-05-19","probability":0.2,"risk_level":"ELEVE","expected_days":null,
                         "factors":[{"feature":"dow_rate","label":"taux historique d'absence le lundi : 20%%","direction":"UP","weight":1.2}]},
                        {"employee_id":%s,"horizon":"W1","status":"INSUFFICIENT_HISTORY","reference_date":"%s","target_date":"2025-05-19",
                         "target_end":"2025-05-23","probability":null,"risk_level":null,"expected_days":null,"factors":[]}"""
                        .formatted(ids.get(i), ref, ids.get(i), ref));
            }
            reply(ex, 200, sb.append("]}").toString());
        });
        server.start();
    }

    static void reply(com.sun.net.httpserver.HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    @DynamicPropertySource
    static void mlUrl(DynamicPropertyRegistry r) {
        r.add("app.ml.url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
        r.add("app.ml.max-attempts", () -> "2");
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    @Autowired
    PredictionJobService job;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ImportService importService;
    @Autowired
    MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        TestData.ensureImported(jdbc, importService);
        ML_UP.set(true);
    }

    String rhToken() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"rh\",\"password\":\"Demo@2025\"}")).andReturn().getResponse().getContentAsString();
        return "Bearer " + JSON.readTree(body).get("token").asText();
    }

    @Test
    void predictionsAreStoredAndNeverOverwritten() {
        long before = jdbc.queryForObject("SELECT count(*) FROM prediction", Long.class);
        PredictionJobService.RunStatus s1 = job.run(null);
        assertThat(s1.success()).isTrue();
        assertThat(s1.referenceDate()).hasToString("2025-05-19"); // dernière journée de données
        assertThat(s1.stored()).isEqualTo(16); // 8 collaborateurs × 2 horizons
        job.run(null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM prediction", Long.class)).isEqualTo(before + 32);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT model_version) FROM prediction WHERE model_version = 'vfake'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT top_factors->0->>'label' FROM prediction WHERE horizon = 'D1' LIMIT 1", String.class))
                .contains("taux historique");
    }

    @Test
    void backfillCreatesHistoryForPastReferenceDates() {
        int stored = job.backfill(3);
        assertThat(stored).isEqualTo(3 * 16);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT reference_date) FROM prediction WHERE reference_date < '2025-05-19'", Long.class))
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    void mlServiceDownIsRetriedThenReportedWithoutBreakingTheDashboard() throws Exception {
        ML_UP.set(false);
        int calls = PREDICT_CALLS.get();
        PredictionJobService.RunStatus s = job.runSafely(null, "test");
        assertThat(s.success()).isFalse();
        assertThat(PREDICT_CALLS.get() - calls).isEqualTo(2); // 2 tentatives
        String rh = rhToken();
        mvc.perform(get("/api/dashboard/kpis").header("Authorization", rh)).andExpect(status().isOk());
        Thread.sleep(10);
        mvc.perform(get("/api/predictions/status").header("Authorization", rh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
        ML_UP.set(true);
    }

    @Test
    void latestRiskCardsAreExposedToManagersOfTheTeamOnly() throws Exception {
        job.run(null);
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"manager.a\",\"password\":\"Demo@2025\"}")).andReturn().getResponse().getContentAsString();
        String token = "Bearer " + JSON.readTree(body).get("token").asText();
        mvc.perform(get("/api/predictions/latest").param("horizon", "D1").param("minLevel", "ELEVE").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].prediction.riskLevel").value("ELEVE"));
    }
}
