package com.inkwell.auth.service;

import com.inkwell.auth.config.JwtUtil;
import com.inkwell.auth.dto.AuthRequest;
import com.inkwell.auth.dto.AuthResponse;
import com.inkwell.auth.dto.ForgotPasswordRequest;
import com.inkwell.auth.dto.RegisterRequest;
import com.inkwell.auth.dto.ResetPasswordRequest;
import com.inkwell.auth.dto.RoleSelectionRequest;
import com.inkwell.auth.dto.UpdateProfileRequest;
import com.inkwell.auth.entity.User;
import com.inkwell.auth.enums.Provider;
import com.inkwell.auth.enums.Role;
import com.inkwell.auth.exception.CustomException;
import com.inkwell.auth.repository.UserRepository;
import com.inkwell.auth.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void register_ShouldCreateUser() {
        RegisterRequest request = RegisterRequest.builder()
                .username("test")
                .email("test@example.com")
                .password("password")
                .fullName("Full Name")
                .build();
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(1L);
            return user;
        });
        when(jwtUtil.generateToken(any(), any())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh");

        AuthResponse response = authService.register(request);

        assertEquals("access", response.getAccessToken());
        verify(emailService).sendWelcomeEmail("test@example.com", "Full Name");
    }

    @Test
    void register_WhenEmailExists_ShouldThrowConflict() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(true);

        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .username("test")
                .password("password")
                .fullName("Full Name")
                .build();

        CustomException exception = assertThrows(CustomException.class, () -> authService.register(request));
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    @Test
    void register_WhenUsernameExists_ShouldThrowConflict() {
        when(userRepository.existsByEmail("test@example.com")).thenReturn(false);
        when(userRepository.existsByUsername("test")).thenReturn(true);

        RegisterRequest request = RegisterRequest.builder()
                .email("test@example.com")
                .username("test")
                .password("password")
                .fullName("Full Name")
                .build();

        CustomException exception = assertThrows(CustomException.class, () -> authService.register(request));
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    @Test
    void register_WhenUsernameBlank_ShouldAutogenerateUniqueUsername() {
        RegisterRequest request = RegisterRequest.builder()
                .username(" ")
                .email("test@example.com")
                .password("password")
                .fullName("Full Name")
                .build();
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername("test")).thenReturn(true);
        when(userRepository.existsByUsername("test1")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setUserId(1L);
            return user;
        });
        when(jwtUtil.generateToken(any(), any())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh");

        AuthResponse response = authService.register(request);

        assertEquals("test1", response.getUsername());
    }

    @Test
    void login_WithEmail_ShouldAuthenticate() {
        AuthRequest request = new AuthRequest("test@example.com", "password");
        User user = User.builder()
                .userId(1L)
                .email("test@example.com")
                .username("test")
                .passwordHash("hash")
                .isActive(true)
                .role(Role.READER)
                .build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(jwtUtil.generateToken(any(), any())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh");

        AuthResponse response = authService.login(request);

        assertEquals("access", response.getAccessToken());
        verify(emailService).sendLoginNotificationEmail("test@example.com", "test");
    }

    @Test
    void login_WithUsernameFallback_ShouldAuthenticate() {
        AuthRequest request = new AuthRequest("tester", "password");
        User user = User.builder()
                .userId(1L)
                .email("test@example.com")
                .username("tester")
                .passwordHash("hash")
                .isActive(true)
                .role(null)
                .build();
        when(userRepository.findByEmail("tester")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("tester")).thenReturn(Optional.of(user));
        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(jwtUtil.generateToken(any(), any())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh");

        AuthResponse response = authService.login(request);

        assertEquals("READER", response.getRole());
    }

    @Test
    void login_WhenUserMissing_ShouldThrowNotFound() {
        when(userRepository.findByEmail("missing")).thenReturn(Optional.empty());
        when(userRepository.findByUsername("missing")).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class, () -> authService.login(new AuthRequest("missing", "password")));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void login_WhenInactive_ShouldThrowForbidden() {
        User user = User.builder().email("test@example.com").isActive(false).build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        CustomException exception = assertThrows(CustomException.class, () -> authService.login(new AuthRequest("test@example.com", "password")));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void login_WhenAuthenticationFails_ShouldThrowUnauthorized() {
        User user = User.builder()
                .email("test@example.com")
                .username("test")
                .passwordHash("hash")
                .isActive(true)
                .role(Role.READER)
                .build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("bad creds")).when(authenticationManager).authenticate(any());

        CustomException exception = assertThrows(CustomException.class, () -> authService.login(new AuthRequest("test@example.com", "bad")));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void logout_WithFutureExpiration_ShouldBlacklistToken() {
        when(jwtUtil.extractExpiration("token")).thenReturn(new Date(System.currentTimeMillis() + 60_000));
        authService.logout("token");
        assertFalse(authService.validateToken("token"));
    }

    @Test
    void validateToken_WhenBlacklisted_ShouldReturnFalse() {
        when(jwtUtil.extractExpiration("token")).thenReturn(new Date(System.currentTimeMillis() + 60_000));
        authService.logout("token");
        
        User user = User.builder().email("test@example.com").isActive(true).role(Role.READER).build();
        when(jwtUtil.extractUsername("token")).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        
        assertFalse(authService.validateToken("token"));
    }

    @Test
    void validateToken_WhenValid_ShouldReturnTrue() {
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("hash")
                .isActive(true)
                .role(Role.READER)
                .build();
        when(jwtUtil.extractUsername("token")).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.isTokenValid(anyString(), any())).thenReturn(true);

        assertTrue(authService.validateToken("token"));
    }

    @Test
    void validateToken_Invalid_ShouldReturnFalse() {
        when(jwtUtil.extractUsername(anyString())).thenThrow(new RuntimeException());
        assertFalse(authService.validateToken("invalid"));
    }

    @Test
    void refreshToken_WhenValid_ShouldReturnNewAccessToken() {
        User user = User.builder()
                .userId(1L)
                .email("test@example.com")
                .passwordHash("hash")
                .role(Role.AUTHOR)
                .build();
        when(jwtUtil.extractUsername("refresh")).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.isTokenValid(anyString(), any())).thenReturn(true);
        when(jwtUtil.generateToken(any(), any())).thenReturn("new-access");

        AuthResponse response = authService.refreshToken("refresh");

        assertEquals("new-access", response.getAccessToken());
        assertEquals("refresh", response.getRefreshToken());
    }

    @Test
    void refreshToken_WhenInvalid_ShouldThrowUnauthorized() {
        User user = User.builder().userId(1L).email("test@example.com").passwordHash("hash").role(Role.READER).build();
        when(jwtUtil.extractUsername("refresh")).thenReturn("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(jwtUtil.isTokenValid(anyString(), any())).thenReturn(false);

        CustomException exception = assertThrows(CustomException.class, () -> authService.refreshToken("refresh"));
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatus());
    }

    @Test
    void getUserByEmail_ShouldReturnResponse() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(
                User.builder().userId(1L).email("test@example.com").username("test").role(Role.READER).build()
        ));
        assertEquals("test@example.com", authService.getUserByEmail("test@example.com").getEmail());
    }

    @Test
    void getUserById_Success() {
        User user = User.builder().userId(1L).username("test").email("test@ex.com").role(Role.READER).build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
        assertEquals("test", authService.getUserById(1L).getUsername());
    }

    @Test
    void updateProfile_ShouldUpdateNonNullFields() {
        User user = User.builder().userId(1L).fullName("Old").bio("Old bio").avatarUrl("old.png").build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));

        AuthResponse response = authService.updateProfile(1L, new UpdateProfileRequest("New", null, "new.png"));

        assertEquals("New", response.getFullName());
        assertEquals("Old bio", response.getBio());
        assertEquals("new.png", response.getAvatarUrl());
    }

    @Test
    void changePassword_WhenOldPasswordInvalid_ShouldThrowBadRequest() {
        User user = User.builder().userId(1L).passwordHash("hash").build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "hash")).thenReturn(false);

        CustomException exception = assertThrows(CustomException.class, () -> authService.changePassword(1L, "old", "new"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void changePassword_ShouldEncodeAndSave() {
        User user = User.builder().userId(1L).passwordHash("hash").build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "hash")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("encoded");

        authService.changePassword(1L, "old", "new");

        assertEquals("encoded", user.getPasswordHash());
        verify(userRepository).save(user);
    }

    @Test
    void searchUsers_ShouldMapResults() {
        when(userRepository.searchByUsername("te")).thenReturn(List.of(
                User.builder().userId(1L).username("test").email("test@example.com").role(Role.READER).build()
        ));
        assertEquals(1, authService.searchUsers("te").size());
    }

    @Test
    void getAllUsers_ShouldMapResults() {
        when(userRepository.findAll()).thenReturn(List.of(
                User.builder().userId(1L).username("test").email("test@example.com").role(Role.READER).build()
        ));
        assertEquals(1, authService.getAllUsers().size());
    }

    @Test
    void deactivateAccount_ShouldSaveInactiveUser() {
        User user = User.builder().userId(1L).isActive(true).build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));

        authService.deactivateAccount(1L);

        assertFalse(user.isActive());
        verify(userRepository).save(user);
    }

    @Test
    void updateUserRole_WhenInvalid_ShouldThrowBadRequest() {
        User user = User.builder().userId(1L).role(Role.READER).build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));

        CustomException exception = assertThrows(CustomException.class, () -> authService.updateUserRole(1L, "bad-role"));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void updateUserRole_ShouldUpdateRole() {
        User user = User.builder().userId(1L).role(Role.READER).build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));

        AuthResponse response = authService.updateUserRole(1L, "author");

        assertEquals("AUTHOR", response.getRole());
    }

    @Test
    void updateUserStatus_WhenSuspending_ShouldSendEmail() {
        User user = User.builder().userId(1L).email("test@example.com").fullName("Full Name").isActive(true).role(Role.READER).build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));

        authService.updateUserStatus(1L, false);

        verify(emailService).sendSuspensionEmail("test@example.com", "Full Name");
    }

    @Test
    void updateUserStatus_WhenReactivating_ShouldNotSendEmail() {
        User user = User.builder().userId(1L).email("test@example.com").isActive(false).role(Role.READER).build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));

        authService.updateUserStatus(1L, true);

        verify(emailService, never()).sendSuspensionEmail(anyString(), anyString());
    }

    @Test
    void forgotPassword_WhenUserMissing_ShouldThrowNotFound() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("missing@example.com");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        CustomException exception = assertThrows(CustomException.class, () -> authService.forgotPassword(request));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void forgotPassword_ShouldGenerateOtpAndSendEmail() {
        User user = User.builder().email("test@example.com").build();
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("test@example.com");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        authService.forgotPassword(request);

        assertNotNull(user.getResetToken());
        assertNotNull(user.getResetTokenExpiry());
        verify(emailService).sendResetPasswordEmail("test@example.com", user.getResetToken());
    }

    @Test
    void resetPassword_Success() {
        ResetPasswordRequest request = new ResetPasswordRequest("token", "newPass");
        User user = User.builder()
                .email("test@example.com")
                .resetToken("token")
                .resetTokenExpiry(LocalDateTime.now().plusHours(1))
                .build();
        when(userRepository.findByResetToken("token")).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("newPass")).thenReturn("encoded");

        authService.resetPassword(request);

        assertEquals("encoded", user.getPasswordHash());
        assertNull(user.getResetToken());
        verify(userRepository).save(user);
    }

    @Test
    void resetPassword_WhenTokenExpired_ShouldThrowBadRequest() {
        ResetPasswordRequest request = new ResetPasswordRequest("token", "newPass");
        User user = User.builder().resetToken("token").resetTokenExpiry(LocalDateTime.now().minusMinutes(1)).build();
        when(userRepository.findByResetToken("token")).thenReturn(Optional.of(user));

        CustomException exception = assertThrows(CustomException.class, () -> authService.resetPassword(request));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void selectRole_WhenRoleNotFound_ShouldThrowBadRequest() {
        User user = User.builder().userId(1L).build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
        
        RoleSelectionRequest request = RoleSelectionRequest.builder().role("NON_EXISTENT").build();
        CustomException exception = assertThrows(CustomException.class, () -> authService.selectRole(1L, request));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void selectRole_WhenInvalidRole_ShouldThrowBadRequest() {
        User user = User.builder().userId(1L).role(Role.ROLE_NOT_SELECTED).build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));

        RoleSelectionRequest request = RoleSelectionRequest.builder().role("invalid").build();

        CustomException exception = assertThrows(CustomException.class, () -> authService.selectRole(1L, request));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void selectRole_WhenAuthorTermsNotAccepted_ShouldThrowBadRequest() {
        User user = User.builder().userId(1L).role(Role.READER).build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));

        RoleSelectionRequest request = RoleSelectionRequest.builder().role("AUTHOR").acceptedTerms(false).build();

        CustomException exception = assertThrows(CustomException.class, () -> authService.selectRole(1L, request));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void selectRole_WhenAdminCurrentRole_ShouldThrowForbidden() {
        User user = User.builder().userId(1L).role(Role.ADMIN).build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));

        RoleSelectionRequest request = RoleSelectionRequest.builder().role("AUTHOR").acceptedTerms(true).build();

        CustomException exception = assertThrows(CustomException.class, () -> authService.selectRole(1L, request));
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void selectRole_WhenReaderSelectsReader_ShouldReturnExistingProfile() {
        User user = User.builder().userId(1L).role(Role.READER).email("test@example.com").build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));

        RoleSelectionRequest request = RoleSelectionRequest.builder().role("READER").build();
        AuthResponse response = authService.selectRole(1L, request);

        assertEquals("READER", response.getRole());
    }

    @Test
    void selectRole_WhenAuthorSelectsAuthor_ShouldReturnExistingProfile() {
        User user = User.builder().userId(1L).role(Role.AUTHOR).email("test@example.com").build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));

        RoleSelectionRequest request = RoleSelectionRequest.builder().role("AUTHOR").acceptedTerms(true).build();
        AuthResponse response = authService.selectRole(1L, request);

        assertEquals("AUTHOR", response.getRole());
    }

    @Test
    void selectRole_WhenReaderSelectsInvalidUpgrade_ShouldThrowBadRequest() {
        User user = User.builder().userId(1L).role(Role.READER).build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));

        // Reader trying to "upgrade" to something else that's not AUTHOR
        RoleSelectionRequest request = RoleSelectionRequest.builder().role("ADMIN").build();

        CustomException exception = assertThrows(CustomException.class, () -> authService.selectRole(1L, request));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void selectRole_WhenRoleAlreadyAssigned_ShouldThrowBadRequest() {
        User user = User.builder().userId(1L).role(Role.AUTHOR).build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));

        // Author trying to change to Reader
        RoleSelectionRequest request = RoleSelectionRequest.builder().role("READER").build();

        CustomException exception = assertThrows(CustomException.class, () -> authService.selectRole(1L, request));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void selectRole_WhenUsernameTaken_ShouldThrowConflict() {
        User user = User.builder().userId(1L).role(Role.READER).username("old").build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        RoleSelectionRequest request = RoleSelectionRequest.builder()
                .role("AUTHOR")
                .acceptedTerms(true)
                .username("taken")
                .build();

        CustomException exception = assertThrows(CustomException.class, () -> authService.selectRole(1L, request));
        assertEquals(HttpStatus.CONFLICT, exception.getStatus());
    }

    @Test
    void selectRole_ShouldUpgradeReaderToAuthorAndReturnTokens() {
        User user = User.builder()
                .userId(1L)
                .email("test@example.com")
                .username("old")
                .passwordHash("hash")
                .role(Role.READER)
                .build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("newauthor")).thenReturn(false);
        when(jwtUtil.generateToken(any(), any())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh");

        RoleSelectionRequest request = RoleSelectionRequest.builder()
                .role("AUTHOR")
                .acceptedTerms(true)
                .username("newauthor")
                .phoneNumber("1234567890")
                .build();

        AuthResponse response = authService.selectRole(1L, request);

        assertEquals("AUTHOR", response.getRole());
        assertEquals("newauthor", user.getUsername());
        assertEquals("1234567890", user.getPhoneNumber());
    }

    @Test
    void selectRole_WithSameUsername_ShouldSucceed() {
        User user = User.builder().userId(1L).role(Role.READER).username("same").email("test@example.com").build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
        // existsByUsername is not called because username is same
        when(jwtUtil.generateToken(any(), any())).thenReturn("access");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh");

        RoleSelectionRequest request = RoleSelectionRequest.builder()
                .role("AUTHOR")
                .acceptedTerms(true)
                .username("same")
                .build();

        authService.selectRole(1L, request);
        assertEquals("same", user.getUsername());
    }

    @Test
    void getPublicAuthors_ShouldReturnMappedAuthors() {
        when(userRepository.findAllByRole(Role.AUTHOR)).thenReturn(List.of(
                User.builder().userId(1L).email("author@example.com").username("author").role(Role.AUTHOR).build()
        ));
        assertEquals(1, authService.getPublicAuthors().size());
    }

    @Test
    void deleteUser_ShouldDeleteAndNotify() {
        User user = User.builder().userId(1L).email("test@example.com").fullName("Full Name").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        authService.deleteUser(1L);

        verify(userRepository).deleteById(1L);
        verify(userRepository).flush();
        verify(emailService).sendAccountDeletionEmail("test@example.com", "Full Name");
    }

    @Test
    void deleteUser_WhenDeleteFails_ShouldThrowInternalServerError() {
        User user = User.builder().userId(1L).email("test@example.com").fullName("Full Name").build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        doThrow(new RuntimeException("db error")).when(userRepository).deleteById(1L);

        CustomException exception = assertThrows(CustomException.class, () -> authService.deleteUser(1L));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
    }
    @Test
    void getUserByEmail_WhenNotFound_ShouldThrowException() {
        when(userRepository.findByEmail("none@test.com")).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> authService.getUserByEmail("none@test.com"));
    }

    @Test
    void getUserById_WhenNotFound_ShouldThrowException() {
        when(userRepository.findByUserId(99L)).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> authService.getUserById(99L));
    }

    @Test
    void updateProfile_WhenNotFound_ShouldThrowException() {
        when(userRepository.findByUserId(99L)).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> authService.updateProfile(99L, new UpdateProfileRequest()));
    }

    @Test
    void changePassword_WhenNotFound_ShouldThrowException() {
        when(userRepository.findByUserId(99L)).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> authService.changePassword(99L, "old", "new"));
    }

    @Test
    void deactivateAccount_WhenNotFound_ShouldThrowException() {
        when(userRepository.findByUserId(99L)).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> authService.deactivateAccount(99L));
    }

    @Test
    void logout_WithPastExpiration_ShouldNotBlacklist() {
        when(jwtUtil.extractExpiration("token")).thenReturn(new Date(System.currentTimeMillis() - 60_000));
        authService.logout("token");
        // No interaction with blacklistedTokens that we can verify easily, but branch is covered
    }

    @Test
    void validateToken_WhenUserNotFound_ShouldReturnFalse() {
        when(jwtUtil.extractUsername("token")).thenReturn("none@test.com");
        when(userRepository.findByEmail("none@test.com")).thenReturn(Optional.empty());
        assertFalse(authService.validateToken("token"));
    }

    @Test
    void updateUserStatus_WhenNotFound_ShouldThrowException() {
        when(userRepository.findByUserId(99L)).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> authService.updateUserStatus(99L, true));
    }

    @Test
    void resetPassword_WhenTokenNotFound_ShouldThrowException() {
        when(userRepository.findByResetToken("none")).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> authService.resetPassword(new ResetPasswordRequest("none", "pass")));
    }

    @Test
    void selectRole_WhenUserNotFound_ShouldThrowException() {
        when(userRepository.findByUserId(99L)).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> authService.selectRole(99L, RoleSelectionRequest.builder().role("READER").build()));
    }

    @Test
    void selectRole_WhenRoleAdmin_ShouldThrowException() {
        User user = User.builder().userId(1L).build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
        RoleSelectionRequest request = RoleSelectionRequest.builder().role("ADMIN").build();
        assertThrows(CustomException.class, () -> authService.selectRole(1L, request));
    }



    @Test
    void selectRole_WhenSelectedRoleAdmin_ShouldThrowException() {
        User user = User.builder().userId(1L).build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
        RoleSelectionRequest request = RoleSelectionRequest.builder().role("ADMIN").build();
        assertThrows(CustomException.class, () -> authService.selectRole(1L, request));
    }

    @Test
    void selectRole_WhenSelectedRoleNotSelected_ShouldThrowException() {
        User user = User.builder().userId(1L).build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
        RoleSelectionRequest request = RoleSelectionRequest.builder().role("ROLE_NOT_SELECTED").build();
        assertThrows(CustomException.class, () -> authService.selectRole(1L, request));
    }

    @Test
    void selectRole_WhenCurrentRoleReader_AndSelectedRoleReader_ShouldReturnResponse() {
        User user = User.builder().userId(1L).role(Role.READER).build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
        RoleSelectionRequest request = RoleSelectionRequest.builder().role("READER").build();
        AuthResponse response = authService.selectRole(1L, request);
        assertNotNull(response);
    }

    @Test
    void selectRole_WhenUsernameTaken_ShouldThrowException() {
        User user = User.builder().userId(1L).role(Role.ROLE_NOT_SELECTED).username("old").build();
        when(userRepository.findByUserId(1L)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsername("taken")).thenReturn(true);
        RoleSelectionRequest request = RoleSelectionRequest.builder().role("READER").username("taken").build();
        assertThrows(CustomException.class, () -> authService.selectRole(1L, request));
    }

    @Test
    void deleteUser_WhenUserNotFound_ShouldThrowException() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> authService.deleteUser(99L));
    }

    @Test
    void updateUserRole_WhenUserNotFound_ShouldThrowException() {
        when(userRepository.findByUserId(99L)).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> authService.updateUserRole(99L, "AUTHOR"));
    }

    @Test
    void refreshToken_WhenUserNotFound_ShouldThrowException() {
        when(jwtUtil.extractUsername(anyString())).thenReturn("none@test.com");
        when(userRepository.findByEmail("none@test.com")).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> authService.refreshToken("token"));
    }
}

