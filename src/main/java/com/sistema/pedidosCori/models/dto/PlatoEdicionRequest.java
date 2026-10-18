package com.sistema.pedidosCori.models.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
public class PlatoEdicionRequest {
    @Setter private String nombre;
    @Setter private Double precio;
    @Setter private String categoria;
    @Setter private Boolean sinLimite;
    private String descripcion;
    private boolean descripcionPresente;
    private Integer stockDisponible;
    private boolean stockPresente;

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
        this.descripcionPresente = true;
    }

    public void setStockDisponible(Integer stockDisponible) {
        this.stockDisponible = stockDisponible;
        this.stockPresente = true;
    }
}
