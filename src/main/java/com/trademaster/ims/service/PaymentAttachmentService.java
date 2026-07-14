package com.trademaster.ims.service;

import com.trademaster.ims.annotation.Auditable;
import com.trademaster.ims.exception.ApiResponseException;
import com.trademaster.ims.model.Payment;
import com.trademaster.ims.model.PaymentAttachment;
import com.trademaster.ims.repository.PaymentAttachmentRepository;
import com.trademaster.ims.repository.PaymentRepository;
import com.trademaster.ims.security.AuthContextService;
import com.trademaster.ims.util.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentAttachmentService {
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg", "webp", "doc", "docx", "xls", "xlsx");
    private static final Set<String> ALLOWED_MIME_PREFIXES = Set.of("image/", "application/pdf", "application/msword", "application/vnd.openxmlformats", "application/vnd.ms-excel");
    private static final long MAX_SIZE = 5L * 1024L * 1024L;

    @Autowired private PaymentAttachmentRepository attachmentRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private AuthContextService authContextService;
    @Autowired private FileStorageService fileStorageService;

    public List<PaymentAttachment> list(Long paymentId) {
        return attachmentRepository.findByPaymentIdAndDeletedFalseOrderByUploadedAtDesc(paymentId);
    }

    @Transactional
    @Auditable(action = "CREATE", entityType = "PaymentAttachment")
    public PaymentAttachment upload(Long paymentId, MultipartFile file, String description) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (file == null || file.isEmpty()) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Attachment file is required");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Attachment size cannot exceed 5MB");
        }
        String original = sanitize(file.getOriginalFilename());
        String extension = extension(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Unsupported attachment type");
        }
        String mimeType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        if (ALLOWED_MIME_PREFIXES.stream().noneMatch(mimeType::startsWith)) {
            throw new ApiResponseException(HttpStatus.BAD_REQUEST, "Unsupported attachment MIME type");
        }
        String stored = "payment-" + paymentId + "-" + UUID.randomUUID() + "." + extension;
        try {
            fileStorageService.store(file.getInputStream(), FileStorageService.PAYMENT_ATTACHMENTS, stored);
        } catch (IOException ex) {
            throw new ApiResponseException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not store payment attachment");
        }
        PaymentAttachment attachment = new PaymentAttachment();
        attachment.setPaymentId(payment.getPaymentId());
        attachment.setOriginalFileName(original);
        attachment.setStoredFileName(stored);
        attachment.setFileUrl("/uploads/payments/" + stored);
        attachment.setMimeType(mimeType);
        attachment.setFileSize(file.getSize());
        attachment.setDescription(description);
        attachment.setUploadedBy(authContextService.getCurrentUserId());
        attachment.setUploadedAt(LocalDateTime.now());
        return attachmentRepository.save(attachment);
    }

    public Resource download(Long paymentId, Long attachmentId) {
        PaymentAttachment attachment = attachmentRepository.findByAttachmentIdAndPaymentIdAndDeletedFalse(attachmentId, paymentId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Payment attachment not found"));
        Path file = fileStorageService.resolve(FileStorageService.PAYMENT_ATTACHMENTS, attachment.getStoredFileName());
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ApiResponseException(HttpStatus.NOT_FOUND, "Attachment file not found");
            }
            return resource;
        } catch (MalformedURLException ex) {
            throw new ApiResponseException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not read attachment file");
        }
    }

    @Transactional
    @Auditable(action = "DELETE", entityType = "PaymentAttachment")
    public void delete(Long paymentId, Long attachmentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (payment.getTransactionStatus() == Payment.PaymentTransactionStatus.POSTED) {
            throw new ApiResponseException(HttpStatus.CONFLICT, "Posted payment attachments cannot be deleted");
        }
        PaymentAttachment attachment = attachmentRepository.findByAttachmentIdAndPaymentIdAndDeletedFalse(attachmentId, paymentId)
                .orElseThrow(() -> new ApiResponseException(HttpStatus.NOT_FOUND, "Payment attachment not found"));
        attachment.setDeleted(true);
        attachmentRepository.save(attachment);
    }

    private String sanitize(String originalName) {
        String name = originalName == null || originalName.isBlank() ? "attachment" : Paths.get(originalName).getFileName().toString();
        return name.replaceAll("[^A-Za-z0-9._ -]", "_");
    }

    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index >= 0 ? fileName.substring(index + 1).toLowerCase(Locale.ROOT) : "";
    }
}
