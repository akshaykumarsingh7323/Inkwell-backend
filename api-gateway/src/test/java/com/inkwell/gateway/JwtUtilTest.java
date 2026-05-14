package com.inkwell.gateway;

import com.inkwell.gateway.util.JwtUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtUtilTest {

    @Test
    void jwtUtil_ShouldExtractClaimsAndValidateToken() {
        String secret = "12345678901234567890123456789012";
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("john@example.com")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key)
                .compact();

        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", "  " + secret + "  ");
        jwtUtil.init();

        assertEquals("john@example.com", jwtUtil.extractUsername(token));
        assertTrue(jwtUtil.extractExpiration(token).after(new Date()));
        assertTrue(jwtUtil.validateToken(token));
    }

    @Test
    void validateToken_WhenExpiredOrInvalid_ShouldReturnFalse() {
        String secret = "12345678901234567890123456789012";
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        String expired = Jwts.builder()
                .subject("john@example.com")
                .expiration(new Date(System.currentTimeMillis() - 60_000))
                .signWith(key)
                .compact();

        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
        jwtUtil.init();

        assertFalse(jwtUtil.validateToken(expired));
        assertFalse(jwtUtil.validateToken("not-a-token"));
        assertFalse(jwtUtil.validateToken(null));
    }

    @Test
    void validateToken_WhenExpirationIsNowOrFuture_ShouldReturnExpectedResult() {
        String secret = "12345678901234567890123456789012";
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        String validToken = Jwts.builder()
                .subject("future@example.com")
                .expiration(new Date(System.currentTimeMillis() + 5_000))
                .signWith(key)
                .compact();

        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
        jwtUtil.init();

        assertTrue(jwtUtil.validateToken(validToken));
    }

    @Test
    void init_WithNullSecret_ShouldUseEmptyString() {
        JwtUtil jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", null);
        // We expect it to throw due to weak key, but we want to cover the null check branch
        assertThrows(Exception.class, jwtUtil::init);
    }
}
