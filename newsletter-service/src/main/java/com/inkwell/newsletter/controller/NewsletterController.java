package com.inkwell.newsletter.controller;

import com.inkwell.newsletter.dto.*;
import com.inkwell.newsletter.entity.Subscriber;
import com.inkwell.newsletter.exception.CustomException;
import com.inkwell.newsletter.service.NewsletterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.inkwell.newsletter.entity.SubscriberStatus;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/newsletter")
@RequiredArgsConstructor
@Tag(name = "Newsletter Management", description = "Endpoints for handling subscriptions and sending campaigns")
public class NewsletterController {

    private final NewsletterService newsletterService;

    @Operation(summary = "Subscribe to newsletter", description = "Registers a new email address for the newsletter. A confirmation email will be sent.")
    @ApiResponse(responseCode = "200", description = "Subscription request received")
    @PostMapping("/subscribe")
    public ResponseEntity<String> subscribe(@Valid @RequestBody SubscribeRequest request) {
        newsletterService.subscribe(request);
        return ResponseEntity.ok("Subscription request received. Please check your email to confirm.");
    }

    @Operation(summary = "Confirm subscription", description = "Validates the subscription using a token sent via email.")
    @ApiResponse(responseCode = "200", description = "Subscription confirmed")
    @GetMapping("/confirm")
    public ResponseEntity<String> confirm(@RequestParam String token) {
        newsletterService.confirmSubscription(token);
        return ResponseEntity.ok("Subscription confirmed successfully!");
    }

    @Operation(summary = "Unsubscribe", description = "Removes a subscriber from the mailing list using a unique token.")
    @ApiResponse(responseCode = "200", description = "Unsubscribed successfully")
    @GetMapping("/unsubscribe")
    public ResponseEntity<String> unsubscribe(@RequestParam String token) {
        newsletterService.unsubscribe(token);
        return ResponseEntity.ok("You have been unsubscribed successfully.");
    }

    @Operation(summary = "Get all subscribers", description = "Retrieves a list of all newsletter subscribers (Admin only).")
    @ApiResponse(responseCode = "200", description = "List of subscribers retrieved")
    @GetMapping("/subscribers")
    public ResponseEntity<List<Subscriber>> getAllSubscribers(
            @RequestParam(required = false) Long authorId,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureAdminOrAuthor(roleHeader);
        Long targetAuthorId = (authorId != null) ? authorId : Long.parseLong(userIdHeader);
        return ResponseEntity.ok(newsletterService.getAllSubscribers(targetAuthorId));
    }

    @Operation(summary = "Send newsletter", description = "Dispatches a manual newsletter campaign to all active subscribers.")
    @ApiResponse(responseCode = "200", description = "Newsletter sent successfully")
    @PostMapping("/send")
    public ResponseEntity<String> sendNewsletter(
            @Valid @RequestBody NewsletterRequest request,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        newsletterService.sendNewsletter(request);
        return ResponseEntity.ok("Newsletter sent to all active subscribers.");
    }

    @Operation(summary = "Notify new post", description = "Triggers an automated notification email for a new blog post.")
    @ApiResponse(responseCode = "200", description = "Post notification sent")
    @PostMapping("/post-notify")
    public ResponseEntity<String> notifyNewPost(
            @RequestParam String title,
            @RequestParam String url,
            @RequestParam(required = false) Long authorId) {
        newsletterService.sendPostNotification(title, url, authorId);
        return ResponseEntity.ok("Post notification sent to active subscribers.");
    }

    @Operation(summary = "Update preferences", description = "Updates frequency or topic preferences for a subscriber.")
    @ApiResponse(responseCode = "200", description = "Preferences updated")
    @PutMapping("/preferences/{subscriberId}")
    public ResponseEntity<String> updatePreferences(
            @PathVariable Long subscriberId,
            @RequestBody PreferenceRequest request) {
        newsletterService.updatePreferences(subscriberId, request);
        return ResponseEntity.ok("Preferences updated successfully.");
    }

