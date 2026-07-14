package com.trademaster.ims.controller;

import com.trademaster.ims.annotation.Auditable;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trademaster.ims.model.Product;
import com.trademaster.ims.repository.ProductRepository;
import com.trademaster.ims.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/products")
@CrossOrigin(origins = "http://localhost:4200")
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${file.upload-dir}")
    public String mypro;
    
    // Create a new product
    @Auditable(action = "CREATE", entityType = "Product")
    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
    	
    	System.out.println(mypro);
        Product savedProduct = productRepository.save(product);
        return new ResponseEntity<>(savedProduct, HttpStatus.CREATED);
    }

    @Auditable(action = "CREATE", entityType = "Product")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createProductWithImage(
            @RequestPart("product") MultipartFile productPart,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        log.info("Product multipart create received. productPart={}, imagePart={}",
                productPart != null && !productPart.isEmpty(), image != null && !image.isEmpty());
        try {
            Product product = parseProductJson(readProductPart(productPart));
            Product savedProduct = productService.save(product, image);
            return new ResponseEntity<>(savedProduct, HttpStatus.CREATED);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Product create failed", ex);
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "Product create failed: " + ex.getMessage()));
        }
    }

    // Get all products
    @GetMapping
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @GetMapping("/search")
    public List<Product> searchProducts(@RequestParam(name = "q", defaultValue = "") String keyword) {
        String trimmedKeyword = keyword.trim();
        if (trimmedKeyword.isEmpty()) {
            return productRepository.findAll();
        }
        return productService.searchProducts(trimmedKeyword);
    }

    // Get product by ID
    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        Optional<Product> product = productRepository.findById(id);
        return product.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Update existing product
    @Auditable(action = "UPDATE", entityType = "Product")
    @PutMapping("/{id}")
    public ResponseEntity<Product> updateProduct(@PathVariable Long id, @RequestBody Product productDetails) {
        Optional<Product> existingProduct = productRepository.findById(id);
        if (existingProduct.isPresent()) {
            Product product = existingProduct.get();
            
            //  Converted to camelCase setter methods
            product.setProductCode(productDetails.getProductCode());
            product.setSku(productDetails.getSku());
            product.setProductName(productDetails.getProductName());
            product.setDescription(productDetails.getDescription());
            product.setImageUrl(productDetails.getImageUrl());
            product.setBaseUnitId(productDetails.getBaseUnitId());
            product.setBuyingPrice(productDetails.getBuyingPrice());
            product.setSellingPrice(productDetails.getSellingPrice());
            product.setTaxRate(productDetails.getTaxRate());
            product.setMinStockLevel(productDetails.getMinStockLevel());
            product.setMaxStockLevel(productDetails.getMaxStockLevel());
            product.setReorderLevel(productDetails.getReorderLevel());
            product.setSelectUnit(productDetails.getSelectUnit());
            product.setStatus(productDetails.getStatus());
            
            Product updated = productRepository.save(product);
            return ResponseEntity.ok(updated);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @Auditable(action = "UPDATE", entityType = "Product")
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updateProductWithImage(
            @PathVariable Long id,
            @RequestPart("product") MultipartFile productPart,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        log.info("Product multipart update received. productId={}, productPart={}, imagePart={}",
                id, productPart != null && !productPart.isEmpty(), image != null && !image.isEmpty());
        try {
            Product productDetails = parseProductJson(readProductPart(productPart));
            Product updated = productService.update(id, productDetails, image);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Product not found")) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.badRequest().body(java.util.Map.of("message", ex.getMessage()));
        } catch (Exception ex) {
            log.error("Product update failed for id {}", id, ex);
            return ResponseEntity.badRequest().body(java.util.Map.of("message", "Product update failed: " + ex.getMessage()));
        }
    }

    // Delete product
    @Auditable(action = "DELETE", entityType = "Product")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    @GetMapping("/by-code/{code}")
    public ResponseEntity<Product> getProductByCode(@PathVariable String code) {
        return productService.getProductByProductCode(code)
                .map(ResponseEntity::ok)
                .orElseGet(() -> productService.getProductBySku(code)
                        .map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build()));
    }

    private Product parseProductJson(String productJson) {
        if (productJson == null || productJson.isBlank()) {
            throw new IllegalArgumentException("Missing product part");
        }

        try {
            JsonNode node = objectMapper.readTree(productJson);
            Product product = new Product();
            product.setCategoryId(longValue(node, "categoryId"));
            product.setProductCode(textValue(node, "productCode"));
            product.setSku(textValue(node, "sku"));
            product.setProductName(textValue(node, "productName"));
            product.setDescription(textValue(node, "description"));
            product.setImageUrl(textValue(node, "imageUrl"));
            product.setBaseUnitId(longValue(node, "baseUnitId"));
            product.setBuyingPrice(decimalValue(node, "buyingPrice"));
            product.setSellingPrice(decimalValue(node, "sellingPrice"));
            product.setTaxRate(decimalValue(node, "taxRate"));
            product.setMinStockLevel(integerValue(node, "minStockLevel"));
            product.setMaxStockLevel(integerValue(node, "maxStockLevel"));
            product.setReorderLevel(integerValue(node, "reorderLevel"));
            product.setSelectUnit(textValue(node, "selectUnit"));

            String status = textValue(node, "status");
            if (status != null && !status.isBlank()) {
                product.setStatus(Product.ProductStatus.valueOf(status));
            }
            return product;
        } catch (JsonProcessingException ex) {
            log.error("Invalid product JSON", ex);
            throw new IllegalArgumentException("Invalid product JSON");
        } catch (IllegalArgumentException ex) {
            log.error("Invalid product data", ex);
            throw ex;
        }
    }

    private String readProductPart(MultipartFile productPart) {
        if (productPart == null || productPart.isEmpty()) {
            throw new IllegalArgumentException("Missing product part");
        }
        try {
            return new String(productPart.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            log.error("Could not read product multipart part", ex);
            throw new IllegalArgumentException("Could not read product part");
        }
    }

    private String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private Long longValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asLong();
    }

    private Integer integerValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return value.asInt();
    }

    private BigDecimal decimalValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            return null;
        }
        return new BigDecimal(value.asText());
    }
}
