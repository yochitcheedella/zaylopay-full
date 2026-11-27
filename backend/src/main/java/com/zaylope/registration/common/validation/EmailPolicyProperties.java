package com.zaylope.registration.common.validation;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.email-validation")
public record EmailPolicyProperties(
        @DefaultValue("true") boolean allowlistEnabled,
        @DefaultValue({"gmail.com", "yahoo.com", "yahoo.co.in", "outlook.com", "hotmail.com", "live.com",
                "icloud.com", "proton.me", "protonmail.com", "rediffmail.com"}) Set<String> allowedDomains,
        @DefaultValue("true") boolean disposableBlockingEnabled,
        @DefaultValue("classpath:validation/disposable-email-domains.conf") String disposableResource,
        @DefaultValue("true") boolean dnsEnabled,
        @DefaultValue("2s") Duration dnsTimeout,
        @DefaultValue("5m") Duration dnsCacheTtl,
        @DefaultValue("1024") int dnsCacheSize) {
    public EmailPolicyProperties {
        allowedDomains = allowedDomains.stream().map(String::strip).map(s -> s.toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (allowlistEnabled && allowedDomains.isEmpty()) throw new IllegalArgumentException("Email allowlist is empty");
        for (String domain : allowedDomains) {
            if (ApplicantInputValidator.normalizeEmail("policy@" + domain) == null)
                throw new IllegalArgumentException("Invalid configured email provider domain");
        }
        if (dnsTimeout.isNegative() || dnsTimeout.isZero() || dnsTimeout.compareTo(Duration.ofSeconds(10)) > 0
                || dnsCacheTtl.isNegative() || dnsCacheTtl.isZero() || dnsCacheSize < 1 || dnsCacheSize > 100_000)
            throw new IllegalArgumentException("Invalid bounded email DNS configuration");
        if (!(disposableResource.startsWith("classpath:") || disposableResource.startsWith("file:")))
            throw new IllegalArgumentException("Disposable dataset must be a local resource");
    }
}
