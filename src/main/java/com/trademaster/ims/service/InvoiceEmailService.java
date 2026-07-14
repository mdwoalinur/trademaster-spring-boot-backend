package com.trademaster.ims.service;

import com.trademaster.ims.model.*;
import com.trademaster.ims.repository.*;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class InvoiceEmailService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceEmailService.class);

    @Autowired private JavaMailSender mailSender;
    @Autowired private EmailLogRepository emailLogRepository;
    @Autowired private SaleRepository saleRepository;
    @Autowired private SaleItemRepository saleItemRepository;
    @Autowired private PurchaseRepository purchaseRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private SupplierRepository supplierRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private UserRepository userRepository;

    private static final DateTimeFormatter EMAIL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);

    @Value("${app.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${spring.mail.host:}")
    private String mailHost;

    @Value("${spring.mail.port:0}")
    private int mailPort;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${app.email.from:}")
    private String fromEmail;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.email.company-name:TradeMaster}")
    private String companyName;

    @Value("${app.email.company-phone:}")
    private String companyPhone;

    @Value("${app.email.company-address:}")
    private String companyAddress;

    @Value("${app.email.support-email:}")
    private String supportEmail;

    @Value("${app.email.attach-pdf:false}")
    private boolean attachPdf;

    @Async
    public void sendSaleInvoiceAsync(Long saleId, boolean manualResend) {
        try {
            sendSaleInvoiceSync(saleId, manualResend);
        } catch (Exception ex) {
            log.error("Async sales invoice email sending failed for saleId={}", saleId, ex);
        }
    }

    @Async
    public void sendPurchaseOrderAsync(Long purchaseId, boolean manualResend) {
        try {
            sendPurchaseOrderSync(purchaseId, manualResend);
        } catch (Exception ex) {
            log.error("Async purchase order email sending failed for purchaseId={}", purchaseId, ex);
        }
    }

    @Transactional(noRollbackFor = Exception.class)
    public EmailLog sendSaleInvoiceSync(Long saleId, boolean manualResend) {
        EmailLog log = null;
        try {
            Sale sale = saleRepository.findById(saleId)
                    .orElseThrow(() -> new RuntimeException("Sale not found with id: " + saleId));
            Customer customer = customerRepository.findById(sale.getCustomerId())
                    .orElseThrow(() -> new RuntimeException("Customer not found with id: " + sale.getCustomerId()));

            String subject = "Sales Invoice - " + sale.getInvoiceNo() + " - " + companyName;
            log = createPendingLog(EmailLog.EmailType.SALE_INVOICE, sale.getSaleId(), sale.getInvoiceNo(),
                    customer.getEmail(), customer.getCustomerName(), subject, manualResend);
            String recipientEmail = requireEmail(customer.getEmail(), "Customer email is missing for sale invoice " + sale.getInvoiceNo());
            log.setRecipientEmail(recipientEmail);
            log.setSenderEmail(resolveSenderEmail());

            String html = buildSaleInvoiceHtml(sale, customer, saleItemRepository.findBySaleId(sale.getSaleId()));
            return sendHtmlEmail(log, html, sale.getInvoiceNo());
        } catch (Exception ex) {
            markFailed(log, ex);
            InvoiceEmailService.log.error("Email sending failed", ex);
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }

    @Transactional(noRollbackFor = Exception.class)
    public EmailLog sendPurchaseOrderSync(Long purchaseId, boolean manualResend) {
        EmailLog log = null;
        try {
            Purchase purchase = purchaseRepository.findById(purchaseId)
                    .orElseThrow(() -> new RuntimeException("Purchase not found with id: " + purchaseId));
            Supplier supplier = supplierRepository.findById(purchase.getSupplierId())
                    .orElseThrow(() -> new RuntimeException("Supplier not found with id: " + purchase.getSupplierId()));

            String subject = "Purchase Order - " + purchase.getPurchaseOrderNo() + " - " + companyName;
            log = createPendingLog(EmailLog.EmailType.PURCHASE_ORDER, purchase.getPurchaseId(), purchase.getPurchaseOrderNo(),
                    supplier.getEmail(), supplier.getSupplierName(), subject, manualResend);
            String recipientEmail = requireEmail(supplier.getEmail(), "Supplier email is missing for purchase order " + purchase.getPurchaseOrderNo());
            log.setRecipientEmail(recipientEmail);
            log.setSenderEmail(resolveSenderEmail());

            String html = buildPurchaseOrderHtml(purchase, supplier);
            return sendHtmlEmail(log, html, purchase.getPurchaseOrderNo());
        } catch (Exception ex) {
            markFailed(log, ex);
            InvoiceEmailService.log.error("Email sending failed", ex);
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }

    public EmailLog sendTestEmailSync(String to) {
        EmailLog log = null;
        try {
            String recipientEmail = requireEmail(to, "Receiver email is required");
            String subject = companyName + " SMTP Test Email";
            log = createPendingLog(EmailLog.EmailType.TEST_EMAIL, 0L, "TEST-" + System.currentTimeMillis(),
                    recipientEmail, "SMTP Test Receiver", subject, true);
            log.setSenderEmail(resolveSenderEmail());
            String body = "SMTP test email from " + companyName + ". If you received this, Gmail SMTP is working.";
            return sendPlainTextEmail(log, body);
        } catch (Exception ex) {
            markFailed(log, ex);
            InvoiceEmailService.log.error("Email sending failed", ex);
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }

    public List<EmailLog> getSaleEmailLogs(Long saleId) {
        return emailLogRepository.findByEmailTypeAndReferenceIdOrderByCreatedAtDesc(EmailLog.EmailType.SALE_INVOICE, saleId);
    }

    public List<EmailLog> getPurchaseEmailLogs(Long purchaseId) {
        return emailLogRepository.findByEmailTypeAndReferenceIdOrderByCreatedAtDesc(EmailLog.EmailType.PURCHASE_ORDER, purchaseId);
    }

    private EmailLog createPendingLog(EmailLog.EmailType type, Long referenceId, String referenceNo,
                                      String recipientEmail, String recipientName, String subject, boolean manualResend) {
        EmailLog log = new EmailLog();
        log.setEmailType(type);
        log.setReferenceId(referenceId);
        log.setReferenceNo(referenceNo);
        log.setRecipientEmail(StringUtils.hasText(recipientEmail) ? recipientEmail.trim() : "MISSING_EMAIL");
        log.setRecipientName(recipientName);
        log.setSubject(subject);
        log.setManualResend(manualResend);
        log.setStatus(EmailLog.EmailStatus.PENDING);
        return emailLogRepository.save(log);
    }

    private EmailLog sendHtmlEmail(EmailLog log, String html, String documentNo) throws Exception {
        log.setAttemptCount(log.getAttemptCount() + 1);
        log.setUpdatedAt(LocalDateTime.now());

        validateMailConfiguration(log);
        logMailAttempt(log);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, attachPdf, "UTF-8");
        helper.setFrom(log.getSenderEmail());
        helper.setTo(log.getRecipientEmail());
        helper.setSubject(log.getSubject());
        helper.setText(html, true);

        if (attachPdf) {
            byte[] pdfBytes = buildSimplePdfBytes(log, documentNo);
            helper.addAttachment(documentNo + ".pdf", () -> new java.io.ByteArrayInputStream(pdfBytes));
        }

        mailSender.send(message);
        return markSent(log);
    }

    private EmailLog sendPlainTextEmail(EmailLog log, String body) throws Exception {
        log.setAttemptCount(log.getAttemptCount() + 1);
        log.setUpdatedAt(LocalDateTime.now());

        validateMailConfiguration(log);
        logMailAttempt(log);

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(log.getSenderEmail());
        helper.setTo(log.getRecipientEmail());
        helper.setSubject(log.getSubject());
        helper.setText(body, false);

        mailSender.send(message);
        return markSent(log);
    }

    private EmailLog markSent(EmailLog emailLog) {
        emailLog.setStatus(EmailLog.EmailStatus.SENT);
        emailLog.setLastError(null);
        emailLog.setErrorMessage(null);
        emailLog.setSentAt(LocalDateTime.now());
        emailLog.setUpdatedAt(LocalDateTime.now());
        EmailLog saved = emailLogRepository.save(emailLog);
        log.info("Email sent successfully. emailLogId={}, type={}, to={}, subject={}",
                saved.getEmailLogId(), saved.getEmailType(), saved.getRecipientEmail(), saved.getSubject());
        return saved;
    }

    private void markFailed(EmailLog log, Exception ex) {
        if (log == null) {
            log = new EmailLog();
            log.setEmailType(EmailLog.EmailType.SALE_INVOICE);
            log.setReferenceId(0L);
            log.setRecipientEmail("unknown");
            log.setSenderEmail(resolveSenderEmailOrUnknown());
            log.setSubject("Invoice email");
        }
        log.setStatus(EmailLog.EmailStatus.FAILED);
        log.setAttemptCount(log.getAttemptCount() == null || log.getAttemptCount() == 0 ? 1 : log.getAttemptCount());
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName();
        log.setLastError(message);
        log.setErrorMessage(message);
        log.setUpdatedAt(LocalDateTime.now());
        emailLogRepository.save(log);
    }

    private String requireEmail(String email, String message) {
        if (!StringUtils.hasText(email)) {
            throw new RuntimeException(message);
        }
        return email.trim();
    }

    private void validateMailConfiguration(EmailLog log) {
        if (!emailEnabled) {
            throw new RuntimeException("Email sending is disabled. Set TRADEMASTER_EMAIL_ENABLED=true.");
        }
        if (!StringUtils.hasText(mailHost)) {
            throw new RuntimeException("SMTP host is missing. Expected smtp.gmail.com.");
        }
        if (mailPort <= 0) {
            throw new RuntimeException("SMTP port is missing. Expected 587.");
        }
        if (!StringUtils.hasText(mailUsername)) {
            throw new RuntimeException("spring.mail.username is missing. Set TRADEMASTER_MAIL_USERNAME.");
        }
        if (!StringUtils.hasText(mailPassword)) {
            throw new RuntimeException("Gmail App Password is missing. Set TRADEMASTER_MAIL_PASSWORD to a Gmail App Password, not the normal Gmail login password. Gmail 2-Step Verification must be enabled.");
        }
        if (!StringUtils.hasText(log.getSenderEmail())) {
            throw new RuntimeException("Sender email is missing. Set TRADEMASTER_EMAIL_FROM or TRADEMASTER_MAIL_USERNAME.");
        }
        if (!StringUtils.hasText(log.getRecipientEmail()) || "MISSING_EMAIL".equals(log.getRecipientEmail())) {
            throw new RuntimeException("Receiver email is missing.");
        }
    }

    private String resolveSenderEmail() {
        String sender = StringUtils.hasText(fromEmail) ? fromEmail.trim() : mailUsername;
        if (!StringUtils.hasText(sender)) {
            throw new RuntimeException("Sender email is missing. Set TRADEMASTER_EMAIL_FROM or TRADEMASTER_MAIL_USERNAME.");
        }
        return sender.trim();
    }

    private String resolveSenderEmailOrUnknown() {
        try {
            return resolveSenderEmail();
        } catch (Exception ex) {
            return "unknown";
        }
    }

    private void logMailAttempt(EmailLog emailLog) {
        log.info("Preparing email send. enabled={}, smtpHost={}, smtpPort={}, sender={}, receiver={}, subject={}, emailType={}",
                emailEnabled,
                mailHost,
                mailPort,
                emailLog.getSenderEmail(),
                emailLog.getRecipientEmail(),
                emailLog.getSubject(),
                emailLog.getEmailType());
    }

    private String buildSaleInvoiceHtml(Sale sale, Customer customer, List<SaleItem> items) {
        try {
            String warehouseName = getWarehouseName(sale.getWarehouseId());
            String soldBy = getUserName(sale.getUserId());
            StringBuilder rows = new StringBuilder();
            int index = 1;
            for (SaleItem item : items) {
                Product product = getProduct(item.getProductId());
                rows.append("<tr>")
                        .append(td(String.valueOf(index++), "center"))
                        .append(td(getProductName(product), "left"))
                        .append(td(getSkuOrBarcode(product), "left"))
                        .append(td(getCategoryName(product), "left"))
                        .append(td(warehouseName, "left"))
                        .append(td(String.valueOf(nullToZero(item.getQuantity())), "right"))
                        .append(td(formatCurrency(item.getUnitPrice()), "right"))
                        .append(td(formatCurrency(item.getDiscountAmount()), "right"))
                        .append(td(formatPercent(item.getTaxRate()), "right"))
                        .append(td(formatCurrency(item.getTotalPrice()), "right"))
                        .append("</tr>");
            }

            String info = twoColumnSection(
                    "Invoice Information",
                    labelValue("Invoice No", sale.getInvoiceNo()) +
                            labelValue("Sale Date", formatDate(sale.getSaleDate())) +
                            labelValue("Sale Status", statusBadge(safeText(sale.getStatus()))) +
                            labelValue("Payment Status", statusBadge(safeText(sale.getPaymentStatus()))) +
                            labelValue("Sold By", soldBy),
                    "Customer Information",
                    labelValue("Customer Name", safeText(customer.getCustomerName())) +
                            labelValue("Email", safeText(customer.getEmail())) +
                            labelValue("Phone", firstText(customer.getPhone(), customer.getMobile())) +
                            labelValue("Address", buildAddress(customer.getAddress(), customer.getCity(), customer.getState(), customer.getPostalCode(), customer.getCountry()))
            );

            String table = productTable(rows.toString());
            String summary = amountSummary(
                    new String[][]{
                            {"Subtotal", formatCurrency(sale.getSubtotal())},
                            {"Total Discount", formatCurrency(sale.getDiscountAmount())},
                            {"Total Tax", formatCurrency(sale.getTaxAmount())},
                            {"Grand Total", formatCurrency(sale.getTotalAmount())},
                            {"Paid Amount", formatCurrency(sale.getPaidAmount())},
                            {"Due Amount", formatCurrency(sale.getDueAmount())},
                            {"Change Amount", "N/A"}
                    }
            );

            return documentTemplate("Sales Invoice", "Sales Invoice - " + safeText(sale.getInvoiceNo()), info + table + summary);
        } catch (Exception ex) {
            log.error("Sales invoice template generation failed for saleId={}", sale != null ? sale.getSaleId() : null, ex);
            throw ex;
        }
    }

    private String buildPurchaseOrderHtml(Purchase purchase, Supplier supplier) {
        try {
            String warehouseName = getWarehouseName(purchase.getWarehouseId());
            String createdBy = getUserName(purchase.getUserId());
            StringBuilder rows = new StringBuilder();
            BigDecimal totalDiscount = BigDecimal.ZERO;
            int index = 1;
            for (PurchaseItem item : purchase.getItems()) {
                Product product = getProduct(item.getProductId());
                BigDecimal discount = item.getDiscountAmount();
                totalDiscount = totalDiscount.add(nullToZero(discount));
                rows.append("<tr>")
                        .append(td(String.valueOf(index++), "center"))
                        .append(td(getProductName(product), "left"))
                        .append(td(getSkuOrBarcode(product), "left"))
                        .append(td(getCategoryName(product), "left"))
                        .append(td(warehouseName, "left"))
                        .append(td(String.valueOf(nullToZero(item.getQuantity())), "right"))
                        .append(td(formatCurrency(item.getUnitPrice()), "right"))
                        .append(td(formatCurrency(discount), "right"))
                        .append(td(formatPercent(item.getTax()), "right"))
                        .append(td(formatCurrency(item.getTotalPrice()), "right"))
                        .append("</tr>");
            }

            String info = twoColumnSection(
                    "Purchase Order Information",
                    labelValue("Purchase Order No", purchase.getPurchaseOrderNo()) +
                            labelValue("Purchase Date", formatDate(purchase.getPurchaseDate())) +
                            labelValue("Expected Delivery Date", formatDate(purchase.getExpectedDelivery())) +
                            labelValue("Purchase Status", statusBadge(safeText(purchase.getStatus()))) +
                            labelValue("Payment Status", "N/A") +
                            labelValue("Created By", createdBy),
                    "Supplier Information",
                    labelValue("Supplier Name", safeText(supplier.getSupplierName())) +
                            labelValue("Email", safeText(supplier.getEmail())) +
                            labelValue("Phone", safeText(supplier.getPhone())) +
                            labelValue("Address", buildAddress(supplier.getAddress(), supplier.getCity(), supplier.getState(), supplier.getPostalCode(), supplier.getCountry()))
            );

            String table = productTable(rows.toString());
            String summary = amountSummary(
                    new String[][]{
                            {"Subtotal", formatCurrency(purchase.getSubtotal())},
                            {"Total Discount", formatCurrency(totalDiscount)},
                            {"Total Tax", formatCurrency(purchase.getTaxAmount())},
                            {"Shipping/Other Cost", "N/A"},
                            {"Grand Total", formatCurrency(purchase.getTotalAmount())},
                            {"Paid Amount", "N/A"},
                            {"Due Amount", "N/A"}
                    }
            );

            return documentTemplate("Purchase Order", "Purchase Order - " + safeText(purchase.getPurchaseOrderNo()), info + table + summary);
        } catch (Exception ex) {
            log.error("Purchase order template generation failed for purchaseId={}", purchase != null ? purchase.getPurchaseId() : null, ex);
            throw ex;
        }
    }

    private String legacyBuildSaleInvoiceHtml(Sale sale, Customer customer, List<SaleItem> items) {
        StringBuilder rows = new StringBuilder();
        for (SaleItem item : items) {
            rows.append("<tr>")
                    .append(cell(String.valueOf(item.getProductId())))
                    .append(cell(String.valueOf(item.getQuantity())))
                    .append(cell(formatMoney(item.getUnitPrice())))
                    .append(cell(formatMoney(item.getTotalPrice())))
                    .append("</tr>");
        }
        return baseTemplate("Sales Invoice", sale.getInvoiceNo(), customer.getCustomerName(),
                "<p><strong>Sale Date:</strong> " + safe(sale.getSaleDate()) + "</p>" +
                        table(new String[]{"Product ID", "Qty", "Unit Price", "Total"}, rows.toString()) +
                        totals(sale.getSubtotal(), sale.getTaxAmount(), sale.getDiscountAmount(), sale.getTotalAmount()));
    }

    private String legacyBuildPurchaseOrderHtml(Purchase purchase, Supplier supplier) {
        StringBuilder rows = new StringBuilder();
        for (PurchaseItem item : purchase.getItems()) {
            rows.append("<tr>")
                    .append(cell(String.valueOf(item.getProductId())))
                    .append(cell(String.valueOf(item.getQuantity())))
                    .append(cell(formatMoney(item.getUnitPrice())))
                    .append(cell(formatMoney(item.getSubtotal())))
                    .append("</tr>");
        }
        return baseTemplate("Purchase Order", purchase.getPurchaseOrderNo(), supplier.getSupplierName(),
                "<p><strong>Purchase Date:</strong> " + safe(purchase.getPurchaseDate()) + "</p>" +
                        "<p><strong>Expected Delivery:</strong> " + safe(purchase.getExpectedDelivery()) + "</p>" +
                        table(new String[]{"Product ID", "Qty", "Unit Price", "Subtotal"}, rows.toString()) +
                        totals(purchase.getSubtotal(), purchase.getTaxAmount(), BigDecimal.ZERO, purchase.getTotalAmount()));
    }

    private String documentTemplate(String emailTitle, String documentTitle, String content) {
        String support = firstText(supportEmail, fromEmail, mailUsername);
        return "<!doctype html><html><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width,initial-scale=1.0'></head>" +
                "<body style='margin:0;background:#eef2f7;font-family:Arial,Helvetica,sans-serif;color:#172033;'>" +
                "<div style='width:100%;background:#eef2f7;padding:24px 10px;'>" +
                "<div style='max-width:980px;margin:0 auto;background:#ffffff;border:1px solid #dbe3ee;border-radius:10px;overflow:hidden;'>" +
                header(emailTitle) +
                "<div style='padding:24px;'>" +
                "<h2 style='margin:0 0 18px;color:#172033;font-size:22px;letter-spacing:0;'>" + escape(documentTitle) + "</h2>" +
                content +
                "<div style='margin-top:28px;padding:18px;background:#f8fafc;border:1px solid #e5e7eb;border-radius:8px;color:#475569;font-size:13px;line-height:1.6;'>" +
                "<p style='margin:0 0 6px;font-weight:700;color:#172033;'>Thank you for working with " + escape(companyName) + ".</p>" +
                "<p style='margin:0;'>This is an auto-generated email from TradeMaster Inventory and Stock Management System.</p>" +
                "<p style='margin:6px 0 0;'>For support, contact " + escape(support) + "</p>" +
                "</div>" +
                "</div></div></div></body></html>";
    }

    private String header(String emailTitle) {
        return "<div style='background:#0d6efd;color:#ffffff;padding:22px 24px;'>" +
                "<table style='width:100%;border-collapse:collapse;'><tr>" +
                "<td style='vertical-align:top;'>" +
                "<div style='font-size:26px;font-weight:800;letter-spacing:.2px;'>TradeMaster</div>" +
                "<div style='font-size:13px;opacity:.92;margin-top:4px;'>Inventory and Stock Management System</div>" +
                "</td>" +
                "<td style='vertical-align:top;text-align:right;font-size:13px;line-height:1.6;'>" +
                "<div style='display:inline-block;background:rgba(255,255,255,.16);border:1px solid rgba(255,255,255,.25);border-radius:999px;padding:5px 12px;font-weight:700;margin-bottom:6px;'>" + escape(emailTitle) + "</div>" +
                "<div>" + escape(companyName) + "</div>" +
                optionalLine(firstText(fromEmail, mailUsername)) +
                optionalLine(companyPhone) +
                optionalLine(companyAddress) +
                "</td></tr></table></div>";
    }

    private String twoColumnSection(String leftTitle, String leftBody, String rightTitle, String rightBody) {
        return "<table style='width:100%;border-collapse:collapse;margin-bottom:20px;'><tr>" +
                "<td style='width:50%;vertical-align:top;padding-right:10px;'>" + infoCard(leftTitle, leftBody) + "</td>" +
                "<td style='width:50%;vertical-align:top;padding-left:10px;'>" + infoCard(rightTitle, rightBody) + "</td>" +
                "</tr></table>";
    }

    private String infoCard(String title, String body) {
        return "<div style='border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;background:#ffffff;'>" +
                "<div style='background:#f8fafc;border-bottom:1px solid #e5e7eb;padding:10px 12px;font-weight:700;color:#172033;'>" + escape(title) + "</div>" +
                "<div style='padding:12px;'>" + body + "</div></div>";
    }

    private String labelValue(String label, String value) {
        return "<div style='margin-bottom:7px;font-size:13px;line-height:1.45;'>" +
                "<span style='display:inline-block;min-width:150px;color:#64748b;'>" + escape(label) + ":</span>" +
                "<span style='font-weight:600;color:#172033;'>" + value + "</span></div>";
    }

    private String productTable(String rows) {
        return "<div style='overflow-x:auto;margin-top:18px;'>" +
                "<table style='width:100%;border-collapse:collapse;font-size:12px;'>" +
                "<thead><tr>" +
                th("SL No", "center") +
                th("Product Name", "left") +
                th("SKU / Barcode", "left") +
                th("Category", "left") +
                th("Warehouse", "left") +
                th("Quantity", "right") +
                th("Unit Price", "right") +
                th("Discount", "right") +
                th("Tax", "right") +
                th("Line Total", "right") +
                "</tr></thead><tbody>" +
                (StringUtils.hasText(rows) ? rows : "<tr><td colspan='10' style='padding:14px;border:1px solid #e5e7eb;text-align:center;color:#64748b;'>No items found</td></tr>") +
                "</tbody></table></div>";
    }

    private String amountSummary(String[][] rows) {
        StringBuilder html = new StringBuilder();
        html.append("<div style='margin-top:20px;text-align:right;'><div style='display:inline-block;min-width:320px;border:1px solid #dbe3ee;border-radius:8px;overflow:hidden;background:#ffffff;text-align:left;'>");
        html.append("<div style='background:#f8fafc;border-bottom:1px solid #dbe3ee;padding:10px 14px;font-weight:800;color:#172033;'>Amount Summary</div>");
        for (String[] row : rows) {
            boolean grandTotal = row[0].toLowerCase(Locale.ROOT).contains("grand total");
            html.append("<div style='display:flex;justify-content:space-between;gap:16px;padding:9px 14px;border-bottom:1px solid #edf2f7;")
                    .append(grandTotal ? "background:#eef6ff;font-size:16px;font-weight:800;" : "")
                    .append("'>")
                    .append("<span style='color:#64748b;'>").append(escape(row[0])).append("</span>")
                    .append("<span style='color:#172033;font-weight:700;'>").append(escape(row[1])).append("</span>")
                    .append("</div>");
        }
        html.append("</div></div>");
        return html.toString();
    }

    private String th(String value, String align) {
        return "<th style='padding:10px 8px;border:1px solid #cbd5e1;background:#eaf1fb;color:#172033;text-align:" + align + ";font-weight:800;white-space:nowrap;'>" + escape(value) + "</th>";
    }

    private String td(String value, String align) {
        return "<td style='padding:9px 8px;border:1px solid #e5e7eb;text-align:" + align + ";vertical-align:top;color:#263445;'>" + escape(safeText(value)) + "</td>";
    }

    private String statusBadge(String value) {
        String safe = safeText(value);
        String bg = safe.equalsIgnoreCase("PAID") || safe.equalsIgnoreCase("COMPLETED") || safe.equalsIgnoreCase("RECEIVED") ? "#dcfce7" :
                safe.equalsIgnoreCase("PENDING") || safe.equalsIgnoreCase("PARTIAL") ? "#fef3c7" :
                        safe.equalsIgnoreCase("CANCELLED") || safe.equalsIgnoreCase("UNPAID") ? "#fee2e2" : "#e5e7eb";
        String color = safe.equalsIgnoreCase("PAID") || safe.equalsIgnoreCase("COMPLETED") || safe.equalsIgnoreCase("RECEIVED") ? "#166534" :
                safe.equalsIgnoreCase("PENDING") || safe.equalsIgnoreCase("PARTIAL") ? "#92400e" :
                        safe.equalsIgnoreCase("CANCELLED") || safe.equalsIgnoreCase("UNPAID") ? "#991b1b" : "#374151";
        return "<span style='display:inline-block;padding:3px 10px;border-radius:999px;background:" + bg + ";color:" + color + ";font-size:12px;font-weight:800;'>" + escape(safe) + "</span>";
    }

    private String optionalLine(String value) {
        return StringUtils.hasText(value) ? "<div>" + escape(value.trim()) + "</div>" : "";
    }

    private String formatCurrency(BigDecimal amount) {
        return amount == null ? "BDT 0.00" : "BDT " + amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String formatDate(LocalDate date) {
        return date == null ? "N/A" : date.format(EMAIL_DATE_FORMAT);
    }

    private String formatDate(LocalDateTime dateTime) {
        return dateTime == null ? "N/A" : dateTime.toLocalDate().format(EMAIL_DATE_FORMAT);
    }

    private String safeText(Object value) {
        if (value == null) return "N/A";
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) ? text : "N/A";
    }

    private Product getProduct(Long productId) {
        if (productId == null) return null;
        return productRepository.findById(productId).orElse(null);
    }

    private String getProductName(Product product) {
        return product == null ? "N/A" : safeText(product.getProductName());
    }

    private String getSkuOrBarcode(Product product) {
        if (product == null) return "N/A";
        return firstText(product.getSku(), product.getProductCode());
    }

    private String getCategoryName(Product product) {
        if (product == null || product.getCategoryId() == null) return "N/A";
        return categoryRepository.findById(product.getCategoryId())
                .map(Category::getCategoryName)
                .filter(StringUtils::hasText)
                .orElse("N/A");
    }

    private String getWarehouseName(Long warehouseId) {
        if (warehouseId == null) return "N/A";
        return warehouseRepository.findById(warehouseId)
                .map(Warehouse::getName)
                .filter(StringUtils::hasText)
                .orElse("N/A");
    }

    private String getUserName(Long userId) {
        if (userId == null) return "N/A";
        return userRepository.findById(userId)
                .map(user -> firstText(user.getFullName(), user.getUsername()))
                .orElse("N/A");
    }

    private String buildAddress(String address, String city, String state, String postalCode, String country) {
        StringBuilder builder = new StringBuilder();
        appendAddressPart(builder, address);
        appendAddressPart(builder, city);
        appendAddressPart(builder, state);
        appendAddressPart(builder, postalCode);
        appendAddressPart(builder, country);
        return builder.length() == 0 ? "N/A" : escape(builder.toString());
    }

    private void appendAddressPart(StringBuilder builder, String part) {
        if (StringUtils.hasText(part)) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(part.trim());
        }
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) return value.trim();
        }
        return "N/A";
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private String formatPercent(BigDecimal value) {
        if (value == null) return "N/A";
        return value.setScale(2, java.math.RoundingMode.HALF_UP) + "%";
    }

    private String baseTemplate(String title, String documentNo, String recipientName, String body) {
        return "<!doctype html><html><body style='font-family:Arial,sans-serif;color:#172033;background:#f6f8fb;padding:24px;'>" +
                "<div style='max-width:760px;margin:auto;background:#fff;border:1px solid #e5e7eb;border-radius:8px;overflow:hidden;'>" +
                "<div style='background:#0d6efd;color:#fff;padding:18px 22px;'><h2 style='margin:0;'>" + escape(companyName) + "</h2></div>" +
                "<div style='padding:22px;'><h3 style='margin-top:0;'>" + escape(title) + " #" + escape(documentNo) + "</h3>" +
                "<p>Dear " + escape(recipientName) + ",</p><p>Please find your " + escape(title.toLowerCase(Locale.ROOT)) + " details below.</p>" +
                body +
                "<p style='margin-top:24px;'>Thank you,<br>" + escape(companyName) + "</p></div></div></body></html>";
    }

    private String table(String[] headers, String rows) {
        StringBuilder th = new StringBuilder();
        for (String header : headers) {
            th.append("<th style='padding:10px;border:1px solid #dbe3ee;background:#eef2f7;text-align:left;'>").append(escape(header)).append("</th>");
        }
        return "<table style='width:100%;border-collapse:collapse;margin-top:16px;'><thead><tr>" + th + "</tr></thead><tbody>" + rows + "</tbody></table>";
    }

    private String totals(BigDecimal subtotal, BigDecimal tax, BigDecimal discount, BigDecimal total) {
        return "<div style='margin-top:18px;text-align:right;'>" +
                "<p><strong>Subtotal:</strong> " + formatMoney(subtotal) + "</p>" +
                "<p><strong>Tax:</strong> " + formatMoney(tax) + "</p>" +
                "<p><strong>Discount:</strong> " + formatMoney(discount) + "</p>" +
                "<p style='font-size:18px;'><strong>Total:</strong> " + formatMoney(total) + "</p>" +
                "</div>";
    }

    private String cell(String value) {
        return "<td style='padding:10px;border:1px solid #e5e7eb;'>" + escape(value) + "</td>";
    }

    private String formatMoney(BigDecimal value) {
        return value == null ? "BDT 0.00" : "BDT " + value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private String safe(Object value) {
        return value == null ? "-" : escape(String.valueOf(value));
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private byte[] buildSimplePdfBytes(EmailLog log, String documentNo) {
        String text = log.getSubject() + "\\nDocument: " + documentNo + "\\nRecipient: " + log.getRecipientName();
        String escapedText = text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)").replace("\n", ") Tj T* (");
        String stream = "BT /F1 14 Tf 50 760 Td (" + escapedText + ") Tj ET";
        String[] objects = new String[] {
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
                "<< /Length " + stream.length() + " >>\nstream\n" + stream + "\nendstream"
        };
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        int[] offsets = new int[objects.length + 1];
        for (int i = 0; i < objects.length; i++) {
            offsets[i + 1] = pdf.length();
            pdf.append(i + 1).append(" 0 obj\n").append(objects[i]).append("\nendobj\n");
        }
        int xref = pdf.length();
        pdf.append("xref\n0 ").append(objects.length + 1).append("\n0000000000 65535 f \n");
        for (int i = 1; i <= objects.length; i++) {
            pdf.append(String.format("%010d 00000 n \n", offsets[i]));
        }
        pdf.append("trailer << /Size ").append(objects.length + 1).append(" /Root 1 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF");
        return pdf.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
}
