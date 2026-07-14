package com.trademaster.ims.repository;

import com.trademaster.ims.model.SaleReturnItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface SaleReturnItemRepository extends JpaRepository<SaleReturnItem, Long> {
    List<SaleReturnItem> findByReturnId(Long returnId);
    void deleteByReturnId(Long returnId);
}