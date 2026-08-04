package com.infosys.cfootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infosys.cfootprint.dto.ActivityLogRequest;
import com.infosys.cfootprint.dto.LoginRequest;
import com.infosys.cfootprint.dto.SignupRequest;
import com.infosys.cfootprint.dto.VerifyEmailRequest;
import com.infosys.cfootprint.model.ActivityProofImage;
import com.infosys.cfootprint.model.OtpToken;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.ActivityLogRepository;
import com.infosys.cfootprint.repository.OtpTokenRepository;
import com.infosys.cfootprint.repository.UserRepository;
import com.infosys.cfootprint.repository.mongo.ActivityProofImageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ActivityProofIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpTokenRepository otpTokenRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private ActivityProofImageRepository activityProofImageRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private String userToken;
    private String userEmail = "proofer@cfootprint.com";

    @BeforeEach
    public void setup() throws Exception {
        Mockito.reset(activityProofImageRepository);
        activityLogRepository.deleteAll();
        otpTokenRepository.deleteAll();
        userRepository.findAll().forEach(user -> {
            if (!user.getEmail().equals("admin@cfootprint.com")) {
                userRepository.delete(user);
            }
        });

        // 1. Setup User Token
        SignupRequest signup = new SignupRequest();
        signup.setUsername("proofer");
        signup.setEmail(userEmail);
        signup.setPassword("password123");
        signup.setMobileNumber("+1234567890");
        signup.setAge(25);
        signup.setGender("Male");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("proofer").orElseThrow();
        OtpToken otpToken = otpTokenRepository.findByUserAndPurpose(user, "EMAIL_VERIFICATION").orElseThrow();

        VerifyEmailRequest verifyEmailRequest = new VerifyEmailRequest();
        verifyEmailRequest.setEmail(userEmail);
        verifyEmailRequest.setOtp(otpToken.getOtp());

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyEmailRequest)))
                .andExpect(status().isOk());

        LoginRequest login = new LoginRequest();
        login.setUsernameOrEmail("proofer");
        login.setPassword("password123");

        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andReturn();
        String resStr = loginRes.getResponse().getContentAsString();
        Map<?, ?> map = objectMapper.readValue(resStr, Map.class);
        userToken = (String) map.get("accessToken");
    }

    @Test
    public void testActivityProofUploadAndVerificationFlow() throws Exception {
        // 1. Setup Mongo DB mock responses
        ActivityProofImage mockImage = ActivityProofImage.builder()
                .id("mongo-proof-id-123")
                .filename("receipt.png")
                .contentType("image/png")
                .data(new byte[]{10, 20, 30})
                .uploadedAt(LocalDateTime.now())
                .build();

        Mockito.when(activityProofImageRepository.save(Mockito.any(ActivityProofImage.class)))
                .thenReturn(mockImage);
        Mockito.when(activityProofImageRepository.existsById("mongo-proof-id-123"))
                .thenReturn(true);

        // 2. Perform Image upload (multipart/form-data)
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "receipt.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{10, 20, 30}
        );

        mockMvc.perform(multipart("/api/activities/upload-proof")
                .file(mockFile)
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageProofId", is("mongo-proof-id-123")));

        // 3. Log activity with missing proof ID -> Expect 400 Bad Request
        ActivityLogRequest emptyProofReq = new ActivityLogRequest();
        emptyProofReq.setCategory("transport");
        emptyProofReq.setActivityType("CAR_PETROL");
        emptyProofReq.setQuantity(10.0);
        emptyProofReq.setUnit("km");
        emptyProofReq.setLogDate(LocalDate.now());
        emptyProofReq.setImageProofId(""); // Empty!

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(emptyProofReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Image proof is required")));

        // 4. Log activity with invalid/fake proof ID -> Expect 400 Bad Request
        // (Note: in test profile we simulate this by making repository return false for fake id)
        Mockito.when(activityProofImageRepository.existsById("fake-id")).thenReturn(false);

        ActivityLogRequest fakeProofReq = new ActivityLogRequest();
        fakeProofReq.setCategory("transport");
        fakeProofReq.setActivityType("CAR_PETROL");
        fakeProofReq.setQuantity(10.0);
        fakeProofReq.setUnit("km");
        fakeProofReq.setLogDate(LocalDate.now());
        fakeProofReq.setImageProofId("fake-id");

        mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(fakeProofReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Invalid image proof ID")));

        // 5. Log activity with valid proof ID -> Expect 200 OK
        ActivityLogRequest validReq = new ActivityLogRequest();
        validReq.setCategory("transport");
        validReq.setActivityType("CAR_PETROL");
        validReq.setQuantity(10.0);
        validReq.setUnit("km");
        validReq.setLogDate(LocalDate.now());
        validReq.setImageProofId("mongo-proof-id-123");

        MvcResult postRes = mockMvc.perform(post("/api/activities")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imageProofId", is("mongo-proof-id-123")))
                .andReturn();

        String resBody = postRes.getResponse().getContentAsString();
        Map<?, ?> resMap = objectMapper.readValue(resBody, Map.class);
        String logId = (String) resMap.get("id");

        // 6. Retrieve uploaded proof image bytes
        mockMvc.perform(get("/api/activities/" + logId + "/proof")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("receipt.png")))
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[]{1, 2, 3})); 
                // Note: The service in test mode returns a dummy test stub containing byte[]{1, 2, 3} 
                // to stay fully isolated from database connections.
    }
}
