package com.ticket.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ticket.gateway")
public class GatewayBackendProperties {

    private String ticketApiUri = "http://localhost:8080";
    private String ticketWebsocketUri = "http://localhost:8080";
    private String queueUri = "http://localhost:8090";

    public String getTicketApiUri() {
        return ticketApiUri;
    }

    public void setTicketApiUri(final String ticketApiUri) {
        this.ticketApiUri = ticketApiUri;
    }

    public String getTicketWebsocketUri() {
        return ticketWebsocketUri;
    }

    public void setTicketWebsocketUri(final String ticketWebsocketUri) {
        this.ticketWebsocketUri = ticketWebsocketUri;
    }

    public String getQueueUri() {
        return queueUri;
    }

    public void setQueueUri(final String queueUri) {
        this.queueUri = queueUri;
    }
}
