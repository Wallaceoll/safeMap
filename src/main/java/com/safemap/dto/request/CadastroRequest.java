package com.safemap.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Payload recebido no POST /api/auth/cadastro
 */
public record CadastroRequest(

                @NotBlank(message = "Nome é obrigatório") @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres") String nome,

                @NotBlank(message = "E-mail é obrigatório") @Email(message = "Formato de e-mail inválido") String email,

                @NotBlank(message = "Senha é obrigatória") @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres") String senha,

                @NotBlank(message = "Confirmação de senha é obrigatória") String confirmarSenha,

                /**
                 * Telefone opcional.
                 * Aceita formatos variados: (11) 91234-5678, 11912345678, 11 91234-5678, etc.
                 * Tratamento da formação feita no backend para (XX) XXXXX-XXXX antes de salvar.
                 */
                @Pattern(regexp = "^\\(?\\d{2}\\)?\\s?\\d{4,5}-?\\d{4}$|^$", message = "Telefone inválido. Informe DDD + número (ex: 11912345678)") String telefone) {
}
