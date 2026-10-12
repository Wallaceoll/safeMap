package com.safemap.repository;

import com.safemap.model.TipoOcorrencia;

/**
 * Projeção mínima de uma ocorrência para o cálculo de risco das rotas
 * (evita carregar descrição, endereço e público-alvo).
 */
public record IncidenteRisco(Double latitude, Double longitude, TipoOcorrencia tipoOcorrencia) {
}
