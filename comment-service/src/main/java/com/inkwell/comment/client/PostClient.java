package com.inkwell.comment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@FeignClient(name = "post-service", path = "/posts")
public interface PostClient {
    
    @GetMapping("/{postId}")
    Map<String, Object> getPostById(@PathVariable("postId") Long postId);
}
