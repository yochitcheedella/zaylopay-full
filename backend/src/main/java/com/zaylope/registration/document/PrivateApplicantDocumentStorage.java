package com.zaylope.registration.document;

import com.zaylope.registration.common.exception.ApplicantDocumentNotFoundException;
import com.zaylope.registration.common.exception.DocumentUploadNotConfiguredException;
import com.zaylope.registration.common.exception.PrivateDocumentStorageException;
import com.zaylope.registration.config.DocumentUploadProperties;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PrivateApplicantDocumentStorage {

	private static final Set<PosixFilePermission> PRIVATE_DIRECTORY_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE,
			PosixFilePermission.OWNER_EXECUTE);
	private static final Set<PosixFilePermission> PRIVATE_FILE_PERMISSIONS = Set.of(
			PosixFilePermission.OWNER_READ,
			PosixFilePermission.OWNER_WRITE);

	private final DocumentUploadProperties properties;
	private Path storageRoot;

	public PrivateApplicantDocumentStorage(DocumentUploadProperties properties) {
		this.properties = properties;
	}

	@PostConstruct
	void initialize() {
		if (!properties.isPolicyConfigured()) {
			return;
		}

		Path configuredRoot = properties.storagePath();
		Path projectRoot = Path.of("").toAbsolutePath().normalize();
		if ("backend".equalsIgnoreCase(projectRoot.getFileName().toString())) {
			projectRoot = projectRoot.getParent();
		}
		Path frontendPublic = projectRoot.resolve("frontend").resolve("public").normalize();
		Path frontendBuild = projectRoot.resolve("frontend").resolve("dist").normalize();
		Path backendStatic = projectRoot.resolve("backend")
				.resolve("src")
				.resolve("main")
				.resolve("resources")
				.resolve("static")
				.normalize();
		Path backendBuiltStatic = projectRoot.resolve("backend")
				.resolve("target")
				.resolve("classes")
				.resolve("static")
				.normalize();
		if (isPublicWebRoot(configuredRoot, frontendPublic, frontendBuild, backendStatic, backendBuiltStatic)) {
			throw new IllegalStateException("Applicant document storage must not be inside a public web root.");
		}

		try {
			Files.createDirectories(configuredRoot);
			try {
				Files.setPosixFilePermissions(configuredRoot, PRIVATE_DIRECTORY_PERMISSIONS);
			} catch (UnsupportedOperationException ignored) {
				// Windows ACLs are managed by the operator.
			}
			Path realRoot = configuredRoot.toRealPath();
			if (isPublicWebRoot(realRoot, frontendPublic, frontendBuild, backendStatic, backendBuiltStatic)) {
				throw new IllegalStateException("Applicant document storage must not resolve inside a public web root.");
			}
			storageRoot = realRoot;
		} catch (IOException exception) {
			throw new IllegalStateException("Private applicant document storage is unavailable.", exception);
		}
	}

	public String store(byte[] content, String extension) {
		Path root = requireStorageRoot();
		String identifier = UUID.randomUUID() + "." + extension;
		Path file = root.resolve(identifier).normalize();
		if (!file.startsWith(root)) {
			throw new IllegalStateException("Generated document identifier escaped private storage.");
		}
		try {
			Files.write(file, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			try {
				Files.setPosixFilePermissions(file, PRIVATE_FILE_PERMISSIONS);
			} catch (UnsupportedOperationException ignored) {
				// Windows ACLs are managed by the operator.
			}
			return identifier;
		} catch (IOException exception) {
			try {
				Files.deleteIfExists(file);
			} catch (IOException cleanupException) {
				exception.addSuppressed(cleanupException);
			}
			throw new PrivateDocumentStorageException(exception);
		}
	}

	public byte[] read(String identifier) {
		Path file = resolve(identifier);
		try (SeekableByteChannel channel = Files.newByteChannel(
				file,
				Set.<OpenOption>of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS));
				ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			ByteBuffer buffer = ByteBuffer.allocate(8192);
			while (channel.read(buffer) >= 0) {
				buffer.flip();
				byte[] chunk = new byte[buffer.remaining()];
				buffer.get(chunk);
				output.write(chunk);
				buffer.clear();
			}
			return output.toByteArray();
		} catch (IOException exception) {
			if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
				throw new ApplicantDocumentNotFoundException();
			}
			throw new PrivateDocumentStorageException(exception);
		}
	}

	public void delete(String identifier) {
		Path file = resolve(identifier);
		try {
			Files.deleteIfExists(file);
		} catch (IOException exception) {
			throw new PrivateDocumentStorageException(exception);
		}
	}

	private Path resolve(String identifier) {
		Path root = requireStorageRoot();
		if (identifier == null) {
			throw new ApplicantDocumentNotFoundException();
		}
		int separator = identifier.lastIndexOf('.');
		if (separator <= 0) {
			throw new ApplicantDocumentNotFoundException();
		}
		try {
			UUID uuid = UUID.fromString(identifier.substring(0, separator));
			if (!uuid.toString().equals(identifier.substring(0, separator))
					|| !Set.of("pdf", "jpg", "png").contains(identifier.substring(separator + 1))) {
				throw new ApplicantDocumentNotFoundException();
			}
		} catch (IllegalArgumentException exception) {
			throw new ApplicantDocumentNotFoundException();
		}
		Path file = root.resolve(identifier).normalize();
		if (!file.startsWith(root)) {
			throw new ApplicantDocumentNotFoundException();
		}
		return file;
	}

	private Path requireStorageRoot() {
		if (!properties.isPolicyConfigured() || storageRoot == null) {
			throw new DocumentUploadNotConfiguredException();
		}
		return storageRoot;
	}

	private static boolean isPublicWebRoot(Path candidate, Path... publicRoots) {
		for (Path publicRoot : publicRoots) {
			if (candidate.startsWith(publicRoot)) {
				return true;
			}
		}
		return false;
	}
}
