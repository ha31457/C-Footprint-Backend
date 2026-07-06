package com.infosys.cfootprint.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminUserAnalyticsResponse {
    private Long totalUsers;
    private Long enabledUsers;
    private Long disabledUsers;
}
