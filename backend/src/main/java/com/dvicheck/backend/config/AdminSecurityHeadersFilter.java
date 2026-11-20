package com.dvicheck.backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Response headers for the admin dashboard (Day 38).
 *
 * Ordered ahead of Spring Security's filter chain, so the headers are also on responses the
 * security chain ends itself (e.g. a 401 on /api/admin/**). Spring Security's own cache-control
 * writer leaves an existing Cache-Control header alone; its X-Frame-Options DENY and
 * X-Content-Type-Options nosniff defaults still apply on top.
 *
 * No CORS here or anywhere: the page is same-origin.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class AdminSecurityHeadersFilter extends OncePerRequestFilter {

    static final String ADMIN_CSP = "default-src 'none'; script-src 'self'; style-src 'self'; "
        + "connect-src 'self'; img-src 'self' data:; base-uri 'none'; form-action 'none'; "
        + "frame-ancestors 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/admin")) {
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Robots-Tag", "noindex");
        } else if (path.startsWith("/admin")) {
            response.setHeader("Content-Security-Policy", ADMIN_CSP);
            response.setHeader("X-Robots-Tag", "noindex, nofollow");
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }
}
