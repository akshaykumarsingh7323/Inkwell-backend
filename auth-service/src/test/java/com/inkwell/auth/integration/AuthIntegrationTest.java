package com.inkwell.auth.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.inkwell.auth.dto.RegisterRequest;

import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class AuthIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldRegisterUser() {

        RegisterRequest request = new RegisterRequest();
        request.setEmail("test@gmail.com");
        request.setUsername("test");
        request.setPassword("12345678");
        request.setFullName("Test User");

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/auth/register", request, String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}
