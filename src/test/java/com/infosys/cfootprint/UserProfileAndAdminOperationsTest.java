package com.infosys.cfootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infosys.cfootprint.dto.*;
import com.infosys.cfootprint.model.User;
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
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class UserProfileAndAdminOperationsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    private String adminToken;

    @BeforeEach
    public void setup() throws Exception {
        userRepository.findAll().forEach(user -> {
            if (!user.getEmail().equals("admin@cfootprint.com")) {
                userRepository.delete(user);
            }
        });

        // Setup Admin Token
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
    }

    @Test
    public void testAdminUserCreationAndEnabling() throws Exception {
        // 1. Admin creates a user
        AdminCreateUserRequest createUserRequest = new AdminCreateUserRequest();
        createUserRequest.setUsername("admincreated");
        createUserRequest.setEmail("admincreated@cfootprint.com");
        createUserRequest.setPassword("adminpassword123");
        createUserRequest.setMobileNumber("+1234567890");
        createUserRequest.setAge(30);
        createUserRequest.setGender("Female");

        MvcResult createRes = mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("admincreated")))
                .andExpect(jsonPath("$.enabled", is(true)))
                .andExpect(jsonPath("$.disabled", is(false)))
                .andReturn();

        String createResStr = createRes.getResponse().getContentAsString();
        Map<?, ?> userMap = objectMapper.readValue(createResStr, Map.class);
        String userIdStr = (String) userMap.get("id");
        UUID userId = UUID.fromString(userIdStr);

        // 2. Log in immediately as the new user (no verification required since admin created them active)
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsernameOrEmail("admincreated");
        loginRequest.setPassword("adminpassword123");

        MvcResult userLoginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", notNullValue()))
                .andReturn();

        String userLoginStr = userLoginRes.getResponse().getContentAsString();
        Map<?, ?> userLoginMap = objectMapper.readValue(userLoginStr, Map.class);
        String userToken = (String) userLoginMap.get("accessToken");

        // 3. Admin disables user
        mockMvc.perform(delete("/api/admin/users/" + userId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // 4. Login attempt should fail
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("disabled by the admin")));

        // 5. Admin enables user
        mockMvc.perform(put("/api/admin/users/" + userId + "/enable")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disabled", is(false)));

        // 6. Login should succeed again
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk());
    }

    @Test
    public void testUserProfileEdit() throws Exception {
        // 1. Admin creates user
        AdminCreateUserRequest createUserRequest = new AdminCreateUserRequest();
        createUserRequest.setUsername("profileuser");
        createUserRequest.setEmail("profileuser@cfootprint.com");
        createUserRequest.setPassword("adminpassword123");
        createUserRequest.setMobileNumber("+1234567890");
        createUserRequest.setAge(20);
        createUserRequest.setGender("Male");

        mockMvc.perform(post("/api/admin/users")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createUserRequest)))
                .andExpect(status().isOk());

        // 2. Login
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsernameOrEmail("profileuser");
        loginRequest.setPassword("adminpassword123");

        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn();

        String loginStr = loginRes.getResponse().getContentAsString();
        Map<?, ?> loginMap = objectMapper.readValue(loginStr, Map.class);
        String userToken = (String) loginMap.get("accessToken");

        // 3. Get /me -> verify initial values
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobileNumber", is("+1234567890")))
                .andExpect(jsonPath("$.age", is(20)))
                .andExpect(jsonPath("$.gender", is("Male")));

        // 4. Update profile: mobile: +1999999999, age: 21, gender: Female
        UpdateProfileRequest updateRequest = new UpdateProfileRequest();
        updateRequest.setMobileNumber("+1999999999");
        updateRequest.setAge(21);
        updateRequest.setGender("Female");

        mockMvc.perform(put("/api/users/profile")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobileNumber", is("+1999999999")))
                .andExpect(jsonPath("$.age", is(21)))
                .andExpect(jsonPath("$.gender", is("Female")));

        // 5. Get /me again -> verify updated values persist
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mobileNumber", is("+1999999999")))
                .andExpect(jsonPath("$.age", is(21)))
                .andExpect(jsonPath("$.gender", is("Female")));
    }
}
