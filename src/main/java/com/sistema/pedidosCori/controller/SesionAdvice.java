package com.sistema.pedidosCori.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Expone el usuario logueado y las vistas que puede abrir, para la barra de sesión. */
@ControllerAdvice(assignableTypes = { PageController.class, adminController.class })
public class SesionAdvice {

    @ModelAttribute
    public void datosSesion(Authentication auth, Model model) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken) return;

        Set<String> roles = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        List<Map<String, String>> vistas = new ArrayList<>();
        if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_MESERO"))
            vistas.add(Map.of("url", "/mesero", "label", "🧑‍💼 Mesero"));
        if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_COCINA"))
            vistas.add(Map.of("url", "/cocina", "label", "👨‍🍳 Cocina"));
        if (roles.contains("ROLE_ADMIN"))
            vistas.add(Map.of("url", "/menu", "label", "👔 Dueño"));

        String rol = roles.stream().filter(r -> r.startsWith("ROLE_"))
            .map(r -> r.substring(5)).findFirst().orElse("");

        model.addAttribute("sesionUsuario", auth.getName());
        model.addAttribute("sesionRol", rol);
        model.addAttribute("sesionVistas", vistas);
    }
}
