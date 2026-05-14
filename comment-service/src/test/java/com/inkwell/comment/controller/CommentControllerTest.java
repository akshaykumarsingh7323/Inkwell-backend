package com.inkwell.comment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.inkwell.comment.dto.CommentRequest;
import com.inkwell.comment.dto.CommentResponse;
import com.inkwell.comment.dto.ModerationRequest;
import com.inkwell.comment.dto.UpdateCommentRequest;
import com.inkwell.comment.service.CommentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CommentController.class)
@AutoConfigureMockMvc(addFilters = false)
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommentService commentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createComment_ShouldReturnCreated() throws Exception {
        CommentRequest request = new CommentRequest();
        request.setPostId(1L);
        request.setContent("Test content");

        when(commentService.createComment(any(), anyLong())).thenReturn(new CommentResponse());

        mockMvc.perform(post("/comments")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void getByPost_ShouldReturnOk() throws Exception {
        when(commentService.getApprovedCommentsByPost(1L)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/comments/post/1"))
                .andExpect(status().isOk());
    }

    @Test
    void moderateComment_ShouldReturnOk() throws Exception {
        ModerationRequest request = new ModerationRequest();
        request.setAction("APPROVE");
        when(commentService.moderateComment(anyLong(), any(), anyLong(), anyString())).thenReturn(new CommentResponse());

        mockMvc.perform(patch("/comments/1/moderate")
                .header("X-User-Id", "1")
                .header("X-User-Role", "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteComment_ShouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/comments/1")
                .header("X-User-Id", "1")
                .header("X-User-Role", "ADMIN"))
                .andExpect(status().isNoContent());
    }

    @Test
    void getCountByPost_ShouldReturnOk() throws Exception {
        when(commentService.getCommentCountByPost(1L)).thenReturn(5L);
        mockMvc.perform(get("/comments/post/1/count"))
                .andExpect(status().isOk())
                .andExpect(content().string("5"));
    }

    @Test
    void getPending_WhenAdmin_ShouldReturnOk() throws Exception {
        when(commentService.getPendingComments()).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/comments/pending")
                .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void getPending_WhenNotAdmin_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/comments/pending")
                .header("X-User-Role", "READER"))
                .andExpect(status().isForbidden());
    }

    @Test
    void approve_WhenAdmin_ShouldReturnOk() throws Exception {
        when(commentService.approveComment(1L)).thenReturn(new CommentResponse());
        mockMvc.perform(put("/comments/1/approve")
                .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void reject_WhenAdmin_ShouldReturnOk() throws Exception {
        when(commentService.rejectComment(1L)).thenReturn(new CommentResponse());
        mockMvc.perform(put("/comments/1/reject")
                .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk());
    }

    @Test
    void update_ShouldReturnOk() throws Exception {
        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("Updated content");
        when(commentService.updateComment(anyLong(), any(), anyLong(), anyString())).thenReturn(new CommentResponse());

        mockMvc.perform(put("/comments/1")
                .header("X-User-Id", "1")
                .header("X-User-Role", "READER")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void like_ShouldReturnOk() throws Exception {
        when(commentService.likeComment(anyLong(), anyLong())).thenReturn(new CommentResponse());
        mockMvc.perform(post("/comments/1/like")
                .header("X-User-Id", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void unlike_ShouldReturnOk() throws Exception {
        when(commentService.unlikeComment(anyLong(), anyLong())).thenReturn(new CommentResponse());
        mockMvc.perform(post("/comments/1/unlike")
                .header("X-User-Id", "1"))
                .andExpect(status().isOk());
    }

    @Test
    void validateUserId_WhenHeaderMissing_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/comments/1/like"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validateUserId_WhenHeaderInvalid_ShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/comments/1/like")
                .header("X-User-Id", "not-a-number"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validateUserId_WhenHeaderBlank_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(post("/comments/1/like")
                .header("X-User-Id", " "))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPendingForRequester_ShouldReturnOk() throws Exception {
        when(commentService.getPendingCommentsForRequester(anyLong(), anyString())).thenReturn(Collections.emptyList());
        mockMvc.perform(get("/comments/pending/mine")
                .header("X-User-Id", "1")
                .header("X-User-Role", "AUTHOR"))
                .andExpect(status().isOk());
    }
}
