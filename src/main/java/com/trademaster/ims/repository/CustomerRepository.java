package com.trademaster.ims.repository;

import com.trademaster.ims.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    
    // Find by customer code
    Optional<Customer> findByCustomerCode(String customerCode);
    
    // Find by email
    Optional<Customer> findByEmail(String email);
    
    // Find by phone
    Optional<Customer> findByPhone(String phone);
    
    // Find by status
    List<Customer> findByStatus(Boolean status);
    
    // Find by customer type
    List<Customer> findByCustomerType(Customer.CustomerType customerType);
    
    // Search by name or code (case-insensitive)
    List<Customer> findByCustomerNameContainingIgnoreCaseOrCustomerCodeContainingIgnoreCase(String name, String code);
    
    // Find by city
    List<Customer> findByCity(String city);
    
    // Find customers with outstanding balance
    List<Customer> findByCurrentBalanceGreaterThan(java.math.BigDecimal zero);
}