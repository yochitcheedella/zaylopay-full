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
		name = "payment_orders",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_payment_orders_gateway_order", columnNames = "gateway_order_id")
		},
		indexes = {
				@Index(name = "ix_payment_orders_applicant", columnList = "applicant_id"),
				@Index(name = "ix_payment_orders_status", columnList = "status")
		})
public class PaymentOrder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "order_id", nullable = false, updatable = false)
	private Long orderId;

	@Column(name = "applicant_id", nullable = false, updatable = false)
	private Long applicantId;

	@Column(name = "gateway_order_id", nullable = false, length = 64)
	private String gatewayOrderId;

	@Column(name = "amount_paise", nullable = false)
	private Long amountPaise = 59900L;

	@Column(name = "currency", nullable = false, length = 8)
	private String currency = "INR";

	@Column(name = "receipt", nullable = false, length = 64)
	private String receipt;

	@Column(name = "status", nullable = false, length = 32)
	private String status = "CREATED";

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant updatedAt;

	protected PaymentOrder() {
	}

	public PaymentOrder(Long applicantId, String gatewayOrderId, Long amountPaise, String currency, String receipt) {
		this.applicantId = applicantId;
		this.gatewayOrderId = gatewayOrderId;
		this.amountPaise = amountPaise;
		this.currency = currency;
		this.receipt = receipt;
		this.status = "CREATED";
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

	public Long getOrderId() {
		return orderId;
	}

	public Long getApplicantId() {
		return applicantId;
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

	public String getReceipt() {
		return receipt;
	}

	public String getStatus() {
		return status;
	}

	public void markPaid() {
		this.status = "PAID";
	}

	public void markFailed() {
		this.status = "FAILED";
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
