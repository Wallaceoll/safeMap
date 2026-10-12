package com.safemap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Importa os limites oficiais das 27 UFs (IBGE, API de Malhas v3) para public.tb_limite_uf.
 *
 * - Roda ao iniciar a aplicação, apenas se a tabela não tiver os 27 limites válidos.
 * - A tabela precisa existir (script src/main/resources/db/scripts/V1__tb_limite_uf.sql).
 * - Tudo numa transação: os dados anteriores só são substituídos se a nova malha
 *   tiver as 27 UFs e passar na verificação (geometria válida, SRID 4326).
 *
 * A malha do IBGE usa SIRGAS 2000 (EPSG:4674), que coincide com o WGS 84 (EPSG:4326)
 * na escala de centímetros; por isso a geometria é gravada com SRID 4326.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LimiteUfImportService {

    static final String URL_MALHA =
            "https://servicodados.ibge.gov.br/api/v3/malhas/paises/BR?formato=application/vnd.geo%2Bjson&qualidade=maxima&intrarregiao=UF";

    /** Código IBGE → {sigla, nome} das 27 UFs. */
    static final Map<String, String[]> UFS = new LinkedHashMap<>();
    static {
        String[][] dados = {
                {"11", "RO", "Rondônia"}, {"12", "AC", "Acre"}, {"13", "AM", "Amazonas"}, {"14", "RR", "Roraima"},
                {"15", "PA", "Pará"}, {"16", "AP", "Amapá"}, {"17", "TO", "Tocantins"}, {"21", "MA", "Maranhão"},
                {"22", "PI", "Piauí"}, {"23", "CE", "Ceará"}, {"24", "RN", "Rio Grande do Norte"},
                {"25", "PB", "Paraíba"}, {"26", "PE", "Pernambuco"}, {"27", "AL", "Alagoas"}, {"28", "SE", "Sergipe"},
                {"29", "BA", "Bahia"}, {"31", "MG", "Minas Gerais"}, {"32", "ES", "Espírito Santo"},
                {"33", "RJ", "Rio de Janeiro"}, {"35", "SP", "São Paulo"}, {"41", "PR", "Paraná"},
                {"42", "SC", "Santa Catarina"}, {"43", "RS", "Rio Grande do Sul"}, {"50", "MS", "Mato Grosso do Sul"},
                {"51", "MT", "Mato Grosso"}, {"52", "GO", "Goiás"}, {"53", "DF", "Distrito Federal"}
        };
        for (String[] uf : dados) UFS.put(uf[0], new String[]{uf[1], uf[2]});
    }

    private static final String SQL_UPSERT = """
            INSERT INTO public.tb_limite_uf (codigo_ibge, nome, uf, geometria, fonte, importado_em)
            VALUES (?, ?, ?,
                    ST_Multi(ST_CollectionExtract(ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON(?), 4326)), 3)),
                    ?, now())
            ON CONFLICT (codigo_ibge) DO UPDATE
               SET nome = EXCLUDED.nome, uf = EXCLUDED.uf, geometria = EXCLUDED.geometria,
                   fonte = EXCLUDED.fonte, importado_em = EXCLUDED.importado_em
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final ValidacaoGeograficaService validacaoGeografica;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        Thread thread = new Thread(this::importarSeNecessario, "limite-uf-import");
        thread.setDaemon(true);
        thread.start();
    }

    public void importarSeNecessario() {
        if (!tabelaExiste()) {
            log.error("[geo] Tabela public.tb_limite_uf não existe. Execute o script "
                    + "src/main/resources/db/scripts/V1__tb_limite_uf.sql no Supabase. "
                    + "Até lá, novos relatos serão recusados com 503.");
            return;
        }
        if (validacaoGeografica.limitesCarregados()) {
            log.info("[geo] Limites das 27 UFs já carregados.");
            return;
        }
        try {
            importar();
        } catch (Exception e) {
            log.error("[geo] Falha ao importar limites do IBGE: {}. Nova tentativa no próximo reinício.",
                    NestedExceptionUtils.getMostSpecificCause(e).getMessage());
        }
    }

    /** Baixa a malha do IBGE e grava as 27 UFs (substitui as existentes). */
    public void importar() throws Exception {
        log.info("[geo] Baixando limites das UFs do IBGE...");
        HttpRequest request = HttpRequest.newBuilder(URI.create(URL_MALHA))
                .timeout(Duration.ofMinutes(2))
                .header("Accept", "application/vnd.geo+json, application/json")
                .GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("IBGE retornou status " + response.statusCode());
        }

        Map<String, String> geometrias = extrairGeometrias(objectMapper.readTree(response.body()));
        String fonte = "IBGE API Malhas v3 (paises/BR, intrarregiao=UF, qualidade=maxima), baixado em " + LocalDate.now();

        transactionTemplate.executeWithoutResult(status -> {
            for (Map.Entry<String, String> e : geometrias.entrySet()) {
                String[] uf = UFS.get(e.getKey());
                jdbcTemplate.update(SQL_UPSERT, e.getKey(), uf[1], uf[0], e.getValue(), fonte);
            }
            Integer ok = jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM public.tb_limite_uf
                    WHERE ST_IsValid(geometria) AND ST_SRID(geometria) = 4326
                      AND GeometryType(geometria) = 'MULTIPOLYGON'
                    """, Integer.class);
            if (ok == null || ok != ValidacaoGeograficaService.TOTAL_UFS) {
                throw new IllegalStateException("Verificação falhou: " + ok + " UFs válidas (esperado 27)");
            }
        });

        validacaoGeografica.invalidarCache();
        log.info("[geo] Limites das 27 UFs importados e verificados ({}).", fonte);
    }

    /**
     * Extrai e confere a geometria de cada UF do GeoJSON do IBGE.
     *
     * @return código IBGE → geometria (GeoJSON)
     * @throws IllegalStateException se faltar, sobrar ou repetir alguma UF
     */
    Map<String, String> extrairGeometrias(JsonNode featureCollection) {
        Map<String, String> geometrias = new TreeMap<>();
        for (JsonNode feature : featureCollection.path("features")) {
            String codigo = feature.path("properties").path("codarea").asText();
            JsonNode geometria = feature.path("geometry");
            String tipo = geometria.path("type").asText();

            if (!UFS.containsKey(codigo)) {
                throw new IllegalStateException("Código de UF inesperado na malha do IBGE: " + codigo);
            }
            if (!tipo.equals("Polygon") && !tipo.equals("MultiPolygon")) {
                throw new IllegalStateException("Geometria inesperada para a UF " + codigo + ": " + tipo);
            }
            if (geometrias.put(codigo, geometria.toString()) != null) {
                throw new IllegalStateException("UF repetida na malha do IBGE: " + codigo);
            }
        }
        if (geometrias.size() != UFS.size()) {
            throw new IllegalStateException("Malha do IBGE com " + geometrias.size() + " UFs (esperado 27)");
        }
        return geometrias;
    }

    private boolean tabelaExiste() {
        String tabela = jdbcTemplate.queryForObject("SELECT to_regclass('public.tb_limite_uf')::text", String.class);
        return tabela != null;
    }
}
