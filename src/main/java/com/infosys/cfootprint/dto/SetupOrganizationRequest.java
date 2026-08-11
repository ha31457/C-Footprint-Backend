package com.infosys.cfootprint.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SetupOrganizationRequest {
    @NotBlank(message = "Organization name is required")
    private String organizationName;

    @NotBlank(message = "Industry is required")
    private String industry;

    @NotBlank(message = "Address is required")
    private String address;

    private String description;
}
