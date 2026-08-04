package com.infosys.cfootprint.controller;

import com.infosys.cfootprint.dto.FilteredActivitiesResponse;
import com.infosys.cfootprint.dto.ActivityLogRequest;
import com.infosys.cfootprint.dto.ActivityLogResponse;
import com.infosys.cfootprint.model.ActivityProofImage;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.UserRepository;
import com.infosys.cfootprint.security.CustomUserDetails;
import com.infosys.cfootprint.service.ActivityLogService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

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

    @PostMapping("/upload-proof")
    public ResponseEntity<Map<String, String>> uploadProofImage(
            @RequestParam("file") MultipartFile file) {
        String imageProofId = activityLogService.uploadProofImage(file);
        return ResponseEntity.ok(Map.of("imageProofId", imageProofId));
    }

    @GetMapping("/{id}/proof")
    public ResponseEntity<byte[]> getProofImage(
            @PathVariable UUID id,
            Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        ActivityProofImage img = activityLogService.getProofImage(id, user);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + img.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(img.getContentType()))
                .body(img.getData());
    }

    @GetMapping
    public ResponseEntity<FilteredActivitiesResponse> getUserLogs(
            Authentication authentication,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String range,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(activityLogService.getUserLogs(user, category, date, range, startDate, endDate));
    }

    private User getAuthenticatedUser(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found in database"));
    }
}
