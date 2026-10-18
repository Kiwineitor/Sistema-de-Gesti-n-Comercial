package com.sistema.pedidosCori.models.dto;

public record CategoriaRequest(
    String nombre,
    String icono,
    Boolean activa,
    Boolean llevaAcompanamiento,
    Boolean esAcompanamiento,
    Integer cantidadSugerida
) {
}
