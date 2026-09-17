package com.vidasalud.bff.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Valida el JWT emitido por Azure Entra ID: firma, issuer y audience.
 * Además mapea el claim "roles" del token a authorities de Spring
 * (prefijo ROLE_) para poder usar hasAnyAuthority/hasRole.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Value("${azure.issuer}")
    private String issuer;

    @Value("${azure.audience}")
    private String audience;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/audit/**", "/api/report/**")
                    .hasAnyAuthority("ROLE_Admin", "ROLE_Auditor")
                .requestMatchers(HttpMethod.PUT, "/api/appointments/*/status")
                    .hasAnyAuthority("ROLE_Admin", "ROLE_Operador")
                    .requestMatchers(HttpMethod.POST, "/api/catalog/**")
                    .hasAuthority("ROLE_Admin")
                    .requestMatchers(HttpMethod.PUT, "/api/catalog/**")
                    .hasAuthority("ROLE_Admin")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        return http.build();
    }

    // OJO: no se conecta a Azure al arrancar. Solo lo hace cuando llega
    // el primer token real a validar (ver LazyJwtDecoder.java).
    @Bean
    public JwtDecoder jwtDecoder() {
        return new LazyJwtDecoder(issuer, audience);
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
