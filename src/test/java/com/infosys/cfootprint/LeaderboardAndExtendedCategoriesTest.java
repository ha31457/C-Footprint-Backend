package com.infosys.cfootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infosys.cfootprint.dto.*;
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
public class LeaderboardAndExtendedCategoriesTest {

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

    private String userAToken;
    private String userBToken;

    @BeforeEach
    public void setup() throws Exception {
        activityLogRepository.deleteAll();
        otpTokenRepository.deleteAll();
        userRepository.findAll().forEach(user -> {
            if (!user.getEmail().equals("admin@cfootprint.com")) {
                userRepository.delete(user);
            }
        });

        // 1. Create and verify userA
        userAToken = registerAndVerify("usera", "usera@cfootprint.com");

        // 2. Create and verify userB
        userBToken = registerAndVerify("userb", "userb@cfootprint.com");
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
    public void testExtendedCategoriesLeaderboardAndRecommendations() throws Exception {
        LocalDate today = LocalDate.now();

        // 1. UserA logs activity in new category "waste": WASTE_LANDFILL (10 kg -> 5.0 kg CO2)
        ActivityLogRequest logWaste = new ActivityLogRequest();
        logWaste.setCategory("waste");
        logWaste.setActivityType("WASTE_LANDFILL");
        logWaste.setQuantity(10.0);
        logWaste.setUnit("kg");
        logWaste.setLogDate(today);

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userAToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logWaste)))
                .andExpect(status().isOk());

        // 2. UserB logs activity in new category "heating": HEATING_ELECTRIC (100 kWh -> 40.0 kg CO2)
        ActivityLogRequest logHeating = new ActivityLogRequest();
        logHeating.setCategory("heating");
        logHeating.setActivityType("HEATING_ELECTRIC");
        logHeating.setQuantity(100.0);
        logHeating.setUnit("kWh");
        logHeating.setLogDate(today);

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userBToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logHeating)))
                .andExpect(status().isOk());

        // 3. UserA calls filtered logs list -> verifies list, total, and category breakdown
        mockMvc.perform(get("/api/activities")
                .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities", hasSize(1)))
                .andExpect(jsonPath("$.totalCo2Emission", is(5.0)))
                .andExpect(jsonPath("$.categoryBreakdown.waste", is(5.0)));

        // 4. UserA calls dashboard -> verifies 3 recommendations (including waste recommendation!)
        mockMvc.perform(get("/api/dashboard?range=weekly")
                .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations", hasSize(3)))
                .andExpect(jsonPath("$.recommendations", hasItem(containsString("Landfill waste generates greenhouse gases"))))
                .andExpect(jsonPath("$.categoryBreakdown", notNullValue()));

        // 5. UserA calls leaderboard -> verifies ranks (UserA is rank 1 [5kg], UserB is rank 2 [40kg])
        mockMvc.perform(get("/api/leaderboard")
                .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(2)))
                .andExpect(jsonPath("$.entries[0].username", is("usera")))
                .andExpect(jsonPath("$.entries[0].totalCo2Emission", is(5.0)))
                .andExpect(jsonPath("$.entries[0].rank", is(1)))
                .andExpect(jsonPath("$.entries[1].username", is("userb")))
                .andExpect(jsonPath("$.entries[1].totalCo2Emission", is(40.0)))
                .andExpect(jsonPath("$.entries[1].rank", is(2)))
                .andExpect(jsonPath("$.currentUserRank", is(1)))
                .andExpect(jsonPath("$.currentUserPercentile", is(50.0))) // 1 / 2 = 50%
                .andExpect(jsonPath("$.averageEmission", is(22.5))); // (5 + 40) / 2 = 22.5
    }

    @Test
    public void testLeaderboardPenaltyForInactiveUsers() throws Exception {
        // Register a third user (userc) who will NOT log any activities
        String userCToken = registerAndVerify("userc", "userc@cfootprint.com");

        // Set userc's createdAt to 2 days ago so that unloggedDays = 3 (penalty = 45 kg)
        User userc = userRepository.findByUsername("userc").orElseThrow();
        userc.setCreatedAt(java.time.LocalDateTime.now().minusDays(2));
        userRepository.save(userc);

        // Fetch leaderboard for usera
        mockMvc.perform(get("/api/leaderboard")
                .header("Authorization", "Bearer " + userAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries", hasSize(3)))
                .andExpect(jsonPath("$.entries[0].username", is("usera")))
                .andExpect(jsonPath("$.entries[0].totalCo2Emission", is(5.0)))
                .andExpect(jsonPath("$.entries[0].rank", is(1)))
                .andExpect(jsonPath("$.entries[1].username", is("userb")))
                .andExpect(jsonPath("$.entries[1].totalCo2Emission", is(40.0)))
                .andExpect(jsonPath("$.entries[1].rank", is(2)))
                .andExpect(jsonPath("$.entries[2].username", is("userc")))
                .andExpect(jsonPath("$.entries[2].totalCo2Emission", is(45.0)))
                .andExpect(jsonPath("$.entries[2].rank", is(3)));
    }
}
