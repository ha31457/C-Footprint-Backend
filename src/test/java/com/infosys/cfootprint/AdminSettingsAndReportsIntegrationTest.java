package com.infosys.cfootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infosys.cfootprint.dto.LoginRequest;
import com.infosys.cfootprint.dto.SignupRequest;
import com.infosys.cfootprint.dto.VerifyEmailRequest;
import com.infosys.cfootprint.model.OtpToken;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.OtpTokenRepository;
import com.infosys.cfootprint.repository.UserRepository;
import com.infosys.cfootprint.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminSettingsAndReportsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpTokenRepository otpTokenRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    private String userToken;
    private String adminToken;

    @BeforeEach
    public void setup() throws Exception {
        otpTokenRepository.deleteAll();
        userRepository.findAll().forEach(user -> {
            if (!user.getEmail().equals("admin@cfootprint.com")) {
                userRepository.delete(user);
            }
        });

        // 1. Regular User Token
        userToken = registerAndVerify("regularuser", "regular@cfootprint.com");

        // 2. Admin Login Token
        LoginRequest login = new LoginRequest();
        login.setUsernameOrEmail("admin@cfootprint.com");
        login.setPassword("admin123");

        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        String resStr = loginRes.getResponse().getContentAsString();
        Map<?, ?> map = objectMapper.readValue(resStr, Map.class);
        adminToken = (String) map.get("accessToken");
    }

    private String registerAndVerify(String username, String email) throws Exception {
        SignupRequest signup = new SignupRequest();
        signup.setUsername(username);
        signup.setEmail(email);
        signup.setPassword("password123");
        signup.setMobileNumber("+1234567890");
        signup.setAge(25);
        signup.setGender("Male");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername(username).orElseThrow();
        OtpToken otpToken = otpTokenRepository.findByUserAndPurpose(user, "EMAIL_VERIFICATION").orElseThrow();

        VerifyEmailRequest verifyEmailRequest = new VerifyEmailRequest();
        verifyEmailRequest.setEmail(email);
        verifyEmailRequest.setOtp(otpToken.getOtp());

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyEmailRequest)))
                .andExpect(status().isOk());

        LoginRequest login = new LoginRequest();
        login.setUsernameOrEmail(username);
        login.setPassword("password123");

        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andReturn();
        String resStr = loginRes.getResponse().getContentAsString();
        Map<?, ?> map = objectMapper.readValue(resStr, Map.class);
        return (String) map.get("accessToken");
    }

    @Test
    public void testSettingsFeatureTogglingAndEndpointsBlockage() throws Exception {
        // 1. Get current public settings (should show true defaults)
        mockMvc.perform(get("/api/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderboard_enabled", is(true)))
                .andExpect(jsonPath("$.badges_enabled", is(true)))
                .andExpect(jsonPath("$.google_signin_enabled", is(true)));

        // 2. Disable leaderboard and badges as admin
        Map<String, Boolean> updateReq = new HashMap<>();
        updateReq.put("leaderboard_enabled", false);
        updateReq.put("badges_enabled", false);

        mockMvc.perform(put("/api/admin/settings")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderboard_enabled", is(false)))
                .andExpect(jsonPath("$.badges_enabled", is(false)));

        // 3. User attempts to fetch leaderboard -> Expect 400 Bad Request
        mockMvc.perform(get("/api/leaderboard")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Community leaderboard is disabled")));

        // 4. User attempts to fetch badges -> Expect 400 Bad Request
        mockMvc.perform(get("/api/badges")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Badges system is disabled")));

        // 5. Restore features
        updateReq.put("leaderboard_enabled", true);
        updateReq.put("badges_enabled", true);
        mockMvc.perform(put("/api/admin/settings")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());
    }

    @Test
    public void testAdminReportsExport() throws Exception {
        // 1. Fetch report JSON summary
        mockMvc.perform(get("/api/admin/reports/summary")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", is(notNullValue())))
                .andExpect(jsonPath("$.totalLogs", is(notNullValue())));

        // 2. Export PDF
        mockMvc.perform(get("/api/admin/reports/export")
                .header("Authorization", "Bearer " + adminToken)
                .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        // 3. Export Word
        mockMvc.perform(get("/api/admin/reports/export")
                .header("Authorization", "Bearer " + adminToken)
                .param("format", "word"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));

        // 4. Export CSV
        mockMvc.perform(get("/api/admin/reports/export")
                .header("Authorization", "Bearer " + adminToken)
                .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"));
    }
}
