package com.zaylope.registration.config;

import com.zaylope.registration.session.ApplicantRequestSecurityFilter;
import com.zaylope.registration.session.ApplicantSessionFilter;
import com.zaylope.registration.session.ApplicantSessionService;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockFilterChain;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicantCorsTest {
    private static final String ORIGIN = "http://localhost:5173";
    private static final String PATH = "/api/v1/applicants/me/verification/status";
    private final WebConfiguration config = new WebConfiguration(ORIGIN);
    private final ApplicantSessionService sessions = mock(ApplicantSessionService.class);
    private final ApplicantSessionFilter sessionFilter = new ApplicantSessionFilter(sessions,
            new ApplicantSessionProperties("ZAYLOPE_APPLICANT", Duration.ofMinutes(30), false), new ObjectMapper());

    private MockHttpServletResponse request(String method, String origin, Cookie cookie) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, PATH);
        request.setServletPath(PATH);
        request.addHeader("Origin", origin);
        if (cookie != null) request.setCookies(cookie);
        if (method.equals("OPTIONS")) {
            request.addHeader("Access-Control-Request-Method", "GET");
            request.addHeader("Access-Control-Request-Headers", "content-type,x-zaylope-request");
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        config.applicantCorsFilter().getFilter().doFilter(request, response,
                (req, res) -> new ApplicantRequestSecurityFilter(ORIGIN).doFilter(req, res,
                        (r, s) -> sessionFilter.doFilter(r, s, new MockFilterChain())));
        return response;
    }

    @Test void corsRunsBeforeSessionAndRequestSecurityFilters() {
        assertTrue(config.applicantCorsFilter().getOrder() < Ordered.HIGHEST_PRECEDENCE + 10);
    }

    @Test void missingSessionRemainsUnauthorizedButBrowserCanReadIt() throws Exception {
        var response = request("GET", ORIGIN, null);
        assertEquals(401, response.getStatus());
        assertEquals(ORIGIN, response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
        assertTrue(response.getContentAsString().contains("\"sessionState\":\"NONE\""));
        assertEquals("no-store, private", response.getHeader("Cache-Control"));
        verifyNoInteractions(sessions);
    }

    @Test void invalidSessionHasReadableSecureRecoveryState() throws Exception {
        when(sessions.findActiveApplicantId("invalid")).thenReturn(Optional.empty());
        var response = request("GET", ORIGIN, new Cookie("ZAYLOPE_APPLICANT", "invalid"));
        assertEquals(401, response.getStatus());
        assertEquals(ORIGIN, response.getHeader("Access-Control-Allow-Origin"));
        assertTrue(response.getContentAsString().contains("EXPIRED_OR_INVALID"));
    }

    @Test void activeSessionStillPassesValidation() throws Exception {
        when(sessions.findActiveApplicantId("active")).thenReturn(Optional.of(42L));
        var response = request("GET", ORIGIN, new Cookie("ZAYLOPE_APPLICANT", "active"));
        assertEquals(200, response.getStatus());
        assertEquals(ORIGIN, response.getHeader("Access-Control-Allow-Origin"));
        verify(sessions).findActiveApplicantId("active");
    }

    @Test void preflightSupportsCredentialedClientHeadersWithoutSession() throws Exception {
        var response = request("OPTIONS", ORIGIN, null);
        assertEquals(200, response.getStatus());
        assertEquals(ORIGIN, response.getHeader("Access-Control-Allow-Origin"));
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
        assertTrue(response.getHeader("Access-Control-Allow-Headers").contains("x-zaylope-request"));
        verifyNoInteractions(sessions);
    }

    @Test void otherOriginsAreRejectedBeforeAnySessionLookup() throws Exception {
        var response = request("GET", "https://untrusted.example", null);
        assertEquals(403, response.getStatus());
        assertNull(response.getHeader("Access-Control-Allow-Origin"));
        verifyNoInteractions(sessions);
    }
}
