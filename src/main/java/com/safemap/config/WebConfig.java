package com.safemap.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.server.Compression;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.util.unit.DataSize;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/geocode/**", "/api/rotas/**", "/api/apoio/**", "/api/geo/**");
    }

    /**
     * JS e CSS ficam 7 dias no cache do navegador. É seguro porque toda referência
     * nos HTML leva versão (?v=1.4): ao alterar um arquivo, suba o número da versão.
     * Os HTML continuam sem cache longo, para cada deploy aparecer na hora.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        CacheControl seteDias = CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic();
        registry.addResourceHandler("/js/**")
                .addResourceLocations("classpath:/static/js/")
                .setCacheControl(seteDias);
        registry.addResourceHandler("/css/**")
                .addResourceLocations("classpath:/static/css/")
                .setCacheControl(seteDias);
    }

    /** Compressão gzip de JSON, JS, CSS e HTML (respostas a partir de 1 KB). */
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> compressao() {
        return factory -> {
            Compression compression = new Compression();
            compression.setEnabled(true);
            compression.setMinResponseSize(DataSize.ofKilobytes(1));
            compression.setMimeTypes(new String[]{
                    "application/json", "application/geo+json", "application/vnd.geo+json",
                    "text/html", "text/css", "text/javascript", "application/javascript", "text/plain"
            });
            factory.setCompression(compression);
        };
    }
}
