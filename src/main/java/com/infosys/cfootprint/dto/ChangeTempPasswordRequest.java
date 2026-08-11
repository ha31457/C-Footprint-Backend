package com.infosys.cfootprint.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangeTempPasswordRequest {
    @NotBlank(message = "New password is required")
    private String newPassword;
}
