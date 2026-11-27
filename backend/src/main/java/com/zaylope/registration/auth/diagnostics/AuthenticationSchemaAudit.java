package com.zaylope.registration.auth.diagnostics;

import com.zaylope.registration.common.validation.ApplicantInputValidator;
import com.zaylope.registration.registration.IndianMobileNumber;
import java.sql.*;
import java.util.*;

/** Metadata and aggregate counts only; never emits contact values, tokens, OTP digests or payment details. */
public final class AuthenticationSchemaAudit {
    private static final List<String> TABLES = List.of("applicants", "applicant_auth_challenges",
            "applicant_verification_codes", "applicant_access_sessions", "otp_abuse_limits",
            "applicant_onboarding_sessions", "affiliate_memberships", "payment_orders", "payment_transactions", "flyway_schema_history");
    private AuthenticationSchemaAudit() {}

    public static Map<String, Object> inspect(Connection connection, String expectedSchema) throws SQLException {
        if (!expectedSchema.equals(connection.getCatalog())) throw new IllegalArgumentException("Unexpected database target; audit refused");
        boolean originalReadOnly = connection.isReadOnly();
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setReadOnly(true);
        connection.setAutoCommit(false);
        try {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("schema", expectedSchema);
            report.put("readOnly", connection.isReadOnly());
            report.put("databaseProduct", connection.getMetaData().getDatabaseProductName());
            report.put("databaseVersion", connection.getMetaData().getDatabaseProductVersion());
            report.put("server", rows(connection, "SELECT @@hostname AS hostname, @@port AS port, DATABASE() AS schema_name"));
            var columns = rows(connection, "SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLLATION_NAME,EXTRA "
                    + "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=? ORDER BY TABLE_NAME,ORDINAL_POSITION", expectedSchema)
                    .stream().filter(row -> TABLES.contains(row.get("TABLE_NAME"))).toList();
            report.put("columns", columns);
            report.put("indexes", rows(connection, "SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME "
                    + "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=? ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX", expectedSchema)
                    .stream().filter(row -> TABLES.contains(row.get("TABLE_NAME"))).toList());
            report.put("foreignKeys", rows(connection, "SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME "
                    + "FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=? AND REFERENCED_TABLE_NAME IS NOT NULL ORDER BY TABLE_NAME,CONSTRAINT_NAME", expectedSchema)
                    .stream().filter(row -> TABLES.contains(row.get("TABLE_NAME"))).toList());
            Set<String> present = new HashSet<>();
            columns.forEach(row -> present.add((String) row.get("TABLE_NAME")));
            report.put("missingTables", TABLES.stream().filter(table -> !present.contains(table)).toList());
            Map<String, Object> counts = new LinkedHashMap<>();
            for (String table : TABLES) if (present.contains(table))
                counts.put(table, rows(connection, "SELECT COUNT(*) AS row_count FROM " + table).getFirst().get("row_count"));
            report.put("rowCounts", counts);
            if (present.contains("flyway_schema_history")) {
                report.put("flywayHistory", rows(connection, "SELECT installed_rank,version,description,type,success FROM flyway_schema_history ORDER BY installed_rank"));
            }
            Set<String> applicantColumns = new HashSet<>();
            columns.stream().filter(row -> "applicants".equals(row.get("TABLE_NAME")))
                    .forEach(row -> applicantColumns.add((String) row.get("COLUMN_NAME")));
            Map<String, Object> integrity = new LinkedHashMap<>();
            for (String key : List.of("verified_email_key", "verified_mobile_key", "google_sub")) {
                if (applicantColumns.contains(key)) integrity.put("duplicate_" + key,
                        rows(connection, "SELECT COUNT(*) AS duplicates FROM (SELECT " + key + " FROM applicants WHERE "
                                + key + " IS NOT NULL GROUP BY " + key + " HAVING COUNT(*)>1) identity_duplicates").getFirst().get("duplicates"));
            }
            if (applicantColumns.containsAll(List.of("email_address", "mobile_number"))) {
                int malformedEmail = 0, noncanonicalEmail = 0, malformedMobile = 0, noncanonicalMobile = 0;
                try (var statement = connection.prepareStatement("SELECT email_address,mobile_number FROM applicants")) {
                    statement.setQueryTimeout(5);
                    try (var contacts = statement.executeQuery()) {
                        while (contacts.next()) {
                            String email = contacts.getString(1);
                            String normalized = ApplicantInputValidator.normalizeEmail(email);
                            if (normalized == null) malformedEmail++;
                            else if (!normalized.equals(email)) noncanonicalEmail++;
                            try {
                                String mobile = contacts.getString(2);
                                if (!IndianMobileNumber.normalize(mobile).equals(mobile)) noncanonicalMobile++;
                            }
                            catch (RuntimeException invalid) { malformedMobile++; }
                        }
                    }
                }
                integrity.put("malformedEmails", malformedEmail);
                integrity.put("noncanonicalEmails", noncanonicalEmail);
                integrity.put("malformedMobiles", malformedMobile);
                integrity.put("noncanonicalMobiles", noncanonicalMobile);
            }
            report.put("identityIntegrity", integrity);
            if (applicantColumns.containsAll(List.of("email_verified_at", "email_verification_method", "mobile_verified", "approval_status"))) {
                report.put("contactStates", rows(connection, "SELECT COUNT(*) AS applicants, "
                        + "SUM(email_verified_at IS NULL) AS unverified_email, "
                        + "SUM(email_verified_at IS NOT NULL AND email_verification_method IN ('EMAIL_OTP','GOOGLE')) AS trusted_email, "
                        + "SUM(email_verified_at IS NOT NULL AND email_verification_method='LEGACY_UNKNOWN') AS legacy_email_reproof, "
                        + "SUM(mobile_verified=1) AS verified_mobile, SUM(approval_status='REJECTED') AS restricted_applicants FROM applicants"));
            }
            return report;
        } finally {
            connection.rollback(); // Audit transaction contains reads only.
            connection.setReadOnly(originalReadOnly);
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    private static List<Map<String, Object>> rows(Connection connection, String sql, Object... parameters) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(5);
            for (int index = 0; index < parameters.length; index++) statement.setObject(index + 1, parameters[index]);
            try (var result = statement.executeQuery()) {
                List<Map<String, Object>> rows = new ArrayList<>();
                var metadata = result.getMetaData();
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int column = 1; column <= metadata.getColumnCount(); column++) row.put(metadata.getColumnLabel(column), result.getObject(column));
                    rows.add(row);
                }
                return rows;
            }
        }
    }
}
