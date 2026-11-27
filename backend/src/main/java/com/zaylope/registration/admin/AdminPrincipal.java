package com.zaylope.registration.admin;

public record AdminPrincipal(long adminId, String username, String role) {

	public static final String REQUEST_ATTRIBUTE = "adminPrincipal";

	public boolean isSuperAdmin() {
		return "SUPER_ADMIN".equalsIgnoreCase(role);
	}
}
