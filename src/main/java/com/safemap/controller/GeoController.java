package com.safemap.controller;

import com.safemap.service.ValidacaoGeograficaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validação geográfica pública: GET /api/geo/validar?lat=..&lng=..
 *
 * 200 → {"valid": true, "withinBrazil": true, "state": {"code": "26", "name": "Pernambuco", "uf": "PE"}}
 * 200 → {"valid": true, "withinBrazil": false, "state": null}
 * 400 → coordenadas inválidas · 503 → limites das UFs indisponíveis (GlobalException)
 */
@RestController
@RequestMapping("/api/geo")
@RequiredArgsConstructor
public class GeoController {

    private final ValidacaoGeograficaService validacaoGeografica;

    @GetMapping("/validar")
    public ResponseEntity<Map<String, Object>> validar(@RequestParam Double lat, @RequestParam Double lng) {
        ValidacaoGeograficaService.Resultado r = validacaoGeografica.validar(lat, lng);

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("valid", true);
        resposta.put("withinBrazil", r.dentroDoBrasil());
        resposta.put("state", r.dentroDoBrasil()
                ? Map.of("code", r.codigoIbge(), "name", r.nome(), "uf", r.uf())
                : null);
        return ResponseEntity.ok(resposta);
    }
}
