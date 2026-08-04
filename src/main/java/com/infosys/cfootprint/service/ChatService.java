package com.infosys.cfootprint.service;

import com.infosys.cfootprint.dto.ChatResponse;
import com.infosys.cfootprint.model.ActivityLog;
import com.infosys.cfootprint.model.Badge;
import com.infosys.cfootprint.model.Goal;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.ActivityLogRepository;
import com.infosys.cfootprint.repository.BadgeRepository;
import com.infosys.cfootprint.repository.GoalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    @Autowired
    private GroqService groqService;

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private BadgeRepository badgeRepository;

    private static final String PLATFORM_CONTEXT =
            "You are the chatbot assistant for the Carbon Footprint Tracker platform. " +
            "Here is the context of our platform:\n" +
            "- It is a Carbon Footprint tracking application.\n" +
            "- Users can log daily activities under categories like Transport (e.g. driving, public transit), " +
            "Energy (electricity, heating, gas), Waste (recycling, landfill), and Food.\n" +
            "- Users can earn badges (e.g., Eco Starter, Carbon Minimizer, Active Logger) by tracking consistently or hitting goals.\n" +
            "- Users can set monthly/weekly carbon goals and target budgets, receiving notifications on threshold triggers (like exceeding 80% of budget).\n" +
            "- There is a Leaderboard ranking users by lowest emissions (which penalizes inactive users with a 15 kg daily default penalty to promote active tracking).\n" +
            "- There is an In-App Notification panel showing real-time feedback, and a Support Page for submitting complaints or queries to admin.\n" +
            "Answer the user's question about the platform or general sustainability accurately using this information.";

    public ChatResponse handleChat(User user, String userQuery) {
        boolean isContextual = groqService.isContextualQuery(userQuery);
        String prompt;

        if (isContextual) {
            LocalDate today = LocalDate.now();
            LocalDate startOfWeek = today.minusDays(7);

            List<ActivityLog> logs = activityLogRepository.findByUserOrderByLogDateDesc(user);
            List<ActivityLog> weeklyLogs = logs.stream()
                    .filter(log -> !log.getLogDate().isBefore(startOfWeek))
                    .collect(Collectors.toList());

            double weeklyCo2 = weeklyLogs.stream()
                    .mapToDouble(ActivityLog::getCo2Emission)
                    .sum();

            List<Goal> activeGoals = goalRepository.findByUserAndStatusOrderByStartDateDesc(user, "IN_PROGRESS");
            List<Badge> badges = badgeRepository.findByUser(user);

            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("User Specific Context:\n");
            contextBuilder.append("- Username: ").append(user.getUsername()).append("\n");
            contextBuilder.append("- Logged CO2 emissions in the last 7 days: ").append(Math.round(weeklyCo2 * 100.0) / 100.0).append(" kg CO2\n");
            
            if (!weeklyLogs.isEmpty()) {
                contextBuilder.append("- Recent activities:\n");
                weeklyLogs.stream().limit(5).forEach(l -> {
                    contextBuilder.append("  * ").append(l.getCategory()).append(" -> ")
                            .append(l.getActivityType()).append(" (").append(l.getQuantity()).append(" ")
                            .append(l.getUnit()).append(", ").append(Math.round(l.getCo2Emission() * 100.0) / 100.0).append(" kg CO2) on ")
                            .append(l.getLogDate()).append("\n");
                });
            }

            if (!activeGoals.isEmpty()) {
                contextBuilder.append("- Active Goals:\n");
                activeGoals.forEach(g -> {
                    contextBuilder.append("  * Target Budget: ").append(g.getTargetEmission()).append(" kg CO2 (Period: ")
                            .append(g.getPeriodType()).append(", Started: ").append(g.getStartDate()).append(")\n");
                });
            }

            if (!badges.isEmpty()) {
                contextBuilder.append("- Earned Badges: ");
                String badgeNames = badges.stream()
                        .map(Badge::getBadgeType)
                        .collect(Collectors.joining(", "));
                contextBuilder.append(badgeNames).append("\n");
            }

            prompt = PLATFORM_CONTEXT + "\n\n" + contextBuilder.toString() + "\n\nUser Question: " + userQuery;
        } else {
            prompt = PLATFORM_CONTEXT + "\n\nUser Question: " + userQuery;
        }

        String aiResponse = geminiService.generateContent(prompt);

        return ChatResponse.builder()
                .response(aiResponse)
                .isContextual(isContextual)
                .build();
    }
}
