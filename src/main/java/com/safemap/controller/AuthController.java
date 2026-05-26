package com.safemap.controller;

import com.safemap.dto.request.CadastroRequest;
import com.safemap.dto.request.EsqueciSenhaRequest;
import com.safemap.dto.request.LoginRequest;
import com.safemap.dto.request.RedefinirSenhaRequest;
import com.safemap.dto.response.AuthResponse;
import com.safemap.service.AuthService;
import com.safemap.service.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller de autenticação.
 *
 * Endpoints:
 *  POST /api/auth/login           — autentica e retorna JWT
 *  POST /api/auth/cadastro        — cria conta e retorna JWT
 *  POST /api/auth/esqueci-senha   — envia link de recuperação por e-mail
 *  POST /api/auth/redefinir-senha — redefine a senha com token do e-mail
 *
 * Todos são públicos (configurados no SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService         authService;
    private final PasswordResetService passwordResetService;

    // --------------------------------------------------------
    //  POST /api/auth/login
    // --------------------------------------------------------
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // --------------------------------------------------------
    //  POST /api/auth/cadastro
    // --------------------------------------------------------
    @PostMapping("/cadastro")
    public ResponseEntity<AuthResponse> cadastrar(@Valid @RequestBody CadastroRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.cadastrar(request));
    }

    // --------------------------------------------------------
    //  POST /api/auth/esqueci-senha
    //
    //  Request body: { "email": "usuario@email.com" }
    //  Response 200: sempre retorna sucesso (não revela se e-mail existe)
    //
    //  O frontend (esqueci-senha.html) já chama este endpoint.
    // --------------------------------------------------------
    @PostMapping("/esqueci-senha")
    public ResponseEntity<Map<String, String>> esqueciSenha(
            @Valid @RequestBody EsqueciSenhaRequest request) {

        passwordResetService.solicitarRecuperacao(request.email());

        // Resposta genérica intencional — não revela se o e-mail existe no banco
        return ResponseEntity.ok(Map.of(
                "mensagem", "Se este e-mail estiver cadastrado, você receberá as instruções em breve."
        ));
    }

    // --------------------------------------------------------
    //  POST /api/auth/redefinir-senha
    //
    //  Request body: { "token": "uuid-do-email", "novaSenha": "novasenha123" }
    //  Response 200: senha redefinida com sucesso
    //  Response 400: token inválido ou expirado
    //
    //  O frontend (redefinir-senha.html) lê o token da URL (?token=...) e chama este endpoint.
    // --------------------------------------------------------
    @PostMapping("/redefinir-senha")
    public ResponseEntity<Map<String, String>> redefinirSenha(
            @Valid @RequestBody RedefinirSenhaRequest request) {

        passwordResetService.redefinirSenha(request.token(), request.novaSenha());

        return ResponseEntity.ok(Map.of(
                "mensagem", "Senha redefinida com sucesso! Você já pode fazer login."
        ));
    }
}
