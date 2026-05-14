package com.inkwell.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    // Use CustomUserDetailsService directly to avoid ambiguity with AuthServiceImpl
    // which also implements UserDetailsService and causes circular dependency.
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        return path.contains("/v3/api-docs") || 
               path.contains("/swagger-ui") || 
               path.contains("/auth/login") || 
               path.contains("/auth/register") || 
               path.contains("/auth/forgot-password") || 
               path.contains("/auth/reset-password") || 
               path.contains("/oauth2/") || 
               path.contains("/login/oauth2/") ||
               path.contains("/actuator/") ||
               path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String requestURI = request.getRequestURI();
        log.debug("JWT Filter processing request: {}", requestURI);
        
        String method = request.getMethod();
        log.info("JwtFilter processing {} request: {}", method, requestURI);

        // Log all headers for deep debugging
        java.util.Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                log.debug("Header: {} = {}", name, request.getHeader(name));
            }
        }

        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        String userEmail = null;

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.info("No JWT token found for URI: {}", requestURI);
            filterChain.doFilter(request, response);
            return;
        }

        jwt = authHeader.substring(7);
        try {
            userEmail = jwtUtil.extractUsername(jwt);
            log.info("Extracted email from JWT: {}", userEmail);

            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                log.info("Authentication is null, attempting to load user for email: {}", userEmail);
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);
                log.info("UserDetails loaded for email: {}, authorities: {}", userEmail, userDetails.getAuthorities());

                if (jwtUtil.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.info("Successfully authenticated user: {} and set SecurityContext into SecurityContextHolder", userEmail);
                    
                    // Verify authentication was set
                    org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    log.info("Verified Authentication in SecurityContext: {}", auth);
                } else {
                    log.warn("JWT token is invalid for user: {}", userEmail);
                }
            } else if (userEmail != null) {
                log.info("User {} already authenticated in SecurityContext", userEmail);
            }
        } catch (org.springframework.security.core.userdetails.UsernameNotFoundException unfe) {
            log.error("User not found in database for JWT email: {}. Token is likely stale or DB was reset.", userEmail);
            sendErrorResponse(response, "User account no longer exists. Please re-register.", HttpServletResponse.SC_UNAUTHORIZED);
            return;
        } catch (Exception e) {
            log.error("Error processing JWT token for URI: {}. Error: {}", requestURI, e.getMessage(), e);
            sendErrorResponse(response, "Invalid or expired token: " + e.getMessage(), HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, String message, int status) throws IOException {
        response.setContentType("application/json");
        response.setStatus(status);
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        new ObjectMapper().writeValue(response.getOutputStream(), body);
    }
}
