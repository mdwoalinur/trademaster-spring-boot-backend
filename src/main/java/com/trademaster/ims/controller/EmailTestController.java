package com.trademaster.ims.controller;

import com.trademaster.ims.model.EmailLog;
import com.trademaster.ims.service.InvoiceEmailService;
import com.trademaster.ims.service.MailDiagnosticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/email")
@CrossOrigin(origins = "http://localhost:4200")
public class EmailTestController {

    private static final Logger log = LoggerFactory.getLogger(EmailTestController.class);

    @Autowired
    private InvoiceEmailService invoiceEmailService;

    @Autowired
    private MailDiagnosticsService mailDiagnosticsService;

    @PostMapping("/test")
    public ResponseEntity<?> sendTestEmail(@RequestParam String to) {
        try {
            EmailLog emailLog = invoiceEmailService.sendTestEmailSync(to);
            return ResponseEntity.ok(emailLog);
        } catch (Exception ex) {
            log.error("Test email sending failed", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "status", "FAILED",
                            "message", ex.getMessage() == null ? "Email sending failed" : ex.getMessage()
                    ));
        }
    }

    @GetMapping("/diagnostics")
    public ResponseEntity<?> diagnostics() {
        return ResponseEntity.ok(mailDiagnosticsService.safeDiagnostics());
    }

    @PostMapping("/test-connection")
    public ResponseEntity<?> testConnection() {
        Map<String, Object> result = mailDiagnosticsService.testConnection();
        if ("OK".equals(result.get("status"))) {
            return ResponseEntity.ok(result);
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
    }
}
