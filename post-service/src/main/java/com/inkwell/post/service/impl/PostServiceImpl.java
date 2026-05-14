package com.inkwell.post.service.impl;

import com.inkwell.post.dto.PaginatedResponse;
import com.inkwell.post.dto.PostRequest;
import com.inkwell.post.dto.PostResponse;
import com.inkwell.post.entity.Post;
import com.inkwell.post.entity.PostLike;
import com.inkwell.post.enums.PostStatus;
import com.inkwell.post.exception.CustomException;
import com.inkwell.post.exception.AlreadyLikedException;
import com.inkwell.post.exception.PostNotFoundException;
import com.inkwell.post.exception.InvalidPostException;
import com.inkwell.post.repository.PostLikeRepository;
import com.inkwell.post.repository.PostRepository;
import com.inkwell.post.service.PostService;
import com.inkwell.post.util.CacheService;
import com.inkwell.post.util.HtmlSanitizer;
import com.inkwell.post.client.CategoryClient;
import com.inkwell.post.client.MediaClient;
import com.inkwell.post.client.NewsletterClient;
import com.inkwell.post.client.CommentClient;
import com.inkwell.post.dto.PostPublishedEvent;
import com.inkwell.post.dto.NotificationEvent;
import com.inkwell.post.client.PaymentClient;
import com.inkwell.post.exception.PremiumContentLockedException;
import com.inkwell.post.event.PostEventPublisher;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageImpl;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final CategoryClient categoryClient;
    private final NewsletterClient newsletterClient;
    private final MediaClient mediaClient;
    private final CacheService cacheService;
    private final PostEventPublisher postEventPublisher;
    private final CommentClient commentClient;
    private final PaymentClient paymentClient;

    @Override
    @Transactional
    @CacheEvict(value = "posts", allEntries = true)
    public PostResponse createPost(PostRequest request, Long authorId) {
        String slug = generateUniqueSlug(request.getTitle());
        String sanitizedContent = HtmlSanitizer.sanitize(request.getContent());
        int readTime = calculateReadTime(sanitizedContent);

        String finalFeaturedImageUrl = validateFeaturedImage(request, authorId, "AUTHOR");

        validatePremiumPrice(request.isPremium(), request.getPrice());

        Post post = Post.builder()
                .authorId(authorId)
                .title(request.getTitle())
                .slug(slug)
                .content(sanitizedContent)
                .excerpt(request.getExcerpt())
                .featuredImageUrl(finalFeaturedImageUrl)
                .categoryId(request.getCategoryId())
                .status(PostStatus.DRAFT)
                .readTimeMin(readTime)
                .isPremium(request.isPremium())
                .price(request.isPremium() ? request.getPrice() : 0.0)
                .build();

        Post savedPost = postRepository.save(post);
        
        if (savedPost.getCategoryId() != null) {
            try {
                categoryClient.incrementPostCount(savedPost.getCategoryId());
            } catch (Exception e) {
                log.error("Failed to increment category post count", e);
            }
        }

        syncTags(savedPost.getPostId(), request.getTagIds());
        return mapToPostResponse(savedPost);
    }

    @Override
    @Cacheable(value = "post", key = "#postId + '_' + (#requesterId ?: 'anonymous')")
    public PostResponse getPostById(Long postId, Long requesterId) {
        Post post = postRepository.findByPostId(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found with id: " + postId));
        return mapToPostResponse(post, requesterId);
    }

    @Override
    @Cacheable(value = "post", key = "#slug + '_' + (#requesterId ?: 'anonymous')")
    public PostResponse getPostBySlug(String slug, Long requesterId) {
        Post post = postRepository.findBySlugAndStatus(slug, PostStatus.PUBLISHED)
                .orElseThrow(() -> new PostNotFoundException("Post not found with slug: " + slug));
        return mapToPostResponse(post, requesterId);
    }

    @Override
    public PaginatedResponse<PostResponse> getPostsByAuthor(Long authorId, Pageable pageable) {
        log.info("Fetching posts for authorId: {} with pageable: {}", authorId, pageable);
        try {
            Page<Post> posts = postRepository.findByAuthorIdAndStatusNot(authorId, PostStatus.ARCHIVED, pageable);
            log.info("Found {} posts for authorId: {}", posts.getTotalElements(), authorId);
            return PaginatedResponse.fromPage(posts.map(post -> {
                try {
                    return this.mapToPostResponse(post);
                } catch (Exception e) {
                    log.error("Error mapping post ID {} to response", post.getPostId(), e);
                    throw e;
                }
            }));
        } catch (Exception e) {
            log.error("Error fetching posts for authorId: {}", authorId, e);
            throw e;
        }
    }

    @Override
    public PaginatedResponse<PostResponse> getPublishedPostsByAuthor(Long authorId, Pageable pageable) {
        return PaginatedResponse.fromPage(postRepository.findByAuthorIdAndStatus(authorId, PostStatus.PUBLISHED, pageable)
                .map(this::mapToPostResponse));
    }

    @Override
    @Cacheable(value = "posts", key = "'published_' + #pageable.pageNumber + '_' + #pageable.pageSize + '_' + #pageable.sort + '_' + (#requesterId ?: 'anonymous')")
    public PaginatedResponse<PostResponse> getPublishedPosts(Pageable pageable, Long requesterId) {
        return PaginatedResponse.fromPage(postRepository.findByStatus(PostStatus.PUBLISHED, pageable)
                .map(post -> this.mapToPostResponse(post, requesterId)));
    }

    @Override
    public PaginatedResponse<PostResponse> getPublishedPostsByCategory(Long categoryId, Pageable pageable, Long requesterId) {
        return PaginatedResponse.fromPage(postRepository.findByCategoryIdAndStatus(categoryId, PostStatus.PUBLISHED, pageable)
                .map(post -> this.mapToPostResponse(post, requesterId)));
    }

    @Override
    public PaginatedResponse<PostResponse> getPublishedPostsByTag(Long tagId, Pageable pageable, Long requesterId) {
        List<Long> postIds = categoryClient.getPostIdsByTag(tagId);
        if (postIds.isEmpty()) {
            return PaginatedResponse.fromPage(new PageImpl<>(Collections.emptyList(), pageable, 0));
        }

        return PaginatedResponse.fromPage(postRepository.findByPostIdInAndStatus(postIds, PostStatus.PUBLISHED, pageable)
                .map(post -> this.mapToPostResponse(post, requesterId)));
    }

    @Override
    public PaginatedResponse<PostResponse> searchPosts(String keyword, Pageable pageable, Long requesterId) {
        log.info("Searching posts with keyword: {}", keyword);
        
        List<Long> tagPostIds = Collections.emptyList();
        try {
            List<CategoryClient.TagSummary> matchingTags = categoryClient.searchTags(keyword);
            if (!matchingTags.isEmpty()) {
                tagPostIds = matchingTags.stream()
                        .flatMap(tag -> categoryClient.getPostIdsByTag(tag.getTagId()).stream())
                        .distinct()
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Failed to fetch tags for search keyword: {}", keyword, e);
        }

        Page<Post> resultPage;
        if (tagPostIds.isEmpty()) {
            resultPage = postRepository.searchPosts(keyword, PostStatus.PUBLISHED, pageable);
        } else {
            resultPage = postRepository.searchPostsWithTags(keyword, tagPostIds, PostStatus.PUBLISHED, pageable);
        }
        
        return PaginatedResponse.fromPage(resultPage.map(post -> this.mapToPostResponse(post, requesterId)));
    }

    @Override
    @Transactional
    @CacheEvict(value = {"posts", "post"}, allEntries = true)
    public PostResponse updatePost(Long postId, PostRequest request, Long requesterId, String requesterRole) {
        Post post = postRepository.findByPostId(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found with id: " + postId));

        if (!"ADMIN".equalsIgnoreCase(requesterRole)) {
            ensureOwner(post, requesterId);
        }

        Long previousCategoryId = post.getCategoryId();

        if (!post.getTitle().equals(request.getTitle())) {
            post.setTitle(request.getTitle());
            post.setSlug(generateUniqueSlug(request.getTitle()));
        }

        String sanitizedContent = HtmlSanitizer.sanitize(request.getContent());
        post.setContent(sanitizedContent);
        post.setExcerpt(request.getExcerpt());
        
        String finalFeaturedImageUrl = validateFeaturedImage(request, post.getAuthorId(), requesterRole);
        post.setFeaturedImageUrl(finalFeaturedImageUrl);
        post.setReadTimeMin(calculateReadTime(sanitizedContent));
        validatePremiumPrice(request.isPremium(), request.getPrice());
        post.setCategoryId(request.getCategoryId());
        post.setPremium(request.isPremium());
        post.setPrice(request.isPremium() ? request.getPrice() : 0.0);

        Post updatedPost = postRepository.save(post);
        syncCategoryCounts(previousCategoryId, updatedPost.getCategoryId());
        syncTags(updatedPost.getPostId(), request.getTagIds());
        return mapToPostResponse(updatedPost);
    }

    @Override
    @Transactional
    @CacheEvict(value = "posts", allEntries = true)
    public PostResponse publishPost(Long postId, Long requesterId, String requesterRole) {
        Post post = postRepository.findByPostId(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found with id: " + postId));

        if (!"ADMIN".equalsIgnoreCase(requesterRole)) {
            ensureOwner(post, requesterId);
        }

        if (post.getStatus() == PostStatus.PUBLISHED) {
            return mapToPostResponse(post);
        }

        post.setStatus(PostStatus.PUBLISHED);
        post.setPublishedAt(LocalDateTime.now());

        Post updatedPost = postRepository.save(post);
        log.info("Post saved as PUBLISHED. Preparing to broadcast event for postId: {}", updatedPost.getPostId());
        
        try {
            postEventPublisher.publishPostPublishedEvent(PostPublishedEvent.builder()
                    .postId(updatedPost.getPostId())
                    .authorId(updatedPost.getAuthorId())
                    .title(updatedPost.getTitle())
                    .slug(updatedPost.getSlug())
                    .build());
        } catch (Exception e) {
            log.error("Failed to publish post event", e);
        }
        
        return mapToPostResponse(updatedPost);
    }

    @Override
    @Transactional
    @CacheEvict(value = "posts", allEntries = true)
    public PostResponse unpublishPost(Long postId, Long requesterId, String requesterRole) {
        Post post = postRepository.findByPostId(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found with id: " + postId));

        if (!"ADMIN".equalsIgnoreCase(requesterRole)) {
            ensureOwner(post, requesterId);
        }

        post.setStatus(PostStatus.UNPUBLISHED);
        Post updatedPost = postRepository.save(post);
        return mapToPostResponse(updatedPost);
    }

    @Override
    @Transactional
    @CacheEvict(value = "posts", allEntries = true)
    public void deletePost(Long postId, Long requesterId, String requesterRole) {
        Post post = postRepository.findByPostId(postId)
                .orElseThrow(() -> new PostNotFoundException("Post not found with id: " + postId));
        
        if (!"ADMIN".equalsIgnoreCase(requesterRole)) {
            ensureOwner(post, requesterId);
        }
        
        post.setStatus(PostStatus.ARCHIVED);
        postRepository.save(post);

        if (post.getCategoryId() != null) {
            try {
                categoryClient.decrementPostCount(post.getCategoryId());
            } catch (Exception e) {
                log.error("Failed to decrement category post count", e);
            }
        }
    }

    @Override
    @Transactional
    public void incrementViews(Long postId, String sessionId) {
        String cacheKey = "post:view:" + postId + ":" + sessionId;
        
        if (!cacheService.hasKey(cacheKey)) {
            Post post = postRepository.findByPostId(postId)
                    .orElseThrow(() -> new PostNotFoundException("Post not found with id: " + postId));
            
            // Total views
            post.setViewCount(post.getViewCount() + 1);
            
            // Daily views logic
            LocalDate today = LocalDate.now();
            if (post.getLastViewDate() == null || !post.getLastViewDate().isEqual(today)) {
                post.setDailyViewCount(1L);
                post.setLastViewDate(today);
            } else {
                post.setDailyViewCount(post.getDailyViewCount() + 1);
            }
            
            postRepository.save(post);
            cacheService.put(cacheKey, true, 30, java.util.concurrent.TimeUnit.MINUTES);
            log.info("Incremented view count (Daily: {}) for post: {} by session: {}", post.getDailyViewCount(), postId, sessionId);
        }
    }

    @Override
    @Transactional
    public void likePost(Long postId, Long userId) {
        log.info("User: {} liking post: {}", userId, postId);
        if (!postRepository.existsById(postId)) {
            throw new PostNotFoundException("Post not found with id: " + postId);
        }
        
        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new AlreadyLikedException("You have already liked this post");
        }

        PostLike like = PostLike.builder()
                .postId(postId)
                .userId(userId)
                .build();
        
        postLikeRepository.save(like);
        postRepository.incrementLikesCount(postId);

        // Send notification to author
        try {
            postRepository.findByPostId(postId).ifPresent(post -> {
                if (!post.getAuthorId().equals(userId)) {
                    postEventPublisher.publishNotificationEvent(NotificationEvent.builder()
                            .userId(post.getAuthorId())
                            .type("LIKE")
                            .message("Someone liked your post: " + post.getTitle())
                            .metadata(Map.of("postId", post.getPostId().toString(), "actorId", userId.toString()))
                            .build());
                }
            });
        } catch (Exception e) {
            log.error("Failed to publish like notification for post {}. Service will continue.", postId, e);
        }
    }

    @Override
    @Transactional
    public void unlikePost(Long postId, Long userId) {
        log.info("User: {} unliking post: {}", userId, postId);
        if (!postRepository.existsById(postId)) {
            throw new PostNotFoundException("Post not found with id: " + postId);
        }

        if (postLikeRepository.existsByPostIdAndUserId(postId, userId)) {
            postLikeRepository.deleteByPostIdAndUserId(postId, userId);
            postRepository.decrementLikesCount(postId);
        }
    }

    private void ensureOwner(Post post, Long requesterId) {
        if (!post.getAuthorId().equals(requesterId)) {
            throw new CustomException("You can only manage your own posts", HttpStatus.FORBIDDEN);
        }
    }

    @Override
    public Long getPostCount(Long authorId, Long requesterId, String requesterRole) {
        return postRepository.countByAuthorId(authorId);
    }

    @Override
    public List<PostResponse> getTrendingPosts(Long requesterId) {
        return postRepository.findTop5ByStatusOrderByDailyViewCountDesc(PostStatus.PUBLISHED)
                .stream()
                .map(post -> this.mapToPostResponse(post, requesterId))
                .collect(Collectors.toList());
    }

    @Override
    public PaginatedResponse<PostResponse> explorePosts(String sort, Long categoryId, Long tagId, String keyword, Pageable pageable, Long requesterId) {
        log.info("Exploring posts with sort: {}, categoryId: {}, tagId: {}, keyword: {}", sort, categoryId, tagId, keyword);
        
        Page<Post> resultPage;
        
        // 1. Handle Keyword Search + Tags (if keyword provided)
        if (keyword != null && !keyword.isBlank()) {
            // For now, reuse existing search logic but we could enhance it with sorting later
            // Existing search returns paginated results
            return this.searchPosts(keyword, pageable, requesterId);
        }

        // 2. Handle Tag Filtering
        if (tagId != null) {
            return this.getPublishedPostsByTag(tagId, pageable, requesterId);
        }

        // 3. Handle Category Filtering
        if (categoryId != null) {
            // If category is provided, we use the specific category query
            resultPage = postRepository.findByCategoryIdAndStatusOrderByPublishedAtDesc(categoryId, PostStatus.PUBLISHED, pageable);
        } else {
            // 4. Handle Sorting Options (Global Discovery)
            switch (sort.toLowerCase()) {
                case "views":
                    resultPage = postRepository.findAllPublishedOrderByViewCountDesc(PostStatus.PUBLISHED, pageable);
                    break;
                case "likes":
                    resultPage = postRepository.findAllPublishedOrderByLikesCountDesc(PostStatus.PUBLISHED, pageable);
                    break;
                case "trending":
                    resultPage = postRepository.findAllPublishedTrending(PostStatus.PUBLISHED, pageable);
                    break;
                case "latest":
                default:
                    resultPage = postRepository.findByStatus(PostStatus.PUBLISHED, pageable);
                    break;
            }
        }

        return PaginatedResponse.fromPage(resultPage.map(post -> this.mapToPostResponse(post, requesterId)));
    }

    private String generateUniqueSlug(String title) {
        String baseSlug = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", "-");
        
        String slug = baseSlug;
        int count = 1;
        while (postRepository.existsBySlug(slug)) {
            slug = baseSlug + "-" + count++;
        }
        return slug;
    }

    private int calculateReadTime(String content) {
        if (content == null || content.isBlank()) return 0;
        String[] words = content.trim().split("\\s+");
        return (int) Math.ceil(words.length / 200.0);
    }

    private void syncCategoryCounts(Long previousCategoryId, Long nextCategoryId) {
        if (Objects.equals(previousCategoryId, nextCategoryId)) {
            return;
        }

        if (previousCategoryId != null) {
            try {
                categoryClient.decrementPostCount(previousCategoryId);
            } catch (Exception e) {
                log.error("Failed to decrement previous category post count", e);
            }
        }

        if (nextCategoryId != null) {
            try {
                categoryClient.incrementPostCount(nextCategoryId);
            } catch (Exception e) {
                log.error("Failed to increment new category post count", e);
            }
        }
    }

    private void syncTags(Long postId, List<Long> requestedTagIds) {
        Set<Long> requested = requestedTagIds == null
                ? Collections.emptySet()
                : requestedTagIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());

        Set<Long> existing = getTagIds(postId).stream().collect(Collectors.toSet());

        for (Long tagId : requested) {
            if (!existing.contains(tagId)) {
                try {
                    categoryClient.addTagToPost(tagId, postId);
                } catch (Exception e) {
                    log.warn("Failed to add tag {} to post {} (category-service may be down)", tagId, postId, e);
                }
            }
        }

        for (Long tagId : existing) {
            if (!requested.contains(tagId)) {
                try {
                    categoryClient.removeTagFromPost(tagId, postId);
                } catch (Exception e) {
                    log.warn("Failed to remove tag {} from post {} (category-service may be down)", tagId, postId, e);
                }
            }
        }
    }

    private List<Long> getTagIds(Long postId) {
        try {
            return categoryClient.getTagsByPost(postId).stream()
                    .map(CategoryClient.TagSummary::getTagId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Failed to fetch tags for post {}", postId, e);
            return Collections.emptyList();
        }
    }

    private String validateFeaturedImage(PostRequest request, Long authorId, String requesterRole) {
        if (request.getFeaturedImageMediaId() != null) {
            try {
                Map<String, Object> mediaData = mediaClient.getMedia(
                    request.getFeaturedImageMediaId(),
                    String.valueOf(authorId),
                    requesterRole
                );
                
                if (mediaData != null && mediaData.containsKey("url")) {
                    return (String) mediaData.get("url");
                }
            } catch (Exception e) {
                log.error("Featured image validation failed for mediaId {}", request.getFeaturedImageMediaId(), e);
                throw new CustomException("Invalid or inaccessible featured image media", HttpStatus.BAD_REQUEST);
            }
        }
        return request.getFeaturedImageUrl();
    }

    private void validatePremiumPrice(boolean isPremium, double price) {
        if (isPremium && price <= 0) {
            throw new InvalidPostException("Premium posts must have a price greater than 0");
        }
    }

    private PostResponse mapToPostResponse(Post post) {
        return mapToPostResponse(post, null);
    }

    private PostResponse mapToPostResponse(Post post, Long requesterId) {
        boolean isLikedByCurrentUser = false;
        if (requesterId != null) {
            isLikedByCurrentUser = postLikeRepository.existsByPostIdAndUserId(post.getPostId(), requesterId);
        }

        boolean accessUnlocked = true;
        String displayContent = post.getContent();

        if (post.isPremium()) {
            // Check if requester is NOT the author
            if (requesterId == null || !post.getAuthorId().equals(requesterId)) {
                // Check payment status
                try {
                    accessUnlocked = requesterId != null && paymentClient.hasAccess(String.valueOf(requesterId), String.valueOf(post.getPostId()));
                } catch (Exception e) {
                    log.warn("Payment-service check failed for post {}", post.getPostId(), e);
                    accessUnlocked = false;
                }

                if (!accessUnlocked) {
                    // Lock content: only show excerpt or first 200 chars obfuscated
                    String preview = post.getExcerpt() != null ? post.getExcerpt() : (displayContent.length() > 200 ? displayContent.substring(0, 200) : displayContent);
                    displayContent = "{\"locked\": true, \"message\": \"This is a premium post. Please purchase to unlock.\", \"preview\": \"" + preview + "\"}";
                }
            }
        }

        // Get comments count (use DB field with fallback to Feign client)
        Long commentsCount = (post.getCommentsCount() != null && post.getCommentsCount() > 0) ? post.getCommentsCount() : 0L;
        if (commentsCount == 0L) {
            try {
                commentsCount = commentClient.getCommentCountByPost(post.getPostId());
            } catch (Exception e) {
                log.warn("Failed to fetch comment count for post {}", post.getPostId(), e);
            }
        }

        return PostResponse.builder()
                .postId(post.getPostId())
                .authorId(post.getAuthorId())
                .categoryId(post.getCategoryId())
                .title(post.getTitle())
                .slug(post.getSlug())
                .content(displayContent)
                .excerpt(post.getExcerpt())
                .featuredImageUrl(post.getFeaturedImageUrl())
                .status(post.getStatus())
                .readTimeMin(post.getReadTimeMin())
                .viewCount(post.getViewCount())
                .likesCount(post.getLikesCount())
                .commentsCount(commentsCount)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .publishedAt(post.getPublishedAt())
                .isPremium(post.isPremium())
                .price(post.getPrice())
                .accessUnlocked(accessUnlocked)
                .tagIds(getTagIds(post.getPostId()))
                .isLikedByCurrentUser(isLikedByCurrentUser)
                .build();
    }
}
