package com.sistema.pedidosCori.components;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.Rol;
import com.sistema.pedidosCori.models.entity.Usuario;
import com.sistema.pedidosCori.service.AuditoriaService;

import lombok.RequiredArgsConstructor;

@Component
@Order(100)
@RequiredArgsConstructor
public class RecuperacionAcceso implements CommandLineRunner {
    public static final String VARIABLE = "RECUPERAR_ACCESO";
    private static final String LETRAS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITOS = "23456789";

    private final IUsuarioDao usuarioDao;
    private final PasswordEncoder passwordEncoder;
    private final AuditoriaService auditoria;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.recuperar-acceso:}")
    private String usuarioSolicitado;

    private int codigoSalida;

    public static boolean solicitada() {
        String valor = System.getenv(VARIABLE);
        return valor != null && !valor.isBlank();
    }

    public int codigoSalida() { return codigoSalida; }

    @Override
    @Transactional
    public void run(String... args) {
        if (usuarioSolicitado == null || usuarioSolicitado.isBlank()) return;
        codigoSalida = 0;
        String username = usuarioSolicitado.trim().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarioDao.findByUsername(username).orElse(null);
        if (usuario != null && usuario.getRol() != Rol.ADMIN) {
            codigoSalida = 2;
            imprimir("No se recuperó el acceso", "El usuario '" + username + "' no es administrador.",
                "Las cuentas del personal se restablecen desde Administración → Usuarios.");
            return;
        }
        String temporal = generarTemporal();
        if (usuario == null) {
            usuario = Usuario.builder().username(username).nombreCompleto("Administrador").rol(Rol.ADMIN).build();
        }
        usuario.setPassword(passwordEncoder.encode(temporal));
        usuario.setActivo(true);
        usuario.setDebeCambiarPassword(true);
        usuario.setPasswordActualizadaEn(LocalDateTime.now());
        Usuario guardado = usuarioDao.save(usuario);
        auditoria.registrarComo("servidor", AccionAuditoria.ACCESO_RECUPERADO, "Usuario", guardado.getId(),
            () -> "Contraseña temporal emitida para " + username + " desde la consola del servidor");
        imprimir("Acceso recuperado", "Usuario: " + username, "Contraseña temporal: " + temporal,
            "Al ingresar, el sistema pedirá elegir una contraseña nueva.",
            "Esta contraseña no se guarda en ningún registro: anótala ahora.");
    }

    private String generarTemporal() {
        StringBuilder clave = new StringBuilder();
        for (int bloque = 0; bloque < 3; bloque++) {
            if (bloque > 0) clave.append('-');
            for (int i = 0; i < 3; i++) clave.append(LETRAS.charAt(random.nextInt(LETRAS.length())));
            clave.append(DIGITOS.charAt(random.nextInt(DIGITOS.length())));
        }
        return clave.toString();
    }

    private static void imprimir(String titulo, String... lineas) {
        String borde = "=".repeat(64);
        StringBuilder texto = new StringBuilder("\n").append(borde).append("\n  ").append(titulo).append("\n").append(borde).append('\n');
        for (String linea : lineas) texto.append("  ").append(linea).append('\n');
        System.out.println(texto.append(borde).append('\n'));
    }
}
