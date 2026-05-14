package com.inkwell.auth.repository;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.inkwell.auth.entity.User;

import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
public class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldSaveUser() {

        User user = new User();
        user.setEmail("test@gmail.com");
        user.setUsername("test");
        user.setPasswordHash("123");

        User saved = userRepository.save(user);

        assertNotNull(saved.getUserId());
    }
}