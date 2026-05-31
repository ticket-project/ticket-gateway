package com.ticket.gateway;

import com.ticket.support.security.internalauth.InternalAuthClaims;
import com.ticket.support.security.internalauth.InternalAuthTokenProperties;
import com.ticket.support.security.internalauth.InternalAuthTokenService;
import com.ticket.support.security.jwt.JwtAccessTokenIssuer;
import com.ticket.support.security.jwt.JwtProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.ticket.gateway.config.GatewayAuthFilter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.function.RouterFunction;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class TicketGatewayApplicationTest {

    private static final String INTERNAL_AUTH_HEADER = "X-Internal-Auth";
    private static final String EXTERNAL_JWT_SECRET_KEY = "12345678901234567890123456789012";
    private static final String INTERNAL_AUTH_SECRET_KEY = "abcdefabcdefabcdefabcdefabcdef12";

    private static final TestBackend TICKET_BACKEND = TestBackend.start("ticket");
    private static final TestBackend QUEUE_BACKEND = TestBackend.start("queue");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private CorsFilter corsFilter;

    @Autowired
    private GatewayAuthFilter gatewayAuthFilter;

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void gatewayTargets(final DynamicPropertyRegistry registry) {
        registry.add("ticket.gateway.ticket-api-uri", TICKET_BACKEND::uri);
        registry.add("ticket.gateway.ticket-websocket-uri", TICKET_BACKEND::uri);
        registry.add("ticket.gateway.queue-uri", QUEUE_BACKEND::uri);
        registry.add("security.jwt.issuer", () -> "ticket");
        registry.add("security.jwt.secret-key", () -> EXTERNAL_JWT_SECRET_KEY);
        registry.add("security.jwt.access-token-expiration-seconds", () -> "1800");
        registry.add("security.internal-auth.issuer", () -> "ticket-gateway");
        registry.add("security.internal-auth.secret-key", () -> INTERNAL_AUTH_SECRET_KEY);
        registry.add("security.internal-auth.expiration-seconds", () -> "60");
        registry.add("security.internal-auth.core-audience", () -> "ticket-core");
        registry.add("security.internal-auth.queue-audience", () -> "ticket-queue");
    }

    @AfterAll
    static void stopBackends() {
        TICKET_BACKEND.stop();
        QUEUE_BACKEND.stop();
    }

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(corsFilter, gatewayAuthFilter)
                .build();
    }

    @Test
    void mvc_gateway_routes_are_registered_as_single_application_router_function() {
        assertThat(applicationContext.getBeansOfType(RouterFunction.class))
                .containsKey("gatewayRoutes")
                .doesNotContainKeys("ticketQueueRoute", "ticketWebsocketRoute", "ticketApiRoute");
    }

    @Test
    void queue_route_is_selected_before_general_api_route() throws Exception {
        mockMvc.perform(get("/api/v1/queue/ping"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Test-Backend", "queue"))
                .andExpect(content().string("queue:/api/v1/queue/ping"));
    }

    @Test
    void api_route_is_forwarded_to_ticket_backend() throws Exception {
        mockMvc.perform(get("/api/v1/shows"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Test-Backend", "ticket"))
                .andExpect(content().string("ticket:/api/v1/shows"));
    }

    @Test
    void public_api_removes_client_supplied_internal_auth_header() throws Exception {
        mockMvc.perform(get("/api/v1/shows")
                        .header(INTERNAL_AUTH_HEADER, "Bearer forged-internal-token"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("X-Received-Internal-Auth"));
    }

    @Test
    void protected_queue_enter_exchanges_access_token_to_queue_internal_auth_token() throws Exception {
        String accessToken = accessToken(7L, "MEMBER");

        String forwardedInternalAuth = mockMvc.perform(post("/api/v1/queue/performances/1/enter")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(INTERNAL_AUTH_HEADER, "Bearer forged-internal-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Test-Backend", "queue"))
                .andReturn()
                .getResponse()
                .getHeader("X-Received-Internal-Auth");

        assertThat(forwardedInternalAuth).startsWith("Bearer ");
        InternalAuthClaims claims = internalAuthVerifier("ticket-queue")
                .verify(forwardedInternalAuth.substring("Bearer ".length()));
        assertThat(claims.memberId()).isEqualTo(7L);
        assertThat(claims.role()).isEqualTo("MEMBER");
        assertThat(claims.audience()).isEqualTo("ticket-queue");
    }

    @Test
    void protected_queue_enter_requires_access_token() throws Exception {
        mockMvc.perform(post("/api/v1/queue/performances/1/enter"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void websocket_route_is_forwarded_to_ticket_backend_for_frontend_main() throws Exception {
        mockMvc.perform(get("/ws/info"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Test-Backend", "ticket"))
                .andExpect(content().string("ticket:/ws/info"));
    }

    @Test
    void cors_exposes_queue_session_and_admission_headers() throws Exception {
        mockMvc.perform(get("/api/v1/queue/ping")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsString("X-Queue-Session")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                        containsString("X-Admission-Token")));
    }

    private static final class TestBackend {

        private final String name;
        private final HttpServer server;

        private TestBackend(final String name, final HttpServer server) {
            this.name = name;
            this.server = server;
        }

        private static TestBackend start(final String name) {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
                TestBackend backend = new TestBackend(name, server);
                server.createContext("/", backend::handle);
                server.start();
                return backend;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private String uri() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        private void stop() {
            server.stop(0);
        }

        private void handle(final HttpExchange exchange) throws IOException {
            byte[] body = (name + ":" + exchange.getRequestURI().getPath()).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("X-Test-Backend", name);
            String internalAuth = exchange.getRequestHeaders().getFirst(INTERNAL_AUTH_HEADER);
            if (internalAuth != null) {
                exchange.getResponseHeaders().add("X-Received-Internal-Auth", internalAuth);
            }
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }

    private String accessToken(final Long memberId, final String role) {
        JwtProperties properties = new JwtProperties("ticket", EXTERNAL_JWT_SECRET_KEY, 1800L);
        return new JwtAccessTokenIssuer(properties).issue(memberId, role);
    }

    private InternalAuthTokenService internalAuthVerifier(final String audience) {
        InternalAuthTokenProperties properties = new InternalAuthTokenProperties(
                "ticket-gateway",
                audience,
                INTERNAL_AUTH_SECRET_KEY,
                60L
        );
        return new InternalAuthTokenService(properties);
    }
}
