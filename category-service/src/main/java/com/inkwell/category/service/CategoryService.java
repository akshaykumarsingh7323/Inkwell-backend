package com.inkwell.category.service;

import com.inkwell.category.dto.CategoryRequest;
import com.inkwell.category.dto.CategoryResponse;
import com.inkwell.category.dto.TagRequest;
import com.inkwell.category.dto.TagResponse;

import java.util.List;

public interface CategoryService {
    // Category
    CategoryResponse createCategory(CategoryRequest request);
    CategoryResponse getBySlug(String slug);
    List<CategoryResponse> getAllCategories();
    List<com.inkwell.category.dto.CategoryTreeResponse> getCategoryTree();
    CategoryResponse updateCategory(Long categoryId, CategoryRequest request);
    void deleteCategory(Long categoryId);
    void incrementPostCount(Long categoryId);
    void decrementPostCount(Long categoryId);

    // Tag
    TagResponse createTag(TagRequest request);
    TagResponse getTagBySlug(String slug);
    List<TagResponse> getAllTags();
    void deleteTag(Long tagId);

    // Post-Tag Association
    void addTagToPost(Long postId, Long tagId);
    void removeTagFromPost(Long postId, Long tagId);
    List<TagResponse> getTagsByPost(Long postId);
    List<Long> getPostIdsByTag(Long tagId);

    // Trending
    List<TagResponse> getTrendingTags();

    List<TagResponse> searchTags(String keyword);
}
