package com.safemap.service;

import com.safemap.config.BrasilBounds;
import com.safemap.exception.ValidacaoGeograficaIndisponivelException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ValidacaoGeograficaServiceTest {

    private JdbcTemplate jdbc;
    private ValidacaoGeograficaService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        BrasilBounds bounds = new BrasilBounds();
        ReflectionTestUtils.setField(bounds, "minLat", -33.76);
        ReflectionTestUtils.setField(bounds, "maxLat", 5.28);
        ReflectionTestUtils.setField(bounds, "minLng", -74.00);
        ReflectionTestUtils.setField(bounds, "maxLng", -28.80);
        service = new ValidacaoGeograficaService(jdbc, bounds);
    }

    private void limitesCarregados(int quantidade) {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(quantidade);
    }

    @SuppressWarnings("unchecked")
    private void consultaRetorna(List<ValidacaoGeograficaService.Resultado> linhas) {
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any())).thenReturn((List) linhas);
    }

    @Test
    void coordenadasMalformadasSaoRecusadas() {
        assertThatThrownBy(() -> service.validar(null, -46.6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validar(-23.5, Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validar(91.0, -46.6)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.validar(-23.5, 181.0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void foraDoRetanguloDoBrasilNaoConsultaOBanco() {
        assertThat(service.validar(38.72, -9.14).dentroDoBrasil()).isFalse(); // Lisboa
        verify(jdbc, never()).queryForObject(anyString(), eq(Integer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void dentroDeUmaUfRetornaCodigoNomeESigla() {
        limitesCarregados(27);
        consultaRetorna(List.of(new ValidacaoGeograficaService.Resultado(true, "26", "Pernambuco", "PE")));

        ValidacaoGeograficaService.Resultado r = service.validar(-8.06, -34.88);
        assertThat(r.dentroDoBrasil()).isTrue();
        assertThat(r.uf()).isEqualTo("PE");
        assertThat(r.codigoIbge()).isEqualTo("26");

        // Segunda chamada para o mesmo ponto vem do cache
        service.validar(-8.06, -34.88);
        verify(jdbc, times(1)).query(anyString(), any(RowMapper.class), any(), any());
    }

    @Test
    void dentroDoRetanguloMasForaDosPoligonosEhForaDoBrasil() {
        limitesCarregados(27);
        consultaRetorna(List.of());
        assertThat(service.validar(-34.90, -56.16).dentroDoBrasil()).isFalse(); // Montevidéu
    }

    @Test
    void limitesIncompletosGeramIndisponivelENaoForaDoBrasil() {
        limitesCarregados(0);
        assertThatThrownBy(() -> service.validar(-8.06, -34.88))
                .isInstanceOf(ValidacaoGeograficaIndisponivelException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void falhaNoBancoGeraIndisponivelENaoForaDoBrasil() {
        limitesCarregados(27);
        when(jdbc.query(anyString(), any(RowMapper.class), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("conexão perdida"));
        assertThatThrownBy(() -> service.validar(-8.06, -34.88))
                .isInstanceOf(ValidacaoGeograficaIndisponivelException.class);
    }

    @Test
    void tabelaInexistenteGeraIndisponivel() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new DataAccessResourceFailureException("relation does not exist"));
        assertThat(service.limitesCarregados()).isFalse();
        assertThatThrownBy(() -> service.validar(-8.06, -34.88))
                .isInstanceOf(ValidacaoGeograficaIndisponivelException.class);
    }
}
