package com.safemap.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safemap.exception.ValidacaoGeograficaIndisponivelException;
import com.safemap.model.PontoApoio;
import com.safemap.model.TipoPontoApoio;
import com.safemap.repository.PontoApoioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Importa os pontos de apoio (hospitais, delegacias, acolhimento social) do
 * OpenStreetMap para o banco, estado por estado.
 *
 * - Roda ao iniciar a aplicação e todo dia de madrugada, mas só reimporta as UFs
 *   cuja última importação tem mais de {@link #REFRESH_INTERVAL}.
 * - Uma UF que falhar (Overpass indisponível) é tentada de novo na próxima execução,
 *   e os dados anteriores continuam valendo até lá.
 * - Pontos com fonte MANUAL nunca são alterados.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PontoApoioImportService {

    /** Ordenadas por população, para que os estados com mais usuários fiquem prontos primeiro. */
    static final List<String> UFS = List.of(
            "SP", "MG", "RJ", "BA", "PR", "RS", "PE", "CE", "PA", "SC", "GO", "MA", "AM", "ES",
            "PB", "RN", "MT", "AL", "PI", "DF", "MS", "SE", "RO", "TO", "AC", "AP", "RR"
    );

    private static final Duration REFRESH_INTERVAL = Duration.ofDays(30);
    /**
     * Timeout declarado na consulta. Valores altos fazem a Overpass recusar a consulta
     * (504) quando está carregada; 90 s basta até para SP inteiro (~25 s).
     */
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(90);
    /** Tempo máximo de espera pela resposta HTTP (fila + execução + download). */
    private static final Duration HTTP_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration PAUSE_BETWEEN_UFS = Duration.ofSeconds(10);
    /** Rodadas por execução; as UFs que falharem são tentadas de novo após a pausa. */
    private static final int MAX_PASSES = 3;
    private static final Duration PAUSE_BETWEEN_PASSES = Duration.ofMinutes(10);

    private final OverpassService overpassService;
    private final PontoApoioRepository repository;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final ValidacaoGeograficaService validacaoGeografica;

    @Value("${APOIO_IMPORT_ENABLED:true}")
    private boolean importEnabled;

    private final AtomicBoolean running = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void aoIniciar() {
        if (!importEnabled) {
            log.info("[apoio] Importação de pontos de apoio desativada (APOIO_IMPORT_ENABLED=false)");
            return;
        }
        Thread thread = new Thread(this::importarDesatualizadas, "apoio-import");
        thread.setDaemon(true);
        thread.start();
    }

    @Scheduled(cron = "0 0 4 * * *", zone = "America/Sao_Paulo")
    public void agendado() {
        if (importEnabled) importarDesatualizadas();
    }

    /** Importa todas as UFs desatualizadas, uma por vez. */
    public void importarDesatualizadas() {
        if (!running.compareAndSet(false, true)) {
            log.info("[apoio] Importação já em andamento; ignorando nova execução");
            return;
        }
        try {
            LocalDateTime limite = LocalDateTime.now().minus(REFRESH_INTERVAL);
            List<String> pendentes = UFS.stream()
                    .filter(uf -> {
                        LocalDateTime ultima = repository.findUltimaAtualizacao(uf);
                        return ultima == null || !ultima.isAfter(limite);
                    })
                    .toList();

            for (int pass = 1; pass <= MAX_PASSES && !pendentes.isEmpty(); pass++) {
                if (pass > 1) {
                    log.info("[apoio] {} UF(s) falharam; nova tentativa em {} min: {}",
                            pendentes.size(), PAUSE_BETWEEN_PASSES.toMinutes(), pendentes);
                    Thread.sleep(PAUSE_BETWEEN_PASSES.toMillis());
                }

                List<String> falharam = new ArrayList<>();
                for (String uf : pendentes) {
                    if (!importarUf(uf)) falharam.add(uf);
                    Thread.sleep(PAUSE_BETWEEN_UFS.toMillis());
                }
                pendentes = falharam;
            }

            if (pendentes.isEmpty()) {
                log.info("[apoio] Importação concluída: todas as UFs estão atualizadas.");
            } else {
                log.warn("[apoio] UF(s) sem importação após {} rodadas: {}. Nova tentativa na próxima execução.",
                        MAX_PASSES, pendentes);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running.set(false);
        }
    }

    /** Importa uma UF. Retorna false se a Overpass falhar. */
    public boolean importarUf(String uf) throws InterruptedException {
        log.info("[apoio] Importando pontos de apoio de {}...", uf);
        String body = overpassService.executar(montarQuery(uf), HTTP_TIMEOUT);
        if (body == null) {
            log.warn("[apoio] Falha ao importar {}: Overpass indisponível. Nova tentativa na próxima execução.", uf);
            return false;
        }

        List<PontoApoio> convertidos;
        try {
            convertidos = converter(objectMapper.readTree(body), uf);
        } catch (Exception e) {
            log.warn("[apoio] Resposta inválida da Overpass para {}: {}", uf, e.getMessage());
            return false;
        }

        List<PontoApoio> importados = conferirLimites(uf, convertidos);

        // Proteção: uma resposta vazia para uma UF inteira indica erro, não ausência de dados
        if (importados.isEmpty()) {
            log.warn("[apoio] Nenhum ponto retornado para {}; mantendo dados anteriores.", uf);
            return false;
        }

        int[] stats = transactionTemplate.execute(status -> sincronizar(uf, importados));
        log.info("[apoio] {}: {} pontos ({} novos, {} removidos)", uf, importados.size(), stats[0], stats[1]);
        return true;
    }

    private String montarQuery(String uf) {
        return "[out:json][timeout:" + QUERY_TIMEOUT.toSeconds() + "];"
                + "area[\"ISO3166-2\"=\"BR-" + uf + "\"][\"admin_level\"=\"4\"]->.uf;"
                + "("
                + "nwr[\"amenity\"=\"hospital\"](area.uf);"
                + "nwr[\"amenity\"=\"police\"](area.uf);"
                + "nwr[\"amenity\"=\"social_facility\"](area.uf);"
                + "nwr[\"social_facility\"](area.uf);"
                + ");out center tags;";
    }

    List<PontoApoio> converter(JsonNode root, String uf) {
        List<PontoApoio> pontos = new ArrayList<>();
        Set<String> vistos = new HashSet<>();

        for (JsonNode el : root.path("elements")) {
            JsonNode tags = el.path("tags");
            TipoPontoApoio tipo = classificar(tags);
            if (tipo == null) continue;

            JsonNode coords = el.has("lat") ? el : el.path("center");
            if (!coords.has("lat") || !coords.has("lon")) continue;

            String osmId = el.path("type").asText("x").charAt(0) + el.path("id").asText();
            if (!vistos.add(osmId)) continue;

            PontoApoio p = new PontoApoio();
            p.setOsmId(osmId);
            p.setTipo(tipo);
            p.setNome(limitar(texto(tags, "name", "official_name", "operator"), 200));
            p.setRua(limitar(texto(tags, "addr:street"), 200));
            p.setNumero(limitar(texto(tags, "addr:housenumber"), 20));
            p.setBairro(limitar(texto(tags, "addr:suburb", "addr:neighbourhood", "addr:district"), 120));
            p.setCidade(limitar(texto(tags, "addr:city"), 120));
            p.setUf(uf);
            p.setTelefone(limitar(texto(tags, "phone", "contact:phone"), 60));
            p.setHorario(limitar(texto(tags, "opening_hours"), 200));
            p.setLatitude(coords.path("lat").asDouble());
            p.setLongitude(coords.path("lon").asDouble());
            p.setFonte(PontoApoio.FONTE_OSM);
            pontos.add(p);
        }
        return pontos;
    }

    /**
     * Confere cada ponto com os limites oficiais das UFs (IBGE):
     * - fora do Brasil → descartado;
     * - em outra UF → mantido na UF da consulta (a sincronização é feita por UF consultada;
     *   mudar a UF faria o ponto alternar entre importações) e contabilizado no log.
     * Sem os limites carregados, mantém todos os pontos (exibição não exige validação).
     */
    List<PontoApoio> conferirLimites(String uf, List<PontoApoio> pontos) {
        if (pontos.isEmpty()) return pontos;

        List<String> ufsReais;
        try {
            ufsReais = validacaoGeografica.ufsPorCoordenadas(
                    pontos.stream().map(p -> new double[]{p.getLatitude(), p.getLongitude()}).toList());
        } catch (ValidacaoGeograficaIndisponivelException e) {
            log.warn("[apoio] {}: limites das UFs indisponíveis; pontos importados sem conferência geográfica.", uf);
            return pontos;
        }

        List<PontoApoio> validos = new ArrayList<>(pontos.size());
        int fora = 0, outraUf = 0;
        for (int i = 0; i < pontos.size(); i++) {
            String ufReal = ufsReais.get(i);
            if (ufReal == null) {
                fora++;
                continue;
            }
            if (!ufReal.equals(uf)) {
                outraUf++;
                log.debug("[apoio] {} está em {} pelos limites do IBGE (consulta: {})", pontos.get(i).getOsmId(), ufReal, uf);
            }
            validos.add(pontos.get(i));
        }
        if (fora > 0 || outraUf > 0) {
            log.info("[apoio] {}: {} ponto(s) fora do Brasil descartado(s); {} ponto(s) na divisa com outra UF.", uf, fora, outraUf);
        }
        return validos;
    }

    /** Insere/atualiza os pontos da UF e remove os que não existem mais no OSM. */
    private int[] sincronizar(String uf, List<PontoApoio> importados) {
        Map<String, PontoApoio> existentes = new HashMap<>();
        for (PontoApoio p : repository.findByUfAndFonte(uf, PontoApoio.FONTE_OSM)) {
            existentes.put(p.getOsmId(), p);
        }

        List<String> idsNovos = importados.stream()
                .map(PontoApoio::getOsmId)
                .filter(id -> !existentes.containsKey(id))
                .toList();
        Set<String> emOutraUf = new HashSet<>();
        for (int i = 0; i < idsNovos.size(); i += 1000) {
            emOutraUf.addAll(repository.findOsmIdsEmOutraUf(uf, idsNovos.subList(i, Math.min(i + 1000, idsNovos.size()))));
        }

        int novos = 0;
        List<PontoApoio> salvar = new ArrayList<>(importados.size());
        for (PontoApoio novo : importados) {
            if (emOutraUf.contains(novo.getOsmId())) continue;

            PontoApoio atual = existentes.remove(novo.getOsmId());
            if (atual == null) {
                atual = novo;
                novos++;
            } else {
                atual.setTipo(novo.getTipo());
                atual.setNome(novo.getNome());
                atual.setRua(novo.getRua());
                atual.setNumero(novo.getNumero());
                atual.setBairro(novo.getBairro());
                atual.setCidade(novo.getCidade());
                atual.setTelefone(novo.getTelefone());
                atual.setHorario(novo.getHorario());
                atual.setLatitude(novo.getLatitude());
                atual.setLongitude(novo.getLongitude());
                atual.setAtualizadoEm(LocalDateTime.now()); // força o @PreUpdate mesmo sem mudanças
            }
            salvar.add(atual);
        }
        repository.saveAll(salvar);
        repository.deleteAll(existentes.values());
        return new int[]{novos, existentes.size()};
    }

    private TipoPontoApoio classificar(JsonNode tags) {
        String amenity = tags.path("amenity").asText("");
        if (amenity.equals("hospital")) return TipoPontoApoio.HOSPITAL;
        if (amenity.equals("police")) return TipoPontoApoio.DELEGACIA;
        if (amenity.equals("social_facility") || tags.has("social_facility")) return TipoPontoApoio.ACOLHIMENTO;
        return null;
    }

    private String texto(JsonNode tags, String... chaves) {
        for (String chave : chaves) {
            String valor = tags.path(chave).asText("").trim();
            if (!valor.isEmpty()) return valor;
        }
        return null;
    }

    private String limitar(String valor, int max) {
        return valor == null || valor.length() <= max ? valor : valor.substring(0, max);
    }
}
