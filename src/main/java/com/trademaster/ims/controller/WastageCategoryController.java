package com.trademaster.ims.controller;

import com.trademaster.ims.model.WastageCategory;
import com.trademaster.ims.service.WastageCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wastage-categories")
@CrossOrigin(origins = "http://localhost:4200")
public class WastageCategoryController {

    @Autowired
    private WastageCategoryService categoryService;

    @GetMapping
    public List<WastageCategory> getAllCategories() {
        return categoryService.getAllCategories();
    }

    @GetMapping("/{id}")
    public ResponseEntity<WastageCategory> getCategoryById(@PathVariable Long id) {
        return ResponseEntity.ok(categoryService.getCategoryById(id));
    }

    @PostMapping
    public ResponseEntity<WastageCategory> createCategory(@RequestBody WastageCategory category) {
        WastageCategory created = categoryService.createCategory(category);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<WastageCategory> updateCategory(@PathVariable Long id, @RequestBody WastageCategory category) {
        WastageCategory updated = categoryService.updateCategory(id, category);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/bulk")
    public ResponseEntity<List<WastageCategory>> createBulkCategories(@RequestBody List<WastageCategory> categories) {
        List<WastageCategory> created = categoryService.createBulkCategories(categories);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }
}