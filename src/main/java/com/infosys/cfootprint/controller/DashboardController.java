package com.infosys.cfootprint.controller;

import com.infosys.cfootprint.dto.UserDashboardResponse;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.UserRepository;
import com.infosys.cfootprint.security.CustomUserDetails;
import com.infosys.cfootprint.service.ActivityLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private UserRepository userRepository;

    @GetMapping
    public ResponseEntity<UserDashboardResponse> getUserDashboard(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found in database"));
        
        return ResponseEntity.ok(activityLogService.getUserDashboardData(user));
    }
}
