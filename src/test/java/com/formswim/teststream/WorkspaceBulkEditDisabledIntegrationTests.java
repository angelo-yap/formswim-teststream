package com.formswim.teststream;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
import com.formswim.teststream.etl.repository.TestCaseRepository;

@SpringBootTest(properties = {
    "teststream.bulk-edit.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkspaceBulkEditDisabledIntegrationTests {

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
    void workspaceOmitsBulkEditUiWhenFeatureFlagDisabled() throws Exception {
        mockMvc.perform(get("/workspace")
                .with(user("team1.user@example.com").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("name=\"workspace-bulk-edit-enabled\" content=\"false\"")))
            .andExpect(content().string(not(containsString("id=\"bulkEditOpen\""))));
    }
}
