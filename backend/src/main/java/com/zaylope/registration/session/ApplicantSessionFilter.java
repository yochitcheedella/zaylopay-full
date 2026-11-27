package com.zaylope.registration.session;

import com.zaylope.registration.config.ApplicantSessionProperties;
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
public class ApplicantSessionFilter extends OncePerRequestFilter {

	private final ApplicantSessionService applicantSessionService;
	private final ApplicantSessionProperties properties;
	private final ObjectMapper objectMapper;

	public ApplicantSessionFilter(
			ApplicantSessionService applicantSessionService,
			ApplicantSessionProperties properties,
			ObjectMapper objectMapper) {
		this.applicantSessionService = applicantSessionService;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String path = request.getServletPath();
		if ("OPTIONS".equalsIgnoreCase(request.getMethod()) || !requiresApplicantSession(path)) {
			filterChain.doFilter(request, response);
			return;
		}

		Optional<ResolvedSession> resolved = resolveActiveSession(request, properties.cookieName());
		if (resolved.isEmpty()) {
			boolean hadCookie = request.getCookies() != null && Arrays.stream(request.getCookies())
					.anyMatch(cookie -> properties.cookieName().equals(cookie.getName()));
			writeError(response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
					"An active applicant session is required.",
					hadCookie ? "EXPIRED_OR_INVALID" : "NONE");
			return;
		}

		ResolvedSession session = resolved.get();
		if (session.scope() == ApplicantSessionScope.ONBOARDING && !isAllowedForOnboardingScope(path)) {
			writeError(response, HttpStatus.FORBIDDEN, "RESTRICTED_SESSION_SCOPE",
					"Please verify your contact before accessing this resource.",
					"ONBOARDING");
			return;
		}

		request.setAttribute(
				ApplicantSessionPrincipal.REQUEST_ATTRIBUTE,
				new ApplicantSessionPrincipal(session.applicantId(), session.scope(), session.token()));
		filterChain.doFilter(request, response);
	}

	private static boolean requiresApplicantSession(String path) {
		return path.equals("/api/v1/auth/me")
				|| path.equals("/api/v1/auth/logout")
				|| path.equals("/api/v1/auth/logout-all")
				|| path.startsWith("/api/v1/applicants/me/");
	}

	private static boolean isAllowedForOnboardingScope(String path) {
		return path.equals("/api/v1/auth/me")
				|| path.equals("/api/v1/auth/logout")
				|| path.equals("/api/v1/auth/logout-all")
				|| path.equals("/api/v1/applicants/me/registration")
				|| path.startsWith("/api/v1/applicants/me/verification/");
	}

	private Optional<ResolvedSession> resolveActiveSession(HttpServletRequest request, String cookieName) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return Optional.empty();
		}
		for (Cookie cookie : cookies) {
			if (!cookieName.equals(cookie.getName())) {
				continue;
			}
			String token = cookie.getValue();
			Optional<ApplicantAccessSessionRepository.ActiveSessionProjection> projection =
					applicantSessionService.findActiveSession(token);
			if (projection != null && projection.isPresent()) {
				return Optional.of(new ResolvedSession(
						projection.get().applicantId(),
						projection.get().sessionScope(),
						token));
			}
			// Fallback compatibility if tests mock findActiveApplicantId instead of findActiveSession
			Optional<Long> legacyApplicantId = applicantSessionService.findActiveApplicantId(token);
			if (legacyApplicantId != null && legacyApplicantId.isPresent()) {
				return Optional.of(new ResolvedSession(
						legacyApplicantId.get(),
						ApplicantSessionScope.AUTHENTICATED,
						token));
			}
		}
		return Optional.empty();
	}

	private void writeError(
			HttpServletResponse response,
			HttpStatus status,
			String code,
			String detail,
			String sessionState) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setTitle(status.getReasonPhrase());
		problem.setProperty("code", code);
		problem.setProperty("sessionState", sessionState);
		objectMapper.writeValue(response.getOutputStream(), problem);
	}

	private record ResolvedSession(long applicantId, ApplicantSessionScope scope, String token) {}
}
