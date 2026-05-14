package com.inkwell.media.repository;

import com.inkwell.media.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MediaRepository extends JpaRepository<Media, Long> {
    List<Media> findByUploaderId(Long uploaderId);
    Optional<Media> findByMediaId(Long mediaId);
    List<Media> findByLinkedPostId(Long linkedPostId);
    List<Media> findByMimeType(String mimeType);
    List<Media> findByIsDeleted(Boolean isDeleted);
    Long countByUploaderId(Long uploaderId);
    void deleteByMediaId(Long mediaId);
}
