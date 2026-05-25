package com.safemap.controller;

import com.safemap.service.RoutingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Controller responsável pela rota segura.
 */
@RestController
@RequestMapping("/api/rotas")
@RequiredArgsConstructor
public class RoutingController {

    private final RoutingService routingService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> obterRotas(
            @RequestParam double origemLat,
            @RequestParam double origemLng,
            @RequestParam double destinoLat,
            @RequestParam double destinoLng) {

        Map<String, Object> rotas = routingService.calculateRoutes(origemLat, origemLng, destinoLat, destinoLng);
        return ResponseEntity.ok(rotas);
    }
}
