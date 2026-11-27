package com.zaylope.registration.payment;

import com.zaylope.registration.common.exception.PaymentGatewayException;
import com.zaylope.registration.config.RazorpayProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@EnableConfigurationProperties(RazorpayProperties.class)
public class RazorpayService {

	private static final Logger LOGGER = LoggerFactory.getLogger(RazorpayService.class);
	private static final String HMAC_SHA256 = "HmacSHA256";

	private final RazorpayProperties properties;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	public RazorpayService(RazorpayProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build();
	}

	public RazorpayOrderResult createOrder(Long applicantId, long amountPaise, String currency, String receipt) {
		if (properties.mockGateway()) {
			String mockOrderId = "order_mock_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
			LOGGER.info("Created Mock Razorpay Order {} for applicantId {} of amount {} {}",
					mockOrderId, applicantId, amountPaise, currency);
			return new RazorpayOrderResult(mockOrderId, amountPaise, currency, receipt, "created", properties.keyId());
		}

		if (properties.keyId() == null || properties.keyId().isBlank()
				|| properties.keySecret() == null || properties.keySecret().isBlank()) {
			throw new PaymentGatewayException(
					"Razorpay Test credentials are not configured. Please set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET environment variables.");
		}

		try {
			String auth = Base64.getEncoder().encodeToString(
					(properties.keyId().trim() + ":" + properties.keySecret().trim()).getBytes(StandardCharsets.UTF_8));

			String requestBody = objectMapper.writeValueAsString(java.util.Map.of(
					"amount", amountPaise,
					"currency", currency,
					"receipt", receipt,
					"notes", java.util.Map.of("applicantId", String.valueOf(applicantId))));

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(properties.apiUrl() + "/orders"))
					.header("Authorization", "Basic " + auth)
					.header("Content-Type", "application/json")
					.timeout(Duration.ofSeconds(15))
					.POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				JsonNode root = objectMapper.readTree(response.body());
				String orderId = root.path("id").asString();
				String status = root.path("status").asString("created");
				LOGGER.info("Created real Razorpay Test Order {} for applicantId {} of amount {} {}",
						orderId, applicantId, amountPaise, currency);
				return new RazorpayOrderResult(orderId, amountPaise, currency, receipt, status, properties.keyId().trim());
			} else {
				String errorDesc = parseRazorpayError(response.body());
				LOGGER.error("Razorpay order creation failed (HTTP {}): {}", response.statusCode(), errorDesc);
				throw new PaymentGatewayException("Failed to create Razorpay order: " + errorDesc);
			}
		} catch (PaymentGatewayException e) {
			throw e;
		} catch (Exception e) {
			LOGGER.error("Payment gateway communication error: {}", e.getMessage(), e);
			throw new PaymentGatewayException("Payment gateway communication error: " + e.getMessage(), e);
		}
	}

	public RazorpayPaymentDetails fetchPayment(String paymentId) {
		if (properties.mockGateway()) {
			return new RazorpayPaymentDetails(paymentId, "captured", properties.amountPaise(), properties.currency(), null);
		}

		if (properties.keyId().isBlank() || properties.keySecret().isBlank()) {
			throw new PaymentGatewayException("Razorpay credentials are not configured.");
		}

		try {
			String auth = Base64.getEncoder().encodeToString(
					(properties.keyId().trim() + ":" + properties.keySecret().trim()).getBytes(StandardCharsets.UTF_8));

			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(properties.apiUrl() + "/payments/" + paymentId.trim()))
					.header("Authorization", "Basic " + auth)
					.timeout(Duration.ofSeconds(15))
					.GET()
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() >= 200 && response.statusCode() < 300) {
				JsonNode root = objectMapper.readTree(response.body());
				String status = root.path("status").asString();
				long amount = root.path("amount").asLong();
				String currency = root.path("currency").asString();
				String orderId = root.path("order_id").asString();
				return new RazorpayPaymentDetails(paymentId, status, amount, currency, orderId);
			} else {
				String errorDesc = parseRazorpayError(response.body());
				LOGGER.error("Failed to fetch Razorpay payment {}: HTTP {} - {}", paymentId, response.statusCode(), errorDesc);
				throw new PaymentGatewayException("Payment verification failed at gateway: " + errorDesc);
			}
		} catch (PaymentGatewayException e) {
			throw e;
		} catch (Exception e) {
			LOGGER.error("Error communicating with Razorpay for payment {}: {}", paymentId, e.getMessage(), e);
			throw new PaymentGatewayException("Payment gateway communication error: " + e.getMessage(), e);
		}
	}

	public java.util.List<RazorpayPaymentDetails> fetchOrderPayments(String orderId) {
		if (properties.mockGateway() || orderId == null || orderId.isBlank() || orderId.startsWith("order_mock_")) {
			return java.util.List.of();
		}
		if (properties.keyId().isBlank() || properties.keySecret().isBlank()) {
			return java.util.List.of();
		}
		try {
			String auth = Base64.getEncoder().encodeToString(
					(properties.keyId().trim() + ":" + properties.keySecret().trim()).getBytes(StandardCharsets.UTF_8));
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(properties.apiUrl() + "/orders/" + orderId.trim() + "/payments"))
					.header("Authorization", "Basic " + auth)
					.timeout(Duration.ofSeconds(10))
					.GET()
					.build();
			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				return java.util.List.of();
			}
			JsonNode root = objectMapper.readTree(response.body());
			JsonNode items = root.path("items");
			if (!items.isArray()) {
				return java.util.List.of();
			}
			java.util.List<RazorpayPaymentDetails> results = new java.util.ArrayList<>();
			for (JsonNode item : items) {
				results.add(new RazorpayPaymentDetails(
						item.path("id").asString(),
						item.path("status").asString(),
						item.path("amount").asLong(),
						item.path("currency").asString(),
						item.path("order_id").asString(orderId),
						item.path("method").asString("razorpay")));
			}
			return results;
		} catch (Exception e) {
			LOGGER.warn("Unable to reconcile order {} payments from Razorpay: {}", orderId, e.getMessage());
			return java.util.List.of();
		}
	}

	public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
		if (signature == null || signature.isBlank() || orderId == null || paymentId == null) {
			return false;
		}

		if (properties.mockGateway() && signature.startsWith("mock_sig_")) {
			return true;
		}
		if (properties.keySecret() == null || properties.keySecret().isBlank()) {
			return false;
		}

		try {
			String data = orderId + "|" + paymentId;
			Mac mac = Mac.getInstance(HMAC_SHA256);
			SecretKeySpec secretKey = new SecretKeySpec(
					properties.keySecret().trim().getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
			mac.init(secretKey);
			byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			String expectedSignature = HexFormat.of().formatHex(hash);

			return MessageDigest.isEqual(
					expectedSignature.getBytes(StandardCharsets.UTF_8),
					signature.trim().getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			LOGGER.error("Error verifying payment signature: {}", e.getMessage());
			return false;
		}
	}

	public boolean verifyWebhookSignature(String rawPayload, String signatureHeader) {
		if (signatureHeader == null || signatureHeader.isBlank() || rawPayload == null) {
			return false;
		}

		if (properties.mockGateway() && signatureHeader.startsWith("mock_webhook_")) {
			return true;
		}
		if (properties.webhookSecret() == null || properties.webhookSecret().isBlank()) {
			return false;
		}

		try {
			Mac mac = Mac.getInstance(HMAC_SHA256);
			SecretKeySpec secretKey = new SecretKeySpec(
					properties.webhookSecret().trim().getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
			mac.init(secretKey);
			byte[] hash = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
			String expectedSignature = HexFormat.of().formatHex(hash);

			return MessageDigest.isEqual(
					expectedSignature.getBytes(StandardCharsets.UTF_8),
					signatureHeader.trim().getBytes(StandardCharsets.UTF_8));
		} catch (Exception e) {
			LOGGER.error("Error verifying webhook signature: {}", e.getMessage());
			return false;
		}
	}

	private String parseRazorpayError(String responseBody) {
		if (responseBody == null || responseBody.isBlank()) {
			return "No response details from Razorpay.";
		}
		try {
			JsonNode node = objectMapper.readTree(responseBody);
			if (node.has("error")) {
				JsonNode err = node.get("error");
				String desc = err.path("description").asString("");
				String code = err.path("code").asString("");
				if (!desc.isBlank()) {
					return desc + (!code.isBlank() ? " (" + code + ")" : "");
				}
			}
		} catch (Exception ignored) {
		}
		return responseBody;
	}

	public RazorpayProperties getProperties() {
		return properties;
	}

	public record RazorpayOrderResult(
			String id,
			long amount,
			String currency,
			String receipt,
			String status,
			String keyId) {
	}

	public record RazorpayPaymentDetails(
			String paymentId,
			String status,
			long amountPaise,
			String currency,
			String orderId,
			String method) {

		public RazorpayPaymentDetails(
				String paymentId,
				String status,
				long amountPaise,
				String currency,
				String orderId) {
			this(paymentId, status, amountPaise, currency, orderId, "razorpay");
		}
	}
}
