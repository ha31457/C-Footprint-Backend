package com.infosys.cfootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infosys.cfootprint.dto.*;
import com.infosys.cfootprint.model.AvatarImage;
import com.infosys.cfootprint.model.OtpToken;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.ActivityLogRepository;
import com.infosys.cfootprint.repository.OtpTokenRepository;
import com.infosys.cfootprint.repository.UserRepository;
import com.infosys.cfootprint.repository.mongo.AvatarImageRepository;
import com.infosys.cfootprint.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class UserAvatarIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpTokenRepository otpTokenRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private AvatarImageRepository avatarImageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    private String userToken;
    private User testUser;

    @BeforeEach
    public void setup() throws Exception {
        Mockito.reset(avatarImageRepository);
        activityLogRepository.deleteAll();
        otpTokenRepository.deleteAll();
        userRepository.findAll().forEach(user -> {
            if (!user.getEmail().equals("admin@cfootprint.com")) {
                userRepository.delete(user);
            }
        });

        userToken = registerAndVerify("avataruser", "avataruser@cfootprint.com");
        testUser = userRepository.findByUsername("avataruser").orElseThrow();
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
                .andExpect(jsonPath("$.avatar", is("male-1")))
                .andExpect(jsonPath("$.avatarUrl", notNullValue()))
                .andReturn();

        String resStr = loginRes.getResponse().getContentAsString();
        Map<?, ?> map = objectMapper.readValue(resStr, Map.class);
        return (String) map.get("accessToken");
    }

    @Test
    public void testUpdateAvatarSuccess() throws Exception {
        UpdateAvatarRequest request = new UpdateAvatarRequest();
        request.setAvatar("female-2");

        mockMvc.perform(put("/api/users/avatar")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar", is("female-2")))
                .andExpect(jsonPath("$.avatarUrl", containsString("10.x/initial-face/svg")));

        // Verify leaderboard contains avatar and avatarUrl
        mockMvc.perform(get("/api/leaderboard")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].avatar", is("female-2")))
                .andExpect(jsonPath("$.entries[0].avatarUrl", containsString("10.x/initial-face/svg")));
    }

    @Test
    public void testUpdateInvalidAvatarReturnsBadRequest() throws Exception {
        UpdateAvatarRequest request = new UpdateAvatarRequest();
        request.setAvatar("invalid-avatar-99");

        mockMvc.perform(put("/api/users/avatar")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid avatar key")));
    }

    @Test
    public void testCustomAvatarUploadAndVerification() throws Exception {
        // 1. Setup mock MongoDB response
        AvatarImage mockImage = AvatarImage.builder()
                .id("mongo-avatar-uuid-123")
                .filename("photo.jpg")
                .contentType("image/jpeg")
                .data(new byte[]{1, 2, 3})
                .build();
        Mockito.when(avatarImageRepository.save(any(AvatarImage.class))).thenReturn(mockImage);

        // 2. Upload custom photo (multipart)
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "photo.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{1, 2, 3}
        );

        mockMvc.perform(multipart("/api/users/upload-avatar")
                .file(mockFile)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarImageId", is("mongo-avatar-uuid-123")))
                .andExpect(jsonPath("$.avatarUrl", is("/api/users/avatar/" + testUser.getId())));

        // 3. Verify user profile now carries the custom avatar URL
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl", is("/api/users/avatar/" + testUser.getId())));

        // 4. Retrieve custom avatar image bytes publicly (without JWT)
        mockMvc.perform(get("/api/users/avatar/" + testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("avatar.png"))) // Stays dummy in test profile
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));

        // 5. Update avatar back to preset -> should clear custom avatar mapping
        UpdateAvatarRequest presetReq = new UpdateAvatarRequest();
        presetReq.setAvatar("male-2");

        mockMvc.perform(put("/api/users/avatar")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(presetReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatar", is("male-2")))
                .andExpect(jsonPath("$.avatarUrl", containsString("10.x/initial-face/svg")));
    }
}
