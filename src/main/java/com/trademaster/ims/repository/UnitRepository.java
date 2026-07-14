package com.trademaster.ims.repository;

import com.trademaster.ims.model.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UnitRepository extends JpaRepository<Unit, Long> {
    
    // Find by unit code
    Optional<Unit> findByUnitCode(String unitCode);
    
    // Find by unit type
    List<Unit> findByUnitType(Unit.UnitType unitType);
    
    // Find base units
    List<Unit> findByIsBase(Boolean isBase);
    
    // Find by status
    List<Unit> findByStatus(Boolean status);
    
    // Find base units by type
    List<Unit> findByUnitTypeAndIsBase(Unit.UnitType unitType, Boolean isBase);
    
    // Search by name or code (case-insensitive)
    List<Unit> findByUnitNameContainingIgnoreCaseOrUnitCodeContainingIgnoreCase(String name, String code);
    
    // Check if unit code exists
    boolean existsByUnitCode(String unitCode);
}