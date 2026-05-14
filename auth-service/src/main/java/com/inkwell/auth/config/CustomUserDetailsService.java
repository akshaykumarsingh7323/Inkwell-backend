package com.inkwell.auth.config;

import com.inkwell.auth.enums.Role;
import com.inkwell.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Standalone UserDetailsService to break the circular dependency:
 * SecurityConfig → JwtFilter → UserDetailsService (AuthServiceImpl)
 *                              → AuthenticationManager (SecurityConfig bean)
 *
 * By extracting UserDetailsService into its own bean with no dependency on
 * SecurityConfig beans, the cycle is eliminated.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        com.inkwell.auth.entity.User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash() != null ? user.getPasswordHash() : "")
                .authorities(Collections.singletonList(() -> "ROLE_" + (user.getRole() != null ? user.getRole().name() : Role.READER.name())))
                .build();
    }
}
