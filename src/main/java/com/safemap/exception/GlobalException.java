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

    /** Credenciais inválidas no login */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErroResponse> handleBadCredentials(BadCredentialsException ex) {
        // Mensagem genérica intencional — não informar ao atacante qual campo está
        // errado
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

    /** Recurso estático ou rota não encontrada (evita poluir os logs com favicon.ico e retorna 404) */
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ErroResponse> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErroResponse.of(404, "Recurso não encontrado"));
    }

    /** Fallback — erros inesperados */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResponse> handleGeneral(Exception ex) {
        // Logar o ex completo para depuração local
        ex.printStackTrace();
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErroResponse.of(500, "Erro interno. Tente novamente mais tarde."));
    }
}

