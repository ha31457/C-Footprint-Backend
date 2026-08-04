package com.infosys.cfootprint.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

@Data
public class UpdateGoalRequest {

    @DecimalMin(value = "1.0", message = "Target reduction percentage must be at least 1%")
    @DecimalMax(value = "90.0", message = "Target reduction percentage cannot exceed 90%")
    private Double targetReductionPercentage;

    private String periodType;

    private Integer durationDays;
}
