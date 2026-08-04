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
public class ActivityFiltersAndCustomLoggingTest {

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
        signup.setUsername("filteruser");
        signup.setEmail("filteruser@cfootprint.com");
        signup.setPassword("password123");
        signup.setMobileNumber("+1234567890");
        signup.setAge(25);
        signup.setGender("Male");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("filteruser").orElseThrow();
        OtpToken otpToken = otpTokenRepository.findByUserAndPurpose(user, "EMAIL_VERIFICATION").orElseThrow();

        VerifyEmailRequest verifyEmailRequest = new VerifyEmailRequest();
        verifyEmailRequest.setEmail("filteruser@cfootprint.com");
        verifyEmailRequest.setOtp(otpToken.getOtp());

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyEmailRequest)))
                .andExpect(status().isOk());

        LoginRequest userLogin = new LoginRequest();
        userLogin.setUsernameOrEmail("filteruser");
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
    public void testCustomLoggingFallbackAndFilters() throws Exception {
        LocalDate today = LocalDate.now();

        // 1. Log custom activity in "other" category: custom type, quantity: 100 km (fallback factor: 0.15 -> expected: 15.0 kg)
        ActivityLogRequest customLog = new ActivityLogRequest();
        customLog.setCategory("other");
        customLog.setActivityType("MY_CUSTOM_DRIVE");
        customLog.setQuantity(100.0);
        customLog.setUnit("km");
        customLog.setLogDate(today);

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(customLog)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.co2Emission", is(15.0)))
                .andExpect(jsonPath("$.category", is("other")))
                .andExpect(jsonPath("$.activityType", is("MY_CUSTOM_DRIVE")));

        // 2. Log activity for 5 DAYS AGO: MEAL_MEAT (2 servings -> 5.0 kg)
        ActivityLogRequest logFiveDaysAgo = new ActivityLogRequest();
        logFiveDaysAgo.setCategory("food");
        logFiveDaysAgo.setActivityType("MEAL_MEAT");
        logFiveDaysAgo.setQuantity(2.0);
        logFiveDaysAgo.setUnit("servings");
        logFiveDaysAgo.setLogDate(today.minusDays(5));

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logFiveDaysAgo)))
                .andExpect(status().isOk());

        // 3. Log activity for 45 DAYS AGO: CAR_GASOLINE (100 km -> 18.0 kg)
        ActivityLogRequest logFortyFiveDaysAgo = new ActivityLogRequest();
        logFortyFiveDaysAgo.setCategory("transport");
        logFortyFiveDaysAgo.setActivityType("CAR_GASOLINE");
        logFortyFiveDaysAgo.setQuantity(100.0);
        logFortyFiveDaysAgo.setUnit("km");
        logFortyFiveDaysAgo.setLogDate(today.minusDays(45));

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logFortyFiveDaysAgo)))
                .andExpect(status().isOk());

        // 4. Test user filter: category=other -> returns only custom log (size 1)
        mockMvc.perform(get("/api/activities?category=other")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].category", is("other")));

        // 5. Test user filter: range=weekly -> returns 2 logs (today + 5 days ago)
        mockMvc.perform(get("/api/activities?range=weekly")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // 6. Test user filter: date range (from 50 days ago to 10 days ago) -> returns 1 log (45 days ago)
        mockMvc.perform(get("/api/activities?startDate=" + today.minusDays(50).toString() + "&endDate=" + today.minusDays(10).toString())
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].category", is("transport")));

        // 7. Test Admin filter by category: category=food -> returns 1 log
        mockMvc.perform(get("/api/admin/activities/list?category=food")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].category", is("food")));
    }
}
