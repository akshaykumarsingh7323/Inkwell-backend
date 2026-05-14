package com.inkwell.category.controller;

import com.inkwell.category.dto.CategoryRequest;
import com.inkwell.category.dto.CategoryResponse;
import com.inkwell.category.dto.CategoryTreeResponse;
import com.inkwell.category.dto.TagRequest;
import com.inkwell.category.dto.TagResponse;
import com.inkwell.category.exception.CustomException;
import com.inkwell.category.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryControllerTest {

    @Mock
    private CategoryService categoryService;

    @InjectMocks
    private CategoryController categoryController;

    @Test
    void createCategory_AsAdmin_ShouldReturnCreated() {
        CategoryRequest request = new CategoryRequest();
        CategoryResponse response = new CategoryResponse();
        when(categoryService.createCategory(request)).thenReturn(response);

        assertEquals(HttpStatus.CREATED, categoryController.createCategory(request, "ADMIN").getStatusCode());
    }

    @Test
    void getAllCategories_ShouldReturnList() {
        List<CategoryResponse> responses = List.of(new CategoryResponse());
        when(categoryService.getAllCategories()).thenReturn(responses);

        assertEquals(responses, categoryController.getAllCategories().getBody());
    }

    @Test
    void getCategoryTree_ShouldReturnTree() {
        List<CategoryTreeResponse> responses = List.of(new CategoryTreeResponse());
        when(categoryService.getCategoryTree()).thenReturn(responses);

        assertEquals(responses, categoryController.getCategoryTree().getBody());
    }

    @Test
    void getCategoryBySlug_ShouldReturnCategory() {
        CategoryResponse response = new CategoryResponse();
        when(categoryService.getBySlug("tech")).thenReturn(response);

        assertEquals(response, categoryController.getCategoryBySlug("tech").getBody());
    }

    @Test
    void updateCategory_ShouldReturnUpdatedCategory() {
        CategoryRequest request = new CategoryRequest();
        CategoryResponse response = new CategoryResponse();
        when(categoryService.updateCategory(1L, request)).thenReturn(response);

        assertEquals(response, categoryController.updateCategory(1L, request, "ADMIN").getBody());
    }

    @Test
    void deleteCategory_ShouldDelegate() {
        categoryController.deleteCategory(1L, "ADMIN");

        verify(categoryService).deleteCategory(1L);
    }

    @Test
    void createTag_ShouldReturnCreated() {
        TagRequest request = new TagRequest();
        TagResponse response = new TagResponse();
        when(categoryService.createTag(request)).thenReturn(response);

        assertEquals(HttpStatus.CREATED, categoryController.createTag(request, "ADMIN").getStatusCode());
    }

    @Test
    void getAllTags_ShouldReturnList() {
        List<TagResponse> responses = List.of(new TagResponse());
        when(categoryService.getAllTags()).thenReturn(responses);

        assertEquals(responses, categoryController.getAllTags().getBody());
    }

    @Test
    void getTagBySlug_ShouldReturnTag() {
        TagResponse response = new TagResponse();
        when(categoryService.getTagBySlug("java")).thenReturn(response);

        assertEquals(response, categoryController.getTagBySlug("java").getBody());
    }

    @Test
    void deleteTag_ShouldDelegate() {
        categoryController.deleteTag(1L, "ADMIN");

        verify(categoryService).deleteTag(1L);
    }

    @Test
    void addTagToPost_WithBlankRoleHeader_ShouldAllow() {
        categoryController.addTagToPost(1L, 2L, "");

        verify(categoryService).addTagToPost(2L, 1L);
    }

    @Test
    void removeTagFromPost_AsAuthor_ShouldDelegate() {
        categoryController.removeTagFromPost(1L, 2L, "AUTHOR");

        verify(categoryService).removeTagFromPost(2L, 1L);
    }

    @Test
    void getTagsByPost_ShouldReturnTags() {
        List<TagResponse> responses = List.of(new TagResponse());
        when(categoryService.getTagsByPost(2L)).thenReturn(responses);

        assertEquals(responses, categoryController.getTagsByPost(2L).getBody());
    }

    @Test
    void getPostIdsByTag_ShouldReturnIds() {
        when(categoryService.getPostIdsByTag(1L)).thenReturn(List.of(2L, 3L));

        assertEquals(List.of(2L, 3L), categoryController.getPostIdsByTag(1L).getBody());
    }

    @Test
    void getTrendingTags_ShouldReturnTags() {
        List<TagResponse> responses = List.of(new TagResponse());
        when(categoryService.getTrendingTags()).thenReturn(responses);

        assertEquals(responses, categoryController.getTrendingTags().getBody());
    }

    @Test
    void searchTags_ShouldReturnTags() {
        List<TagResponse> responses = List.of(new TagResponse());
        when(categoryService.searchTags("ja")).thenReturn(responses);

        assertEquals(responses, categoryController.searchTags("ja").getBody());
    }

    @Test
    void incrementPostCount_AsAuthor_ShouldDelegate() {
        categoryController.incrementPostCount(1L, "AUTHOR");

        verify(categoryService).incrementPostCount(1L);
    }

    @Test
    void decrementPostCount_AsAdmin_ShouldDelegate() {
        categoryController.decrementPostCount(1L, "ADMIN");

        verify(categoryService).decrementPostCount(1L);
    }

    @Test
    void createCategory_WhenNotAdmin_ShouldThrow() {
        CustomException exception = assertThrows(CustomException.class,
                () -> categoryController.createCategory(new CategoryRequest(), "AUTHOR"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void addTagToPost_WhenRoleNotAllowed_ShouldThrow() {
        CustomException exception = assertThrows(CustomException.class,
                () -> categoryController.addTagToPost(1L, 2L, "READER"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }
}
