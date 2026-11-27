package com.zaylope.registration.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
		name = "payment_webhook_events",
		indexes = {
				@Index(name = "ix_payment_webhook_processed", columnList = "processed")
		})
public class PaymentWebhookEvent {

	@Id
	@Column(name = "event_id", nullable = false, length = 64)
	private String eventId;

	@Column(name = "event_type", nullable = false, length = 64)
	private String eventType;

	@Column(name = "payload_hash", nullable = false, length = 64)
	private String payloadHash;

	@Column(name = "processed", nullable = false)
	private boolean processed = false;

	@Column(name = "processed_at")
	private Instant processedAt;

	@Column(name = "processing_error", length = 255)
	private String processingError;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant createdAt;

	protected PaymentWebhookEvent() {
	}

	public PaymentWebhookEvent(String eventId, String eventType, String payloadHash) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.payloadHash = payloadHash;
		this.processed = false;
	}

	@PrePersist
	void initializeTimestamps() {
		this.createdAt = Instant.now();
	}

	public String getEventId() {
		return eventId;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPayloadHash() {
		return payloadHash;
	}

	public boolean isProcessed() {
		return processed;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}

	public String getProcessingError() {
		return processingError;
	}

	public void markProcessed() {
		this.processed = true;
		this.processedAt = Instant.now();
		this.processingError = null;
	}

	public void markFailed(String error) {
		this.processed = false;
		this.processingError = error;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
