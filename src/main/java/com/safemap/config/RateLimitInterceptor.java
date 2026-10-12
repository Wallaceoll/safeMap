package com.safemap.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limite simples de requisições por IP (janela fixa de 1 minuto) para os endpoints
 * que consomem cotas de serviços externos. Impede que um único cliente esgote a
 * cota gratuita do Openrouteservice.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final long WINDOW_MS = 60_000;

    /** Requisições permitidas por minuto, por IP, em cada grupo de rotas. */
    private static final Map<String, Integer> LIMITES = Map.of(
            "/api/geocode", 60,
            "/api/rotas", 20,
            "/api/apoio", 120,
            "/api/geo", 60
    );

    private record Window(long start, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        // Compara o segmento inteiro ("/api/geo" não pode casar com "/api/geocode/...")
        String prefixo = LIMITES.keySet().stream()
                .filter(p -> uri.equals(p) || uri.startsWith(p + "/"))
                .findFirst()
                .orElse(null);
        if (prefixo == null) return true;
        int limit = LIMITES.get(prefixo);

        String key = clientIp(request) + "|" + prefixo;
        long now = System.currentTimeMillis();
        Window window = windows.compute(key, (k, w) ->
                (w == null || now - w.start() >= WINDOW_MS) ? new Window(now, new AtomicInteger()) : w);

        if (window.count().incrementAndGet() > limit) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"erro\":\"Muitas requisições. Aguarde um minuto e tente novamente.\"}");
            return false;
        }
        return true;
    }

    /**
     * IP do cliente atrás do proxy do Railway. Usa o último valor de X-Forwarded-For,
     * que é o adicionado pelo proxy; os anteriores podem ser forjados pelo próprio cliente.
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] ips = forwarded.split(",");
            return ips[ips.length - 1].trim();
        }
        return request.getRemoteAddr();
    }

    @Scheduled(fixedRate = 5 * 60_000)
    public void limparJanelasExpiradas() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> now - e.getValue().start() >= WINDOW_MS);
    }
}
