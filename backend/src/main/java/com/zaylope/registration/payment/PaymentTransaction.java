package com.zaylope.registration.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
		name = "payment_transactions",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_payment_tx_gateway_payment", columnNames = "gateway_payment_id")
		},
		indexes = {
				@Index(name = "ix_payment_tx_order", columnList = "payment_order_id"),
				@Index(name = "ix_payment_tx_applicant", columnList = "applicant_id"),
				@Index(name = "ix_payment_tx_status", columnList = "status")
		})
public class PaymentTransaction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "transaction_id", nullable = false, updatable = false)
	private Long transactionId;

	@Column(name = "payment_order_id", nullable = false, updatable = false)
	private Long paymentOrderId;

	@Column(name = "applicant_id", nullable = false, updatable = false)
	private Long applicantId;

	@Column(name = "gateway_payment_id", nullable = false, length = 64)
	private String gatewayPaymentId;

	@Column(name = "gateway_order_id", nullable = false, length = 64)
	private String gatewayOrderId;

	@Column(name = "amount_paise", nullable = false)
	private Long amountPaise = 59900L;

	@Column(name = "currency", nullable = false, length = 8)
	private String currency = "INR";

	@Column(name = "method", length = 32)
	private String method;

	@Column(name = "status", nullable = false, length = 32)
	private String status = "CAPTURED";

	@Column(name = "signature", length = 128)
	private String signature;

	@Column(name = "signature_verified", nullable = false)
	private boolean signatureVerified = false;

	@Column(name = "captured_at")
	private Instant capturedAt;

	@Column(name = "error_code", length = 64)
	private String errorCode;

	@Column(name = "error_description", length = 255)
	private String errorDescription;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant updatedAt;

	protected PaymentTransaction() {
	}

	public PaymentTransaction(
			Long paymentOrderId,
			Long applicantId,
			String gatewayPaymentId,
			String gatewayOrderId,
			Long amountPaise,
			String currency,
			String method,
			String status,
			String signature,
			boolean signatureVerified) {
		this.paymentOrderId = paymentOrderId;
		this.applicantId = applicantId;
		this.gatewayPaymentId = gatewayPaymentId;
		this.gatewayOrderId = gatewayOrderId;
		this.amountPaise = amountPaise;
		this.currency = currency;
		this.method = method;
		this.status = status;
		this.signature = signature;
		this.signatureVerified = signatureVerified;
		if ("CAPTURED".equalsIgnoreCase(status)) {
			this.capturedAt = Instant.now();
		}
	}

	@PrePersist
	void initializeTimestamps() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void updateTimestamp() {
		this.updatedAt = Instant.now();
	}

	public Long getTransactionId() {
		return transactionId;
	}

	public Long getPaymentOrderId() {
		return paymentOrderId;
	}

	public Long getApplicantId() {
		return applicantId;
	}

	public String getGatewayPaymentId() {
		return gatewayPaymentId;
	}

	public String getGatewayOrderId() {
		return gatewayOrderId;
	}

	public Long getAmountPaise() {
		return amountPaise;
	}

	public String getCurrency() {
		return currency;
	}

	public String getMethod() {
		return method;
	}

	public String getStatus() {
		return status;
	}

	public String getSignature() {
		return signature;
	}

	public boolean isSignatureVerified() {
		return signatureVerified;
	}

	public Instant getCapturedAt() {
		return capturedAt;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public String getErrorDescription() {
		return errorDescription;
	}

	public void markFailed(String errorCode, String errorDescription) {
		this.status = "FAILED";
		this.errorCode = errorCode;
		this.errorDescription = errorDescription;
	}

	public void markRefunded(String errorCode, String errorDescription) {
		this.status = "REFUNDED";
		this.errorCode = errorCode;
		this.errorDescription = errorDescription;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
