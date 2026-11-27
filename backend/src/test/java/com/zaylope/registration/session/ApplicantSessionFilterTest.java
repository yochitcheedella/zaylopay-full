package com.zaylope.registration.session;

import com.zaylope.registration.config.ApplicantSessionProperties;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicantSessionFilterTest {

	private final ApplicantSessionService applicantSessionService = Mockito.mock(ApplicantSessionService.class);
	private final ApplicantSessionProperties properties = Mockito.mock(ApplicantSessionProperties.class);
	private final FilterChain filterChain = Mockito.mock(FilterChain.class);
	private final ApplicantSessionFilter filter =
			new ApplicantSessionFilter(applicantSessionService, properties, new ObjectMapper());

	@Test
	void requiresAnActiveSessionForApplicantOwnedRoutes() throws Exception {
		when(properties.cookieName()).thenReturn("ZAYLOPE_APPLICANT");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServletPath("/api/v1/applicants/me/documents");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
		org.junit.jupiter.api.Assertions.assertTrue(
				response.getContentAsString().contains("\"code\":\"UNAUTHENTICATED\""));
		verify(filterChain, never()).doFilter(request, response);
	}

	@Test
	void requiresAnActiveSessionToRetrieveDocumentContent() throws Exception {
		when(properties.cookieName()).thenReturn("ZAYLOPE_APPLICANT");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServletPath("/api/v1/applicants/me/documents/document-1/content");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
		verify(filterChain, never()).doFilter(request, response);
	}

	@Test
	void requiresAnActiveApplicantSessionForVerificationEndpoints() throws Exception {
		when(properties.cookieName()).thenReturn("ZAYLOPE_APPLICANT");
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServletPath("/api/v1/applicants/me/verification/email/send");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatus());
		verify(filterChain, never()).doFilter(request, response);
	}

	@Test
	void providesApplicantIdentityOnlyForAnActiveSessionCookie() throws Exception {
		when(properties.cookieName()).thenReturn("ZAYLOPE_APPLICANT");
		when(applicantSessionService.findActiveApplicantId("active-session")).thenReturn(Optional.of(42L));
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServletPath("/api/v1/applicants/me/documents");
		request.setCookies(new jakarta.servlet.http.Cookie("ZAYLOPE_APPLICANT", "active-session"));
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		ApplicantSessionPrincipal principal = assertInstanceOf(
				ApplicantSessionPrincipal.class,
				request.getAttribute(ApplicantSessionPrincipal.REQUEST_ATTRIBUTE));
		assertEquals(42L, principal.applicantId());
		verify(applicantSessionService).findActiveApplicantId("active-session");
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void registrationEndpointDoesNotRequireAnApplicantSession() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServletPath("/api/v1/applicants/register");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertEquals(HttpStatus.OK.value(), response.getStatus());
		verify(applicantSessionService, never()).findActiveApplicantId(Mockito.any());
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void sessionDiscoveryEndpointDoesNotRequireAnApplicantSession() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServletPath("/api/v1/auth/session");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertEquals(HttpStatus.OK.value(), response.getStatus());
		verify(filterChain).doFilter(request, response);
	}

	@Test
	void distinguishesMissingCookieFromAnExpiredOrInvalidCookie() throws Exception {
		when(properties.cookieName()).thenReturn("ZAYLOPE_APPLICANT");
		when(applicantSessionService.findActiveApplicantId("expired-session")).thenReturn(Optional.empty());
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setServletPath("/api/v1/applicants/me/verification/status");
		request.setCookies(new jakarta.servlet.http.Cookie("ZAYLOPE_APPLICANT", "expired-session"));
		MockHttpServletResponse response = new MockHttpServletResponse();
		filter.doFilter(request, response, filterChain);
		assertEquals(401, response.getStatus());
		org.junit.jupiter.api.Assertions.assertTrue(response.getContentAsString().contains("EXPIRED_OR_INVALID"));
		verify(filterChain, never()).doFilter(request, response);
	}

	@Test
	void onboardingScopedSessionCannotAccessProtectedFinancialOrSubmissionEndpoints() throws Exception {
		when(properties.cookieName()).thenReturn("ZAYLOPE_APPLICANT");
		when(applicantSessionService.findActiveSession("onboarding-session"))
				.thenReturn(Optional.of(new ApplicantAccessSessionRepository.ActiveSessionProjection(
						42L, ApplicantSessionScope.ONBOARDING)));

		for (String blockedPath : java.util.List.of(
				"/api/v1/applicants/me/bank-details",
				"/api/v1/applicants/me/payments/create-order",
				"/api/v1/applicants/me/submit",
				"/api/v1/applicants/me/membership")) {
			MockHttpServletRequest request = new MockHttpServletRequest("GET", blockedPath);
			request.setServletPath(blockedPath);
			request.setCookies(new jakarta.servlet.http.Cookie("ZAYLOPE_APPLICANT", "onboarding-session"));
			MockHttpServletResponse response = new MockHttpServletResponse();

			filter.doFilter(request, response, filterChain);

			assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
			org.junit.jupiter.api.Assertions.assertTrue(
					response.getContentAsString().contains("RESTRICTED_SESSION_SCOPE"));
		}
		verify(filterChain, never()).doFilter(Mockito.any(), Mockito.any());
	}

	@Test
	void allowsPreflightRequestsForApplicantOwnedRoutes() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/applicants/me/documents");
		request.setServletPath("/api/v1/applicants/me/documents");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, filterChain);

		assertEquals(HttpStatus.OK.value(), response.getStatus());
		verify(applicantSessionService, never()).findActiveApplicantId(Mockito.any());
		verify(filterChain).doFilter(request, response);
	}
}
