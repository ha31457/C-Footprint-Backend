package com.infosys.cfootprint.service;

import com.infosys.cfootprint.dto.CreateGoalRequest;
import com.infosys.cfootprint.dto.GoalResponse;
import com.infosys.cfootprint.dto.UpdateGoalRequest;
import com.infosys.cfootprint.exception.BadRequestException;
import com.infosys.cfootprint.model.ActivityLog;
import com.infosys.cfootprint.model.Goal;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.ActivityLogRepository;
import com.infosys.cfootprint.repository.GoalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GoalService {

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private BadgeService badgeService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private NotificationService notificationService;

    @Transactional
    public GoalResponse createGoal(User user, CreateGoalRequest request) {
        String periodType = request.getPeriodType().toUpperCase();
        if (!List.of("WEEKLY", "MONTHLY", "TOTAL").contains(periodType)) {
            throw new BadRequestException("Invalid period type. Must be WEEKLY, MONTHLY, or TOTAL.");
        }

        LocalDate startDate = LocalDate.now();
        LocalDate endDate;

        if (request.getDurationDays() != null && request.getDurationDays() > 0) {
            endDate = startDate.plusDays(request.getDurationDays() - 1);
        } else if ("WEEKLY".equals(periodType)) {
            endDate = startDate.plusDays(6);
        } else if ("MONTHLY".equals(periodType)) {
            endDate = startDate.plusDays(29);
        } else {
            endDate = startDate.plusDays(364);
        }

        // Calculate baseline emission from prior equivalent period
        double baseline;
        if ("WEEKLY".equals(periodType)) {
            double prior = sumEmissions(user, startDate.minusDays(7), startDate.minusDays(1));
            baseline = prior > 0 ? prior : 20.0;
        } else if ("MONTHLY".equals(periodType)) {
            double prior = sumEmissions(user, startDate.minusDays(30), startDate.minusDays(1));
            baseline = prior > 0 ? prior : 80.0;
        } else {
            double prior = sumEmissions(user, LocalDate.of(2000, 1, 1), startDate.minusDays(1));
            baseline = prior > 0 ? prior : 100.0;
        }

        double targetEmission = baseline * (1.0 - (request.getTargetReductionPercentage() / 100.0));

        Goal goal = Goal.builder()
                .targetReductionPercentage(request.getTargetReductionPercentage())
                .periodType(periodType)
                .startDate(startDate)
                .endDate(endDate)
                .baselineEmission(Math.round(baseline * 100.0) / 100.0)
                .targetEmission(Math.round(targetEmission * 100.0) / 100.0)
                .status("ACTIVE")
                .user(user)
                .build();

        Goal saved = goalRepository.save(goal);
        notificationService.createNotification(
                user,
                "Goal setup successfully! Target: " + saved.getTargetReductionPercentage() + "% reduction over " + saved.getPeriodType().toLowerCase() + " period.",
                "GOAL_CREATED"
        );
        badgeService.checkAndAwardBadges(user);

        try {
            String subject = "New Carbon Reduction Goal Set!";
            String htmlContent = "<div style=\"font-family: Arial, sans-serif; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px; max-width: 600px;\">" +
                    "<h2 style=\"color: #2b6cb0;\">Goal Confirmation</h2>" +
                    "<p>Hello <strong>" + user.getUsername() + "</strong>,</p>" +
                    "<p>You have successfully set a new sustainability goal:</p>" +
                    "<ul>" +
                    "<li><strong>Reduction Target:</strong> " + saved.getTargetReductionPercentage() + "%</li>" +
                    "<li><strong>Period Type:</strong> " + saved.getPeriodType() + "</li>" +
                    "<li><strong>Duration:</strong> " + saved.getStartDate() + " to " + saved.getEndDate() + "</li>" +
                    "<li><strong>Baseline Emission:</strong> " + saved.getBaselineEmission() + " kg CO₂</li>" +
                    "<li><strong>Target Emission Limit:</strong> " + saved.getTargetEmission() + " kg CO₂</li>" +
                    "</ul>" +
                    "<p>Stay green and track your activities daily to hit your target!</p>" +
                    "<hr style=\"border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;\" />" +
                    "<p style=\"font-size: 12px; color: #718096;\">Carbon Footprint Tracker and Goal Management System</p>" +
                    "</div>";
            emailService.sendHtmlEmail(user.getEmail(), subject, htmlContent);
        } catch (Exception e) {
            System.err.println("Failed to send goal creation email: " + e.getMessage());
        }

        return buildGoalResponse(saved, user);
    }

    public List<GoalResponse> getActiveGoals(User user) {
        return goalRepository.findByUserAndStatusOrderByStartDateDesc(user, "ACTIVE").stream()
                .map(goal -> buildGoalResponse(goal, user))
                .collect(Collectors.toList());
    }

    public GoalResponse getActiveGoal(User user) {
        List<GoalResponse> active = getActiveGoals(user);
        return active.isEmpty() ? null : active.get(0);
    }

    public List<GoalResponse> getUserGoals(User user) {
        return goalRepository.findByUserOrderByStartDateDesc(user).stream()
                .map(goal -> buildGoalResponse(goal, user))
                .collect(Collectors.toList());
    }

    @Transactional
    public GoalResponse updateGoal(User user, UUID goalId, UpdateGoalRequest request) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new BadRequestException("Goal not found with ID: " + goalId));

        if (!goal.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Goal does not belong to the authenticated user");
        }

        if (request.getTargetReductionPercentage() != null) {
            goal.setTargetReductionPercentage(request.getTargetReductionPercentage());
            double newTarget = goal.getBaselineEmission() * (1.0 - (request.getTargetReductionPercentage() / 100.0));
            goal.setTargetEmission(Math.round(newTarget * 100.0) / 100.0);
        }

        if (request.getPeriodType() != null && !request.getPeriodType().isBlank()) {
            String periodType = request.getPeriodType().toUpperCase();
            if (!List.of("WEEKLY", "MONTHLY", "TOTAL").contains(periodType)) {
                throw new BadRequestException("Invalid period type. Must be WEEKLY, MONTHLY, or TOTAL.");
            }
            goal.setPeriodType(periodType);
        }

        if (request.getDurationDays() != null && request.getDurationDays() > 0) {
            goal.setEndDate(goal.getStartDate().plusDays(request.getDurationDays() - 1));
        }

        Goal saved = goalRepository.save(goal);
        badgeService.checkAndAwardBadges(user);
        return buildGoalResponse(saved, user);
    }

    @Transactional
    public GoalResponse cancelGoal(User user, UUID goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new BadRequestException("Goal not found with ID: " + goalId));

        if (!goal.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Goal does not belong to the authenticated user");
        }

        goal.setStatus("CANCELLED");
        Goal saved = goalRepository.save(goal);
        return buildGoalResponse(saved, user);
    }

    @Transactional
    public void deleteGoal(User user, UUID goalId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new BadRequestException("Goal not found with ID: " + goalId));

        if (!goal.getUser().getId().equals(user.getId())) {
            throw new BadRequestException("Goal does not belong to the authenticated user");
        }

        goalRepository.delete(goal);
    }

    @Transactional
    public GoalResponse buildGoalResponse(Goal goal, User user) {
        LocalDate today = LocalDate.now();

        // Calculate current emissions within goal period
        double currentEmission = sumEmissions(user, goal.getStartDate(), today);
        currentEmission = Math.round(currentEmission * 100.0) / 100.0;

        // Auto-complete or fail if period ended
        if ("ACTIVE".equals(goal.getStatus()) && today.isAfter(goal.getEndDate())) {
            String newStatus;
            String msg;
            String type;
            if (currentEmission <= goal.getTargetEmission()) {
                newStatus = "COMPLETED";
                msg = "Goal Completed: You successfully achieved your " + goal.getTargetReductionPercentage() + "% carbon reduction target!";
                type = "GOAL_COMPLETED";
            } else {
                newStatus = "FAILED";
                msg = "Goal Failed: Your emissions during the period exceeded the target limit of " + goal.getTargetEmission() + " kg CO₂.";
                type = "GOAL_FAILED";
            }
            goal.setStatus(newStatus);
            goalRepository.save(goal);
            notificationService.createNotification(user, msg, type);
            badgeService.checkAndAwardBadges(user);
        }

        long totalPeriodDays = Math.max(1, ChronoUnit.DAYS.between(goal.getStartDate(), goal.getEndDate()) + 1);
        long elapsedDays = Math.max(1, ChronoUnit.DAYS.between(goal.getStartDate(), today.isAfter(goal.getEndDate()) ? goal.getEndDate() : today) + 1);

        double progressFraction = Math.min(1.0, elapsedDays / (double) totalPeriodDays);
        double allowedEmissionToday = goal.getBaselineEmission() - ((goal.getBaselineEmission() - goal.getTargetEmission()) * progressFraction);
        allowedEmissionToday = Math.round(allowedEmissionToday * 100.0) / 100.0;
        boolean isOnTrack = currentEmission <= allowedEmissionToday;

        double progressPercentage;
        if ("COMPLETED".equals(goal.getStatus())) {
            progressPercentage = 100.0;
        } else if ("FAILED".equals(goal.getStatus()) || "CANCELLED".equals(goal.getStatus())) {
            progressPercentage = progressFraction * 100.0;
        } else {
            if (currentEmission <= allowedEmissionToday) {
                progressPercentage = progressFraction * 100.0;
            } else {
                double ratio = currentEmission > 0 ? (allowedEmissionToday / currentEmission) : 1.0;
                progressPercentage = progressFraction * ratio * 100.0;
            }
        }
        progressPercentage = Math.max(0.0, Math.min(100.0, Math.round(progressPercentage * 100.0) / 100.0));

        double actualReduction = goal.getBaselineEmission() - currentEmission;

        String alertMessage;
        if ("COMPLETED".equals(goal.getStatus())) {
            alertMessage = "🌟 Goal Achieved! Congratulations on hitting your " + goal.getTargetReductionPercentage() + "% carbon reduction target!";
        } else if ("FAILED".equals(goal.getStatus())) {
            alertMessage = "❌ Goal Period Ended: Your emissions (" + currentEmission + " kg) exceeded the target limit (" + goal.getTargetEmission() + " kg). Set a new goal to try again!";
        } else if ("CANCELLED".equals(goal.getStatus())) {
            alertMessage = "Goal has been cancelled.";
        } else if (isOnTrack && progressPercentage >= 75.0) {
            alertMessage = "🎉 Outstanding work! You have reduced emissions by " + Math.round(actualReduction * 100.0) / 100.0 + " kg and are well on track to exceed your " + goal.getTargetReductionPercentage() + "% reduction target!";
        } else if (isOnTrack) {
            alertMessage = "👍 On Track! Your current emission pace (" + currentEmission + " kg) is within the budget for your " + goal.getTargetReductionPercentage() + "% reduction target.";
        } else {
            alertMessage = "⚠️ Correction Alert: Your emissions (" + currentEmission + " kg) are currently higher than projected limit (" + Math.round(allowedEmissionToday * 100.0) / 100.0 + " kg). Try reducing car usage or energy consumption this week!";
        }

        return GoalResponse.builder()
                .id(goal.getId())
                .targetReductionPercentage(goal.getTargetReductionPercentage())
                .periodType(goal.getPeriodType())
                .startDate(goal.getStartDate())
                .endDate(goal.getEndDate())
                .baselineEmission(goal.getBaselineEmission())
                .currentEmission(currentEmission)
                .targetEmission(goal.getTargetEmission())
                .progressPercentage(progressPercentage)
                .isOnTrack(isOnTrack)
                .status(goal.getStatus())
                .alertMessage(alertMessage)
                .build();
    }

    @Transactional
    public void checkGoalThresholds(User user, double addedCo2) {
        List<Goal> activeGoals = goalRepository.findByUserAndStatusOrderByStartDateDesc(user, "ACTIVE");
        for (Goal goal : activeGoals) {
            if (goal.isLimitAlertSent()) {
                continue;
            }
            double currentEmission = sumEmissions(user, goal.getStartDate(), LocalDate.now());
            double totalNew = currentEmission + addedCo2;
            if (totalNew >= 0.8 * goal.getTargetEmission() && totalNew <= goal.getTargetEmission()) {
                goal.setLimitAlertSent(true);
                goalRepository.save(goal);

                notificationService.createNotification(
                        user,
                        "Warning: You have consumed over 80% of your allowed carbon budget (" + (Math.round(totalNew * 100.0) / 100.0) + " kg CO₂) for your " + goal.getPeriodType().toLowerCase() + " goal.",
                        "GOAL_WARNING"
                );

                try {
                    String subject = "Warning: Approaching Carbon Budget Limit!";
                    String htmlContent = "<div style=\"font-family: Arial, sans-serif; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px; max-width: 600px;\">" +
                            "<h2 style=\"color: #c53030;\">Emissions Alert Warning</h2>" +
                            "<p>Hello <strong>" + user.getUsername() + "</strong>,</p>" +
                            "<p>You are approaching the carbon emission limit for one of your set goals:</p>" +
                            "<ul>" +
                            "<li><strong>Goal Period:</strong> " + goal.getPeriodType() + " (" + goal.getStartDate() + " to " + goal.getEndDate() + ")</li>" +
                            "<li><strong>Target Limit:</strong> " + goal.getTargetEmission() + " kg CO₂</li>" +
                            "<li><strong>Current Emissions:</strong> " + Math.round(totalNew * 100.0) / 100.0 + " kg CO₂</li>" +
                            "</ul>" +
                            "<p style=\"color: #c53030; font-weight: bold;\">You have consumed over 80% of your allowed carbon budget. Try to conserve energy or reduce travel to stay within your goal limit!</p>" +
                            "<hr style=\"border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;\" />" +
                            "<p style=\"font-size: 12px; color: #718096;\">Carbon Footprint Tracker and Goal Management System</p>" +
                            "</div>";
                    emailService.sendHtmlEmail(user.getEmail(), subject, htmlContent);
                } catch (Exception e) {
                    System.err.println("Failed to send budget warning email: " + e.getMessage());
                }
            }
        }
    }

    private double sumEmissions(User user, LocalDate start, LocalDate end) {
        return activityLogRepository.findByUserAndLogDateBetweenOrderByLogDateAsc(user, start, end).stream()
                .mapToDouble(ActivityLog::getCo2Emission)
                .sum();
    }
}
