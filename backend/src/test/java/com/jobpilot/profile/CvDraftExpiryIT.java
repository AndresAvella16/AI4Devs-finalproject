package com.jobpilot.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jobpilot.auth.service.TokenService;
import com.jobpilot.support.PostgresTestcontainer;
import com.jobpilot.support.TestPdfs;
import com.jobpilot.support.TestRows;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

/**
 * El borrador del CV caduca (JP-BE-04): con una caducidad de 1 ms, cuando llega el {@code PUT /profile} el borrador
 * ya no existe y el perfil se guarda igualmente, sin {@code cv_raw_text} ni {@code cv_file_name} y sin error.
 */
@SpringBootTest(properties = "jobpilot.cv-draft.ttl=1ms")
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class CvDraftExpiryIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TokenService tokens;

    @Test
    void siElBorradorHaCaducadoElPerfilSeGuardaSinElCv() throws Exception {
        UUID userId = new TestRows(jdbc).user();
        String token = "Bearer " + tokens.issue(userId).value();
        MockMultipartFile cv = new MockMultipartFile("file", "cv.pdf", MediaType.APPLICATION_PDF_VALUE,
                TestPdfs.sampleCv());

        mockMvc.perform(multipart("/api/v1/profile/cv").file(cv).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cvFileName").value("cv.pdf"));

        mockMvc.perform(put("/api/v1/profile").header(HttpHeaders.AUTHORIZATION, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"headline\": \"Java Junior\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.headline").value("Java Junior"))
                .andExpect(jsonPath("$.cvFileName").value(nullValue()));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM profiles WHERE user_id = ?", userId);
        assertThat(row.get("cv_raw_text")).isNull();
        assertThat(row.get("cv_file_name")).isNull();
    }
}
