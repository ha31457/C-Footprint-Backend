package com.infosys.cfootprint.controller;

import com.infosys.cfootprint.dto.AdminActivityAnalyticsResponse;
import com.infosys.cfootprint.dto.AdminActivityLogResponse;
import com.infosys.cfootprint.dto.AdminUserAnalyticsResponse;
import com.infosys.cfootprint.dto.UserResponse;
import com.infosys.cfootprint.service.AdminAnalyticsService;
import com.infosys.cfootprint.service.AdminUserService;
import com.infosys.cfootprint.service.AdminActivityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminAnalyticsService adminAnalyticsService;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private AdminActivityService adminActivityService;

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
        return ResponseEntity.ok("User has been successfully disabled and logged out.");
    }

    @GetMapping("/activities")
    public ResponseEntity<AdminActivityAnalyticsResponse> getActivityAnalytics(
            @RequestParam(required = false, defaultValue = "daily") String range) {
        return ResponseEntity.ok(adminAnalyticsService.getPlatformActivityAnalytics(range));
    }

    @GetMapping("/activities/list")
    public ResponseEntity<List<AdminActivityLogResponse>> getPlatformActivities(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) LocalDate date) {
        return ResponseEntity.ok(adminActivityService.getPlatformActivities(range, date));
    }
}
