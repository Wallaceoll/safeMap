package com.safemap.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Serviço responsável por gerar, assinar e validar tokens JWT.
 */
@Service
public class JwtService {

    @Value("${safemap.jwt.secret}")
    private String secret;

    @Value("${safemap.jwt.expiration-ms}")
    private long expiracaoMs;

    // --------------------------------------------------------
    //  Geração
    // --------------------------------------------------------

    /** Gera token JWT para o usuário autenticado. */
    public String gerarToken(UserDetails userDetails) {
        return gerarToken(new HashMap<>(), userDetails);
    }

    public String gerarToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims)
                .subject(userDetails.getUsername())          // email como subject
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiracaoMs))
                .signWith(getChave())
                .compact();
    }

    // --------------------------------------------------------
    //  Validação
    // --------------------------------------------------------

    /** Retorna true se o token for válido e pertencer ao userDetails informado. */
    public boolean isTokenValido(String token, UserDetails userDetails) {
        try {
            final String email = extrairEmail(token);
            return email.equals(userDetails.getUsername()) && !isTokenExpirado(token);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    // --------------------------------------------------------
    //  Extração de claims
    // --------------------------------------------------------

    public String extrairEmail(String token) {
        return extrairClaim(token, Claims::getSubject);
    }

    private boolean isTokenExpirado(String token) {
        return extrairExpiracao(token).before(new Date());
    }

    private Date extrairExpiracao(String token) {
        return extrairClaim(token, Claims::getExpiration);
    }

    public <T> T extrairClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extrairTodosClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extrairTodosClaims(String token) {
        return Jwts.parser()
                .verifyWith(getChave())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getChave() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
