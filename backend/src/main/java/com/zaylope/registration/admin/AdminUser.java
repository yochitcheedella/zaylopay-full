package com.zaylope.registration.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
		name = "admin_users",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_admin_users_username", columnNames = "username"),
				@UniqueConstraint(name = "uk_admin_users_email", columnNames = "email")
		})
public class AdminUser {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "admin_id", nullable = false, updatable = false)
	private Long adminId;

	@Column(name = "username", nullable = false, length = 64)
	private String username;

	@Column(name = "email", nullable = false, length = 254)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(name = "full_name", nullable = false, length = 100)
	private String fullName;

	@Column(name = "role", nullable = false, length = 32)
	private String role = "ADMIN";

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
	private Instant updatedAt;

	protected AdminUser() {
	}

	public AdminUser(String username, String email, String passwordHash, String fullName, String role) {
		this.username = username;
		this.email = email;
		this.passwordHash = passwordHash;
		this.fullName = fullName;
		this.role = role == null ? "ADMIN" : role;
		this.active = true;
	}

	public AdminUser(Long adminId, String username, String email, String passwordHash, String fullName, String role) {
		this(username, email, passwordHash, fullName, role);
		this.adminId = adminId;
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

	public Long getAdminId() {
		return adminId;
	}

	public String getUsername() {
		return username;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public void updatePasswordHash(String passwordHash) {
		this.passwordHash = passwordHash;
	}

	public String getFullName() {
		return fullName;
	}

	public String getRole() {
		return role;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
