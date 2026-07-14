package com.trademaster.ims.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    public static final String PRODUCTS = "products";
    public static final String CUSTOMERS = "customers";
    public static final String SUPPLIERS = "suppliers";
    public static final String PRODUCT_VARIATIONS = "product-variations";
    public static final String PROFILES = "profiles";
    public static final String RECEIPTS = "receipts";
    public static final String PAYMENT_ATTACHMENTS = "payment-attachments";

    @Value("${app.upload.root:uploads}")
    private String uploadRoot;

    @PostConstruct
    public void initializeDirectories() throws IOException {
        for (String directory : uploadDirectories()) {
            Files.createDirectories(directory(directory));
        }
    }

   
    public String storeFile(MultipartFile file) throws IOException {
        return storeFile(file, RECEIPTS);
    }

    public String storeFile(MultipartFile file, String directory) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Cannot store empty file");
        }

        Path uploadPath = directory(directory);
        Files.createDirectories(uploadPath);

       
        String originalName = file.getOriginalFilename();
        String extension = "";
        if (originalName != null && originalName.contains(".")) {
            extension = originalName.substring(originalName.lastIndexOf("."));
        }
        String storedFileName = UUID.randomUUID().toString() + extension;

        
        Path filePath = uploadPath.resolve(storedFileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        return storedFileName;
    }

    public void store(InputStream inputStream, String directory, String fileName) throws IOException {
        Path target = resolve(directory, fileName);
        Files.createDirectories(target.getParent());
        Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
    }

    
    public void deleteFile(String fileName) throws IOException {
        deleteFile(RECEIPTS, fileName);
    }

    public void deleteFile(String directory, String fileName) throws IOException {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        Path filePath = resolve(directory, fileName);
        Files.deleteIfExists(filePath);
    }

    public String getUploadDirectoryPath() {
        return directory(RECEIPTS).toString();
    }

    public Path resolve(String directory, String fileName) {
        Path parent = directory(directory);
        Path filePath = parent.resolve(Paths.get(fileName).getFileName()).normalize();
        if (!filePath.startsWith(parent)) {
            throw new IllegalArgumentException("Invalid upload file path");
        }
        return filePath;
    }

    public Path directory(String directory) {
        if (!uploadDirectories().contains(directory)) {
            throw new IllegalArgumentException("Unsupported upload directory");
        }
        Path root = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path path = root.resolve(directory).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Invalid upload directory");
        }
        return path;
    }

    private Set<String> uploadDirectories() {
        return Set.of(PRODUCTS, CUSTOMERS, SUPPLIERS, PRODUCT_VARIATIONS,
                PROFILES, RECEIPTS, PAYMENT_ATTACHMENTS);
    }
}
