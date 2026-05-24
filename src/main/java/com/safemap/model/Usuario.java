package com.safemap.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Entidade principal de usuário.
 * Implementa UserDetails para integração direta com Spring Security.
 */
@Entity
@Table(name = "tb_usuario",
        uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
public class Usuario implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Nome é obrigatório")
    @Size(min = 3, max = 100, message = "Nome deve ter entre 3 e 100 caracteres")
    @Column(nullable = false, length = 100)
    private String nome;

    @NotBlank(message = "E-mail é obrigatório")
    @Email(message = "Formato de e-mail inválido")
    @Column(nullable = false, unique = true, length = 150)
    private String email;

    /**
     * SEMPRE armazena o hash BCrypt — nunca a senha em texto plano.
     */
    @NotBlank
    @Column(nullable = false)
    private String senha;

    /** Telefone opcional — formato: (XX) XXXXX-XXXX */
    @Column(length = 20)
    private String telefone;

    @Column(nullable = false, updatable = false)
    private LocalDateTime dataCadastro;

    @Column(nullable = false)
    private boolean ativo = true;

    @PrePersist
    protected void onCreate() {
        this.dataCadastro = LocalDateTime.now();
    }

    // --------------------------------------------------------
    //  UserDetails — Spring Security
    // --------------------------------------------------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    /** Spring Security usa getPassword() para autenticação. */
    @Override
    public String getPassword() {
        return this.senha;
    }

    /** Spring Security usa getUsername() como identificador único. */
    @Override
    public String getUsername() {
        return this.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return this.ativo;
    }
}
