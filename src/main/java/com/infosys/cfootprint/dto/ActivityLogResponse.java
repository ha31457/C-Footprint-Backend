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
public class ActivityLogResponse {
    private UUID id;
    private String category;
    private String activityType;
    private Double quantity;
    private String unit;
    private Double co2Emission;
    private LocalDate logDate;
    private String imageProofId;
}
