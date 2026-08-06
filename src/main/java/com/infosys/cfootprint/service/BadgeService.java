package com.infosys.cfootprint.service;

import com.infosys.cfootprint.dto.BadgeResponse;
import com.infosys.cfootprint.dto.CreateBadgeDefinitionRequest;
import com.infosys.cfootprint.dto.LeaderboardResponse;
import com.infosys.cfootprint.exception.BadRequestException;
import com.infosys.cfootprint.model.ActivityLog;
import com.infosys.cfootprint.model.Badge;
import com.infosys.cfootprint.model.BadgeDefinition;
import com.infosys.cfootprint.model.Goal;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.ActivityLogRepository;
import com.infosys.cfootprint.repository.BadgeDefinitionRepository;
import com.infosys.cfootprint.repository.BadgeRepository;
import com.infosys.cfootprint.repository.GoalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BadgeService {

    @Autowired
    private BadgeRepository badgeRepository;

    @Autowired
    private BadgeDefinitionRepository badgeDefinitionRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    @Lazy
    private LeaderboardService leaderboardService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private SystemSettingService systemSettingService;

    @Transactional
    public void checkAndAwardBadges(User user) {
        if (!systemSettingService.isFeatureEnabled("badges_enabled")) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<BadgeDefinition> definitions = badgeDefinitionRepository.findAll();

        if (definitions.isEmpty()) {
            return;
        }

        // Lazy calculations to avoid duplicate db fetches
        Long logCount = null;
        List<ActivityLog> logs = null;
        Long distinctCategories = null;
        List<Goal> goals = null;
        Long completedCount = null;
        Double totalReduction = null;
        Integer userRank = null;

        for (BadgeDefinition def : definitions) {
            if (badgeRepository.existsByUserAndBadgeType(user, def.getBadgeType())) {
                continue;
            }

            boolean qualifies = false;

            switch (def.getRuleType().toUpperCase()) {
                case "LOG_COUNT":
                    if (logCount == null) {
                        logCount = activityLogRepository.countByUser(user);
                    }
                    qualifies = logCount >= def.getRuleValue();
                    break;

                case "DIVERSE_CATEGORIES":
                    if (distinctCategories == null) {
                        if (logs == null) {
                            logs = activityLogRepository.findByUserOrderByLogDateDesc(user);
                        }
                        distinctCategories = logs.stream()
                                .map(ActivityLog::getCategory)
                                .map(String::toLowerCase)
                                .distinct()
                                .count();
                    }
                    qualifies = distinctCategories >= def.getRuleValue();
                    break;

                case "GOALS_COUNT":
                    if (goals == null) {
                        goals = goalRepository.findByUserOrderByStartDateDesc(user);
                    }
                    qualifies = goals.size() >= def.getRuleValue();
                    break;

                case "GOALS_COMPLETED":
                    if (completedCount == null) {
                        if (goals == null) {
                            goals = goalRepository.findByUserOrderByStartDateDesc(user);
                        }
                        completedCount = goals.stream()
                                .filter(g -> "COMPLETED".equals(g.getStatus()))
                                .count();
                    }
                    qualifies = completedCount >= def.getRuleValue();
                    break;

                case "CARBON_REDUCED":
                    if (totalReduction == null) {
                        if (goals == null) {
                            goals = goalRepository.findByUserOrderByStartDateDesc(user);
                        }
                        totalReduction = goals.stream()
                                .filter(g -> "COMPLETED".equals(g.getStatus()))
                                .mapToDouble(g -> g.getBaselineEmission() - g.getTargetEmission())
                                .sum();
                    }
                    qualifies = totalReduction >= def.getRuleValue();
                    break;

                case "LEADERBOARD_RANK":
                    if (userRank == null) {
                        try {
                            LeaderboardResponse lb = leaderboardService.getLeaderboard(user);
                            userRank = (lb != null) ? lb.getCurrentUserRank() : 0;
                        } catch (Exception e) {
                            userRank = 0;
                        }
                    }
                    qualifies = userRank > 0 && userRank <= def.getRuleValue();
                    break;

                default:
                    break;
            }

            if (qualifies) {
                saveBadge(user, def.getBadgeType(), now);
                notificationService.createNotification(
                        user,
                        "New Badge Unlocked: " + def.getTitle() + "!",
                        "BADGE_EARNED"
                );
            }
        }
    }

    @Transactional
    public List<BadgeResponse> getUserBadges(User user) {
        checkAndAwardBadges(user);

        List<Badge> earned = badgeRepository.findByUser(user);
        Map<String, Badge> earnedMap = earned.stream()
                .collect(Collectors.toMap(Badge::getBadgeType, b -> b));

        List<BadgeDefinition> definitions = badgeDefinitionRepository.findAll();

        return definitions.stream()
                .map(def -> {
                    Badge earnedBadge = earnedMap.get(def.getBadgeType());
                    return BadgeResponse.builder()
                            .badgeType(def.getBadgeType())
                            .title(def.getTitle())
                            .description(def.getDescription())
                            .iconName(def.getIconName())
                            .iconUrl(def.getIconUrl())
                            .earnedDate(earnedBadge != null ? earnedBadge.getEarnedDate() : null)
                            .isLocked(earnedBadge == null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public BadgeDefinition createDefinition(CreateBadgeDefinitionRequest request) {
        if (badgeDefinitionRepository.existsByBadgeType(request.getBadgeType().toUpperCase())) {
            throw new BadRequestException("Badge definition already exists with type: " + request.getBadgeType());
        }

        List<String> allowedRules = List.of("LOG_COUNT", "DIVERSE_CATEGORIES", "GOALS_COUNT", "GOALS_COMPLETED", "CARBON_REDUCED", "LEADERBOARD_RANK");
        if (!allowedRules.contains(request.getRuleType().toUpperCase())) {
            throw new BadRequestException("Invalid rule type. Allowed: " + allowedRules);
        }

        BadgeDefinition definition = BadgeDefinition.builder()
                .badgeType(request.getBadgeType().toUpperCase())
                .title(request.getTitle())
                .description(request.getDescription())
                .iconName(request.getIconName())
                .iconUrl(request.getIconUrl())
                .ruleType(request.getRuleType().toUpperCase())
                .ruleValue(request.getRuleValue())
                .build();

        return badgeDefinitionRepository.save(definition);
    }

    @Transactional
    public BadgeDefinition updateDefinition(UUID id, CreateBadgeDefinitionRequest request) {
        BadgeDefinition definition = badgeDefinitionRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Badge definition not found with ID: " + id));

        List<String> allowedRules = List.of("LOG_COUNT", "DIVERSE_CATEGORIES", "GOALS_COUNT", "GOALS_COMPLETED", "CARBON_REDUCED", "LEADERBOARD_RANK");
        if (!allowedRules.contains(request.getRuleType().toUpperCase())) {
            throw new BadRequestException("Invalid rule type. Allowed: " + allowedRules);
        }

        definition.setTitle(request.getTitle());
        definition.setDescription(request.getDescription());
        definition.setIconName(request.getIconName());
        definition.setIconUrl(request.getIconUrl());
        definition.setRuleType(request.getRuleType().toUpperCase());
        definition.setRuleValue(request.getRuleValue());

        return badgeDefinitionRepository.save(definition);
    }

    @Transactional
    public void deleteDefinition(UUID id) {
        BadgeDefinition definition = badgeDefinitionRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Badge definition not found with ID: " + id));

        // Cascade delete earned badges of this badge type
        List<Badge> earned = badgeRepository.findAll().stream()
                .filter(b -> b.getBadgeType().equals(definition.getBadgeType()))
                .collect(Collectors.toList());
        badgeRepository.deleteAll(earned);

        badgeDefinitionRepository.delete(definition);
    }

    public List<BadgeDefinition> getAllDefinitions() {
        return badgeDefinitionRepository.findAll();
    }

    private void saveBadge(User user, String badgeType, LocalDateTime now) {
        Badge badge = Badge.builder()
                .user(user)
                .badgeType(badgeType)
                .earnedDate(now)
                .build();
        badgeRepository.save(badge);
    }
}
