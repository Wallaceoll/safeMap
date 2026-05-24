package com.safemap.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Representa uma ocorrência relatada por um usuário no mapa.
 *
 * Sobre coordenadas: usamos latitude/longitude como Double simples
 * (em vez de tipo geoespacial Point) para máxima compatibilidade com
 * o Hibernate padrão. Quando o PostGIS estiver configurado no servidor,
 * o campo `localizacao` pode ser ativado substituindo os campos lat/lng.
 */
@Entity
@Table(name = "tb_ocorrencia",
        indexes = {
                @Index(name = "idx_ocorrencia_latitude",  columnList = "latitude"),
                @Index(name = "idx_ocorrencia_longitude", columnList = "longitude"),
                @Index(name = "idx_ocorrencia_dataHora",  columnList = "dataHora")
        })
@Getter
@Setter
@NoArgsConstructor
public class Ocorrencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Tipo de ocorrência é obrigatório")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoOcorrencia tipoOcorrencia;

    /**
     * Armazena múltiplos públicos-alvo em tabela separada.
     * Ex: ["MULHERES", "LGBT"] → linha para cada um.
     */
    @ElementCollection(targetClass = PublicoAlvo.class, fetch = FetchType.EAGER)
    @CollectionTable(
            name = "tb_ocorrencia_publico_alvo",
            joinColumns = @JoinColumn(name = "ocorrencia_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "publico_alvo", length = 20)
    private List<PublicoAlvo> publicoAlvo = new ArrayList<>();

    /**
     * Descrição livre pelo usuário.
     * Se null ou vazio, o backend substitui pela mensagem padrão.
     */
    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(length = 300)
    private String endereco;

    @NotNull(message = "Latitude é obrigatória")
    @Column(nullable = false)
    private Double latitude;

    @NotNull(message = "Longitude é obrigatória")
    @Column(nullable = false)
    private Double longitude;

    @NotNull(message = "Data/hora é obrigatória")
    @Column(nullable = false)
    private LocalDateTime dataHora;

    /**
     * Calculado automaticamente no @PrePersist a partir de dataHora.
     * DIURNO: 06:00–17:59 | NOTURNO: 18:00–05:59
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Periodo periodo;

    /** Usuário que criou o relato. Nullable = permite relatos anônimos. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    // --------------------------------------------------------
    //  Hooks do ciclo de vida
    // --------------------------------------------------------

    @PrePersist
    protected void onCreate() {
        this.criadoEm = LocalDateTime.now();
        calcularPeriodo();
        aplicarDescricaoPadrao();
    }

    @PreUpdate
    protected void onUpdate() {
        calcularPeriodo();
        aplicarDescricaoPadrao();
    }

    /**
     * Calcula diurno/noturno automaticamente.
     * Diurno = 06:00 até 17:59.
     */
    private void calcularPeriodo() {
        if (this.dataHora != null) {
            int hora = this.dataHora.getHour();
            this.periodo = (hora >= 6 && hora < 18) ? Periodo.DIURNO : Periodo.NOTURNO;
        }
    }

    /**
     * Se o usuário não informou descrição, aplica o texto padrão.
     */
    private void aplicarDescricaoPadrao() {
        if (this.descricao == null || this.descricao.isBlank()) {
            this.descricao = "O usuário preferiu não descrever o ocorrido.";
        }
    }
}

