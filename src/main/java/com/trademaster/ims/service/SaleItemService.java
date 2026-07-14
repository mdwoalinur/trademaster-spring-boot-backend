package com.trademaster.ims.service;

import com.trademaster.ims.model.SaleItem;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.SaleItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class SaleItemService {

    @Autowired
    private SaleItemRepository saleItemRepository;

    public List<SaleItem> getAllSaleItems() {
        return saleItemRepository.findAll();
    }

    public SaleItem getSaleItemById(Long id) {
        return saleItemRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("SaleItem not found with id: " + id));
    }

    public List<SaleItem> getSaleItemsBySaleId(Long saleId) {
        return saleItemRepository.findBySaleId(saleId);
    }

    public List<SaleItem> getSaleItemsByProductId(Long productId) {
        return saleItemRepository.findByProductId(productId);
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "SaleItem")
    public SaleItem createSaleItem(SaleItem saleItem) {
        saleItem.setSalesItemId(null);
        // Auto-calculate discountAmount and totalPrice
        calculateDerivedFields(saleItem);
        return saleItemRepository.save(saleItem);
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "SaleItem")
    public SaleItem updateSaleItem(Long id, SaleItem saleItemDetails) {
        SaleItem existing = getSaleItemById(id);
        existing.setSaleId(saleItemDetails.getSaleId());
        existing.setProductId(saleItemDetails.getProductId());
        existing.setQuantity(saleItemDetails.getQuantity());
        existing.setUnitPrice(saleItemDetails.getUnitPrice());
        existing.setTaxRate(saleItemDetails.getTaxRate());
        existing.setDiscountPercent(saleItemDetails.getDiscountPercent());
        // Recalculate discountAmount and totalPrice
        calculateDerivedFields(existing);
        return saleItemRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "SaleItem")
    public void deleteSaleItem(Long id) {
        saleItemRepository.deleteById(id);
    }

    private void calculateDerivedFields(SaleItem item) {
        BigDecimal lineTotal = item.getUnitPrice()
                .multiply(BigDecimal.valueOf(item.getQuantity()));
        
        BigDecimal discountAmount = lineTotal
                .multiply(item.getDiscountPercent() != null ? item.getDiscountPercent() : BigDecimal.ZERO)
                .divide(BigDecimal.valueOf(100), 2, BigDecimal.ROUND_HALF_UP);
        
        item.setDiscountAmount(discountAmount);
        item.setTotalPrice(lineTotal.subtract(discountAmount));
    }
    
    @Transactional
    public List<SaleItem> createBulkSaleItems(List<SaleItem> saleItems) {
        List<SaleItem> createdItems = new ArrayList<>();
        for (SaleItem item : saleItems) {
            createdItems.add(createSaleItem(item)); // discountAmount & totalPrice অটো ক্যালকুলেট হবে
        }
        return createdItems;
    }

}
