package com.safemap.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limites geográficos (bounding box) do Brasil, incluindo ilhas oceânicas
 * (Fernando de Noronha, Trindade). Configurável via application.properties.
 */
@Component
public class BrasilBounds {

    @Value("${safemap.bounds.min-lat:-33.76}")
    private double minLat;

    @Value("${safemap.bounds.max-lat:5.28}")
    private double maxLat;

    @Value("${safemap.bounds.min-lng:-74.00}")
    private double minLng;

    @Value("${safemap.bounds.max-lng:-28.80}")
    private double maxLng;

    public boolean contem(double lat, double lng) {
        return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
    }

    public boolean intersecta(double south, double west, double north, double east) {
        return south <= maxLat && north >= minLat && west <= maxLng && east >= minLng;
    }
}
