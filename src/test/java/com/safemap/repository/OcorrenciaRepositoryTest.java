package com.safemap.repository;

import com.safemap.dto.response.OcorrenciaResponse;
import com.safemap.model.Ocorrencia;
import com.safemap.model.PublicoAlvo;
import com.safemap.model.TipoOcorrencia;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.generate_statistics=true"
})
class OcorrenciaRepositoryTest {

    private static final int QUANTIDADE = 30;

    @Autowired
    private OcorrenciaRepository repository;

    @Autowired
    private EntityManager entityManager;

    private Statistics estatisticas;

    @BeforeEach
    void setUp() {
        for (int i = 0; i < QUANTIDADE; i++) {
            Ocorrencia o = new Ocorrencia();
            o.setTipoOcorrencia(i % 2 == 0 ? TipoOcorrencia.ASSEDIO : TipoOcorrencia.AGRESSAO_VERBAL);
            o.setPublicoAlvo(i % 3 == 0 ? List.of(PublicoAlvo.MULHERES, PublicoAlvo.LGBT) : List.of(PublicoAlvo.MULHERES));
            o.setLatitude(-23.55 + i * 0.001);
            o.setLongitude(-46.63 + i * 0.001);
            o.setDataHora(LocalDateTime.of(2026, 10, 1, 10, 0).plusHours(i));
            repository.save(o);
        }
        entityManager.flush();
        entityManager.clear();

        estatisticas = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        estatisticas.clear();
    }

    @Test
    void listarParaOMapaUsaUmaUnicaConsulta() {
        List<OcorrenciaResponse> respostas = repository.findAllForHeatmap().stream()
                .map(OcorrenciaResponse::fromEntity)
                .toList();

        assertThat(respostas).hasSize(QUANTIDADE);
        assertThat(respostas.get(0).publicoAlvo()).isNotEmpty();
        // Antes da correção: 1 consulta + 1 por ocorrência para carregar o publicoAlvo (N+1)
        assertThat(estatisticas.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void listarParaOMapaMantemOrdemESemDuplicar() {
        List<Ocorrencia> ocorrencias = repository.findAllForHeatmap();

        // Ocorrências com dois públicos-alvo não podem aparecer duas vezes por causa do JOIN
        assertThat(ocorrencias).hasSize(QUANTIDADE);
        assertThat(ocorrencias).extracting(Ocorrencia::getId).doesNotHaveDuplicates();
        assertThat(ocorrencias.get(0).getCriadoEm()).isAfterOrEqualTo(ocorrencias.get(QUANTIDADE - 1).getCriadoEm());
    }
}
