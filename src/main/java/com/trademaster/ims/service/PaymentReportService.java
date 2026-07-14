package com.trademaster.ims.service;

import com.trademaster.ims.model.AccountLedgerEntry;
import com.trademaster.ims.model.PartyLedgerEntry;
import com.trademaster.ims.model.Payment;
import com.trademaster.ims.repository.AccountLedgerEntryRepository;
import com.trademaster.ims.repository.PartyLedgerEntryRepository;
import com.trademaster.ims.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class PaymentReportService {
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private AccountLedgerEntryRepository accountLedgerRepository;
    @Autowired private PartyLedgerEntryRepository partyLedgerRepository;

    public Map<String, Object> paymentRegister(LocalDate startDate, LocalDate endDate, String type, String direction, String method, Pageable pageable) {
        LocalDateTime start = (startDate == null ? LocalDate.now().withDayOfMonth(1) : startDate).atStartOfDay();
        LocalDateTime end = (endDate == null ? LocalDate.now() : endDate).atTime(23, 59, 59);
        Page<Payment> page = paymentRepository.findByPaymentDateBetween(start, end, pageable);
        var filtered = page.getContent().stream()
                .filter(p -> type == null || type.isBlank() || p.getPaymentType().name().equals(type))
                .filter(p -> direction == null || direction.isBlank() || p.getDirection().name().equals(direction))
                .filter(p -> method == null || method.isBlank() || p.getPaymentMethod().name().equals(method))
                .toList();
        BigDecimal requested = filtered.stream().map(p -> nvl(p.getRequestedAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal approved = filtered.stream().map(p -> nvl(p.getApprovedAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("payments", filtered);
        result.put("totalCount", filtered.size());
        result.put("requestedTotal", requested);
        result.put("approvedTotal", approved);
        result.put("startDate", startDate);
        result.put("endDate", endDate);
        return result;
    }

    public Page<AccountLedgerEntry> accountStatement(Long accountId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        if (startDate != null && endDate != null) {
            return accountLedgerRepository.findByAccountIdAndPostedAtBetweenOrderByPostedAtDesc(accountId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59), pageable);
        }
        return accountLedgerRepository.findByAccountIdOrderByPostedAtDesc(accountId, pageable);
    }

    public Page<PartyLedgerEntry> partyStatement(PartyLedgerEntry.PartyType partyType, Long partyId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        if (startDate != null && endDate != null) {
            return partyLedgerRepository.findByPartyTypeAndPartyIdAndPostedAtBetweenOrderByPostedAtDesc(partyType, partyId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59), pageable);
        }
        return partyLedgerRepository.findByPartyTypeAndPartyIdOrderByPostedAtDesc(partyType, partyId, pageable);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
