package com.trademaster.ims.service;

import com.trademaster.ims.model.WastageCategory;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.WastageCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class WastageCategoryService {

    @Autowired
    private WastageCategoryRepository categoryRepository;

    public List<WastageCategory> getAllCategories() {
        return categoryRepository.findAll();
    }

    public WastageCategory getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found"));
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "WastageCategory")
    public WastageCategory createCategory(WastageCategory category) {
        category.setCreatedAt(LocalDateTime.now());
        category.setUpdatedAt(LocalDateTime.now());
        return categoryRepository.save(category);
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "WastageCategory")
    public WastageCategory updateCategory(Long id, WastageCategory categoryDetails) {
        WastageCategory existing = getCategoryById(id);
        existing.setCategoryName(categoryDetails.getCategoryName());
        existing.setCategoryCode(categoryDetails.getCategoryCode());
        existing.setDescription(categoryDetails.getDescription());
        existing.setLossPercentage(categoryDetails.getLossPercentage());
        existing.setStatus(categoryDetails.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        return categoryRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "WastageCategory")
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }
    
    @Transactional
    public List<WastageCategory> createBulkCategories(List<WastageCategory> categories) {
        List<WastageCategory> createdCategories = new ArrayList<>();
        for (WastageCategory category : categories) {
            createdCategories.add(createCategory(category));
        }
        return createdCategories;
    }
}
