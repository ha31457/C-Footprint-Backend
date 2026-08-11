package com.infosys.cfootprint.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JwtResponse {
    @Builder.Default
    private String tokenType = "Bearer";
    private String accessToken;
    private String refreshToken;
    private UUID id;
    private String username;
    private String email;
    private String role;
    private String avatar;
    private String avatarUrl;
    private String organizationName;

    @JsonProperty("isTempPassword")
    private boolean isTempPassword;
}
