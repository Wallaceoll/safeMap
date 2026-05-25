package com.safemap.controller;

import com.safemap.dto.request.OcorrenciaRequest;
import com.safemap.dto.response.OcorrenciaResponse;
import com.safemap.service.OcorrenciaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller responsável pelos endpoints de ocorrências.
 * 
 * Endpoints:
 *  GET  /api/ocorrencias - Lista todas as ocorrências cadastrada
 *  POST /api/ocorrencias - Cadastra uma nova ocorrência (requer autenticação JWT)
 */
@RestController
@RequestMapping("/api/ocorrencias")
@RequiredArgsConstructor
public class OcorrenciaController {

    private final OcorrenciaService ocorrenciaService;

    @GetMapping
    public ResponseEntity<List<OcorrenciaResponse>> listar() {
        List<OcorrenciaResponse> ocorrencias = ocorrenciaService.listarTodas();
        return ResponseEntity.ok(ocorrencias);
    }

    @PostMapping
    public ResponseEntity<OcorrenciaResponse> criar(@Valid @RequestBody OcorrenciaRequest request) {
        OcorrenciaResponse response = ocorrenciaService.criar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
