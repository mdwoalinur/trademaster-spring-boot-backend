package com.trademaster.ims.repository;

import com.trademaster.ims.model.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {
    
    // Find items by sale ID
    List<SaleItem> findBySaleId(Long saleId);
    
    // Find items by product ID
    List<SaleItem> findByProductId(Long productId);
    
    // Delete items by sale ID
    void deleteBySaleId(Long saleId);
    
}