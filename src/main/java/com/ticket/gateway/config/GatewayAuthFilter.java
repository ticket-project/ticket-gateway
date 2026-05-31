package com.ticket.gateway.config;

import com.ticket.support.security.internalauth.InternalAuthTokenService;
import com.ticket.support.security.jwt.JwtMemberClaims;
import com.ticket.support.security.jwt.JwtTokenVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.cors.CorsUtils;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class GatewayAuthFilter extends OncePerRequestFilter {

    public static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenVerifier accessTokenVerifier;
    private final InternalAuthTokenService coreTokenService;
    private final InternalAuthTokenService queueTokenService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public GatewayAuthFilter(
            final JwtTokenVerifier accessTokenVerifier,
            final InternalAuthTokenService coreTokenService,
            final InternalAuthTokenService queueTokenService
    ) {
        this.accessTokenVerifier = Objects.requireNonNull(accessTokenVerifier, "accessTokenVerifier must not be null");
        this.coreTokenService = Objects.requireNonNull(coreTokenService, "coreTokenService must not be null");
        this.queueTokenService = Objects.requireNonNull(queueTokenService, "queueTokenService must not be null");
    }

    @Override
    protected void doFilterInternal(
            final HttpServletRequest request,
            final HttpServletResponse response,
            final FilterChain filterChain
    ) throws ServletException, IOException {
        if (CorsUtils.isPreFlightRequest(request) || !isGatewayApiRequest(request)) {
            filterChain.doFilter(withoutInternalAuth(request), response);
            return;
        }

        RouteTarget routeTarget = resolveRouteTarget(request);
        if (!requiresAuthentication(request)) {
            filterChain.doFilter(withoutInternalAuth(request), response);
            return;
        }

        String token = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try {
            JwtMemberClaims claims = accessTokenVerifier.verify(token);
            String internalToken = tokenService(routeTarget).issue(claims.memberId(), claims.role());
            filterChain.doFilter(withInternalAuth(request, "Bearer " + internalToken), response);
        } catch (RuntimeException exception) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }

    private boolean isGatewayApiRequest(final HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/") || path.startsWith("/actuator/") || path.startsWith("/ws/");
    }

    private RouteTarget resolveRouteTarget(final HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/api/v1/queue/")) {
            return RouteTarget.QUEUE;
        }
        return RouteTarget.CORE;
    }

    private boolean requiresAuthentication(final HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (HttpMethod.OPTIONS.matches(method)) {
            return false;
        }
        if (pathMatcher.match("/api/v1/queue/performances/*/enter", path)) {
            return HttpMethod.POST.matches(method);
        }
        if (path.startsWith("/api/v1/queue/")) {
            return false;
        }
        if (HttpMethod.GET.matches(method) && pathMatcher.match("/api/v1/performances/*/seats/status", path)) {
            return true;
        }
        return path.startsWith("/api/") && !isCorePublicRequest(method, path);
    }

    private boolean isCorePublicRequest(final String method, final String path) {
        if (matches(path, "/", "/api/swagger-ui.html", "/api/swagger-ui/**", "/api/api-docs/**",
                "/ws/**", "/api/images/**", "/actuator/health", "/actuator/health/**",
                "/actuator/info", "/actuator/prometheus")) {
            return true;
        }
        if (matches(path, "/api/v1/auth/signup", "/api/v1/auth/login", "/api/v1/auth/refresh",
                "/api/v1/auth/oauth2/token", "/api/v1/auth/social/urls",
                "/api/v1/auth/oauth2/authorize/**", "/api/v1/auth/oauth2/callback/*")) {
            return true;
        }
        return HttpMethod.GET.matches(method)
                && matches(path, "/api/v1/shows", "/api/v1/shows/**",
                "/api/v1/performances", "/api/v1/performances/**",
                "/api/v1/genres", "/api/v1/genres/**",
                "/api/v1/meta", "/api/v1/meta/**");
    }

    private boolean matches(final String path, final String... patterns) {
        for (String pattern : patterns) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private InternalAuthTokenService tokenService(final RouteTarget routeTarget) {
        return switch (routeTarget) {
            case CORE -> coreTokenService;
            case QUEUE -> queueTokenService;
        };
    }

    private String extractBearerToken(final String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        return token.isBlank() ? null : token;
    }

    private HttpServletRequest withoutInternalAuth(final HttpServletRequest request) {
        return new HeaderOverrideRequest(request, Map.of(), Set.of(INTERNAL_AUTH_HEADER));
    }

    private HttpServletRequest withInternalAuth(final HttpServletRequest request, final String internalAuth) {
        return new HeaderOverrideRequest(request, Map.of(INTERNAL_AUTH_HEADER, List.of(internalAuth)),
                Set.of(INTERNAL_AUTH_HEADER));
    }

    private enum RouteTarget {
        CORE,
        QUEUE
    }

    private static final class HeaderOverrideRequest extends HttpServletRequestWrapper {

        private final Map<String, List<String>> setHeaders;
        private final Set<String> removedHeaderNames;

        private HeaderOverrideRequest(
                final HttpServletRequest request,
                final Map<String, List<String>> setHeaders,
                final Set<String> removedHeaderNames
        ) {
            super(request);
            this.setHeaders = setHeaders;
            this.removedHeaderNames = normalize(removedHeaderNames);
        }

        @Override
        public String getHeader(final String name) {
            List<String> values = getSetHeaderValues(name);
            if (values != null) {
                return values.isEmpty() ? null : values.getFirst();
            }
            if (isRemoved(name)) {
                return null;
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(final String name) {
            List<String> values = getSetHeaderValues(name);
            if (values != null) {
                return Collections.enumeration(values);
            }
            if (isRemoved(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            LinkedHashSet<String> names = new LinkedHashSet<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original.hasMoreElements()) {
                String name = original.nextElement();
                if (!isRemoved(name)) {
                    names.add(name);
                }
            }
            names.addAll(setHeaders.keySet());
            return Collections.enumeration(names);
        }

        private List<String> getSetHeaderValues(final String name) {
            for (Map.Entry<String, List<String>> entry : setHeaders.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
            return null;
        }

        private boolean isRemoved(final String name) {
            return removedHeaderNames.contains(normalize(name));
        }

        private static Set<String> normalize(final Set<String> values) {
            Set<String> normalized = new LinkedHashSet<>();
            for (String value : values) {
                normalized.add(normalize(value));
            }
            return normalized;
        }

        private static String normalize(final String value) {
            return value.toLowerCase(Locale.ROOT);
        }
    }
}
