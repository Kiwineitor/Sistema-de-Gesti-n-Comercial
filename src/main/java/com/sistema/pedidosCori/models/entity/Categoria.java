package com.sistema.pedidosCori.models.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "categoria")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Categoria {
    public static final int MAX_CODIGO = 40;
    public static final int MAX_NOMBRE = 40;
    public static final int MAX_ICONO = 16;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false, length = MAX_CODIGO)
    private String codigo;

    @Column(nullable = false, length = MAX_NOMBRE)
    private String nombre;

    @Column(nullable = false, length = MAX_ICONO)
    private String icono;

    @Column(nullable = false)
    private Integer orden;

    @Builder.Default
    @Column(nullable = false)
    private Boolean activa = true;

    @Builder.Default
    @Column(nullable = false)
    private Boolean llevaAcompanamiento = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean esAcompanamiento = false;

    private Integer cantidadSugerida;
}
