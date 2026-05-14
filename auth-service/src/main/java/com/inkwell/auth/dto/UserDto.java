package com.inkwell.auth.dto;

import com.inkwell.auth.enums.Provider;
import com.inkwell.auth.enums.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private Role role;
    private String bio;
    private String avatarUrl;
    private Provider provider;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
