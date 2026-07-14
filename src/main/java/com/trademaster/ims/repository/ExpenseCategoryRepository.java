package com.trademaster.ims.repository;

import com.trademaster.ims.model.ExpenseCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {
    List<ExpenseCategory> findByStatusTrue();
    List<ExpenseCategory> findByParentCategoryId(Long parentId);
    Optional<ExpenseCategory> findByCategoryCode(String categoryCode);
    boolean existsByCategoryCode(String categoryCode);
}