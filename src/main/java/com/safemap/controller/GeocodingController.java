package com.safemap.controller;

import com.safemap.config.BrasilBounds;
import com.safemap.service.OpenRouteService;
import com.safemap.util.TtlCache;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Busca de endereços no Brasil, intermediada pelo backend (Openrouteservice).
 *
 * - GET /api/geocode/busca?q=...&autocomplete=true&lat=..&lng=..
 * - GET /api/geocode/reverso?lat=..&lng=..
 *
 * Retorna 503 quando a chave ORS_API_KEY não está configurada; nesse caso o
 * frontend usa a busca pública (Nominatim) como alternativa.
 */
@RestController
@RequestMapping("/api/geocode")
@RequiredArgsConstructor
public class GeocodingController {

    private static final int MAX_QUERY_LENGTH = 200;

    private final OpenRouteService openRouteService;
    private final BrasilBounds brasilBounds;

    private final TtlCache<String, List<Map<String, Object>>> cacheBusca = new TtlCache<>(Duration.ofHours(24), 5000);
    private final TtlCache<String, Map<String, Object>> cacheReverso = new TtlCache<>(Duration.ofDays(7), 5000);

    @GetMapping("/busca")
    public ResponseEntity<?> buscar(@RequestParam String q,
                                    @RequestParam(defaultValue = "false") boolean autocomplete,
                                    @RequestParam(required = false) Double lat,
                                    @RequestParam(required = false) Double lng) {
        if (!openRouteService.isConfigurado()) return indisponivel();

        String texto = q.trim();
        if (texto.length() < 3 || texto.length() > MAX_QUERY_LENGTH) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Texto de busca inválido"));
        }
        if (lat != null && lng != null && !brasilBounds.contem(lat, lng)) {
            lat = null;
            lng = null;
        }

        // O foco é arredondado (~10 km) para aproveitar melhor o cache
        String chave = String.format(Locale.US, "%s|%b|%s|%s", texto.toLowerCase(Locale.ROOT), autocomplete,
                lat == null ? "" : String.format(Locale.US, "%.1f", lat),
                lng == null ? "" : String.format(Locale.US, "%.1f", lng));

        List<Map<String, Object>> resultados = cacheBusca.get(chave);
        if (resultados == null) {
            resultados = openRouteService.geocodificar(texto, autocomplete, lat, lng);
            if (resultados == null) return indisponivel();
            cacheBusca.put(chave, resultados);
        }
        return ResponseEntity.ok(resultados);
    }

    @GetMapping("/reverso")
    public ResponseEntity<?> reverso(@RequestParam double lat, @RequestParam double lng) {
        if (!openRouteService.isConfigurado()) return indisponivel();
        if (!brasilBounds.contem(lat, lng)) {
            return ResponseEntity.badRequest().body(Map.of("erro", "Coordenadas fora do Brasil"));
        }

        String chave = String.format(Locale.US, "%.4f,%.4f", lat, lng);
        Map<String, Object> resultado = cacheReverso.get(chave);
        if (resultado == null) {
            resultado = openRouteService.geocodificarReverso(lat, lng);
            if (resultado == null) return indisponivel();
            cacheReverso.put(chave, resultado);
        }
        return ResponseEntity.ok(resultado);
    }

    private ResponseEntity<Map<String, String>> indisponivel() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("erro", "Serviço de busca de endereços indisponível"));
    }
}
