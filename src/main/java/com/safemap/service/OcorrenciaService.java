package com.safemap.service;

import com.safemap.dto.request.OcorrenciaRequest;
import com.safemap.dto.response.OcorrenciaResponse;
import com.safemap.model.Ocorrencia;
import com.safemap.model.Usuario;
import com.safemap.repository.OcorrenciaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OcorrenciaService {

    private final OcorrenciaRepository ocorrenciaRepository;

    @Transactional(readOnly = true)
    public List<OcorrenciaResponse> listarTodas() {
        return ocorrenciaRepository.findAllForHeatmap()
                .stream()
                .map(OcorrenciaResponse::fromEntity)
                .toList();
    }

    @Transactional
    public OcorrenciaResponse criar(OcorrenciaRequest request) {
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
        return OcorrenciaResponse.fromEntity(salva);
    }
}
