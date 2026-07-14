package com.trademaster.ims.controller;

import com.trademaster.ims.model.StockMovement;
import com.trademaster.ims.service.StockMovementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock-movements")
@CrossOrigin(origins = "http://localhost:4200")
public class StockMovementController {

    @Autowired
    private StockMovementService movementService;

    @GetMapping
    public List<StockMovement> getAllMovements() {
        return movementService.getAllMovements();
    }

    @GetMapping("/{id}")
    public ResponseEntity<StockMovement> getMovementById(@PathVariable Long id) {
        return ResponseEntity.ok(movementService.getMovementById(id));
    }

    @GetMapping("/product/{productId}")
    public List<StockMovement> getMovementsByProduct(@PathVariable Long productId) {
        return movementService.getMovementsByProduct(productId);
    }

    @GetMapping("/warehouse/{warehouseId}")
    public List<StockMovement> getMovementsByWarehouse(@PathVariable Long warehouseId) {
        return movementService.getMovementsByWarehouse(warehouseId);
    }

    @GetMapping("/type/{type}")
    public List<StockMovement> getMovementsByType(@PathVariable StockMovement.MovementType type) {
        return movementService.getMovementsByType(type);
    }

    @PostMapping
    public ResponseEntity<StockMovement> createMovement(@RequestBody StockMovement movement) {
        StockMovement created = movementService.createMovement(movement);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }
}