package com.zaylope.registration.document;

import com.zaylope.registration.common.exception.InvalidApplicantDocumentException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApplicantDocumentFileValidatorTest {

	private final ApplicantDocumentFileValidator validator = new ApplicantDocumentFileValidator();

	@Test
	void acceptsValidPngAndJpegImages() throws IOException {
		var png = image("png");
		var jpeg = image("jpg");

		assertEquals("image/png", validator.validate(file("photo.png", "image/png", png)).contentType());
		assertEquals("image/jpeg", validator.validate(file("photo.jpeg", "image/jpeg", jpeg)).contentType());
	}

	@Test
	void acceptsAParseablePdf() throws IOException {
		byte[] pdf;
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			document.addPage(new PDPage());
			document.save(output);
			pdf = output.toByteArray();
		}

		assertEquals(
				"application/pdf",
				validator.validate(file("identity.pdf", "application/pdf", pdf)).contentType());
	}

	@Test
	void rejectsEmptyOversizedCorruptAndMismatchedFiles() throws IOException {
		assertThrows(
				InvalidApplicantDocumentException.class,
				() -> validator.validate(file("empty.png", "image/png", new byte[0])));
		assertThrows(
				InvalidApplicantDocumentException.class,
				() -> validator.validate(file("large.png", "image/png", new byte[10 * 1024 * 1024 + 1])));
		assertThrows(
				InvalidApplicantDocumentException.class,
				() -> validator.validate(file("broken.png", "image/png", new byte[] {
						(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
				})));
		assertThrows(
				InvalidApplicantDocumentException.class,
				() -> validator.validate(file("fake.pdf", "application/pdf", image("png"))));
		assertThrows(
				InvalidApplicantDocumentException.class,
				() -> validator.validate(file("mismatch.jpg", "image/png", image("png"))));
		assertThrows(
				InvalidApplicantDocumentException.class,
				() -> validator.validate(file("document.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[] {0x50, 0x4b, 0x03, 0x04})));
		assertThrows(
				InvalidApplicantDocumentException.class,
				() -> validator.validate(file("script.sh", "application/x-sh", "#!/bin/bash\necho hello".getBytes())));
	}

	@Test
	void rejectsEncryptedOrPasswordProtectedPdf() throws IOException {
		byte[] encryptedPdf;
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			document.addPage(new PDPage());
			AccessPermission permissions = new AccessPermission();
			StandardProtectionPolicy policy = new StandardProtectionPolicy("ownerSecret", "userSecret", permissions);
			document.protect(policy);
			document.save(output);
			encryptedPdf = output.toByteArray();
		}

		assertThrows(
				InvalidApplicantDocumentException.class,
				() -> validator.validate(file("secure.pdf", "application/pdf", encryptedPdf)));
	}

	private static byte[] image(String format) throws IOException {
		BufferedImage image = new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB);
		try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			javax.imageio.ImageIO.write(image, format, output);
			return output.toByteArray();
		}
	}

	private static MockMultipartFile file(String name, String contentType, byte[] content) {
		return new MockMultipartFile("file", name, contentType, content);
	}
}
