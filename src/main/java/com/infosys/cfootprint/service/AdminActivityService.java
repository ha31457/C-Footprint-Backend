package com.infosys.cfootprint.service;

import com.infosys.cfootprint.dto.AdminActivityLogResponse;
import com.infosys.cfootprint.dto.AdminFilteredActivitiesResponse;
import com.infosys.cfootprint.model.ActivityLog;
import com.infosys.cfootprint.repository.ActivityLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminActivityService {

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Transactional(readOnly = true)
    public AdminFilteredActivitiesResponse getPlatformActivities(String range, LocalDate date, String category) {
        List<ActivityLog> logs;

        if (date != null) {
            logs = activityLogRepository.findByLogDate(date);
        } else if (range != null && !range.isBlank()) {
            LocalDate today = LocalDate.now();
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

            if (startDate != null) {
                logs = activityLogRepository.findByLogDateBetweenOrderByLogDateDesc(startDate, today);
            } else {
                logs = activityLogRepository.findAllByOrderByLogDateDesc();
            }
        } else {
            logs = activityLogRepository.findAllByOrderByLogDateDesc();
        }

        if (category != null && !category.isBlank()) {
            logs = logs.stream()
                    .filter(log -> log.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }

        List<AdminActivityLogResponse> mappedLogs = logs.stream().map(log -> AdminActivityLogResponse.builder()
                .id(log.getId())
                .category(log.getCategory())
                .activityType(log.getActivityType())
                .quantity(log.getQuantity())
                .unit(log.getUnit())
                .co2Emission(Math.round(log.getCo2Emission() * 100.0) / 100.0)
                .logDate(log.getLogDate())
                .userId(log.getUser().getId())
                .username(log.getUser().getUsername())
                .userEmail(log.getUser().getEmail())
                .imageProofId(log.getImageProofId())
                .build()
        ).collect(Collectors.toList());

        double totalCo2 = mappedLogs.stream()
                .mapToDouble(AdminActivityLogResponse::getCo2Emission)
                .sum();

        Map<String, Double> breakdown = mappedLogs.stream()
                .collect(Collectors.groupingBy(
                        AdminActivityLogResponse::getCategory,
                        Collectors.summingDouble(AdminActivityLogResponse::getCo2Emission)
                ));
        breakdown.forEach((k, v) -> breakdown.put(k, Math.round(v * 100.0) / 100.0));

        return AdminFilteredActivitiesResponse.builder()
                .activities(mappedLogs)
                .totalCo2Emission(Math.round(totalCo2 * 100.0) / 100.0)
                .categoryBreakdown(breakdown)
                .build();
    }
}
