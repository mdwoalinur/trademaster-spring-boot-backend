package com.trademaster.ims.repository;

import com.trademaster.ims.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    
    // Find categories by parent category ID
    List<Category> findByParentCategoryId(Long parentCategoryId);
    
    // Find root categories (parent is null)
    List<Category> findByParentCategoryIdIsNull();
    
    // Find by status
    List<Category> findByStatus(Boolean status);
    
    // Find by category name containing (case-insensitive)
    List<Category> findByCategoryNameContainingIgnoreCase(String categoryName);
}