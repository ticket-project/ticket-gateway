package com.ticket.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.internal-auth")
public class GatewayInternalAuthProperties {

    private String issuer = "ticket-gateway";
    private String secretKey;
    private long expirationSeconds = 60L;
    private String coreAudience = "ticket-core";
    private String queueAudience = "ticket-queue";

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(final String issuer) {
        this.issuer = issuer;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(final String secretKey) {
        this.secretKey = secretKey;
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    public void setExpirationSeconds(final long expirationSeconds) {
        this.expirationSeconds = expirationSeconds;
    }

    public String getCoreAudience() {
        return coreAudience;
    }

    public void setCoreAudience(final String coreAudience) {
        this.coreAudience = coreAudience;
    }

    public String getQueueAudience() {
        return queueAudience;
    }

    public void setQueueAudience(final String queueAudience) {
        this.queueAudience = queueAudience;
    }
}
