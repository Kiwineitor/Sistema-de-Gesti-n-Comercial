package com.sistema.pedidosCori.controller;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.sistema.pedidosCori.models.dto.ConfiguracionNegocioVista;
import com.sistema.pedidosCori.service.ConfiguracionNegocioService;

import lombok.RequiredArgsConstructor;

@ControllerAdvice(assignableTypes = {PageController.class, AdminPageController.class})
@RequiredArgsConstructor
public class NegocioAdvice {
    private final ConfiguracionNegocioService service;

    @ModelAttribute
    public void negocio(Model model) {
        ConfiguracionNegocioVista vista = service.vista();
        model.addAttribute("negocio", vista);
        StringBuilder css = new StringBuilder(":root{");
        vista.getTema().forEach((clave, valor) -> css.append(clave).append(':').append(valor).append(';'));
        model.addAttribute("temaCss", css.append('}').toString());
    }
}
