package com.trademaster.ims.repository;

import com.trademaster.ims.model.WastageCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WastageCategoryRepository extends JpaRepository<WastageCategory, Long> {
    
    // Find by category code
    Optional<WastageCategory> findByCategoryCode(String categoryCode);
    
    // Find by company ID
    List<WastageCategory> findByCompanyId(Long companyId);
    
    // Find active categories
    List<WastageCategory> findByStatusTrue();
    
    // Find by status
    List<WastageCategory> findByStatus(Boolean status);
    
    // Search by name or code (case-insensitive)
    List<WastageCategory> findByCategoryNameContainingIgnoreCaseOrCategoryCodeContainingIgnoreCase(String name, String code);
    
    // Find by company and status
    List<WastageCategory> findByCompanyIdAndStatus(Long companyId, Boolean status);
    
    // Check if category code exists
    boolean existsByCategoryCode(String categoryCode);
    
    // Check if category code exists for other category (for update)
    boolean existsByCategoryCodeAndCategoryIdNot(String categoryCode, Long categoryId);
}