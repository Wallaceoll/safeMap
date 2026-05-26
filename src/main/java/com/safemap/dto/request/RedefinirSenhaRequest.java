package com.safemap.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload para POST /api/auth/redefinir-senha
 * Frontend envia o token (vindo da URL do e-mail) + nova senha.
 */
public record RedefinirSenhaRequest(

        @NotBlank(message = "Token é obrigatório") String token,

        @NotBlank(message = "Nova senha é obrigatória") @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres") String novaSenha) {
}
