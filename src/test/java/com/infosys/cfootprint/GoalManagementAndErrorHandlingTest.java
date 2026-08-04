package com.infosys.cfootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infosys.cfootprint.dto.*;
import com.infosys.cfootprint.model.OtpToken;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.ActivityLogRepository;
import com.infosys.cfootprint.repository.GoalRepository;
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

import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class GoalManagementAndErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpTokenRepository otpTokenRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    private String userToken;

    @BeforeEach
    public void setup() throws Exception {
        goalRepository.deleteAll();
        activityLogRepository.deleteAll();
        otpTokenRepository.deleteAll();
        userRepository.findAll().forEach(user -> {
            if (!user.getEmail().equals("admin@cfootprint.com")) {
                userRepository.delete(user);
            }
        });

        userToken = registerAndVerify("goaluser", "goaluser@cfootprint.com");
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
                .andExpect(status().isOk())
                .andReturn();
        String resStr = loginRes.getResponse().getContentAsString();
        Map<?, ?> map = objectMapper.readValue(resStr, Map.class);
        return (String) map.get("accessToken");
    }

    @Test
    public void testSequentialLoginsNoConflict() throws Exception {
        LoginRequest login = new LoginRequest();
        login.setUsernameOrEmail("goaluser");
        login.setPassword("password123");

        // Execute multiple logins sequentially -> verify no duplicate token constraint exceptions occur
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(login)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken", notNullValue()));
        }
    }

    @Test
    public void testGoalManagementAlertsAndTotalAllTimeEmissions() throws Exception {
        LocalDate today = LocalDate.now();

        // 1. User sets a 20% weekly reduction goal
        CreateGoalRequest createGoalRequest = new CreateGoalRequest();
        createGoalRequest.setTargetReductionPercentage(20.0);
        createGoalRequest.setPeriodType("WEEKLY");

        mockMvc.perform(post("/api/goals")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createGoalRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.periodType", is("WEEKLY")))
                .andExpect(jsonPath("$.targetReductionPercentage", is(20.0)))
                .andExpect(jsonPath("$.onTrack", is(true)));

        // 2. Fetch active goal
        mockMvc.perform(get("/api/goals/active")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status", is("ACTIVE")))
                .andExpect(jsonPath("$[0].alertMessage", containsString("On Track")));

        // 3. User logs a high emission activity (100 kg CO2)
        ActivityLogRequest highLog = new ActivityLogRequest();
        highLog.setCategory("other");
        highLog.setActivityType("HIGH_EMITTING_ACTIVITY");
        highLog.setQuantity(200.0); // 200 * 0.5 = 100 kg CO2
        highLog.setUnit("kg");
        highLog.setLogDate(today);

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(highLog)))
                .andExpect(status().isOk());

        // 4. Verify Total All-Time Emission on Dashboard
        mockMvc.perform(get("/api/dashboard")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAllTimeEmission", is(100.0)));

        // 5. Verify Total All-Time Emission on Analysis
        mockMvc.perform(get("/api/analysis")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAllTimeEmission", is(100.0)));

        // 6. Active goal now projects off-track and emits Correction Alert
        mockMvc.perform(get("/api/goals/active")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].currentEmission", is(100.0)))
                .andExpect(jsonPath("$[0].onTrack", is(false)))
                .andExpect(jsonPath("$[0].alertMessage", containsString("Correction Alert")));
    }
}
