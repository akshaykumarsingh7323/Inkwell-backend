package com.inkwell.media.service;

import com.inkwell.media.dto.MediaResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface MediaService {
    MediaResponse uploadMedia(MultipartFile file, Long uploaderId);
    MediaResponse getMediaById(Long mediaId);
    List<MediaResponse> getMediaByUploader(Long uploaderId);
    List<MediaResponse> getMediaByPost(Long postId);
    void deleteMedia(Long mediaId);
    MediaResponse updateAltText(Long mediaId, String altText);
    MediaResponse linkToPost(Long mediaId, Long postId);
    MediaResponse unlinkFromPost(Long mediaId);
    List<MediaResponse> getAllMedia();
    void cleanupDeleted();
}
