package com.infosys.cfootprint.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminActivityAnalyticsResponse {
    private Long totalLogs;
    private Long logsLoggedToday;
    private Double totalCo2EmissionKgs;
    private List<CategoryBreakdownDTO> categoryBreakdown;
    private List<TrendDTO> trend;
}
