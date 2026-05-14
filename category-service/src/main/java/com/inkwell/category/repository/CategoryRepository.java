package com.inkwell.category.repository;

import com.inkwell.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findBySlug(String slug);
    Optional<Category> findByCategoryId(Long categoryId);
    List<Category> findByParentCategoryId(Long parentCategoryId);
    boolean existsBySlug(String slug);
}
