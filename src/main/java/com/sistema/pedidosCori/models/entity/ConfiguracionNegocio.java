package com.sistema.pedidosCori.models.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "configuracion_negocio")
@Getter
@Setter
public class ConfiguracionNegocio {
    @Id
    private Long id = 1L;

    @Version
    private Long version;

    @Column(nullable = false, length = 120)
    private String nombreNegocio;
    @Column(length = 240)
    private String eslogan;
    @Column(nullable = false, length = 7)
    private String colorPrimario;
    @Column(nullable = false, length = 7)
    private String colorSecundario;
    @Column(nullable = false, length = 7)
    private String colorAcento;
    @Column(nullable = false, length = 40)
    private String puntoAtencionSingular;
    @Column(nullable = false, length = 40)
    private String puntoAtencionPlural;
    @Column(nullable = false, length = 40)
    private String etiquetaParaLlevar;
    @Column(nullable = false, length = 40)
    private String etiquetaRolMesero;
    @Column(nullable = false, length = 40)
    private String etiquetaRolCocina;
    @Column(nullable = false, length = 12)
    private String simboloMoneda;
    @Column(nullable = false)
    private Integer cantidadPuntosAtencion;

    @Lob
    private byte[] logoDatos;
    @Column(length = 24)
    private String logoMimeType;
    @Column(nullable = false)
    private Long logoVersion = 1L;
}
