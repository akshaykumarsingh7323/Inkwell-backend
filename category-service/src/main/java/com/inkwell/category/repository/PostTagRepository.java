package com.inkwell.category.repository;

import com.inkwell.category.entity.PostTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PostTagRepository extends JpaRepository<PostTag, Long> {
    List<PostTag> findByPostId(Long postId);
    List<PostTag> findByTagId(Long tagId);
    Optional<PostTag> findByPostIdAndTagId(Long postId, Long tagId);
    void deleteByPostIdAndTagId(Long postId, Long tagId);
}
