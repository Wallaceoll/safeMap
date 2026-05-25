package com.safemap.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload recebido no POST /api/auth/login
 */
public record LoginRequest(

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "Formato de e-mail inválido")
        String email,

        @NotBlank(message = "Senha é obrigatória")
        String senha
) {}
