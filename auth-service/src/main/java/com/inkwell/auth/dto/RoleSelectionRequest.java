package com.inkwell.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleSelectionRequest {
    @NotBlank(message = "Role is required")
    private String role;

    private String username;
    
    private String phoneNumber;

    private boolean acceptedTerms;
}
