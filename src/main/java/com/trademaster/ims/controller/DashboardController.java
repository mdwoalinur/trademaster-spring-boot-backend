package com.trademaster.ims.controller;

import com.trademaster.ims.model.Purchase;
import com.trademaster.ims.model.Sale;
import com.trademaster.ims.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "http://localhost:4200")
public class DashboardController {

    @Autowired
    private DashboardService dashboardService;

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return dashboardService.getStats();
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        return dashboardService.getSummary();
    }

    @GetMapping("/sales-trend")
    public Map<String, Object> getSalesTrend() {
        return dashboardService.getSalesTrend();
    }

    @GetMapping("/sales-analytics")
    public Map<String, Object> getSalesAnalytics(@RequestParam(defaultValue = "monthly") String period) {
        return dashboardService.getSalesAnalytics(period);
    }

    @GetMapping("/profit-trend")
    public Map<String, Object> getProfitTrend() {
        return dashboardService.getProfitTrend();
    }

    @GetMapping("/stock-movement")
    public Map<String, Object> getStockMovement(@RequestParam(defaultValue = "monthly") String period) {
        return dashboardService.getStockMovement(period);
    }

    @GetMapping("/warehouse-stock-value")
    public ResponseEntity<List<Map<String, Object>>> getWarehouseStockValue() {
        return ResponseEntity.ok(dashboardService.getWarehouseStockValue());
    }

    @GetMapping("/profit-overview")
    public Map<String, Object> getProfitOverview(@RequestParam(defaultValue = "monthly") String period) {
        return dashboardService.getProfitOverview(period);
    }

    @GetMapping("/top-products")
    public Map<String, Object> getTopProducts() {
        return dashboardService.getTopProducts();
    }
    
    @GetMapping("/top-customers")
    public ResponseEntity<List<Map<String, Object>>> getTopCustomers(@RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(dashboardService.getTopCustomers(limit));
    }
    
    @GetMapping("/recent-sales")
    public ResponseEntity<List<Sale>> getRecentSales(@RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(dashboardService.getRecentSales(limit));
    }

    @GetMapping("/low-stock-alerts")
    public ResponseEntity<List<Map<String, Object>>> getLowStockAlerts() {
        return ResponseEntity.ok(dashboardService.getLowStockAlerts());
    }

    @GetMapping("/out-of-stock")
    public ResponseEntity<List<Map<String, Object>>> getOutOfStock() {
        return ResponseEntity.ok(dashboardService.getOutOfStock());
    }

    @GetMapping("/recent-stock-movements")
    public ResponseEntity<List<Map<String, Object>>> getRecentStockMovements(@RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(dashboardService.getRecentStockMovements(limit));
    }

    @GetMapping("/top-selling-products")
    public ResponseEntity<List<Map<String, Object>>> getTopSellingProducts(@RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(dashboardService.getTopSellingProducts(limit));
    }

    @GetMapping("/recent-purchase-orders")
    public ResponseEntity<List<Map<String, Object>>> getRecentPurchaseOrders(@RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(dashboardService.getRecentPurchaseOrders(limit));
    }

    @GetMapping("/recent-purchases")
    public ResponseEntity<List<Purchase>> getRecentPurchases(@RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(dashboardService.getRecentPurchases(limit));
    }
}
