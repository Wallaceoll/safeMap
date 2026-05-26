package com.safemap.service;

import com.sendgrid.*;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.safemap.exception.TokenInvalidoException;
import com.safemap.model.PasswordResetToken;
import com.safemap.model.Usuario;
import com.safemap.repository.PasswordResetTokenRepository;
import com.safemap.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UsuarioRepository            usuarioRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder              passwordEncoder;
    private final SendGrid                     sendGrid;

    @Value("${safemap.cors.allowed-origins}")
    private String allowedOrigins;

    @Value("${sendgrid.from-email}")
    private String fromEmail;

    // -------------------------------------------------------
    //  ETAPA 1 — Solicitar recuperação
    // -------------------------------------------------------

    @Transactional
    public void solicitarRecuperacao(String email) {
        Optional<Usuario> optUsuario = usuarioRepository.findByEmail(email.toLowerCase().trim());

        if (optUsuario.isEmpty()) {
            log.info("Recuperação solicitada para e-mail não cadastrado: {}", email);
            return;
        }

        Usuario usuario = optUsuario.get();

        tokenRepository.invalidarTokensAnteriores(usuario);

        PasswordResetToken resetToken = new PasswordResetToken(usuario);
        tokenRepository.save(resetToken);

        log.info("Token gerado para: {} | expira em: {}", email, resetToken.getExpiracao());

        try {
            enviarEmailRecuperacao(usuario, resetToken.getToken());
            log.info("✅ E-mail de recuperação enviado com sucesso para: {}", email);
        } catch (Exception e) {
            log.error("❌ FALHA ao enviar e-mail para {} — Causa: {} — Detalhe: {}",
                    email, e.getClass().getSimpleName(), e.getMessage(), e);
            throw new RuntimeException(
                    "Não foi possível enviar o e-mail de recuperação. Verifique as configurações.", e);
        }
    }

    // -------------------------------------------------------
    //  ETAPA 2 — Redefinir senha com token
    // -------------------------------------------------------

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
        usuario.setSenha(passwordEncoder.encode(novaSenha));
        usuarioRepository.save(usuario);

        resetToken.setUtilizado(true);
        tokenRepository.save(resetToken);

        log.info("✅ Senha redefinida com sucesso para: {}", usuario.getEmail());
    }

    // -------------------------------------------------------
    //  Limpeza periódica — roda todo dia às 02:00
    // -------------------------------------------------------

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void limparTokensExpirados() {
        tokenRepository.deleteExpirados(LocalDateTime.now());
        log.info("Limpeza de tokens expirados executada: {}", LocalDateTime.now());
    }

    // -------------------------------------------------------
    //  Envio via SendGrid API
    // -------------------------------------------------------

    private void enviarEmailRecuperacao(Usuario usuario, String token) throws IOException {
        String baseUrl = allowedOrigins.split(",")[0].trim();
        String link = baseUrl + "/redefinir-senha.html?token=" + token;

        log.debug("Enviando e-mail via SendGrid para: {} | link: {}", usuario.getEmail(), link);

        Email from = new Email(fromEmail, "SafeMap");
        Email to = new Email(usuario.getEmail());
        String subject = "SafeMap — Recuperação de senha";
        Content content = new Content("text/html", construirCorpoEmail(usuario.getNome(), link));

        Mail mail = new Mail(from, subject, to, content);
        mail.setReplyTo(new Email(fromEmail));

        Request request = new Request();
        request.setMethod(Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(mail.build());

        Response response = sendGrid.api(request);

        log.info("SendGrid status: {} | body: {}", response.getStatusCode(), response.getBody());

        if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
            throw new IOException("SendGrid retornou status " + response.getStatusCode()
                    + ": " + response.getBody());
        }
    }

    // -------------------------------------------------------
    //  Template HTML do e-mail
    // -------------------------------------------------------

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
                          <tr>
                            <td style="background:linear-gradient(135deg,#7C3AED,#9F67FA);
                                       padding:32px 40px;text-align:center;">
                              <h1 style="margin:0;color:#ffffff;font-size:26px;font-weight:700;">
                                🗺️ safeMap
                              </h1>
                              <p style="margin:8px 0 0;color:rgba(255,255,255,0.85);font-size:14px;">
                                Recuperação de senha
                              </p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:40px 40px 32px;">
                              <p style="margin:0 0 16px;font-size:16px;color:#1a1a2e;font-weight:600;">
                                Olá, %s 👋
                              </p>
                              <p style="margin:0 0 24px;font-size:15px;color:#4a4a6a;line-height:1.6;">
                                Recebemos uma solicitação para redefinir a senha da sua conta no
                                <strong>SafeMap</strong>. Clique no botão abaixo para criar uma nova senha.
                              </p>
                              <div style="text-align:center;margin:32px 0;">
                                <a href="%s"
                                   style="display:inline-block;background:#7C3AED;color:#ffffff;
                                          font-size:15px;font-weight:600;text-decoration:none;
                                          padding:14px 36px;border-radius:10px;">
                                  Redefinir minha senha
                                </a>
                              </div>
                              <p style="margin:0 0 8px;font-size:13px;color:#888;line-height:1.6;">
                                ⏰ Este link expira em <strong>1 hora</strong>.
                              </p>
                              <p style="margin:0;font-size:13px;color:#888;line-height:1.6;">
                                Se você não solicitou a recuperação, ignore este e-mail.
                              </p>
                            </td>
                          </tr>
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