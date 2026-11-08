package com.sistema.pedidosCori.config;

import java.io.IOException;
import java.util.List;

import org.springframework.web.filter.OncePerRequestFilter;

import com.sistema.pedidosCori.service.CuentaService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

public class CuentaPendienteFilter extends OncePerRequestFilter {
    private static final List<String> PERMITIDAS = List.of("/cuenta", "/terminos", "/logout", "/login", "/error",
        "/favicon.ico", "/branding/logo");
    private static final List<String> PREFIJOS_PERMITIDOS = List.of("/css/", "/js/", "/img/");

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        HttpSession sesion = req.getSession(false);
        if (sesion == null || !Boolean.TRUE.equals(sesion.getAttribute(CuentaService.SESION_PENDIENTE))) {
            chain.doFilter(req, res);
            return;
        }
        String ruta = req.getRequestURI().substring(req.getContextPath().length());
        if (PERMITIDAS.contains(ruta) || PREFIJOS_PERMITIDOS.stream().anyMatch(ruta::startsWith)) {
            chain.doFilter(req, res);
            return;
        }
        if (ruta.startsWith("/api/") || ruta.startsWith("/ws")) {
            SecurityConfig.apiError(res, org.springframework.http.HttpStatus.FORBIDDEN,
                "Antes de continuar debes completar tu primer ingreso", "CUENTA_PENDIENTE");
            return;
        }
        res.sendRedirect(req.getContextPath() + "/cuenta");
    }
}
