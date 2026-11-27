package com.zaylope.registration.document;

import com.zaylope.registration.common.exception.InvalidApplicantDocumentException;
import com.zaylope.registration.config.DocumentUploadProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import static com.zaylope.registration.config.DocumentUploadProperties.MAX_FILE_SIZE_BYTES;

@Component
public class ApplicantDocumentFileValidator {

	public static final List<String> ACCEPTED_FILE_FORMATS = List.of("PDF", "JPG/JPEG", "PNG");

	private static final byte[] PNG_SIGNATURE = {
			(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
	};
	private static final Set<String> ACTIVE_PDF_KEYS = Set.of(
			"JS", "JavaScript", "Launch", "OpenAction", "AA", "EmbeddedFiles", "RichMedia");
	private static final long MAX_IMAGE_PIXELS = 20_000_000;

	public ValidatedDocument validate(MultipartFile file) {
		if (file == null || file.isEmpty() || file.getSize() > MAX_FILE_SIZE_BYTES) {
			throw new InvalidApplicantDocumentException();
		}

		byte[] content;
		try {
			content = file.getBytes();
		} catch (IOException exception) {
			throw new InvalidApplicantDocumentException(exception);
		}
		if (content.length == 0 || content.length != file.getSize()) {
			throw new InvalidApplicantDocumentException();
		}

		String detectedType = detectType(content);
		String declaredType = file.getContentType();
		String extension = extension(file.getOriginalFilename());
		if (detectedType == null
				|| declaredType == null
				|| !detectedType.equalsIgnoreCase(declaredType)
				|| !extensionMatches(detectedType, extension)) {
			throw new InvalidApplicantDocumentException();
		}

		validateContent(content, detectedType);
		return new ValidatedDocument(
				content,
				detectedType,
				extensionFor(detectedType),
				safeOriginalFilename(file.getOriginalFilename(), extensionFor(detectedType)));
	}

	private static String detectType(byte[] content) {
		if (startsWith(content, PNG_SIGNATURE)) {
			return "image/png";
		}
		if (content.length >= 3
				&& content[0] == (byte) 0xff
				&& content[1] == (byte) 0xd8
				&& content[2] == (byte) 0xff) {
			return "image/jpeg";
		}
		if (content.length >= 5
				&& content[0] == '%'
				&& content[1] == 'P'
				&& content[2] == 'D'
				&& content[3] == 'F'
				&& content[4] == '-') {
			return "application/pdf";
		}
		return null;
	}

	private static void validateContent(byte[] content, String contentType) {
		if ("application/pdf".equals(contentType)) {
			validatePdf(content);
			return;
		}
		validateImage(content, contentType);
	}

	private static void validatePdf(byte[] content) {
		try (PDDocument document = Loader.loadPDF(content)) {
			if (document.isEncrypted() || document.getNumberOfPages() < 1 || containsActivePdfContent(document.getDocument().getTrailer())) {
				throw new InvalidApplicantDocumentException();
			}
		} catch (IOException exception) {
			throw new InvalidApplicantDocumentException(exception);
		}
	}

	private static boolean containsActivePdfContent(COSBase root) {
		var pending = new ArrayDeque<COSBase>();
		Set<COSBase> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		pending.push(root);
		while (!pending.isEmpty()) {
			COSBase current = pending.pop();
			if (current instanceof COSObject indirectObject) {
				current = indirectObject.getObject();
			}
			if (current == null || !visited.add(current)) {
				continue;
			}
			if (current instanceof COSDictionary dictionary) {
				for (var entry : dictionary.entrySet()) {
					if (ACTIVE_PDF_KEYS.contains(entry.getKey().getName())) {
						return true;
					}
					if (entry.getValue() != null) {
						pending.push(entry.getValue());
					}
				}
			} else if (current instanceof COSArray array) {
				for (COSBase value : array) {
					if (value != null) {
						pending.push(value);
					}
				}
			}
		}
		return false;
	}

	private static void validateImage(byte[] content, String contentType) {
		try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
			if (input == null) {
				throw new InvalidApplicantDocumentException();
			}
			Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw new InvalidApplicantDocumentException();
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input, true, true);
				String formatName = reader.getFormatName().toLowerCase(Locale.ROOT);
				if (!formatMatches(contentType, formatName)) {
					throw new InvalidApplicantDocumentException();
				}
				int width = reader.getWidth(0);
				int height = reader.getHeight(0);
				if (width <= 0 || height <= 0 || (long) width * height > MAX_IMAGE_PIXELS) {
					throw new InvalidApplicantDocumentException();
				}
				BufferedImage image = reader.read(0);
				if (image == null) {
					throw new InvalidApplicantDocumentException();
				}
			} finally {
				reader.dispose();
			}
		} catch (IOException exception) {
			throw new InvalidApplicantDocumentException(exception);
		}
	}

	private static boolean startsWith(byte[] content, byte[] prefix) {
		if (content.length < prefix.length) {
			return false;
		}
		for (int index = 0; index < prefix.length; index++) {
			if (content[index] != prefix[index]) {
				return false;
			}
		}
		return true;
	}

	private static boolean formatMatches(String contentType, String formatName) {
		return "image/png".equals(contentType)
				? "png".equals(formatName)
				: "jpeg".equals(formatName) || "jpg".equals(formatName);
	}

	private static boolean extensionMatches(String contentType, String extension) {
		return switch (contentType) {
			case "application/pdf" -> "pdf".equals(extension);
			case "image/jpeg" -> "jpg".equals(extension) || "jpeg".equals(extension);
			case "image/png" -> "png".equals(extension);
			default -> false;
		};
	}

	private static String extension(String filename) {
		if (filename == null) {
			return "";
		}
		String normalized = filename.replace('\\', '/');
		int separator = normalized.lastIndexOf('/');
		int dot = normalized.lastIndexOf('.');
		return dot > separator && dot < normalized.length() - 1
				? normalized.substring(dot + 1).toLowerCase(Locale.ROOT)
				: "";
	}

	private static String extensionFor(String contentType) {
		return switch (contentType) {
			case "application/pdf" -> "pdf";
			case "image/jpeg" -> "jpg";
			case "image/png" -> "png";
			default -> throw new InvalidApplicantDocumentException();
		};
	}

	private static String safeOriginalFilename(String filename, String extension) {
		String normalized = filename == null ? "" : filename.replace('\\', '/');
		int separator = normalized.lastIndexOf('/');
		String baseName = normalized.substring(separator + 1)
				.replaceAll("[\\p{Cntrl}]", "_")
				.trim();
		if (baseName.isEmpty() || ".".equals(baseName) || "..".equals(baseName)) {
			baseName = "document." + extension;
		}
		return baseName.length() <= 255 ? baseName : baseName.substring(0, 255);
	}

	public record ValidatedDocument(
			byte[] content,
			String contentType,
			String extension,
			String originalFilename) {
	}
}
