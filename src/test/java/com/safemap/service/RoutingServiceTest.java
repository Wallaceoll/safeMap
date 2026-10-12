package com.safemap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safemap.model.TipoOcorrencia;
import com.safemap.repository.IncidenteRisco;
import com.safemap.repository.OcorrenciaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comportamento da rota segura: risco = nº de relatos a até 250 m do trajeto;
 * desvios passam por pontos ~550 m ao lado do 1º relato perigoso e só entram
 * se forem menores que 2,5x a distância da rota direta.
 */
class RoutingServiceTest {

    // Rota direta leste-oeste de ~2 km em SP
    private static final double LAT = -23.5600, LNG_ORIGEM = -46.6600, LNG_DESTINO = -46.6400;

    private OcorrenciaRepository repository;
    private OpenRouteService ors;
    private RoutingService service;

    /** Relatos "no banco"; o mock do repositório filtra pelo retângulo pedido. */
    private final List<IncidenteRisco> banco = new ArrayList<>();
    private Map<String, Object> respostaDireta;
    private double distanciaDesvio = 2600;

    @BeforeEach
    void setUp() {
        repository = mock(OcorrenciaRepository.class);
        ors = mock(OpenRouteService.class);
        service = new RoutingService(repository, ors, new ObjectMapper());

        when(repository.findIncidentesRiscoInBounds(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenAnswer(inv -> {
                    double s = inv.getArgument(0), w = inv.getArgument(1), n = inv.getArgument(2), e = inv.getArgument(3);
                    return banco.stream()
                            .filter(i -> i.latitude() >= s && i.latitude() <= n && i.longitude() >= w && i.longitude() <= e)
                            .toList();
                });

        respostaDireta = resposta(rota(2000, linha(LAT, LNG_ORIGEM, LAT, LNG_DESTINO)));
        when(ors.isConfigurado()).thenReturn(true);
        when(ors.rotaAPe(anyList())).thenAnswer(inv -> {
            List<double[]> pontos = inv.getArgument(0);
            if (pontos.size() == 2) return respostaDireta;
            // Desvio: origem → ponto intermediário → destino
            double[] meio = pontos.get(1);
            List<List<Double>> coords = new ArrayList<>(linha(LAT, LNG_ORIGEM, meio[1], meio[0]));
            coords.addAll(linha(meio[1], meio[0], LAT, LNG_DESTINO));
            return resposta(rota(distanciaDesvio, coords));
        });
    }

    @Test
    void semRelatosPertoNaoPedeDesvios() {
        banco.add(new IncidenteRisco(-23.70, -46.80, TipoOcorrencia.ASSEDIO)); // longe

        List<Map<String, Object>> rotas = rotas(service.calculateRoutes(LAT, LNG_ORIGEM, LAT, LNG_DESTINO));

        assertThat(rotas).hasSize(1);
        assertThat(rotas.get(0).get("riskScore")).isEqualTo(0.0);
        verify(ors, times(1)).rotaAPe(anyList());
    }

    @Test
    void relatoNoTrajetoGeraDoisDesviosComRiscoCalculado() {
        banco.add(new IncidenteRisco(LAT - 0.0001, -46.6500, TipoOcorrencia.ASSEDIO));    // no trajeto
        banco.add(new IncidenteRisco(LAT + 0.0010, -46.6550, TipoOcorrencia.AGRESSAO_VERBAL)); // ~110 m do trajeto

        List<Map<String, Object>> rotas = rotas(service.calculateRoutes(LAT, LNG_ORIGEM, LAT, LNG_DESTINO));

        assertThat(rotas).hasSize(3);
        assertThat(rotas.get(0).get("riskScore")).isEqualTo(2.0);
        assertThat(rotas.get(1).get("riskScore")).isEqualTo(0.0);
        assertThat(rotas.get(2).get("riskScore")).isEqualTo(0.0);
        verify(ors, times(3)).rotaAPe(anyList());
    }

    @Test
    void desvioMuitoLongoEhDescartado() {
        banco.add(new IncidenteRisco(LAT, -46.6500, TipoOcorrencia.ASSEDIO));
        distanciaDesvio = 5000; // > 2,5 x 2000

        assertThat(rotas(service.calculateRoutes(LAT, LNG_ORIGEM, LAT, LNG_DESTINO))).hasSize(1);
    }

    @Test
    void relatoForaDaRotaDiretaMasNoDesvioContaNoRiscoDoDesvio() {
        banco.add(new IncidenteRisco(LAT, -46.6500, TipoOcorrencia.ASSEDIO));
        // ~550 m ao sul do trajeto direto: exatamente onde passa o 1º desvio
        banco.add(new IncidenteRisco(LAT - 0.0050, -46.6500, TipoOcorrencia.AGRESSAO_FISICA));

        List<Map<String, Object>> rotas = rotas(service.calculateRoutes(LAT, LNG_ORIGEM, LAT, LNG_DESTINO));

        assertThat(rotas).hasSize(3);
        assertThat(rotas.get(0).get("riskScore")).isEqualTo(1.0);
        assertThat(rotas.get(1).get("riskScore")).isEqualTo(1.0); // desvio pelo sul passa pelo 2º relato
        assertThat(rotas.get(2).get("riskScore")).isEqualTo(0.0); // desvio pelo norte
    }

    @Test
    void alternativasDoProvedorTambemRecebemRisco() {
        banco.add(new IncidenteRisco(LAT - 0.0050, -46.6500, TipoOcorrencia.ASSEDIO));
        List<List<Double>> alternativa = new ArrayList<>(linha(LAT, LNG_ORIGEM, LAT - 0.0050, -46.6500));
        alternativa.addAll(linha(LAT - 0.0050, -46.6500, LAT, LNG_DESTINO));
        respostaDireta = resposta(rota(2000, linha(LAT, LNG_ORIGEM, LAT, LNG_DESTINO)), rota(2400, alternativa));

        List<Map<String, Object>> rotas = rotas(service.calculateRoutes(LAT, LNG_ORIGEM, LAT, LNG_DESTINO));

        assertThat(rotas).hasSize(2);
        assertThat(rotas.get(0).get("riskScore")).isEqualTo(0.0);
        assertThat(rotas.get(1).get("riskScore")).isEqualTo(1.0);
    }

    // ------------------------------------------------------------------

    /** Pontos [lng, lat] a cada ~50 m entre dois pontos. */
    private static List<List<Double>> linha(double lat1, double lng1, double lat2, double lng2) {
        int passos = 60;
        List<List<Double>> coords = new ArrayList<>();
        for (int i = 0; i <= passos; i++) {
            double t = (double) i / passos;
            coords.add(List.of(lng1 + (lng2 - lng1) * t, lat1 + (lat2 - lat1) * t));
        }
        return coords;
    }

    private static Map<String, Object> rota(double distancia, List<List<Double>> coords) {
        Map<String, Object> geometry = new LinkedHashMap<>();
        geometry.put("type", "LineString");
        geometry.put("coordinates", coords);
        Map<String, Object> rota = new LinkedHashMap<>();
        rota.put("distance", distancia);
        rota.put("duration", distancia / 1.4);
        rota.put("geometry", geometry);
        return rota;
    }

    @SafeVarargs
    private static Map<String, Object> resposta(Map<String, Object>... rotas) {
        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("code", "Ok");
        resposta.put("routes", new ArrayList<>(List.of(rotas)));
        return resposta;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rotas(Map<String, Object> resposta) {
        return (List<Map<String, Object>>) resposta.get("routes");
    }
}
