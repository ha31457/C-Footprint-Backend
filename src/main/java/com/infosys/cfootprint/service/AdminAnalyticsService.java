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
        double totalCo2 = allLogs.stream().mapToDouble(ActivityLog::getCo2Emission).sum();

        Map<String, Double> categorySums = allLogs.stream()
                .collect(Collectors.groupingBy(
                        ActivityLog::getCategory,
                        Collectors.summingDouble(ActivityLog::getCo2Emission)
                ));

        List<CategoryBreakdownDTO> breakdown = new ArrayList<>();
        List<String> categories = Arrays.asList("transport", "electricity", "food", "shopping");
        for (String cat : categories) {
            double co2 = categorySums.getOrDefault(cat, 0.0);
            double pct = totalCo2 > 0 ? (co2 / totalCo2) * 100 : 0.0;
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
                .totalCo2EmissionKgs(Math.round(totalCo2 * 100.0) / 100.0)
                .categoryBreakdown(breakdown)
                .trend(trend)
                .build();
    }
}
