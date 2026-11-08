package com.sistema.pedidosCori.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.sistema.pedidosCori.service.CuentaService;
import com.sistema.pedidosCori.service.CuentaService.Pendientes;
import com.sistema.pedidosCori.service.PoliticaPassword;
import com.sistema.pedidosCori.service.Terminos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class CuentaController {
    private final CuentaService cuentaService;

    @GetMapping("/terminos")
    public String terminos(Model model) {
        model.addAttribute("terminosVersion", Terminos.VERSION);
        model.addAttribute("terminosVigencia", Terminos.VIGENTE_DESDE);
        return "terminos";
    }

    @GetMapping("/cuenta")
    public String cuenta(Authentication auth, Model model) {
        prepararModelo(model, cuentaService.pendientes(auth.getName()));
        return "cuenta";
    }

    @PostMapping("/cuenta")
    public String guardar(Authentication auth, HttpServletRequest request, Model model, RedirectAttributes redirect,
            @RequestParam(required = false) String actual,
            @RequestParam(required = false) String nueva,
            @RequestParam(required = false) String confirmacion,
            @RequestParam(defaultValue = "false") boolean aceptaTerminos) {
        HttpSession sesion = request.getSession();
        Pendientes pendientes = cuentaService.pendientes(auth.getName());
        try {
            if (pendientes.alguno())
                cuentaService.completarPrimerIngreso(auth.getName(), actual, nueva, confirmacion, aceptaTerminos,
                    request.getRemoteAddr(), sesion.getId());
            else
                cuentaService.cambiarPassword(auth.getName(), actual, nueva, confirmacion, sesion.getId());
        } catch (IllegalArgumentException e) {
            prepararModelo(model, pendientes);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("aceptaTerminos", aceptaTerminos);
            return "cuenta";
        }
        sesion.removeAttribute(CuentaService.SESION_PENDIENTE);
        if (pendientes.alguno()) return "redirect:/";
        redirect.addFlashAttribute("ok", "Tu contraseña se cambió correctamente.");
        return "redirect:/cuenta";
    }

    private void prepararModelo(Model model, Pendientes pendientes) {
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("pedirPassword", pendientes.password() || !pendientes.alguno());
        model.addAttribute("terminosVersion", Terminos.VERSION);
        model.addAttribute("passwordMinimo", PoliticaPassword.MINIMO);
    }
}
