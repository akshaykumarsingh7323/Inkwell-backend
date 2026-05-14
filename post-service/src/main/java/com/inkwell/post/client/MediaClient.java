package com.inkwell.post.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "media-service")
public interface MediaClient {

    /**
     * Verifies if a media item exists and belongs to the specified user.
     * Note: media-service should return 200 OK with media details if it exists and the user has access.
     */
    @GetMapping("/media/{mediaId}")
    Map<String, Object> getMedia(@PathVariable("mediaId") Long mediaId,
                                 @RequestHeader("X-User-Id") String userId,
                                 @RequestHeader("X-User-Role") String role);
}
