package com.safemap.dto.response;

/**
 * Payload retornado após login ou cadastro bem-sucedido.
 * O frontend armazena o token e envia em cada requisição:
 *   Authorization: Bearer {token}
 */
public record AuthResponse(
        String token,
        String tipo,       // sempre "Bearer"
        Long   userId,
        String nome,
        String email
) {
    public static AuthResponse of(String token, Long userId, String nome, String email) {
        return new AuthResponse(token, "Bearer", userId, nome, email);
    }
}

