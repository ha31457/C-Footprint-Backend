package com.infosys.cfootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infosys.cfootprint.dto.*;
import com.infosys.cfootprint.model.OtpToken;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.*;
import com.infosys.cfootprint.service.EmailService;
import com.infosys.cfootprint.service.ScheduledNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class NotificationSystemIntegrationTest {

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
    private NotificationRepository notificationRepository;

    @Autowired
    private ScheduledNotificationService scheduledNotificationService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    private String userToken;

    @BeforeEach
    public void setup() throws Exception {
        goalRepository.deleteAll();
        activityLogRepository.deleteAll();
        notificationRepository.deleteAll();
        otpTokenRepository.deleteAll();
        userRepository.findAll().forEach(user -> {
            if (!user.getEmail().equals("admin@cfootprint.com")) {
                userRepository.delete(user);
            }
        });

        userToken = registerAndVerify("notifyuser", "notifyuser@cfootprint.com");
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
    public void testGoalNotificationsAndMonthlyAnalytics() throws Exception {
        // 1. Log baseline activity first so we have some baseline emissions
        ActivityLogRequest initialLog = new ActivityLogRequest();
        initialLog.setCategory("transport");
        initialLog.setActivityType("CAR_GASOLINE");
        initialLog.setQuantity(100.0); // 100 * 0.18 = 18 kg CO2
        initialLog.setUnit("km");
        initialLog.setLogDate(LocalDate.now().minusDays(3));

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(initialLog)))
                .andExpect(status().isOk());

        // 2. Create goal -> verifies confirmation email is triggered
        CreateGoalRequest createGoal = new CreateGoalRequest();
        createGoal.setTargetReductionPercentage(20.0);
        createGoal.setPeriodType("WEEKLY");

        mockMvc.perform(post("/api/goals")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createGoal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ACTIVE")));

        // Verify confirmation email was sent
        verify(emailService).sendHtmlEmail(eq("notifyuser@cfootprint.com"), eq("New Carbon Reduction Goal Set!"), anyString());

        // Reset mock to check warning email next
        Mockito.clearInvocations(emailService);

        // 3. Log activity to consume over 80% of budget limit (target emission limit: 14.4 kg)
        // Log 70 km of gasoline car -> 70 * 0.18 = 12.6 kg CO2
        // 12.6 / 14.4 = 87.5% which is above the 80% warning limit
        ActivityLogRequest logWarning = new ActivityLogRequest();
        logWarning.setCategory("transport");
        logWarning.setActivityType("CAR_GASOLINE");
        logWarning.setQuantity(70.0);
        logWarning.setUnit("km");
        logWarning.setLogDate(LocalDate.now());

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logWarning)))
                .andExpect(status().isOk());

        // Verify warning email was sent
        verify(emailService).sendHtmlEmail(eq("notifyuser@cfootprint.com"), eq("Warning: Approaching Carbon Budget Limit!"), anyString());

        // Reset mock
        Mockito.clearInvocations(emailService);

        // 4. Trigger monthly scheduled report manually
        scheduledNotificationService.sendMonthlyAnalytics();

        // Verify monthly analytics report email was sent
        verify(emailService, atLeastOnce()).sendHtmlEmail(eq("notifyuser@cfootprint.com"), eq("Your Monthly Carbon Footprint Analytics Report"), anyString());

        // 5. Verify In-App Notifications
        mockMvc.perform(get("/api/notifications")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].type", anyOf(is("GOAL_WARNING"), is("ACTIVITY_LOGGED"), is("GOAL_CREATED"))));

        // Get notification ID from database to test read
        java.util.UUID notificationId = notificationRepository.findAll().get(0).getId();

        // 6. Mark single notification as read
        mockMvc.perform(put("/api/notifications/" + notificationId + "/read")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read", is(true)));

        // 7. Mark all notifications as read
        mockMvc.perform(put("/api/notifications/read-all")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk());
    }
}
