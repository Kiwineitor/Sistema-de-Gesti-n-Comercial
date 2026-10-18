package com.sistema.pedidosCori.models.dto;

public record CategoriaVista(
    Long id,
    String codigo,
    String nombre,
    String icono,
    Integer orden,
    Boolean activa,
    Boolean llevaAcompanamiento,
    Boolean esAcompanamiento,
    Integer cantidadSugerida,
    long productos
) {
}
