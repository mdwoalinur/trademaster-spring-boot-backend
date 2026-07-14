package com.trademaster.ims.config;

import com.trademaster.ims.model.Payment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PaymentSchemaFixer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PaymentSchemaFixer.class);

    private final JdbcTemplate jdbcTemplate;

    public PaymentSchemaFixer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureEnumColumn("payments", "payment_type", enumNames(Payment.PaymentType.values()));
            ensureEnumColumn("payments", "payment_method", enumNames(Payment.PaymentMethod.values()));
            ensureEnumColumn("payments", "payment_status", enumNames(Payment.PaymentStatus.values()));
        } catch (Exception ex) {
            log.error("Failed to verify payment enum columns. Payment requests may fail until the schema is updated.", ex);
        }
    }

    private Set<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values)
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void ensureEnumColumn(String tableName, String columnName, Set<String> requiredValues) {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                tableName, columnName);

        if (columns.isEmpty()) {
            log.warn("{}.{} not found. Skipping enum verification.", tableName, columnName);
            return;
        }

        String columnType = String.valueOf(columns.get(0).get("COLUMN_TYPE"));
        String nullable = String.valueOf(columns.get(0).get("IS_NULLABLE"));
        if (!columnType.toLowerCase(Locale.ROOT).startsWith("enum(")) {
            log.info("{}.{} is not a MySQL ENUM ({}). No enum alteration needed.", tableName, columnName, columnType);
            return;
        }

        Set<String> values = new LinkedHashSet<>(parseEnumValues(columnType));
        if (values.containsAll(requiredValues)) {
            log.info("{}.{} already supports all application values: {}", tableName, columnName, requiredValues);
            return;
        }

        values.addAll(requiredValues);
        String enumSql = values.stream()
                .map(value -> "'" + value.replace("'", "''") + "'")
                .collect(Collectors.joining(", "));
        String nullSql = "YES".equalsIgnoreCase(nullable) ? " NULL" : " NOT NULL";
        String sql = "ALTER TABLE " + tableName + " MODIFY COLUMN " + columnName + " ENUM(" + enumSql + ")" + nullSql;

        jdbcTemplate.execute(sql);
        log.info("Updated {}.{} enum. Old column type: {}. New values: {}", tableName, columnName, columnType, values);
    }

    private List<String> parseEnumValues(String columnType) {
        String body = columnType.substring(columnType.indexOf('(') + 1, columnType.lastIndexOf(')'));
        List<String> values = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuote = false;

        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '\'') {
                if (inQuote && i + 1 < body.length() && body.charAt(i + 1) == '\'') {
                    current.append('\'');
                    i++;
                } else {
                    inQuote = !inQuote;
                }
            } else if (ch == ',' && !inQuote) {
                values.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }

        if (current.length() > 0) {
            values.add(current.toString());
        }
        return values;
    }
}
