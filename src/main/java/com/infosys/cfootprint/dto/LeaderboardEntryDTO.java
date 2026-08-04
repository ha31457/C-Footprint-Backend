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
public class LeaderboardEntryDTO {
    private Integer rank;
    private UUID userId;
    private String username;
    private String avatar;
    private String avatarUrl;
    private Double totalCo2Emission;
    private Boolean isCurrentUser;
}
