package com.infosys.cfootprint.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateGoalRequest {

    @NotNull(message = "Target reduction percentage is required")
    @DecimalMin(value = "1.0", message = "Target reduction percentage must be at least 1%")
    @DecimalMax(value = "90.0", message = "Target reduction percentage cannot exceed 90%")
    private Double targetReductionPercentage;

    @NotBlank(message = "Period type is required")
    private String periodType;

    private Integer durationDays;
}
