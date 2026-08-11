package com.infosys.cfootprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infosys.cfootprint.dto.*;
import com.infosys.cfootprint.model.ActivityProofImage;
import com.infosys.cfootprint.model.Organization;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.ActivityLogRepository;
import com.infosys.cfootprint.repository.OrganizationRepository;
import com.infosys.cfootprint.repository.UserRepository;
import com.infosys.cfootprint.repository.mongo.ActivityProofImageRepository;
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

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class OrganizationManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private ActivityProofImageRepository activityProofImageRepository;

    @Autowired
    private com.infosys.cfootprint.repository.OtpTokenRepository otpTokenRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    private String adminToken;

    @BeforeEach
    public void setup() throws Exception {
        activityLogRepository.deleteAll();
        userRepository.findAll().forEach(u -> {
            if (!u.getEmail().equals("admin@cfootprint.com")) {
                userRepository.delete(u);
            }
        });
        organizationRepository.deleteAll();

        // Admin login
        LoginRequest login = new LoginRequest();
        login.setUsernameOrEmail("admin@cfootprint.com");
        login.setPassword("admin123");

        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn();

        String resStr = loginRes.getResponse().getContentAsString();
        Map<?, ?> map = objectMapper.readValue(resStr, Map.class);
        adminToken = (String) map.get("accessToken");
    }

    @Test
    public void testFullOrganizationEmployeeFlow() throws Exception {
        // 1. Create Organization
        CreateOrganizationRequest createOrg = new CreateOrganizationRequest();
        createOrg.setName("Eco Corp");

        MvcResult orgRes = mockMvc.perform(post("/api/admin/organizations")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createOrg)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Eco Corp")))
                .andReturn();

        String orgStr = orgRes.getResponse().getContentAsString();
        Map<?, ?> orgMap = objectMapper.readValue(orgStr, Map.class);
        String orgId = (String) orgMap.get("id");

        // 2. Create Organization Admin
        CreateOrgAdminRequest createOrgAdmin = new CreateOrgAdminRequest();
        createOrgAdmin.setUsername("ecoadmin");
        createOrgAdmin.setEmail("admin@ecocorp.com");
        createOrgAdmin.setPassword("adminpass123");

        mockMvc.perform(post("/api/admin/organizations/" + orgId + "/admin")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createOrgAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("ecoadmin")))
                .andExpect(jsonPath("$.role", is("ROLE_ORG_ADMIN")))
                .andExpect(jsonPath("$.organizationName", is("Eco Corp")));

        // 3. Log in as Organization Admin
        LoginRequest orgLogin = new LoginRequest();
        orgLogin.setUsernameOrEmail("admin@ecocorp.com");
        orgLogin.setPassword("adminpass123");

        MvcResult orgLoginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(orgLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("ROLE_ORG_ADMIN")))
                .andExpect(jsonPath("$.organizationName", is("Eco Corp")))
                .andReturn();

        String orgAdminStr = orgLoginRes.getResponse().getContentAsString();
        Map<?, ?> orgAdminMap = objectMapper.readValue(orgAdminStr, Map.class);
        String orgAdminToken = (String) orgAdminMap.get("accessToken");

        // 4. Org Admin creates an Employee account
        OrgCreateEmployeeRequest createEmployee = new OrgCreateEmployeeRequest();
        createEmployee.setUsername("ecoemp");
        createEmployee.setEmail("emp@ecocorp.com");
        createEmployee.setTemporaryPassword("temppass123");

        mockMvc.perform(post("/api/org-admin/employees")
                .header("Authorization", "Bearer " + orgAdminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createEmployee)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username", is("ecoemp")))
                .andExpect(jsonPath("$.organizationName", is("Eco Corp")))
                .andExpect(jsonPath("$.isTempPassword", is(true)));

        // 5. Employee logs in with temporary password
        LoginRequest empLogin = new LoginRequest();
        empLogin.setUsernameOrEmail("emp@ecocorp.com");
        empLogin.setPassword("temppass123");

        MvcResult empLoginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(empLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("ROLE_USER")))
                .andExpect(jsonPath("$.organizationName", is("Eco Corp")))
                .andExpect(jsonPath("$.isTempPassword", is(true)))
                .andReturn();

        String empStr = empLoginRes.getResponse().getContentAsString();
        Map<?, ?> empMap = objectMapper.readValue(empStr, Map.class);
        String empToken = (String) empMap.get("accessToken");

        // Verify that standard API request is blocked since they hold a temporary password
        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + empToken))
                .andExpect(status().isForbidden());

        // 6. Employee changes temporary password
        ChangeTempPasswordRequest changePass = new ChangeTempPasswordRequest();
        changePass.setNewPassword("newsecurepass123");

        mockMvc.perform(post("/api/auth/change-temp-password")
                .header("Authorization", "Bearer " + empToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(changePass)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isTempPassword", is(false)));

        // 7. Log in with new secure password
        LoginRequest empNewLogin = new LoginRequest();
        empNewLogin.setUsernameOrEmail("emp@ecocorp.com");
        empNewLogin.setPassword("newsecurepass123");

        MvcResult empNewLoginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(empNewLogin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isTempPassword", is(false)))
                .andReturn();

        String empNewStr = empNewLoginRes.getResponse().getContentAsString();
        Map<?, ?> empNewMap = objectMapper.readValue(empNewStr, Map.class);
        String empNewToken = (String) empNewMap.get("accessToken");

        // 8. Log an activity log for employee
        Mockito.when(activityProofImageRepository.existsById("proof-image-123")).thenReturn(true);

        ActivityLogRequest logReq = new ActivityLogRequest();
        logReq.setCategory("transport");
        logReq.setActivityType("CAR_GASOLINE");
        logReq.setQuantity(20.0);
        logReq.setUnit("km");
        logReq.setLogDate(java.time.LocalDate.now());
        logReq.setImageProofId("proof-image-123");

        mockMvc.perform(post("/api/logs")
                .header("Authorization", "Bearer " + empNewToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logReq)))
                .andExpect(status().isOk());

        // 9. Org Admin fetches summary stats & exports documents
        mockMvc.perform(get("/api/org-admin/reports/summary")
                .header("Authorization", "Bearer " + orgAdminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEmployees", is(1)))
                .andExpect(jsonPath("$.totalLogs", is(1)))
                .andExpect(jsonPath("$.totalCo2", is(notNullValue())));

        mockMvc.perform(get("/api/org-admin/reports/export")
                .header("Authorization", "Bearer " + orgAdminToken)
                .param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        mockMvc.perform(get("/api/org-admin/reports/export")
                .header("Authorization", "Bearer " + orgAdminToken)
                .param("format", "docx"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));

        mockMvc.perform(get("/api/org-admin/reports/export")
                .header("Authorization", "Bearer " + orgAdminToken)
                .param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"));
    }

    @Test
    public void testOrgAdminSelfRegistration() throws Exception {
        SignupRequest signup = new SignupRequest();
        signup.setUsername("selforgadmin");
        signup.setEmail("selfadmin@greenearth.com");
        signup.setPassword("securepass123");
        signup.setMobileNumber("+12345678901");
        signup.setAge(35);
        signup.setGender("Female");
        signup.setOrgAdmin(true);

        // 1. Call signup
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(signup)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("ROLE_ORG_ADMIN")))
                .andExpect(jsonPath("$.organizationName", is(nullValue())))
                .andExpect(jsonPath("$.enabled", is(false)));

        // 2. Fetch OTP token from repository
        User user = userRepository.findByEmail("selfadmin@greenearth.com")
                .orElseThrow(() -> new AssertionError("User not found"));
        assertThat(user.getOrganization(), is(nullValue()));
        
        com.infosys.cfootprint.model.OtpToken otpToken = otpTokenRepository.findByUserAndPurpose(user, "EMAIL_VERIFICATION")
                .orElseThrow(() -> new AssertionError("OTP token not found"));

        // 3. Verify OTP
        VerifyEmailRequest verify = new VerifyEmailRequest();
        verify.setEmail("selfadmin@greenearth.com");
        verify.setOtp(otpToken.getOtp());

        mockMvc.perform(post("/api/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(verify)))
                .andExpect(status().isOk());

        // 4. Log in
        LoginRequest login = new LoginRequest();
        login.setUsernameOrEmail("selfadmin@greenearth.com");
        login.setPassword("securepass123");

        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role", is("ROLE_ORG_ADMIN")))
                .andExpect(jsonPath("$.organizationName", is(nullValue())))
                .andExpect(jsonPath("$.isTempPassword", is(false)))
                .andReturn();

        String token = (String) objectMapper.readValue(loginRes.getResponse().getContentAsString(), Map.class).get("accessToken");

        // 5. Setup Organization
        SetupOrganizationRequest setup = new SetupOrganizationRequest();
        setup.setOrganizationName("Green Earth Corp");
        setup.setIndustry("Agriculture");
        setup.setAddress("456 Farm Road, Green County");

        mockMvc.perform(post("/api/org-admin/setup-organization")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(setup)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName", is("Green Earth Corp")));

        // 6. Verify organization is saved and linked in the database
        User verifiedUser = userRepository.findByEmail("selfadmin@greenearth.com").get();
        assertThat(verifiedUser.getOrganization(), is(notNullValue()));
        assertThat(verifiedUser.getOrganization().getIndustry(), is("Agriculture"));
        assertThat(verifiedUser.getOrganization().getAddress(), is("456 Farm Road, Green County"));
    }

    @Test
    public void testOrgAdminFirstTimeSetupAndEmployeeManagement() throws Exception {
        User unassociatedAdmin = User.builder()
                .username("unassociated")
                .email("unassociated@test.com")
                .password(userRepository.findAll().stream().filter(u -> u.getRole().equals("ROLE_ADMIN")).findFirst().get().getPassword())
                .role("ROLE_ORG_ADMIN")
                .isEnabled(true)
                .build();
        unassociatedAdmin = userRepository.save(unassociatedAdmin);

        LoginRequest login = new LoginRequest();
        login.setUsernameOrEmail("unassociated@test.com");
        login.setPassword("admin123");

        MvcResult loginRes = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName", is(nullValue())))
                .andReturn();

        String token = (String) objectMapper.readValue(loginRes.getResponse().getContentAsString(), Map.class).get("accessToken");

        SetupOrganizationRequest setup = new SetupOrganizationRequest();
        setup.setOrganizationName("Dynamic Org");
        setup.setIndustry("Technology");
        setup.setAddress("123 Green Way, Eco City");

        mockMvc.perform(post("/api/org-admin/setup-organization")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(setup)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationName", is("Dynamic Org")));

        User refreshedAdmin = userRepository.findById(unassociatedAdmin.getId()).get();
        assertThat(refreshedAdmin.getOrganization(), is(notNullValue()));
        assertThat(refreshedAdmin.getOrganization().getName(), is("Dynamic Org"));
        assertThat(refreshedAdmin.getOrganization().getIndustry(), is("Technology"));
        assertThat(refreshedAdmin.getOrganization().getAddress(), is("123 Green Way, Eco City"));

        OrgCreateEmployeeRequest createEmployee = new OrgCreateEmployeeRequest();
        createEmployee.setUsername("dynamicemp");
        createEmployee.setEmail("emp@dynamic.com");
        createEmployee.setTemporaryPassword("temppass123");

        MvcResult empRes = mockMvc.perform(post("/api/org-admin/employees")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(createEmployee)))
                .andExpect(status().isOk())
                .andReturn();

        String empId = (String) objectMapper.readValue(empRes.getResponse().getContentAsString(), Map.class).get("id");

        mockMvc.perform(put("/api/org-admin/employees/" + empId + "/disable")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Employee has been suspended successfully")));

        User disabledEmp = userRepository.findById(UUID.fromString(empId)).get();
        assertThat(disabledEmp.isDisabled(), is(true));

        mockMvc.perform(put("/api/org-admin/employees/" + empId + "/enable")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.disabled", is(false)));

        mockMvc.perform(get("/api/org-admin/analytics/users")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", is(1)));

        mockMvc.perform(get("/api/org-admin/analytics/activities")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalLogs", is(0)));

        mockMvc.perform(get("/api/org-admin/activities")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activities", hasSize(0)));

        mockMvc.perform(get("/api/org-admin/leaderboard")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    public void testOrgAdminCascadingDeletion() throws Exception {
        Organization org = Organization.builder()
                .id(UUID.randomUUID())
                .name("Cascade Org")
                .createdAt(LocalDateTime.now())
                .build();
        org = organizationRepository.save(org);

        User orgAdmin = User.builder()
                .username("cascadeadmin")
                .email("cascadeadmin@test.com")
                .password(userRepository.findAll().stream().filter(u -> u.getRole().equals("ROLE_ADMIN")).findFirst().get().getPassword())
                .role("ROLE_ORG_ADMIN")
                .isEnabled(true)
                .organization(org)
                .build();
        orgAdmin = userRepository.save(orgAdmin);

        User employee = User.builder()
                .username("cascadeemp")
                .email("cascadeemp@test.com")
                .password("emppass123")
                .role("ROLE_USER")
                .isEnabled(true)
                .organization(org)
                .build();
        employee = userRepository.save(employee);

        mockMvc.perform(delete("/api/admin/users/" + orgAdmin.getId())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("User has been suspended successfully")));

        User deletedAdmin = userRepository.findById(orgAdmin.getId()).get();
        assertThat(deletedAdmin.isDisabled(), is(true));
        assertThat(deletedAdmin.getOrganization(), is(nullValue()));

        boolean orgExists = organizationRepository.existsById(org.getId());
        assertThat(orgExists, is(false));

        User detachedEmp = userRepository.findById(employee.getId()).get();
        assertThat(detachedEmp.getOrganization(), is(nullValue()));
        assertThat(detachedEmp.getRole(), is("ROLE_USER"));
    }
}
