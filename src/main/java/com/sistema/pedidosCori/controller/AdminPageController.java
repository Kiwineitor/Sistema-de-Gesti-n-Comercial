package com.sistema.pedidosCori.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminPageController {

    @GetMapping("/admin")
    public String inicio() { return "admin/inicio"; }

    @GetMapping("/admin/menu-hoy")
    public String menuHoy() { return "admin/menu-hoy"; }

    @GetMapping("/admin/productos")
    public String productos() { return "admin/productos"; }

    @GetMapping("/admin/categorias")
    public String categorias() { return "admin/categorias"; }

    @GetMapping("/admin/local")
    public String local() { return "admin/local"; }

    @GetMapping("/admin/usuarios")
    public String usuarios() { return "admin/usuarios"; }

    @GetMapping("/admin/reportes")
    public String reportes() { return "admin/reportes"; }

    @GetMapping("/admin/actividad")
    public String actividad() { return "admin/actividad"; }

    @GetMapping("/admin/apariencia")
    public String apariencia() { return "admin/apariencia"; }

    @GetMapping("/menu")
    public String menuAnterior() { return "redirect:/admin/menu-hoy"; }

    @GetMapping("/configuracion")
    public String configuracionAnterior() { return "redirect:/admin/apariencia"; }
}
