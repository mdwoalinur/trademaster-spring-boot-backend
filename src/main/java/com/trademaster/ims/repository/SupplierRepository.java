package com.trademaster.ims.repository;

import com.trademaster.ims.model.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {
    
    // Find by supplier code
    Optional<Supplier> findBySupplierCode(String supplierCode);
    
    // Find by email
    Optional<Supplier> findByEmail(String email);
    
    // Find by phone
    Optional<Supplier> findByPhone(String phone);
    
    // Find by status
    List<Supplier> findByStatus(Boolean status);
    
    // Search by name or code (case-insensitive)
    List<Supplier> findBySupplierNameContainingIgnoreCaseOrSupplierCodeContainingIgnoreCase(String name, String code);
    
    // Find by city
    List<Supplier> findByCity(String city);
    
    // Find by country
    List<Supplier> findByCountry(String country);
    
    // Check if supplier code exists
    boolean existsBySupplierCode(String supplierCode);
    
    // Check if email exists
    boolean existsByEmail(String email);
}