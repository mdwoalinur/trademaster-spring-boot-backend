package com.trademaster.ims.service;

import com.trademaster.ims.model.Category;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public Category getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
    }

    // Get only root categories (parentCategoryId is null)
    public List<Category> getRootCategories() {
        return categoryRepository.findAll().stream()
                .filter(c -> c.getParentCategoryId() == null)
                .collect(Collectors.toList());
    }

    // Get subcategories of a given parent
    public List<Category> getSubCategories(Long parentId) {
        return categoryRepository.findAll().stream()
                .filter(c -> parentId.equals(c.getParentCategoryId()))
                .collect(Collectors.toList());
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "Category")
    public Category createCategory(Category category) {
        category.setCategoryId(null); // ensure new record
        return categoryRepository.save(category);
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "Category")
    public Category updateCategory(Long id, Category categoryDetails) {
        Category existing = getCategoryById(id);
        existing.setCategoryName(categoryDetails.getCategoryName());
        existing.setParentCategoryId(categoryDetails.getParentCategoryId());
        existing.setDescription(categoryDetails.getDescription());
        existing.setStatus(categoryDetails.getStatus());
        return categoryRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "Category")
    public void deleteCategory(Long id) {
        // Optional: handle reassigning children to parent or set their parent to null
        List<Category> children = getSubCategories(id);
        for (Category child : children) {
            child.setParentCategoryId(null);
            categoryRepository.save(child);
        }
        categoryRepository.deleteById(id);
    }
    
    @Transactional
    public List<Category> createBulkCategories(List<Category> categories) {
        // saveAll() একসাথে সবগুলো database এ insert করবে
        return categoryRepository.saveAll(categories);
    }
}
