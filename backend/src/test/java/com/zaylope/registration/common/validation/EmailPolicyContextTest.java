package com.zaylope.registration.common.validation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import static org.junit.jupiter.api.Assertions.*;

class EmailPolicyContextTest {
    @Test
    void approvedPolicyBindsAndLoadsInRealSpringContext() {
        try (var context = new AnnotationConfigApplicationContext(PolicyConfiguration.class)) {
            var properties = context.getBean(EmailPolicyProperties.class);
            assertTrue(properties.allowlistEnabled());
            assertTrue(properties.disposableBlockingEnabled());
            assertTrue(properties.dnsEnabled());
            assertEquals(10, properties.allowedDomains().size());
            var policy = context.getBean(EmailContactPolicy.class);
            assertThrows(com.zaylope.registration.common.exception.InvalidRegistrationInputException.class,
                    () -> policy.normalizeNewContact("member@kin.com"));
            assertTrue(((java.util.Set<?>) policy.publicPolicy().get("disposableDomains")).size() > 1000);
        }
    }
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EmailPolicyProperties.class)
    @Import({EmailContactPolicy.class, EmailDomainValidator.class, JndiEmailDnsResolver.class, EmailPolicyController.class})
    static class PolicyConfiguration {}
}
