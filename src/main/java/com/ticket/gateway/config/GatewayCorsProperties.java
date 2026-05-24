package com.ticket.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "ticket.gateway.cors")
public class GatewayCorsProperties {

    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(final List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}