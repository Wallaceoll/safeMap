package com.safemap.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Formato padrão de resposta de erro para toda a API.
 */
public record ErroResponse(
        int    status,
        String mensagem,
        List<String> detalhes,
        LocalDateTime timestamp
) {
    public static ErroResponse of(int status, String mensagem) {
        return new ErroResponse(status, mensagem, List.of(), LocalDateTime.now());
    }

    public static ErroResponse of(int status, String mensagem, List<String> detalhes) {
        return new ErroResponse(status, mensagem, detalhes, LocalDateTime.now());
    }
}
