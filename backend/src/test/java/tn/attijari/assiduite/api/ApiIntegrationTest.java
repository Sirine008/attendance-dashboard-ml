package tn.attijari.assiduite.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tn.attijari.assiduite.AbstractIntegrationTest;
import tn.attijari.assiduite.TestData;
import tn.attijari.assiduite.imports.ImportService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Contrats principaux de l'API (avec les données réelles importées). */
@AutoConfigureMockMvc
class ApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ImportService importService;
    String rh;
    Long y;

    @BeforeEach
    void setUp() throws Exception {
        TestData.ensureImported(jdbc, importService);
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"rh\",\"password\":\"Demo@2025\"}")).andReturn().getResponse().getContentAsString();
        rh = "Bearer " + json.readTree(body).get("token").asText();
        y = jdbc.queryForObject("SELECT id FROM employee WHERE code = 'YYYYYY'", Long.class);
    }

    @Test
    void metaAndKpisExposeDataBoundsAndPreviousPeriod() throws Exception {
        mvc.perform(get("/api/meta").header("Authorization", rh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataStart").value("2024-01-02"))
                .andExpect(jsonPath("$.dataEnd").value("2025-05-19"));
        mvc.perform(get("/api/dashboard/kpis").param("from", "2025-04-01").param("to", "2025-04-30").header("Authorization", rh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previousFrom").value("2025-03-02"))
                .andExpect(jsonPath("$.previousTo").value("2025-03-31"))
                .andExpect(jsonPath("$.current.employees").value(8));
    }

    @Test
    void trendHeatmapAndArrivalsAreComputed() throws Exception {
        String trend = mvc.perform(get("/api/dashboard/trend").param("from", "2024-01-01").param("to", "2024-12-31").header("Authorization", rh))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(trend)).hasSize(12); // granularité mensuelle automatique
        mvc.perform(get("/api/dashboard/heatmap").param("from", "2025-04-01").param("to", "2025-04-30").header("Authorization", rh))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].weekday").exists());
        mvc.perform(get("/api/dashboard/arrivals").header("Authorization", rh))
                .andExpect(status().isOk()).andExpect(jsonPath("$.arrivalTimes.length()").value(21));
    }

    @Test
    void employeeCalendarAndDayTimeline() throws Exception {
        String cal = mvc.perform(get("/api/employees/" + y + "/calendar").param("month", "2024-04").header("Authorization", rh))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode days = json.readTree(cal);
        assertThat(days).hasSize(30);
        assertThat(days.get(9).get("kind").asText()).isEqualTo("FERIE"); // 10/04/2024 Aïd el-Fitr
        mvc.perform(get("/api/employees/" + y + "/days/2024-01-02").header("Authorization", rh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.punches[0].time").value("07:55:09"))
                .andExpect(jsonPath("$.punches[0].direction").value("IN"))
                .andExpect(jsonPath("$.punches[0].terminal").value("TB"));
    }

    @Test
    void leaveDeclaredByRhTurnsAbsenceIntoLeave() throws Exception {
        // 2024-01-03 : jour ouvré sans pointage pour XXXXXX (1re présence le 04/01)
        Long x = jdbc.queryForObject("SELECT id FROM employee WHERE code = 'XXXXXX'", Long.class);
        assertThat(jdbc.queryForObject("SELECT status FROM daily_attendance WHERE employee_id = ? AND work_date = '2024-01-03'", String.class, x))
                .isEqualTo("ABSENCE_PONCTUELLE");
        String created = mvc.perform(post("/api/admin/leaves").header("Authorization", rh).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":" + x + ",\"startDate\":\"2024-01-02\",\"endDate\":\"2024-01-03\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(jdbc.queryForObject("SELECT status FROM daily_attendance WHERE employee_id = ? AND work_date = '2024-01-03'", String.class, x))
                .isEqualTo("CONGE");
        mvc.perform(delete("/api/admin/leaves/" + json.readTree(created).get("id").asLong()).header("Authorization", rh))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM daily_attendance WHERE employee_id = ? AND work_date = '2024-01-03'", String.class, x))
                .isEqualTo("ABSENCE_PONCTUELLE");
    }
}
