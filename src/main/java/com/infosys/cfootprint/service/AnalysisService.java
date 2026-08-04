package com.infosys.cfootprint.service;

import com.infosys.cfootprint.dto.AdminAnalysisResponse;
import com.infosys.cfootprint.dto.TrendDTO;
import com.infosys.cfootprint.dto.UserAnalysisResponse;
import com.infosys.cfootprint.model.ActivityLog;
import com.infosys.cfootprint.repository.ActivityLogRepository;
import com.infosys.cfootprint.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AnalysisService {

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityLogService activityLogService;

    public UserAnalysisResponse getUserAnalysis(com.infosys.cfootprint.model.User user) {
        List<ActivityLog> logs = activityLogRepository.findByUserOrderByLogDateDesc(user);
        long totalLogs = logs.size();

        Map<String, Long> categoryLogs = new HashMap<>();
        Map<String, Double> categoryEmission = new HashMap<>();

        for (ActivityLog log : logs) {
            String cat = log.getCategory().toLowerCase();
            categoryLogs.put(cat, categoryLogs.getOrDefault(cat, 0L) + 1);
            categoryEmission.put(cat, categoryEmission.getOrDefault(cat, 0.0) + log.getCo2Emission());
        }

        categoryEmission.forEach((key, val) -> categoryEmission.put(key, Math.round(val * 100.0) / 100.0));

        String mostLoggedCategory = categoryLogs.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");

        String highestEmissionCategory = categoryEmission.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");

        double totalAllTimeEmission = logs.stream().mapToDouble(ActivityLog::getCo2Emission).sum();
        List<String> tips = getTips(highestEmissionCategory);
        List<TrendDTO> trend = activityLogService.getUserDashboardData(user, "weekly").getTrend();
        List<String> recommendations = activityLogService.getRecommendationsForUser(user);

        return UserAnalysisResponse.builder()
                .totalLogs(totalLogs)
                .totalAllTimeEmission(Math.round(totalAllTimeEmission * 100.0) / 100.0)
                .categoryLogs(categoryLogs)
                .categoryEmission(categoryEmission)
                .mostLoggedCategory(mostLoggedCategory)
                .highestEmissionCategory(highestEmissionCategory)
                .tips(tips)
                .trend(trend)
                .recommendations(recommendations)
                .build();
    }

    public AdminAnalysisResponse getAdminAnalysis() {
        long totalUsers = userRepository.countByRole("ROLE_USER");
        List<ActivityLog> logs = activityLogRepository.findAll();
        long totalLogs = logs.size();

        Map<String, Long> categoryLogs = new HashMap<>();
        Map<String, Double> categoryEmission = new HashMap<>();
        double grandTotalEmission = 0.0;

        for (ActivityLog log : logs) {
            String cat = log.getCategory().toLowerCase();
            categoryLogs.put(cat, categoryLogs.getOrDefault(cat, 0L) + 1);
            categoryEmission.put(cat, categoryEmission.getOrDefault(cat, 0.0) + log.getCo2Emission());
            grandTotalEmission += log.getCo2Emission();
        }

        categoryEmission.forEach((key, val) -> categoryEmission.put(key, Math.round(val * 100.0) / 100.0));

        String mostLoggedCategory = categoryLogs.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");

        String highestEmissionCategory = categoryEmission.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("None");

        double avgEmission = totalUsers > 0 ? (grandTotalEmission / totalUsers) : 0.0;

        List<String> adminTips = Arrays.asList(
                "Encourage users to log transport reductions.",
                "Promote home energy audits and efficiency campaigns.",
                "Initiate community recycling programs and composting guides."
        );

        return AdminAnalysisResponse.builder()
                .totalUsers(totalUsers)
                .totalLogs(totalLogs)
                .categoryLogs(categoryLogs)
                .categoryEmission(categoryEmission)
                .mostLoggedCategory(mostLoggedCategory)
                .highestEmissionCategory(highestEmissionCategory)
                .averageEmissionPerUser(Math.round(avgEmission * 100.0) / 100.0)
                .tips(adminTips)
                .build();
    }

    private List<String> getTips(String highestCategory) {
        if (highestCategory == null) return getGeneralTips();

        switch (highestCategory.toLowerCase()) {
            case "transport":
            case "transportation":
                return Arrays.asList(
                        "Consider public transit, carpooling, biking, or walking to reduce travel footprint.",
                        "Avoid idling your vehicle to save emissions.",
                        "Choose train travel over short-haul flights whenever possible."
                );
            case "electricity":
                return Arrays.asList(
                        "Turn off lights and electronics when not in use.",
                        "Switch to energy-efficient LED light bulbs to reduce energy consumption.",
                        "Unplug phantom chargers and appliances when away."
                );
            case "food":
                return Arrays.asList(
                        "Try incorporating more plant-based meals into your weekly diet.",
                        "Reduce food waste by planning your weekly grocery list in advance.",
                        "Buy locally sourced, seasonal produce where possible."
                );
            case "shopping":
                return Arrays.asList(
                        "Think twice before buying new products; consider thrifting or buying second-hand.",
                        "Repair and recycle items instead of throwing them away.",
                        "Avoid single-use plastic and paper shopping bags."
                );
            default:
                return getGeneralTips();
        }
    }

    private List<String> getGeneralTips() {
        return Arrays.asList(
                "Track your daily habits and set goals to lower your carbon footprint.",
                "Share sustainability ideas with friends and family."
        );
    }
}
