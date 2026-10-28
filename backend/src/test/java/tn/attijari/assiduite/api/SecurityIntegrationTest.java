package tn.attijari.assiduite.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tn.attijari.assiduite.AbstractIntegrationTest;
import tn.attijari.assiduite.TestData;
import tn.attijari.assiduite.imports.ImportService;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sécurité par rôle : authentification JWT, périmètre MANAGER filtré côté serveur, traçabilité des consultations. */
@AutoConfigureMockMvc
class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ImportService importService;

    Long teamA, teamB, employeeOfTeamB;

    @BeforeEach
    void setUp() throws Exception {
        TestData.ensureImported(jdbc, importService);
        teamA = jdbc.queryForObject("SELECT id FROM team WHERE name = 'Équipe Études A (fictive)'", Long.class);
        teamB = jdbc.queryForObject("SELECT id FROM team WHERE name = 'Équipe Études B (fictive)'", Long.class);
        employeeOfTeamB = jdbc.queryForObject("SELECT id FROM employee WHERE code = 'VVVVVV'", Long.class);
    }

    String token(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"Demo@2025\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    @Test
    void rejectsMissingTokenAndBadPassword() throws Exception {
        mvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"rh\",\"password\":\"wrong\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void managerSeesOnlyHisTeam() throws Exception {
        String body = mvc.perform(get("/api/employees").param("from", "2025-01-01").param("to", "2025-05-19")
                        .header("Authorization", token("manager.a")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode rows = json.readTree(body);
        Set<String> codes = new HashSet<>();
        rows.forEach(r -> {
            codes.add(r.get("code").asText());
            assertThat(r.get("teamId").asLong()).isEqualTo(teamA);
        });
        assertThat(codes).containsExactlyInAnyOrder("XXXXXX", "YYYYYY", "ZZZZZZ", "TTTTTT");

        mvc.perform(get("/api/dashboard/kpis").header("Authorization", token("manager.a")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.employees").value(4));
    }

    @Test
    void managerCannotReachAnotherTeam() throws Exception {
        String t = token("manager.a");
        mvc.perform(get("/api/employees/" + employeeOfTeamB).header("Authorization", t)).andExpect(status().isForbidden());
        mvc.perform(get("/api/employees/" + employeeOfTeamB + "/calendar").header("Authorization", t)).andExpect(status().isForbidden());
        mvc.perform(get("/api/employees/" + employeeOfTeamB + "/days/2025-05-14").header("Authorization", t)).andExpect(status().isForbidden());
        mvc.perform(get("/api/employees/" + employeeOfTeamB + "/predictions").header("Authorization", t)).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/kpis").param("teamId", teamB.toString()).header("Authorization", t)).andExpect(status().isForbidden());
        mvc.perform(get("/api/predictions/latest").param("teamId", teamB.toString()).header("Authorization", t)).andExpect(status().isForbidden());
    }

    @Test
    void managerCannotUseAdministration() throws Exception {
        String t = token("manager.b");
        mvc.perform(get("/api/admin/holidays").header("Authorization", t)).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/imports").file(new MockMultipartFile("file", "x.xls", "application/vnd.ms-excel", new byte[]{1}))
                .header("Authorization", t)).andExpect(status().isForbidden());
        mvc.perform(get("/api/model/info").header("Authorization", t)).andExpect(status().isForbidden());
    }

    @Test
    void rhSeesEveryoneButOnlyAdminManagesUsers() throws Exception {
        String rh = token("rh");
        String body = mvc.perform(get("/api/employees").header("Authorization", rh)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(body)).hasSize(8);
        mvc.perform(get("/api/admin/holidays").header("Authorization", rh)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").header("Authorization", rh)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").header("Authorization", token("admin"))).andExpect(status().isOk());
    }

    @Test
    void viewingAnEmployeeProfileIsAudited() throws Exception {
        Long before = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'VIEW_EMPLOYEE' AND username = 'manager.b'", Long.class);
        mvc.perform(get("/api/employees/" + employeeOfTeamB).header("Authorization", token("manager.b")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("VVVVVV"));
        Long after = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'VIEW_EMPLOYEE' AND username = 'manager.b'", Long.class);
        assertThat(after).isEqualTo(before + 1);
        assertThat(jdbc.queryForObject("SELECT entity_id FROM audit_log WHERE action = 'VIEW_EMPLOYEE' ORDER BY id DESC LIMIT 1", String.class))
                .isEqualTo(employeeOfTeamB.toString());
    }
}
