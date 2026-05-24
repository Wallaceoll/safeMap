package com.safemap.controller;

import com.safemap.dto.response.AuthResponse;
import com.safemap.dto.request.CadastroRequest;
import com.safemap.dto.request.LoginRequest;
import com.safemap.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controller de autenticação.
 *
 * Endpoints:
 *  POST /api/auth/login    — autentica e retorna JWT
 *  POST /api/auth/cadastro — cria conta e retorna JWT
 *
 * Ambos são públicos (configurados no SecurityConfig).
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // --------------------------------------------------------
    //  POST /api/auth/login
    // --------------------------------------------------------

    /**
     * Autentica o usuário.
     *
     * Request body:
     * {
     *   "email": "usuario@email.com",
     *   "senha": "minhasenha123"
     * }
     *
     * Response 200:
     * {
     *   "token": "eyJhbGci...",
     *   "tipo": "Bearer",
     *   "userId": 1,
     *   "nome": "João Silva",
     *   "email": "usuario@email.com"
     * }
     *
     * Response 401: credenciais inválidas
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    // --------------------------------------------------------
    //  POST /api/auth/cadastro
    // --------------------------------------------------------

    /**
     * Cadastra um novo usuário.
     *
     * Request body:
     * {
     *   "nome": "João Silva",
     *   "email": "joao@email.com",
     *   "senha": "minhasenha123",
     *   "confirmarSenha": "minhasenha123",
     *   "telefone": "(11) 91234-5678"   ← opcional
     * }
     *
     * Response 201: usuário criado + JWT (já autenticado)
     * Response 400: dados inválidos ou senhas não conferem
     * Response 409: e-mail já cadastrado
     */
    @PostMapping("/cadastro")
    public ResponseEntity<AuthResponse> cadastrar(@Valid @RequestBody CadastroRequest request) {
        AuthResponse response = authService.cadastrar(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

