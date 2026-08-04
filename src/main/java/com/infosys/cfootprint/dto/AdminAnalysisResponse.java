package com.infosys.cfootprint.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAnalysisResponse {
    private Long totalUsers;
    private Long totalLogs;
    private Map<String, Long> categoryLogs;
    private Map<String, Double> categoryEmission;
    private String mostLoggedCategory;
    private String highestEmissionCategory;
    private Double averageEmissionPerUser;
    private List<String> tips;
}
