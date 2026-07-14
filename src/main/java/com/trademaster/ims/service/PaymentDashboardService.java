package com.trademaster.ims.service;

import com.trademaster.ims.model.BankStatementEntry;
import com.trademaster.ims.model.FinancialAccount;
import com.trademaster.ims.model.Payment;
import com.trademaster.ims.repository.BankStatementEntryRepository;
import com.trademaster.ims.repository.FinancialAccountRepository;
import com.trademaster.ims.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PaymentDashboardService {
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private FinancialAccountRepository accountRepository;
    @Autowired(required = false) private BankStatementEntryRepository statementRepository;

    public Map<String, Object> dashboard(LocalDate startDate, LocalDate endDate) {
        LocalDate today = LocalDate.now();
        LocalDate start = startDate == null ? today.withDayOfMonth(1) : startDate;
        LocalDate end = endDate == null ? today : endDate;
        LocalDateTime startAt = start.atStartOfDay();
        LocalDateTime endAt = end.atTime(23, 59, 59);

        List<Payment> rangePayments = paymentRepository.findByPaymentDateBetween(startAt, endAt, PageRequest.of(0, 5000)).getContent();
        List<Payment> posted = rangePayments.stream()
                .filter(p -> p.getApprovalStatus() == Payment.PaymentApprovalStatus.APPROVED)
                .filter(p -> p.getTransactionStatus() == Payment.PaymentTransactionStatus.POSTED)
                .toList();
        List<Payment> pending = paymentRepository.findByApprovalStatus(Payment.PaymentApprovalStatus.PENDING_APPROVAL, PageRequest.of(0, 5000)).getContent();
        List<FinancialAccount> accounts = accountRepository.findAll();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("todayReceived", sum(posted.stream().filter(p -> sameDay(p.getPaymentDate(), today)).filter(p -> p.getDirection() == Payment.PaymentDirection.RECEIVE).toList()));
        result.put("todayPaid", sum(posted.stream().filter(p -> sameDay(p.getPaymentDate(), today)).filter(p -> p.getDirection() == Payment.PaymentDirection.PAY || p.getDirection() == Payment.PaymentDirection.REFUND).toList()));
        result.put("monthReceived", sum(posted.stream().filter(p -> p.getDirection() == Payment.PaymentDirection.RECEIVE).toList()));
        result.put("monthPaid", sum(posted.stream().filter(p -> p.getDirection() == Payment.PaymentDirection.PAY || p.getDirection() == Payment.PaymentDirection.REFUND).toList()));
        result.put("pendingApprovalCount", pending.size());
        result.put("pendingApprovalAmount", sumRequested(pending));
        result.put("approvedToday", posted.stream().filter(p -> sameDay(p.getApprovedAt(), today)).count());
        result.put("cashBalance", sumAccounts(accounts, FinancialAccount.AccountType.CASH));
        result.put("bankBalance", sumAccounts(accounts, FinancialAccount.AccountType.BANK));
        result.put("mobileBalance", sumAccounts(accounts, FinancialAccount.AccountType.MOBILE_BANKING));
        result.put("unreconciledCount", statementRepository == null ? 0 : statementRepository.countByStatus(BankStatementEntry.ReconciliationStatus.UNMATCHED));
        result.put("refundAmountThisMonth", sum(posted.stream().filter(p -> p.getDirection() == Payment.PaymentDirection.REFUND).toList()));
        result.put("voidedAmountThisMonth", sum(rangePayments.stream().filter(p -> p.getTransactionStatus() == Payment.PaymentTransactionStatus.VOIDED).toList()));
        result.put("methodDistribution", posted.stream().collect(Collectors.groupingBy(p -> p.getPaymentMethod().name(), LinkedHashMap::new, Collectors.reducing(BigDecimal.ZERO, p -> nvl(p.getApprovedAmount()), BigDecimal::add))));
        result.put("approvalDistribution", rangePayments.stream().collect(Collectors.groupingBy(p -> p.getApprovalStatus().name(), LinkedHashMap::new, Collectors.counting())));
        result.put("accountBalances", accounts.stream().map(a -> Map.of(
                "accountId", a.getAccountId(),
                "accountName", a.getAccountName(),
                "accountType", a.getAccountType(),
                "balance", nvl(a.getCurrentBalance())
        )).toList());
        result.put("recentPayments", paymentRepository.findAll(PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("paymentDate").descending())).getContent());
        result.put("pendingApprovals", pending.stream().limit(10).toList());
        result.put("periodStart", start);
        result.put("periodEnd", end);
        return result;
    }

    private BigDecimal sum(List<Payment> payments) {
        return payments.stream().map(p -> nvl(p.getApprovedAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumRequested(List<Payment> payments) {
        return payments.stream().map(p -> nvl(p.getRequestedAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal sumAccounts(List<FinancialAccount> accounts, FinancialAccount.AccountType type) {
        return accounts.stream().filter(a -> a.getAccountType() == type).map(a -> nvl(a.getCurrentBalance())).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean sameDay(LocalDateTime value, LocalDate day) {
        return value != null && value.toLocalDate().equals(day);
    }
}
