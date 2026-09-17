package com.vidasalud.bff.config;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.*;

/**
 * Envuelve al NimbusJwtDecoder real, pero NO se conecta a Azure al
 * arrancar la aplicacion. Solo construye el decoder de verdad la
 * PRIMERA VEZ que llega un token a validar. Asi la app puede levantar
 * sin tener el Tenant/Client ID reales configurados todavia, y solo
 * falla si de verdad llega una peticion con Authorization: Bearer ...
 * antes de que Azure este bien configurado.
 */
public class LazyJwtDecoder implements JwtDecoder {

    private final String issuer;
    private final String audience;
    private volatile JwtDecoder delegate;

    public LazyJwtDecoder(String issuer, String audience) {
        this.issuer = issuer;
        this.audience = audience;
    }

    @Override
    public Jwt decode(String token) throws JwtException {
        return getDelegate().decode(token);
    }

    private JwtDecoder getDelegate() {
        JwtDecoder result = delegate;
        if (result == null) {
            synchronized (this) {
                result = delegate;
                if (result == null) {
                    result = buildRealDecoder();
                    delegate = result;
                }
            }
        }
        return result;
    }

    private JwtDecoder buildRealDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> withAudience = jwt ->
            jwt.getAudience().contains(audience)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_audience", "El audience del token no coincide", null));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }
}
