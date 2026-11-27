package com.zaylope.registration.payment;

import static org.junit.jupiter.api.Assertions.*;

import com.zaylope.registration.common.exception.PaymentGatewayException;
import com.zaylope.registration.config.RazorpayProperties;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RazorpayServiceTest {

	private RazorpayService razorpayService;
	private final String keySecret = "test_secret_key_12345";
	private final String webhookSecret = "test_webhook_secret_67890";

	@BeforeEach
	void setUp() {
		RazorpayProperties properties = new RazorpayProperties(
				"rzp_test_sample",
				keySecret,
				webhookSecret,
				59900L,
				"INR",
				false,
				"https://api.razorpay.com/v1");

		razorpayService = new RazorpayService(properties, new ObjectMapper());
	}

	@Test
	void shouldVerifyValidPaymentSignature() throws Exception {
		String orderId = "order_sample_001";
		String paymentId = "pay_sample_002";
		String data = orderId + "|" + paymentId;

		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		String validSignature = HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));

		assertTrue(razorpayService.verifyPaymentSignature(orderId, paymentId, validSignature));
		assertFalse(razorpayService.verifyPaymentSignature(orderId, paymentId, "invalid_signature"));
		assertFalse(razorpayService.verifyPaymentSignature(orderId, paymentId, null));
		assertFalse(razorpayService.verifyPaymentSignature(orderId, paymentId, ""));
	}

	@Test
	void shouldVerifyValidWebhookSignature() throws Exception {
		String payload = "{\"event\":\"payment.captured\",\"payload\":{}}";

		Mac mac = Mac.getInstance("HmacSHA256");
		mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
		String validSignature = HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));

		assertTrue(razorpayService.verifyWebhookSignature(payload, validSignature));
		assertFalse(razorpayService.verifyWebhookSignature(payload, "invalid_sig"));
		assertFalse(razorpayService.verifyWebhookSignature(payload, null));
	}

	@Test
	void shouldThrowExceptionWhenCredentialsNotConfigured() {
		RazorpayProperties emptyProps = new RazorpayProperties(
				"", "", "", 59900L, "INR", false, "https://api.razorpay.com/v1");
		RazorpayService unconfiguredService = new RazorpayService(emptyProps, new ObjectMapper());

		PaymentGatewayException ex = assertThrows(PaymentGatewayException.class,
				() -> unconfiguredService.createOrder(1L, 59900L, "INR", "rcpt_1"));
		assertTrue(ex.getMessage().contains("RAZORPAY_KEY_ID"));
	}
}
