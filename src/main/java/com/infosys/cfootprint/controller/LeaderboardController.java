package com.infosys.cfootprint.controller;

import com.infosys.cfootprint.dto.LeaderboardResponse;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.UserRepository;
import com.infosys.cfootprint.security.CustomUserDetails;
import com.infosys.cfootprint.service.LeaderboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/leaderboard")
public class LeaderboardController {

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.infosys.cfootprint.service.SystemSettingService systemSettingService;

    @GetMapping
    public ResponseEntity<LeaderboardResponse> getLeaderboard(Authentication authentication) {
        if (!systemSettingService.isFeatureEnabled("leaderboard_enabled")) {
            throw new com.infosys.cfootprint.exception.BadRequestException("Community leaderboard is disabled by administrator.");
        }

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return ResponseEntity.ok(leaderboardService.getLeaderboard(user));
    }
}
