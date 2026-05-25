package com.ticket.gateway;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class TicketGatewayApplicationTest {

    private static final TestBackend TICKET_BACKEND = TestBackend.start("ticket");
    private static final TestBackend QUEUE_BACKEND = TestBackend.start("queue");

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private CorsFilter corsFilter;

    private MockMvc mockMvc;

    @DynamicPropertySource
    static void gatewayTargets(final DynamicPropertyRegistry registry) {
        registry.add("ticket.gateway.ticket-api-uri", TICKET_BACKEND::uri);
        registry.add("ticket.gateway.ticket-websocket-uri", TICKET_BACKEND::uri);
        registry.add("ticket.gateway.queue-uri", QUEUE_BACKEND::uri);
    }

    @AfterAll
    static void stopBackends() {
        TICKET_BACKEND.stop();
        QUEUE_BACKEND.stop();
    }

    @BeforeEach
    void setUpMockMvc() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(corsFilter)
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
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        }
    }
}
