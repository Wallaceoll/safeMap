package com.safemap.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Configuração do mapa base para o frontend.
 *
 * Com CARTO_API_KEY definida (Railway), usa o CARTO Positron (light_all).
 * Sem a chave, usa a Stadia Alidade Smooth, que funciona sem cadastro em localhost.
 *
 * A chave do mapa base fica visível no navegador (é assim com qualquer provedor de
 * tiles); a proteção é a restrição por domínio cadastrada no painel da CARTO.
 */
@RestController
@RequestMapping("/api/config")
public class MapaConfigController {

    private static final String OSM_ATTRIBUTION =
            "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors";

    @Value("${CARTO_API_KEY:}")
    private String cartoApiKey;

    @GetMapping("/mapa")
    public ResponseEntity<Map<String, Object>> mapa() {
        Map<String, Object> config;
        if (cartoApiKey != null && !cartoApiKey.isBlank()) {
            config = Map.of(
                    "tileUrl", "https://basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png?key=" + cartoApiKey,
                    "attribution", OSM_ATTRIBUTION + " &copy; <a href=\"https://carto.com/attributions\">CARTO</a>",
                    "maxZoom", 20
            );
        } else {
            config = Map.of(
                    "tileUrl", "https://tiles.stadiamaps.com/tiles/alidade_smooth/{z}/{x}/{y}{r}.png",
                    "attribution", "&copy; <a href=\"https://stadiamaps.com/\">Stadia Maps</a> &copy; <a href=\"https://openmaptiles.org/\">OpenMapTiles</a> " + OSM_ATTRIBUTION,
                    "maxZoom", 20
            );
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(config);
    }
}
