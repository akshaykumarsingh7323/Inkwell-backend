package com.inkwell.gateway.config;

import com.inkwell.gateway.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;


@Component
@Slf4j
public class JwtAuthenticationFilter implements GlobalFilter {

    private static final List<Pattern> PUBLIC_GET_PATTERNS = List.of(
            Pattern.compile(".*/posts/published(?:/.*)?$"),
            Pattern.compile(".*/posts/slug/[^/]+(?:\\?.*)?$"),
            Pattern.compile(".*/posts/search(?:\\?.*)?$"),
            Pattern.compile(".*/posts/explore(?:\\?.*)?$"),
            Pattern.compile(".*/posts/public/author/\\d+(?:\\?.*)?$"),
            Pattern.compile(".*/categories(?:/.*)?$"),
            Pattern.compile(".*/tags(?:/.*)?$"),
            Pattern.compile(".*/newsletter/confirm(?:\\?.*)?$"),
            Pattern.compile(".*/newsletter/unsubscribe(?:/.*)?(?:\\?.*)?$"),
            Pattern.compile(".*/auth/public/users/\\d+(?:\\?.*)?$"),
            Pattern.compile(".*/auth/public/authors(?:/.*)?(?:\\?.*)?$"),
            Pattern.compile(".*/posts/public/trending(?:\\?.*)?$"),
            Pattern.compile(".*/comments/post/\\d+(?:/count)?(?:\\?.*)?$"),
            Pattern.compile(".*/media/files/.*$")
    );

    private static final List<Pattern> PUBLIC_POST_PATTERNS = List.of(
            Pattern.compile(".*/newsletter/subscribe(?:\\?.*)?$"),
            Pattern.compile(".*/posts/\\d+/view(?:\\?.*)?$")
    );

    @Autowired
    private JwtUtil jwtUtil;


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();

        log.info("Gateway filtering {} request: {}", method, path);

        if ("OPTIONS".equalsIgnoreCase(method)) {
            return chain.filter(exchange);
        }

        String token = this.getAuthHeader(request);
        boolean isPublic = isPublicEndpoint(path, method);

        if (token != null) {
            // If a token is provided, it MUST be valid, even for public endpoints
            if (!jwtUtil.validateToken(token)) {
                log.error("Invalid JWT token for request: {}", request.getURI());
                return this.onError(exchange, "Invalid JWT token", HttpStatus.UNAUTHORIZED);
            }



            // Valid token, proceed with population
            return this.populateRequestAndFilter(exchange, chain, token);
        } else {
            // No token provided
            if (!isPublic) {
                log.error("Authorization header is missing for protected request: {}", request.getURI());
                return this.onError(exchange, "Authorization header is missing", HttpStatus.UNAUTHORIZED);
            }
            // Public endpoint, no token, proceed normally
            return chain.filter(exchange);
        }
    }

    private boolean isPublicEndpoint(String rawPath, String rawMethod) {
        String path = rawPath != null && rawPath.startsWith("/") ? rawPath : "/" + rawPath;
        String method = rawMethod != null ? rawMethod.toUpperCase() : "GET";

        // Paths that should never require a JWT (Auth login/register, OAuth2, Login callbacks, Docs, Health)
        String lowerPath = path.toLowerCase();
        
        // Explicitly public auth endpoints
        if (lowerPath.contains("/auth/login") || 
            lowerPath.contains("/auth/register") || 
            lowerPath.contains("/auth/forgot-password") || 
            lowerPath.contains("/auth/reset-password") ||
            lowerPath.contains("/auth/refresh") ||
            lowerPath.contains("/auth/oauth-success")) {
            return true;
        }

        // Other public infrastructures
        if (lowerPath.contains("/oauth2")
                || lowerPath.contains("/login/") // social login callbacks
                || lowerPath.contains("/v3/api-docs")
                || lowerPath.contains("/swagger-ui")
                || lowerPath.contains("/swagger-resources")
                || lowerPath.contains("/actuator/")) {
            return true;
        }

        if ("POST".equals(method)) {
            return matchesAny(path, PUBLIC_POST_PATTERNS);
        }

        if (!"GET".equals(method)) {
            return false;
        }

        return matchesAny(path, PUBLIC_GET_PATTERNS);
    }

    private boolean matchesAny(String path, List<Pattern> patterns) {
        return patterns.stream().anyMatch(pattern -> pattern.matcher(path).matches());
    }

    private Mono<Void> populateRequestAndFilter(ServerWebExchange exchange, GatewayFilterChain chain, String token) {
        String username = jwtUtil.extractUsername(token);
        
        io.jsonwebtoken.Claims claims = jwtUtil.getAllClaimsFromToken(token);
        String userId = claims.get("userId") != null ? String.valueOf(claims.get("userId")) : "";
        String role = claims.get("role") != null ? String.valueOf(claims.get("role")) : "";
        
        ServerHttpRequest request = exchange.getRequest();
        boolean isPublic = isPublicEndpoint(request.getURI().getPath(), request.getMethod().name());
        
        if (userId.isEmpty() && !isPublic) {
            log.error("JWT token does not contain userId for protected request: {}", request.getURI());
            return this.onError(exchange, "Session expired or invalid token. Please log in again.", HttpStatus.UNAUTHORIZED);
        }
        
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(httpHeaders -> {
                    httpHeaders.set("X-User-Name", username);
                    httpHeaders.set("X-User-Id", userId);
                    httpHeaders.set("X-User-Role", role);
                })
                .build();
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private Mono<Void> onError(ServerWebExchange exchange, String err, HttpStatus httpStatus) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String body = String.format("{\"success\": false, \"message\": \"%s\", \"path\": \"%s\"}", 
                err, exchange.getRequest().getURI().getPath());
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        org.springframework.core.io.buffer.DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private String getAuthHeader(ServerHttpRequest request) {
        java.util.List<String> headers = request.getHeaders().get("Authorization");
        if (headers == null || headers.isEmpty()) {
            return null;
        }
        String header = headers.get(0);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private boolean isAuthMissing(ServerHttpRequest request) {
        return !request.getHeaders().containsKey("Authorization");
    }

}
