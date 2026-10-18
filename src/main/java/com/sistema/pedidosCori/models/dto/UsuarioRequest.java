package com.sistema.pedidosCori.models.dto;

public record UsuarioRequest(String username, String nombreCompleto, String rol, Boolean activo, String password) {
}
