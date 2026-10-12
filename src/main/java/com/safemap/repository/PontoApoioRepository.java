package com.safemap.repository;

import com.safemap.model.PontoApoio;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PontoApoioRepository extends JpaRepository<PontoApoio, Long> {

    /** Pontos dentro de um retângulo (usa o índice composto latitude/longitude). */
    @Query("""
            SELECT p FROM PontoApoio p
            WHERE p.latitude BETWEEN :south AND :north
              AND p.longitude BETWEEN :west AND :east
            """)
    List<PontoApoio> findInBounds(@Param("south") double south,
                                  @Param("west") double west,
                                  @Param("north") double north,
                                  @Param("east") double east,
                                  Pageable pageable);

    List<PontoApoio> findByUfAndFonte(String uf, String fonte);

    /** osmIds já cadastrados em outra UF (objetos na divisa entre estados). */
    @Query("SELECT p.osmId FROM PontoApoio p WHERE p.uf <> :uf AND p.osmId IN :osmIds")
    List<String> findOsmIdsEmOutraUf(@Param("uf") String uf, @Param("osmIds") List<String> osmIds);

    /** Data da última importação bem-sucedida de uma UF (null se nunca importada). */
    @Query("SELECT MAX(p.atualizadoEm) FROM PontoApoio p WHERE p.uf = :uf AND p.fonte = 'OSM'")
    LocalDateTime findUltimaAtualizacao(@Param("uf") String uf);
}
