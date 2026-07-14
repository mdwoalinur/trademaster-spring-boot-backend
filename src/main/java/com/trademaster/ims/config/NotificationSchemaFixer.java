package com.trademaster.ims.config;

import com.trademaster.ims.model.Notification;
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
public class NotificationSchemaFixer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NotificationSchemaFixer.class);

    private final JdbcTemplate jdbcTemplate;

    public NotificationSchemaFixer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureNotificationTypes();
        } catch (Exception ex) {
            log.error("Failed to verify notifications.type enum. Notifications may fail until the column is updated.", ex);
        }
    }

    private void ensureNotificationTypes() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_TYPE, IS_NULLABLE " +
                        "FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() " +
                        "AND TABLE_NAME = 'notifications' " +
                        "AND COLUMN_NAME = 'type'");

        if (columns.isEmpty()) {
            log.warn("notifications.type column not found. Skipping enum verification.");
            return;
        }

        String columnType = String.valueOf(columns.get(0).get("COLUMN_TYPE"));
        String nullable = String.valueOf(columns.get(0).get("IS_NULLABLE"));
        if (!columnType.toLowerCase(Locale.ROOT).startsWith("enum(")) {
            log.info("notifications.type is not a MySQL ENUM ({}). No enum alteration needed.", columnType);
            return;
        }

        Set<String> values = new LinkedHashSet<>(parseEnumValues(columnType));
        Set<String> requiredValues = Arrays.stream(Notification.NotificationType.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (values.containsAll(requiredValues)) {
            log.info("notifications.type already supports all application notification types: {}", requiredValues);
            return;
        }

        values.addAll(requiredValues);
        String enumSql = values.stream()
                .map(value -> "'" + value.replace("'", "''") + "'")
                .collect(Collectors.joining(", "));
        String nullSql = "YES".equalsIgnoreCase(nullable) ? " NULL" : " NOT NULL";
        String sql = "ALTER TABLE notifications MODIFY COLUMN type ENUM(" + enumSql + ")" + nullSql;

        jdbcTemplate.execute(sql);
        log.info("Updated notifications.type enum. Old column type: {}. New values: {}", columnType, values);
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
