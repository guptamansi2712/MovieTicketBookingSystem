package com.example.movieticket.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class HeaderUserFilter extends OncePerRequestFilter {
    private final JdbcTemplate jdbcTemplate;

    public HeaderUserFilter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String userId = request.getHeader("X-User-Id");
        if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            var users = jdbcTemplate.queryForList("""
                    SELECT id, email, role
                    FROM app_users
                    WHERE id = ?
                    """, Long.parseLong(userId));
            if (!users.isEmpty()) {
                var user = users.get(0);
                var principal = new AppPrincipal(
                        ((Number) user.get("id")).longValue(),
                        (String) user.get("email"),
                        (String) user.get("role"));
                var auth = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + principal.role())));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        filterChain.doFilter(request, response);
    }
}
