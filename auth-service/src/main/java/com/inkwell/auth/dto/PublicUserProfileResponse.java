package com.inkwell.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicUserProfileResponse {
    private String userId;
    private String username;
    private String fullName;
    private String avatarUrl;
    private String bio;
    private String role;
}
