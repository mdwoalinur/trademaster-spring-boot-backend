package com.trademaster.ims.service;

import com.trademaster.ims.model.Supplier;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.SupplierRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class SupplierService {

    private static final long MAX_SUPPLIER_PHOTO_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Path SUPPLIER_UPLOAD_DIR = Paths.get("uploads", "suppliers").toAbsolutePath().normalize();

    @Autowired
    private SupplierRepository supplierRepository;

    // Get all suppliers
    public List<Supplier> getAllSuppliers() {
        return supplierRepository.findAll();
    }

    // Get supplier by ID (used in update)
    public Supplier getSupplierById(Long id) {
        return supplierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + id));
    }

    // Create new supplier
    @Transactional
    @Auditable(action = "CREATE", entityType = "Supplier")
    public Supplier createSupplier(Supplier supplier) {
        validateSupplierCodeForCreate(supplier.getSupplierCode());
        supplier.setSupplierId(null); // ensure new record (ID auto-generated)
        supplier.setCreatedAt(LocalDateTime.now());
        supplier.setUpdatedAt(LocalDateTime.now());
        return supplierRepository.save(supplier);
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "Supplier")
    public Supplier createSupplier(Supplier supplier, MultipartFile photo) {
        validateSupplierCodeForCreate(supplier.getSupplierCode());
        supplier.setSupplierId(null);
        supplier.setCreatedAt(LocalDateTime.now());
        supplier.setUpdatedAt(LocalDateTime.now());
        if (photo != null && !photo.isEmpty()) {
            supplier.setPhotoUrl(storeSupplierPhoto(photo));
        }
        return supplierRepository.save(supplier);
    }

    // Update existing supplier
    @Transactional
    @Auditable(action = "UPDATE", entityType = "Supplier")
    public Supplier updateSupplier(Long id, Supplier supplierDetails) {
        Supplier existing = getSupplierById(id);
        validateSupplierCodeForUpdate(id, supplierDetails.getSupplierCode());
        applySupplierDetails(existing, supplierDetails);
        existing.setPhotoUrl(supplierDetails.getPhotoUrl());
        existing.setUpdatedAt(LocalDateTime.now());
        return supplierRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "Supplier")
    public Supplier updateSupplier(Long id, Supplier supplierDetails, MultipartFile photo) {
        Supplier existing = getSupplierById(id);
        validateSupplierCodeForUpdate(id, supplierDetails.getSupplierCode());
        String oldPhotoUrl = existing.getPhotoUrl();
        applySupplierDetails(existing, supplierDetails);

        if (photo != null && !photo.isEmpty()) {
            existing.setPhotoUrl(storeSupplierPhoto(photo));
            deleteSupplierPhotoIfOwned(oldPhotoUrl);
        } else if (supplierDetails.getPhotoUrl() != null && supplierDetails.getPhotoUrl().isBlank()) {
            existing.setPhotoUrl(null);
            deleteSupplierPhotoIfOwned(oldPhotoUrl);
        } else {
            existing.setPhotoUrl(oldPhotoUrl);
        }

        existing.setUpdatedAt(LocalDateTime.now());
        return supplierRepository.save(existing);
    }

    // Delete supplier
    @Transactional
    @Auditable(action = "DELETE", entityType = "Supplier")
    public void deleteSupplier(Long id) {
        Supplier existing = getSupplierById(id);
        supplierRepository.deleteById(id);
        deleteSupplierPhotoIfOwned(existing.getPhotoUrl());
    }

    private void applySupplierDetails(Supplier existing, Supplier supplierDetails) {
        existing.setSupplierCode(supplierDetails.getSupplierCode());
        existing.setSupplierName(supplierDetails.getSupplierName());
        existing.setContactPerson(supplierDetails.getContactPerson());
        existing.setEmail(supplierDetails.getEmail());
        existing.setPhone(supplierDetails.getPhone());
        existing.setAddress(supplierDetails.getAddress());
        existing.setCity(supplierDetails.getCity());
        existing.setState(supplierDetails.getState());
        existing.setPostalCode(supplierDetails.getPostalCode());
        existing.setCountry(supplierDetails.getCountry());
        existing.setPaymentTerms(supplierDetails.getPaymentTerms());
        existing.setGstNumber(supplierDetails.getGstNumber());
        existing.setStatus(supplierDetails.getStatus());
    }

    private void validateSupplierCodeForCreate(String supplierCode) {
        if (supplierCode != null && !supplierCode.isBlank() && supplierRepository.existsBySupplierCode(supplierCode)) {
            throw new IllegalArgumentException("Supplier code already exists: " + supplierCode);
        }
    }

    private void validateSupplierCodeForUpdate(Long id, String supplierCode) {
        if (supplierCode == null || supplierCode.isBlank()) {
            return;
        }
        supplierRepository.findBySupplierCode(supplierCode)
                .filter(existing -> !existing.getSupplierId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Supplier code already exists: " + supplierCode);
                });
    }

    private String storeSupplierPhoto(MultipartFile photo) {
        validateSupplierPhoto(photo);
        try {
            Files.createDirectories(SUPPLIER_UPLOAD_DIR);
            String extension = getExtension(photo.getOriginalFilename());
            String filename = "supplier-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + "." + extension;
            Path target = SUPPLIER_UPLOAD_DIR.resolve(filename).normalize();
            if (!target.startsWith(SUPPLIER_UPLOAD_DIR)) {
                throw new IllegalArgumentException("Invalid supplier photo path");
            }
            try (InputStream inputStream = photo.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return "/uploads/suppliers/" + filename;
        } catch (IOException ex) {
            throw new RuntimeException("Failed to store supplier photo", ex);
        }
    }

    private void validateSupplierPhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            return;
        }
        if (photo.getSize() > MAX_SUPPLIER_PHOTO_SIZE) {
            throw new IllegalArgumentException("Supplier photo/logo must be 5MB or smaller");
        }
        String extension = getExtension(photo.getOriginalFilename());
        String contentType = photo.getContentType() == null ? "" : photo.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension) || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only JPG, JPEG, PNG, and WEBP supplier photos/logos are allowed");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("Supplier photo/logo file extension is required");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private void deleteSupplierPhotoIfOwned(String photoUrl) {
        if (photoUrl == null || !photoUrl.startsWith("/uploads/suppliers/")) {
            return;
        }
        String filename = photoUrl.substring("/uploads/suppliers/".length());
        Path target = SUPPLIER_UPLOAD_DIR.resolve(filename).normalize();
        if (!target.startsWith(SUPPLIER_UPLOAD_DIR)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Supplier updates should not fail only because old photo cleanup failed.
        }
    }
}
