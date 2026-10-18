package com.sistema.pedidosCori.models.dto;

public record UsuarioVista(Long id, String username, String nombreCompleto, String rol, Boolean activo,
        String ultimoAcceso, boolean esActual) {
}
