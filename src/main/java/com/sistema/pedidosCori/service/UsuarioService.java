package com.sistema.pedidosCori.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.dto.ConfiguracionNegocioVista;
import com.sistema.pedidosCori.models.dto.UsuarioRequest;
import com.sistema.pedidosCori.models.dto.UsuarioVista;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.Rol;
import com.sistema.pedidosCori.models.entity.Usuario;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {
    private static final Pattern USERNAME = Pattern.compile("[a-z0-9._-]+");

    private final IUsuarioDao usuarioDao;
    private final PasswordEncoder passwordEncoder;
    private final SessionRegistry sessionRegistry;
    private final AuditoriaService auditoria;
    private final ConfiguracionNegocioService configuracion;

    @Transactional(readOnly = true)
    public List<UsuarioVista> listar() {
        String actual = UsuarioActual.nombre().orElse(null);
        return usuarioDao.findAll().stream()
            .sorted(Comparator.comparing((Usuario u) -> u.getRol().ordinal())
                .thenComparing(u -> Objects.toString(u.getNombreCompleto(), u.getUsername()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Usuario::getUsername))
            .map(u -> vista(u, actual)).toList();
    }

    @Transactional
    public UsuarioVista crear(UsuarioRequest r) {
        if (r == null) throw new IllegalArgumentException("Datos del usuario requeridos");
        String username = username(r.username());
        String nombre = nombre(r.nombreCompleto());
        Rol rol = rol(r.rol());
        String password = password(r.password());
        if (usuarioDao.existsByUsername(username)) throw new IllegalStateException("Ese nombre de usuario ya existe");
        Usuario u = usuarioDao.save(Usuario.builder()
            .username(username).nombreCompleto(nombre).rol(rol)
            .password(passwordEncoder.encode(password)).activo(true).build());
        auditoria.registrar(AccionAuditoria.USUARIO_CREADO, "Usuario", u.getId(),
            () -> nombre + " (" + username + ") · Rol " + etiquetaRol(rol));
        return vista(u, UsuarioActual.nombre().orElse(null));
    }

    @Transactional
    public UsuarioVista actualizar(Long id, UsuarioRequest r) {
        if (r == null) throw new IllegalArgumentException("Datos del usuario requeridos");
        Usuario u = buscar(id);
        String nombre = nombre(r.nombreCompleto());
        Rol rol = rol(r.rol());
        boolean activo = r.activo() == null ? Boolean.TRUE.equals(u.getActivo()) : r.activo();
        boolean esActual = esActual(u);
        if (esActual && !activo) throw new IllegalStateException("No puedes desactivar tu propia cuenta");
        if (esActual && rol != Rol.ADMIN && u.getRol() == Rol.ADMIN)
            throw new IllegalStateException("No puedes quitarte el rol de administrador");
        boolean eraAdminActivo = u.getRol() == Rol.ADMIN && Boolean.TRUE.equals(u.getActivo());
        if (eraAdminActivo && (rol != Rol.ADMIN || !activo) && usuarioDao.countByRolAndActivoTrue(Rol.ADMIN) <= 1)
            throw new IllegalStateException("Debe quedar al menos un administrador activo");

        Rol rolAnterior = u.getRol();
        String nombreAnterior = u.getNombreCompleto();
        boolean activoAnterior = Boolean.TRUE.equals(u.getActivo());
        u.setNombreCompleto(nombre);
        u.setRol(rol);
        u.setActivo(activo);
        usuarioDao.save(u);

        List<String> cambios = new ArrayList<>();
        if (!Objects.equals(nombreAnterior, nombre)) cambios.add("Nombre: " + Objects.toString(nombreAnterior, "—") + " → " + nombre);
        if (rolAnterior != rol) cambios.add("Rol: " + etiquetaRol(rolAnterior) + " → " + etiquetaRol(rol));
        if (!cambios.isEmpty())
            auditoria.registrar(AccionAuditoria.USUARIO_EDITADO, "Usuario", u.getId(),
                () -> u.getUsername() + " · " + String.join(" · ", cambios));
        if (activoAnterior != activo)
            auditoria.registrar(activo ? AccionAuditoria.USUARIO_ACTIVADO : AccionAuditoria.USUARIO_DESACTIVADO,
                "Usuario", u.getId(), () -> Objects.toString(u.getNombreCompleto(), "") + " (" + u.getUsername() + ")");
        if (!esActual && (rolAnterior != rol || !activo)) expirarSesiones(u.getUsername());
        return vista(u, UsuarioActual.nombre().orElse(null));
    }

    @Transactional
    public void cambiarPassword(Long id, String nuevaPassword) {
        Usuario u = buscar(id);
        String password = password(nuevaPassword);
        u.setPassword(passwordEncoder.encode(password));
        usuarioDao.save(u);
        auditoria.registrar(AccionAuditoria.PASSWORD_RESTABLECIDA, "Usuario", u.getId(),
            () -> Objects.toString(u.getNombreCompleto(), "") + " (" + u.getUsername() + ")");
        if (!esActual(u)) expirarSesiones(u.getUsername());
    }

    @Transactional
    public void eliminar(Long id) {
        Usuario u = buscar(id);
        if (esActual(u)) throw new IllegalStateException("No puedes eliminar tu propia cuenta");
        if (u.getRol() == Rol.ADMIN && Boolean.TRUE.equals(u.getActivo())
                && usuarioDao.countByRolAndActivoTrue(Rol.ADMIN) <= 1)
            throw new IllegalStateException("Debe quedar al menos un administrador activo");
        usuarioDao.delete(u);
        auditoria.registrar(AccionAuditoria.USUARIO_ELIMINADO, "Usuario", u.getId(),
            () -> Objects.toString(u.getNombreCompleto(), "") + " (" + u.getUsername() + ") · Rol " + etiquetaRol(u.getRol()));
        expirarSesiones(u.getUsername());
    }

    public void expirarSesiones(String username) {
        TrasConfirmar.ejecutar(() -> expirarAhora(username));
    }

    int expirarAhora(String username) {
        int expiradas = 0;
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            String nombre = principal instanceof UserDetails d ? d.getUsername() : String.valueOf(principal);
            if (!username.equals(nombre)) continue;
            for (SessionInformation sesion : sessionRegistry.getAllSessions(principal, false)) {
                sesion.expireNow();
                expiradas++;
            }
        }
        return expiradas;
    }

    private Usuario buscar(Long id) {
        return usuarioDao.findById(id).orElseThrow(() -> new NoSuchElementException("Usuario no encontrado"));
    }

    private boolean esActual(Usuario u) {
        return UsuarioActual.nombre().map(u.getUsername()::equals).orElse(false);
    }

    private UsuarioVista vista(Usuario u, String actual) {
        return new UsuarioVista(u.getId(), u.getUsername(), u.getNombreCompleto(), u.getRol().name(),
            Boolean.TRUE.equals(u.getActivo()), u.getUltimoAcceso() == null ? null : u.getUltimoAcceso().toString(),
            u.getUsername().equals(actual));
    }

    private String etiquetaRol(Rol rol) {
        if (rol == null) return "—";
        ConfiguracionNegocioVista negocio = configuracion.vista();
        return switch (rol) {
            case ADMIN -> "Administrador";
            case MESERO -> negocio != null ? negocio.getEtiquetaRolMesero() : "Mesero";
            case COCINA -> negocio != null ? negocio.getEtiquetaRolCocina() : "Cocina";
        };
    }

    private static String username(String valor) {
        String u = valor == null ? "" : valor.trim().toLowerCase(Locale.ROOT);
        if (u.length() < 3 || u.length() > 40)
            throw new IllegalArgumentException("El nombre de usuario debe tener entre 3 y 40 caracteres");
        if (!USERNAME.matcher(u).matches())
            throw new IllegalArgumentException("El nombre de usuario solo admite letras minúsculas, números, punto, guion y guion bajo");
        return u;
    }

    private static String nombre(String valor) {
        String n = valor == null ? "" : valor.trim();
        if (n.isEmpty() || n.length() > 80)
            throw new IllegalArgumentException("El nombre completo es obligatorio y admite hasta 80 caracteres");
        return n;
    }

    private static Rol rol(String valor) {
        if (valor == null || valor.isBlank()) throw new IllegalArgumentException("Selecciona un rol");
        try {
            return Rol.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Rol inválido");
        }
    }

    private static String password(String valor) {
        if (valor == null || valor.length() < 8 || valor.length() > 72)
            throw new IllegalArgumentException("La contraseña debe tener entre 8 y 72 caracteres");
        return valor;
    }
}
