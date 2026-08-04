package com.infosys.cfootprint.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateEmissionFactorRequest {

    @NotBlank(message = "Category is required")
    private String category;

    @NotBlank(message = "Activity type is required")
    private String activityType;

    @NotNull(message = "Factor is required")
    @DecimalMin(value = "0.0", message = "Factor must be positive")
    private Double factor;

    @NotBlank(message = "Unit is required")
    private String unit;
}
