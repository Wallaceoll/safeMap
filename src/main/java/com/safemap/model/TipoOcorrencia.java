package com.safemap.model;

/**
 * Tipos de ocorrência que podem ser relatados no SafeMap.
 */
public enum TipoOcorrencia {
    ASSEDIO("Assédio"),
    IMPORTUNACAO_SEXUAL("Importunação Sexual"),
    AGRESSAO_FISICA("Agressão Física"),
    AGRESSAO_VERBAL("Agressão Verbal");

    private final String descricao;

    TipoOcorrencia(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
