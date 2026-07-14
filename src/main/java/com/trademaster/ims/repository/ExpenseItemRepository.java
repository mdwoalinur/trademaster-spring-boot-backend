package com.trademaster.ims.repository;

import com.trademaster.ims.model.ExpenseItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ExpenseItemRepository extends JpaRepository<ExpenseItem, Long> {
    List<ExpenseItem> findByExpense_ExpenseId(Long expenseId);
    void deleteByExpense_ExpenseId(Long expenseId);
}