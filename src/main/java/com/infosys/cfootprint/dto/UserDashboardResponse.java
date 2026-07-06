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
public class UserDashboardResponse {
    private Double todayTotalEmission;
    private List<CategoryBreakdownDTO> categoryBreakdown;
    private List<WeeklyTrendDTO> weeklyTrend;
    private List<TrendDTO> trend;
}
