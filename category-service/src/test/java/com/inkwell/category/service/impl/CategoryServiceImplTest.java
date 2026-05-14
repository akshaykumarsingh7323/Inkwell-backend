package com.inkwell.category.service.impl;

import com.inkwell.category.dto.CategoryRequest;
import com.inkwell.category.dto.CategoryResponse;
import com.inkwell.category.dto.TagRequest;
import com.inkwell.category.dto.TagResponse;
import com.inkwell.category.entity.Category;
import com.inkwell.category.entity.PostTag;
import com.inkwell.category.entity.Tag;
import com.inkwell.category.exception.CustomException;
import com.inkwell.category.repository.CategoryRepository;
import com.inkwell.category.repository.PostTagRepository;
import com.inkwell.category.repository.TagRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TagRepository tagRepository;

    @Mock
    private PostTagRepository postTagRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    void createCategory_ShouldCreateWithUniqueSlug() {
        when(categoryRepository.existsBySlug("tech")).thenReturn(true);
        when(categoryRepository.existsBySlug("tech-1")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryRequest request = new CategoryRequest();
        request.setName("Tech");
        request.setDescription("Technology");

        CategoryResponse response = categoryService.createCategory(request);

        assertEquals("tech-1", response.getSlug());
    }

    @Test
    void createCategory_WithMissingParent_ShouldThrowNotFound() {
        when(categoryRepository.existsBySlug("child")).thenReturn(false);
        when(categoryRepository.findById(9L)).thenReturn(Optional.empty());

        CategoryRequest request = new CategoryRequest();
        request.setName("Child");
        request.setParentCategoryId(9L);

        CustomException exception = assertThrows(CustomException.class, () -> categoryService.createCategory(request));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatus());
    }

    @Test
    void getBySlug_ShouldReturnCategory() {
        Category category = Category.builder().categoryId(1L).name("Tech").slug("tech").build();
        when(categoryRepository.findBySlug("tech")).thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.getBySlug("tech");

        assertEquals("Tech", response.getName());
    }

    @Test
    void getBySlug_WhenMissing_ShouldThrowNotFound() {
        when(categoryRepository.findBySlug("missing")).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> categoryService.getBySlug("missing"));
    }

    @Test
    void getAllCategories_ShouldMapAllItems() {
        when(categoryRepository.findAll()).thenReturn(List.of(
                Category.builder().categoryId(1L).name("Tech").slug("tech").build(),
                Category.builder().categoryId(2L).name("Life").slug("life").build()
        ));

        List<CategoryResponse> result = categoryService.getAllCategories();

        assertEquals(2, result.size());
    }

    @Test
    void getCategoryTree_ShouldReturnHierarchy() {
        Category parent = Category.builder().categoryId(1L).name("Parent").slug("parent").build();
        Category child = Category.builder().categoryId(2L).name("Child").slug("child").parentCategoryId(1L).build();
        when(categoryRepository.findAll()).thenReturn(List.of(parent, child));

        var tree = categoryService.getCategoryTree();

        assertEquals(1, tree.size());
        assertEquals(1, tree.get(0).getChildren().size());
    }

    @Test
    void updateCategory_WhenNameChanges_ShouldUpdateSlug() {
        Category existing = Category.builder().categoryId(1L).name("Old").slug("old").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsBySlug("new")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryRequest request = new CategoryRequest();
        request.setName("New");

        CategoryResponse response = categoryService.updateCategory(1L, request);

        assertEquals("new", response.getSlug());
    }

    @Test
    void updateCategory_WhenNameUnchanged_ShouldKeepSlug() {
        Category existing = Category.builder().categoryId(1L).name("Old").slug("old").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(existing)).thenReturn(existing);

        CategoryRequest request = new CategoryRequest();
        request.setName("old");
        request.setDescription("Updated");

        CategoryResponse response = categoryService.updateCategory(1L, request);

        assertEquals("old", response.getSlug());
        assertEquals("Updated", response.getDescription());
    }

    @Test
    void deleteCategory_ShouldFailIfHasChildren() {
        when(categoryRepository.findByParentCategoryId(1L)).thenReturn(List.of(new Category()));
        assertThrows(CustomException.class, () -> categoryService.deleteCategory(1L));
    }

    @Test
    void deleteCategory_WithoutChildren_ShouldDelete() {
        when(categoryRepository.findByParentCategoryId(1L)).thenReturn(List.of());
        categoryService.deleteCategory(1L);
        verify(categoryRepository).deleteById(1L);
    }

    @Test
    void createTag_ShouldGenerateUniqueSlug() {
        when(tagRepository.existsBySlug("java")).thenReturn(true);
        when(tagRepository.existsBySlug("java-1")).thenReturn(false);
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TagRequest request = new TagRequest();
        request.setName("Java");

        TagResponse response = categoryService.createTag(request);

        assertEquals("java-1", response.getSlug());
    }

    @Test
    void getTagBySlug_WhenMissing_ShouldThrowNotFound() {
        when(tagRepository.findBySlug("missing")).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> categoryService.getTagBySlug("missing"));
    }

    @Test
    void deleteTag_ShouldDeleteById() {
        categoryService.deleteTag(5L);
        verify(tagRepository).deleteById(5L);
    }

    @Test
    void addTagToPost_WhenAlreadyAssociated_ShouldThrowBadRequest() {
        Tag tag = Tag.builder().tagId(1L).postCount(0L).build();
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
        when(postTagRepository.findByPostIdAndTagId(1L, 1L)).thenReturn(Optional.of(new PostTag()));

        CustomException exception = assertThrows(CustomException.class, () -> categoryService.addTagToPost(1L, 1L));
        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void addTagToPost_ShouldIncrementCount() {
        Tag tag = Tag.builder().tagId(1L).postCount(0L).build();
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
        when(postTagRepository.findByPostIdAndTagId(1L, 1L)).thenReturn(Optional.empty());

        categoryService.addTagToPost(1L, 1L);

        assertEquals(1L, tag.getPostCount());
        verify(postTagRepository).save(any(PostTag.class));
        verify(tagRepository).save(tag);
    }

    @Test
    void removeTagFromPost_WhenMissingTag_ShouldThrowNotFound() {
        when(tagRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> categoryService.removeTagFromPost(1L, 1L));
    }

    @Test
    void removeTagFromPost_WhenAssociationMissing_ShouldThrowNotFound() {
        Tag tag = Tag.builder().tagId(1L).postCount(2L).build();
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
        when(postTagRepository.findByPostIdAndTagId(1L, 1L)).thenReturn(Optional.empty());

        assertThrows(CustomException.class, () -> categoryService.removeTagFromPost(1L, 1L));
    }

    @Test
    void removeTagFromPost_ShouldDecrementCount_WhenPositive() {
        Tag tag = Tag.builder().tagId(1L).postCount(2L).build();
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
        when(postTagRepository.findByPostIdAndTagId(1L, 1L)).thenReturn(Optional.of(PostTag.builder().postId(1L).tagId(1L).build()));

        categoryService.removeTagFromPost(1L, 1L);

        assertEquals(1L, tag.getPostCount());
        verify(postTagRepository).deleteByPostIdAndTagId(1L, 1L);
        verify(tagRepository).save(tag);
    }

    @Test
    void removeTagFromPost_ShouldNotSaveTag_WhenCountAlreadyZero() {
        Tag tag = Tag.builder().tagId(1L).postCount(0L).build();
        when(tagRepository.findById(1L)).thenReturn(Optional.of(tag));
        when(postTagRepository.findByPostIdAndTagId(1L, 1L)).thenReturn(Optional.of(PostTag.builder().postId(1L).tagId(1L).build()));

        categoryService.removeTagFromPost(1L, 1L);

        verify(tagRepository, never()).save(tag);
    }

    @Test
    void getTagsByPost_ShouldReturnMappedTags() {
        when(postTagRepository.findByPostId(1L)).thenReturn(List.of(
                PostTag.builder().postId(1L).tagId(10L).build(),
                PostTag.builder().postId(1L).tagId(11L).build()
        ));
        when(tagRepository.findAllById(List.of(10L, 11L))).thenReturn(List.of(
                Tag.builder().tagId(10L).name("Java").slug("java").build(),
                Tag.builder().tagId(11L).name("Spring").slug("spring").build()
        ));

        List<TagResponse> result = categoryService.getTagsByPost(1L);

        assertEquals(2, result.size());
    }

    @Test
    void getPostIdsByTag_ShouldReturnDistinctIds() {
        when(tagRepository.findById(1L)).thenReturn(Optional.of(new Tag()));
        when(postTagRepository.findByTagId(1L)).thenReturn(List.of(
                PostTag.builder().postId(10L).build(),
                PostTag.builder().postId(10L).build(),
                PostTag.builder().postId(11L).build()
        ));

        List<Long> ids = categoryService.getPostIdsByTag(1L);

        assertEquals(List.of(10L, 11L), ids);
    }

    @Test
    void getTrendingTags_ShouldMapResults() {
        when(tagRepository.findTopTags(any(Pageable.class))).thenReturn(List.of(
                Tag.builder().tagId(1L).name("Java").slug("java").build()
        ));
        assertEquals(1, categoryService.getTrendingTags().size());
    }

    @Test
    void searchTags_ShouldReturnMatches() {
        when(tagRepository.findByNameContainingIgnoreCase("ja")).thenReturn(List.of(
                Tag.builder().tagId(1L).name("Java").slug("java").build()
        ));
        assertEquals(1, categoryService.searchTags("ja").size());
    }

    @Test
    void incrementPostCount_ShouldUpdateCategory() {
        Category category = Category.builder().categoryId(1L).postCount(5L).build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        categoryService.incrementPostCount(1L);

        assertEquals(6L, category.getPostCount());
        verify(categoryRepository).save(category);
    }

    @Test
    void decrementPostCount_ShouldSave_WhenPositive() {
        Category category = Category.builder().categoryId(1L).postCount(2L).build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        categoryService.decrementPostCount(1L);

        assertEquals(1L, category.getPostCount());
        verify(categoryRepository).save(category);
    }

    @Test
    void decrementPostCount_ShouldNotSave_WhenZero() {
        Category category = Category.builder().categoryId(1L).postCount(0L).build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        categoryService.decrementPostCount(1L);

        assertEquals(0L, category.getPostCount());
        verify(categoryRepository, never()).save(eq(category));
        assertTrue(true);
    }
}
