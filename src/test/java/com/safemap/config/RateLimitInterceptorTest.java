package com.safemap.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitInterceptorTest {

    private final RateLimitInterceptor interceptor = new RateLimitInterceptor();

    private boolean chamar(String path, String forwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (forwardedFor != null) request.addHeader("X-Forwarded-For", forwardedFor);
        return interceptor.preHandle(request, new MockHttpServletResponse(), null);
    }

    @Test
    void bloqueiaAposOLimitePorMinuto() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(chamar("/api/rotas", "203.0.113.1")).isTrue();
        }
        assertThat(chamar("/api/rotas", "203.0.113.1")).isFalse();

        // Outro IP e outro grupo de rotas têm contadores próprios
        assertThat(chamar("/api/rotas", "203.0.113.2")).isTrue();
        assertThat(chamar("/api/geocode/busca", "203.0.113.1")).isTrue();
    }

    @Test
    void ipForjadoNoInicioDoCabecalhoNaoBurlaOLimite() throws Exception {
        for (int i = 0; i < 20; i++) {
            assertThat(chamar("/api/rotas", "198.51.100." + i + ", 203.0.113.9")).isTrue();
        }
        assertThat(chamar("/api/rotas", "198.51.100.99, 203.0.113.9")).isFalse();
    }

    @Test
    void geoEGeocodeTemContadoresSeparados() throws Exception {
        for (int i = 0; i < 60; i++) {
            assertThat(chamar("/api/geocode/busca", "203.0.113.5")).isTrue();
        }
        assertThat(chamar("/api/geocode/busca", "203.0.113.5")).isFalse();
        assertThat(chamar("/api/geo/validar", "203.0.113.5")).isTrue();
    }

    @Test
    void rotasSemLimiteNaoSaoAfetadas() throws Exception {
        for (int i = 0; i < 500; i++) {
            assertThat(chamar("/api/ocorrencias", "203.0.113.1")).isTrue();
        }
    }
}
