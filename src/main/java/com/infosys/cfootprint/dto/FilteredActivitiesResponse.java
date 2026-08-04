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
public class FilteredActivitiesResponse {
    private List<ActivityLogResponse> activities;
    private Double totalCo2Emission;
    private Map<String, Double> categoryBreakdown;
}
