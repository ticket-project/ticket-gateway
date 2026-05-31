package com.ticket.gateway.config;

import com.ticket.support.security.internalauth.InternalAuthTokenProperties;
import com.ticket.support.security.internalauth.InternalAuthTokenService;
import com.ticket.support.security.jwt.JwtProperties;
import com.ticket.support.security.jwt.JwtTokenVerifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({GatewayJwtProperties.class, GatewayInternalAuthProperties.class})
public class GatewayAuthConfig {

    @Bean
    public GatewayAuthFilter gatewayAuthFilter(
            final GatewayJwtProperties jwtProperties,
            final GatewayInternalAuthProperties internalAuthProperties
    ) {
        JwtTokenVerifier accessTokenVerifier = new JwtTokenVerifier(new JwtProperties(
                jwtProperties.getIssuer(),
                jwtProperties.getSecretKey(),
                jwtProperties.getAccessTokenExpirationSeconds()
        ));
        InternalAuthTokenService coreTokenService = internalAuthTokenService(
                internalAuthProperties,
                internalAuthProperties.getCoreAudience()
        );
        InternalAuthTokenService queueTokenService = internalAuthTokenService(
                internalAuthProperties,
                internalAuthProperties.getQueueAudience()
        );
        return new GatewayAuthFilter(accessTokenVerifier, coreTokenService, queueTokenService);
    }

    private InternalAuthTokenService internalAuthTokenService(
            final GatewayInternalAuthProperties properties,
            final String audience
    ) {
        return new InternalAuthTokenService(new InternalAuthTokenProperties(
                properties.getIssuer(),
                audience,
                properties.getSecretKey(),
                properties.getExpirationSeconds()
        ));
    }
}
