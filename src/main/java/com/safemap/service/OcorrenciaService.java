package com.safemap.service;

import com.safemap.dto.request.OcorrenciaRequest;
import com.safemap.dto.response.OcorrenciaResponse;
import com.safemap.model.Ocorrencia;
import com.safemap.model.Usuario;
import com.safemap.repository.OcorrenciaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OcorrenciaService {

    private final OcorrenciaRepository ocorrenciaRepository;
    private final ValidacaoGeograficaService validacaoGeografica;

    @Transactional(readOnly = true)
    public List<OcorrenciaResponse> listarTodas() {
        return ocorrenciaRepository.findAllForHeatmap()
                .stream()
                .map(OcorrenciaResponse::fromEntity)
                .toList();
    }

    @Transactional
    public OcorrenciaResponse criar(OcorrenciaRequest request) {
        try {
            // Limites oficiais das UFs (IBGE/PostGIS); indisponível → 503, nunca "fora do Brasil"
            if (!validacaoGeografica.validar(request.latitude(), request.longitude()).dentroDoBrasil()) {
                throw new IllegalArgumentException("As coordenadas devem estar dentro do território brasileiro.");
            }

            Ocorrencia ocorrencia = new Ocorrencia();
            ocorrencia.setTipoOcorrencia(request.tipoOcorrencia());
            ocorrencia.setPublicoAlvo(request.publicoAlvo());
            ocorrencia.setDescricao(request.descricao());
            ocorrencia.setEndereco(request.endereco());
            ocorrencia.setLatitude(request.latitude());
            ocorrencia.setLongitude(request.longitude());
            ocorrencia.setDataHora(request.dataHora());

            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof Usuario) {
                ocorrencia.setUsuario((Usuario) principal);
            }

            Ocorrencia salva = ocorrenciaRepository.save(ocorrencia);
            log.info("✅ Ocorrência registrada com sucesso. ID: {}, Tipo: {}", salva.getId(), salva.getTipoOcorrencia());
            return OcorrenciaResponse.fromEntity(salva);
        } catch (Exception e) {
            log.error("❌ FALHA ao registrar ocorrência — Detalhes: {} — Dados da requisição: {}", e.getMessage(), request, e);
            throw e;
        }
    }
}
