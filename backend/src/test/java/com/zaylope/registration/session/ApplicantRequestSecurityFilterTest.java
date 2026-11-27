package com.zaylope.registration.session;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;
class ApplicantRequestSecurityFilterTest {
    private final ApplicantRequestSecurityFilter filter=new ApplicantRequestSecurityFilter("http://localhost:5173");
    @Test void rejectsCrossOriginWritesAndSimpleForms() throws Exception {
        var request=new MockHttpServletRequest("POST","/api/v1/applicants/me/verification/mobile/send");request.setServletPath(request.getRequestURI());request.addHeader("Origin","https://untrusted.example");request.addHeader("X-Zaylope-Request","1");var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(a,b)->fail("Cross-origin write passed"));assertEquals(403,response.getStatus());assertTrue(response.getHeader("Cache-Control").contains("no-store"));
    }
    @Test void permitsApprovedJsonClientAndPreventsCaching() throws Exception {
        var request=new MockHttpServletRequest("POST","/api/v1/applicants/me/verification/mobile/verify");request.setServletPath(request.getRequestURI());request.addHeader("Origin","http://localhost:5173");request.addHeader("X-Zaylope-Request","1");var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(a,b)->b.getWriter().write("ok"));assertEquals("ok",response.getContentAsString());assertTrue(response.getHeader("Cache-Control").contains("no-store"));
    }
    @Test void rejectsWritesWithoutNonSimpleRequestHeader() throws Exception {
        var request=new MockHttpServletRequest("POST","/api/v1/applicants/register");request.setServletPath(request.getRequestURI());var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(a,b)->fail("Simple write passed"));assertEquals(403,response.getStatus());
    }
}
