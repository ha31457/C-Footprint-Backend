package com.infosys.cfootprint.controller;

import com.infosys.cfootprint.dto.CreateGoalRequest;
import com.infosys.cfootprint.dto.GoalResponse;
import com.infosys.cfootprint.dto.UpdateGoalRequest;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.UserRepository;
import com.infosys.cfootprint.security.CustomUserDetails;
import com.infosys.cfootprint.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    @Autowired
    private GoalService goalService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping
    public ResponseEntity<GoalResponse> createGoal(
            @Valid @RequestBody CreateGoalRequest request,
            Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(goalService.createGoal(user, request));
    }

    @GetMapping("/active")
    public ResponseEntity<List<GoalResponse>> getActiveGoals(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(goalService.getActiveGoals(user));
    }

    @GetMapping
    public ResponseEntity<List<GoalResponse>> getUserGoals(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(goalService.getUserGoals(user));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> updateGoal(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGoalRequest request,
            Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(goalService.updateGoal(user, id, request));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<GoalResponse> cancelGoal(
            @PathVariable UUID id,
            Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return ResponseEntity.ok(goalService.cancelGoal(user, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteGoal(
            @PathVariable UUID id,
            Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        goalService.deleteGoal(user, id);
        return ResponseEntity.ok("Goal deleted successfully");
    }

    private User getAuthenticatedUser(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        return userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }
}
