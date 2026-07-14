package com.trademaster.ims.repository;

import com.trademaster.ims.model.SaleReturn;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SaleReturnRepository extends JpaRepository<SaleReturn, Long> {
    Page<SaleReturn> findByStatus(SaleReturn.ReturnStatus status, Pageable pageable);
    List<SaleReturn> findBySaleId(Long saleId);
    List<SaleReturn> findByCustomerId(Long customerId);
    boolean existsByReturnNo(String returnNo);
}