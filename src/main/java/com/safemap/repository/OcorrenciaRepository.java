package com.safemap.repository;


import com.safemap.model.Ocorrencia;
import com.safemap.model.TipoOcorrencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OcorrenciaRepository extends JpaRepository<Ocorrencia, Long> {

    /**
     * Busca ocorrências dentro de um raio em quilômetros.
     *
     * Usa a fórmula de Haversine simplificada via JPQL.
     * Para produção com muitos registros, prefira a query nativa
     * com ST_DWithin do PostGIS (comentada abaixo).
     *
     * @param lat     Latitude do centro da busca
     * @param lng     Longitude do centro da busca
     * @param raioKm  Raio em quilômetros
     */
    @Query("""
            SELECT o FROM Ocorrencia o
            WHERE (6371 * acos(
                cos(radians(:lat)) * cos(radians(o.latitude))
                * cos(radians(o.longitude) - radians(:lng))
                + sin(radians(:lat)) * sin(radians(o.latitude))
            )) <= :raioKm
            ORDER BY o.dataHora DESC
            """)
    List<Ocorrencia> findByRaio(
            @Param("lat")    double lat,
            @Param("lng")    double lng,
            @Param("raioKm") double raioKm
    );

    /*
     * Alternativa com PostGIS (descomente quando o banco tiver a extensão ativada
     * e o campo `localizacao` do tipo geometry for adicionado na entidade):
     *
     * @Query(value = """
     *     SELECT * FROM tb_ocorrencia
     *     WHERE ST_DWithin(
     *         localizacao::geography,
     *         ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
     *         :raioMetros
     *     )
     *     ORDER BY dataHora DESC
     *     """, nativeQuery = true)
     * List<Ocorrencia> findByRaioPostGIS(double lat, double lng, double raioMetros);
     */

    /** Para o heatmap: retorna todos os pontos com lat/lng. */
    @Query("SELECT o FROM Ocorrencia o ORDER BY o.criadoEm DESC")
    List<Ocorrencia> findAllForHeatmap();

    /** Filtra por tipo (pode ser expandido para o mapa). */
    List<Ocorrencia> findByTipoOcorrencia(TipoOcorrencia tipo);
}
