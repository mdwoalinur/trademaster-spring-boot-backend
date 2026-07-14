package com.trademaster.ims.service;

import com.trademaster.ims.model.Customer;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.CustomerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class CustomerService {

    private static final long MAX_CUSTOMER_PHOTO_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Path CUSTOMER_UPLOAD_DIR = Paths.get("uploads", "customers").toAbsolutePath().normalize();

    @Autowired
    private CustomerRepository customerRepository;

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found with id: " + id));
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "Customer")
    public Customer createCustomer(Customer customer) {
        customer.setCustomerId(null); // ensure new record
        return customerRepository.save(customer);
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "Customer")
    public Customer createCustomer(Customer customer, MultipartFile photo) {
        customer.setCustomerId(null);
        if (photo != null && !photo.isEmpty()) {
            customer.setPhotoUrl(storeCustomerPhoto(photo));
        }
        return customerRepository.save(customer);
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "Customer")
    public Customer updateCustomer(Long id, Customer customerDetails) {
        Customer existing = getCustomerById(id);
        existing.setCustomerCode(customerDetails.getCustomerCode());
        existing.setCustomerName(customerDetails.getCustomerName());
        existing.setCustomerType(customerDetails.getCustomerType());
        existing.setEmail(customerDetails.getEmail());
        existing.setPhone(customerDetails.getPhone());
        existing.setMobile(customerDetails.getMobile());
        existing.setAddress(customerDetails.getAddress());
        existing.setCity(customerDetails.getCity());
        existing.setState(customerDetails.getState());
        existing.setPostalCode(customerDetails.getPostalCode());
        existing.setCountry(customerDetails.getCountry());
        existing.setCreditLimit(customerDetails.getCreditLimit());
        existing.setOpeningBalance(customerDetails.getOpeningBalance());
        existing.setCurrentBalance(customerDetails.getCurrentBalance());
        existing.setGstNumber(customerDetails.getGstNumber());
        existing.setPhotoUrl(customerDetails.getPhotoUrl());
        existing.setStatus(customerDetails.getStatus());
        return customerRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "Customer")
    public Customer updateCustomer(Long id, Customer customerDetails, MultipartFile photo) {
        Customer existing = getCustomerById(id);
        String oldPhotoUrl = existing.getPhotoUrl();
        applyCustomerDetails(existing, customerDetails);

        if (photo != null && !photo.isEmpty()) {
            existing.setPhotoUrl(storeCustomerPhoto(photo));
            deleteCustomerPhotoIfOwned(oldPhotoUrl);
        } else if (customerDetails.getPhotoUrl() != null && customerDetails.getPhotoUrl().isBlank()) {
            existing.setPhotoUrl(null);
            deleteCustomerPhotoIfOwned(oldPhotoUrl);
        } else {
            existing.setPhotoUrl(oldPhotoUrl);
        }

        return customerRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "Customer")
    public void deleteCustomer(Long id) {
        customerRepository.deleteById(id);
    }

    private void applyCustomerDetails(Customer existing, Customer customerDetails) {
        existing.setCustomerCode(customerDetails.getCustomerCode());
        existing.setCustomerName(customerDetails.getCustomerName());
        existing.setCustomerType(customerDetails.getCustomerType());
        existing.setEmail(customerDetails.getEmail());
        existing.setPhone(customerDetails.getPhone());
        existing.setMobile(customerDetails.getMobile());
        existing.setAddress(customerDetails.getAddress());
        existing.setCity(customerDetails.getCity());
        existing.setState(customerDetails.getState());
        existing.setPostalCode(customerDetails.getPostalCode());
        existing.setCountry(customerDetails.getCountry());
        existing.setCreditLimit(customerDetails.getCreditLimit());
        existing.setOpeningBalance(customerDetails.getOpeningBalance());
        existing.setCurrentBalance(customerDetails.getCurrentBalance());
        existing.setGstNumber(customerDetails.getGstNumber());
        existing.setStatus(customerDetails.getStatus());
    }

    private String storeCustomerPhoto(MultipartFile photo) {
        validateCustomerPhoto(photo);
        try {
            Files.createDirectories(CUSTOMER_UPLOAD_DIR);
            String extension = getExtension(photo.getOriginalFilename());
            String filename = "customer-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + "." + extension;
            Path target = CUSTOMER_UPLOAD_DIR.resolve(filename).normalize();
            if (!target.startsWith(CUSTOMER_UPLOAD_DIR)) {
                throw new IllegalArgumentException("Invalid customer photo path");
            }
            try (InputStream inputStream = photo.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return "/uploads/customers/" + filename;
        } catch (IOException ex) {
            throw new RuntimeException("Failed to store customer photo", ex);
        }
    }

    private void validateCustomerPhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return;
        }
        if (photo.getSize() > MAX_CUSTOMER_PHOTO_SIZE) {
            throw new IllegalArgumentException("Customer photo must be 5MB or smaller");
        }
        String extension = getExtension(photo.getOriginalFilename());
        String contentType = photo.getContentType() == null ? "" : photo.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension) || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only JPG, JPEG, PNG, and WEBP customer photos are allowed");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("Customer photo file extension is required");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private void deleteCustomerPhotoIfOwned(String photoUrl) {
        if (photoUrl == null || !photoUrl.startsWith("/uploads/customers/")) {
            return;
        }
        String filename = photoUrl.substring("/uploads/customers/".length());
        Path target = CUSTOMER_UPLOAD_DIR.resolve(filename).normalize();
        if (!target.startsWith(CUSTOMER_UPLOAD_DIR)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Customer updates should not fail only because old photo cleanup failed.
        }
    }
}
