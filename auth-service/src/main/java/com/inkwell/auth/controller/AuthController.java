package com.inkwell.auth.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.inkwell.auth.dto.*;
import com.inkwell.auth.entity.User;
import com.inkwell.auth.enums.Role;
import com.inkwell.auth.exception.CustomException;
import com.inkwell.auth.repository.UserRepository;
import com.inkwell.auth.service.AuthService;
import com.inkwell.auth.service.OtpService;
import org.springframework.http.HttpStatus;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Endpoints for user registration, login, and session management")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final com.inkwell.auth.service.OtpService otpService;

    @Operation(summary = "Register a new user", description = "Creates a new user account and returns the authentication response with tokens.")
    @ApiResponse(responseCode = "200", description = "User successfully registered")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @Operation(summary = "Login user", description = "Authenticates user credentials and returns access and refresh tokens.")
    @ApiResponse(responseCode = "200", description = "User successfully authenticated")
    @ApiResponse(responseCode = "401", description = "Invalid credentials")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(summary = "Logout user", description = "Invalidates the user session and blacklists the current access token.")
    @ApiResponse(responseCode = "200", description = "User successfully logged out")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String token) {
        authService.logout(validateAndExtractToken(token));
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Refresh tokens", description = "Generates a new access token using a valid refresh token.")
    @ApiResponse(responseCode = "200", description = "Tokens successfully refreshed")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(authService.refreshToken(validateAndExtractToken(token)));
    }

    private String validateAndExtractToken(String token) {
        if (token == null || !token.startsWith("Bearer ")) {
            throw new CustomException("Invalid Authorization header", HttpStatus.UNAUTHORIZED);
        }
        return token.substring(7);
    }

    @Operation(summary = "Get current profile", description = "Returns the profile details of the currently authenticated user.")
    @ApiResponse(responseCode = "200", description = "Profile details retrieved successfully")
    @GetMapping("/me")
    public ResponseEntity<AuthResponse> getProfile(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            throw new CustomException("User not authenticated", HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.ok(authService.getUserByEmail(userDetails.getUsername()));
    }

    @Operation(summary = "Get current authenticated user details", description = "Returns the full UserDto structure for the currently logged-in user based on JWT.")
    @ApiResponse(responseCode = "200", description = "User details retrieved successfully")
    @ApiResponse(responseCode = "401", description = "Invalid or expired token")
    @ApiResponse(responseCode = "404", description = "User not found")
    @GetMapping("/users/me")
    public ResponseEntity<AuthResponse> getCurrentUser(@AuthenticationPrincipal UserDetails userDetails) {
        log.info("GET /api/v1/auth/users/me - Request received. AuthenticationPrincipal: {}", userDetails);
        
        if (userDetails == null) {
            log.error("GET /api/v1/auth/users/me - AuthenticationPrincipal (userDetails) is null!");
            throw new CustomException("Authentication failed: User details not found in context. Please ensure you are sending a valid JWT token.", HttpStatus.UNAUTHORIZED);
        }

        String email = userDetails.getUsername();
        log.info("GET /api/v1/auth/users/me - Extracted email from principal: {}", email);

        try {
            AuthResponse response = authService.getUserByEmail(email);
            log.info("GET /api/v1/auth/users/me - Successfully retrieved AuthResponse for: {}", email);
            return ResponseEntity.ok(response);
        } catch (CustomException ce) {
            log.error("GET /api/v1/auth/users/me - CustomException: {} - {}", ce.getStatus(), ce.getMessage());
            throw ce;
        } catch (Exception e) {
            log.error("GET /api/v1/auth/users/me - Unexpected error: {}", e.getMessage(), e);
            throw new CustomException("An internal error occurred while fetching user details: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Operation(summary = "Update profile", description = "Updates the profile information for the authenticated user.")
    @ApiResponse(responseCode = "200", description = "Profile updated successfully")
    @PutMapping("/update-profile")
    public ResponseEntity<AuthResponse> updateProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody UpdateProfileRequest request) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(authService.updateProfile(user.getUserId(), request));
    }

    @Operation(summary = "Change password", description = "Allows the authenticated user to change their account password.")
    @ApiResponse(responseCode = "200", description = "Password changed successfully")
    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, String> passwords) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        authService.changePassword(user.getUserId(), passwords.get("oldPassword"), passwords.get("newPassword"));
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Search users", description = "Finds users based on a keyword (username or email).")
    @ApiResponse(responseCode = "200", description = "Search results returned")
    @GetMapping("/search")
    public ResponseEntity<List<AuthResponse>> search(@RequestParam String keyword) {
        return ResponseEntity.ok(authService.searchUsers(keyword));
    }

    @GetMapping("/public/users/{userId}")
    public ResponseEntity<PublicUserProfileResponse> getPublicProfile(@PathVariable Long userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(PublicUserProfileResponse.builder()
                .userId(String.valueOf(user.getUserId()))
                .username(user.getUsername())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .role(user.getRole() != null ? user.getRole().name() : null)
                .build());
    }

    @Operation(summary = "Get all authors", description = "Returns a list of all users with the AUTHOR role. Publicly accessible.")
    @ApiResponse(responseCode = "200", description = "List of authors returned")
    @GetMapping("/public/authors")
    public ResponseEntity<List<AuthResponse>> getPublicAuthors() {
        return ResponseEntity.ok(authService.getPublicAuthors());
    }

    @GetMapping("/admin/users")
    public ResponseEntity<List<AuthResponse>> getAllUsers(@RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        return ResponseEntity.ok(authService.getAllUsers());
    }

    @PutMapping("/admin/users/{userId}/role")
    public ResponseEntity<AuthResponse> updateUserRole(
            @PathVariable Long userId,
            @RequestParam String role,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        return ResponseEntity.ok(authService.updateUserRole(userId, role));
    }

    @PutMapping("/admin/users/{userId}/status")
    public ResponseEntity<AuthResponse> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam boolean active,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        return ResponseEntity.ok(authService.updateUserStatus(userId, active));
    }

    @DeleteMapping("/admin/users/{userId}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable Long userId,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        authService.deleteUser(userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Deactivate account", description = "Deactivates the account of the currently authenticated user.")
    @ApiResponse(responseCode = "200", description = "Account successfully deactivated")
    @DeleteMapping("/deactivate")
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        authService.deactivateAccount(user.getUserId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Forgot password", description = "Sends a password reset link to the user's email.")
    @ApiResponse(responseCode = "200", description = "Reset email sent")
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Reset password", description = "Resets the user's password using a valid reset token.")
    @ApiResponse(responseCode = "200", description = "Password successfully reset")
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Select user role", description = "Allows a newly registered OAuth2 user to select their role (READER or AUTHOR).")
    @ApiResponse(responseCode = "200", description = "Role successfully assigned")
    @PostMapping("/select-role")
    public ResponseEntity<AuthResponse> selectRole(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody RoleSelectionRequest request) {
        if (userDetails == null) {
            throw new CustomException("User not authenticated", HttpStatus.UNAUTHORIZED);
        }
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        
        return ResponseEntity.ok(authService.selectRole(user.getUserId(), request));
    }

    /**
     * Returns a list of user IDs whose role matches the given parameter.
     * Used internally by notification-service for role-based broadcast notifications.
     * Requires Admin JWT (Bearer token) + X-User-Role: ADMIN header from the gateway.
     */
    @Operation(summary = "Get user IDs by role",
               description = "Returns a list of user IDs that have a specific role. Admin only. Used internally for broadcast notifications.")
    @ApiResponse(responseCode = "200", description = "User IDs returned")
    @ApiResponse(responseCode = "403", description = "Admin access required")
    @GetMapping("/users/by-role")
    public ResponseEntity<List<Long>> getUserIdsByRole(
            @RequestParam String role,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        Role targetRole;
        try {
            targetRole = Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException("Invalid role: " + role, HttpStatus.BAD_REQUEST);
        }
        List<Long> ids = userRepository.findAllByRole(targetRole)
                .stream()
                .map(User::getUserId)
                .toList();
        return ResponseEntity.ok(ids);
    }

    /**
     * Internal endpoint to get user email by ID.
     */
    @GetMapping("/internal/users/{userId}/email")
    public ResponseEntity<String> getUserEmail(@PathVariable Long userId) {
        return userRepository.findByUserId(userId)
                .map(user -> ResponseEntity.ok(user.getEmail()))
                .orElse(ResponseEntity.notFound().build());
    }

    private void ensureAdmin(String roleHeader) {
        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            throw new CustomException("Admin access is required", HttpStatus.FORBIDDEN);
        }
    }

    @Operation(summary = "Send OTP", description = "Sends a 6-digit OTP to the given phone number using Twilio Verify.")
    @ApiResponse(responseCode = "200", description = "OTP sent successfully")
    @PostMapping("/send-otp")
    public ResponseEntity<Map<String, String>> sendOtp(@Valid @RequestBody OtpRequest request) {
        otpService.sendOtp(request.getPhoneNumber());
        return ResponseEntity.ok(Map.of("message", "OTP sent successfully"));
    }

    @Operation(summary = "Verify OTP", description = "Verifies the 6-digit OTP for the given phone number.")
    @ApiResponse(responseCode = "200", description = "OTP verified successfully")
    @PostMapping("/verify-otp")
    public ResponseEntity<Map<String, String>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        boolean isValid = otpService.verifyOtp(request.getPhoneNumber(), request.getCode());
        if (isValid) {
            return ResponseEntity.ok(Map.of("message", "OTP verified successfully"));
        } else {
            throw new CustomException("Invalid or expired OTP", HttpStatus.BAD_REQUEST);
        }
    }
}
