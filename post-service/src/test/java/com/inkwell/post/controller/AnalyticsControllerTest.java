package com.inkwell.post.controller;

import com.inkwell.post.entity.Post;
import com.inkwell.post.enums.PostStatus;
import com.inkwell.post.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PostRepository postRepository;

    @Test
    void getTopViewedPostsShouldReturnListWhenAdmin() throws Exception {
        Post post = Post.builder()
                .postId(1L)
                .title("Top Post")
                .slug("top-post")
                .authorId(10L)
                .viewCount(100L)
                .likesCount(5L)
                .build();

        when(postRepository.findTop10ByStatusOrderByViewCountDesc(PostStatus.PUBLISHED))
                .thenReturn(List.of(post));

        mockMvc.perform(get("/posts/analytics/top-viewed")
                .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Top Post"))
                .andExpect(jsonPath("$[0].viewCount").value(100));
    }

    @Test
    void getTopViewedPostsShouldDefaultNullCountsToZero() throws Exception {
        Post post = Post.builder()
                .postId(2L)
                .title("Null Counts")
                .slug("null-counts")
                .authorId(20L)
                .viewCount(null)
                .likesCount(null)
                .build();

        when(postRepository.findTop10ByStatusOrderByViewCountDesc(PostStatus.PUBLISHED))
                .thenReturn(List.of(post));

        mockMvc.perform(get("/posts/analytics/top-viewed")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].viewCount").value(0))
                .andExpect(jsonPath("$[0].likesCount").value(0));
    }

    @Test
    void getTopViewedPostsShouldReturnForbiddenWhenNotAdmin() throws Exception {
        mockMvc.perform(get("/posts/analytics/top-viewed")
                .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void getTopAuthorsShouldReturnListWhenAdmin() throws Exception {
        Object[] row = new Object[]{10L, 500L};
        List<Object[]> rows = new java.util.ArrayList<>();
        rows.add(row);
        when(postRepository.findTopAuthorsByViewCount(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(rows);

        mockMvc.perform(get("/posts/analytics/top-authors")
                .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].authorId").value(10))
                .andExpect(jsonPath("$[0].totalViews").value(500));
    }

    @Test
    void getTopAuthorsShouldReturnForbiddenWhenNotAdmin() throws Exception {
        mockMvc.perform(get("/posts/analytics/top-authors")
                .header("X-User-Role", "AUTHOR"))
                .andExpect(status().isForbidden());
    }
}
