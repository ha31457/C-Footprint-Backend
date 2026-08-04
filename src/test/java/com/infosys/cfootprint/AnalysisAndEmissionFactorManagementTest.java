package com.infosys.cfootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infosys.cfootprint.dto.*;
import com.infosys.cfootprint.model.EmissionFactor;
import com.infosys.cfootprint.model.OtpToken;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.ActivityLogRepository;
import com.infosys.cfootprint.repository.EmissionFactorRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AnalysisAndEmissionFactorManagementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpTokenRepository otpTokenRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private EmissionFactorRepository emissionFactorRepository;

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
        signup.setUsername("analysisuser");
        signup.setEmail("analysisuser@cfootprint.com");
        signup.setPassword("password123");
        signup.setMobileNumber("+1234567890");
        signup.setAge(25);
        signup.setGender("Male");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("analysisuser").orElseThrow();
        OtpToken otpToken = otpTokenRepository.findByUserAndPurpose(user, "EMAIL_VERIFICATION").orElseThrow();

        VerifyEmailRequest verifyEmailRequest = new VerifyEmailRequest();
        verifyEmailRequest.setEmail("analysisuser@cfootprint.com");
        verifyEmailRequest.setOtp(otpToken.getOtp());

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyEmailRequest)))
                .andExpect(status().isOk());

        LoginRequest userLogin = new LoginRequest();
        userLogin.setUsernameOrEmail("analysisuser");
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
    public void testEmissionFactorCrudAndAnalysis() throws Exception {
        // 1. Admin reads factors
        mockMvc.perform(get("/api/admin/emission-factors")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", notNullValue()));

        // 2. Admin creates a new emission factor: category: recycling, type: PLASTIC_BOTTLE, factor: 0.05, unit: count
        CreateEmissionFactorRequest createRequest = new CreateEmissionFactorRequest();
        createRequest.setCategory("recycling");
        createRequest.setActivityType("PLASTIC_BOTTLE");
        createRequest.setFactor(0.05);
        createRequest.setUnit("count");

        MvcResult createRes = mockMvc.perform(post("/api/admin/emission-factors")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category", is("recycling")))
                .andExpect(jsonPath("$.activityType", is("PLASTIC_BOTTLE")))
                .andExpect(jsonPath("$.factor", is(0.05)))
                .andReturn();

        String createResStr = createRes.getResponse().getContentAsString();
        EmissionFactor factor = objectMapper.readValue(createResStr, EmissionFactor.class);
        UUID factorId = factor.getId();

        // 3. Admin updates the factor: factor: 0.08, unit: units
        UpdateEmissionFactorRequest updateRequest = new UpdateEmissionFactorRequest();
        updateRequest.setFactor(0.08);
        updateRequest.setUnit("units");

        mockMvc.perform(put("/api/admin/emission-factors/" + factorId)
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.factor", is(0.08)))
                .andExpect(jsonPath("$.unit", is("units")));

        // 4. User logs activities to verify wrapper sum and user analysis
        LocalDate today = LocalDate.now();

        ActivityLogRequest log1 = new ActivityLogRequest();
        log1.setCategory("transport");
        log1.setActivityType("CAR_GASOLINE");
        log1.setQuantity(10.0); // 10 * 0.18 = 1.8 kg
        log1.setUnit("km");
        log1.setLogDate(today);

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(log1)))
                .andExpect(status().isOk());

        ActivityLogRequest log2 = new ActivityLogRequest();
        log2.setCategory("transport");
        log2.setActivityType("CAR_GASOLINE");
        log2.setQuantity(20.0); // 20 * 0.18 = 3.6 kg
        log2.setUnit("km");
        log2.setLogDate(today);

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(log2)))
                .andExpect(status().isOk());

        // 5. User requests filtered list -> verify totalCo2Emission = 5.4 kg
        mockMvc.perform(get("/api/activities")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities", hasSize(2)))
                .andExpect(jsonPath("$.totalCo2Emission", is(5.4)));

        // 6. User calls analysis -> verify dashboard recommendations and top categories
        mockMvc.perform(get("/api/analysis")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLogs", is(2)))
                .andExpect(jsonPath("$.mostLoggedCategory", is("transport")))
                .andExpect(jsonPath("$.highestEmissionCategory", is("transport")))
                .andExpect(jsonPath("$.tips", hasItem(containsString("Consider public transit"))))
                .andExpect(jsonPath("$.trend", notNullValue()));

        // 7. Admin calls analysis -> verify platform metrics
        mockMvc.perform(get("/api/admin/analysis")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", is(1)))
                .andExpect(jsonPath("$.totalLogs", is(2)))
                .andExpect(jsonPath("$.averageEmissionPerUser", is(5.4)))
                .andExpect(jsonPath("$.highestEmissionCategory", is("transport")));

        // 8. Admin deletes the emission factor
        mockMvc.perform(delete("/api/admin/emission-factors/" + factorId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }
}
