package com.infosys.cfootprint.controller;

import com.infosys.cfootprint.dto.*;
import com.infosys.cfootprint.model.BadgeDefinition;
import com.infosys.cfootprint.model.EmissionFactor;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.UserRepository;
import com.infosys.cfootprint.security.CustomUserDetails;
import com.infosys.cfootprint.service.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private BadgeService badgeService;

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdminAnalyticsService adminAnalyticsService;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private AdminActivityService adminActivityService;

    @Autowired
    private EmissionFactorService emissionFactorService;

    @Autowired
    private AnalysisService analysisService;

    @Autowired
    private SupportService supportService;

    // User Management

    @PostMapping("/users")
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody AdminCreateUserRequest request) {
        return ResponseEntity.ok(adminUserService.createUser(request));
    }

    @PutMapping("/users/{id}/enable")
    public ResponseEntity<UserResponse> enableUser(@PathVariable UUID id) {
        return ResponseEntity.ok(adminUserService.enableUser(id));
    }

    @GetMapping("/users")
    public ResponseEntity<AdminUserAnalyticsResponse> getUserAnalytics() {
        return ResponseEntity.ok(adminAnalyticsService.getUserAnalytics());
    }

    @GetMapping("/users/all")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(adminUserService.getAllUsers());
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<String> disableUser(@PathVariable UUID id) {
        adminUserService.disableUser(id);
        return ResponseEntity.ok("User has been suspended successfully and disabled.");
    }

    // Activity Analytics

    @GetMapping("/activities")
    public ResponseEntity<AdminActivityAnalyticsResponse> getActivityAnalytics(
            @RequestParam(required = false, defaultValue = "daily") String range) {
        return ResponseEntity.ok(adminAnalyticsService.getPlatformActivityAnalytics(range));
    }

    @GetMapping("/activities/list")
    public ResponseEntity<AdminFilteredActivitiesResponse> getPlatformActivities(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(adminActivityService.getPlatformActivities(range, date, category));
    }

    // Badge Management

    @PostMapping("/badges")
    public ResponseEntity<BadgeDefinition> createBadgeDefinition(
            @Valid @RequestBody CreateBadgeDefinitionRequest request) {
        return ResponseEntity.ok(badgeService.createDefinition(request));
    }

    @PutMapping("/badges/{id}")
    public ResponseEntity<BadgeDefinition> updateBadgeDefinition(
            @PathVariable UUID id,
            @Valid @RequestBody CreateBadgeDefinitionRequest request) {
        return ResponseEntity.ok(badgeService.updateDefinition(id, request));
    }

    @DeleteMapping("/badges/{id}")
    public ResponseEntity<String> deleteBadgeDefinition(@PathVariable UUID id) {
        badgeService.deleteDefinition(id);
        return ResponseEntity.ok("Badge definition deleted successfully");
    }

    @GetMapping("/badges")
    public ResponseEntity<List<BadgeDefinition>> getAllBadgeDefinitions() {
        return ResponseEntity.ok(badgeService.getAllDefinitions());
    }

    // Leaderboard Diagnostics

    @GetMapping("/leaderboard")
    public ResponseEntity<List<AdminLeaderboardResponse>> getAdminLeaderboard(Authentication authentication) {
        User admin = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(leaderboardService.getAdminLeaderboard(admin));
    }

    // Emission Factors Management

    @GetMapping("/emission-factors")
    public ResponseEntity<List<EmissionFactor>> getAllEmissionFactors() {
        return ResponseEntity.ok(emissionFactorService.getAllFactors());
    }

    @PostMapping("/emission-factors")
    public ResponseEntity<EmissionFactor> createEmissionFactor(@Valid @RequestBody CreateEmissionFactorRequest request) {
        return ResponseEntity.ok(emissionFactorService.createFactor(request));
    }

    @PutMapping("/emission-factors/{id}")
    public ResponseEntity<EmissionFactor> updateEmissionFactor(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateEmissionFactorRequest request) {
        return ResponseEntity.ok(emissionFactorService.updateFactor(id, request));
    }

    @DeleteMapping("/emission-factors/{id}")
    public ResponseEntity<Void> deleteEmissionFactor(@PathVariable UUID id) {
        emissionFactorService.deleteFactor(id);
        return ResponseEntity.noContent().build();
    }

    // System Analytics

    @GetMapping("/analysis")
    public ResponseEntity<AdminAnalysisResponse> getAdminAnalysis() {
        return ResponseEntity.ok(analysisService.getAdminAnalysis());
    }

    // Support Management

    @GetMapping("/support")
    public ResponseEntity<List<ComplaintResponse>> getAllComplaints() {
        return ResponseEntity.ok(supportService.getAllComplaints());
    }

    @PostMapping("/support/{id}/reply")
    public ResponseEntity<ComplaintResponse> replyToComplaint(
            @PathVariable UUID id,
            @Valid @RequestBody ReplyComplaintRequest request) {
        return ResponseEntity.ok(supportService.replyToComplaint(id, request));
    }

    private User getAuthenticatedUser(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));
    }
}
