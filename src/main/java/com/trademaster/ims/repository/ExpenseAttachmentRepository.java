package com.trademaster.ims.repository;

import com.trademaster.ims.model.ExpenseAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExpenseAttachmentRepository extends JpaRepository<ExpenseAttachment, Long> {
    List<ExpenseAttachment> findByExpense_ExpenseId(Long expenseId);
    void deleteByExpense_ExpenseId(Long expenseId);
    boolean existsByFileName(String fileName);
}
