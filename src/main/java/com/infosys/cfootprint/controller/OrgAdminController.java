package com.infosys.cfootprint.controller;

import com.infosys.cfootprint.dto.*;
import com.infosys.cfootprint.exception.BadRequestException;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.UserRepository;
import com.infosys.cfootprint.security.CustomUserDetails;
import com.infosys.cfootprint.service.OrgAdminService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/org-admin")
@PreAuthorize("hasAuthority('ROLE_ORG_ADMIN')")
public class OrgAdminController {

    @Autowired
    private OrgAdminService orgAdminService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.infosys.cfootprint.service.AnalysisService analysisService;

    @PostMapping("/employees")
    public ResponseEntity<UserResponse> createEmployee(
            @Valid @RequestBody OrgCreateEmployeeRequest request,
            Authentication authentication) {
        User orgAdmin = getAuthenticatedOrgAdmin(authentication);
        return ResponseEntity.ok(orgAdminService.createEmployee(orgAdmin, request));
    }

    @GetMapping("/employees")
    public ResponseEntity<List<UserResponse>> getEmployees(Authentication authentication) {
        User orgAdmin = getAuthenticatedOrgAdmin(authentication);
        return ResponseEntity.ok(orgAdminService.getEmployees(orgAdmin));
    }

    @GetMapping("/employees/{id}/activities")
    public ResponseEntity<List<ActivityLogResponse>> getEmployeeActivities(
            @PathVariable UUID id,
            Authentication authentication) {
        User orgAdmin = getAuthenticatedOrgAdmin(authentication);
        return ResponseEntity.ok(orgAdminService.getEmployeeActivities(orgAdmin, id));
    }

    @GetMapping("/reports/summary")
    public ResponseEntity<Map<String, Object>> getReportSummary(Authentication authentication) {
        User orgAdmin = getAuthenticatedOrgAdmin(authentication);
        return ResponseEntity.ok(orgAdminService.getOrgSummary(orgAdmin));
    }

    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(defaultValue = "pdf") String format,
            Authentication authentication) {
        User orgAdmin = getAuthenticatedOrgAdmin(authentication);
        String filename = "org_report_" + System.currentTimeMillis();
        byte[] data;
        MediaType mediaType;
        String finalFilename;

        switch (format.toLowerCase()) {
            case "csv":
                String csv = orgAdminService.generateOrgCsv(orgAdmin);
                data = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                mediaType = MediaType.parseMediaType("text/csv");
                finalFilename = filename + ".csv";
                break;
            case "word":
            case "docx":
                data = orgAdminService.generateOrgWord(orgAdmin);
                mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
                finalFilename = filename + ".docx";
                break;
            case "pdf":
            default:
                data = orgAdminService.generateOrgPdf(orgAdmin);
                mediaType = MediaType.APPLICATION_PDF;
                finalFilename = filename + ".pdf";
                break;
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + finalFilename + "\"")
                .contentType(mediaType)
                .body(data);
    }

    @Autowired
    private com.infosys.cfootprint.service.SupportService supportService;

    @PostMapping("/setup-organization")
    public ResponseEntity<UserResponse> setupOrganization(
            @Valid @RequestBody SetupOrganizationRequest request,
            Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User orgAdmin = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!"ROLE_ORG_ADMIN".equals(orgAdmin.getRole())) {
            throw new BadRequestException("Access denied: You are not an Organization Administrator.");
        }
        return ResponseEntity.ok(orgAdminService.setupOrganization(orgAdmin, request));
    }

    @PutMapping("/employees/{id}/disable")
    public ResponseEntity<String> disableEmployee(
            @PathVariable UUID id,
            Authentication authentication) {
        User orgAdmin = getAuthenticatedOrgAdmin(authentication);
        orgAdminService.disableEmployee(orgAdmin, id);
        return ResponseEntity.ok("Employee has been suspended successfully and disabled.");
    }

    @PutMapping("/employees/{id}/enable")
    public ResponseEntity<UserResponse> enableEmployee(
            @PathVariable UUID id,
            Authentication authentication) {
        User orgAdmin = getAuthenticatedOrgAdmin(authentication);
        return ResponseEntity.ok(orgAdminService.enableEmployee(orgAdmin, id));
    }

    @GetMapping("/analytics/users")
    public ResponseEntity<AdminUserAnalyticsResponse> getUserAnalytics(Authentication authentication) {
        User orgAdmin = getAuthenticatedOrgAdmin(authentication);
        return ResponseEntity.ok(orgAdminService.getOrgUserAnalytics(orgAdmin));
    }

    @GetMapping("/analytics/activities")
    public ResponseEntity<AdminActivityAnalyticsResponse> getActivityAnalytics(
            @RequestParam(required = false, defaultValue = "daily") String range,
            Authentication authentication) {
        User orgAdmin = getAuthenticatedOrgAdmin(authentication);
        return ResponseEntity.ok(orgAdminService.getOrgActivityAnalytics(orgAdmin, range));
    }

    @GetMapping("/activities")
    public ResponseEntity<AdminFilteredActivitiesResponse> getActivities(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) java.time.LocalDate date,
            @RequestParam(required = false) String category,
            Authentication authentication) {
        User orgAdmin = getAuthenticatedOrgAdmin(authentication);
        return ResponseEntity.ok(orgAdminService.getOrgActivities(orgAdmin, range, date, category));
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<AdminLeaderboardResponse>> getLeaderboard(Authentication authentication) {
        User orgAdmin = getAuthenticatedOrgAdmin(authentication);
        return ResponseEntity.ok(orgAdminService.getOrgLeaderboard(orgAdmin));
    }

    @GetMapping("/support")
    public ResponseEntity<List<ComplaintResponse>> getComplaints(Authentication authentication) {
        User orgAdmin = getAuthenticatedOrgAdmin(authentication);
        return ResponseEntity.ok(supportService.getOrgComplaints(orgAdmin));
    }

    @PostMapping("/support/{id}/reply")
    public ResponseEntity<ComplaintResponse> replyToComplaint(
            @PathVariable UUID id,
            @Valid @RequestBody ReplyComplaintRequest request,
            Authentication authentication) {
        User orgAdmin = getAuthenticatedOrgAdmin(authentication);
        return ResponseEntity.ok(supportService.replyToComplaintOrg(id, request, orgAdmin));
    }

    @GetMapping("/analysis")
    public ResponseEntity<AdminAnalysisResponse> getOrgAnalysis(Authentication authentication) {
        User orgAdmin = getAuthenticatedOrgAdmin(authentication);
        return ResponseEntity.ok(analysisService.getOrgAdminAnalysis(orgAdmin));
    }

    private User getAuthenticatedOrgAdmin(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!"ROLE_ORG_ADMIN".equals(user.getRole())) {
            throw new BadRequestException("Access denied: You are not an Organization Administrator.");
        }
        if (user.getOrganization() == null) {
            throw new BadRequestException("You are not associated with any organization.");
        }
        return user;
    }
}
