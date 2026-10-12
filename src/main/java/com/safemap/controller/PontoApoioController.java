package com.safemap.controller;

import com.safemap.config.BrasilBounds;
import com.safemap.dto.response.PontoApoioResponse;
import com.safemap.repository.PontoApoioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Pontos de apoio (hospitais, delegacias, acolhimento) armazenados no banco.
 * Ex.: GET /api/apoio?bbox=-23.60,-46.70,-23.55,-46.65  (sul,oeste,norte,leste)
 */
@RestController
@RequestMapping("/api/apoio")
@RequiredArgsConstructor
public class PontoApoioController {

    /** Tamanho máximo de cada lado do bbox, em graus (~55 km). */
    private static final double MAX_BBOX_SPAN = 0.5;
    private static final int MAX_RESULTADOS = 2000;

    private final PontoApoioRepository repository;
    private final BrasilBounds brasilBounds;

    @GetMapping
    public ResponseEntity<?> buscar(@RequestParam String bbox) {
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            return erro("bbox deve ter o formato sul,oeste,norte,leste");
        }

        double south, west, north, east;
        try {
            south = Double.parseDouble(parts[0].trim());
            west = Double.parseDouble(parts[1].trim());
            north = Double.parseDouble(parts[2].trim());
            east = Double.parseDouble(parts[3].trim());
        } catch (NumberFormatException e) {
            return erro("bbox contém valores inválidos");
        }

        if (south >= north || west >= east) {
            return erro("bbox inválido");
        }
        if (north - south > MAX_BBOX_SPAN || east - west > MAX_BBOX_SPAN) {
            return erro("bbox muito grande; aproxime o mapa");
        }
        if (!brasilBounds.intersecta(south, west, north, east)) {
            return erro("bbox fora do Brasil");
        }

        List<PontoApoioResponse> pontos = repository
                .findInBounds(south, west, north, east, PageRequest.of(0, MAX_RESULTADOS))
                .stream()
                .map(PontoApoioResponse::fromEntity)
                .toList();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(pontos);
    }

    private ResponseEntity<Map<String, String>> erro(String mensagem) {
        return ResponseEntity.badRequest().body(Map.of("erro", mensagem));
    }
}
