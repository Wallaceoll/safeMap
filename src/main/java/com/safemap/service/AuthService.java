package com.safemap.service;


import com.safemap.dto.response.AuthResponse;
import com.safemap.dto.request.CadastroRequest;
import com.safemap.dto.request.LoginRequest;
import com.safemap.exception.EmailJaCadastradoException;
import com.safemap.exception.SenhasNaoConferemException;
import com.safemap.model.Usuario;
import com.safemap.repository.UsuarioRepository;
import com.safemap.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negócio para autenticação e cadastro.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository    usuarioRepository;
    private final PasswordEncoder      passwordEncoder;
    private final JwtService           jwtService;
    private final AuthenticationManager authenticationManager;

    // --------------------------------------------------------
    //  Login
    // --------------------------------------------------------

    /**
     * Autentica o usuário e retorna um JWT.
     *
     * O AuthenticationManager já trata:
     *  - Usuário não encontrado → lança BadCredentialsException
     *  - Senha errada           → lança BadCredentialsException
     *  - Conta desativada       → lança DisabledException
     */
    public AuthResponse login(LoginRequest request) {
        // 1. Valida credenciais — lança exceção se inválidas
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.senha())
        );

        // 2. Busca o usuário (garantidamente existe após autenticação)
        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .orElseThrow();

        // 3. Gera e retorna o token
        String token = jwtService.gerarToken(usuario);
        return AuthResponse.of(token, usuario.getId(), usuario.getNome(), usuario.getEmail());
    }

    // --------------------------------------------------------
    //  Cadastro
    // --------------------------------------------------------

    /**
     * Cadastra um novo usuário e retorna um JWT (já autenticado).
     *
     * Validações:
     *  - E-mail único no banco
     *  - Senhas conferem
     *  - A senha é armazenada como hash BCrypt (NUNCA em texto plano)
     */
    @Transactional
    public AuthResponse cadastrar(CadastroRequest request) {
        // 1. Verificar e-mail duplicado
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new EmailJaCadastradoException("Este e-mail já está cadastrado.");
        }

        // 2. Verificar se as senhas conferem
        if (!request.senha().equals(request.confirmarSenha())) {
            throw new SenhasNaoConferemException("As senhas não coincidem.");
        }

        // 3. Criar e salvar o usuário com senha hasheada
        Usuario usuario = new Usuario();
        usuario.setNome(request.nome());
        usuario.setEmail(request.email().toLowerCase().trim());
        usuario.setSenha(passwordEncoder.encode(request.senha()));
        usuario.setTelefone(formatarTelefone(request.telefone()));
        Usuario salvo = usuarioRepository.save(usuario);

        // 4. Gera JWT e retorna (usuário já logado após cadastro)
        String token = jwtService.gerarToken(salvo);
        return AuthResponse.of(token, salvo.getId(), salvo.getNome(), salvo.getEmail());
    }

    /**
     * Normaliza qualquer formato de telefone brasileiro para (XX) XXXXX-XXXX ou (XX) XXXX-XXXX.
     * Se vazio ou nulo, retorna string vazia.
     */
    private String formatarTelefone(String telefone) {
        if (telefone == null || telefone.isBlank()) return "";
        String digits = telefone.replaceAll("\\D", "");
        if (digits.length() == 11) {
            return "(%s) %s-%s".formatted(digits.substring(0, 2), digits.substring(2, 7), digits.substring(7));
        }
        if (digits.length() == 10) {
            return "(%s) %s-%s".formatted(digits.substring(0, 2), digits.substring(2, 6), digits.substring(6));
        }
        return telefone; // fallback: retorna como veio
    }
}