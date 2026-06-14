package com.ticket.gateway.config;

import com.ticket.support.token.passport.PassportTokenProperties;
import com.ticket.support.token.passport.PassportTokenService;
import com.ticket.support.token.jwt.JwtProperties;
import com.ticket.support.token.jwt.JwtTokenVerifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({GatewayJwtProperties.class, GatewayPassportProperties.class})
public class GatewayAuthConfig {

    @Bean
    public GatewayAuthFilter gatewayAuthFilter(
            final GatewayJwtProperties jwtProperties,
            final GatewayPassportProperties passportProperties
    ) {
        JwtTokenVerifier accessTokenVerifier = new JwtTokenVerifier(new JwtProperties(
                jwtProperties.getIssuer(),
                jwtProperties.getSecretKey(),
                jwtProperties.getAccessTokenExpirationSeconds()
        ));
        PassportTokenService coreTokenService = passportTokenService(
                passportProperties,
                passportProperties.getCoreAudience()
        );
        PassportTokenService queueTokenService = passportTokenService(
                passportProperties,
                passportProperties.getQueueAudience()
        );
        return new GatewayAuthFilter(accessTokenVerifier, coreTokenService, queueTokenService);
    }

    private PassportTokenService passportTokenService(
            final GatewayPassportProperties properties,
            final String audience
    ) {
        return new PassportTokenService(new PassportTokenProperties(
                properties.getIssuer(),
                audience,
                properties.getSecretKey(),
                properties.getExpirationSeconds()
        ));
    }
}
