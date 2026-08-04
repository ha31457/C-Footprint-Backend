package com.infosys.cfootprint.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CreateBadgeDefinitionRequest {

    @NotBlank(message = "Badge type is required")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Badge type must contain uppercase letters, numbers, and underscores only")
    private String badgeType;

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title cannot exceed 100 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(max = 255, message = "Description cannot exceed 255 characters")
    private String description;

    @Size(max = 100, message = "Icon name cannot exceed 100 characters")
    private String iconName;

    @Size(max = 255, message = "Icon URL cannot exceed 255 characters")
    private String iconUrl;

    @NotBlank(message = "Rule type is required")
    private String ruleType;

    @NotNull(message = "Rule value is required")
    private Double ruleValue;
}
