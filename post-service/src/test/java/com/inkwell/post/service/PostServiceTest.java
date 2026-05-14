package com.inkwell.post.service;

import com.inkwell.post.client.CategoryClient;
import com.inkwell.post.client.CommentClient;
import com.inkwell.post.client.MediaClient;
import com.inkwell.post.client.NewsletterClient;
import com.inkwell.post.client.PaymentClient;
import com.inkwell.post.dto.NotificationEvent;
import com.inkwell.post.dto.PaginatedResponse;
import com.inkwell.post.dto.PostPublishedEvent;
import com.inkwell.post.dto.PostRequest;
import com.inkwell.post.dto.PostResponse;
import com.inkwell.post.entity.Post;
import com.inkwell.post.entity.PostLike;
import com.inkwell.post.enums.PostStatus;
import com.inkwell.post.event.PostEventPublisher;
import com.inkwell.post.exception.AlreadyLikedException;
import com.inkwell.post.exception.CustomException;
import com.inkwell.post.exception.InvalidPostException;
import com.inkwell.post.exception.PostNotFoundException;
import com.inkwell.post.repository.PostLikeRepository;
import com.inkwell.post.repository.PostRepository;
import com.inkwell.post.service.impl.PostServiceImpl;
import com.inkwell.post.util.CacheService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private CategoryClient categoryClient;

    @Mock
    private NewsletterClient newsletterClient;

    @Mock
    private MediaClient mediaClient;

    @Mock
    private CacheService cacheService;

    @Mock
    private PostEventPublisher postEventPublisher;

    @Mock
    private CommentClient commentClient;

    @Mock
    private PaymentClient paymentClient;

    @InjectMocks
    private PostServiceImpl postService;

    private Pageable pageable;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);
    }

    @Test
    void createPostShouldPersistSanitizedDraftAndSyncDependencies() {
        PostRequest request = baseRequest();
        request.setFeaturedImageMediaId(55L);
        when(mediaClient.getMedia(55L, "10", "AUTHOR")).thenReturn(Map.of("url", "https://cdn/img.jpg"));
        when(postRepository.existsBySlug("hello-scriptalert1script-world")).thenReturn(false);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setPostId(99L);
            return post;
        });
        when(categoryClient.getTagsByPost(99L)).thenReturn(List.of());
        when(commentClient.getCommentCountByPost(99L)).thenReturn(4L);

        PostResponse response = postService.createPost(request, 10L);

        ArgumentCaptor<Post> postCaptor = ArgumentCaptor.forClass(Post.class);
        verify(postRepository).save(postCaptor.capture());
        Post savedPost = postCaptor.getValue();
        assertEquals(10L, savedPost.getAuthorId());
        assertEquals("hello-scriptalert1script-world", savedPost.getSlug());
        assertEquals("<p>Hello World</p>", savedPost.getContent());
        assertEquals(PostStatus.DRAFT, savedPost.getStatus());
        assertEquals(1, savedPost.getReadTimeMin());
        assertTrue(savedPost.isPremium());
        assertEquals(19.0, savedPost.getPrice());
        assertEquals("https://cdn/img.jpg", savedPost.getFeaturedImageUrl());
        verify(categoryClient).incrementPostCount(7L);
        verify(categoryClient).addTagToPost(11L, 99L);
        verify(categoryClient).addTagToPost(22L, 99L);
        assertEquals(99L, response.getPostId());
        assertFalse(response.isAccessUnlocked());
        assertTrue(response.getContent().contains("\"locked\": true"));
        assertEquals(4L, response.getCommentsCount());
    }

    @Test
    void createPostShouldThrowWhenPremiumPriceIsInvalid() {
        PostRequest request = baseRequest();
        request.setPrice(0.0);

        InvalidPostException exception = assertThrows(InvalidPostException.class,
                () -> postService.createPost(request, 10L));

        assertEquals("Premium posts must have a price greater than 0", exception.getMessage());
        verify(postRepository, never()).save(any());
    }

    @Test
    void createPostShouldThrowBadRequestWhenFeaturedImageMediaIsInvalid() {
        PostRequest request = baseRequest();
        request.setFeaturedImageMediaId(55L);
        when(mediaClient.getMedia(55L, "10", "AUTHOR")).thenThrow(new RuntimeException("downstream failure"));

        CustomException exception = assertThrows(CustomException.class,
                () -> postService.createPost(request, 10L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        assertEquals("Invalid or inaccessible featured image media", exception.getMessage());
    }

    @Test
    void getPostByIdShouldLockPremiumContentWhenRequesterHasNoAccess() {
        Post post = premiumPost();
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));
        when(postLikeRepository.existsByPostIdAndUserId(5L, 99L)).thenReturn(true);
        when(paymentClient.hasAccess("99", "5")).thenReturn(false);
        when(commentClient.getCommentCountByPost(5L)).thenReturn(3L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of(tag(10L)));

        PostResponse response = postService.getPostById(5L, 99L);

        assertFalse(response.isAccessUnlocked());
        assertTrue(response.getContent().contains("\"locked\": true"));
        assertTrue(response.isLikedByCurrentUser());
        assertEquals(List.of(10L), response.getTagIds());
        assertEquals(3L, response.getCommentsCount());
    }

    @Test
    void getPostByIdShouldReturnPremiumContentToAuthorWithoutPaymentCheck() {
        Post post = premiumPost();
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));
        when(postLikeRepository.existsByPostIdAndUserId(5L, 10L)).thenReturn(false);
        when(commentClient.getCommentCountByPost(5L)).thenReturn(0L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of());

        PostResponse response = postService.getPostById(5L, 10L);

        assertTrue(response.isAccessUnlocked());
        assertEquals("Premium full content body", response.getContent());
        verify(paymentClient, never()).hasAccess(anyString(), anyString());
    }

    @Test
    void getPostBySlugShouldThrowWhenPostDoesNotExist() {
        when(postRepository.findBySlugAndStatus("missing", PostStatus.PUBLISHED)).thenReturn(Optional.empty());

        assertThrows(PostNotFoundException.class, () -> postService.getPostBySlug("missing", null));
    }

    @Test
    void getPostBySlugShouldReturnMappedResponse() {
        Post post = publishedPost();
        when(postRepository.findBySlugAndStatus("published-title", PostStatus.PUBLISHED)).thenReturn(Optional.of(post));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(7L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of(tag(4L)));

        PostResponse response = postService.getPostBySlug("published-title", null);

        assertEquals("published-title", response.getSlug());
        assertEquals(7L, response.getCommentsCount());
        assertEquals(List.of(4L), response.getTagIds());
    }

    @Test
    void getPostsByAuthorShouldMapPageResults() {
        Post post = draftPost();
        when(postRepository.findByAuthorIdAndStatusNot(10L, PostStatus.ARCHIVED, pageable)).thenReturn(pageOf(post));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(2L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of(tag(8L)));

        PaginatedResponse<PostResponse> response = postService.getPostsByAuthor(10L, pageable);

        assertEquals(1, response.getContent().size());
        assertEquals(2L, response.getContent().get(0).getCommentsCount());
        assertEquals(List.of(8L), response.getContent().get(0).getTagIds());
    }

    @Test
    void getPublishedPostsByAuthorShouldMapPageResults() {
        Post post = publishedPost();
        when(postRepository.findByAuthorIdAndStatus(10L, PostStatus.PUBLISHED, pageable)).thenReturn(pageOf(post));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(3L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of());

        PaginatedResponse<PostResponse> response = postService.getPublishedPostsByAuthor(10L, pageable);

        assertEquals(1, response.getContent().size());
        assertEquals(PostStatus.PUBLISHED, response.getContent().get(0).getStatus());
    }

    @Test
    void getPublishedPostsShouldMapPageResultsForRequester() {
        Post post = publishedPost();
        when(postRepository.findByStatus(PostStatus.PUBLISHED, pageable)).thenReturn(pageOf(post));
        when(postLikeRepository.existsByPostIdAndUserId(5L, 99L)).thenReturn(true);
        when(commentClient.getCommentCountByPost(5L)).thenReturn(1L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of());

        PaginatedResponse<PostResponse> response = postService.getPublishedPosts(pageable, 99L);

        assertEquals(1, response.getContent().size());
        assertTrue(response.getContent().get(0).isLikedByCurrentUser());
    }

    @Test
    void getPublishedPostsByCategoryShouldMapPageResults() {
        Post post = publishedPost();
        when(postRepository.findByCategoryIdAndStatus(7L, PostStatus.PUBLISHED, pageable)).thenReturn(pageOf(post));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(5L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of(tag(2L)));

        PaginatedResponse<PostResponse> response = postService.getPublishedPostsByCategory(7L, pageable, null);

        assertEquals(1, response.getContent().size());
        assertEquals(5L, response.getContent().get(0).getCommentsCount());
    }

    @Test
    void getPublishedPostsByTagShouldReturnEmptyPageWhenTagHasNoPosts() {
        when(categoryClient.getPostIdsByTag(33L)).thenReturn(List.of());

        PaginatedResponse<PostResponse> response = postService.getPublishedPostsByTag(33L, pageable, null);

        assertTrue(response.getContent().isEmpty());
        assertEquals(0L, response.getTotalElements());
        verify(postRepository, never()).findByPostIdInAndStatus(any(), any(), any());
    }

    @Test
    void getPublishedPostsByTagShouldMapRepositoryResultsWhenTagHasPosts() {
        Post post = publishedPost();
        when(categoryClient.getPostIdsByTag(33L)).thenReturn(List.of(5L, 6L));
        when(postRepository.findByPostIdInAndStatus(List.of(5L, 6L), PostStatus.PUBLISHED, pageable))
                .thenReturn(pageOf(post));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(9L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of(tag(33L)));

        PaginatedResponse<PostResponse> response = postService.getPublishedPostsByTag(33L, pageable, null);

        assertEquals(1, response.getContent().size());
        assertEquals(9L, response.getContent().get(0).getCommentsCount());
    }

    @Test
    void searchPostsShouldUseTagAwareQueryWhenMatchingTagsExist() {
        CategoryClient.TagSummary tag = tag(88L);
        Post post = publishedPost();
        when(categoryClient.searchTags("spring")).thenReturn(List.of(tag));
        when(categoryClient.getPostIdsByTag(88L)).thenReturn(List.of(5L, 9L));
        when(postRepository.searchPostsWithTags("spring", List.of(5L, 9L), PostStatus.PUBLISHED, pageable))
                .thenReturn(pageOf(post));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(2L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of(tag));

        PaginatedResponse<PostResponse> response = postService.searchPosts("spring", pageable, null);

        assertEquals(1, response.getContent().size());
        verify(postRepository).searchPostsWithTags("spring", List.of(5L, 9L), PostStatus.PUBLISHED, pageable);
        verify(postRepository, never()).searchPosts(eq("spring"), any(), any());
    }

    @Test
    void searchPostsShouldFallbackToKeywordOnlyQueryWhenNoTagsMatch() {
        Post post = publishedPost();
        when(categoryClient.searchTags("kotlin")).thenReturn(List.of());
        when(postRepository.searchPosts("kotlin", PostStatus.PUBLISHED, pageable)).thenReturn(pageOf(post));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(0L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of());

        PaginatedResponse<PostResponse> response = postService.searchPosts("kotlin", pageable, null);

        assertEquals(1, response.getContent().size());
        verify(postRepository).searchPosts("kotlin", PostStatus.PUBLISHED, pageable);
        verify(postRepository, never()).searchPostsWithTags(anyString(), any(), any(), any());
    }

    @Test
    void searchPostsShouldFallbackToKeywordOnlyQueryWhenTagLookupFails() {
        Post post = publishedPost();
        when(categoryClient.searchTags("java")).thenThrow(new RuntimeException("category down"));
        when(postRepository.searchPosts("java", PostStatus.PUBLISHED, pageable)).thenReturn(pageOf(post));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(1L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of());

        PaginatedResponse<PostResponse> response = postService.searchPosts("java", pageable, null);

        assertEquals(1, response.getContent().size());
        verify(postRepository).searchPosts("java", PostStatus.PUBLISHED, pageable);
        verify(postRepository, never()).searchPostsWithTags(anyString(), any(), any(), any());
    }

    @Test
    void updatePostShouldRejectNonOwnerWhenRequesterIsNotAdmin() {
        Post post = draftPost();
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));

        CustomException exception = assertThrows(CustomException.class,
                () -> postService.updatePost(5L, baseRequest(), 99L, "AUTHOR"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(postRepository, never()).save(any());
    }

    @Test
    void updatePostShouldUpdateSlugCategoryTagsAndPremiumFields() {
        Post existing = draftPost();
        existing.setCategoryId(7L);
        existing.setTitle("Old Title");
        existing.setSlug("old-title");
        PostRequest request = baseRequest();
        request.setTitle("Updated Title");
        request.setCategoryId(9L);
        request.setTagIds(List.of(22L, 33L));
        request.setFeaturedImageMediaId(99L);
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(existing));
        when(postRepository.existsBySlug("updated-title")).thenReturn(false);
        when(mediaClient.getMedia(99L, "10", "AUTHOR")).thenReturn(Map.of("url", "https://cdn/updated.jpg"));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of(tag(11L), tag(22L)));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(8L);

        PostResponse response = postService.updatePost(5L, request, 10L, "AUTHOR");

        assertEquals("Updated Title", existing.getTitle());
        assertEquals("updated-title", existing.getSlug());
        assertEquals(9L, existing.getCategoryId());
        assertEquals("https://cdn/updated.jpg", existing.getFeaturedImageUrl());
        assertTrue(existing.isPremium());
        assertEquals(19.0, existing.getPrice());
        verify(categoryClient).decrementPostCount(7L);
        verify(categoryClient).incrementPostCount(9L);
        verify(categoryClient).removeTagFromPost(11L, 5L);
        verify(categoryClient).addTagToPost(33L, 5L);
        assertEquals(8L, response.getCommentsCount());
    }

    @Test
    void updatePostShouldAllowAdminAndKeepSlugWhenTitleDoesNotChange() {
        Post existing = draftPost();
        existing.setTitle("Stable Title");
        existing.setSlug("stable-title");
        existing.setCategoryId(7L);
        PostRequest request = PostRequest.builder()
                .title("Stable Title")
                .content("Updated body content for the same title")
                .excerpt("Updated excerpt")
                .featuredImageUrl("https://cdn/direct.jpg")
                .categoryId(7L)
                .tagIds(null)
                .isPremium(false)
                .price(0.0)
                .build();
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(existing));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of(tag(11L)));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(0L);

        PostResponse response = postService.updatePost(5L, request, 999L, "ADMIN");

        assertEquals("stable-title", existing.getSlug());
        assertFalse(existing.isPremium());
        assertEquals(0.0, existing.getPrice());
        verify(postRepository, never()).existsBySlug(anyString());
        verify(categoryClient).removeTagFromPost(11L, 5L);
        assertEquals("https://cdn/direct.jpg", response.getFeaturedImageUrl());
    }

    @Test
    void updatePostShouldSkipCategoryCountSyncWhenCategoryDoesNotChange() {
        Post existing = draftPost();
        existing.setCategoryId(7L);
        PostRequest request = baseRequest();
        request.setCategoryId(7L);
        request.setTagIds(List.of(11L, 22L));

        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(existing));
        when(postRepository.existsBySlug("hello-scriptalert1script-world")).thenReturn(false);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of(tag(11L), tag(22L)));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(0L);

        postService.updatePost(5L, request, 10L, "AUTHOR");

        verify(categoryClient, never()).incrementPostCount(7L);
        verify(categoryClient, never()).decrementPostCount(7L);
    }

    @Test
    void publishPostShouldReturnImmediatelyWhenAlreadyPublished() {
        Post post = publishedPost();
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(0L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of());

        PostResponse response = postService.publishPost(5L, 10L, "AUTHOR");

        assertEquals(PostStatus.PUBLISHED, response.getStatus());
        verify(postRepository, never()).save(any());
        verify(postEventPublisher, never()).publishPostPublishedEvent(any());
    }

    @Test
    void publishPostShouldPersistStatusAndContinueWhenEventPublishingFails() {
        Post post = draftPost();
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new RuntimeException("rabbit down")).when(postEventPublisher)
                .publishPostPublishedEvent(any(PostPublishedEvent.class));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(0L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of());

        PostResponse response = postService.publishPost(5L, 10L, "AUTHOR");

        assertEquals(PostStatus.PUBLISHED, response.getStatus());
        assertNotNull(post.getPublishedAt());
        verify(postRepository).save(post);
        verify(postEventPublisher).publishPostPublishedEvent(any(PostPublishedEvent.class));
    }

    @Test
    void unpublishPostShouldPersistUnpublishedStatus() {
        Post post = publishedPost();
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(0L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of());

        PostResponse response = postService.unpublishPost(5L, 10L, "AUTHOR");

        assertEquals(PostStatus.UNPUBLISHED, response.getStatus());
        verify(postRepository).save(post);
    }

    @Test
    void publishPostShouldAllowAdminWithoutOwnershipCheck() {
        Post post = draftPost();
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(0L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of());

        PostResponse response = postService.publishPost(5L, 999L, "ADMIN");

        assertEquals(PostStatus.PUBLISHED, response.getStatus());
    }

    @Test
    void unpublishPostShouldAllowAdminWithoutOwnershipCheck() {
        Post post = publishedPost();
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(0L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of());

        PostResponse response = postService.unpublishPost(5L, 999L, "ADMIN");

        assertEquals(PostStatus.UNPUBLISHED, response.getStatus());
    }

    @Test
    void deletePostShouldArchiveAndDecrementCategoryCount() {
        Post post = draftPost();
        post.setCategoryId(7L);
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));

        postService.deletePost(5L, 10L, "AUTHOR");

        assertEquals(PostStatus.ARCHIVED, post.getStatus());
        verify(postRepository).save(post);
        verify(categoryClient).decrementPostCount(7L);
    }

    @Test
    void deletePostShouldContinueWhenCategoryCountUpdateFails() {
        Post post = draftPost();
        post.setCategoryId(7L);
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));
        doThrow(new RuntimeException("category down")).when(categoryClient).decrementPostCount(7L);

        postService.deletePost(5L, 10L, "AUTHOR");

        assertEquals(PostStatus.ARCHIVED, post.getStatus());
        verify(postRepository).save(post);
    }

    @Test
    void deletePostShouldAllowAdminAndSkipCategoryUpdateWhenCategoryIsNull() {
        Post post = draftPost();
        post.setCategoryId(null);
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));

        postService.deletePost(5L, 999L, "ADMIN");

        assertEquals(PostStatus.ARCHIVED, post.getStatus());
        verify(postRepository).save(post);
        verify(categoryClient, never()).decrementPostCount(anyLong());
    }

    @Test
    void incrementViewsShouldUpdateViewCountersAndCacheMisses() {
        Post post = draftPost();
        post.setViewCount(4L);
        post.setDailyViewCount(2L);
        post.setLastViewDate(LocalDate.now());
        when(cacheService.hasKey("post:view:5:session-1")).thenReturn(false);
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));

        postService.incrementViews(5L, "session-1");

        assertEquals(5L, post.getViewCount());
        assertEquals(3L, post.getDailyViewCount());
        verify(postRepository).save(post);
        verify(cacheService).put("post:view:5:session-1", true, 30, TimeUnit.MINUTES);
    }

    @Test
    void incrementViewsShouldResetDailyCounterForFirstViewOfDay() {
        Post post = draftPost();
        post.setViewCount(1L);
        post.setDailyViewCount(7L);
        post.setLastViewDate(LocalDate.now().minusDays(1));
        when(cacheService.hasKey("post:view:5:session-2")).thenReturn(false);
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));

        postService.incrementViews(5L, "session-2");

        assertEquals(2L, post.getViewCount());
        assertEquals(1L, post.getDailyViewCount());
        assertEquals(LocalDate.now(), post.getLastViewDate());
    }

    @Test
    void incrementViewsShouldThrowWhenPostDoesNotExist() {
        when(cacheService.hasKey("post:view:404:session")).thenReturn(false);
        when(postRepository.findByPostId(404L)).thenReturn(Optional.empty());

        assertThrows(PostNotFoundException.class, () -> postService.incrementViews(404L, "session"));
    }

    @Test
    void incrementViewsShouldSkipUpdateWhenSessionAlreadyCounted() {
        when(cacheService.hasKey("post:view:5:session-1")).thenReturn(true);

        postService.incrementViews(5L, "session-1");

        verify(postRepository, never()).findByPostId(anyLong());
        verify(postRepository, never()).save(any());
    }

    @Test
    void likePostShouldThrowWhenPostDoesNotExist() {
        when(postRepository.existsById(5L)).thenReturn(false);

        assertThrows(PostNotFoundException.class, () -> postService.likePost(5L, 99L));

        verify(postLikeRepository, never()).save(any());
    }

    @Test
    void likePostShouldThrowWhenUserAlreadyLikedPost() {
        when(postRepository.existsById(5L)).thenReturn(true);
        when(postLikeRepository.existsByPostIdAndUserId(5L, 99L)).thenReturn(true);

        assertThrows(AlreadyLikedException.class, () -> postService.likePost(5L, 99L));

        verify(postLikeRepository, never()).save(any());
        verify(postRepository, never()).incrementLikesCount(anyLong());
    }

    @Test
    void likePostShouldPersistLikeIncrementCounterAndNotifyAuthor() {
        Post post = publishedPost();
        when(postRepository.existsById(5L)).thenReturn(true);
        when(postLikeRepository.existsByPostIdAndUserId(5L, 99L)).thenReturn(false);
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));

        postService.likePost(5L, 99L);

        ArgumentCaptor<PostLike> likeCaptor = ArgumentCaptor.forClass(PostLike.class);
        verify(postLikeRepository).save(likeCaptor.capture());
        assertEquals(5L, likeCaptor.getValue().getPostId());
        assertEquals(99L, likeCaptor.getValue().getUserId());
        verify(postRepository).incrementLikesCount(5L);

        ArgumentCaptor<NotificationEvent> notificationCaptor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(postEventPublisher).publishNotificationEvent(notificationCaptor.capture());
        NotificationEvent event = notificationCaptor.getValue();
        assertEquals(10L, event.getUserId());
        assertEquals("LIKE", event.getType());
        assertEquals("5", event.getMetadata().get("postId"));
        assertEquals("99", event.getMetadata().get("actorId"));
    }

    @Test
    void likePostShouldNotNotifyWhenAuthorLikesOwnPost() {
        Post post = publishedPost();
        when(postRepository.existsById(5L)).thenReturn(true);
        when(postLikeRepository.existsByPostIdAndUserId(5L, 10L)).thenReturn(false);
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));

        postService.likePost(5L, 10L);

        verify(postEventPublisher, never()).publishNotificationEvent(any());
    }

    @Test
    void unlikePostShouldDeleteLikeAndDecrementCounterWhenLikeExists() {
        when(postRepository.existsById(5L)).thenReturn(true);
        when(postLikeRepository.existsByPostIdAndUserId(5L, 99L)).thenReturn(true);

        postService.unlikePost(5L, 99L);

        verify(postLikeRepository).deleteByPostIdAndUserId(5L, 99L);
        verify(postRepository).decrementLikesCount(5L);
    }

    @Test
    void unlikePostShouldThrowWhenPostDoesNotExist() {
        when(postRepository.existsById(5L)).thenReturn(false);

        assertThrows(PostNotFoundException.class, () -> postService.unlikePost(5L, 99L));
    }

    @Test
    void unlikePostShouldDoNothingWhenLikeDoesNotExist() {
        when(postRepository.existsById(5L)).thenReturn(true);
        when(postLikeRepository.existsByPostIdAndUserId(5L, 99L)).thenReturn(false);

        postService.unlikePost(5L, 99L);

        verify(postLikeRepository, never()).deleteByPostIdAndUserId(anyLong(), anyLong());
        verify(postRepository, never()).decrementLikesCount(anyLong());
    }

    @Test
    void getTrendingPostsShouldMapRepositoryResults() {
        Post post = publishedPost();
        when(postRepository.findTop5ByStatusOrderByDailyViewCountDesc(PostStatus.PUBLISHED)).thenReturn(List.of(post));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(6L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of(tag(1L), tag(2L)));
        when(postLikeRepository.existsByPostIdAndUserId(5L, 99L)).thenReturn(true);

        List<PostResponse> response = postService.getTrendingPosts(99L);

        assertEquals(1, response.size());
        assertEquals(6L, response.get(0).getCommentsCount());
        assertEquals(List.of(1L, 2L), response.get(0).getTagIds());
        assertTrue(response.get(0).isLikedByCurrentUser());
    }

    @Test
    void getPostCountShouldDelegateToRepository() {
        when(postRepository.countByAuthorId(10L)).thenReturn(42L);

        Long count = postService.getPostCount(10L, 99L, "ADMIN");

        assertEquals(42L, count);
    }

    @Test
    void serviceShouldBeCreatedWithInjectedMocks() {
        assertNotNull(postService);
        verifyNoInteractions(newsletterClient);
    }

    @Test
    void createPostShouldGenerateIncrementedSlugAndIgnoreCategoryFailure() {
        PostRequest request = baseRequest();
        request.setPremium(false);
        request.setPrice(0.0);
        request.setCategoryId(7L);
        when(postRepository.existsBySlug("hello-scriptalert1script-world")).thenReturn(true);
        when(postRepository.existsBySlug("hello-scriptalert1script-world-1")).thenReturn(false);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setPostId(100L);
            return post;
        });
        doThrow(new RuntimeException("category down")).when(categoryClient).incrementPostCount(7L);
        when(categoryClient.getTagsByPost(100L)).thenReturn(List.of());
        when(commentClient.getCommentCountByPost(100L)).thenReturn(0L);

        PostResponse response = postService.createPost(request, 10L);

        assertEquals(100L, response.getPostId());
        verify(postRepository).existsBySlug("hello-scriptalert1script-world");
        verify(postRepository).existsBySlug("hello-scriptalert1script-world-1");
    }

    @Test
    void getPostByIdShouldLockPremiumContentWhenPaymentServiceFails() {
        Post post = premiumPost();
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));
        when(postLikeRepository.existsByPostIdAndUserId(5L, 99L)).thenReturn(false);
        when(paymentClient.hasAccess("99", "5")).thenThrow(new RuntimeException("payment down"));
        when(commentClient.getCommentCountByPost(5L)).thenThrow(new RuntimeException("comment down"));
        when(categoryClient.getTagsByPost(5L)).thenThrow(new RuntimeException("tag down"));

        PostResponse response = postService.getPostById(5L, 99L);

        assertFalse(response.isAccessUnlocked());
        assertTrue(response.getContent().contains("\"locked\": true"));
        assertEquals(0L, response.getCommentsCount());
        assertTrue(response.getTagIds().isEmpty());
    }

    private PostRequest baseRequest() {
        return PostRequest.builder()
                .title("Hello <script>alert(1)</script> World")
                .content("<p>Hello World</p><script>alert(1)</script>")
                .excerpt("Short excerpt")
                .featuredImageUrl("https://fallback/img.jpg")
                .categoryId(7L)
                .tagIds(List.of(11L, 22L))
                .isPremium(true)
                .price(19.0)
                .build();
    }

    private Post draftPost() {
        return Post.builder()
                .postId(5L)
                .authorId(10L)
                .categoryId(7L)
                .title("Draft Title")
                .slug("draft-title")
                .content("Draft content body")
                .excerpt("Draft excerpt")
                .featuredImageUrl("https://cdn/original.jpg")
                .status(PostStatus.DRAFT)
                .readTimeMin(1)
                .viewCount(0L)
                .likesCount(0L)
                .dailyViewCount(0L)
                .isPremium(false)
                .price(0.0)
                .createdAt(LocalDateTime.now().minusDays(2))
                .updatedAt(LocalDateTime.now().minusDays(1))
                .build();
    }

    private Post publishedPost() {
        Post post = draftPost();
        post.setStatus(PostStatus.PUBLISHED);
        post.setPublishedAt(LocalDateTime.now().minusHours(5));
        post.setTitle("Published Title");
        post.setSlug("published-title");
        return post;
    }

    private Post premiumPost() {
        Post post = publishedPost();
        post.setPremium(true);
        post.setPrice(29.0);
        post.setContent("Premium full content body");
        post.setExcerpt("Premium preview");
        return post;
    }

    private Page<Post> pageOf(Post post) {
        return new PageImpl<>(List.of(post), pageable, 1);
    }

    @Test
    void createPost_WithNullTags_ShouldSucceed() {
        PostRequest request = baseRequest();
        request.setTagIds(null);
        when(postRepository.existsBySlug(anyString())).thenReturn(false);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setPostId(101L);
            return post;
        });
        when(categoryClient.getTagsByPost(101L)).thenReturn(List.of());
        when(commentClient.getCommentCountByPost(101L)).thenReturn(0L);

        postService.createPost(request, 10L);
        verify(categoryClient, never()).addTagToPost(anyLong(), anyLong());
    }

    @Test
    void getPostBySlug_ForAnonymousUser_ShouldSucceed() {
        Post post = publishedPost();
        when(postRepository.findBySlugAndStatus("published-title", PostStatus.PUBLISHED)).thenReturn(Optional.of(post));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(0L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of());

        PostResponse response = postService.getPostBySlug("published-title", null);
        assertNotNull(response);
    }

    @Test
    void searchPosts_WithMultipleMatchingTags_ShouldBeDistinct() {
        CategoryClient.TagSummary tag1 = tag(1L);
        CategoryClient.TagSummary tag2 = tag(2L);
        when(categoryClient.searchTags("test")).thenReturn(List.of(tag1, tag2));
        when(categoryClient.getPostIdsByTag(1L)).thenReturn(List.of(5L, 6L));
        when(categoryClient.getPostIdsByTag(2L)).thenReturn(List.of(6L, 7L));
        when(postRepository.searchPostsWithTags(eq("test"), any(), eq(PostStatus.PUBLISHED), any())).thenReturn(new PageImpl<>(List.of(publishedPost())));
        when(commentClient.getCommentCountByPost(anyLong())).thenReturn(0L);
        when(categoryClient.getTagsByPost(anyLong())).thenReturn(List.of());

        postService.searchPosts("test", pageable, null);
        verify(postRepository).searchPostsWithTags(eq("test"), eq(List.of(5L, 6L, 7L)), eq(PostStatus.PUBLISHED), any());
    }

    @Test
    void incrementViews_WhenLastViewDateIsNull_ShouldResetCounter() {
        Post post = draftPost();
        post.setLastViewDate(null);
        when(cacheService.hasKey(anyString())).thenReturn(false);
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));

        postService.incrementViews(5L, "session");
        assertEquals(1L, post.getDailyViewCount());
        assertNotNull(post.getLastViewDate());
    }

    @Test
    void validateFeaturedImage_WhenMediaDataIsNull_ShouldReturnDefaultUrl() {
        PostRequest request = baseRequest();
        request.setFeaturedImageMediaId(99L);
        when(mediaClient.getMedia(eq(99L), anyString(), anyString())).thenReturn(null);

        // We need to trigger validateFeaturedImage, e.g. via createPost
        when(postRepository.existsBySlug(anyString())).thenReturn(false);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryClient.getTagsByPost(any())).thenReturn(List.of());
        when(commentClient.getCommentCountByPost(any())).thenReturn(0L);

        PostResponse response = postService.createPost(request, 10L);
        assertEquals(request.getFeaturedImageUrl(), response.getFeaturedImageUrl());
    }

    @Test
    void mapToPostResponse_WithPremiumAndNoExcerpt_ShouldUseObfuscatedContent() {
        Post post = premiumPost();
        post.setExcerpt(null);
        post.setContent("Very long content that should be truncated for preview when locked by the system for premium posts.");
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));
        when(paymentClient.hasAccess(anyString(), anyString())).thenReturn(false);
        when(commentClient.getCommentCountByPost(5L)).thenReturn(0L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of());

        PostResponse response = postService.getPostById(5L, 99L);
        assertTrue(response.getContent().contains("\"locked\": true"));
        assertTrue(response.getContent().contains("Very long content"));
    }

    @Test
    void createPostShouldSkipCategoryCountWhenCategoryIsNull() {
        PostRequest request = baseRequest();
        request.setCategoryId(null);
        when(postRepository.existsBySlug(anyString())).thenReturn(false);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setPostId(102L);
            return post;
        });
        when(categoryClient.getTagsByPost(102L)).thenReturn(List.of());
        when(commentClient.getCommentCountByPost(102L)).thenReturn(0L);

        PostResponse response = postService.createPost(request, 10L);

        assertEquals(102L, response.getPostId());
        verify(categoryClient, never()).incrementPostCount(anyLong());
    }

    @Test
    void validateFeaturedImageWhenMediaDataHasNoUrlShouldReturnRequestUrl() {
        PostRequest request = baseRequest();
        request.setFeaturedImageMediaId(99L);
        when(mediaClient.getMedia(eq(99L), anyString(), anyString())).thenReturn(Map.of("id", 99L));
        when(postRepository.existsBySlug(anyString())).thenReturn(false);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setPostId(103L);
            return post;
        });
        when(categoryClient.getTagsByPost(103L)).thenReturn(List.of());
        when(commentClient.getCommentCountByPost(103L)).thenReturn(0L);

        PostResponse response = postService.createPost(request, 10L);

        assertEquals(request.getFeaturedImageUrl(), response.getFeaturedImageUrl());
    }

    @Test
    void getPostByIdShouldLockPremiumContentForAnonymousRequester() {
        Post post = premiumPost();
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));
        when(commentClient.getCommentCountByPost(5L)).thenReturn(0L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of());

        PostResponse response = postService.getPostById(5L, null);

        assertFalse(response.isAccessUnlocked());
        assertTrue(response.getContent().contains("\"locked\": true"));
        verify(paymentClient, never()).hasAccess(anyString(), anyString());
    }

    @Test
    void getPostByIdShouldUseFullContentWhenPremiumAccessIsGranted() {
        Post post = premiumPost();
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));
        when(postLikeRepository.existsByPostIdAndUserId(5L, 99L)).thenReturn(false);
        when(paymentClient.hasAccess("99", "5")).thenReturn(true);
        when(commentClient.getCommentCountByPost(5L)).thenReturn(0L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of());

        PostResponse response = postService.getPostById(5L, 99L);

        assertTrue(response.isAccessUnlocked());
        assertEquals("Premium full content body", response.getContent());
    }

    @Test
    void getPostByIdShouldUseShortContentAsPreviewWhenExcerptMissing() {
        Post post = premiumPost();
        post.setExcerpt(null);
        post.setContent("short premium preview");
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(post));
        when(postLikeRepository.existsByPostIdAndUserId(5L, 99L)).thenReturn(false);
        when(paymentClient.hasAccess("99", "5")).thenReturn(false);
        when(commentClient.getCommentCountByPost(5L)).thenReturn(0L);
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of());

        PostResponse response = postService.getPostById(5L, 99L);

        assertFalse(response.isAccessUnlocked());
        assertTrue(response.getContent().contains("short premium preview"));
    }

    @Test
    void explorePosts_WithKeyword_ShouldDelegateToSearch() {
        Post post = publishedPost();
        when(categoryClient.searchTags("test")).thenReturn(List.of());
        when(postRepository.searchPosts("test", PostStatus.PUBLISHED, pageable)).thenReturn(pageOf(post));
        when(commentClient.getCommentCountByPost(anyLong())).thenReturn(0L);
        when(categoryClient.getTagsByPost(anyLong())).thenReturn(List.of());

        PaginatedResponse<PostResponse> response = postService.explorePosts("latest", null, null, "test", pageable, null);

        assertEquals(1, response.getContent().size());
        verify(postRepository).searchPosts("test", PostStatus.PUBLISHED, pageable);
    }

    @Test
    void explorePosts_WithTagId_ShouldDelegateToTagFilter() {
        Post post = publishedPost();
        when(categoryClient.getPostIdsByTag(11L)).thenReturn(List.of(5L));
        when(postRepository.findByPostIdInAndStatus(List.of(5L), PostStatus.PUBLISHED, pageable)).thenReturn(pageOf(post));
        when(commentClient.getCommentCountByPost(anyLong())).thenReturn(0L);
        when(categoryClient.getTagsByPost(anyLong())).thenReturn(List.of());

        PaginatedResponse<PostResponse> response = postService.explorePosts("latest", null, 11L, null, pageable, null);

        assertEquals(1, response.getContent().size());
    }

    @Test
    void explorePosts_WithCategoryId_ShouldFilterByCategory() {
        Post post = publishedPost();
        when(postRepository.findByCategoryIdAndStatusOrderByPublishedAtDesc(7L, PostStatus.PUBLISHED, pageable)).thenReturn(pageOf(post));
        when(commentClient.getCommentCountByPost(anyLong())).thenReturn(0L);
        when(categoryClient.getTagsByPost(anyLong())).thenReturn(List.of());

        PaginatedResponse<PostResponse> response = postService.explorePosts("latest", 7L, null, null, pageable, null);

        assertEquals(1, response.getContent().size());
        verify(postRepository).findByCategoryIdAndStatusOrderByPublishedAtDesc(7L, PostStatus.PUBLISHED, pageable);
    }

    @Test
    void explorePosts_WithViewsSort_ShouldSortByViews() {
        Post post = publishedPost();
        when(postRepository.findAllPublishedOrderByViewCountDesc(PostStatus.PUBLISHED, pageable)).thenReturn(pageOf(post));
        when(commentClient.getCommentCountByPost(anyLong())).thenReturn(0L);
        when(categoryClient.getTagsByPost(anyLong())).thenReturn(List.of());

        postService.explorePosts("views", null, null, null, pageable, null);

        verify(postRepository).findAllPublishedOrderByViewCountDesc(PostStatus.PUBLISHED, pageable);
    }

    @Test
    void explorePosts_WithLikesSort_ShouldSortByLikes() {
        Post post = publishedPost();
        when(postRepository.findAllPublishedOrderByLikesCountDesc(PostStatus.PUBLISHED, pageable)).thenReturn(pageOf(post));
        when(commentClient.getCommentCountByPost(anyLong())).thenReturn(0L);
        when(categoryClient.getTagsByPost(anyLong())).thenReturn(List.of());

        postService.explorePosts("likes", null, null, null, pageable, null);

        verify(postRepository).findAllPublishedOrderByLikesCountDesc(PostStatus.PUBLISHED, pageable);
    }

    @Test
    void explorePosts_WithTrendingSort_ShouldSortByTrending() {
        Post post = publishedPost();
        when(postRepository.findAllPublishedTrending(PostStatus.PUBLISHED, pageable)).thenReturn(pageOf(post));
        when(commentClient.getCommentCountByPost(anyLong())).thenReturn(0L);
        when(categoryClient.getTagsByPost(anyLong())).thenReturn(List.of());

        postService.explorePosts("trending", null, null, null, pageable, null);

        verify(postRepository).findAllPublishedTrending(PostStatus.PUBLISHED, pageable);
    }

    @Test
    void explorePosts_WithDefaultSort_ShouldSortByLatest() {
        Post post = publishedPost();
        when(postRepository.findByStatus(PostStatus.PUBLISHED, pageable)).thenReturn(pageOf(post));
        when(commentClient.getCommentCountByPost(anyLong())).thenReturn(0L);
        when(categoryClient.getTagsByPost(anyLong())).thenReturn(List.of());

        postService.explorePosts("invalid", null, null, null, pageable, null);

        verify(postRepository).findByStatus(PostStatus.PUBLISHED, pageable);
    }

    @Test
    void calculateReadTime_WithBlankContent_ShouldReturnZero() {
        PostRequest request = baseRequest();
        request.setContent("");
        when(postRepository.existsBySlug(anyString())).thenReturn(false);
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryClient.getTagsByPost(any())).thenReturn(List.of());
        when(commentClient.getCommentCountByPost(any())).thenReturn(0L);

        PostResponse response = postService.createPost(request, 10L);
        assertEquals(0, response.getReadTimeMin());
    }

    @Test
    void syncTags_WithAddTagFailure_ShouldHandleException() {
        Post existing = draftPost();
        PostRequest request = baseRequest();
        request.setTagIds(List.of(33L)); // Add new tag
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(existing));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of()); // No existing tags
        doThrow(new RuntimeException("category-service down")).when(categoryClient).addTagToPost(33L, 5L);
        when(commentClient.getCommentCountByPost(anyLong())).thenReturn(0L);

        // This should not throw an exception as we catch it and log a warning
        postService.updatePost(5L, request, 10L, "AUTHOR");
        verify(categoryClient).addTagToPost(33L, 5L);
    }

    @Test
    void syncTags_WithRemoveTagFailure_ShouldHandleException() {
        Post existing = draftPost();
        PostRequest request = baseRequest();
        request.setTagIds(List.of()); // Remove all tags
        when(postRepository.findByPostId(5L)).thenReturn(Optional.of(existing));
        when(postRepository.save(any(Post.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryClient.getTagsByPost(5L)).thenReturn(List.of(tag(11L))); // Existing tag
        doThrow(new RuntimeException("category-service down")).when(categoryClient).removeTagFromPost(11L, 5L);
        when(commentClient.getCommentCountByPost(anyLong())).thenReturn(0L);

        // This should not throw an exception
        postService.updatePost(5L, request, 10L, "AUTHOR");
        verify(categoryClient).removeTagFromPost(11L, 5L);
    }

    private CategoryClient.TagSummary tag(Long id) {
        CategoryClient.TagSummary tag = new CategoryClient.TagSummary();
        tag.setTagId(id);
        tag.setName("tag-" + id);
        return tag;
    }
}
