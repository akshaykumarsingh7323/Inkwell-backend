package com.inkwell.auth.controller;

import com.inkwell.auth.dto.AuthRequest;
import com.inkwell.auth.dto.AuthResponse;
import com.inkwell.auth.dto.ForgotPasswordRequest;
import com.inkwell.auth.dto.OtpRequest;
import com.inkwell.auth.dto.PublicUserProfileResponse;
import com.inkwell.auth.dto.RegisterRequest;
import com.inkwell.auth.dto.ResetPasswordRequest;
import com.inkwell.auth.dto.RoleSelectionRequest;
import com.inkwell.auth.dto.UpdateProfileRequest;
import com.inkwell.auth.dto.VerifyOtpRequest;
import com.inkwell.auth.enums.Role;
import com.inkwell.auth.exception.CustomException;
import com.inkwell.auth.repository.UserRepository;
import com.inkwell.auth.service.AuthService;
import com.inkwell.auth.service.OtpService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OtpService otpService;

    @InjectMocks
    private AuthController authController;

    private UserDetails principal(String email) {
        return org.springframework.security.core.userdetails.User.withUsername(email).password("pwd").roles("USER").build();
    }

    @Test
    void register_ShouldReturnBody() {
        RegisterRequest request = RegisterRequest.builder()
                .username("john")
                .email("john@example.com")
                .password("password123")
                .fullName("John Doe")
                .build();
        AuthResponse response = new AuthResponse();
        when(authService.register(request)).thenReturn(response);

        assertEquals(response, authController.register(request).getBody());
    }

    @Test
    void login_ShouldReturnBody() {
        AuthRequest request = new AuthRequest("john@example.com", "password123");
        AuthResponse response = new AuthResponse();
        when(authService.login(request)).thenReturn(response);

        assertEquals(response, authController.login(request).getBody());
    }

    @Test
    void logout_ShouldPassBearerToken() {
        authController.logout("Bearer token-123");

        verify(authService).logout("token-123");
    }

    @Test
    void logout_WhenHeaderInvalid_ShouldThrowUnauthorized() {
        CustomException exception = assertThrows(CustomException.class, () -> authController.logout("token-123"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void refresh_ShouldReturnRefreshedAuthResponse() {
        AuthResponse response = new AuthResponse();
        when(authService.refreshToken("refresh-token")).thenReturn(response);

        assertEquals(response, authController.refresh("Bearer refresh-token").getBody());
    }

    @Test
    void getProfile_ShouldReturnUserByEmail() {
        AuthResponse response = new AuthResponse();
        when(authService.getUserByEmail("john@example.com")).thenReturn(response);

        assertEquals(response, authController.getProfile(principal("john@example.com")).getBody());
    }

    @Test
    void getProfile_WhenUnauthenticated_ShouldThrow() {
        CustomException exception = assertThrows(CustomException.class, () -> authController.getProfile(null));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void updateProfile_ShouldUseResolvedUserId() {
        com.inkwell.auth.entity.User entity = com.inkwell.auth.entity.User.builder()
                .userId(7L)
                .email("john@example.com")
                .build();
        UpdateProfileRequest request = new UpdateProfileRequest();
        AuthResponse response = new AuthResponse();
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(entity));
        when(authService.updateProfile(7L, request)).thenReturn(response);

        assertEquals(response, authController.updateProfile(principal("john@example.com"), request).getBody());
    }

    @Test
    void changePassword_ShouldDelegate() {
        com.inkwell.auth.entity.User entity = com.inkwell.auth.entity.User.builder()
                .userId(7L)
                .email("john@example.com")
                .build();
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(entity));

        authController.changePassword(principal("john@example.com"), Map.of("oldPassword", "old", "newPassword", "new"));

        verify(authService).changePassword(7L, "old", "new");
    }

    @Test
    void search_ShouldReturnResults() {
        List<AuthResponse> responses = List.of(new AuthResponse());
        when(authService.searchUsers("john")).thenReturn(responses);

        assertEquals(responses, authController.search("john").getBody());
    }

    @Test
    void getPublicProfile_ShouldMapEntity() {
        com.inkwell.auth.entity.User entity = com.inkwell.auth.entity.User.builder()
                .userId(4L)
                .username("john")
                .fullName("John Doe")
                .avatarUrl("avatar")
                .bio("bio")
                .role(Role.AUTHOR)
                .build();
        when(userRepository.findByUserId(4L)).thenReturn(Optional.of(entity));

        PublicUserProfileResponse body = authController.getPublicProfile(4L).getBody();

        assertEquals("4", body.getUserId());
        assertEquals("john", body.getUsername());
        assertEquals("AUTHOR", body.getRole());
    }

    @Test
    void getPublicAuthors_ShouldReturnResults() {
        List<AuthResponse> responses = List.of(new AuthResponse());
        when(authService.getPublicAuthors()).thenReturn(responses);

        assertEquals(responses, authController.getPublicAuthors().getBody());
    }

    @Test
    void getAllUsers_AsAdmin_ShouldReturnResults() {
        List<AuthResponse> responses = List.of(new AuthResponse());
        when(authService.getAllUsers()).thenReturn(responses);

        assertEquals(responses, authController.getAllUsers("ADMIN").getBody());
    }

    @Test
    void getAllUsers_WhenNotAdmin_ShouldThrow() {
        CustomException exception = assertThrows(CustomException.class, () -> authController.getAllUsers("AUTHOR"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void updateUserRole_AsAdmin_ShouldDelegate() {
        AuthResponse response = new AuthResponse();
        when(authService.updateUserRole(5L, "AUTHOR")).thenReturn(response);

        assertEquals(response, authController.updateUserRole(5L, "AUTHOR", "ADMIN").getBody());
    }

    @Test
    void updateUserStatus_AsAdmin_ShouldDelegate() {
        AuthResponse response = new AuthResponse();
        when(authService.updateUserStatus(5L, true)).thenReturn(response);

        assertEquals(response, authController.updateUserStatus(5L, true, "ADMIN").getBody());
    }

    @Test
    void deleteUser_AsAdmin_ShouldDelegate() {
        authController.deleteUser(5L, "ADMIN");

        verify(authService).deleteUser(5L);
    }

    @Test
    void deactivate_ShouldDelegate() {
        com.inkwell.auth.entity.User entity = com.inkwell.auth.entity.User.builder()
                .userId(9L)
                .email("john@example.com")
                .build();
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(entity));

        authController.deactivate(principal("john@example.com"));

        verify(authService).deactivateAccount(9L);
    }

    @Test
    void forgotPassword_ShouldDelegate() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("john@example.com");

        authController.forgotPassword(request);

        verify(authService).forgotPassword(request);
    }

    @Test
    void resetPassword_ShouldDelegate() {
        ResetPasswordRequest request = new ResetPasswordRequest("token", "newPassword123");

        authController.resetPassword(request);

        verify(authService).resetPassword(request);
    }

    @Test
    void selectRole_ShouldDelegateForAuthenticatedUser() {
        com.inkwell.auth.entity.User entity = com.inkwell.auth.entity.User.builder()
                .userId(11L)
                .email("john@example.com")
                .build();
        RoleSelectionRequest request = new RoleSelectionRequest();
        request.setRole(Role.AUTHOR.name());
        AuthResponse response = new AuthResponse();
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(entity));
        when(authService.selectRole(11L, request)).thenReturn(response);

        assertEquals(response, authController.selectRole(principal("john@example.com"), request).getBody());
    }

    @Test
    void selectRole_WhenUserMissing_ShouldThrow() {
        RoleSelectionRequest request = new RoleSelectionRequest();
        request.setRole(Role.READER.name());
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class, () -> authController.selectRole(principal("john@example.com"), request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void getUserIdsByRole_ShouldReturnIds() {
        com.inkwell.auth.entity.User first = com.inkwell.auth.entity.User.builder().userId(1L).role(Role.AUTHOR).build();
        com.inkwell.auth.entity.User second = com.inkwell.auth.entity.User.builder().userId(2L).role(Role.AUTHOR).build();
        when(userRepository.findAllByRole(Role.AUTHOR)).thenReturn(List.of(first, second));

        assertEquals(List.of(1L, 2L), authController.getUserIdsByRole("author", "ADMIN").getBody());
    }

    @Test
    void getUserEmail_ShouldReturnEmailIfExists() {
        com.inkwell.auth.entity.User entity = com.inkwell.auth.entity.User.builder()
                .userId(15L)
                .email("test@example.com")
                .build();
        when(userRepository.findByUserId(15L)).thenReturn(Optional.of(entity));

        assertEquals("test@example.com", authController.getUserEmail(15L).getBody());
    }

    @Test
    void getUserEmail_WhenUserNotFound_ShouldReturn404() {
        when(userRepository.findByUserId(99L)).thenReturn(Optional.empty());

        assertEquals(HttpStatus.NOT_FOUND, authController.getUserEmail(99L).getStatusCode());
    }

    @Test
    void getUserIdsByRole_WhenRoleInvalid_ShouldThrow() {
        CustomException exception = assertThrows(CustomException.class, () -> authController.getUserIdsByRole("bad-role", "ADMIN"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void sendOtp_ShouldDelegate() {
        OtpRequest request = new OtpRequest();
        request.setPhoneNumber("+12345678901");

        assertEquals("OTP sent successfully", authController.sendOtp(request).getBody().get("message"));
        verify(otpService).sendOtp("+12345678901");
    }

    @Test
    void verifyOtp_WhenValid_ShouldReturnSuccess() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhoneNumber("+12345678901");
        request.setCode("123456");
        when(otpService.verifyOtp("+12345678901", "123456")).thenReturn(true);

        assertEquals("OTP verified successfully", authController.verifyOtp(request).getBody().get("message"));
    }

    @Test
    void verifyOtp_WhenInvalid_ShouldThrow() {
        VerifyOtpRequest request = new VerifyOtpRequest();
        request.setPhoneNumber("+12345678901");
        request.setCode("123456");
        when(otpService.verifyOtp(anyString(), anyString())).thenReturn(false);

        CustomException exception = assertThrows(CustomException.class, () -> authController.verifyOtp(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void getCurrentUser_WhenAuthenticated_ShouldReturnAuthResponse() {
        AuthResponse response = new AuthResponse();
        when(authService.getUserByEmail("john@example.com")).thenReturn(response);

        assertEquals(response, authController.getCurrentUser(principal("john@example.com")).getBody());
    }

    @Test
    void getCurrentUser_WhenUnauthenticated_ShouldThrow() {
        CustomException exception = assertThrows(CustomException.class, () -> authController.getCurrentUser(null));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void getCurrentUser_WhenUnexpectedError_ShouldThrow500() {
        when(authService.getUserByEmail("john@example.com")).thenThrow(new RuntimeException("Boom"));

        CustomException exception = assertThrows(CustomException.class, () -> authController.getCurrentUser(principal("john@example.com")));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
    }

    @Test
    void getPublicProfile_WhenNotFound_ShouldThrow() {
        when(userRepository.findByUserId(99L)).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class, () -> authController.getPublicProfile(99L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void updateProfile_WhenUserNotFound_ShouldThrow() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> authController.updateProfile(principal("john@example.com"), new UpdateProfileRequest()));
    }

    @Test
    void changePassword_WhenUserNotFound_ShouldThrow() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> authController.changePassword(principal("john@example.com"), Map.of()));
    }

    @Test
    void deactivate_WhenUserNotFound_ShouldThrow() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> authController.deactivate(principal("john@example.com")));
    }

    @Test
    void selectRole_WhenUnauthenticated_ShouldThrow() {
        CustomException exception = assertThrows(CustomException.class, () -> authController.selectRole(null, new RoleSelectionRequest()));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }
}
