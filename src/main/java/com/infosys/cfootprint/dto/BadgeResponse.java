package com.infosys.cfootprint.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BadgeResponse {
    private String badgeType;
    private String title;
    private String description;
    private String iconName;
    private String iconUrl;
    private LocalDateTime earnedDate;
    private boolean isLocked;
}
