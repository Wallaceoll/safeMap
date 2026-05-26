package com.safemap.exception;

import com.safemap.dto.response.ErroResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Centraliza o tratamento de erros de toda a API.
 * Garante que o frontend sempre receba o mesmo formato JSON de erro.
 */
@RestControllerAdvice
public class GlobalException {

    /** Erros de validação do Bean Validation (@NotBlank, @Email, etc.) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<String> detalhes = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .toList();
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErroResponse.of(400, "Dados inválidos", detalhes));
    }

    /** E-mail já cadastrado */
    @ExceptionHandler(EmailJaCadastradoException.class)
    public ResponseEntity<ErroResponse> handleEmailDuplicado(EmailJaCadastradoException ex) {
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ErroResponse.of(409, ex.getMessage()));
    }

    /** Senhas não conferem */
    @ExceptionHandler(SenhasNaoConferemException.class)
    public ResponseEntity<ErroResponse> handleSenhasNaoConferem(SenhasNaoConferemException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErroResponse.of(400, ex.getMessage()));
    }

    /** Token de recuperação inválido ou expirado */
    @ExceptionHandler(TokenInvalidoException.class)
    public ResponseEntity<ErroResponse> handleTokenInvalido(TokenInvalidoException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ErroResponse.of(400, ex.getMessage()));
    }

    /** Credenciais inválidas no login */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErroResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErroResponse.of(401, "E-mail ou senha incorretos"));
    }

    /** Conta desativada */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErroResponse> handleDisabled(DisabledException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ErroResponse.of(401, "Conta desativada. Entre em contato com o suporte."));
    }

    /** Recurso não encontrado */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErroResponse> handleNoResourceFound(
            org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErroResponse.of(404, "Recurso não encontrado"));
    }

    /** Erro de envio de e-mail (SMTP) — senha de app inválida, timeout, etc. */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErroResponse> handleRuntime(RuntimeException ex) {
        if (ex.getMessage() != null && ex.getMessage().contains("e-mail de recuperação")) {
            ex.printStackTrace();
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ErroResponse.of(503, ex.getMessage()));
        }
        ex.printStackTrace();
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErroResponse.of(500, "Erro interno. Tente novamente mais tarde."));
    }

    /** Fallback — erros inesperados */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> handleGeneral(Exception ex) {
        ex.printStackTrace();
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErroResponse.of(500, "Erro interno. Tente novamente mais tarde."));
    }
}