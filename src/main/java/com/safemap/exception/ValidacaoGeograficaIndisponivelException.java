package com.safemap.exception;

/**
 * A validação geográfica não pôde ser feita (limites das UFs ausentes/incompletos
 * ou falha no banco). Nunca deve ser tratada como "coordenada fora do Brasil".
 */
public class ValidacaoGeograficaIndisponivelException extends RuntimeException {

    public ValidacaoGeograficaIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public ValidacaoGeograficaIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
