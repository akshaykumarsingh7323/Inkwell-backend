package com.inkwell.auth.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private String secret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970"; // 256-bit secret

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", secret);
        ReflectionTestUtils.setField(jwtUtil, "jwtExpiration", 3600000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 86400000L);
    }

    @Test
    void testGenerateTokenAndExtractUsername() {
        UserDetails userDetails = new User("testuser", "password", new ArrayList<>());
        String token = jwtUtil.generateToken(userDetails);
        
        assertNotNull(token);
        assertEquals("testuser", jwtUtil.extractUsername(token));
    }

    @Test
    void testIsTokenValid() {
        UserDetails userDetails = new User("testuser", "password", new ArrayList<>());
        String token = jwtUtil.generateToken(userDetails);
        
        assertTrue(jwtUtil.isTokenValid(token, userDetails));
    }

    @Test
    void testExtractExpiration() {
        UserDetails userDetails = new User("testuser", "password", new ArrayList<>());
        String token = jwtUtil.generateToken(userDetails);
        
        Date expiration = jwtUtil.extractExpiration(token);
        assertTrue(expiration.after(new Date()));
    }
}
