package com.safemap.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Ponto de apoio exibido no mapa (hospital, delegacia, acolhimento social).
 *
 * - fonte = "OSM": importado periodicamente do OpenStreetMap (PontoApoioImportService).
 *   osmId identifica o objeto de origem (ex.: "n123", "w456") e permite atualizar sem duplicar.
 * - fonte = "MANUAL": cadastrado diretamente no banco (ex.: Delegacia da Mulher que não está no OSM).
 *   Nunca é alterado ou removido pela importação.
 */
@Entity
@Table(name = "tb_ponto_apoio",
        indexes = {
                @Index(name = "idx_ponto_apoio_lat_lng", columnList = "latitude, longitude"),
                @Index(name = "idx_ponto_apoio_uf_fonte", columnList = "uf, fonte"),
                @Index(name = "idx_ponto_apoio_osm_id", columnList = "osmId", unique = true)
        })
@Getter
@Setter
@NoArgsConstructor
public class PontoApoio {

    public static final String FONTE_OSM = "OSM";
    public static final String FONTE_MANUAL = "MANUAL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 30)
    private String osmId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoPontoApoio tipo;

    @Column(length = 200)
    private String nome;

    @Column(length = 200)
    private String rua;

    @Column(length = 20)
    private String numero;

    @Column(length = 120)
    private String bairro;

    @Column(length = 120)
    private String cidade;

    @Column(nullable = false, length = 2)
    private String uf;

    @Column(length = 60)
    private String telefone;

    @Column(length = 200)
    private String horario;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Column(nullable = false, length = 10)
    private String fonte = FONTE_OSM;

    @Column(nullable = false)
    private LocalDateTime atualizadoEm;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
