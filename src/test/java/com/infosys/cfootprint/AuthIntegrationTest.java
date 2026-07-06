package com.infosys.cfootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infosys.cfootprint.dto.*;
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

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AuthIntegrationTest {

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

    @BeforeEach
    public void setup() {
        // Clear all OTP tokens and users except seeded admin to keep tests clean
        otpTokenRepository.deleteAll();
        userRepository.findAll().forEach(user -> {
            if (!user.getEmail().equals("admin@cfootprint.com")) {
                userRepository.delete(user);
            }
        });
    }

    @Test
    public void testAdminSeededOnStartup() throws Exception {
        LoginRequest adminLogin = new LoginRequest();
        adminLogin.setUsernameOrEmail("admin@cfootprint.com");
        adminLogin.setPassword("adminpassword");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(adminLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.email", is("admin@cfootprint.com")))
                .andExpect(jsonPath("$.role", is("ROLE_ADMIN")));
    }

    @Test
    public void testFullAuthenticationFlow() throws Exception {
        // 1. Signup
        SignupRequest signup = new SignupRequest();
        signup.setUsername("testuser");
        signup.setEmail("testuser@cfootprint.com");
        signup.setPassword("testpassword");
        signup.setMobileNumber("+1234567890");
        signup.setAge(25);
        signup.setGender("Male");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.username", is("testuser")))
                .andExpect(jsonPath("$.email", is("testuser@cfootprint.com")))
                .andExpect(jsonPath("$.role", is("ROLE_USER")))
                .andExpect(jsonPath("$.mobileNumber", is("+1234567890")))
                .andExpect(jsonPath("$.age", is(25)))
                .andExpect(jsonPath("$.gender", is("Male")))
                .andExpect(jsonPath("$.enabled", is(false)));

        // 2. Duplicate Signup should fail
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isBadRequest());

        // 3. Login before email verification -> should fail with 401 Unauthorized (DisabledException)
        LoginRequest login = new LoginRequest();
        login.setUsernameOrEmail("testuser");
        login.setPassword("testpassword");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("email address has not been verified yet")));

        // 4. Verify Email
        User user = userRepository.findByUsername("testuser").orElseThrow();
        OtpToken otpToken = otpTokenRepository.findByUserAndPurpose(user, "EMAIL_VERIFICATION").orElseThrow();

        VerifyEmailRequest verifyEmailRequest = new VerifyEmailRequest();
        verifyEmailRequest.setEmail("testuser@cfootprint.com");
        verifyEmailRequest.setOtp(otpToken.getOtp());

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyEmailRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", containsString("successfully verified")));

        // 5. Login after email verification -> should succeed
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andExpect(jsonPath("$.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.username", is("testuser")))
                .andReturn();

        String loginResponseStr = loginResult.getResponse().getContentAsString();
        Map<?, ?> responseMap = objectMapper.readValue(loginResponseStr, Map.class);
        String accessToken = (String) responseMap.get("accessToken");
        String refreshToken = (String) responseMap.get("refreshToken");

        // 6. Access secure endpoint /api/users/me without token -> should fail (403)
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isForbidden());

        // 7. Access secure endpoint /api/users/me with token -> should succeed
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("testuser")))
                .andExpect(jsonPath("$.role", is("ROLE_USER")));

        // 8. Refresh Token
        TokenRefreshRequest refreshRequest = new TokenRefreshRequest();
        refreshRequest.setRefreshToken(refreshToken);

        MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andReturn();

        String refreshResponseStr = refreshResult.getResponse().getContentAsString();
        Map<?, ?> refreshResponseMap = objectMapper.readValue(refreshResponseStr, Map.class);
        String newAccessToken = (String) refreshResponseMap.get("accessToken");

        // 9. Access secure endpoint with new Access Token
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("testuser")));

        // 10. Logout
        mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isOk());

        // 11. Use revoked Refresh Token -> should fail (403)
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testForgotPasswordAndResetFlow() throws Exception {
        // Setup a verified user
        SignupRequest signup = new SignupRequest();
        signup.setUsername("resetuser");
        signup.setEmail("resetuser@cfootprint.com");
        signup.setPassword("oldpassword");
        signup.setMobileNumber("+1987654321");
        signup.setAge(30);
        signup.setGender("Female");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("resetuser").orElseThrow();
        OtpToken verifyToken = otpTokenRepository.findByUserAndPurpose(user, "EMAIL_VERIFICATION").orElseThrow();

        VerifyEmailRequest verifyEmailRequest = new VerifyEmailRequest();
        verifyEmailRequest.setEmail("resetuser@cfootprint.com");
        verifyEmailRequest.setOtp(verifyToken.getOtp());

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyEmailRequest)))
                .andExpect(status().isOk());

        // 1. Trigger Forgot Password
        ForgotPasswordRequest forgotRequest = new ForgotPasswordRequest();
        forgotRequest.setEmail("resetuser@cfootprint.com");

        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(forgotRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", containsString("OTP has been sent")));

        // 2. Query reset OTP from DB
        OtpToken resetToken = otpTokenRepository.findByUserAndPurpose(user, "PASSWORD_RESET").orElseThrow();

        // 3. Reset Password
        ResetPasswordRequest resetRequest = new ResetPasswordRequest();
        resetRequest.setEmail("resetuser@cfootprint.com");
        resetRequest.setOtp(resetToken.getOtp());
        resetRequest.setNewPassword("brandnewpassword");

        mockMvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(resetRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", containsString("successfully updated")));

        // 4. Try logging in with OLD password -> should fail
        LoginRequest oldLogin = new LoginRequest();
        oldLogin.setUsernameOrEmail("resetuser");
        oldLogin.setPassword("oldpassword");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(oldLogin)))
                .andExpect(status().isUnauthorized());

        // 5. Login with NEW password -> should succeed
        LoginRequest newLogin = new LoginRequest();
        newLogin.setUsernameOrEmail("resetuser");
        newLogin.setPassword("brandnewpassword");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()));
    }
}
