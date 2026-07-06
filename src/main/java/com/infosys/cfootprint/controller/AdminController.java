package com.infosys.cfootprint.controller;

import com.infosys.cfootprint.dto.AdminActivityAnalyticsResponse;
import com.infosys.cfootprint.dto.AdminUserAnalyticsResponse;
import com.infosys.cfootprint.service.AdminAnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminAnalyticsService adminAnalyticsService;

    @GetMapping("/users")
    public ResponseEntity<AdminUserAnalyticsResponse> getUserAnalytics() {
        return ResponseEntity.ok(adminAnalyticsService.getUserAnalytics());
    }

    @GetMapping("/activities")
    public ResponseEntity<AdminActivityAnalyticsResponse> getActivityAnalytics() {
        return ResponseEntity.ok(adminAnalyticsService.getPlatformActivityAnalytics());
    }
}
