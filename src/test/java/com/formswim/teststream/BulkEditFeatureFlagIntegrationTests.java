package com.formswim.teststream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies rollout gate behavior for bulk-edit endpoint.
 */
@SpringBootTest(properties = {
    "teststream.bulk-edit.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BulkEditFeatureFlagIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void bulkEditReturnsServiceUnavailableWhenFeatureFlagDisabled() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("workKeys", List.of("TC-101"));
        payload.put("findText", "Click");
        payload.put("replaceText", "Tap");

        mockMvc.perform(patch("/api/testcases/bulk-edit")
                .with(csrf())
                .with(user("team1.user@example.com").roles("USER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(payload)))
            .andExpect(status().isServiceUnavailable());
    }
}