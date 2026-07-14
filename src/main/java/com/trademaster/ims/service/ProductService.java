package com.trademaster.ims.service;

import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.model.Product;
import com.trademaster.ims.repository.ProductRepository;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductService {

    private static final long MAX_PRODUCT_IMAGE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );
    private static final Path PRODUCT_UPLOAD_DIR = Paths.get("uploads", "products").toAbsolutePath().normalize();

    @Autowired
    private ProductRepository productRepository;

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    public Product getProductById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
    }
    
    @Auditable(action = "CREATE", entityType = "Product")
    @Transactional
    public Product save(Product product) {
        return productRepository.save(product);
    }

    @Auditable(action = "CREATE", entityType = "Product")
    @Transactional
    public Product save(Product product, MultipartFile image) {
        if (image != null && !image.isEmpty()) {
            product.setImageUrl(storeProductImage(image));
        }
        return productRepository.save(product);
    }
     
    @Auditable(action = "UPDATE", entityType = "Product")
    @Transactional
    public Product update(Long id, Product productDetails) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));
        
        existing.setCategoryId(productDetails.getCategoryId());
        existing.setProductCode(productDetails.getProductCode());
        existing.setSku(productDetails.getSku());
        existing.setProductName(productDetails.getProductName());
        existing.setDescription(productDetails.getDescription());
        existing.setImageUrl(productDetails.getImageUrl());
        existing.setBaseUnitId(productDetails.getBaseUnitId());
        existing.setBuyingPrice(productDetails.getBuyingPrice());
        existing.setSellingPrice(productDetails.getSellingPrice());
        existing.setTaxRate(productDetails.getTaxRate());
        existing.setMinStockLevel(productDetails.getMinStockLevel());
        existing.setMaxStockLevel(productDetails.getMaxStockLevel());
        existing.setReorderLevel(productDetails.getReorderLevel());
        existing.setSelectUnit(productDetails.getSelectUnit());
        existing.setStatus(productDetails.getStatus());
        
        return productRepository.save(existing);
    }

    @Auditable(action = "UPDATE", entityType = "Product")
    @Transactional
    public Product update(Long id, Product productDetails, MultipartFile image) {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found with id: " + id));

        applyProductDetails(existing, productDetails);
        if (image != null && !image.isEmpty()) {
            String oldImageUrl = existing.getImageUrl();
            existing.setImageUrl(storeProductImage(image));
            deleteProductImageIfOwned(oldImageUrl);
        }

        return productRepository.save(existing);
    }
    
    @Auditable(action = "DELETE", entityType = "Product")
    @Transactional
    public void deleteById(Long id) {
        productRepository.deleteById(id);
    }

    // Search products by keyword
    public List<Product> searchProducts(String keyword) {
        return productRepository.findByProductNameContainingIgnoreCaseOrProductCodeContainingIgnoreCaseOrSkuContainingIgnoreCase(
                keyword, keyword, keyword);
    }
    
    // ✅ Keep only the Optional version (used by controller)
    public Optional<Product> getProductByProductCode(String productCode) {
        return productRepository.findByProductCode(productCode);
    }
    
    public Optional<Product> getProductBySku(String sku) {
        return productRepository.findBySku(sku);
    }

    public String storeProductImage(MultipartFile image) {
        validateProductImage(image);
        try {
            Files.createDirectories(PRODUCT_UPLOAD_DIR);
            String extension = getExtension(image.getOriginalFilename());
            String filename = "product-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + "." + extension;
            Path target = PRODUCT_UPLOAD_DIR.resolve(filename).normalize();
            if (!target.startsWith(PRODUCT_UPLOAD_DIR)) {
                throw new IllegalArgumentException("Invalid product image path");
            }
            try (InputStream inputStream = image.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return "/uploads/products/" + filename;
        } catch (IOException ex) {
            throw new RuntimeException("Failed to store product image", ex);
        }
    }

    public void applyProductDetails(Product existing, Product productDetails) {
        existing.setCategoryId(productDetails.getCategoryId());
        existing.setProductCode(productDetails.getProductCode());
        existing.setSku(productDetails.getSku());
        existing.setProductName(productDetails.getProductName());
        existing.setDescription(productDetails.getDescription());
        existing.setBaseUnitId(productDetails.getBaseUnitId());
        existing.setBuyingPrice(productDetails.getBuyingPrice());
        existing.setSellingPrice(productDetails.getSellingPrice());
        existing.setTaxRate(productDetails.getTaxRate());
        existing.setMinStockLevel(productDetails.getMinStockLevel());
        existing.setMaxStockLevel(productDetails.getMaxStockLevel());
        existing.setReorderLevel(productDetails.getReorderLevel());
        existing.setSelectUnit(productDetails.getSelectUnit());
        existing.setStatus(productDetails.getStatus());
    }

    private void validateProductImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return;
        }
        if (image.getSize() > MAX_PRODUCT_IMAGE_SIZE) {
            throw new IllegalArgumentException("Product image must be 5MB or smaller");
        }
        String extension = getExtension(image.getOriginalFilename());
        String contentType = image.getContentType() == null ? "" : image.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(extension) || !ALLOWED_IMAGE_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only JPG, JPEG, PNG, and WEBP product images are allowed");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new IllegalArgumentException("Product image file extension is required");
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private void deleteProductImageIfOwned(String imageUrl) {
        if (imageUrl == null || !imageUrl.startsWith("/uploads/products/")) {
            return;
        }
        String filename = imageUrl.substring("/uploads/products/".length());
        Path target = PRODUCT_UPLOAD_DIR.resolve(filename).normalize();
        if (!target.startsWith(PRODUCT_UPLOAD_DIR)) {
            return;
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // Replacing the image should not fail only because old-file cleanup failed.
        }
    }
}
