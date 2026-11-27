package com.zaylope.registration.config;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.support.CronExpression;

@ConfigurationProperties(prefix = "app.documents")
public record DocumentUploadProperties(
		boolean enabled,
		String storageDirectory,
		List<String> requiredTypes,
		List<String> optionalTypes,
		String collectionPurpose,
		String privacyNotice,
		String authorizedPersonnel,
		String retentionPolicy,
		String retentionPeriod,
		String deletionPolicy,
		String privacyNoticeVersion,
		String retentionCron) {

	public static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024;

	public DocumentUploadProperties {
		requiredTypes = clean(requiredTypes);
		optionalTypes = clean(optionalTypes);
	}

	public boolean isPolicyConfigured() {
		if (!enabled
				|| !hasText(collectionPurpose)
				|| !hasText(privacyNotice)
				|| !hasText(authorizedPersonnel)
				|| !hasText(retentionPolicy)
				|| !hasText(deletionPolicy)
				|| !hasText(privacyNoticeVersion)
				|| !hasText(storageDirectory)
				|| !hasText(retentionCron)
				|| requiredTypes.isEmpty() && optionalTypes.isEmpty()) {
			return false;
		}

		try {
			if (!Path.of(storageDirectory.trim()).isAbsolute()) {
				return false;
			}
		} catch (InvalidPathException exception) {
			return false;
		}

		Duration duration;
		try {
			duration = Duration.parse(retentionPeriod);
		} catch (DateTimeParseException | NullPointerException exception) {
			return false;
		}
		if (duration.isZero() || duration.isNegative()) {
			return false;
		}
		try {
			CronExpression.parse(retentionCron);
		} catch (IllegalArgumentException exception) {
			return false;
		}

		Set<String> normalizedTypes = requiredTypes.stream()
				.map(DocumentUploadProperties::normalizeType)
				.collect(Collectors.toSet());
		if (normalizedTypes.size() != requiredTypes.size()) {
			return false;
		}
		for (String type : optionalTypes) {
			if (!normalizedTypes.add(normalizeType(type))) {
				return false;
			}
		}
		return requiredTypes.stream().allMatch(DocumentUploadProperties::isSafeType)
				&& optionalTypes.stream().allMatch(DocumentUploadProperties::isSafeType);
	}

	public Duration retentionDuration() {
		return Duration.parse(retentionPeriod);
	}

	public Path storagePath() {
		if (!hasText(storageDirectory)) {
			throw new IllegalStateException("Private document storage is not configured.");
		}
		return Path.of(storageDirectory.trim()).toAbsolutePath().normalize();
	}

	public List<DocumentTypeOption> documentTypes() {
		List<DocumentTypeOption> options = new ArrayList<>();
		requiredTypes.forEach(type -> options.add(new DocumentTypeOption(type, true)));
		optionalTypes.forEach(type -> options.add(new DocumentTypeOption(type, false)));
		return List.copyOf(options);
	}

	public String configuredType(String requestedType) {
		if (!hasText(requestedType)) {
			return null;
		}
		return documentTypes().stream()
				.map(DocumentTypeOption::type)
				.filter(type -> type.equalsIgnoreCase(requestedType.trim()))
				.findFirst()
				.orElse(null);
	}

	public List<String> requiredDocumentTypes() {
		return List.copyOf(requiredTypes);
	}

	private static List<String> clean(List<String> values) {
		if (values == null) {
			return List.of();
		}
		return values.stream()
				.filter(DocumentUploadProperties::hasText)
				.map(String::trim)
				.toList();
	}

	private static boolean isSafeType(String type) {
		return type.length() <= 80 && type.matches("[A-Za-z0-9][A-Za-z0-9 _-]*");
	}

	private static String normalizeType(String type) {
		return type.toLowerCase(Locale.ROOT);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	public record DocumentTypeOption(String type, boolean required) {
	}
}
