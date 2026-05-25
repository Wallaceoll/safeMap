package com.safemap.dto.response;

import com.safemap.model.Ocorrencia;
import com.safemap.model.TipoOcorrencia;
import com.safemap.model.PublicoAlvo;
import com.safemap.model.Periodo;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Payload de resposta contendo os detalhes de uma ocorrência.
 */
public record OcorrenciaResponse(
        Long id,
        TipoOcorrencia tipoOcorrencia,
        String tipoOcorrenciaDescricao,
        List<PublicoAlvo> publicoAlvo,
        String descricao,
        String endereco,
        Double latitude,
        Double longitude,
        LocalDateTime dataHora,
        Periodo periodo,
        LocalDateTime criadoEm,
        Long usuarioId
) {
    public static OcorrenciaResponse fromEntity(Ocorrencia o) {
        return new OcorrenciaResponse(
                o.getId(),
                o.getTipoOcorrencia(),
                o.getTipoOcorrencia().getDescricao(),
                o.getPublicoAlvo(),
                o.getDescricao(),
                o.getEndereco(),
                o.getLatitude(),
                o.getLongitude(),
                o.getDataHora(),
                o.getPeriodo(),
                o.getCriadoEm(),
                o.getUsuario() != null ? o.getUsuario().getId() : null
        );
    }
}
