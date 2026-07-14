package com.trademaster.ims.repository;

import com.trademaster.ims.model.PurchaseReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Long> {
    Optional<PurchaseReturn> findByReturnNo(String returnNo);
    List<PurchaseReturn> findByOriginalPurchaseId(Long purchaseId);

    @Query("SELECT pr FROM PurchaseReturn pr WHERE " +
            "(:status IS NULL OR pr.status = :status) AND " +
            "(:startDate IS NULL OR pr.returnDate >= :startDate) AND " +
            "(:endDate IS NULL OR pr.returnDate <= :endDate) AND " +
            "(:search IS NULL OR LOWER(pr.returnNo) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "ORDER BY pr.returnDate DESC, pr.id DESC")
    List<PurchaseReturn> searchReturns(@Param("status") PurchaseReturn.PurchaseReturnStatus status,
                                       @Param("search") String search,
                                       @Param("startDate") LocalDate startDate,
                                       @Param("endDate") LocalDate endDate);
}
