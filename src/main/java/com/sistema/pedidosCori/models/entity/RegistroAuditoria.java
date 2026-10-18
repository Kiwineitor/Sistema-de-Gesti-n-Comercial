package com.sistema.pedidosCori.models.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "registro_auditoria", indexes = {
    @Index(name = "idx_registro_auditoria_fecha", columnList = "fecha"),
    @Index(name = "idx_registro_auditoria_accion", columnList = "accion"),
    @Index(name = "idx_registro_auditoria_usuario", columnList = "usuario")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistroAuditoria {
    public static final int MAX_USUARIO = 60;
    public static final int MAX_ACCION = 40;
    public static final int MAX_ENTIDAD = 40;
    public static final int MAX_ENTIDAD_ID = 40;
    public static final int MAX_DETALLE = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(length = MAX_USUARIO)
    private String usuario;

    @Column(nullable = false, length = MAX_ACCION)
    private String accion;

    @Column(length = MAX_ENTIDAD)
    private String entidad;

    @Column(name = "entidad_id", length = MAX_ENTIDAD_ID)
    private String entidadId;

    @Column(length = MAX_DETALLE)
    private String detalle;
}
