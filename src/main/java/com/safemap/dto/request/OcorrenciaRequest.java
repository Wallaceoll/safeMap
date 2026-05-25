package com.safemap.dto.request;

import com.safemap.model.TipoOcorrencia;
import com.safemap.model.PublicoAlvo;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Payload recebido ao criar uma nova ocorrência (POST /api/ocorrencias)
 */
public record OcorrenciaRequest(
        @NotNull(message = "Tipo de ocorrência é obrigatório")
        TipoOcorrencia tipoOcorrencia,

        List<PublicoAlvo> publicoAlvo,

        String descricao,
        String endereco,

        @NotNull(message = "Latitude é obrigatória")
        Double latitude,

        @NotNull(message = "Longitude é obrigatória")
        Double longitude,

        @NotNull(message = "Data e hora são obrigatórias")
        LocalDateTime dataHora
) {}
