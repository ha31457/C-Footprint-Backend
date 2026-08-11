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
public class UserResponse {
    private UUID id;
    private String username;
    private String email;
    private String role;
    private String mobileNumber;
    private Integer age;
    private String gender;
    private String avatar;
    private String avatarUrl;
    private boolean isEnabled;
    private boolean isDisabled;
    private String organizationName;

    @JsonProperty("isTempPassword")
    private boolean isTempPassword;
}
