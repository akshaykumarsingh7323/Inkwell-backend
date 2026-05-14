package com.inkwell.comment.service;

import com.inkwell.comment.dto.CommentRequest;
import com.inkwell.comment.dto.CommentResponse;
import com.inkwell.comment.dto.ModerationRequest;
import com.inkwell.comment.dto.UpdateCommentRequest;

import java.util.List;

public interface CommentService {
    CommentResponse createComment(CommentRequest request, Long requesterId);
    List<CommentResponse> getApprovedCommentsByPost(Long postId);
    List<CommentResponse> getPendingComments();
    List<CommentResponse> getPendingCommentsForRequester(Long requesterId, String requesterRole);
    CommentResponse approveComment(Long commentId);
    CommentResponse rejectComment(Long commentId);
    void deleteComment(Long commentId, Long requesterId, String requesterRole);
    CommentResponse updateComment(Long commentId, UpdateCommentRequest request, Long requesterId, String requesterRole);
    CommentResponse moderateComment(Long commentId, ModerationRequest request, Long requesterId, String requesterRole);
    CommentResponse likeComment(Long commentId, Long requesterId);
    CommentResponse unlikeComment(Long commentId, Long requesterId);
    long getCommentCountByPost(Long postId);
}
