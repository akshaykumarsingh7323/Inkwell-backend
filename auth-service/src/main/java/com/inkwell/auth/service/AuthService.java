package com.inkwell.auth.service;

import com.inkwell.auth.dto.*;

import java.util.List;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(AuthRequest request);
    void logout(String token);
    boolean validateToken(String token);
    AuthResponse refreshToken(String token);
    AuthResponse getUserByEmail(String email);
    AuthResponse getUserById(Long userId);
    AuthResponse updateProfile(Long userId, UpdateProfileRequest request);
    void changePassword(Long userId, String oldPassword, String newPassword);
    List<AuthResponse> searchUsers(String keyword);
    void deactivateAccount(Long userId);
    List<AuthResponse> getAllUsers();
    AuthResponse updateUserRole(Long targetUserId, String role);
    AuthResponse updateUserStatus(Long targetUserId, boolean active);
    void forgotPassword(ForgotPasswordRequest request);
    void resetPassword(ResetPasswordRequest request);
    AuthResponse selectRole(Long userId, RoleSelectionRequest request);
    List<AuthResponse> getPublicAuthors();
    void deleteUser(Long userId);
}
