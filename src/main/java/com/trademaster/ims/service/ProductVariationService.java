package com.trademaster.ims.service;

import com.trademaster.ims.model.ProductVariation;
import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.repository.ProductVariationRepository;
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
public class ProductVariationService {

    private static final long MAX_VARIATION_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Path VARIATION_UPLOAD_DIR = Paths.get("uploads", "product-variations").toAbsolutePath().normalize();

    @Autowired
    private ProductVariationRepository productVariationRepository;

    public List<ProductVariation> getAllVariations() {
        return productVariationRepository.findAll();
    }

    public ProductVariation getVariationById(Long id) {
        return productVariationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Variation not found with id: " + id));
    }

    public List<ProductVariation> getVariationsByProductId(Long productId) {
        return productVariationRepository.findByProductId(productId);
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "ProductVariation")
    public ProductVariation createVariation(ProductVariation variation) {
        validateSkuForCreate(variation.getSku());
        variation.setVariationId(null);
        variation.setCreatedAt(LocalDateTime.now());
        variation.setUpdatedAt(LocalDateTime.now());
        return productVariationRepository.save(variation);
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "ProductVariation")
    public ProductVariation createVariation(ProductVariation variation, MultipartFile image) {
        validateSkuForCreate(variation.getSku());
        variation.setVariationId(null);
        variation.setCreatedAt(LocalDateTime.now());
        variation.setUpdatedAt(LocalDateTime.now());
        if (image != null && !image.isEmpty()) {
            variation.setImageUrl(storeVariationImage(image));
        }
        return productVariationRepository.save(variation);
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "ProductVariation")
    public ProductVariation updateVariation(Long id, ProductVariation variationDetails) {
        ProductVariation existing = getVariationById(id);
        validateSkuForUpdate(id, variationDetails.getSku());
        applyVariationDetails(existing, variationDetails);
        if (variationDetails.getImageUrl() != null) {
            existing.setImageUrl(variationDetails.getImageUrl().isBlank() ? null : variationDetails.getImageUrl());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        return productVariationRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "UPDATE", entityType = "ProductVariation")
    public ProductVariation updateVariation(Long id, ProductVariation variationDetails, MultipartFile image) {
        ProductVariation existing = getVariationById(id);
        validateSkuForUpdate(id, variationDetails.getSku());
        String oldImageUrl = existing.getImageUrl();
        applyVariationDetails(existing, variationDetails);

        if (image != null && !image.isEmpty()) {
            existing.setImageUrl(storeVariationImage(image));
            deleteVariationImageIfOwned(oldImageUrl);
        } else if (variationDetails.getImageUrl() != null && variationDetails.getImageUrl().isBlank()) {
            existing.setImageUrl(null);
            deleteVariationImageIfOwned(oldImageUrl);
        } else {
            existing.setImageUrl(oldImageUrl);
        }

        existing.setUpdatedAt(LocalDateTime.now());
        return productVariationRepository.save(existing);
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "ProductVariation")
    public void deleteVariation(Long id) {
        ProductVariation existing = getVariationById(id);
        productVariationRepository.deleteById(id);
        deleteVariationImageIfOwned(existing.getImageUrl());
    }

    private void applyVariationDetails(ProductVariation existing, ProductVariation variationDetails) {
        existing.setProductId(variationDetails.getProductId());
        existing.setVariationName(variationDetails.getVariationName());
        existing.setSku(variationDetails.getSku());
        existing.setBuyingPrice(variationDetails.getBuyingPrice());
        existing.setAdditionalPrice(variationDetails.getAdditionalPrice());
        existing.setStatus(variationDetails.getStatus());
    }

    private void validateSkuForCreate(String sku) {
        if (sku != null && !sku.isBlank() && productVariationRepository.existsBySku(sku)) {
            throw new IllegalArgumentException("Variation SKU already exists: " + sku);
        }
    }

    private void validateSkuForUpdate(Long variationId, String sku) {
        if (sku == null || sku.isBlank()) {
            return;
        }
        if (productVariationRepository.existsBySkuAndVariationIdNot(sku, variationId)) {
            throw new IllegalArgumentException("Variation SKU already exists: " + sku);
        }
    }

    private String storeVariationImage(MultipartFile image) {
        validateVariationImage(image);
        try {
            Files.createDirectories(VARIATION_UPLOAD_DIR);
            String extension = getExtension(image.getOriginalFilename());
            String filename = "variation-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + "." + extension;
            Path target = VARIATION_UPLOAD_DIR.resolve(filename).normalize();
            if (!target.startsWith(VARIATION_UPLOAD_DIR)) {
                throw new IllegalArgumentException("Invalid variation image path");
            }
            try (InputStream inputStream = image.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return "/uploads/product-variations/" + filename;
        } catch (IOException ex) {
            throw new RuntimeException("Failed to store variation image", ex);
        }
    }

    private void validateVariationImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return;
        }
        if (image.getSize() > MAX_VARIATION_IMAGE_SIZE) {
            throw new IllegalArgumentException("Variation image must be 5MB or smaller");
        }
        String extension = getExtension(image.getOriginalFilename());
        String contentType = image.getContentType() == null ? "" : image.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension) || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only JPG, JPEG, PNG, and WEBP variation images are allowed");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("Variation image file extension is required");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private void deleteVariationImageIfOwned(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("/uploads/product-variations/")) {
            return;
        }
        String filename = imageUrl.substring("/uploads/product-variations/".length());
        Path target = VARIATION_UPLOAD_DIR.resolve(filename).normalize();
        if (!target.startsWith(VARIATION_UPLOAD_DIR)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Variation save/delete should not fail only because old image cleanup failed.
        }
    }
}
