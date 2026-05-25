package com.safemap.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Token temporário para recuperação de senha.
 *
 * Fluxo:
 *  1. Usuário solicita recuperação → backend gera este token e envia por e-mail.
 *  2. Usuário clica no link → backend valida token (não expirado + não utilizado).
 *  3. Usuário define nova senha → token é marcado como utilizado.
 */
@Entity
@Table(name = "tb_password_reset_token",
        indexes = @Index(name = "idx_token_valor", columnList = "token"))
@Getter
@Setter
@NoArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** UUID único enviado no link de e-mail. */
    @Column(nullable = false, unique = true, length = 100)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Momento em que o token foi gerado. */
    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    /** Expira 1 hora após a criação. */
    @Column(nullable = false)
    private LocalDateTime expiracao;

    /** Marcado true assim que a senha for redefinida com sucesso. */
    @Column(nullable = false)
    private boolean utilizado = false;

    /**
     * Construtor de conveniência: gera token UUID e define expiração de 1h.
     */
    public PasswordResetToken(Usuario usuario) {
        this.usuario  = usuario;
        this.token   = UUID.randomUUID().toString();
        this.criadoEm = LocalDateTime.now();
        this.expiracao = this.criadoEm.plusHours(1);
    }

    /** Verifica se o token ainda é válido (não expirado e não utilizado). */
    public boolean isValido() {
        return !this.utilizado && LocalDateTime.now().isBefore(this.expiracao);
    }
}
