package com.ticket.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.servlet.function.RouterFunction;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "ticket.gateway.ticket-api-uri=http://ticket-api:8080",
        "ticket.gateway.ticket-websocket-uri=http://ticket-api:8080",
        "ticket.gateway.queue-uri=http://ticket-queue:8090"
})
class TicketGatewayApplicationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void mvc_gateway_routes_are_registered_as_router_functions() {
        assertThat(applicationContext.getBeansOfType(RouterFunction.class))
                .containsKeys("ticketQueueRoute", "ticketWebsocketRoute", "ticketApiRoute");
    }
}