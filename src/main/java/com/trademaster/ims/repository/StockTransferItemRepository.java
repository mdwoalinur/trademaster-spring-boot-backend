// StockTransferItemRepository.java
package com.trademaster.ims.repository;

import com.trademaster.ims.model.StockTransferItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface StockTransferItemRepository extends JpaRepository<StockTransferItem, Long> {
    List<StockTransferItem> findByStockTransfer_TransferId(Long transferId);
}