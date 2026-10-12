package com.safemap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LimiteUfImportServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final LimiteUfImportService service = new LimiteUfImportService(null, null, mapper, null);

    private static String feature(String codigo, String tipo) {
        String coords = tipo.equals("Polygon")
                ? "[[[-40,-10],[-39,-10],[-39,-9],[-40,-10]]]"
                : "[[[[-40,-10],[-39,-10],[-39,-9],[-40,-10]]]]";
        return "{\"type\":\"Feature\",\"properties\":{\"codarea\":\"" + codigo + "\"},"
                + "\"geometry\":{\"type\":\"" + tipo + "\",\"coordinates\":" + coords + "}}";
    }

    private JsonNode colecao(List<String> features) throws Exception {
        return mapper.readTree("{\"type\":\"FeatureCollection\",\"features\":[" + String.join(",", features) + "]}");
    }

    private List<String> todasAsUfs() {
        List<String> features = new ArrayList<>();
        int i = 0;
        for (String codigo : LimiteUfImportService.UFS.keySet()) {
            features.add(feature(codigo, i++ % 2 == 0 ? "Polygon" : "MultiPolygon"));
        }
        return features;
    }

    @Test
    void aceitaAs27UfsComPolygonEMultiPolygon() throws Exception {
        Map<String, String> geometrias = service.extrairGeometrias(colecao(todasAsUfs()));
        assertThat(geometrias).hasSize(27).containsKeys("26", "35", "53");
        assertThat(geometrias.get("26")).contains("coordinates");
    }

    @Test
    void recusaMalhaIncompleta() throws Exception {
        List<String> features = todasAsUfs();
        features.remove(0);
        assertThatThrownBy(() -> service.extrairGeometrias(colecao(features)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("26 UFs");
    }

    @Test
    void recusaUfRepetidaOuDesconhecida() throws Exception {
        List<String> repetida = todasAsUfs();
        repetida.add(feature("35", "Polygon"));
        assertThatThrownBy(() -> service.extrairGeometrias(colecao(repetida)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("repetida");

        List<String> desconhecida = todasAsUfs();
        desconhecida.set(0, feature("99", "Polygon"));
        assertThatThrownBy(() -> service.extrairGeometrias(colecao(desconhecida)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("inesperado");
    }

    @Test
    void recusaGeometriaQueNaoEhPoligono() throws Exception {
        List<String> features = todasAsUfs();
        features.set(0, "{\"type\":\"Feature\",\"properties\":{\"codarea\":\"11\"},"
                + "\"geometry\":{\"type\":\"Point\",\"coordinates\":[-40,-10]}}");
        assertThatThrownBy(() -> service.extrairGeometrias(colecao(features)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Geometria inesperada");
    }
}
