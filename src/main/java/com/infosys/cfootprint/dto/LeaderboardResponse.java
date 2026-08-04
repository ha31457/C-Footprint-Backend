package com.infosys.cfootprint.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaderboardResponse {
    private List<LeaderboardEntryDTO> entries;
    private Integer currentUserRank;
    private Double currentUserPercentile;
    private Double averageEmission;
    private Long totalParticipants;
    private List<String> insights;
}
