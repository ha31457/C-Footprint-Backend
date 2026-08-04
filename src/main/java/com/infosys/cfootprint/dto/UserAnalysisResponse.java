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
public class UserAnalysisResponse {
    private Long totalLogs;
    private Double totalAllTimeEmission;
    private Map<String, Long> categoryLogs;
    private Map<String, Double> categoryEmission;
    private String mostLoggedCategory;
    private String highestEmissionCategory;
    private List<String> tips;
    private List<TrendDTO> trend;
    private List<String> recommendations;
}
