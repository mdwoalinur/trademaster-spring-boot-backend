package com.trademaster.ims.repository;

import com.trademaster.ims.model.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    
    // Find by product and warehouse
    Optional<Inventory> findByProductIdAndWarehouseId(Long productId, Long warehouseId);
    
    // Find all inventory for a company
    List<Inventory> findByCompanyId(Long companyId);
    
    // Find by warehouse
    List<Inventory> findByWarehouseId(Long warehouseId);
    
    // Find by product
    List<Inventory> findByProductId(Long productId);
    
    // Find low stock items (quantity <= minStockLevel)
    List<Inventory> findByQuantityLessThanEqual(Integer threshold);
    
    // Find by warehouse and low stock
    List<Inventory> findByWarehouseIdAndQuantityLessThanEqual(Long warehouseId, Integer threshold);
    
    // Find by product and warehouse (exists check)
    boolean existsByProductIdAndWarehouseId(Long productId, Long warehouseId);
}