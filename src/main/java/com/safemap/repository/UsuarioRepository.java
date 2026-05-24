package com.safemap.repository;

import com.safemap.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** Usado no login e no UserDetailsService para buscar por e-mail. */
    Optional<Usuario> findByEmail(String email);

    /** Verifica duplicidade de e-mail no cadastro. */
    boolean existsByEmail(String email);
}
