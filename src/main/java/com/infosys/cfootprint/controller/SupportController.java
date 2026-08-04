package com.infosys.cfootprint.controller;

import com.infosys.cfootprint.dto.ComplaintResponse;
import com.infosys.cfootprint.dto.CreateComplaintRequest;
import com.infosys.cfootprint.security.CustomUserDetails;
import com.infosys.cfootprint.service.SupportService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/support")
public class SupportController {

    @Autowired
    private SupportService supportService;

    @PostMapping
    public ResponseEntity<ComplaintResponse> createComplaint(@Valid @RequestBody CreateComplaintRequest request) {
        return ResponseEntity.ok(supportService.createComplaint(request));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> getCategories() {
        return ResponseEntity.ok(supportService.getCategories());
    }

    @GetMapping("/me")
    public ResponseEntity<List<ComplaintResponse>> getMyComplaints(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails userDetails) {
            return ResponseEntity.ok(supportService.getComplaintsByEmail(userDetails.getEmail()));
        }
        return ResponseEntity.status(401).build();
    }
}
