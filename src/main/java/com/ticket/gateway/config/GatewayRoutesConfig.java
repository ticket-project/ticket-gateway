package com.ticket.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.path;

@Configuration
@EnableConfigurationProperties({GatewayBackendProperties.class, GatewayCorsProperties.class})
public class GatewayRoutesConfig {

    @Bean
    public RouterFunction<ServerResponse> gatewayRoutes(final GatewayBackendProperties properties) {
        return route("ticket-queue")
                .route(path("/api/v1/queue/**"), http())
                .before(uri(properties.getQueueUri()))
                .build()
                .and(route("ticket-api-websocket")
                        .route(path("/ws/**"), http())
                        .before(uri(properties.getTicketWebsocketUri()))
                        .build())
                .and(route("ticket-api")
                        .route(path("/api/**"), http())
                        .before(uri(properties.getTicketApiUri()))
                        .build());
    }
}