    @Operation(summary = "Get subscriber count", description = "Returns the total number of active subscribers.")
    @ApiResponse(responseCode = "200", description = "Count retrieved successfully")
    @GetMapping("/count")
    public ResponseEntity<Long> getCount(
            @RequestParam(required = false) Long authorId,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureAdminOrAuthor(roleHeader);
        Long targetAuthorId = (authorId != null) ? authorId : Long.parseLong(userIdHeader);
        return ResponseEntity.ok(newsletterService.getSubscriberCount(targetAuthorId));
    }

    @Operation(summary = "Send targeted campaign",
               description = "Sends a newsletter campaign filtered by subscriber status and/or preference tags.")
    @ApiResponse(responseCode = "200", description = "Campaign sent successfully")
    @PostMapping("/campaign")
    public ResponseEntity<String> sendCampaign(
            @Valid @RequestBody CampaignRequest request,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureAdminOrAuthor(roleHeader);
        Long authorId = "ADMIN".equalsIgnoreCase(roleHeader) ? null : Long.parseLong(userIdHeader);
        newsletterService.sendCampaign(request, authorId);
        return ResponseEntity.ok("Campaign dispatched successfully.");
    }

    @Operation(summary = "Resend confirmation email",
               description = "Regenerates the confirmation token and resends the double opt-in email.")
    @ApiResponse(responseCode = "200", description = "Confirmation email resent")
    @PostMapping("/resend-confirmation")
    public ResponseEntity<String> resendConfirmation(
            @Valid @RequestBody ResendConfirmationRequest request) {
        newsletterService.resendConfirmation(request);
        return ResponseEntity.ok("Confirmation email resent. Please check your inbox.");
    }

    @Operation(summary = "Get subscriber by email", description = "Retrieves a subscriber's details using their email address.")
    @GetMapping("/subscribers/{email}")
    public ResponseEntity<Subscriber> getSubscriberByEmail(
            @PathVariable String email,
            @RequestParam Long authorId) {
        return ResponseEntity.ok(newsletterService.getSubscriberByEmail(email, authorId));
    }

    @Operation(summary = "Unsubscribe by email", description = "Unsubscribes a user from an author using their email address.")
    @DeleteMapping("/unsubscribe/{email}")
    public ResponseEntity<String> unsubscribeByEmail(
            @PathVariable String email,
            @RequestParam Long authorId) {
        newsletterService.unsubscribeByEmail(email, authorId);
        return ResponseEntity.ok("Unsubscribed successfully.");
    }

    @Operation(summary = "Get newsletter analytics", description = "Returns subscriber statistics for admin/author dashboard.")
    @GetMapping("/analytics")
    public ResponseEntity<NewsletterAnalytics> getAnalytics(
            @RequestParam(required = false) Long authorId,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        ensureAdminOrAuthor(roleHeader);
        Long targetAuthorId = ("ADMIN".equalsIgnoreCase(roleHeader)) ? authorId : (userIdHeader != null ? Long.valueOf(userIdHeader) : null);
        return ResponseEntity.ok(newsletterService.getAnalytics(targetAuthorId));
    }

    @Operation(summary = "Search subscribers (Admin only)", description = "Retrieves a paginated and filtered list of subscribers.")
    @GetMapping("/admin/search")
    public ResponseEntity<Page<Subscriber>> searchSubscribers(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) SubscriberStatus status,
            @RequestParam(required = false) String preference,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "subscribedAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        ensureAdmin(roleHeader);
        Sort sort = direction.equalsIgnoreCase("asc") ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(newsletterService.searchSubscribers(query, status, preference, pageable));
    }

    @Operation(summary = "Get subscriber details (Admin only)", description = "Retrieves full details of a specific subscriber.")
    @GetMapping("/admin/subscribers/{id}")
    public ResponseEntity<Subscriber> getSubscriberById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        ensureAdmin(roleHeader);
        return ResponseEntity.ok(newsletterService.getSubscriberById(id));
    }

    private void ensureAdmin(String roleHeader) {
        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            throw new CustomException("Admin access is required", HttpStatus.FORBIDDEN);
        }
    }

    private void ensureAdminOrAuthor(String roleHeader) {
        if (!"ADMIN".equalsIgnoreCase(roleHeader) && !"AUTHOR".equalsIgnoreCase(roleHeader)) {
            throw new CustomException("Admin or Author access is required", HttpStatus.FORBIDDEN);
        }
    }
}

