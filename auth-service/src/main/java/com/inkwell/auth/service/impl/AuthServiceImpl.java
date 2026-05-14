package com.inkwell.auth.service.impl;

import com.inkwell.auth.config.JwtUtil;
import com.inkwell.auth.config.CustomUserDetailsService;
import com.inkwell.auth.dto.*;
import com.inkwell.auth.entity.User;
import com.inkwell.auth.enums.Provider;
import com.inkwell.auth.enums.Role;
import com.inkwell.auth.exception.CustomException;
import com.inkwell.auth.repository.UserRepository;
import com.inkwell.auth.service.AuthService;
import com.inkwell.auth.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;
    private final java.util.Set<String> blacklistedTokens = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public AuthServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtUtil jwtUtil,
            @Lazy AuthenticationManager authenticationManager,
            EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.authenticationManager = authenticationManager;
        this.emailService = emailService;
    }

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("New user registration attempt for email: {}", request.getEmail());
        
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: Email {} already exists", request.getEmail());
            throw new CustomException("Email already registered", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("Registration failed: Username {} already exists", request.getUsername());
            throw new CustomException("Username already taken", HttpStatus.CONFLICT);
        }

        String username = request.getUsername();
        if (username == null || username.trim().isEmpty()) {
            String base = request.getEmail().split("@")[0];
            username = base;
            int count = 1;
            while (userRepository.existsByUsername(username)) {
                username = base + count++;
            }
        }

        User user = User.builder()
                .username(username)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .role(Role.READER)
                .provider(Provider.LOCAL)
                .isActive(true)
                .build();

        user = userRepository.save(user);
        log.info("User registered successfully: {}", user.getEmail());

        Role effectiveRole = user.getRole() != null ? user.getRole() : Role.READER;
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + effectiveRole.name())))
                .build();

        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("role", effectiveRole.name());

        String token = jwtUtil.generateToken(claims, userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        emailService.sendWelcomeEmail(user.getEmail(), user.getFullName());

        return mapToAuthResponse(user, token, refreshToken);
    }

    @Override
    public AuthResponse login(AuthRequest request) {
        log.info("Login attempt for user: {}", request.getUsernameOrEmail());
        
        User user = userRepository.findByEmail(request.getUsernameOrEmail())
                .orElseGet(() -> userRepository.findByUsername(request.getUsernameOrEmail())
                        .orElseThrow(() -> {
                            log.warn("Login failed: User {} not found", request.getUsernameOrEmail());
                            return new CustomException("Email not registered. Please create an account.", HttpStatus.NOT_FOUND);
                        }));

        if (!user.isActive()) {
            log.warn("Login failed: Account {} is deactivated", user.getEmail());
            throw new CustomException("Account is deactivated", HttpStatus.FORBIDDEN);
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(user.getEmail(), request.getPassword())
            );
        } catch (Exception e) {
            log.warn("Login failed: Authentication failed for {}", user.getEmail());
            throw new CustomException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        Role effectiveRole = user.getRole() != null ? user.getRole() : Role.READER;
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + effectiveRole.name())))
                .build();

        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("role", effectiveRole.name());

        String token = jwtUtil.generateToken(claims, userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        log.info("Login successful for user: {}", user.getEmail());
        
        emailService.sendLoginNotificationEmail(user.getEmail(), user.getUsername());

        return mapToAuthResponse(user, token, refreshToken);
    }

    @Override
    public void logout(String token) {
        long expiration = jwtUtil.extractExpiration(token).getTime() - System.currentTimeMillis();
        if (expiration > 0) {
            try {
                blacklistedTokens.add(token);
                log.info("Token blacklisted for logout: {}", token);
            } catch (Exception e) {
                log.warn("Failed to blacklist token: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean validateToken(String token) {
        try {
            String email = jwtUtil.extractUsername(token);
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            Role effectiveRole = user.getRole() != null ? user.getRole() : Role.READER;
            UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                    .username(user.getEmail())
                    .password(user.getPasswordHash())
                    .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + effectiveRole.name())))
                    .build();
                    
            try {
                if (blacklistedTokens.contains(token)) {
                    log.warn("Token is blacklisted: {}", token);
                    return false;
                }
            } catch (Exception e) {
                log.warn("Skipping blacklist check: {}", e.getMessage());
            }
            return jwtUtil.isTokenValid(token, userDetails);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public AuthResponse refreshToken(String token) {
        String email = jwtUtil.extractUsername(token);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        Role effectiveRole = user.getRole() != null ? user.getRole() : Role.READER;
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + effectiveRole.name())))
                .build();

        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("role", effectiveRole.name());

        if (jwtUtil.isTokenValid(token, userDetails)) {
            String newToken = jwtUtil.generateToken(claims, userDetails);
            return mapToAuthResponse(user, newToken, token);
        }
        throw new CustomException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
    }

    @Override
    public AuthResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        return mapToAuthResponse(user, null, null);
    }

    @Override
    public AuthResponse getUserById(Long userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        return mapToAuthResponse(user, null, null);
    }

    @Override
    @Transactional
    public AuthResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getBio() != null) user.setBio(request.getBio());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        userRepository.save(user);
        return mapToAuthResponse(user, null, null);
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new CustomException("Invalid old password", HttpStatus.BAD_REQUEST);
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Override
    public List<AuthResponse> searchUsers(String keyword) {
        return userRepository.searchByUsername(keyword).stream()
                .map(user -> mapToAuthResponse(user, null, null))
                .collect(Collectors.toList());
    }

    @Override
    public List<AuthResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(user -> mapToAuthResponse(user, null, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deactivateAccount(Long userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        user.setActive(false);
        userRepository.save(user);
    }

    @Override
    @Transactional
    public AuthResponse updateUserRole(Long targetUserId, String role) {
        User user = userRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        try {
            user.setRole(Role.valueOf(role.toUpperCase()));
        } catch (IllegalArgumentException ex) {
            throw new CustomException("Invalid role", HttpStatus.BAD_REQUEST);
        }
        userRepository.save(user);
        return mapToAuthResponse(user, null, null);
    }

    @Override
    @Transactional
    public AuthResponse updateUserStatus(Long targetUserId, boolean active) {
        User user = userRepository.findByUserId(targetUserId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        
        boolean oldStatus = user.isActive();
        user.setActive(active);
        userRepository.save(user);

        // Send email if suspended
        if (oldStatus && !active) {
            emailService.sendSuspensionEmail(user.getEmail(), user.getFullName());
            log.info("User {} suspended by admin, notification email sent.", user.getEmail());
        }

        return mapToAuthResponse(user, null, null);
    }

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException("User not found with this email", HttpStatus.NOT_FOUND));

        // Generate 6-digit OTP
        String otp = String.format("%06d", new java.util.Random().nextInt(1000000));
        user.setResetToken(otp);
        user.setResetTokenExpiry(LocalDateTime.now().plusMinutes(10)); // OTP valid for 10 mins
        userRepository.save(user);

        // Send email via EmailService
        emailService.sendResetPasswordEmail(user.getEmail(), otp);
        log.info("Reset password token generated and email queued for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByResetToken(request.getToken())
                .orElseThrow(() -> new CustomException("Invalid or expired token", HttpStatus.BAD_REQUEST));

        if (user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new CustomException("Token has expired", HttpStatus.BAD_REQUEST);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);
        
        log.info("Password successfully reset for user: {}", user.getEmail());
    }

    @Override
    @Transactional
    public AuthResponse selectRole(Long userId, RoleSelectionRequest request) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        Role selectedRole;
        try {
            selectedRole = Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new CustomException("Invalid role", HttpStatus.BAD_REQUEST);
        }

        if (selectedRole == Role.ADMIN || selectedRole == Role.ROLE_NOT_SELECTED) {
            throw new CustomException("Invalid role selection", HttpStatus.BAD_REQUEST);
        }

        if (selectedRole == Role.AUTHOR && !request.isAcceptedTerms()) {
            throw new CustomException("You must accept the author terms before continuing", HttpStatus.BAD_REQUEST);
        }

        Role currentRole = user.getRole();
        if (currentRole == Role.ADMIN) {
            throw new CustomException("Admin role cannot be changed here", HttpStatus.FORBIDDEN);
        }

        if (currentRole == Role.AUTHOR && selectedRole == Role.AUTHOR) {
            return mapToAuthResponse(user, null, null);
        }

        if (currentRole == Role.READER && selectedRole == Role.READER) {
            return mapToAuthResponse(user, null, null);
        }

        if (currentRole == Role.READER && selectedRole != Role.AUTHOR) {
            throw new CustomException("Reader accounts can only upgrade to AUTHOR here", HttpStatus.BAD_REQUEST);
        }

        if (currentRole != Role.ROLE_NOT_SELECTED && currentRole != Role.READER) {
            throw new CustomException("Role already assigned", HttpStatus.BAD_REQUEST);
        }

        if (request.getUsername() != null && !request.getUsername().trim().isEmpty()) {
            if (userRepository.existsByUsername(request.getUsername()) && !request.getUsername().equals(user.getUsername())) {
                throw new CustomException("Username already taken", HttpStatus.CONFLICT);
            }
            user.setUsername(request.getUsername().trim());
        }
        
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().trim().isEmpty()) {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }

        user.setRole(selectedRole);
        userRepository.save(user);
        log.info("User {} upgraded to role: {}", user.getEmail(), selectedRole);

        // Generate new JWT after role selection
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build();

        java.util.Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("role", user.getRole().name());

        String token = jwtUtil.generateToken(claims, userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        return mapToAuthResponse(user, token, refreshToken);
    }

    @Override
    public List<AuthResponse> getPublicAuthors() {
        return userRepository.findAllByRole(Role.AUTHOR).stream()
                .map(user -> mapToAuthResponse(user, null, null))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        log.info("Attempting to permanently delete user ID: {}", userId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException("User not found in the cosmic registry", HttpStatus.NOT_FOUND));
        
        String email = user.getEmail();
        String fullName = user.getFullName();

        // Safety check: Prevent self-deletion if possible (requires current user info)
        // For now, we proceed with standard deletion logic but use deleteById for better JPA handling
        
        try {
            userRepository.deleteById(userId);
            userRepository.flush(); // Force immediate execution
            log.info("User {} ({}) permanently purged from database.", fullName, email);
            
            // Send notification after successful purge
            emailService.sendAccountDeletionEmail(email, fullName);
        } catch (Exception e) {
            log.error("Failed to purge user {}: {}", email, e.getMessage());
            throw new CustomException("Failed to delete user: Database constraint or system error", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private AuthResponse mapToAuthResponse(User user, String token, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(token)
                .refreshToken(refreshToken)
                .userId(String.valueOf(user.getUserId()))
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole() != null ? user.getRole().name() : Role.READER.name())
                .active(user.isActive())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .phoneNumber(user.getPhoneNumber())
                .build();
    }
}
