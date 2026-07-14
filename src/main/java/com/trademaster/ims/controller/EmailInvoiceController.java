package com.trademaster.ims.controller;

import com.trademaster.ims.model.EmailLog;
import com.trademaster.ims.service.InvoiceEmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/email-invoices")
@CrossOrigin(origins = "http://localhost:4200")
public class EmailInvoiceController {

    @Autowired
    private InvoiceEmailService invoiceEmailService;

    @PostMapping("/sales/{saleId}/send")
    public ResponseEntity<EmailLog> sendSaleInvoice(@PathVariable Long saleId) {
        return ResponseEntity.ok(invoiceEmailService.sendSaleInvoiceSync(saleId, true));
    }

    @PostMapping("/sales/{saleId}/resend")
    public ResponseEntity<EmailLog> resendSaleInvoice(@PathVariable Long saleId) {
        return ResponseEntity.ok(invoiceEmailService.sendSaleInvoiceSync(saleId, true));
    }

    @PostMapping("/purchases/{purchaseId}/send")
    public ResponseEntity<EmailLog> sendPurchaseOrder(@PathVariable Long purchaseId) {
        return ResponseEntity.ok(invoiceEmailService.sendPurchaseOrderSync(purchaseId, true));
    }

    @PostMapping("/purchases/{purchaseId}/resend")
    public ResponseEntity<EmailLog> resendPurchaseOrder(@PathVariable Long purchaseId) {
        return ResponseEntity.ok(invoiceEmailService.sendPurchaseOrderSync(purchaseId, true));
    }

    @GetMapping("/sales/{saleId}/logs")
    public ResponseEntity<List<EmailLog>> getSaleEmailLogs(@PathVariable Long saleId) {
        return ResponseEntity.ok(invoiceEmailService.getSaleEmailLogs(saleId));
    }

    @GetMapping("/purchases/{purchaseId}/logs")
    public ResponseEntity<List<EmailLog>> getPurchaseEmailLogs(@PathVariable Long purchaseId) {
        return ResponseEntity.ok(invoiceEmailService.getPurchaseEmailLogs(purchaseId));
    }
}
