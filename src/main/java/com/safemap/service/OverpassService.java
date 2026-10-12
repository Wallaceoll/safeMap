package com.safemap.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Cliente da Overpass API (OpenStreetMap), usado apenas pela importação em
 * segundo plano dos pontos de apoio — o navegador nunca chama a Overpass.
 *
 * Os servidores públicos oscilam bastante (504/500), então cada consulta
 * percorre vários mirrors e faz novas rodadas com espera crescente.
 */
@Slf4j
@Service
public class OverpassService {

    private static final int ROUNDS = 3;
    private static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(30);

    private static final List<String> MIRRORS = List.of(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter",
            "https://overpass.private.coffee/api/interpreter"
            // maps.mail.ru ficou de fora: o certificado é da HARICA TLS RSA Root CA 2021,
            // ausente do truststore padrão do Java (falha com "PKIX path building failed").
    );

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Executa uma consulta Overpass QL.
     *
     * @return JSON da resposta, ou null se todos os mirrors falharem.
     */
    public String executar(String query, Duration timeout) throws InterruptedException {
        String form = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        Duration delay = FIRST_RETRY_DELAY;

        for (int round = 1; round <= ROUNDS; round++) {
            for (String mirror : MIRRORS) {
                String body = consultar(mirror, form, timeout);
                if (body != null) return body;
            }
            if (round < ROUNDS) {
                Thread.sleep(delay.toMillis());
                delay = delay.multipliedBy(2);
            }
        }
        return null;
    }

    private String consultar(String mirror, String form, Duration timeout) throws InterruptedException {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(mirror))
                    .timeout(timeout)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("User-Agent", "SafeMap/1.0 (https://safemap-production.up.railway.app)")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();

            // A Overpass pode responder 200 com um HTML de erro quando sobrecarregada
            if (response.statusCode() == 200 && body != null && body.trim().startsWith("{")) {
                return body;
            }
            log.warn("[overpass] Mirror {} retornou status {}", mirror, response.statusCode());
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[overpass] Erro ao consultar mirror {}: {}", mirror, e.getMessage());
        }
        return null;
    }
}
