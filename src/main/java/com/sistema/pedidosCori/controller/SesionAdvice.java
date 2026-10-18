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

import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.dto.ConfiguracionNegocioVista;
import com.sistema.pedidosCori.models.entity.Usuario;
import com.sistema.pedidosCori.service.ConfiguracionNegocioService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@ControllerAdvice(assignableTypes = { PageController.class, AdminPageController.class })
@RequiredArgsConstructor
public class SesionAdvice {
    private final ConfiguracionNegocioService negocioService;
    private final IUsuarioDao usuarioDao;

    @ModelAttribute
    public void datosSesion(Authentication auth, HttpServletRequest request, Model model) {
        if (auth == null || auth instanceof AnonymousAuthenticationToken) return;

        Set<String> roles = auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        ConfiguracionNegocioVista negocio = negocioService.vista();

        List<Map<String, String>> vistas = new ArrayList<>();
        if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_MESERO"))
            vistas.add(Map.of("url", "/mesero", "label", negocio.getEtiquetaRolMesero()));
        if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_MESERO"))
            vistas.add(Map.of("url", "/catalogo", "label", "Catálogo"));
        if (roles.contains("ROLE_ADMIN") || roles.contains("ROLE_COCINA"))
            vistas.add(Map.of("url", "/cocina", "label", negocio.getEtiquetaRolCocina()));
        if (roles.contains("ROLE_ADMIN"))
            vistas.add(Map.of("url", "/admin", "label", "Administración"));

        String rol = roles.stream().filter(r -> r.startsWith("ROLE_"))
            .map(r -> r.substring(5)).findFirst().orElse("");
        String rolEtiqueta = switch (rol) {
            case "ADMIN" -> "Administrador";
            case "MESERO" -> negocio.getEtiquetaRolMesero();
            case "COCINA" -> negocio.getEtiquetaRolCocina();
            default -> rol;
        };

        model.addAttribute("sesionUsuario", auth.getName());
        model.addAttribute("sesionNombre", usuarioDao.findByUsername(auth.getName())
            .map(Usuario::getNombreCompleto).filter(n -> !n.isBlank()).orElse(auth.getName()));
        model.addAttribute("sesionRol", rol);
        model.addAttribute("sesionRolEtiqueta", rolEtiqueta);
        model.addAttribute("sesionVistas", vistas);
        model.addAttribute("sesionRuta", request.getRequestURI().substring(request.getContextPath().length()));
    }
}
