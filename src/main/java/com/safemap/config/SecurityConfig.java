package com.safemap.config;

import com.safemap.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuração principal de segurança do SafeMap.
 *
 * - Autenticação stateless via JWT (sem sessão HTTP).
 * - CORS configurado para aceitar apenas origens autorizadas.
 * - CSRF desabilitado (APIs REST stateless não precisam).
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter      jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Value("${safemap.cors.allowed-origins}")
    private String allowedOrigins;

    // --------------------------------------------------------
    //  Cadeia de filtros HTTP
    // --------------------------------------------------------

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Desabilita CSRF (desnecessário para APIs JWT stateless)
                .csrf(AbstractHttpConfigurer::disable)

                // 2. Configura CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 3. Regras de autorização por rota
                .authorizeHttpRequests(auth -> auth

                        // Rotas públicas — autenticação e cadastro
                        .requestMatchers("/api/auth/**").permitAll()

                        // Rotas públicas — leitura do mapa (visitante pode ver)
                        .requestMatchers(HttpMethod.GET, "/api/ocorrencias/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/apoio/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/rotas/**").permitAll()

                        // Frontend estático servido pelo Spring Boot
                        .requestMatchers(
                                "/", "/index.html", "/login.html", "/cadastro.html",
                                "/esqueci-senha.html", "/redefinir-senha.html",
                                "/relatar.html", "/apoio.html", "/detalhes-relato.html",
                                "/css/**", "/js/**", "/assets/**", "/favicon.ico"
                        ).permitAll()

                        // Tudo o mais requer autenticação
                        .anyRequest().authenticated()
                )

                // 4. Política de sessão: STATELESS (sem HttpSession)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // 5. Provider de autenticação customizado
                .authenticationProvider(authenticationProvider())

                // 6. Adiciona o filtro JWT antes do filtro padrão de usuário/senha
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // --------------------------------------------------------
    //  CORS
    // --------------------------------------------------------

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Lê as origens do application.properties (separa por vírgula)
        List<String> origens = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .toList();
        config.setAllowedOrigins(origens);

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // --------------------------------------------------------
    //  Beans de autenticação
    // --------------------------------------------------------

    /**
     * Provider que usa o UserDetailsService (busca por e-mail no banco)
     * e o BCryptPasswordEncoder para verificar a senha.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * BCrypt com strength 12 — bom equilíbrio entre segurança e performance.
     * Strength 10 (padrão) é aceitável; 12 é mais seguro para dados sensíveis.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}