package com.safemap.repository;


import com.safemap.model.PasswordResetToken;
import com.safemap.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    /** Invalida todos os tokens anteriores do usuário antes de gerar um novo. */
    @Modifying
    @Transactional
    @Query("""
            UPDATE PasswordResetToken t
            SET t.utilizado = true
            WHERE t.usuario = :usuario AND t.utilizado = false
            """)
    void invalidarTokensAnteriores(@Param("usuario") Usuario usuario);

    /** Limpeza periódica: remove tokens expirados do banco. */
    @Modifying
    @Transactional
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiracao < :agora")
    void deleteExpirados(@Param("agora") LocalDateTime agora);
}
