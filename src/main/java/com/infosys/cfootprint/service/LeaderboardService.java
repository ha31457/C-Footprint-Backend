package com.infosys.cfootprint.service;

import com.infosys.cfootprint.dto.AdminLeaderboardResponse;
import com.infosys.cfootprint.dto.LeaderboardEntryDTO;
import com.infosys.cfootprint.dto.LeaderboardResponse;
import com.infosys.cfootprint.model.ActivityLog;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.ActivityLogRepository;
import com.infosys.cfootprint.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.infosys.cfootprint.util.AvatarUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LeaderboardService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    private static final double DAILY_UNLOGGED_EMISSION_PENALTY = 15.0;

    public LeaderboardResponse getLeaderboard(User currentUser) {
        // Fetch all active non-admin users
        List<User> activeUsers = userRepository.findAll().stream()
                .filter(u -> "ROLE_USER".equals(u.getRole()))
                .filter(u -> !u.isDisabled())
                .filter(User::isEnabled)
                .collect(Collectors.toList());

        List<LeaderboardEntryDTO> entries = new ArrayList<>();
        double grandTotalCo2 = 0.0;

        for (User user : activeUsers) {
            java.time.LocalDate createdDate = user.getCreatedAt() != null 
                    ? user.getCreatedAt().toLocalDate() 
                    : java.time.LocalDate.now();
            long totalDays = java.time.temporal.ChronoUnit.DAYS.between(createdDate, java.time.LocalDate.now()) + 1;
            
            List<ActivityLog> logs = activityLogRepository.findByUserOrderByLogDateDesc(user);
            long loggedDays = logs.stream()
                    .map(ActivityLog::getLogDate)
                    .distinct()
                    .count();
            
            long unloggedDays = Math.max(0, totalDays - loggedDays);
            double actualCo2 = logs.stream()
                    .mapToDouble(ActivityLog::getCo2Emission)
                    .sum();
            
            double totalCo2 = actualCo2 + (unloggedDays * DAILY_UNLOGGED_EMISSION_PENALTY);
            
            grandTotalCo2 += totalCo2;

            entries.add(LeaderboardEntryDTO.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .avatar(user.getAvatar())
                    .avatarUrl(AvatarUtils.getAvatarUrl(user))
                    .totalCo2Emission(Math.round(totalCo2 * 100.0) / 100.0)
                    .isCurrentUser(user.getId().equals(currentUser.getId()))
                    .build());
        }

        // Sort ascending by total emissions (lower emission = better rank)
        entries.sort(Comparator.comparing(LeaderboardEntryDTO::getTotalCo2Emission));

        // Assign ranks sequentially
        int currentRank = 1;
        for (LeaderboardEntryDTO entry : entries) {
            entry.setRank(currentRank++);
        }

        long totalParticipants = entries.size();
        double averageEmission = totalParticipants > 0 ? (grandTotalCo2 / totalParticipants) : 0.0;

        // Find current user's rank
        int userRank = entries.stream()
                .filter(LeaderboardEntryDTO::getIsCurrentUser)
                .map(LeaderboardEntryDTO::getRank)
                .findFirst()
                .orElse(0);

        double percentile = 0.0;
        if (totalParticipants > 0 && userRank > 0) {
            percentile = (userRank / (double) totalParticipants) * 100.0;
        }

        List<String> insights = new ArrayList<>();
        insights.add("You are currently ranked #" + userRank + " out of " + totalParticipants + " active participants.");
        
        if (percentile > 0.0) {
            if (percentile <= 25.0) {
                insights.add("Outstanding! You are in the top 25% of eco-friendly users on the platform. Keep up the green choices!");
            } else if (percentile <= 50.0) {
                insights.add("Great job! You are in the top 50% of low-emitters. A few more green actions can boost you even higher.");
            } else {
                insights.add("You are in the lower 50% of carbon emitters. Try reducing your transport emissions or shifting to plant-based meals to improve your rank!");
            }
        }
        
        if (userRank > 1) {
            insights.add("Reducing your weekly footprint by 5 kg would help you climb the leaderboard ranks!");
        }

        return LeaderboardResponse.builder()
                .entries(entries)
                .currentUserRank(userRank)
                .currentUserPercentile(Math.round(percentile * 100.0) / 100.0)
                .averageEmission(Math.round(averageEmission * 100.0) / 100.0)
                .totalParticipants(totalParticipants)
                .insights(insights)
                .build();
    }

    public List<AdminLeaderboardResponse> getAdminLeaderboard(User admin) {
        List<User> users = userRepository.findAll().stream()
                .filter(u -> "ROLE_USER".equals(u.getRole()))
                .collect(Collectors.toList());

        List<AdminLeaderboardResponse> entries = new ArrayList<>();

        for (User user : users) {
            java.time.LocalDate createdDate = user.getCreatedAt() != null 
                    ? user.getCreatedAt().toLocalDate() 
                    : java.time.LocalDate.now();
            long totalDays = java.time.temporal.ChronoUnit.DAYS.between(createdDate, java.time.LocalDate.now()) + 1;
            
            List<ActivityLog> logs = activityLogRepository.findByUserOrderByLogDateDesc(user);
            long loggedDays = logs.stream()
                    .map(ActivityLog::getLogDate)
                    .distinct()
                    .count();
            
            long unloggedDays = Math.max(0, totalDays - loggedDays);
            double actualCo2 = logs.stream()
                    .mapToDouble(ActivityLog::getCo2Emission)
                    .sum();
            
            double totalCo2 = actualCo2 + (unloggedDays * DAILY_UNLOGGED_EMISSION_PENALTY);

            entries.add(AdminLeaderboardResponse.builder()
                    .userId(user.getId())
                    .username(user.getUsername())
                    .email(user.getEmail())
                    .isEnabled(user.isEnabled())
                    .isDisabled(user.isDisabled())
                    .avatar(user.getAvatar())
                    .avatarUrl(AvatarUtils.getAvatarUrl(user))
                    .totalCo2Emission(Math.round(totalCo2 * 100.0) / 100.0)
                    .totalLogsCount((long) logs.size())
                    .build());
        }

        entries.sort(Comparator.comparing(AdminLeaderboardResponse::getTotalCo2Emission));

        int rank = 1;
        for (AdminLeaderboardResponse entry : entries) {
            entry.setRank(rank++);
        }

        return entries;
    }
}
