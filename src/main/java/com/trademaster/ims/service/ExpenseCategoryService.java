package com.trademaster.ims.service;

import com.trademaster.ims.model.ExpenseCategory;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.ExpenseCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExpenseCategoryService {

    @Autowired
    private ExpenseCategoryRepository categoryRepository;

    public List<ExpenseCategory> getAllCategories() {
        return categoryRepository.findAll();
    }

    public ExpenseCategory getCategoryById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Expense category not found with id: " + id));
    }

    public List<ExpenseCategory> getActiveCategories() {
        return categoryRepository.findByStatusTrue();
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "ExpenseCategory")
    public ExpenseCategory createCategory(ExpenseCategory category) {
        if (categoryRepository.existsByCategoryCode(category.getCategoryCode())) {
            throw new RuntimeException("Category code already exists");
        }
        return categoryRepository.save(category);
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "ExpenseCategory")
    public ExpenseCategory updateCategory(Long id, ExpenseCategory categoryDetails) {
        ExpenseCategory existing = getCategoryById(id);
        existing.setCategoryName(categoryDetails.getCategoryName());
        existing.setCategoryCode(categoryDetails.getCategoryCode());
        existing.setParentCategoryId(categoryDetails.getParentCategoryId());
        existing.setDescription(categoryDetails.getDescription());
        existing.setStatus(categoryDetails.getStatus());
        return categoryRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "ExpenseCategory")
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }
    
    @Transactional
    public List<ExpenseCategory> createBulkCategories(List<ExpenseCategory> categories) {
        List<ExpenseCategory> createdCategories = new ArrayList<>();
        for (ExpenseCategory category : categories) {
            // আগে থেকে থাকা createCategory লজিক ব্যবহার হচ্ছে (যাতে categoryCode unique চেক হয়)
            createdCategories.add(createCategory(category));
        }
        return createdCategories;
    }
}
