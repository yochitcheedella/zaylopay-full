package com.zaylope.registration.common.validation;

import com.zaylope.registration.common.exception.InvalidRegistrationInputException;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class EmailDomainValidator implements AutoCloseable {
    enum Result { ACCEPTED, NONEXISTENT, NO_MAIL, TEMPORARY }
    private record Cached(Result result, Instant expiresAt) {}
    private final EmailPolicyProperties properties;
    private final EmailDnsResolver resolver;
    private final LinkedHashMap<String, Cached> cache = new LinkedHashMap<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(32), task -> { var thread = new Thread(task, "email-dns"); thread.setDaemon(true); return thread; },
            new ThreadPoolExecutor.AbortPolicy());

    public EmailDomainValidator(EmailPolicyProperties properties, EmailDnsResolver resolver) {
        this.properties = properties;
        this.resolver = resolver;
    }

    public void validate(String normalizedEmail) {
        if (!properties.dnsEnabled()) return;
        if (TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Email DNS validation must run before opening a database transaction");
        String normalized = ApplicantInputValidator.normalizeEmail(normalizedEmail);
        if (normalized == null) throw new InvalidRegistrationInputException("email", ApplicantInputValidator.EMAIL_MESSAGE);
        String domain = normalized.substring(normalized.indexOf('@') + 1);
        Result result = cachedLookup(domain);
        if (result == Result.TEMPORARY) throw new EmailDomainUnavailableException();
        if (result == Result.NONEXISTENT)
            throw new InvalidRegistrationInputException("email", "This email domain does not exist. Check the address.");
        if (result == Result.NO_MAIL)
            throw new InvalidRegistrationInputException("email", "This email domain does not accept email. Use another address.");
    }

    private Result cachedLookup(String domain) {
        Instant now = Instant.now();
        synchronized (cache) {
            Cached entry = cache.get(domain);
            if (entry != null && now.isBefore(entry.expiresAt())) return entry.result();
        }
        Result result;
        Future<EmailDnsResolver.Answer> future = null;
        try {
            future = executor.submit(() -> resolver.resolve(domain));
            result = classify(future.get(properties.dnsTimeout().toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            result = Result.TEMPORARY;
        } catch (ExecutionException | TimeoutException | RejectedExecutionException failure) {
            result = Result.TEMPORARY;
        } finally {
            if (future != null && !future.isDone()) future.cancel(true);
        }
        long seconds = result == Result.TEMPORARY ? 5 : result == Result.ACCEPTED ? properties.dnsCacheTtl().toSeconds() : 60;
        synchronized (cache) {
            cache.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
            if (cache.size() >= properties.dnsCacheSize()) cache.remove(cache.keySet().iterator().next());
            cache.put(domain, new Cached(result, now.plusSeconds(seconds)));
        }
        return result;
    }

    static Result classify(EmailDnsResolver.Answer answer) {
        if (answer.status() == EmailDnsResolver.Status.NONEXISTENT) return Result.NONEXISTENT;
        if (answer.status() == EmailDnsResolver.Status.TEMPORARY_FAILURE) return Result.TEMPORARY;
        if (answer.mx().stream().anyMatch(record -> record.strip().matches("[0-9]+\\s+\\."))) return Result.NO_MAIL;
        if (!answer.mx().isEmpty() || answer.hasAddress()) return Result.ACCEPTED;
        return Result.NO_MAIL;
    }

    @PreDestroy
    public void close() { executor.shutdownNow(); }
}
