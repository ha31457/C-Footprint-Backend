package com.infosys.cfootprint.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminLeaderboardResponse {
    private Integer rank;
    private UUID userId;
    private String username;
    private String email;
    private boolean isEnabled;
    private boolean isDisabled;
    private String avatar;
    private String avatarUrl;
    private Double totalCo2Emission;
    private Long totalLogsCount;
}
