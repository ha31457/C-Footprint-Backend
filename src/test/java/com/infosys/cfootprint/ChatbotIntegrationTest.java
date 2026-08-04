package com.infosys.cfootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infosys.cfootprint.dto.ChatRequest;
import com.infosys.cfootprint.dto.LoginRequest;
import com.infosys.cfootprint.dto.SignupRequest;
import com.infosys.cfootprint.dto.VerifyEmailRequest;
import com.infosys.cfootprint.model.OtpToken;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.OtpTokenRepository;
import com.infosys.cfootprint.repository.UserRepository;
import com.infosys.cfootprint.service.EmailService;
import com.infosys.cfootprint.service.GeminiService;
import com.infosys.cfootprint.service.GroqService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ChatbotIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpTokenRepository otpTokenRepository;

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GroqService groqService;

    @MockitoBean
    private EmailService emailService;

    private String userToken;
    private String userEmail = "chatter@cfootprint.com";

    @BeforeEach
    public void setup() throws Exception {
        otpTokenRepository.deleteAll();
        userRepository.findAll().forEach(user -> {
            if (!user.getEmail().equals("admin@cfootprint.com")) {
                userRepository.delete(user);
            }
        });

        // Setup User Session
        SignupRequest signup = new SignupRequest();
        signup.setUsername("chatter");
        signup.setEmail(userEmail);
        signup.setPassword("password123");
        signup.setMobileNumber("+1234567890");
        signup.setAge(25);
        signup.setGender("Male");

        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("chatter").orElseThrow();
        OtpToken otpToken = otpTokenRepository.findByUserAndPurpose(user, "EMAIL_VERIFICATION").orElseThrow();

        VerifyEmailRequest verifyEmailRequest = new VerifyEmailRequest();
        verifyEmailRequest.setEmail(userEmail);
        verifyEmailRequest.setOtp(otpToken.getOtp());

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verifyEmailRequest)))
                .andExpect(status().isOk());

        LoginRequest login = new LoginRequest();
        login.setUsernameOrEmail("chatter");
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
    public void testGeneralChatResponse() throws Exception {
        // Mock Groq: Not contextual query
        Mockito.when(groqService.isContextualQuery("What is carbon footprint?")).thenReturn(false);

        // Mock RestTemplate inside GeminiService
        RestTemplate mockRestTemplate = Mockito.mock(RestTemplate.class);
        ReflectionTestUtils.setField(geminiService, "restTemplate", mockRestTemplate);

        String mockGeminiJson = "{ \"candidates\": [ { \"content\": { \"parts\": [ { \"text\": \"A carbon footprint is the total amount of greenhouse gases generated by our actions.\" } ] } } ] }";
        Mockito.when(mockRestTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(mockGeminiJson));

        ChatRequest req = new ChatRequest("What is carbon footprint?");
        mockMvc.perform(post("/api/chat")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isContextual", is(false)))
                .andExpect(jsonPath("$.response", containsString("A carbon footprint is")));
    }

    @Test
    public void testContextualChatResponse() throws Exception {
        // Mock Groq: Contextual query
        Mockito.when(groqService.isContextualQuery("How are my goals looking?")).thenReturn(true);

        // Mock RestTemplate inside GeminiService
        RestTemplate mockRestTemplate = Mockito.mock(RestTemplate.class);
        ReflectionTestUtils.setField(geminiService, "restTemplate", mockRestTemplate);

        String mockGeminiJson = "{ \"candidates\": [ { \"content\": { \"parts\": [ { \"text\": \"Your goals are in good shape, chatter.\" } ] } } ] }";
        Mockito.when(mockRestTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(ResponseEntity.ok(mockGeminiJson));

        ChatRequest req = new ChatRequest("How are my goals looking?");
        mockMvc.perform(post("/api/chat")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isContextual", is(true)))
                .andExpect(jsonPath("$.response", containsString("Your goals are in good shape")));
    }

    @Test
    public void testGeminiKeyRotation() {
        // Setup mock RestTemplate to simulate rate limits (429) for key 1 and key 2
        RestTemplate mockRestTemplate = Mockito.mock(RestTemplate.class);
        ReflectionTestUtils.setField(geminiService, "restTemplate", mockRestTemplate);

        // Key 1 (3 models) + Key 2 (3 models) = 6 requests throw exception
        // Request 7 (Key 3, Model 1) succeeds
        String mockSuccessJson = "{ \"candidates\": [ { \"content\": { \"parts\": [ { \"text\": \"Success answer\" } ] } } ] }";

        Mockito.when(mockRestTemplate.exchange(
                any(String.class),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        ))
        .thenThrow(new RuntimeException("Rate limit 429")) // Key 1 Model 1
        .thenThrow(new RuntimeException("Rate limit 429")) // Key 1 Model 2
        .thenThrow(new RuntimeException("Rate limit 429")) // Key 1 Model 3
        .thenThrow(new RuntimeException("Rate limit 429")) // Key 2 Model 1
        .thenThrow(new RuntimeException("Rate limit 429")) // Key 2 Model 2
        .thenThrow(new RuntimeException("Rate limit 429")) // Key 2 Model 3
        .thenReturn(ResponseEntity.ok(mockSuccessJson));    // Key 3 Model 1

        String answer = geminiService.generateContent("Test prompt");
        assertEquals("Success answer", answer);
    }
}
