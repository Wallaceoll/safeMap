package com.safemap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenRouteServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenRouteService service = new OpenRouteService(mapper);

    @Test
    @SuppressWarnings("unchecked")
    void converteGeocodificacaoParaFormatoNominatim() throws Exception {
        String json = """
                {"type":"FeatureCollection","features":[
                  {"geometry":{"type":"Point","coordinates":[-34.9016,-8.1361]},
                   "properties":{"layer":"address","label":"Avenida Boa Viagem, 1000, Recife, PE, Brasil",
                     "street":"Avenida Boa Viagem","housenumber":"1000","neighbourhood":"Boa Viagem",
                     "locality":"Recife","region":"Pernambuco","region_a":"PE","country_a":"BRA"}},
                  {"geometry":{"type":"Point","coordinates":[-34.88,-8.05]},
                   "properties":{"layer":"locality","label":"Recife, PE, Brasil","name":"Recife",
                     "locality":"Recife","region_a":"PE","country_a":"BRA"}}
                ]}""";

        List<Map<String, Object>> itens = service.converterFeatures(mapper.readTree(json));

        assertThat(itens).hasSize(2);
        Map<String, Object> endereco = itens.get(0);
        assertThat(endereco.get("lat")).isEqualTo(-8.1361);
        assertThat(endereco.get("lon")).isEqualTo(-34.9016);
        assertThat(endereco.get("type")).isEqualTo("address");

        Map<String, Object> address = (Map<String, Object>) endereco.get("address");
        assertThat(address).containsEntry("road", "Avenida Boa Viagem")
                .containsEntry("house_number", "1000")
                .containsEntry("suburb", "Boa Viagem")
                .containsEntry("city", "Recife")
                .containsEntry("state", "PE")
                .containsEntry("country_code", "br");

        // Cidades viram class=place/type=city, para o frontend poder descartá-las ao relatar
        assertThat(itens.get(1)).containsEntry("class", "place").containsEntry("type", "city");
        assertThat(itens.get(0).get("name")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void lugaresTrazemONomeSeparadoDoEndereco() throws Exception {
        String json = """
                {"type":"FeatureCollection","features":[
                  {"geometry":{"type":"Point","coordinates":[-46.5698,-23.5921]},
                   "properties":{"layer":"venue","name":"McDonald's","label":"McDonald's, São Paulo, SP, Brasil",
                     "street":"Rua Costa Barros","neighbourhood":"São Lucas","locality":"São Paulo",
                     "region_a":"SP","country_a":"BRA"}},
                  {"geometry":{"type":"Point","coordinates":[-46.5800,-23.5850]},
                   "properties":{"layer":"venue","name":"Galo Jack Mix Bar","locality":"São Paulo",
                     "region_a":"SP","country_a":"BRA"}}
                ]}""";

        List<Map<String, Object>> itens = service.converterFeatures(mapper.readTree(json));

        assertThat(itens.get(0)).containsEntry("name", "McDonald's");
        assertThat((Map<String, Object>) itens.get(0).get("address")).containsEntry("road", "Rua Costa Barros");
        // Sem rua cadastrada: o nome não vira "rua" (o frontend mostra só o nome + bairro/cidade)
        assertThat(itens.get(1)).containsEntry("name", "Galo Jack Mix Bar");
        assertThat((Map<String, Object>) itens.get(1).get("address")).containsEntry("road", null);
    }

    @Test
    @SuppressWarnings("unchecked")
    void converteRotasParaFormatoOsrm() throws Exception {
        String json = """
                {"type":"FeatureCollection","features":[
                  {"geometry":{"type":"LineString","coordinates":[[-34.90,-8.13],[-34.89,-8.12]]},
                   "properties":{"summary":{"distance":1520.4,"duration":1094.7}}},
                  {"geometry":{"type":"LineString","coordinates":[[-34.90,-8.13],[-34.88,-8.12]]},
                   "properties":{"summary":{"distance":1800.0,"duration":1300.0}}}
                ]}""";

        Map<String, Object> resposta = service.converterRotas(mapper.readTree(json));

        assertThat(resposta.get("code")).isEqualTo("Ok");
        List<Map<String, Object>> routes = (List<Map<String, Object>>) resposta.get("routes");
        assertThat(routes).hasSize(2);
        assertThat(routes.get(0)).containsEntry("distance", 1520.4).containsEntry("duration", 1094.7);

        Map<String, Object> geometry = (Map<String, Object>) routes.get(0).get("geometry");
        assertThat((List<List<Double>>) geometry.get("coordinates")).first().isEqualTo(List.of(-34.90, -8.13));
    }

    @Test
    void semChaveNaoChamaServicoExterno() {
        assertThat(service.isConfigurado()).isFalse();
        assertThat(service.geocodificar("Recife", true, null, null)).isNull();
        assertThat(service.rotaAPe(List.of(new double[]{-34.9, -8.1}, new double[]{-34.8, -8.0}))).isNull();
    }
}
