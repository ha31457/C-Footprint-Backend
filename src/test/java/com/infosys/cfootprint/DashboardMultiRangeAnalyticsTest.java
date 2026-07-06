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
public class DashboardMultiRangeAnalyticsTest {

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
        signup.setUsername("trenduser");
        signup.setEmail("trenduser@cfootprint.com");
        signup.setPassword("password123");
        signup.setMobileNumber("+1234567890");
        signup.setAge(25);
        signup.setGender("Male");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("trenduser").orElseThrow();
        OtpToken otpToken = otpTokenRepository.findByUserAndPurpose(user, "EMAIL_VERIFICATION").orElseThrow();

        VerifyEmailRequest verifyEmailRequest = new VerifyEmailRequest();
        verifyEmailRequest.setEmail("trenduser@cfootprint.com");
        verifyEmailRequest.setOtp(otpToken.getOtp());

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyEmailRequest)))
                .andExpect(status().isOk());

        LoginRequest userLogin = new LoginRequest();
        userLogin.setUsernameOrEmail("trenduser");
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
    public void testMultiRangeDashboardAnalytics() throws Exception {
        LocalDate today = LocalDate.now();

        // 1. Log transport activity for TODAY: CAR_GASOLINE (10 km -> 1.8 kg CO2e)
        ActivityLogRequest logToday = new ActivityLogRequest();
        logToday.setCategory("transport");
        logToday.setActivityType("CAR_GASOLINE");
        logToday.setQuantity(10.0);
        logToday.setUnit("km");
        logToday.setLogDate(today);

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logToday)))
                .andExpect(status().isOk());

        // 2. Log transport activity for LAST WEEK (e.g. 10 days ago): CAR_GASOLINE (20 km -> 3.6 kg CO2e)
        ActivityLogRequest logLastWeek = new ActivityLogRequest();
        logLastWeek.setCategory("transport");
        logLastWeek.setActivityType("CAR_GASOLINE");
        logLastWeek.setQuantity(20.0);
        logLastWeek.setUnit("km");
        logLastWeek.setLogDate(today.minusDays(10));

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logLastWeek)))
                .andExpect(status().isOk());

        // 3. Log transport activity for LAST MONTH (e.g. 45 days ago): CAR_GASOLINE (30 km -> 5.4 kg CO2e)
        ActivityLogRequest logLastMonth = new ActivityLogRequest();
        logLastMonth.setCategory("transport");
        logLastMonth.setActivityType("CAR_GASOLINE");
        logLastMonth.setQuantity(30.0);
        logLastMonth.setUnit("km");
        logLastMonth.setLogDate(today.minusDays(45));

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logLastMonth)))
                .andExpect(status().isOk());

        // 4. Test User Dashboard daily range
        mockMvc.perform(get("/api/dashboard?range=daily")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend", hasSize(7)))
                .andExpect(jsonPath("$.trend[?(@.label=='" + today.toString() + "')].co2Emission", contains(1.8)));

        // 5. Test User Dashboard weekly range
        mockMvc.perform(get("/api/dashboard?range=weekly")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend", hasSize(4)));

        // 6. Test User Dashboard monthly range
        mockMvc.perform(get("/api/dashboard?range=monthly")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend", hasSize(6)));

        // 7. Test User Dashboard yearly range
        mockMvc.perform(get("/api/dashboard?range=yearly")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend", hasSize(3)));

        // 8. Test Admin activities range=monthly
        mockMvc.perform(get("/api/admin/activities?range=monthly")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trend", hasSize(6)));
    }
}
