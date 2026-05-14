package com.inkwell.comment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "auth-service", path = "/auth")
public interface AuthClient {
    
    @GetMapping("/public/users/{userId}")
    Map<String, Object> getUserById(@PathVariable("userId") Long userId);
}
