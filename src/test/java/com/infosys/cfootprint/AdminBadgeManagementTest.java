package com.infosys.cfootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infosys.cfootprint.dto.*;
import com.infosys.cfootprint.model.BadgeDefinition;
import com.infosys.cfootprint.model.OtpToken;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.*;
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

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AdminBadgeManagementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpTokenRepository otpTokenRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private BadgeRepository badgeRepository;

    @Autowired
    private BadgeDefinitionRepository badgeDefinitionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    private String adminToken;
    private String userToken;

    @BeforeEach
    public void setup() throws Exception {
        badgeRepository.deleteAll();
        // Delete all definitions except initial seeded ones to prevent key conflicts
        badgeDefinitionRepository.findAll().forEach(def -> {
            if (!def.getBadgeType().startsWith("FIRST_LOG") && 
                !def.getBadgeType().startsWith("DIVERSE_LOGS") &&
                !def.getBadgeType().startsWith("THREE_GOALS") &&
                !def.getBadgeType().startsWith("GOAL_ACHIEVED") &&
                !def.getBadgeType().startsWith("CARBON_CUTTER_50") &&
                !def.getBadgeType().startsWith("LEADERBOARD_TOP_3")) {
                badgeDefinitionRepository.delete(def);
            }
        });
        activityLogRepository.deleteAll();
        otpTokenRepository.deleteAll();
        userRepository.findAll().forEach(user -> {
            if (!user.getEmail().equals("admin@cfootprint.com")) {
                userRepository.delete(user);
            }
        });

        adminToken = getAdminToken();
        userToken = registerAndVerify("badgeuser", "badgeuser@cfootprint.com");
    }

    private String getAdminToken() throws Exception {
        LoginRequest login = new LoginRequest();
        login.setUsernameOrEmail("admin@cfootprint.com");
        login.setPassword("adminpassword");

        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();
        String resStr = loginRes.getResponse().getContentAsString();
        Map<?, ?> map = objectMapper.readValue(resStr, Map.class);
        return (String) map.get("accessToken");
    }

    private String registerAndVerify(String username, String email) throws Exception {
        SignupRequest signup = new SignupRequest();
        signup.setUsername(username);
        signup.setEmail(email);
        signup.setPassword("password123");
        signup.setMobileNumber("+1234567890");
        signup.setAge(25);
        signup.setGender("Female");

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
                .andExpect(status().isOk())
                .andReturn();
        String resStr = loginRes.getResponse().getContentAsString();
        Map<?, ?> map = objectMapper.readValue(resStr, Map.class);
        return (String) map.get("accessToken");
    }

    @Test
    public void testAdminBadgeCRUDAndLeaderboardDiagnostics() throws Exception {
        // 1. Create a new badge definition via Admin
        CreateBadgeDefinitionRequest request = new CreateBadgeDefinitionRequest();
        request.setBadgeType("FIVE_LOGS");
        request.setTitle("Super Logger");
        request.setDescription("Log at least 5 activities.");
        request.setIconName("SuperLogger");
        request.setIconUrl("https://api.dicebear.com/7.x/identicon/svg?seed=SuperLogger");
        request.setRuleType("LOG_COUNT");
        request.setRuleValue(5.0);

        MvcResult createRes = mockMvc.perform(post("/api/admin/badges")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.badgeType", is("FIVE_LOGS")))
                .andReturn();

        BadgeDefinition definition = objectMapper.readValue(createRes.getResponse().getContentAsString(), BadgeDefinition.class);

        // 2. Edit badge definition
        request.setTitle("Mega Logger");
        mockMvc.perform(put("/api/admin/badges/" + definition.getId())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Mega Logger")));

        // 3. User logs 5 activities to trigger the new badge
        LocalDate today = LocalDate.now();
        for (int i = 0; i < 5; i++) {
            ActivityLogRequest logReq = new ActivityLogRequest();
            logReq.setCategory("transport");
            logReq.setActivityType("CAR_GASOLINE");
            logReq.setQuantity(10.0);
            logReq.setUnit("km");
            logReq.setLogDate(today);

            mockMvc.perform(post("/api/activities")
                    .header("Authorization", "Bearer " + userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(logReq)))
                    .andExpect(status().isOk());
        }

        // 4. Verify user has earned the new dynamic badge definition
        mockMvc.perform(get("/api/badges")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(7)))
                .andExpect(jsonPath("$[6].badgeType", is("FIVE_LOGS")))
                .andExpect(jsonPath("$[6].locked", is(false)));

        // 5. Admin fetches detailed diagnostic leaderboard
        mockMvc.perform(get("/api/admin/leaderboard")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email", is("badgeuser@cfootprint.com")))
                .andExpect(jsonPath("$[0].totalLogsCount", is(5)));

        // 6. Delete badge definition
        mockMvc.perform(delete("/api/admin/badges/" + definition.getId())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 7. Verify badge is deleted from user
        mockMvc.perform(get("/api/badges")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)));
    }
}
