package com.safemap.dto.response;

import com.safemap.model.PontoApoio;
import com.safemap.model.TipoPontoApoio;

/**
 * Payload de resposta de um ponto de apoio.
 */
public record PontoApoioResponse(
        Long id,
        TipoPontoApoio tipo,
        String nome,
        String rua,
        String numero,
        String bairro,
        String cidade,
        String uf,
        String telefone,
        String horario,
        Double latitude,
        Double longitude
) {
    public static PontoApoioResponse fromEntity(PontoApoio p) {
        return new PontoApoioResponse(
                p.getId(),
                p.getTipo(),
                p.getNome(),
                p.getRua(),
                p.getNumero(),
                p.getBairro(),
                p.getCidade(),
                p.getUf(),
                p.getTelefone(),
                p.getHorario(),
                p.getLatitude(),
                p.getLongitude()
        );
    }
}
