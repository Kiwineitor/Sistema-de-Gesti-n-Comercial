package com.sistema.pedidosCori.components;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dao.IPresentacionPlatoDao;
import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.entity.Modalidad;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.models.entity.PresentacionPlato;
import com.sistema.pedidosCori.models.entity.Rol;
import com.sistema.pedidosCori.models.entity.Usuario;
import com.sistema.pedidosCori.service.CategoriaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final IUsuarioDao           usuarioDao;
    private final IPlatoDao             platoDao;
    private final IPresentacionPlatoDao presentacionPlatoDao;
    private final PasswordEncoder       passwordEncoder;
    private final Environment           environment;
    private final CategoriaService      categoriaService;
    private final SecureRandom          random = new SecureRandom();

    @Value("${app.seed.admin-password:}")  private String adminPassword;
    @Value("${app.seed.mesero-password:}") private String meseroPassword;
    @Value("${app.seed.cocina-password:}") private String cocinaPassword;
    @Value("${app.seed.demo-data:false}") private boolean demoData;

    @Override
    @Transactional
    public void run(String... args) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            exigirPassword("SEED_ADMIN_PASSWORD", adminPassword);
            exigirPassword("SEED_MESERO_PASSWORD", meseroPassword);
            exigirPassword("SEED_COCINA_PASSWORD", cocinaPassword);
        }
        if (usuarioDao.count() == 0) {
            crearUsuario("admin",  adminPassword,  "Dueño",  Rol.ADMIN);
            crearUsuario("mesero", meseroPassword, "Mesero", Rol.MESERO);
            crearUsuario("cocina", cocinaPassword, "Cocina", Rol.COCINA);
        } else if (usuarioDao.countByRolAndActivoTrue(Rol.ADMIN) == 0) {
            asegurarAdministrador();
        }

        categoriaService.inicializar();
        if (demoData && platoDao.count() == 0) cargarMenuDemo();
    }

    public static final Set<String> CONTRASENAS_DEMO = Set.of("admin123", "mesero123", "cocina123");

    public static void exigirPassword(String nombre, String valor) {
        if (valor == null || valor.isBlank() || valor.startsWith("CAMBIAR_")) {
            throw new IllegalStateException("Falta configurar " + nombre + " para el perfil prod");
        }
        if (CONTRASENAS_DEMO.contains(valor.trim().toLowerCase())) {
            throw new IllegalStateException(nombre + " usa una contraseña de demostración; define una propia para el perfil prod");
        }
    }

    private void asegurarAdministrador() {
        usuarioDao.findByUsername("admin").ifPresentOrElse(admin -> {
            admin.setRol(Rol.ADMIN);
            admin.setActivo(true);
            usuarioDao.save(admin);
            log.warn("No había ningún administrador activo: se reactivó el usuario admin con su contraseña actual");
        }, () -> {
            crearUsuario("admin", adminPassword, "Dueño", Rol.ADMIN);
            log.warn("No había ningún administrador activo: se creó el usuario admin");
        });
    }

    private void crearUsuario(String username, String password, String nombre, Rol rol) {
        if (usuarioDao.existsByUsername(username)) return;
        if (password == null || password.isBlank() || password.startsWith("CAMBIAR_")) {
            if (!environment.acceptsProfiles(Profiles.of("dev"))) {
                throw new IllegalStateException("Falta SEED_" + username.toUpperCase() + "_PASSWORD para crear el usuario inicial " + username);
            }
            byte[] bytes = new byte[24];
            random.nextBytes(bytes);
            password = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            log.warn("Perfil dev: contraseña inicial generada para {}: {} (solo se muestra al crear el usuario)", username, password);
        }
        usuarioDao.save(Usuario.builder()
            .username(username)
            .password(passwordEncoder.encode(password))
            .nombreCompleto(nombre)
            .rol(rol)
            .build());
        log.info("Usuario inicial creado: {} ({})", username, rol);
    }

    private void cargarMenuDemo() {
        plato("Sopa de maní",        "SOPAS",     12.0, 20);
        plato("Chairo",              "SOPAS",     12.0, 20);
        plato("Silpancho",           "SEGUNDOS",  18.0, 20);
        plato("Pique macho",         "SEGUNDOS",  25.0, 15);
        plato("Milanesa de pollo",   "SEGUNDOS",  18.0, 20);
        plato("Ensalada mixta",      "ENSALADAS", 10.0, 15);
        plato("Gelatina",            "ANTOJITOS",  5.0, 15);
        plato("Huevo extra",         "EXTRAS",     3.0, 30);

        Plato refresco = plato("Refresco de mocochinchi", "REFRESCOS", 0.0, null);
        presentacion(refresco, "Vaso",    3.0,  Modalidad.MESA);
        presentacion(refresco, "Jarra",  12.0,  Modalidad.MESA);
        presentacion(refresco, "Botella 1L", 8.0, Modalidad.LLEVAR);

        log.info("Menú de demostración cargado");
    }

    private Plato plato(String nombre, String categoria, double precio, Integer stock) {
        Plato p = new Plato();
        p.setNombre(nombre);
        p.setCategoria(categoria);
        p.setPrecio(precio);
        p.setDisponible(true);
        p.setStockDisponible(stock);
        return platoDao.save(p);
    }

    private void presentacion(Plato plato, String nombre, double precio, Modalidad modalidad) {
        presentacionPlatoDao.save(PresentacionPlato.builder()
            .plato(plato)
            .nombre(nombre)
            .precio(precio)
            .modalidad(modalidad)
            .build());
    }
}
