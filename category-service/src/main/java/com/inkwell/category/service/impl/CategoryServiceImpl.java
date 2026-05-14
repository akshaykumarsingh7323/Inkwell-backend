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
import com.inkwell.category.service.CategoryService;
import com.inkwell.category.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final PostTagRepository postTagRepository;

    @Override
    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse createCategory(CategoryRequest request) {
        String slug = generateUniqueCategorySlug(request.getName());

        if (request.getParentCategoryId() != null) {
            categoryRepository.findById(request.getParentCategoryId())
                    .orElseThrow(() -> new CustomException("Parent category not found", HttpStatus.NOT_FOUND));
        }

        Category category = Category.builder()
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .parentCategoryId(request.getParentCategoryId())
                .build();

        return mapToCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @Cacheable(value = "categories", key = "#slug")
    public CategoryResponse getBySlug(String slug) {
        Category category = categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new CustomException("Category not found", HttpStatus.NOT_FOUND));
        return mapToCategoryResponse(category);
    }

    @Override
    @Cacheable(value = "categories", key = "'all'")
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::mapToCategoryResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Cacheable(value = "categories", key = "'tree'")
    public List<com.inkwell.category.dto.CategoryTreeResponse> getCategoryTree() {
        List<Category> allCategories = categoryRepository.findAll();
        
        // Group by parent ID
        java.util.Map<Long, List<Category>> childrenMap = allCategories.stream()
                .filter(c -> c.getParentCategoryId() != null)
                .collect(Collectors.groupingBy(Category::getParentCategoryId));
                
        // Find roots (parentCategoryId == null)
        return allCategories.stream()
                .filter(c -> c.getParentCategoryId() == null)
                .map(c -> buildCategoryTree(c, childrenMap))
                .collect(Collectors.toList());
    }

    private com.inkwell.category.dto.CategoryTreeResponse buildCategoryTree(Category category, java.util.Map<Long, List<Category>> childrenMap) {
        List<com.inkwell.category.dto.CategoryTreeResponse> children = childrenMap.getOrDefault(category.getCategoryId(), java.util.Collections.emptyList())
                .stream()
                .map(child -> buildCategoryTree(child, childrenMap))
                .collect(Collectors.toList());
                
        return com.inkwell.category.dto.CategoryTreeResponse.builder()
                .categoryId(category.getCategoryId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .postCount(category.getPostCount())
                .children(children)
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse updateCategory(Long categoryId, CategoryRequest request) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException("Category not found", HttpStatus.NOT_FOUND));

        if (!category.getName().equalsIgnoreCase(request.getName())) {
            category.setName(request.getName());
            category.setSlug(generateUniqueCategorySlug(request.getName()));
        }

        category.setDescription(request.getDescription());
        category.setParentCategoryId(request.getParentCategoryId());

        return mapToCategoryResponse(categoryRepository.save(category));
    }

    @Override
    @Transactional
    @CacheEvict(value = "categories", allEntries = true)
    public void deleteCategory(Long categoryId) {
        if (!categoryRepository.findByParentCategoryId(categoryId).isEmpty()) {
            throw new CustomException("Cannot delete category with children", HttpStatus.BAD_REQUEST);
        }
        categoryRepository.deleteById(categoryId);
    }

    @Override
    @Transactional
    @CacheEvict(value = "tags", allEntries = true)
    public TagResponse createTag(TagRequest request) {
        String slug = generateUniqueTagSlug(request.getName());

        Tag tag = Tag.builder()
                .name(request.getName())
                .slug(slug)
                .build();

        return mapToTagResponse(tagRepository.save(tag));
    }

    @Override
    @Cacheable(value = "tags", key = "#slug")
    public TagResponse getTagBySlug(String slug) {
        Tag tag = tagRepository.findBySlug(slug)
                .orElseThrow(() -> new CustomException("Tag not found", HttpStatus.NOT_FOUND));
        return mapToTagResponse(tag);
    }

    @Override
    @Cacheable(value = "tags", key = "'all'")
    public List<TagResponse> getAllTags() {
        return tagRepository.findAll().stream()
                .map(this::mapToTagResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "tags", allEntries = true)
    public void deleteTag(Long tagId) {
        tagRepository.deleteById(tagId);
    }

    @Override
    @Transactional
    public void addTagToPost(Long postId, Long tagId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new CustomException("Tag not found", HttpStatus.NOT_FOUND));

        if (postTagRepository.findByPostIdAndTagId(postId, tagId).isPresent()) {
            throw new CustomException("Tag already associated with this post", HttpStatus.BAD_REQUEST);
        }

        PostTag postTag = PostTag.builder()
                .postId(postId)
                .tagId(tagId)
                .build();

        postTagRepository.save(postTag);

        tag.setPostCount(tag.getPostCount() + 1);
        tagRepository.save(tag);
    }

    @Override
    @Transactional
    public void removeTagFromPost(Long postId, Long tagId) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new CustomException("Tag not found", HttpStatus.NOT_FOUND));

        postTagRepository.findByPostIdAndTagId(postId, tagId)
                .orElseThrow(() -> new CustomException("Tag not associated with this post", HttpStatus.NOT_FOUND));

        postTagRepository.deleteByPostIdAndTagId(postId, tagId);

        if (tag.getPostCount() > 0) {
            tag.setPostCount(tag.getPostCount() - 1);
            tagRepository.save(tag);
        }
    }

    @Override
    public List<TagResponse> getTagsByPost(Long postId) {
        List<Long> tagIds = postTagRepository.findByPostId(postId).stream()
                .map(PostTag::getTagId)
                .collect(Collectors.toList());

        return tagRepository.findAllById(tagIds).stream()
                .map(this::mapToTagResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<Long> getPostIdsByTag(Long tagId) {
        tagRepository.findById(tagId)
                .orElseThrow(() -> new CustomException("Tag not found", HttpStatus.NOT_FOUND));

        return postTagRepository.findByTagId(tagId).stream()
                .map(PostTag::getPostId)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public List<TagResponse> getTrendingTags() {
        return tagRepository.findTopTags(PageRequest.of(0, 10)).stream()
                .map(this::mapToTagResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<TagResponse> searchTags(String keyword) {
        return tagRepository.findByNameContainingIgnoreCase(keyword).stream()
                .map(this::mapToTagResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void incrementPostCount(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException("Category not found", HttpStatus.NOT_FOUND));
        category.setPostCount(category.getPostCount() + 1);
        categoryRepository.save(category);
    }

    @Override
    @Transactional
    public void decrementPostCount(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CustomException("Category not found", HttpStatus.NOT_FOUND));
        if (category.getPostCount() > 0) {
            category.setPostCount(category.getPostCount() - 1);
            categoryRepository.save(category);
        }
    }

    private String generateUniqueCategorySlug(String name) {
        String baseSlug = SlugUtil.toSlug(name);
        String slug = baseSlug;
        int count = 1;
        while (categoryRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + count++;
        }
        return slug;
    }

    private String generateUniqueTagSlug(String name) {
        String baseSlug = SlugUtil.toSlug(name);
        String slug = baseSlug;
        int count = 1;
        while (tagRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + count++;
        }
        return slug;
    }

    private CategoryResponse mapToCategoryResponse(Category category) {
        return CategoryResponse.builder()
                .categoryId(category.getCategoryId())
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .parentCategoryId(category.getParentCategoryId())
                .postCount(category.getPostCount())
                .createdAt(category.getCreatedAt())
                .build();
    }

    private TagResponse mapToTagResponse(Tag tag) {
        return TagResponse.builder()
                .tagId(tag.getTagId())
                .name(tag.getName())
                .slug(tag.getSlug())
                .postCount(tag.getPostCount())
                .createdAt(tag.getCreatedAt())
                .build();
    }
}
