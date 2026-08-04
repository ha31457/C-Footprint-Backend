package com.infosys.cfootprint.service;

import com.infosys.cfootprint.dto.AdminActivityAnalyticsResponse;
import com.infosys.cfootprint.dto.AdminUserAnalyticsResponse;
import com.infosys.cfootprint.dto.CategoryBreakdownDTO;
import com.infosys.cfootprint.dto.TrendDTO;
import com.infosys.cfootprint.model.ActivityLog;
import com.infosys.cfootprint.repository.ActivityLogRepository;
import com.infosys.cfootprint.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminAnalyticsService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private ActivityLogService activityLogService;

    public AdminUserAnalyticsResponse getUserAnalytics() {
        long total = userRepository.count();
        long enabled = userRepository.findAll().stream().filter(u -> u.isEnabled()).count();
        long disabled = total - enabled;

        return AdminUserAnalyticsResponse.builder()
                .totalUsers(total)
                .enabledUsers(enabled)
                .disabledUsers(disabled)
                .build();
    }

    public AdminActivityAnalyticsResponse getPlatformActivityAnalytics(String range) {
        LocalDate today = LocalDate.now();
        List<ActivityLog> allLogs = activityLogRepository.findAll();

        long totalLogs = allLogs.size();
        long logsToday = allLogs.stream().filter(log -> log.getLogDate().equals(today)).count();

        LocalDate startDate;
        switch (range.toLowerCase()) {
            case "daily":
                startDate = today;
                break;
            case "weekly":
                startDate = today.minusDays(6);
                break;
            case "monthly":
                startDate = today.minusDays(29);
                break;
            case "yearly":
                startDate = today.minusYears(1);
                break;
            default:
                startDate = null;
        }

        List<ActivityLog> filteredLogsForBreakdown = allLogs;
        if (startDate != null) {
            filteredLogsForBreakdown = allLogs.stream()
                    .filter(log -> !log.getLogDate().isBefore(startDate) && !log.getLogDate().isAfter(today))
                    .collect(Collectors.toList());
        }

        double rangeTotalCo2 = filteredLogsForBreakdown.stream().mapToDouble(ActivityLog::getCo2Emission).sum();

        Map<String, Double> categorySums = filteredLogsForBreakdown.stream()
                .collect(Collectors.groupingBy(
                        ActivityLog::getCategory,
                        Collectors.summingDouble(ActivityLog::getCo2Emission)
                ));

        List<CategoryBreakdownDTO> breakdown = new ArrayList<>();
        List<String> categories = Arrays.asList("transport", "electricity", "food", "shopping", "waste", "water", "heating", "other");
        for (String cat : categories) {
            double co2 = categorySums.getOrDefault(cat, 0.0);
            double pct = rangeTotalCo2 > 0 ? (co2 / rangeTotalCo2) * 100 : 0.0;
            breakdown.add(CategoryBreakdownDTO.builder()
                    .category(cat)
                    .co2Emission(Math.round(co2 * 100.0) / 100.0)
                    .percentage(Math.round(pct * 100.0) / 100.0)
                    .build());
        }

        List<TrendDTO> trend = activityLogService.calculateTrend(allLogs, range);

        return AdminActivityAnalyticsResponse.builder()
                .totalLogs(totalLogs)
                .logsLoggedToday(logsToday)
                .totalCo2EmissionKgs(Math.round(rangeTotalCo2 * 100.0) / 100.0)
                .categoryBreakdown(breakdown)
                .trend(trend)
                .build();
    }
}
