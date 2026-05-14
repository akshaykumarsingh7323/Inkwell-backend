package com.inkwell.post.controller;

import com.inkwell.post.dto.PaginatedResponse;
import com.inkwell.post.dto.PostRequest;
import com.inkwell.post.dto.PostResponse;
import com.inkwell.post.service.PostService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostService postService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Test
    void createPostShouldReturnCreated() throws Exception {
        PostRequest request = PostRequest.builder()
                .title("Valid Title")
                .content("Valid content long enough")
                .build();
        PostResponse response = PostResponse.builder().postId(1L).title("Title").build();

        when(postService.createPost(any(PostRequest.class), eq(10L))).thenReturn(response);

        mockMvc.perform(post("/posts")
                .header("X-User-Id", "10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(1));
    }

    @Test
    void getByIdShouldReturnOk() throws Exception {
        PostResponse response = PostResponse.builder().postId(1L).title("Title").build();
        when(postService.getPostById(eq(1L), eq(10L))).thenReturn(response);

        mockMvc.perform(get("/posts/1")
                .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1));
    }

    @Test
    void getByIdWithoutHeaderShouldReturnOk() throws Exception {
        when(postService.getPostById(eq(1L), isNull())).thenReturn(new PostResponse());

        mockMvc.perform(get("/posts/1"))
                .andExpect(status().isOk());
    }

    @Test
    void getBySlugShouldReturnOk() throws Exception {
        PostResponse response = PostResponse.builder().postId(1L).slug("slug").build();
        when(postService.getPostBySlug(eq("slug"), any())).thenReturn(response);

        mockMvc.perform(get("/posts/slug/slug")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("slug"));
    }

    @Test
    void getBySlugWithoutHeaderShouldReturnOk() throws Exception {
        when(postService.getPostBySlug(eq("slug"), isNull())).thenReturn(new PostResponse());

        mockMvc.perform(get("/posts/slug/slug"))
                .andExpect(status().isOk());
    }

    @Test
    void getByAuthorShouldReturnOk() throws Exception {
        when(postService.getPostsByAuthor(eq(10L), any())).thenReturn(new PaginatedResponse<>());

        mockMvc.perform(get("/posts/author/10"))
                .andExpect(status().isOk());
    }

    @Test
    void getPublishedShouldReturnOk() throws Exception {
        when(postService.getPublishedPosts(any(), any())).thenReturn(new PaginatedResponse<>());

        mockMvc.perform(get("/posts/published"))
                .andExpect(status().isOk());
    }

    @Test
    void getPublishedByCategoryShouldReturnOk() throws Exception {
        when(postService.getPublishedPostsByCategory(anyLong(), any(), any())).thenReturn(new PaginatedResponse<>());

        mockMvc.perform(get("/posts/published/category/1"))
                .andExpect(status().isOk());
    }

    @Test
    void getPublishedByTagShouldReturnOk() throws Exception {
        when(postService.getPublishedPostsByTag(anyLong(), any(), any())).thenReturn(new PaginatedResponse<>());

        mockMvc.perform(get("/posts/published/tag/1"))
                .andExpect(status().isOk());
    }

    @Test
    void searchShouldReturnOk() throws Exception {
        when(postService.searchPosts(anyString(), any(), any())).thenReturn(new PaginatedResponse<>());

        mockMvc.perform(get("/posts/search?keyword=test"))
                .andExpect(status().isOk());
    }

    @Test
    void updatePostShouldReturnOk() throws Exception {
        PostRequest request = PostRequest.builder()
                .title("Valid Title")
                .content("Valid content long enough")
                .build();
        when(postService.updatePost(eq(1L), any(), eq(10L), eq("AUTHOR"))).thenReturn(new PostResponse());

        mockMvc.perform(put("/posts/1")
                .header("X-User-Id", "10")
                .header("X-User-Role", "AUTHOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void deletePostShouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/posts/1")
                .header("X-User-Id", "10")
                .header("X-User-Role", "AUTHOR"))
                .andExpect(status().isNoContent());

        verify(postService).deletePost(eq(1L), eq(10L), eq("AUTHOR"));
    }

    @Test
    void likePostShouldReturnOk() throws Exception {
        mockMvc.perform(post("/posts/1/like")
                .header("X-User-Id", "10"))
                .andExpect(status().isOk());

        verify(postService).likePost(eq(1L), eq(10L));
    }

    @Test
    void likePostShouldReturnUnauthorizedWhenNoHeader() throws Exception {
        mockMvc.perform(post("/posts/1/like"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void likePostShouldReturnBadRequestWhenInvalidHeader() throws Exception {
        mockMvc.perform(post("/posts/1/like")
                .header("X-User-Id", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTrendingShouldReturnOk() throws Exception {
        when(postService.getTrendingPosts(any())).thenReturn(List.of());

        mockMvc.perform(get("/posts/public/trending"))
                .andExpect(status().isOk());
    }

    @Test
    void incrementViewsShouldReturnOk() throws Exception {
        mockMvc.perform(post("/posts/1/view"))
                .andExpect(status().isOk());
        verify(postService).incrementViews(eq(1L), any());
    }

    @Test
    void publishShouldReturnOk() throws Exception {
        when(postService.publishPost(eq(1L), eq(10L), eq("AUTHOR"))).thenReturn(new PostResponse());

        mockMvc.perform(put("/posts/1/publish")
                        .header("X-User-Id", "10")
                        .header("X-User-Role", "AUTHOR"))
                .andExpect(status().isOk());
    }

    @Test
    void unpublishShouldReturnOk() throws Exception {
        when(postService.unpublishPost(eq(1L), eq(10L), eq("AUTHOR"))).thenReturn(new PostResponse());

        mockMvc.perform(put("/posts/1/unpublish")
                        .header("X-User-Id", "10")
                        .header("X-User-Role", "AUTHOR"))
                .andExpect(status().isOk());
    }

    @Test
    void unlikeShouldReturnOk() throws Exception {
        mockMvc.perform(post("/posts/1/unlike")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk());
        verify(postService).unlikePost(eq(1L), eq(10L));
    }

    @Test
    void getPublishedWithHeaderShouldPassRequesterId() throws Exception {
        when(postService.getPublishedPosts(any(), eq(10L))).thenReturn(new PaginatedResponse<>());

        mockMvc.perform(get("/posts/published")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void getPublishedByCategoryWithHeaderShouldPassRequesterId() throws Exception {
        when(postService.getPublishedPostsByCategory(eq(1L), any(), eq(10L))).thenReturn(new PaginatedResponse<>());

        mockMvc.perform(get("/posts/published/category/1")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void getPublishedByTagWithHeaderShouldPassRequesterId() throws Exception {
        when(postService.getPublishedPostsByTag(eq(1L), any(), eq(10L))).thenReturn(new PaginatedResponse<>());

        mockMvc.perform(get("/posts/published/tag/1")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void searchWithHeaderShouldPassRequesterId() throws Exception {
        when(postService.searchPosts(eq("test"), any(), eq(10L))).thenReturn(new PaginatedResponse<>());

        mockMvc.perform(get("/posts/search?keyword=test")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void getTrendingWithHeaderShouldPassRequesterId() throws Exception {
        when(postService.getTrendingPosts(eq(10L))).thenReturn(List.of());

        mockMvc.perform(get("/posts/public/trending")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk());
    }

    @Test
    void likePostShouldReturnUnauthorizedOnBlankHeader() throws Exception {
        mockMvc.perform(post("/posts/1/like")
                        .header("X-User-Id", " "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCountShouldReturnOk() throws Exception {
        when(postService.getPostCount(anyLong(), any(), any())).thenReturn(5L);

        mockMvc.perform(get("/posts/count/10"))
                .andExpect(status().isOk());
    }

    @Test
    void exploreShouldReturnOk() throws Exception {
        when(postService.explorePosts(anyString(), any(), any(), any(), any(), any())).thenReturn(new PaginatedResponse<>());

        mockMvc.perform(get("/posts/explore?sort=views&categoryId=1&tagId=2&keyword=test&page=0&size=10")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk());

        verify(postService).explorePosts(eq("views"), eq(1L), eq(2L), eq("test"), any(), eq(10L));
    }

    @Test
    void exploreWithoutFiltersShouldUseDefaults() throws Exception {
        when(postService.explorePosts(anyString(), any(), any(), any(), any(), any())).thenReturn(new PaginatedResponse<>());

        mockMvc.perform(get("/posts/explore"))
                .andExpect(status().isOk());

        verify(postService).explorePosts(eq("latest"), isNull(), isNull(), isNull(), any(), isNull());
    }

    @Test
    void getPublishedByAuthorShouldReturnOk() throws Exception {
        when(postService.getPublishedPostsByAuthor(anyLong(), any())).thenReturn(new PaginatedResponse<>());

        mockMvc.perform(get("/posts/public/author/10"))
                .andExpect(status().isOk());
    }

    @Test
    void getPublishedWithInvalidUserIdShouldPassNull() throws Exception {
        when(postService.getPublishedPosts(any(), isNull())).thenReturn(new PaginatedResponse<>());

        mockMvc.perform(get("/posts/published")
                        .header("X-User-Id", "not-a-number"))
                .andExpect(status().isOk());
    }

    @Test
    void getPublishedWithNullStringUserIdShouldPassNull() throws Exception {
        when(postService.getPublishedPosts(any(), isNull())).thenReturn(new PaginatedResponse<>());

        mockMvc.perform(get("/posts/published")
                        .header("X-User-Id", "null"))
                .andExpect(status().isOk());
    }
}
