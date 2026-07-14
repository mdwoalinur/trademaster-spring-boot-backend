package com.trademaster.ims.repository;

import com.trademaster.ims.model.ProductVariation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariationRepository extends JpaRepository<ProductVariation, Long> {
    
    // Find variations by product ID
    List<ProductVariation> findByProductId(Long productId);
    
    // Find by SKU
    Optional<ProductVariation> findBySku(String sku);
    
    // Find by status
    List<ProductVariation> findByStatus(Boolean status);
    
    // Find active variations by product
    List<ProductVariation> findByProductIdAndStatus(Long productId, Boolean status);
    
    // Check if SKU exists
    boolean existsBySku(String sku);
    
    // Check if SKU exists for other variation (for update)
    boolean existsBySkuAndVariationIdNot(String sku, Long variationId);
}