package com.zaylope.registration.auth;

import com.zaylope.registration.auth.diagnostics.DevelopmentAuthenticationAudit;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DevelopmentAuthenticationAuditTest {
    @Test
    void diagnosticIsAbsentInProductionEvenWithDevAlsoActive() {
        for (String production : new String[] {"prod", "production"}) {
            try (var context = new AnnotationConfigApplicationContext()) {
                context.getEnvironment().setActiveProfiles("dev", production);
                context.register(DevelopmentAuthenticationAudit.class); context.refresh();
                assertTrue(context.getBeansOfType(DevelopmentAuthenticationAudit.class).isEmpty());
            }
        }
    }
    @Test
    void diagnosticIsAbsentInTestProfile() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev", "test");
            context.register(DevelopmentAuthenticationAudit.class); context.refresh();
            assertTrue(context.getBeansOfType(DevelopmentAuthenticationAudit.class).isEmpty());
        }
    }
    @Test
    void developmentAuditCanBeDisabledExplicitly() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev");
            TestPropertyValues.of("app.diagnostics.auth-audit-enabled=false").applyTo(context);
            context.register(DevelopmentAuthenticationAudit.class); context.refresh();
            assertTrue(context.getBeansOfType(DevelopmentAuthenticationAudit.class).isEmpty());
        }
    }
}
