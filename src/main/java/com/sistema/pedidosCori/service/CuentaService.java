package com.sistema.pedidosCori.service;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.Objects;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.Usuario;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CuentaService {
    public static final String SESION_PENDIENTE = "cuenta.pendiente";

    private final IUsuarioDao usuarioDao;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;
    private final SessionRegistry sessionRegistry;

    public record Pendientes(boolean password, boolean terminos) {
        public boolean alguno() { return password || terminos; }
    }

    @Transactional(readOnly = true)
    public Pendientes pendientes(String username) {
        return usuarioDao.findByUsername(username).map(CuentaService::pendientes).orElse(new Pendientes(false, false));
    }

    static Pendientes pendientes(Usuario u) {
        return new Pendientes(Boolean.TRUE.equals(u.getDebeCambiarPassword()),
            !Terminos.VERSION.equals(u.getTerminosVersion()));
    }

    @Transactional
    public void completarPrimerIngreso(String username, String actual, String nueva, String confirmacion,
            boolean aceptaTerminos, String origen, String sesionActual) {
        Usuario u = buscar(username);
        Pendientes pendientes = pendientes(u);
        if (pendientes.terminos() && !aceptaTerminos)
            throw new IllegalArgumentException("Debes leer y aceptar los términos y condiciones para continuar");
        if (pendientes.password()) actualizarPassword(u, actual, nueva, confirmacion, sesionActual);
        if (pendientes.terminos()) {
            u.setTerminosVersion(Terminos.VERSION);
            u.setTerminosAceptadosEn(LocalDateTime.now());
            usuarioDao.save(u);
            auditoria.registrarComo(username, AccionAuditoria.TERMINOS_ACEPTADOS, "Usuario", u.getId(),
                () -> "Versión " + Terminos.VERSION + " · Desde " + Objects.toString(origen, "—"));
        }
    }

    @Transactional
    public void cambiarPassword(String username, String actual, String nueva, String confirmacion, String sesionActual) {
        actualizarPassword(buscar(username), actual, nueva, confirmacion, sesionActual);
    }

    private void actualizarPassword(Usuario u, String actual, String nueva, String confirmacion, String sesionActual) {
        if (actual == null || !passwordEncoder.matches(actual, u.getPassword()))
            throw new IllegalArgumentException("La contraseña actual no es correcta");
        if (!Objects.equals(nueva, confirmacion))
            throw new IllegalArgumentException("La nueva contraseña y su confirmación no coinciden");
        PoliticaPassword.validar(nueva, u.getUsername());
        if (passwordEncoder.matches(nueva, u.getPassword()))
            throw new IllegalArgumentException("La nueva contraseña debe ser distinta de la actual");
        u.setPassword(passwordEncoder.encode(nueva));
        u.setDebeCambiarPassword(false);
        u.setPasswordActualizadaEn(LocalDateTime.now());
        usuarioDao.save(u);
        auditoria.registrarComo(u.getUsername(), AccionAuditoria.PASSWORD_CAMBIADA, "Usuario", u.getId(),
            () -> Objects.toString(u.getNombreCompleto(), "") + " (" + u.getUsername() + ")");
        TrasConfirmar.ejecutar(() -> cerrarOtrasSesiones(u.getUsername(), sesionActual));
    }

    private void cerrarOtrasSesiones(String username, String sesionActual) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            String nombre = principal instanceof UserDetails d ? d.getUsername() : String.valueOf(principal);
            if (!username.equals(nombre)) continue;
            for (SessionInformation sesion : sessionRegistry.getAllSessions(principal, false))
                if (!sesion.getSessionId().equals(sesionActual)) sesion.expireNow();
        }
    }

    private Usuario buscar(String username) {
        return usuarioDao.findByUsername(username).orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
    }
}
