package com.infosys.cfootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infosys.cfootprint.dto.ActivityLogRequest;
import com.infosys.cfootprint.dto.LoginRequest;
import com.infosys.cfootprint.dto.SignupRequest;
import com.infosys.cfootprint.dto.VerifyEmailRequest;
import com.infosys.cfootprint.model.OtpToken;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.ActivityLogRepository;
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
public class ActivityLoggingAndAnalyticsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpTokenRepository otpTokenRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    private String userToken;
    private String adminToken;

    @BeforeEach
    public void setup() throws Exception {
        activityLogRepository.deleteAll();
        otpTokenRepository.deleteAll();
        userRepository.findAll().forEach(user -> {
            if (!user.getEmail().equals("admin@cfootprint.com")) {
                userRepository.delete(user);
            }
        });

        // 1. Setup Admin Token
        LoginRequest adminLogin = new LoginRequest();
        adminLogin.setUsernameOrEmail("admin@cfootprint.com");
        adminLogin.setPassword("adminpassword");

        MvcResult adminLoginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminLogin)))
                .andReturn();
        String adminResStr = adminLoginRes.getResponse().getContentAsString();
        Map<?, ?> adminMap = objectMapper.readValue(adminResStr, Map.class);
        adminToken = (String) adminMap.get("accessToken");

        // 2. Setup verified User
        SignupRequest signup = new SignupRequest();
        signup.setUsername("user1");
        signup.setEmail("user1@cfootprint.com");
        signup.setPassword("password123");
        signup.setMobileNumber("+1234567890");
        signup.setAge(25);
        signup.setGender("Male");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("user1").orElseThrow();
        OtpToken otpToken = otpTokenRepository.findByUserAndPurpose(user, "EMAIL_VERIFICATION").orElseThrow();

        VerifyEmailRequest verifyEmailRequest = new VerifyEmailRequest();
        verifyEmailRequest.setEmail("user1@cfootprint.com");
        verifyEmailRequest.setOtp(otpToken.getOtp());

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyEmailRequest)))
                .andExpect(status().isOk());

        LoginRequest userLogin = new LoginRequest();
        userLogin.setUsernameOrEmail("user1");
        userLogin.setPassword("password123");

        MvcResult userLoginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userLogin)))
                .andReturn();
        String userResStr = userLoginRes.getResponse().getContentAsString();
        Map<?, ?> userMap = objectMapper.readValue(userResStr, Map.class);
        userToken = (String) userMap.get("accessToken");
    }

    @Test
    public void testActivityLoggingAndCalculations() throws Exception {
        // 1. Log activity: CAR_GASOLINE, quantity: 10 km (factor: 0.18 -> expected: 1.8 kg)
        ActivityLogRequest request = new ActivityLogRequest();
        request.setCategory("transport");
        request.setActivityType("CAR_GASOLINE");
        request.setQuantity(10.0);
        request.setUnit("km");
        request.setLogDate(LocalDate.now());

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.co2Emission", is(1.8)))
                .andExpect(jsonPath("$.category", is("transport")))
                .andExpect(jsonPath("$.activityType", is("CAR_GASOLINE")));

        // 2. Fetch logged activities
        mockMvc.perform(get("/api/activities")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].co2Emission", is(1.8)));
    }

    @Test
    public void testUserDashboardData() throws Exception {
        // Log transport: 1.8 kg
        ActivityLogRequest transport = new ActivityLogRequest();
        transport.setCategory("transport");
        transport.setActivityType("CAR_GASOLINE");
        transport.setQuantity(10.0);
        transport.setUnit("km");
        transport.setLogDate(LocalDate.now());

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transport)));

        // Log food: servings: 2 * MEAL_MEAT (2.5 -> expected: 5.0 kg)
        ActivityLogRequest food = new ActivityLogRequest();
        food.setCategory("food");
        food.setActivityType("MEAL_MEAT");
        food.setQuantity(2.0);
        food.setUnit("servings");
        food.setLogDate(LocalDate.now());

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(food)));

        // 3. Fetch Dashboard data
        mockMvc.perform(get("/api/dashboard")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.todayTotalEmission", is(6.8)))
                .andExpect(jsonPath("$.categoryBreakdown", hasSize(4)))
                .andExpect(jsonPath("$.categoryBreakdown[?(@.category=='transport')].co2Emission", contains(1.8)))
                .andExpect(jsonPath("$.categoryBreakdown[?(@.category=='food')].co2Emission", contains(5.0)))
                .andExpect(jsonPath("$.categoryBreakdown[?(@.category=='transport')].percentage", contains(26.47)))
                .andExpect(jsonPath("$.categoryBreakdown[?(@.category=='food')].percentage", contains(73.53)))
                .andExpect(jsonPath("$.weeklyTrend", hasSize(7)));
    }

    @Test
    public void testAdminSecurityAndPlatformAnalytics() throws Exception {
        // Log transport log for user: 1.8 kg
        ActivityLogRequest transport = new ActivityLogRequest();
        transport.setCategory("transport");
        transport.setActivityType("CAR_GASOLINE");
        transport.setQuantity(10.0);
        transport.setUnit("km");
        transport.setLogDate(LocalDate.now());

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(transport)));

        // 1. Regular user tries to access admin stats -> should be Forbidden (403)
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        // 2. Admin access user analytics -> should succeed
        mockMvc.perform(get("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", is(2))) // Admin + user1
                .andExpect(jsonPath("$.enabledUsers", is(2)));

        // 3. Admin access activity analytics -> should succeed
        mockMvc.perform(get("/api/admin/activities")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLogs", is(1)))
                .andExpect(jsonPath("$.logsLoggedToday", is(1)))
                .andExpect(jsonPath("$.totalCo2EmissionKgs", is(1.8)));
    }
}
