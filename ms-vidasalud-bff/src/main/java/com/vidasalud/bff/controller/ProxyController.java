package com.vidasalud.bff.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;

/**
 * El BFF NO implementa la lógica de negocio: reenvía la petición (ya
 * autenticada y autorizada por SecurityConfig) al microservicio de
 * dominio correspondiente, reenviando también el Authorization header
 * para que ese microservicio vuelva a validar el JWT (defensa en
 * profundidad, tal como pide la pauta).
 */
@RestController
public class ProxyController {

    private final RestTemplate restTemplate;

    @Value("${services.appointments-url}")
    private String appointmentsUrl;

    @Value("${services.catalog-url}")
    private String catalogUrl;

    public ProxyController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @RequestMapping(value = "/api/appointments/**", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<String> proxyAppointments(HttpServletRequest request,
                                                      @RequestHeader HttpHeaders headers,
                                                      @RequestBody(required = false) String body) {
        return forward(appointmentsUrl, request, headers, body);
    }

    @RequestMapping(value = "/api/catalog/**", method = {
            RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE})
    public ResponseEntity<String> proxyCatalog(HttpServletRequest request,
                                                 @RequestHeader HttpHeaders headers,
                                                 @RequestBody(required = false) String body) {
        return forward(catalogUrl, request, headers, body);
    }

    private ResponseEntity<String> forward(String baseUrl, HttpServletRequest request,
                                            HttpHeaders headers, String body) {
        String targetUrl = baseUrl + request.getRequestURI()
                + (request.getQueryString() != null ? "?" + request.getQueryString() : "");

        HttpHeaders forwardHeaders = new HttpHeaders();
        forwardHeaders.addAll(headers);
        forwardHeaders.remove(HttpHeaders.HOST);

        HttpEntity<String> entity = new HttpEntity<>(body, forwardHeaders);

        return restTemplate.exchange(
                UriComponentsBuilder.fromHttpUrl(targetUrl).build().toUri(),
                HttpMethod.valueOf(request.getMethod()),
                entity,
                String.class);
    }
}
