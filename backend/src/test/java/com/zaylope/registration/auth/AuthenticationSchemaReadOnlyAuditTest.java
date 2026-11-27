package com.zaylope.registration.auth;

import com.zaylope.registration.auth.diagnostics.AuthenticationSchemaAudit;
import java.net.URI;
import java.nio.file.*;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Explicit read-only main-schema audit. No Spring context, Flyway initialization or application writes. */
@EnabledIfEnvironmentVariable(named = "AUTH_SCHEMA_AUDIT", matches = "true")
class AuthenticationSchemaReadOnlyAuditTest {
    @Test
    void inspectApprovedMainSchemaUsingReadOnlyMetadataAndAggregateQueries() throws Exception {
        String url = System.getenv("DB_URL");
        if (url == null || !url.startsWith("jdbc:mysql://") || !"/zaylope".equals(URI.create(url.substring(5)).getPath()))
            throw new IllegalStateException("Explicit JDBC target must be the approved zaylope schema");
        String username = System.getenv("DB_USERNAME");
        String password = System.getenv("DB_PASSWORD");
        if (username == null || password == null) throw new IllegalStateException("Read-only database credentials are unavailable");
        try (var connection = DriverManager.getConnection(url, username, password)) {
            var report = AuthenticationSchemaAudit.inspect(connection, "zaylope");
            Files.writeString(Path.of("target/auth-main-schema-audit.json"),
                    new tools.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report));
        }
    }
}
