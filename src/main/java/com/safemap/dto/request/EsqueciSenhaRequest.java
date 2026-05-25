package com.safemap.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload para POST /api/auth/esqueci-senha
 * Frontend envia apenas o e-mail do usuário.
 */
public record EsqueciSenhaRequest(

        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "Formato de e-mail inválido")
        String email
) {}
