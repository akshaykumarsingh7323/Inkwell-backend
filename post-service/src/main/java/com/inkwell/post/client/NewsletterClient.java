package com.inkwell.post.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "newsletter-service", path = "/newsletter")
public interface NewsletterClient {

    @PostMapping("/post-notify")
    void sendPostNotification(@RequestParam("title") String title, @RequestParam("url") String url);
}
