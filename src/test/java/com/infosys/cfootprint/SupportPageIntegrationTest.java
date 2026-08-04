package com.infosys.cfootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infosys.cfootprint.dto.CreateComplaintRequest;
import com.infosys.cfootprint.dto.LoginRequest;
import com.infosys.cfootprint.dto.ReplyComplaintRequest;
import com.infosys.cfootprint.dto.SignupRequest;
import com.infosys.cfootprint.dto.VerifyEmailRequest;
import com.infosys.cfootprint.model.OtpToken;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.OtpTokenRepository;
import com.infosys.cfootprint.repository.SupportComplaintRepository;
import com.infosys.cfootprint.repository.UserRepository;
import com.infosys.cfootprint.service.EmailService;
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

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SupportPageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpTokenRepository otpTokenRepository;

    @Autowired
    private SupportComplaintRepository supportComplaintRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    private String adminToken;
    private String userEmail = "registereduser@cfootprint.com";

    @BeforeEach
    public void setup() throws Exception {
        supportComplaintRepository.deleteAll();
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

        // 2. Setup registered user
        SignupRequest signup = new SignupRequest();
        signup.setUsername("registereduser");
        signup.setEmail(userEmail);
        signup.setPassword("password123");
        signup.setMobileNumber("+1234567890");
        signup.setAge(25);
        signup.setGender("Male");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("registereduser").orElseThrow();
        OtpToken otpToken = otpTokenRepository.findByUserAndPurpose(user, "EMAIL_VERIFICATION").orElseThrow();

        VerifyEmailRequest verifyEmailRequest = new VerifyEmailRequest();
        verifyEmailRequest.setEmail(userEmail);
        verifyEmailRequest.setOtp(otpToken.getOtp());

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyEmailRequest)))
                .andExpect(status().isOk());
    }

    @Test
    public void testSupportPageFlow() throws Exception {
        // 0. Fetch support categories list
        mockMvc.perform(get("/api/support/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)))
                .andExpect(jsonPath("$", hasItem("Bug Report")))
                .andExpect(jsonPath("$", hasItem("Analytics Issue")))
                .andExpect(jsonPath("$", hasItem("Email Issue")))
                .andExpect(jsonPath("$", hasItem("Activity Logging Issue")));

        // 1. Submit complaint with unregistered email -> expect 400 Bad Request
        CreateComplaintRequest unregisteredRequest = new CreateComplaintRequest();
        unregisteredRequest.setEmail("unregistered@example.com");
        unregisteredRequest.setCategory("Bug Report");
        unregisteredRequest.setComplaintText("I cannot log my daily activities.");

        mockMvc.perform(post("/api/support")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(unregisteredRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not registered")));

        // 1.5 Submit complaint with invalid category -> expect 400 Bad Request
        CreateComplaintRequest invalidCategoryRequest = new CreateComplaintRequest();
        invalidCategoryRequest.setEmail(userEmail);
        invalidCategoryRequest.setCategory("FakeCategory");
        invalidCategoryRequest.setComplaintText("This category does not exist.");

        mockMvc.perform(post("/api/support")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidCategoryRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid support category")));

        // 2. Submit complaint with registered email and valid category -> expect 200 OK
        CreateComplaintRequest registeredRequest = new CreateComplaintRequest();
        registeredRequest.setEmail(userEmail);
        registeredRequest.setCategory("Analytics Issue");
        registeredRequest.setComplaintText("My weekly leaderboard data is incorrect.");

        mockMvc.perform(post("/api/support")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registeredRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(userEmail)))
                .andExpect(jsonPath("$.category", is("Analytics Issue")))
                .andExpect(jsonPath("$.complaintText", is("My weekly leaderboard data is incorrect.")))
                .andExpect(jsonPath("$.resolved", is(false)));

        // Verify it was stored in db
        assertEquals(1, supportComplaintRepository.findAll().size());
        UUID complaintId = supportComplaintRepository.findAll().get(0).getId();

        // 3. Admin fetches all complaints
        mockMvc.perform(get("/api/admin/support")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email", is(userEmail)))
                .andExpect(jsonPath("$[0].resolved", is(false)));

        // 4. Admin replies to complaint -> expect 200 OK, resolved is true
        ReplyComplaintRequest replyRequest = new ReplyComplaintRequest();
        replyRequest.setReplyText("We have resolved the leaderboard caching issue. Please check again.");

        mockMvc.perform(post("/api/admin/support/" + complaintId + "/reply")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(replyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replyText", is("We have resolved the leaderboard caching issue. Please check again.")))
                .andExpect(jsonPath("$.resolved", is(true)));

        // 5. Verify email reply was triggered
        verify(emailService).sendHtmlEmail(eq(userEmail), eq("Reply to your support complaint"), anyString());

        // 6. User logs in to get their Bearer token
        LoginRequest userLogin = new LoginRequest();
        userLogin.setUsernameOrEmail("registereduser");
        userLogin.setPassword("password123");

        MvcResult userLoginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userLogin)))
                .andExpect(status().isOk())
                .andReturn();
        String userResStr = userLoginRes.getResponse().getContentAsString();
        Map<?, ?> userMap = objectMapper.readValue(userResStr, Map.class);
        String userToken = (String) userMap.get("accessToken");

        // 7. User retrieves their own complaints
        mockMvc.perform(get("/api/support/me")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email", is(userEmail)))
                .andExpect(jsonPath("$[0].resolved", is(true)))
                .andExpect(jsonPath("$[0].replyText", is("We have resolved the leaderboard caching issue. Please check again.")));
    }
}
