package com.sistema.pedidosCori.service;

import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class UsuarioActual {
    private UsuarioActual() { }

    public static Optional<String> nombre() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) return Optional.empty();
        String nombre = auth.getName();
        return nombre == null || nombre.isBlank() ? Optional.empty() : Optional.of(nombre);
    }
}
