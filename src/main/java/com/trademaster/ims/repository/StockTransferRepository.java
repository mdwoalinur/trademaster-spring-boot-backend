// StockTransferRepository.java
package com.trademaster.ims.repository;

import com.trademaster.ims.model.StockTransfer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StockTransferRepository extends JpaRepository<StockTransfer, Long> {
    Page<StockTransfer> findByStatus(StockTransfer.TransferStatus status, Pageable pageable);
    Page<StockTransfer> findByFromWarehouseIdOrToWarehouseId(Long fromWarehouseId, Long toWarehouseId, Pageable pageable);
}