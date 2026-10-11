package com.sistema.pedidosCori.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PageController {

    // Cada rol aterriza en su vista principal
    @GetMapping("/")
    public String home(Authentication auth) {
        if (tieneRol(auth, "ROLE_ADMIN"))  return "redirect:/menu";
        if (tieneRol(auth, "ROLE_COCINA")) return "redirect:/cocina";
        return "redirect:/mesero";
    }

    @GetMapping("/login")
    public String login(Authentication auth) {
        if (auth != null && auth.isAuthenticated()) return "redirect:/";
        return "login";
    }

    @GetMapping("/mesero")
    public String mesero() { return "mesero"; }

    @GetMapping("/cocina")
    public String cocina() { return "cocina"; }

    private boolean tieneRol(Authentication auth, String rol) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> rol.equals(a.getAuthority()));
    }
}
