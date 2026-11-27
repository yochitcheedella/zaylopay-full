package com.zaylope.registration.payment;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/razorpay")
public class RazorpayWebhookController {

	private final PaymentService paymentService;

	public RazorpayWebhookController(PaymentService paymentService) {
		this.paymentService = paymentService;
	}

	@PostMapping
	public ResponseEntity<Map<String, String>> handleWebhook(
			@RequestBody String rawPayload,
			@RequestHeader(value = "X-Razorpay-Signature", required = false) String signatureHeader) {
		paymentService.processWebhook(rawPayload, signatureHeader);
		return ResponseEntity.ok(Map.of("status", "ok"));
	}
}
