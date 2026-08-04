package com.infosys.cfootprint.service;

import com.infosys.cfootprint.dto.LeaderboardResponse;
import com.infosys.cfootprint.model.ActivityLog;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.ActivityLogRepository;
import com.infosys.cfootprint.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@EnableScheduling
public class ScheduledNotificationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    @Lazy
    private LeaderboardService leaderboardService;

    @Scheduled(cron = "0 0 8 1 * *")
    public void sendMonthlyAnalytics() {
        List<User> users = userRepository.findAll().stream()
                .filter(u -> "ROLE_USER".equals(u.getRole()))
                .filter(u -> !u.isDisabled())
                .filter(User::isEnabled)
                .collect(Collectors.toList());

        LocalDate today = LocalDate.now();
        LocalDate startOfLastMonth = today.minusMonths(1).withDayOfMonth(1);
        LocalDate endOfLastMonth = today.minusMonths(1).withDayOfMonth(today.minusMonths(1).lengthOfMonth());

        for (User user : users) {
            List<ActivityLog> logs = activityLogRepository.findByUserAndLogDateBetweenOrderByLogDateAsc(user, startOfLastMonth, endOfLastMonth);

            double totalCo2 = logs.stream()
                    .mapToDouble(ActivityLog::getCo2Emission)
                    .sum();
            totalCo2 = Math.round(totalCo2 * 100.0) / 100.0;

            Map<String, Double> categoryBreakdown = logs.stream()
                    .collect(Collectors.groupingBy(
                            ActivityLog::getCategory,
                            Collectors.summingDouble(ActivityLog::getCo2Emission)
                    ));

            StringBuilder breakdownHtml = new StringBuilder();
            if (categoryBreakdown.isEmpty()) {
                breakdownHtml.append("<p style=\"color: #718096; font-style: italic;\">No activities logged last month.</p>");
            } else {
                breakdownHtml.append("<table style=\"width: 100%; border-collapse: collapse; margin-top: 10px;\">")
                        .append("<thead><tr style=\"background: #f7fafc; text-align: left;\">")
                        .append("<th style=\"padding: 8px; border-bottom: 1px solid #e2e8f0;\">Category</th>")
                        .append("<th style=\"padding: 8px; border-bottom: 1px solid #e2e8f0;\">Emissions (kg CO₂)</th>")
                        .append("</tr></thead><tbody>");

                for (Map.Entry<String, Double> entry : categoryBreakdown.entrySet()) {
                    breakdownHtml.append("<tr>")
                            .append("<td style=\"padding: 8px; border-bottom: 1px solid #e2e8f0; text-transform: capitalize; text-align: left;\">").append(entry.getKey()).append("</td>")
                            .append("<td style=\"padding: 8px; border-bottom: 1px solid #e2e8f0;\">").append(Math.round(entry.getValue() * 100.0) / 100.0).append("</td>")
                            .append("</tr>");
                }
                breakdownHtml.append("</tbody></table>");
            }

            double average = 0.0;
            try {
                LeaderboardResponse lb = leaderboardService.getLeaderboard(user);
                if (lb != null) {
                    average = lb.getAverageEmission();
                }
            } catch (Exception e) {
                // Ignore
            }

            String subject = "Your Monthly Carbon Footprint Analytics Report";
            String htmlContent = "<div style=\"font-family: Arial, sans-serif; padding: 20px; border: 1px solid #e2e8f0; border-radius: 8px; max-width: 600px;\">" +
                    "<h2 style=\"color: #2f855a; margin-bottom: 5px;\">Monthly Analytics Report</h2>" +
                    "<p style=\"color: #718096; font-size: 14px; margin-top: 0;\">For the period: " + startOfLastMonth + " to " + endOfLastMonth + "</p>" +
                    "<p>Hello <strong>" + user.getUsername() + "</strong>,</p>" +
                    "<p>Here is your sustainability diagnostics performance report for the past month:</p>" +
                    "<div style=\"background: #edf2f7; padding: 15px; border-radius: 6px; margin: 20px 0;\">" +
                    "<p style=\"margin: 0; font-size: 14px; color: #4a5568;\">Your Total Emissions:</p>" +
                    "<h1 style=\"margin: 5px 0 0 0; color: #2f855a;\">" + totalCo2 + " <small style=\"font-size: 16px; font-weight: normal;\">kg CO₂e</small></h1>" +
                    (average > 0 ? "<p style=\"margin: 8px 0 0 0; font-size: 12px; color: #718096;\">Community Average: " + average + " kg CO₂e</p>" : "") +
                    "</div>" +
                    "<h3 style=\"color: #2d3748; margin-top: 25px;\">Emissions by Category</h3>" +
                    breakdownHtml.toString() +
                    "<p style=\"margin-top: 25px;\">Keep logging your habits to make green choices and reduce your carbon footprint next month!</p>" +
                    "<hr style=\"border: none; border-top: 1px solid #e2e8f0; margin: 20px 0;\" />" +
                    "<p style=\"font-size: 12px; color: #718096;\">Carbon Footprint Tracker and Scheduled Notification Service</p>" +
                    "</div>";

            emailService.sendHtmlEmail(user.getEmail(), subject, htmlContent);
        }
    }
}
