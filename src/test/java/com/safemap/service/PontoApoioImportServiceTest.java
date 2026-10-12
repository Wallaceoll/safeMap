package com.safemap.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safemap.exception.ValidacaoGeograficaIndisponivelException;
import com.safemap.model.PontoApoio;
import com.safemap.model.TipoPontoApoio;
import com.safemap.repository.PontoApoioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect")
class PontoApoioImportServiceTest {

    @Autowired
    private PontoApoioRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private OverpassService overpass;
    private ValidacaoGeograficaService validacao;
    private PontoApoioImportService service;

    @BeforeEach
    void setUp() {
        overpass = mock(OverpassService.class);
        validacao = mock(ValidacaoGeograficaService.class);
        // Padrão: limites das UFs ainda não carregados (importa sem conferência geográfica)
        when(validacao.ufsPorCoordenadas(anyList()))
                .thenThrow(new ValidacaoGeograficaIndisponivelException("indisponível"));
        service = new PontoApoioImportService(overpass, repository, new TransactionTemplate(transactionManager),
                new ObjectMapper(), validacao);
    }

    private static String overpassJson(String... elements) {
        return "{\"elements\":[" + String.join(",", elements) + "]}";
    }

    private static final String HOSPITAL = """
            {"type":"node","id":1,"lat":-8.13,"lon":-34.90,
             "tags":{"amenity":"hospital","name":"Hospital A","addr:street":"Av. Boa Viagem","addr:housenumber":"100","addr:suburb":"Boa Viagem","addr:city":"Recife"}}""";
    private static final String DELEGACIA_WAY = """
            {"type":"way","id":2,"center":{"lat":-8.05,"lon":-34.88},
             "tags":{"amenity":"police","name":"Delegacia da Mulher"}}""";
    private static final String ABRIGO = """
            {"type":"node","id":3,"lat":-8.06,"lon":-34.89,"tags":{"social_facility":"shelter","name":"Casa Abrigo"}}""";
    private static final String IGNORADO = """
            {"type":"node","id":4,"lat":-8.07,"lon":-34.89,"tags":{"amenity":"school","name":"Escola"}}""";

    @Test
    void importaClassificaEConverteCentroDeWays() throws Exception {
        when(overpass.executar(anyString(), any(Duration.class)))
                .thenReturn(overpassJson(HOSPITAL, DELEGACIA_WAY, ABRIGO, IGNORADO));

        assertThat(service.importarUf("PE")).isTrue();

        List<PontoApoio> pontos = repository.findByUfAndFonte("PE", PontoApoio.FONTE_OSM);
        assertThat(pontos).hasSize(3);

        PontoApoio delegacia = pontos.stream().filter(p -> "w2".equals(p.getOsmId())).findFirst().orElseThrow();
        assertThat(delegacia.getTipo()).isEqualTo(TipoPontoApoio.DELEGACIA);
        assertThat(delegacia.getLatitude()).isEqualTo(-8.05);

        PontoApoio hospital = pontos.stream().filter(p -> "n1".equals(p.getOsmId())).findFirst().orElseThrow();
        assertThat(hospital.getCidade()).isEqualTo("Recife");
        assertThat(hospital.getNumero()).isEqualTo("100");
        assertThat(hospital.getUf()).isEqualTo("PE");

        assertThat(repository.findInBounds(-8.10, -34.95, -8.00, -34.85, PageRequest.of(0, 100)))
                .extracting(PontoApoio::getOsmId)
                .containsExactlyInAnyOrder("w2", "n3");
    }

    @Test
    void reimportacaoAtualizaRemoveObsoletosEPreservaManuais() throws Exception {
        when(overpass.executar(anyString(), any(Duration.class)))
                .thenReturn(overpassJson(HOSPITAL, DELEGACIA_WAY));
        service.importarUf("PE");

        PontoApoio manual = new PontoApoio();
        manual.setTipo(TipoPontoApoio.ACOLHIMENTO);
        manual.setNome("ONG cadastrada manualmente");
        manual.setUf("PE");
        manual.setLatitude(-8.0);
        manual.setLongitude(-34.9);
        manual.setFonte(PontoApoio.FONTE_MANUAL);
        repository.save(manual);

        // Hospital mudou de nome, delegacia saiu do OSM
        when(overpass.executar(anyString(), any(Duration.class)))
                .thenReturn(overpassJson(HOSPITAL.replace("Hospital A", "Hospital B")));
        service.importarUf("PE");

        List<PontoApoio> osm = repository.findByUfAndFonte("PE", PontoApoio.FONTE_OSM);
        assertThat(osm).extracting(PontoApoio::getNome).containsExactly("Hospital B");
        assertThat(repository.findByUfAndFonte("PE", PontoApoio.FONTE_MANUAL)).hasSize(1);
    }

    @Test
    void falhaOuRespostaVaziaMantemDadosAnteriores() throws Exception {
        when(overpass.executar(anyString(), any(Duration.class))).thenReturn(overpassJson(HOSPITAL));
        service.importarUf("PE");

        when(overpass.executar(anyString(), any(Duration.class))).thenReturn(null);
        assertThat(service.importarUf("PE")).isFalse();

        when(overpass.executar(anyString(), any(Duration.class))).thenReturn(overpassJson());
        assertThat(service.importarUf("PE")).isFalse();

        assertThat(repository.findByUfAndFonte("PE", PontoApoio.FONTE_OSM)).hasSize(1);
    }

    @Test
    void objetoNaDivisaFicaApenasNaPrimeiraUf() throws Exception {
        when(overpass.executar(anyString(), any(Duration.class))).thenReturn(overpassJson(HOSPITAL));
        service.importarUf("PE");
        assertThat(service.importarUf("PB")).isTrue();

        assertThat(repository.findByUfAndFonte("PE", PontoApoio.FONTE_OSM)).hasSize(1);
        assertThat(repository.findByUfAndFonte("PB", PontoApoio.FONTE_OSM)).isEmpty();
    }

    @Test
    void pontosForaDoBrasilSaoDescartadosEDivisaEhMantida() throws Exception {
        when(overpass.executar(anyString(), any(Duration.class)))
                .thenReturn(overpassJson(HOSPITAL, DELEGACIA_WAY, ABRIGO));
        // hospital em PE, delegacia fora do Brasil (ex.: erro de coordenada no OSM), abrigo na divisa (PB)
        org.mockito.Mockito.reset(validacao);
        when(validacao.ufsPorCoordenadas(anyList())).thenReturn(java.util.Arrays.asList("PE", null, "PB"));

        assertThat(service.importarUf("PE")).isTrue();

        assertThat(repository.findByUfAndFonte("PE", PontoApoio.FONTE_OSM))
                .extracting(PontoApoio::getOsmId)
                .containsExactlyInAnyOrder("n1", "n3");
    }
}
