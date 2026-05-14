package com.inkwell.category.repository;

import com.inkwell.category.entity.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, Long> {
    Optional<Tag> findBySlug(String slug);
    Optional<Tag> findByTagId(Long tagId);
    boolean existsBySlug(String slug);

    @Query("SELECT t FROM Tag t ORDER BY t.postCount DESC")
    List<Tag> findTopTags(Pageable pageable);

    List<Tag> findByNameContainingIgnoreCase(String name);
}
