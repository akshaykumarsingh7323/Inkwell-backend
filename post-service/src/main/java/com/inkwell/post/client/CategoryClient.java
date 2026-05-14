package com.inkwell.post.client;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "category-service", path = "/")
public interface CategoryClient {

    @PostMapping("/categories/{id}/increment-count")
    void incrementPostCount(@PathVariable("id") Long id);

    @PostMapping("/categories/{id}/decrement-count")
    void decrementPostCount(@PathVariable("id") Long id);

    @PostMapping("/tags/{tagId}/post/{postId}")
    void addTagToPost(@PathVariable("tagId") Long tagId, @PathVariable("postId") Long postId);

    @DeleteMapping("/tags/{tagId}/post/{postId}")
    void removeTagFromPost(@PathVariable("tagId") Long tagId, @PathVariable("postId") Long postId);

    @GetMapping("/tags/post/{postId}")
    List<TagSummary> getTagsByPost(@PathVariable("postId") Long postId);

    @GetMapping("/tags/{tagId}/posts")
    List<Long> getPostIdsByTag(@PathVariable("tagId") Long tagId);

    @GetMapping("/tags/search")
    List<TagSummary> searchTags(@RequestParam("keyword") String keyword);

    @Data
    class TagSummary {
        private Long tagId;
        private String name;
        private String slug;
        private Long postCount;
    }
}
