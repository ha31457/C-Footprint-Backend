package com.infosys.cfootprint.controller;

import com.infosys.cfootprint.dto.ActivityLogRequest;
import com.infosys.cfootprint.dto.ActivityLogResponse;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.UserRepository;
import com.infosys.cfootprint.security.CustomUserDetails;
import com.infosys.cfootprint.service.ActivityLogService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activities")
public class ActivityLogController {

    @Autowired
    private ActivityLogService activityLogService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<ActivityLogResponse> logActivity(
            @Valid @RequestBody ActivityLogRequest request,
            Authentication authentication) {
        
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(activityLogService.logActivity(request, user));
    }

    @GetMapping
    public ResponseEntity<List<ActivityLogResponse>> getUserLogs(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(activityLogService.getUserLogs(user));
    }

    private User getAuthenticatedUser(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found in database"));
    }
}
