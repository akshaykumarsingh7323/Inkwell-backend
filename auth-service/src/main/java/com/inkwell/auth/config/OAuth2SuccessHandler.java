package com.inkwell.auth.config;

import com.inkwell.auth.entity.User;
import com.inkwell.auth.enums.Provider;
import com.inkwell.auth.enums.Role;
import com.inkwell.auth.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Value("${oauth2.redirect.uri:http://localhost:4200/oauth-success}")
    private String frontendRedirectUri;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2AuthenticationToken authToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = authToken.getPrincipal();
        
        String providerStr = authToken.getAuthorizedClientRegistrationId().toUpperCase();
        Provider provider = Provider.valueOf(providerStr);
        
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String providerId = oAuth2User.getName();

        if (email == null && provider == Provider.GITHUB) {
            email = oAuth2User.getAttribute("login") + "@github.com";
        }
        
        if (email == null) {
            log.error("OAuth2 email is null for provider: {}", provider);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Email not provided by " + provider);
            return;
        }

        if (name == null) {
            name = oAuth2User.getAttribute("login") != null ? oAuth2User.getAttribute("login") : email.split("@")[0];
        }

        log.info("Processing OAuth2 login for email: {}, provider: {}", email, provider);

        try {
            Optional<User> userOptional = userRepository.findByEmail(email);
            boolean isNewUser = userOptional.isEmpty();
            User user;
            
            if (isNewUser) {
                log.info("Creating new user for email: {}", email);
                String baseUsername = oAuth2User.getAttribute("login") != null ? 
                        (String) oAuth2User.getAttribute("login") : 
                        email.split("@")[0];
                
                String finalUsername = baseUsername;
                int suffix = 1;
                while (userRepository.existsByUsername(finalUsername)) {
                    finalUsername = baseUsername + "_" + suffix++;
                }

                user = User.builder()
                        .email(email)
                        .username(finalUsername)
                        .fullName(name)
                        .passwordHash(null)
                        .provider(provider)
                        .providerId(providerId)
                        .role(Role.READER)
                        .isActive(true)
                        .build();
                user = userRepository.save(user);
                log.info("New user saved with ID: {}", user.getUserId());
            } else {
                user = userOptional.get();
                log.info("Existing user found: {}", email);
                if (user.getProvider() == Provider.LOCAL || user.getProvider() == null) {
                    user.setProvider(provider);
                    user.setProviderId(providerId);
                    user = userRepository.save(user);
                }
            }

            final Role userRole = user.getRole();
            final String userEmail = user.getEmail();
            final Long userId = user.getUserId();

            org.springframework.security.core.userdetails.UserDetails userDetails = 
                org.springframework.security.core.userdetails.User.builder()
                    .username(userEmail)
                    .password("")
                    .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + userRole.name())))
                    .build();

            java.util.Map<String, Object> claims = new java.util.HashMap<>();
            claims.put("userId", userId);
            claims.put("role", userRole.name());

            String token = jwtUtil.generateToken(claims, userDetails);
            
            String targetUrl = UriComponentsBuilder.fromUriString(frontendRedirectUri)
                        .queryParam("token", token)
                        .queryParam("isNewUser", isNewUser ? "true" : "false")
                        .build().toUriString();

            log.info("Redirecting OAuth2 user to: {}", targetUrl);
            getRedirectStrategy().sendRedirect(request, response, targetUrl);

        } catch (Exception e) {
            log.error("Fatal error during OAuth2 success handling: ", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Auth error: " + e.getMessage());
        }
    }
}
