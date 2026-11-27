package com.zaylope.registration.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Component
public class AdminSessionFilter extends OncePerRequestFilter {

	public static final String ADMIN_COOKIE_NAME = "ZAYLOPE_ADMIN";

	private final AdminSessionService adminSessionService;
	private final ObjectMapper objectMapper;

	public AdminSessionFilter(AdminSessionService adminSessionService, ObjectMapper objectMapper) {
		this.adminSessionService = adminSessionService;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {

		String path = request.getServletPath();

		// Apply to /api/v1/admin/** except /api/v1/admin/auth/login
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())
				|| !path.startsWith("/api/v1/admin/")
				|| path.equals("/api/v1/admin/auth/login")) {
			filterChain.doFilter(request, response);
			return;
		}

		String token = extractAdminToken(request);
		Optional<AdminUser> adminOpt = token != null ? adminSessionService.findActiveAdmin(token) : Optional.empty();

		if (adminOpt.isEmpty()) {
			writeUnauthenticated(response);
			return;
		}

		AdminUser admin = adminOpt.get();
		request.setAttribute(
				AdminPrincipal.REQUEST_ATTRIBUTE,
				new AdminPrincipal(admin.getAdminId(), admin.getUsername(), admin.getRole()));

		filterChain.doFilter(request, response);
	}

	private String extractAdminToken(HttpServletRequest request) {
		// 1. Try Bearer token
		String authHeader = request.getHeader("Authorization");
		if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return authHeader.substring(7).trim();
		}

		// 2. Try custom header
		String customHeader = request.getHeader("X-Admin-Token");
		if (customHeader != null && !customHeader.isBlank()) {
			return customHeader.trim();
		}

		// 3. Try HTTP cookie
		Cookie[] cookies = request.getCookies();
		if (cookies != null) {
			return Arrays.stream(cookies)
					.filter(c -> ADMIN_COOKIE_NAME.equals(c.getName()))
					.map(Cookie::getValue)
					.filter(v -> v != null && !v.isBlank())
					.findFirst()
					.orElse(null);
		}

		return null;
	}

	private void writeUnauthenticated(HttpServletResponse response) throws IOException {
		HttpStatus status = HttpStatus.UNAUTHORIZED;
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(
				status, "Administrator authentication is required.");
		problem.setTitle(status.getReasonPhrase());
		problem.setProperty("code", "ADMIN_UNAUTHENTICATED");
		objectMapper.writeValue(response.getOutputStream(), problem);
	}
}
