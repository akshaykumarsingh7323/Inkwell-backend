package com.inkwell.media.controller;

import com.inkwell.media.dto.MediaResponse;
import com.inkwell.media.service.MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Media Management", description = "Endpoints for uploading and managing media files (images, etc.)")
public class MediaController {

    private final MediaService mediaService;

    @Operation(summary = "Upload media file", description = "Uploads a new file to the server and returns the media metadata.")
    @ApiResponse(responseCode = "201", description = "File successfully uploaded")
    @PostMapping("/upload")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        
        log.info("Received upload request for file: {}, User-Id Header: {}", 
            file.getOriginalFilename(), userIdHeader);

        if (userIdHeader == null || userIdHeader.trim().isEmpty()) {
            log.error("X-User-Id header is missing or empty");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new MediaResponse(null, null, "User ID is required for upload", null, null, null, 0L, null, null, false, null));
        }

        try {
            Long uploaderId = Long.parseLong(userIdHeader);
            MediaResponse response = mediaService.uploadMedia(file, uploaderId);
            return new ResponseEntity<>(response, HttpStatus.CREATED);
        } catch (NumberFormatException e) {
            log.error("Invalid user ID format: {}", userIdHeader);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new MediaResponse(null, null, "Invalid user ID format", null, null, null, 0L, null, null, false, null));
        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new MediaResponse(null, null, "Upload failed: " + e.getMessage(), null, null, null, 0L, null, null, false, null));
        }
    }

    @Operation(summary = "Get media by ID", description = "Retrieves the metadata of a specific media file.")
    @ApiResponse(responseCode = "200", description = "Media details retrieved")
    @GetMapping("/{id}")
    public ResponseEntity<MediaResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(mediaService.getMediaById(id));
    }

    @Operation(summary = "Get media by uploader", description = "Retrieves a list of all media files uploaded by a specific user.")
    @ApiResponse(responseCode = "200", description = "List of media files retrieved")
    @GetMapping("/uploader/{uploaderId}")
    public ResponseEntity<List<MediaResponse>> getByUploader(
            @PathVariable Long uploaderId,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureOwnerOrAdmin(uploaderId, Long.parseLong(userIdHeader), roleHeader);
        return ResponseEntity.ok(mediaService.getMediaByUploader(uploaderId));
    }

    @Operation(summary = "Get media by post", description = "Retrieves all media files associated with a specific blog post.")
    @ApiResponse(responseCode = "200", description = "List of media files retrieved")
    @GetMapping("/post/{postId}")
    public ResponseEntity<List<MediaResponse>> getByPost(@PathVariable Long postId) {
        return ResponseEntity.ok(mediaService.getMediaByPost(postId));
    }

    @Operation(summary = "Update alt text", description = "Updates the accessibility alt text for a specific media file.")
    @ApiResponse(responseCode = "200", description = "Alt text updated successfully")
    @PutMapping("/{id}/alt-text")
    public ResponseEntity<MediaResponse> updateAltText(
            @PathVariable Long id,
            @RequestParam String altText,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureCanManageMedia(id, Long.parseLong(userIdHeader), roleHeader);
        return ResponseEntity.ok(mediaService.updateAltText(id, altText));
    }

    @Operation(summary = "Link media to post", description = "Associates a media file with a specific blog post.")
    @ApiResponse(responseCode = "200", description = "Media linked to post")
    @PostMapping("/{id}/link/{postId}")
    public ResponseEntity<MediaResponse> linkToPost(
            @PathVariable Long id,
            @PathVariable Long postId,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureCanManageMedia(id, Long.parseLong(userIdHeader), roleHeader);
        return ResponseEntity.ok(mediaService.linkToPost(id, postId));
    }

    @Operation(summary = "Unlink media from post", description = "Removes the association between a media file and its current post.")
    @ApiResponse(responseCode = "200", description = "Media unlinked successfully")
    @PostMapping("/{id}/unlink")
    public ResponseEntity<MediaResponse> unlinkFromPost(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureCanManageMedia(id, Long.parseLong(userIdHeader), roleHeader);
        return ResponseEntity.ok(mediaService.unlinkFromPost(id));
    }

    @Operation(summary = "Delete media", description = "Permanently deletes a media file from the server.")
    @ApiResponse(responseCode = "204", description = "Media deleted successfully")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureCanManageMedia(id, Long.parseLong(userIdHeader), roleHeader);
        mediaService.deleteMedia(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get all media", description = "Retrieves a list of all media files stored on the platform (Admin only).")
    @ApiResponse(responseCode = "200", description = "List of all media retrieved")
    @GetMapping
    public ResponseEntity<List<MediaResponse>> getAll(@RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        return ResponseEntity.ok(mediaService.getAllMedia());
    }

    private void ensureCanManageMedia(Long mediaId, Long requesterId, String requesterRole) {
        MediaResponse media = mediaService.getMediaById(mediaId);
        ensureOwnerOrAdmin(media.getUploaderId(), requesterId, requesterRole);
    }

    private void ensureOwnerOrAdmin(Long ownerId, Long requesterId, String requesterRole) {
        if (isAdmin(requesterRole) || ownerId.equals(requesterId)) {
            return;
        }
        throw new com.inkwell.media.exception.CustomException("You do not have permission to manage this media item", HttpStatus.FORBIDDEN);
    }

    private void ensureAdmin(String roleHeader) {
        if (!isAdmin(roleHeader)) {
            throw new com.inkwell.media.exception.CustomException("Admin access is required", HttpStatus.FORBIDDEN);
        }
    }

    private boolean isAdmin(String roleHeader) {
        return "ADMIN".equalsIgnoreCase(roleHeader);
    }
}
