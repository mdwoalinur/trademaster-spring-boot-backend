package com.trademaster.ims.controller;

import com.trademaster.ims.exception.ApiResponseException;
import com.trademaster.ims.model.PaymentAttachment;
import com.trademaster.ims.service.PaymentAttachmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/{paymentId}/attachments")
@CrossOrigin(origins = "http://localhost:4200")
public class PaymentAttachmentController {
    @Autowired private PaymentAttachmentService attachmentService;

    @GetMapping
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public List<PaymentAttachment> list(@PathVariable Long paymentId) {
        return attachmentService.list(paymentId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('PAYMENT_ATTACHMENT_MANAGE')")
    public ResponseEntity<?> upload(@PathVariable Long paymentId,
                                    @RequestParam("file") MultipartFile file,
                                    @RequestParam(required = false) String description) {
        try {
            return ResponseEntity.ok(attachmentService.upload(paymentId, file, description));
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @GetMapping("/{attachmentId}/download")
    @PreAuthorize("hasAuthority('PAYMENT_VIEW')")
    public ResponseEntity<?> download(@PathVariable Long paymentId, @PathVariable Long attachmentId) {
        try {
            Resource resource = attachmentService.download(paymentId, attachmentId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasAuthority('PAYMENT_ATTACHMENT_MANAGE')")
    public ResponseEntity<?> delete(@PathVariable Long paymentId, @PathVariable Long attachmentId) {
        try {
            attachmentService.delete(paymentId, attachmentId);
            return ResponseEntity.noContent().build();
        } catch (ApiResponseException ex) {
            return error(ex);
        }
    }

    private ResponseEntity<Map<String, String>> error(ApiResponseException ex) {
        return ResponseEntity.status(ex.getStatus()).body(Map.of("message", ex.getMessage()));
    }
}
