package com.zaylope.registration.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentUploadExceptionHandlerTest {

	@Test
	void oversizedMultipartRequestReturnsPayloadTooLargeWithoutInternalDetails() {
		var response = new GlobalExceptionHandler().handleMaximumUploadSizeExceeded(
				new MaxUploadSizeExceededException(10L * 1024 * 1024));

		assertEquals(HttpStatus.CONTENT_TOO_LARGE, response.getStatusCode());
		assertEquals("INVALID_DOCUMENT", response.getBody().getProperties().get("code"));
		assertEquals("The file exceeds the 10 MB upload limit.", response.getBody().getDetail());
	}
}
