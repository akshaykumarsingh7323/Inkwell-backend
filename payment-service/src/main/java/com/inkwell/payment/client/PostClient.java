package com.inkwell.payment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "post-service", url = "${POST_SERVICE_URL:http://localhost:8082}")
public interface PostClient {

    @GetMapping("/posts/{postId}")
    PostSummary getPostById(@PathVariable("postId") Long postId);

    class PostSummary {
        private Long postId;
        private Long authorId;
        private String title;

        public Long getPostId() { return postId; }
        public void setPostId(Long postId) { this.postId = postId; }
        public Long getAuthorId() { return authorId; }
        public void setAuthorId(Long authorId) { this.authorId = authorId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
    }
}
