package com.safemap.service;
import java.io.UnsupportedEncodingException;
import com.safemap.exception.TokenInvalidoException;
import com.safemap.model.PasswordResetToken;
import com.safemap.model.Usuario;
import com.safemap.repository.PasswordResetTokenRepository;
import com.safemap.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Serviço responsável pelo fluxo completo de recuperação de senha.
 *
 * Fluxo:
 *  1. solicitarRecuperacao(email)
 *     → Gera token → Invalida tokens antigos → Envia e-mail com link
 *
 *  2. redefinirSenha(token, novaSenha)
 *     → Valida token (existe, não expirado, não usado)
 *     → Atualiza senha com BCrypt
 *     → Marca token como utilizado
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UsuarioRepository              usuarioRepository;
    private final PasswordResetTokenRepository   tokenRepository;
    private final PasswordEncoder                passwordEncoder;
    private final JavaMailSender                 mailSender;

    // URL base do frontend — onde o link de redefinição vai apontar
    // Configurado no application.properties: safemap.frontend.url
    @Value("${safemap.frontend.url:http://localhost:8080}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String emailRemetente;

    // -------------------------------------------------------
    //  ETAPA 1 — Solicitar recuperação
    // -------------------------------------------------------

    /**
     * Recebe o e-mail, gera um token e envia o link por e-mail.
     *
     * IMPORTANTE: Se o e-mail não existir no banco, retornamos
     * sucesso mesmo assim (evita enumeração de usuários por atacantes).
     */
    @Transactional
    public void solicitarRecuperacao(String email) {
        Optional<Usuario> optUsuario = usuarioRepository.findByEmail(email.toLowerCase().trim());

        if (optUsuario.isEmpty()) {
            // Silenciosamente ignora — não revela que o e-mail não existe
            log.info("Solicitação de recuperação para e-mail não cadastrado: {}", email);
            return;
        }

        Usuario usuario = optUsuario.get();

        // Invalida todos os tokens anteriores deste usuário
        tokenRepository.invalidarTokensAnteriores(usuario);

        // Gera novo token (UUID + expiração 1h)
        PasswordResetToken resetToken = new PasswordResetToken(usuario);
        tokenRepository.save(resetToken);

        // Envia o e-mail
        try {
            enviarEmailRecuperacao(usuario, resetToken.getToken());
            log.info("E-mail de recuperação enviado para: {}", email);
        } catch (MessagingException | UnsupportedEncodingException e) {
            log.error("Falha ao enviar e-mail de recuperação para {}: {}", email, e.getMessage());
            // Não lança exceção — token foi gerado, usuário pode tentar novamente
        }
    }

    // -------------------------------------------------------
    //  ETAPA 2 — Redefinir senha com token
    // -------------------------------------------------------

    /**
     * Valida o token e atualiza a senha do usuário.
     */
    @Transactional
    public void redefinirSenha(String tokenValor, String novaSenha) {
        PasswordResetToken resetToken = tokenRepository.findByToken(tokenValor)
                .orElseThrow(() -> new TokenInvalidoException(
                        "Link de recuperação inválido. Solicite um novo."));

        if (!resetToken.isValido()) {
            throw new TokenInvalidoException(
                    "Link de recuperação expirado ou já utilizado. Solicite um novo.");
        }

        Usuario usuario = resetToken.getUsuario();

        // Atualiza a senha com hash BCrypt
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);

        // Marca o token como utilizado (não pode ser reutilizado)
        resetToken.setUtilizado(true);
        tokenRepository.save(resetToken);

        log.info("Senha redefinida com sucesso para usuário: {}", usuario.getEmail());
    }

    // -------------------------------------------------------
    //  Limpeza periódica de tokens expirados
    // -------------------------------------------------------

    /**
     * Roda toda madrugada às 02:00 para limpar tokens velhos do banco.
     * @Scheduled(cron = "segundos minutos horas dia mês dia-semana")
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void limparTokensExpirados() {
        tokenRepository.deleteExpirados(LocalDateTime.now());
        log.info("Limpeza de tokens expirados executada em: {}", LocalDateTime.now());
    }

    // -------------------------------------------------------
    //  Montagem do e-mail HTML
    // -------------------------------------------------------

    private void enviarEmailRecuperacao(Usuario usuario, String token) throws MessagingException, UnsupportedEncodingException {
        String link = frontendUrl + "/redefinir-senha.html?token=" + token;

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(emailRemetente, "SafeMap");
        helper.setTo(usuario.getEmail());
        helper.setSubject("SafeMap — Recuperação de senha");
        helper.setText(construirCorpoEmail(usuario.getNome(), link), true); // true = HTML
    }

    /**
     * HTML do e-mail de recuperação.
     * Visual simples e compatível com os principais clientes de e-mail.
     */
    private String construirCorpoEmail(String nome, String link) {
        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                </head>
                <body style="margin:0;padding:0;background:#f4f4f4;font-family:Inter,Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:32px 0;">
                    <tr>
                      <td align="center">
                        <table width="480" cellpadding="0" cellspacing="0"
                               style="background:#ffffff;border-radius:16px;overflow:hidden;
                                      box-shadow:0 4px 24px rgba(0,0,0,0.08);">

                          <!-- Header roxo -->
                          <tr>
                            <td style="background:linear-gradient(135deg,#7C3AED,#9F67FA);
                                       padding:32px 40px;text-align:center;">
                              <h1 style="margin:0;color:#ffffff;font-size:26px;font-weight:700;
                                         letter-spacing:-0.5px;">
                                🗺️ safeMap
                              </h1>
                              <p style="margin:8px 0 0;color:rgba(255,255,255,0.85);font-size:14px;">
                                Recuperação de senha
                              </p>
                            </td>
                          </tr>

                          <!-- Corpo -->
                          <tr>
                            <td style="padding:40px 40px 32px;">
                              <p style="margin:0 0 16px;font-size:16px;color:#1a1a2e;font-weight:600;">
                                Olá, %s 👋
                              </p>
                              <p style="margin:0 0 24px;font-size:15px;color:#4a4a6a;line-height:1.6;">
                                Recebemos uma solicitação para redefinir a senha da sua conta no
                                <strong>SafeMap</strong>. Clique no botão abaixo para criar uma nova senha.
                              </p>

                              <!-- Botão -->
                              <div style="text-align:center;margin:32px 0;">
                                <a href="%s"
                                   style="display:inline-block;background:#7C3AED;color:#ffffff;
                                          font-size:15px;font-weight:600;text-decoration:none;
                                          padding:14px 36px;border-radius:10px;
                                          letter-spacing:0.3px;">
                                  Redefinir minha senha
                                </a>
                              </div>

                              <p style="margin:0 0 8px;font-size:13px;color:#888;line-height:1.6;">
                                ⏰ Este link expira em <strong>1 hora</strong>.
                              </p>
                              <p style="margin:0;font-size:13px;color:#888;line-height:1.6;">
                                Se você não solicitou a recuperação de senha, ignore este e-mail.
                                Sua conta permanece segura.
                              </p>
                            </td>
                          </tr>

                          <!-- Link manual -->
                          <tr>
                            <td style="background:#f8f8ff;padding:20px 40px;border-top:1px solid #ececec;">
                              <p style="margin:0 0 6px;font-size:12px;color:#aaa;">
                                Ou copie e cole este link no navegador:
                              </p>
                              <p style="margin:0;font-size:12px;color:#7C3AED;word-break:break-all;">
                                %s
                              </p>
                            </td>
                          </tr>

                          <!-- Rodapé -->
                          <tr>
                            <td style="padding:20px 40px;text-align:center;">
                              <p style="margin:0;font-size:12px;color:#ccc;">
                                © 2026 SafeMap · Este é um e-mail automático, não responda.
                              </p>
                            </td>
                          </tr>

                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(nome, link, link);
    }
}
