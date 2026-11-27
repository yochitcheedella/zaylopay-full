package com.zaylope.registration.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Secure one-time Super Admin account provisioning runner.
 * Reads environment variables (SUPER_ADMIN_USERNAME, SUPER_ADMIN_PASSWORD, SUPER_ADMIN_EMAIL, SUPER_ADMIN_NAME).
 * Never overwrites existing accounts and never logs or exposes credentials.
 */
@Component
@Order(10)
public class AdminProvisioningRunner implements CommandLineRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(AdminProvisioningRunner.class);

	private final AdminUserRepository adminUserRepository;
	private final AdminPasswordEncoder passwordEncoder;

	@Value("${SUPER_ADMIN_USERNAME:#{null}}")
	private String superAdminUsername;

	@Value("${SUPER_ADMIN_PASSWORD:#{null}}")
	private String superAdminPassword;

	@Value("${SUPER_ADMIN_EMAIL:superadmin@zaylope.com}")
	private String superAdminEmail;

	@Value("${SUPER_ADMIN_NAME:Super Administrator}")
	private String superAdminName;

	public AdminProvisioningRunner(
			AdminUserRepository adminUserRepository,
			AdminPasswordEncoder passwordEncoder) {
		this.adminUserRepository = adminUserRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(String... args) {
		if (superAdminUsername == null || superAdminUsername.isBlank()
				|| superAdminPassword == null || superAdminPassword.isBlank()) {
			if (adminUserRepository.count() == 0) {
				LOGGER.info("No administrative accounts detected. To provision the initial Super Admin, "
						+ "set SUPER_ADMIN_USERNAME and SUPER_ADMIN_PASSWORD environment variables.");
			}
			return;
		}

		String cleanUsername = superAdminUsername.trim();
		if (cleanUsername.length() < 3 || cleanUsername.length() > 64) {
			LOGGER.error("Provisioning aborted: SUPER_ADMIN_USERNAME must be between 3 and 64 characters.");
			return;
		}

		if (superAdminPassword.length() < 10) {
			LOGGER.error("Provisioning aborted: SUPER_ADMIN_PASSWORD must be at least 10 characters long.");
			return;
		}

		if (adminUserRepository.findByUsernameIgnoreCase(cleanUsername).isPresent()) {
			LOGGER.info("Admin account '{}' already exists. Provisioning runner skipped.", cleanUsername);
			return;
		}

		if (adminUserRepository.findByEmailIgnoreCase(superAdminEmail.trim()).isPresent()) {
			LOGGER.info("Admin email '{}' already registered. Provisioning runner skipped.", superAdminEmail.trim());
			return;
		}

		String encodedPassword = passwordEncoder.encode(superAdminPassword);
		AdminUser superAdmin = new AdminUser(
				cleanUsername,
				superAdminEmail.trim().toLowerCase(),
				encodedPassword,
				superAdminName != null && !superAdminName.isBlank() ? superAdminName.trim() : "Super Administrator",
				"SUPER_ADMIN"
		);

		adminUserRepository.save(superAdmin);
		LOGGER.info("✓ Securely provisioned initial SUPER_ADMIN account: '{}' with BCrypt hashing.", cleanUsername);
	}
}
