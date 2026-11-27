package com.zaylope.registration.session;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
/** Never cache session API responses; browser writes require an exact origin and non-simple header. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApplicantRequestSecurityFilter extends OncePerRequestFilter {
    private final String allowedOrigin;
    public ApplicantRequestSecurityFilter(@Value("${app.cors.allowed-origin:http://localhost:5173}") String allowedOrigin) { this.allowedOrigin=allowedOrigin; }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        if(!request.getServletPath().startsWith("/api/v1/")) { chain.doFilter(request,response);return; }
        if(request.getServletPath().startsWith("/api/v1/webhooks/")) { chain.doFilter(request,response);return; }
        response.setHeader("Cache-Control","no-store, private"); response.setHeader("Pragma","no-cache");
        response.setHeader("X-Content-Type-Options","nosniff"); response.setHeader("Referrer-Policy","no-referrer");
        boolean write=!java.util.Set.of("GET","HEAD","OPTIONS").contains(request.getMethod());
        String origin=request.getHeader("Origin");
        if(write && (!"1".equals(request.getHeader("X-Zaylope-Request")) || origin!=null&&!allowedOrigin.equals(origin))) {
            response.setStatus(403);response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write("{\"status\":403,\"code\":\"REQUEST_ORIGIN_REJECTED\",\"detail\":\"Use the approved application origin and request header.\"}");return;
        }
        chain.doFilter(request,response);
    }
}
