package com.formswim.teststream.bulk;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.formswim.teststream.auth.model.AppUser;
import com.formswim.teststream.auth.repository.UserRepository;
import com.formswim.teststream.shared.domain.TestCaseRepository;

@SpringBootTest(properties = {
    "teststream.bulk-edit.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceBulkEditEnabledIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TestCaseRepository testCaseRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        testCaseRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(new AppUser("team1.user@example.com", passwordEncoder.encode("Password123"), "TEAM1"));
    }

    @Test
    void workspaceRendersBulkEditUiWhenFeatureFlagEnabled() throws Exception {
        mockMvc.perform(get("/workspace")
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"workspace-bulk-edit-enabled\" content=\"true\"")))
            .andExpect(content().string(containsString("id=\"bulkEditOpen\"")))
            .andExpect(content().string(containsString("id=\"bulkEditModal\"")))
            .andExpect(content().string(containsString("items selected on this page")))
            .andExpect(content().string(containsString("current-page selection")))
            .andExpect(content().string(containsString("selected cases on the current page")));
    }
}
