package com.infosys.cfootprint.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoalResponse {
    private UUID id;
    private Double targetReductionPercentage;
    private String periodType;
    private LocalDate startDate;
    private LocalDate endDate;
    private Double baselineEmission;
    private Double currentEmission;
    private Double targetEmission;
    private Double progressPercentage;
    private Boolean isOnTrack;
    private String status;
    private String alertMessage;
}
