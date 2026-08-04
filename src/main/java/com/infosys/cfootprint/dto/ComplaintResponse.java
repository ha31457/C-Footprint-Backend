package com.infosys.cfootprint.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplaintResponse {
    private UUID id;
    private String email;
    private String category;
    private String complaintText;
    private String replyText;
    private boolean isResolved;
    private LocalDateTime createdAt;
    private LocalDateTime repliedAt;
}
