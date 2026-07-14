package com.trademaster.ims.controller;

import com.trademaster.ims.repository.ExpenseAttachmentRepository;
import com.trademaster.ims.repository.UserRepository;
import com.trademaster.ims.util.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLConnection;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/files")
public class FileDownloadController {
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final ExpenseAttachmentRepository expenseAttachmentRepository;

    public FileDownloadController(FileStorageService fileStorageService,
                                  UserRepository userRepository,
                                  ExpenseAttachmentRepository expenseAttachmentRepository) {
        this.fileStorageService = fileStorageService;
        this.userRepository = userRepository;
        this.expenseAttachmentRepository = expenseAttachmentRepository;
    }

    @GetMapping("/profiles/{fileName:.+}")
    public ResponseEntity<Resource> serveProfile(@PathVariable String fileName) {
        if (!userRepository.existsByProfileImageUrlEndingWith("/" + fileName)) {
            return ResponseEntity.notFound().build();
        }
        return serve(FileStorageService.PROFILES, fileName, "inline");
    }

    @GetMapping("/receipts/{fileName:.+}")
    public ResponseEntity<Resource> downloadReceipt(@PathVariable String fileName) {
        if (!expenseAttachmentRepository.existsByFileName(fileName)) {
            return ResponseEntity.notFound().build();
        }
        return serve(FileStorageService.RECEIPTS, fileName, "attachment");
    }

    private ResponseEntity<Resource> serve(String directory, String fileName, String disposition) {
        try {
            Path filePath = fileStorageService.resolve(directory, fileName);
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) return ResponseEntity.notFound().build();

            String contentType = URLConnection.guessContentTypeFromName(fileName);
            if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            disposition + "; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().build();
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
