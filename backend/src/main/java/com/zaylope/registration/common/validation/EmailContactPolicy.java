package com.zaylope.registration.common.validation;

import com.zaylope.registration.common.exception.InvalidRegistrationInputException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Admission policy for new contacts, deliberately separate from existing-account authentication. */
@Component
public class EmailContactPolicy {
    public static final String UNSUPPORTED = "This email provider is not supported for new accounts. Use a supported provider.";
    public static final String DISPOSABLE = "Disposable email services are not supported. Use a permanent email address.";
    private final EmailPolicyProperties properties;
    private final Set<String> disposableDomains;

    public EmailContactPolicy(EmailPolicyProperties properties, ResourceLoader resources) {
        this.properties = properties;
        try (var reader = new BufferedReader(new InputStreamReader(
                resources.getResource(properties.disposableResource()).getInputStream(), StandardCharsets.UTF_8))) {
            disposableDomains = reader.lines().map(String::strip).filter(s -> !s.isEmpty() && !s.startsWith("#"))
                    .map(s -> s.toLowerCase(java.util.Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Could not load local disposable email dataset", failure);
        }
        if (properties.disposableBlockingEnabled() && disposableDomains.isEmpty())
            throw new IllegalStateException("Disposable email blocking requires a nonempty dataset");
    }

    public String normalizeNewContact(String raw) {
        String normalized = ApplicantInputValidator.normalizeEmail(raw);
        if (normalized == null) throw new InvalidRegistrationInputException("email", ApplicantInputValidator.EMAIL_MESSAGE);
        String domain = normalized.substring(normalized.indexOf('@') + 1);
        if (properties.disposableBlockingEnabled() && isDisposable(domain))
            throw new InvalidRegistrationInputException("email", DISPOSABLE);
        if (properties.allowlistEnabled() && !properties.allowedDomains().contains(domain))
            throw new InvalidRegistrationInputException("email", UNSUPPORTED);
        return normalized;
    }

    public boolean isDisposable(String domain) {
        String suffix = domain;
        while (suffix.contains(".")) {
            if (disposableDomains.contains(suffix)) return true;
            suffix = suffix.substring(suffix.indexOf('.') + 1);
        }
        return false;
    }

    public java.util.Map<String, Object> publicPolicy() {
        return java.util.Map.of("allowlistEnabled", properties.allowlistEnabled(), "allowedDomains", properties.allowedDomains(),
                "disposableBlockingEnabled", properties.disposableBlockingEnabled(), "disposableDomains", disposableDomains,
                "dnsEnabled", properties.dnsEnabled());
    }
}
