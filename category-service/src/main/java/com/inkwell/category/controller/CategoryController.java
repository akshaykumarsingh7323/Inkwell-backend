package com.inkwell.category.controller;

import com.inkwell.category.dto.CategoryRequest;
import com.inkwell.category.dto.CategoryResponse;
import com.inkwell.category.dto.TagRequest;
import com.inkwell.category.dto.TagResponse;
import com.inkwell.category.exception.CustomException;
import com.inkwell.category.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/")
@RequiredArgsConstructor
@Tag(name = "Categories & Tags", description = "Endpoints for managing content classification")
public class CategoryController {

    private final CategoryService categoryService;

    // CATEGORY ENDPOINTS
    @Operation(summary = "Create a category", description = "Creates a new category for blog posts.")
    @ApiResponse(responseCode = "201", description = "Category successfully created")
    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(
            @Valid @RequestBody CategoryRequest request,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        return new ResponseEntity<>(categoryService.createCategory(request), HttpStatus.CREATED);
    }

    @Operation(summary = "Get all categories", description = "Retrieves a list of all available categories.")
    @ApiResponse(responseCode = "200", description = "List of categories retrieved")
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(categoryService.getAllCategories());
    }

    @Operation(summary = "Get category tree", description = "Retrieves a list of categories in a hierarchical nested structure.")
    @ApiResponse(responseCode = "200", description = "Category tree retrieved")
    @GetMapping("/categories/tree")
    public ResponseEntity<List<com.inkwell.category.dto.CategoryTreeResponse>> getCategoryTree() {
        return ResponseEntity.ok(categoryService.getCategoryTree());
    }

    @Operation(summary = "Get category by slug", description = "Retrieves details of a category using its slug.")
    @ApiResponse(responseCode = "200", description = "Category details retrieved")
    @GetMapping("/categories/slug/{slug}")
    public ResponseEntity<CategoryResponse> getCategoryBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(categoryService.getBySlug(slug));
    }

    @Operation(summary = "Update category", description = "Updates an existing category.")
    @ApiResponse(responseCode = "200", description = "Category updated successfully")
    @PutMapping("/categories/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        return ResponseEntity.ok(categoryService.updateCategory(id, request));
    }

    @Operation(summary = "Delete category", description = "Permanently deletes a category.")
    @ApiResponse(responseCode = "204", description = "Category deleted successfully")
    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(
            @PathVariable Long id,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    // TAG ENDPOINTS
    @Operation(summary = "Create a tag", description = "Creates a new tag for blog posts.")
    @ApiResponse(responseCode = "201", description = "Tag successfully created")
    @PostMapping("/tags")
    public ResponseEntity<TagResponse> createTag(
            @Valid @RequestBody TagRequest request,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        return new ResponseEntity<>(categoryService.createTag(request), HttpStatus.CREATED);
    }

    @Operation(summary = "Get all tags", description = "Retrieves a list of all available tags.")
    @ApiResponse(responseCode = "200", description = "List of tags retrieved")
    @GetMapping("/tags")
    public ResponseEntity<List<TagResponse>> getAllTags() {
        return ResponseEntity.ok(categoryService.getAllTags());
    }

    @Operation(summary = "Get tag by slug", description = "Retrieves details of a tag using its slug.")
    @ApiResponse(responseCode = "200", description = "Tag details retrieved")
    @GetMapping("/tags/slug/{slug}")
    public ResponseEntity<TagResponse> getTagBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(categoryService.getTagBySlug(slug));
    }

    @Operation(summary = "Delete tag", description = "Permanently deletes a tag.")
    @ApiResponse(responseCode = "204", description = "Tag deleted successfully")
    @DeleteMapping("/tags/{id}")
    public ResponseEntity<Void> deleteTag(
            @PathVariable Long id,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        categoryService.deleteTag(id);
        return ResponseEntity.noContent().build();
    }

    // POST-TAG ENDPOINTS
    @Operation(summary = "Add tag to post", description = "Associates a specific tag with a blog post.")
    @ApiResponse(responseCode = "200", description = "Tag added to post")
    @PostMapping("/tags/{tagId}/post/{postId}")
    public ResponseEntity<Void> addTagToPost(
            @PathVariable Long tagId,
            @PathVariable Long postId,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        ensureAuthorOrAdmin(roleHeader);
        categoryService.addTagToPost(postId, tagId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Remove tag from post", description = "Removes the association between a tag and a blog post.")
    @ApiResponse(responseCode = "200", description = "Tag removed from post")
    @DeleteMapping("/tags/{tagId}/post/{postId}")
    public ResponseEntity<Void> removeTagFromPost(
            @PathVariable Long tagId,
            @PathVariable Long postId,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        ensureAuthorOrAdmin(roleHeader);
        categoryService.removeTagFromPost(postId, tagId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get tags by post", description = "Retrieves all tags associated with a specific blog post.")
    @ApiResponse(responseCode = "200", description = "List of tags retrieved")
    @GetMapping("/tags/post/{postId}")
    public ResponseEntity<List<TagResponse>> getTagsByPost(@PathVariable Long postId) {
        return ResponseEntity.ok(categoryService.getTagsByPost(postId));
    }

    @Operation(summary = "Get post ids by tag", description = "Retrieves all post ids associated with a specific tag.")
    @ApiResponse(responseCode = "200", description = "List of post ids retrieved")
    @GetMapping("/tags/{tagId}/posts")
    public ResponseEntity<List<Long>> getPostIdsByTag(@PathVariable Long tagId) {
        return ResponseEntity.ok(categoryService.getPostIdsByTag(tagId));
    }

    // TRENDING
    @Operation(summary = "Get trending tags", description = "Retrieves a list of tags currently popular on the platform.")
    @ApiResponse(responseCode = "200", description = "List of trending tags retrieved")
    @GetMapping("/tags/trending")
    public ResponseEntity<List<TagResponse>> getTrendingTags() {
        return ResponseEntity.ok(categoryService.getTrendingTags());
    }

    @Operation(summary = "Search tags", description = "Retrieves a list of tags matching the keyword.")
    @ApiResponse(responseCode = "200", description = "List of tags retrieved")
    @GetMapping("/tags/search")
    public ResponseEntity<List<TagResponse>> searchTags(@RequestParam String keyword) {
        return ResponseEntity.ok(categoryService.searchTags(keyword));
    }

    @Operation(summary = "Increment post count", description = "Increments the post count for a specific category.")
    @ApiResponse(responseCode = "200", description = "Post count incremented")
    @PostMapping("/categories/{id}/increment-count")
    public ResponseEntity<Void> incrementPostCount(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        ensureAuthorOrAdmin(roleHeader);
        categoryService.incrementPostCount(id);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Decrement post count", description = "Decrements the post count for a specific category.")
    @ApiResponse(responseCode = "200", description = "Post count decremented")
    @PostMapping("/categories/{id}/decrement-count")
    public ResponseEntity<Void> decrementPostCount(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        ensureAuthorOrAdmin(roleHeader);
        categoryService.decrementPostCount(id);
        return ResponseEntity.ok().build();
    }

    private void ensureAdmin(String roleHeader) {
        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            throw new CustomException("Admin access is required", HttpStatus.FORBIDDEN);
        }
    }

    private void ensureAuthorOrAdmin(String roleHeader) {
        if (roleHeader == null || roleHeader.isBlank()) {
            return;
        }
        if (!"ADMIN".equalsIgnoreCase(roleHeader) && !"AUTHOR".equalsIgnoreCase(roleHeader)) {
            throw new CustomException("Author or admin access is required", HttpStatus.FORBIDDEN);
        }
    }
}
