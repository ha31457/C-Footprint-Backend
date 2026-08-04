package com.infosys.cfootprint.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReplyComplaintRequest {
    @NotBlank(message = "Reply text is required")
    private String replyText;
}
