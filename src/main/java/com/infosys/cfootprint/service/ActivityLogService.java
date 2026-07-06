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

    public UserDashboardResponse getUserDashboardData(User user, String range) {
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

        // 3. Weekly Trend (Last 7 days, including today) - Keep for backward compatibility
        LocalDate startDate = today.minusDays(6);
        List<ActivityLog> weeklyLogs = activityLogRepository.findByUserAndLogDateBetweenOrderByLogDateAsc(user, startDate, today);
        Map<LocalDate, Double> dailySums = weeklyLogs.stream()
                .collect(Collectors.groupingBy(
                        ActivityLog::getLogDate,
                        Collectors.summingDouble(ActivityLog::getCo2Emission)
                ));

        List<WeeklyTrendDTO> weeklyTrend = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            weeklyTrend.add(WeeklyTrendDTO.builder()
                    .date(date)
                    .co2Emission(Math.round(dailySums.getOrDefault(date, 0.0) * 100.0) / 100.0)
                    .build());
        }

        // 4. Generic Multi-Range Trend
        List<TrendDTO> trend = calculateTrend(allLogs, range);

        return UserDashboardResponse.builder()
                .todayTotalEmission(Math.round(todayTotal * 100.0) / 100.0)
                .categoryBreakdown(breakdown)
                .weeklyTrend(weeklyTrend)
                .trend(trend)
                .build();
    }

    public List<TrendDTO> calculateTrend(List<ActivityLog> logs, String range) {
        LocalDate today = LocalDate.now();
        List<TrendDTO> trend = new ArrayList<>();

        if (range == null) {
            range = "daily";
        }
        range = range.toLowerCase();

        switch (range) {
            case "weekly":
                LocalDate currentMonday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                List<LocalDate> mondays = new ArrayList<>();
                for (int i = 3; i >= 0; i--) {
                    mondays.add(currentMonday.minusWeeks(i));
                }

                Map<LocalDate, Double> weeklySums = new HashMap<>();
                for (ActivityLog log : logs) {
                    LocalDate logMonday = log.getLogDate().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
                    weeklySums.put(logMonday, weeklySums.getOrDefault(logMonday, 0.0) + log.getCo2Emission());
                }

                for (LocalDate monday : mondays) {
                    trend.add(TrendDTO.builder()
                            .label("W/C " + monday.toString())
                            .co2Emission(Math.round(weeklySums.getOrDefault(monday, 0.0) * 100.0) / 100.0)
                            .build());
                }
                break;

            case "monthly":
                java.time.format.DateTimeFormatter monthFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy");
                List<java.time.YearMonth> yearMonths = new ArrayList<>();
                for (int i = 5; i >= 0; i--) {
                    yearMonths.add(java.time.YearMonth.from(today.minusMonths(i)));
                }

                Map<java.time.YearMonth, Double> monthlySums = new HashMap<>();
                for (ActivityLog log : logs) {
                    java.time.YearMonth logYM = java.time.YearMonth.from(log.getLogDate());
                    monthlySums.put(logYM, monthlySums.getOrDefault(logYM, 0.0) + log.getCo2Emission());
                }

                for (java.time.YearMonth ym : yearMonths) {
                    trend.add(TrendDTO.builder()
                            .label(ym.format(monthFormatter))
                            .co2Emission(Math.round(monthlySums.getOrDefault(ym, 0.0) * 100.0) / 100.0)
                            .build());
                }
                break;

            case "yearly":
                List<Integer> years = new ArrayList<>();
                for (int i = 2; i >= 0; i--) {
                    years.add(today.getYear() - i);
                }

                Map<Integer, Double> yearlySums = new HashMap<>();
                for (ActivityLog log : logs) {
                    int logYear = log.getLogDate().getYear();
                    yearlySums.put(logYear, yearlySums.getOrDefault(logYear, 0.0) + log.getCo2Emission());
                }

                for (Integer year : years) {
                    trend.add(TrendDTO.builder()
                            .label(year.toString())
                            .co2Emission(Math.round(yearlySums.getOrDefault(year, 0.0) * 100.0) / 100.0)
                            .build());
                }
                break;

            case "daily":
            default:
                List<LocalDate> dates = new ArrayList<>();
                for (int i = 6; i >= 0; i--) {
                    dates.add(today.minusDays(i));
                }

                Map<LocalDate, Double> dailySums = new HashMap<>();
                for (ActivityLog log : logs) {
                    dailySums.put(log.getLogDate(), dailySums.getOrDefault(log.getLogDate(), 0.0) + log.getCo2Emission());
                }

                for (LocalDate date : dates) {
                    trend.add(TrendDTO.builder()
                            .label(date.toString())
                            .co2Emission(Math.round(dailySums.getOrDefault(date, 0.0) * 100.0) / 100.0)
                            .build());
                }
                break;
        }

        return trend;
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
