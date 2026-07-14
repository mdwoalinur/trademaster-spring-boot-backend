package com.trademaster.ims.repository;

import com.trademaster.ims.model.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    
    // Find by warehouse code
    Optional<Warehouse> findByWarehouseCode(String warehouseCode);
    
    // Find by name containing (case-insensitive)
    List<Warehouse> findByNameContainingIgnoreCase(String name);
    
    // Find by status
    List<Warehouse> findByStatus(String status);
    
    // Find by location containing
    List<Warehouse> findByLocationContainingIgnoreCase(String location);
    
    // Check if warehouse code exists
    boolean existsByWarehouseCode(String warehouseCode);
    
    // Check if warehouse code exists for other warehouse (for update)
    boolean existsByWarehouseCodeAndIdNot(String warehouseCode, Long id);
}