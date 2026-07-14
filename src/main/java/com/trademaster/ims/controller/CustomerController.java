package com.trademaster.ims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trademaster.ims.model.Customer;
import com.trademaster.ims.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
@CrossOrigin(origins = "http://localhost:4200")
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    @Autowired
    private CustomerService customerService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping
    public List<Customer> getAllCustomers() {
        return customerService.getAllCustomers();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getCustomerById(id));
    }

    @PostMapping
    public ResponseEntity<Customer> createCustomer(@RequestBody Customer customer) {
        Customer created = customerService.createCustomer(customer);
        return new ResponseEntity<>(created, HttpStatus.CREATED);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createCustomerWithPhoto(
            @RequestPart("customer") MultipartFile customerPart,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        try {
            Customer customer = parseCustomerJson(readCustomerPart(customerPart));
            Customer created = customerService.createCustomer(customer, photo);
            return new ResponseEntity<>(created, HttpStatus.CREATED);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Customer create failed", ex);
            return ResponseEntity.badRequest().body(Map.of("message", "Customer create failed: " + ex.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<Customer> updateCustomer(@PathVariable Long id, @RequestBody Customer customer) {
        Customer updated = customerService.updateCustomer(id, customer);
        return ResponseEntity.ok(updated);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateCustomerWithPhoto(
            @PathVariable Long id,
            @RequestPart("customer") MultipartFile customerPart,
            @RequestPart(value = "photo", required = false) MultipartFile photo) {
        try {
            Customer customer = parseCustomerJson(readCustomerPart(customerPart));
            Customer updated = customerService.updateCustomer(id, customer, photo);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Customer not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Customer update failed for id {}", id, ex);
            return ResponseEntity.badRequest().body(Map.of("message", "Customer update failed: " + ex.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }

    private Customer parseCustomerJson(String customerJson) {
        if (customerJson == null || customerJson.isBlank()) {
            throw new IllegalArgumentException("Missing customer part");
        }
        try {
            return objectMapper.readValue(customerJson, Customer.class);
        } catch (Exception ex) {
            log.error("Invalid customer JSON", ex);
            throw new IllegalArgumentException("Invalid customer JSON");
        }
    }

    private String readCustomerPart(MultipartFile customerPart) {
        if (customerPart == null || customerPart.isEmpty()) {
            throw new IllegalArgumentException("Missing customer part");
        }
        try {
            return new String(customerPart.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.error("Could not read customer multipart part", ex);
            throw new IllegalArgumentException("Could not read customer part");
        }
    }
}
