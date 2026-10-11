package com.sistema.pedidosCori.components;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final IUsuarioDao           usuarioDao;
    private final IPlatoDao             platoDao;
    private final IPresentacionPlatoDao presentacionPlatoDao;
    private final PasswordEncoder       passwordEncoder;

    @Value("${app.seed.admin-password:admin123}")  private String adminPassword;
    @Value("${app.seed.mesero-password:mesero123}") private String meseroPassword;
    @Value("${app.seed.cocina-password:cocina123}") private String cocinaPassword;
    @Value("${app.seed.demo-data:false}") private boolean demoData;

    @Override
    @Transactional
    public void run(String... args) {
        crearUsuario("admin",  adminPassword,  "Dueño",  Rol.ADMIN);
        crearUsuario("mesero", meseroPassword, "Mesero", Rol.MESERO);
        crearUsuario("cocina", cocinaPassword, "Cocina", Rol.COCINA);

        if (demoData && platoDao.count() == 0) cargarMenuDemo();
    }

    private void crearUsuario(String username, String password, String nombre, Rol rol) {
        if (usuarioDao.existsByUsername(username)) return;
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
