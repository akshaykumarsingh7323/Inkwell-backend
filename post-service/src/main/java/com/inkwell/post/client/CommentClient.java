package com.inkwell.post.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "comment-service", path = "/comments")
public interface CommentClient {

    @GetMapping("/post/{postId}/count")
    Long getCommentCountByPost(@PathVariable("postId") Long postId);
}
