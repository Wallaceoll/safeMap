package com.safemap.service;

import com.safemap.config.BrasilBounds;
import com.safemap.exception.ValidacaoGeograficaIndisponivelException;
import com.safemap.util.TtlCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Validação geográfica com os limites oficiais das UFs (IBGE) em PostGIS
 * (tabela public.tb_limite_uf, preenchida pelo LimiteUfImportService).
 *
 * Resultados possíveis:
 * - coordenada dentro de uma UF          → {@link Resultado#dentroDoBrasil()} = true, com código, nome e sigla
 * - coordenada fora do Brasil            → dentroDoBrasil = false
 * - coordenada malformada                → IllegalArgumentException (HTTP 400)
 * - limites ausentes/incompletos ou erro → ValidacaoGeograficaIndisponivelException (HTTP 503),
 *                                          nunca reportado como "fora do Brasil"
 *
 * Pontos exatamente na divisa entre duas UFs: ST_Covers aceita ambos os polígonos;
 * fica com o menor código IBGE (regra determinística).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidacaoGeograficaService {

    public static final int TOTAL_UFS = 27;

    private static final String SQL_PONTO = """
            SELECT codigo_ibge, nome, uf FROM public.tb_limite_uf
            WHERE ST_Covers(geometria, ST_SetSRID(ST_MakePoint(?, ?), 4326))
            ORDER BY codigo_ibge LIMIT 1
            """;

    private static final String SQL_LOTE = """
            SELECT p.idx,
                   (SELECT l.uf FROM public.tb_limite_uf l
                    WHERE ST_Covers(l.geometria, ST_SetSRID(ST_MakePoint(p.lng, p.lat), 4326))
                    ORDER BY l.codigo_ibge LIMIT 1) AS uf
            FROM unnest(?::float8[], ?::float8[]) WITH ORDINALITY AS p(lat, lng, idx)
            """;

    private static final int TAMANHO_LOTE = 2000;
    private static final Duration VERIFICAR_DISPONIBILIDADE_A_CADA = Duration.ofMinutes(5);

    public record Resultado(boolean dentroDoBrasil, String codigoIbge, String nome, String uf) {
        static final Resultado FORA = new Resultado(false, null, null, null);
    }

    private final JdbcTemplate jdbcTemplate;
    private final BrasilBounds brasilBounds;

    private final TtlCache<String, Resultado> cache = new TtlCache<>(Duration.ofDays(1), 20_000);
    private volatile Instant disponivelAte = Instant.MIN;

    public Resultado validar(Double latitude, Double longitude) {
        validarFaixa(latitude, longitude);

        // Pré-filtro barato: fora do retângulo do Brasil não precisa consultar o banco
        if (!brasilBounds.contem(latitude, longitude)) {
            return Resultado.FORA;
        }

        String chave = String.format(Locale.US, "%.5f,%.5f", latitude, longitude);
        Resultado cached = cache.get(chave);
        if (cached != null) return cached;

        garantirDisponivel();
        try {
            List<Resultado> linhas = jdbcTemplate.query(SQL_PONTO,
                    (rs, i) -> new Resultado(true, rs.getString("codigo_ibge"), rs.getString("nome"), rs.getString("uf")),
                    longitude, latitude);
            Resultado resultado = linhas.isEmpty() ? Resultado.FORA : linhas.get(0);
            cache.put(chave, resultado);
            return resultado;
        } catch (DataAccessException e) {
            throw indisponivel("Falha na consulta espacial", e);
        }
    }

    /**
     * UF de cada coordenada ({lat, lng}), na mesma ordem; null para pontos fora do Brasil.
     */
    public List<String> ufsPorCoordenadas(List<double[]> coordenadas) {
        garantirDisponivel();
        List<String> ufs = new ArrayList<>(coordenadas.size());
        for (int inicio = 0; inicio < coordenadas.size(); inicio += TAMANHO_LOTE) {
            List<double[]> lote = coordenadas.subList(inicio, Math.min(inicio + TAMANHO_LOTE, coordenadas.size()));
            Double[] lats = lote.stream().map(c -> c[0]).toArray(Double[]::new);
            Double[] lngs = lote.stream().map(c -> c[1]).toArray(Double[]::new);
            String[] resultado = new String[lote.size()];
            try {
                jdbcTemplate.query(con -> {
                    PreparedStatement ps = con.prepareStatement(SQL_LOTE);
                    ps.setArray(1, con.createArrayOf("float8", lats));
                    ps.setArray(2, con.createArrayOf("float8", lngs));
                    return ps;
                }, rs -> {
                    resultado[rs.getInt("idx") - 1] = rs.getString("uf");
                });
            } catch (DataAccessException e) {
                throw indisponivel("Falha na consulta espacial em lote", e);
            }
            ufs.addAll(Arrays.asList(resultado));
        }
        return ufs;
    }

    /** True se os 27 limites estão carregados e válidos. */
    public boolean limitesCarregados() {
        try {
            Integer validos = jdbcTemplate.queryForObject(
                    "SELECT count(*) FROM public.tb_limite_uf WHERE ST_IsValid(geometria) AND ST_SRID(geometria) = 4326",
                    Integer.class);
            return validos != null && validos == TOTAL_UFS;
        } catch (DataAccessException e) {
            log.debug("[geo] Tabela de limites indisponível: {}", e.getMessage());
            return false;
        }
    }

    private void garantirDisponivel() {
        if (Instant.now().isBefore(disponivelAte)) return;
        if (!limitesCarregados()) {
            throw indisponivel("Limites das UFs ausentes ou incompletos em public.tb_limite_uf", null);
        }
        disponivelAte = Instant.now().plus(VERIFICAR_DISPONIBILIDADE_A_CADA);
    }

    /** Força uma nova verificação na próxima validação (ex.: após importar os limites). */
    public void invalidarCache() {
        disponivelAte = Instant.MIN;
    }

    private void validarFaixa(Double latitude, Double longitude) {
        if (latitude == null || longitude == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Coordenadas inválidas.");
        }
    }

    private ValidacaoGeograficaIndisponivelException indisponivel(String motivo, Exception causa) {
        log.error("[geo] Validação geográfica indisponível: {}{}", motivo,
                causa != null ? " — " + causa.getClass().getSimpleName() : "");
        return new ValidacaoGeograficaIndisponivelException(
                "Validação de localização temporariamente indisponível. Tente novamente em instantes.", causa);
    }
}
