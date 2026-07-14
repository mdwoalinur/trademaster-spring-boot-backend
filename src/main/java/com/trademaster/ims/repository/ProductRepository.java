package com.trademaster.ims.repository;

import com.trademaster.ims.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    
    // Find by product code
    Optional<Product> findByProductCode(String productCode);
    
    // Find by SKU
    Optional<Product> findBySku(String sku);
    
    // Find by status
    List<Product> findByStatus(Product.ProductStatus status);
    
    // Find by category
    List<Product> findByCategoryId(Long categoryId);
    
    // Search products by name, code, or SKU (case-insensitive)
    List<Product> findByProductNameContainingIgnoreCaseOrProductCodeContainingIgnoreCaseOrSkuContainingIgnoreCase(
            String productName, String productCode, String sku);
    
    // Find low stock products (quantity <= reorderLevel) - requires join with inventory
    // @Query("SELECT p FROM Product p JOIN Inventory i ON p.id = i.productId WHERE i.quantity <= p.reorderLevel")
    // List<Product> findLowStockProducts();
    
    // Check if product code exists
    boolean existsByProductCode(String productCode);
    
    // Check if SKU exists
    boolean existsBySku(String sku);
    
}