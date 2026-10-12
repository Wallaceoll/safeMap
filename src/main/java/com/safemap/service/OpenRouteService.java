package com.safemap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cliente do Openrouteservice (https://openrouteservice.org): geocodificação e rotas a pé.
 *
 * A chave fica apenas no backend (variável de ambiente ORS_API_KEY no Railway).
 * As respostas são convertidas para os formatos que o frontend já usa:
 * - geocodificação → itens no formato do Nominatim ({lat, lon, display_name, type, address{...}})
 * - rotas → formato do OSRM ({routes: [{distance, duration, geometry}]})
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenRouteService {

    private static final String BASE_URL = "https://api.openrouteservice.org";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${ORS_API_KEY:}")
    private String apiKey;

    public boolean isConfigurado() {
        return apiKey != null && !apiKey.isBlank();
    }

    // --------------------------------------------------------
    //  Geocodificação
    // --------------------------------------------------------

    /**
     * Busca endereços no Brasil.
     *
     * @param autocomplete true para sugestões enquanto o usuário digita
     * @param focoLat/focoLng prioriza resultados próximos a este ponto (opcional)
     * @return itens no formato Nominatim, ou null em caso de falha
     */
    public List<Map<String, Object>> geocodificar(String texto, boolean autocomplete, Double focoLat, Double focoLng) {
        StringBuilder url = new StringBuilder(BASE_URL)
                .append(autocomplete ? "/geocode/autocomplete" : "/geocode/search")
                .append("?text=").append(URLEncoder.encode(texto, StandardCharsets.UTF_8))
                .append("&boundary.country=BR&size=10&lang=pt");
        if (focoLat != null && focoLng != null) {
            url.append(String.format(Locale.US, "&focus.point.lat=%.5f&focus.point.lon=%.5f", focoLat, focoLng));
        }

        JsonNode root = get(url.toString());
        return root == null ? null : converterFeatures(root);
    }

    /** Endereço de uma coordenada (formato Nominatim), ou null. */
    public Map<String, Object> geocodificarReverso(double lat, double lng) {
        String url = BASE_URL + String.format(Locale.US,
                "/geocode/reverse?point.lat=%.6f&point.lon=%.6f&size=1&boundary.country=BR&lang=pt", lat, lng);
        JsonNode root = get(url);
        if (root == null) return null;
        List<Map<String, Object>> itens = converterFeatures(root);
        return itens.isEmpty() ? null : itens.get(0);
    }

    List<Map<String, Object>> converterFeatures(JsonNode root) {
        List<Map<String, Object>> itens = new ArrayList<>();
        for (JsonNode feature : root.path("features")) {
            JsonNode coords = feature.path("geometry").path("coordinates");
            JsonNode p = feature.path("properties");
            if (coords.size() < 2) continue;

            String layer = p.path("layer").asText("");
            boolean cidade = List.of("locality", "localadmin", "county").contains(layer);

            Map<String, Object> address = new LinkedHashMap<>();
            address.put("road", texto(p, "street"));
            address.put("house_number", texto(p, "housenumber"));
            address.put("suburb", texto(p, "neighbourhood", "borough"));
            address.put("city", texto(p, "locality", "localadmin", "county"));
            address.put("state", texto(p, "region_a", "region"));
            address.put("country_code", "BRA".equals(p.path("country_a").asText()) ? "br" : p.path("country_a").asText("").toLowerCase());
            if (address.get("road") == null && "venue".equals(layer)) {
                address.put("road", texto(p, "name"));
            }

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("lat", coords.get(1).asDouble());
            item.put("lon", coords.get(0).asDouble());
            item.put("display_name", texto(p, "label", "name"));
            // Mesmos "class"/"type" do Nominatim, para o filtro de resultados amplos do frontend
            item.put("class", cidade ? "place" : layer);
            item.put("type", cidade ? "city" : layer);
            item.put("address", address);
            itens.add(item);
        }
        return itens;
    }

    // --------------------------------------------------------
    //  Rotas a pé
    // --------------------------------------------------------

    /**
     * Calcula uma rota a pé passando pelos pontos informados ([lng, lat]).
     * Com exatamente 2 pontos, pede também rotas alternativas.
     *
     * @return resposta no formato OSRM ({code, routes}), ou null em caso de falha
     */
    public Map<String, Object> rotaAPe(List<double[]> pontos) {
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode coordinates = body.putArray("coordinates");
        for (double[] ponto : pontos) {
            coordinates.addArray().add(ponto[0]).add(ponto[1]);
        }
        if (pontos.size() == 2) {
            body.putObject("alternative_routes")
                    .put("target_count", 2)
                    .put("share_factor", 0.6)
                    .put("weight_factor", 1.6);
        }

        JsonNode root = post(BASE_URL + "/v2/directions/foot-walking/geojson", body.toString());
        return root == null ? null : converterRotas(root);
    }

    Map<String, Object> converterRotas(JsonNode root) {
        List<Map<String, Object>> routes = new ArrayList<>();
        for (JsonNode feature : root.path("features")) {
            JsonNode summary = feature.path("properties").path("summary");
            Map<String, Object> route = new LinkedHashMap<>();
            route.put("distance", summary.path("distance").asDouble(0));
            route.put("duration", summary.path("duration").asDouble(0));
            route.put("geometry", objectMapper.convertValue(feature.path("geometry"), Map.class));
            routes.add(route);
        }
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("code", routes.isEmpty() ? "NoRoute" : "Ok");
        resposta.put("routes", routes);
        return resposta;
    }

    // --------------------------------------------------------
    //  HTTP
    // --------------------------------------------------------

    private JsonNode get(String url) {
        return enviar(HttpRequest.newBuilder().uri(URI.create(url)).GET());
    }

    private JsonNode post(String url, String json) {
        return enviar(HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
    }

    private JsonNode enviar(HttpRequest.Builder builder) {
        if (!isConfigurado()) return null;
        try {
            HttpRequest request = builder
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", apiKey)
                    .header("Accept", "application/json, application/geo+json")
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            }
            // Não registra a URL completa para não expor parâmetros
            log.warn("[ors] {} retornou status {}", request.uri().getPath(), response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("[ors] Erro na chamada: {}", e.getMessage());
        }
        return null;
    }

    private String texto(JsonNode node, String... campos) {
        for (String campo : campos) {
            String valor = node.path(campo).asText("").trim();
            if (!valor.isEmpty()) return valor;
        }
        return null;
    }
}
