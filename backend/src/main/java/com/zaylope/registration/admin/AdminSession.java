package com.zaylope.registration.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
		name = "admin_sessions",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_admin_sessions_token_hash", columnNames = "token_hash")
		},
		indexes = {
				@Index(name = "ix_admin_sessions_admin", columnList = "admin_id"),
				@Index(name = "ix_admin_sessions_expires", columnList = "expires_at")
		})
public class AdminSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "session_id", nullable = false, updatable = false)
	private Long sessionId;

	@Column(name = "admin_id", nullable = false, updatable = false)
	private Long adminId;

	@Column(name = "token_hash", nullable = false, length = 64)
	private String tokenHash;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant createdAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	protected AdminSession() {
	}

	public AdminSession(Long adminId, String tokenHash, Instant expiresAt) {
		this.adminId = adminId;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
	}

	@PrePersist
	void initializeTimestamps() {
		this.createdAt = Instant.now();
	}

	public Long getSessionId() {
		return sessionId;
	}

	public Long getAdminId() {
		return adminId;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public boolean isActive(Instant now) {
		return revokedAt == null && expiresAt.isAfter(now);
	}

	public void revoke() {
		this.revokedAt = Instant.now();
	}
}
