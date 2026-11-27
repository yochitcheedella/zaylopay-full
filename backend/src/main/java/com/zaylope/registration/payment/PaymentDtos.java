package com.zaylope.registration.payment;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public class PaymentDtos {

	public record PaymentOrderResponse(
			String orderId,
			long amountPaise,
			String currency,
			String keyId,
			String receipt,
			String status) {
	}

	public record PaymentVerificationRequest(
			@NotBlank(message = "razorpayOrderId is required")
			String razorpayOrderId,

			@NotBlank(message = "razorpayPaymentId is required")
			String razorpayPaymentId,

			@NotBlank(message = "razorpaySignature is required")
			String razorpaySignature) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record PaymentVerificationResponse(
			boolean verified,
			String orderId,
			String paymentId,
			String status,
			long amountPaise,
			Instant capturedAt,
			String membershipStatus,
			boolean activationPending,
			String affiliateId,
			String referralCode,
			Instant activatedAt) {

		public PaymentVerificationResponse(
				boolean verified,
				String orderId,
				String paymentId,
				String status,
				long amountPaise,
				Instant capturedAt) {
			this(verified, orderId, paymentId, status, amountPaise, capturedAt,
					null, false, null, null, null);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record PaymentStatusResponse(
			String paymentStatus,
			boolean paid,
			String orderId,
			String paymentId,
			Long amountPaise,
			Instant paidAt,
			String currency,
			String membershipStatus,
			boolean activationPending,
			String affiliateId,
			Instant activatedAt,
			String lastFailureReason) {

		public PaymentStatusResponse(
				String paymentStatus,
				boolean paid,
				String orderId,
				String paymentId,
				Long amountPaise,
				Instant paidAt) {
			this(paymentStatus, paid, orderId, paymentId, amountPaise, paidAt,
					"INR", paid ? "PENDING_ACTIVATION" : "NOT_STARTED", paid, null, null, null);
		}
	}
}
