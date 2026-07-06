package com.infosys.cfootprint.service;

import com.infosys.cfootprint.dto.*;
import com.infosys.cfootprint.exception.BadRequestException;
import com.infosys.cfootprint.model.ActivityLog;
import com.infosys.cfootprint.model.EmissionFactor;
import com.infosys.cfootprint.model.User;
import com.infosys.cfootprint.repository.ActivityLogRepository;
import com.infosys.cfootprint.repository.EmissionFactorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ActivityLogService {

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private EmissionFactorRepository emissionFactorRepository;

    @Transactional
    public ActivityLogResponse logActivity(ActivityLogRequest request, User user) {
        EmissionFactor factor = emissionFactorRepository.findByCategoryAndActivityType(
                request.getCategory().toLowerCase(),
                request.getActivityType()
        ).orElseThrow(() -> new BadRequestException(
                "Invalid activity type: " + request.getActivityType() + " for category: " + request.getCategory()
        ));

        // Calculate CO2 emission: quantity * factor
        double co2 = request.getQuantity() * factor.getFactor();

        ActivityLog log = ActivityLog.builder()
                .category(request.getCategory().toLowerCase())
                .activityType(request.getActivityType())
                .quantity(request.getQuantity())
                .unit(request.getUnit())
                .co2Emission(co2)
                .logDate(request.getLogDate())
                .user(user)
                .build();

        ActivityLog saved = activityLogRepository.save(log);

        return mapToResponse(saved);
    }

    public List<ActivityLogResponse> getUserLogs(User user) {
        return activityLogRepository.findByUserOrderByLogDateDesc(user)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public UserDashboardResponse getUserDashboardData(User user) {
        LocalDate today = LocalDate.now();

        // 1. Today's emission
        double todayTotal = activityLogRepository.findByUserAndLogDate(user, today)
                .stream()
                .mapToDouble(ActivityLog::getCo2Emission)
                .sum();

        // 2. Category Breakdown (All-time or overall user log history)
        List<ActivityLog> allLogs = activityLogRepository.findByUserOrderByLogDateDesc(user);
        double allTimeTotal = allLogs.stream().mapToDouble(ActivityLog::getCo2Emission).sum();

        Map<String, Double> categorySums = allLogs.stream()
                .collect(Collectors.groupingBy(
                        ActivityLog::getCategory,
                        Collectors.summingDouble(ActivityLog::getCo2Emission)
                ));

        List<CategoryBreakdownDTO> breakdown = new ArrayList<>();
        // Make sure all 4 categories exist in breakdown
        List<String> categories = Arrays.asList("transport", "electricity", "food", "shopping");
        for (String cat : categories) {
            double co2 = categorySums.getOrDefault(cat, 0.0);
            double pct = allTimeTotal > 0 ? (co2 / allTimeTotal) * 100 : 0.0;
            breakdown.add(CategoryBreakdownDTO.builder()
                    .category(cat)
                    .co2Emission(Math.round(co2 * 100.0) / 100.0)
                    .percentage(Math.round(pct * 100.0) / 100.0)
                    .build());
        }

        // 3. Weekly Trend (Last 7 days, including today)
        LocalDate startDate = today.minusDays(6);
        List<ActivityLog> weeklyLogs = activityLogRepository.findByUserAndLogDateBetweenOrderByLogDateAsc(user, startDate, today);
        Map<LocalDate, Double> dailySums = weeklyLogs.stream()
                .collect(Collectors.groupingBy(
                        ActivityLog::getLogDate,
                        Collectors.summingDouble(ActivityLog::getCo2Emission)
                ));

        List<WeeklyTrendDTO> trend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            trend.add(WeeklyTrendDTO.builder()
                    .date(date)
                    .co2Emission(Math.round(dailySums.getOrDefault(date, 0.0) * 100.0) / 100.0)
                    .build());
        }

        return UserDashboardResponse.builder()
                .todayTotalEmission(Math.round(todayTotal * 100.0) / 100.0)
                .categoryBreakdown(breakdown)
                .weeklyTrend(trend)
                .build();
    }

    private ActivityLogResponse mapToResponse(ActivityLog log) {
        return ActivityLogResponse.builder()
                .id(log.getId())
                .category(log.getCategory())
                .activityType(log.getActivityType())
                .quantity(log.getQuantity())
                .unit(log.getUnit())
                .co2Emission(Math.round(log.getCo2Emission() * 100.0) / 100.0)
                .logDate(log.getLogDate())
                .build();
    }
}
