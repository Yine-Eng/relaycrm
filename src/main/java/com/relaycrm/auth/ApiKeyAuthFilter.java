package com.relaycrm.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Stub authentication: every /api request must carry an X-Api-Key header that
 * matches one user. The user's organization and role come from the database,
 * never from the request, so a caller cannot claim to be in another organization.
 *
 * In production this would be replaced by Spring Security validating an OIDC
 * access token (see README).
 */
@Component
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Api-Key";

    private final UserRepository users;

    public ApiKeyAuthFilter(UserRepository users) {
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String apiKey = request.getHeader(HEADER);
        Optional<User> user = (apiKey == null || apiKey.isBlank())
                ? Optional.empty()
                : users.findByApiKeyHash(sha256Hex(apiKey));

        if (user.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(
                    "{\"title\":\"Unauthorized\",\"status\":401,\"detail\":\"Missing or invalid X-Api-Key header\"}");
            return;
        }

        User u = user.get();
        CurrentUserContext.set(new CurrentUser(u.getId(), u.getOrgId(), u.getRole()));
        try {
            chain.doFilter(request, response);
        } finally {
            CurrentUserContext.clear();
        }
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
