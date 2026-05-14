package com.inkwell.post.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "auth-service")
public interface AuthClient {

    @GetMapping("/auth/search")
    List<Map<String, Object>> searchUsers(@RequestParam("keyword") String keyword);
}
