package com.sistema.pedidosCori;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.sistema.pedidosCori.models.dao.IPedidoDao;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dao.IRegistroAuditoriaDao;
import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.models.entity.RegistroAuditoria;
import com.sistema.pedidosCori.models.entity.Rol;

@SpringBootTest
class AdminGestionTests {
    private static final String CLAVE = "ClaveSegura-2026";

    @Autowired WebApplicationContext context;
    @Autowired IUsuarioDao usuarioDao;
    @Autowired IPlatoDao platoDao;
    @Autowired IPedidoDao pedidoDao;
    @Autowired IRegistroAuditoriaDao auditoriaDao;
    @Autowired SessionRegistry sessionRegistry;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static RequestPostProcessor admin() { return user("admin").roles("ADMIN"); }

    private static String unico(String prefijo) {
        return prefijo + UUID.randomUUID().toString().substring(0, 8);
    }

    private long crearUsuario(String username, String rol) throws Exception {
        String body = "{\"username\":\"" + username + "\",\"nombreCompleto\":\"Persona " + username
            + "\",\"rol\":\"" + rol + "\",\"password\":\"" + CLAVE + "\"}";
        String json = mvc().perform(post("/api/admin/usuarios").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private void actualizarUsuario(RequestPostProcessor quien, long id, String nombre, String rol, boolean activo,
            int estado) throws Exception {
        mvc().perform(put("/api/admin/usuarios/" + id).with(quien).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreCompleto\":\"" + nombre + "\",\"rol\":\"" + rol + "\",\"activo\":" + activo + "}"))
            .andExpect(status().is(estado));
    }

    private boolean hayAuditoria(String accion, String entidadId, String usuario) {
        return auditoriaDao.findAll().stream().anyMatch(r -> accion.equals(r.getAccion())
            && (entidadId == null || entidadId.equals(r.getEntidadId()))
            && (usuario == null || usuario.equals(r.getUsuario())));
    }

    @Test
    void userApiHappyPath() throws Exception {
        String username = unico("ana.");
        String json = mvc().perform(post("/api/admin/usuarios").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"  " + username.toUpperCase() + " \",\"nombreCompleto\":\" Ana Pérez \",\"rol\":\"MESERO\",\"password\":\"" + CLAVE + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.username").value(username))
            .andExpect(jsonPath("$.nombreCompleto").value("Ana Pérez"))
            .andExpect(jsonPath("$.rol").value("MESERO"))
            .andExpect(jsonPath("$.activo").value(true))
            .andExpect(jsonPath("$.esActual").value(false))
            .andExpect(jsonPath("$.password").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();
        assertTrue(hayAuditoria("USUARIO_CREADO", String.valueOf(id), "admin"));

        mvc().perform(get("/api/admin/usuarios").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].username", hasItem(username)))
            .andExpect(jsonPath("$[0].rol").value("ADMIN"))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("{bcrypt}"))))
            .andExpect(jsonPath("$[?(@.username == 'admin')].esActual", hasItem(true)));

        mvc().perform(put("/api/admin/usuarios/" + id).with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombreCompleto\":\"Ana María\",\"rol\":\"COCINA\",\"activo\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nombreCompleto").value("Ana María"))
            .andExpect(jsonPath("$.rol").value("COCINA"));
        assertTrue(hayAuditoria("USUARIO_EDITADO", String.valueOf(id), "admin"));

        mvc().perform(put("/api/admin/usuarios/" + id + "/password").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"OtraClave-2026\"}"))
            .andExpect(status().isNoContent());
        assertTrue(hayAuditoria("PASSWORD_RESTABLECIDA", String.valueOf(id), "admin"));

        actualizarUsuario(admin(), id, "Ana María", "COCINA", false, 200);
        assertTrue(hayAuditoria("USUARIO_DESACTIVADO", String.valueOf(id), "admin"));
        actualizarUsuario(admin(), id, "Ana María", "COCINA", true, 200);
        assertTrue(hayAuditoria("USUARIO_ACTIVADO", String.valueOf(id), "admin"));

        mvc().perform(delete("/api/admin/usuarios/" + id).with(admin()).with(csrf()))
            .andExpect(status().isNoContent());
        assertFalse(usuarioDao.existsById(id));
        assertTrue(hayAuditoria("USUARIO_ELIMINADO", String.valueOf(id), "admin"));
        mvc().perform(delete("/api/admin/usuarios/" + id).with(admin()).with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    void userApiValidatesInputAndDuplicates() throws Exception {
        String valido = unico("dup.");
        crearUsuario(valido, "COCINA");
        String[] invalidos = {
            "{\"username\":\"ab\",\"nombreCompleto\":\"X\",\"rol\":\"MESERO\",\"password\":\"" + CLAVE + "\"}",
            "{\"username\":\"" + "a".repeat(41) + "\",\"nombreCompleto\":\"X\",\"rol\":\"MESERO\",\"password\":\"" + CLAVE + "\"}",
            "{\"username\":\"con espacio\",\"nombreCompleto\":\"X\",\"rol\":\"MESERO\",\"password\":\"" + CLAVE + "\"}",
            "{\"username\":\"ñandu.1\",\"nombreCompleto\":\"X\",\"rol\":\"MESERO\",\"password\":\"" + CLAVE + "\"}",
            "{\"username\":\"valido.1\",\"nombreCompleto\":\"   \",\"rol\":\"MESERO\",\"password\":\"" + CLAVE + "\"}",
            "{\"username\":\"valido.1\",\"nombreCompleto\":\"" + "n".repeat(81) + "\",\"rol\":\"MESERO\",\"password\":\"" + CLAVE + "\"}",
            "{\"username\":\"valido.1\",\"nombreCompleto\":\"X\",\"rol\":\"JEFE\",\"password\":\"" + CLAVE + "\"}",
            "{\"username\":\"valido.1\",\"nombreCompleto\":\"X\",\"rol\":\"MESERO\",\"password\":\"corta\"}",
            "{\"username\":\"valido.1\",\"nombreCompleto\":\"X\",\"rol\":\"MESERO\",\"password\":\"" + "p".repeat(73) + "\"}"
        };
        for (String body : invalidos) {
            mvc().perform(post("/api/admin/usuarios").with(admin()).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        }
        assertFalse(usuarioDao.existsByUsername("valido.1"));
        mvc().perform(post("/api/admin/usuarios").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + valido.toUpperCase() + "\",\"nombreCompleto\":\"Otro\",\"rol\":\"MESERO\",\"password\":\"" + CLAVE + "\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.mensaje").value("Ese nombre de usuario ya existe"));
        long id = usuarioDao.findByUsername(valido).orElseThrow().getId();
        mvc().perform(put("/api/admin/usuarios/" + id + "/password").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"1234567\"}"))
            .andExpect(status().isBadRequest());
        mvc().perform(put("/api/admin/usuarios/999999").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombreCompleto\":\"X\",\"rol\":\"MESERO\",\"activo\":true}"))
            .andExpect(status().isNotFound());
    }

    @Test
    void adminCannotDeactivateDeleteOrDemoteThemself() throws Exception {
        long adminId = usuarioDao.findByUsername("admin").orElseThrow().getId();
        actualizarUsuario(admin(), adminId, "Dueño", "ADMIN", false, 409);
        actualizarUsuario(admin(), adminId, "Dueño", "MESERO", true, 409);
        mvc().perform(delete("/api/admin/usuarios/" + adminId).with(admin()).with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.mensaje").value("No puedes eliminar tu propia cuenta"));
        var admin = usuarioDao.findByUsername("admin").orElseThrow();
        assertEquals(Rol.ADMIN, admin.getRol());
        assertTrue(admin.getActivo());
    }

    @Test
    void lastActiveAdminCannotBeRemoved() throws Exception {
        long adminId = usuarioDao.findByUsername("admin").orElseThrow().getId();
        RequestPostProcessor otro = user("externo").roles("ADMIN");
        assertEquals(1, usuarioDao.countByRolAndActivoTrue(Rol.ADMIN));
        actualizarUsuario(otro, adminId, "Dueño", "ADMIN", false, 409);
        actualizarUsuario(otro, adminId, "Dueño", "COCINA", true, 409);
        mvc().perform(delete("/api/admin/usuarios/" + adminId).with(otro).with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.mensaje").value("Debe quedar al menos un administrador activo"));

        String segundo = unico("jefa.");
        long segundoId = crearUsuario(segundo, "ADMIN");
        try {
            actualizarUsuario(otro, segundoId, "Jefa", "MESERO", true, 200);
            mvc().perform(delete("/api/admin/usuarios/" + segundoId).with(otro).with(csrf()))
                .andExpect(status().isNoContent());
        } finally {
            if (usuarioDao.existsById(segundoId)) usuarioDao.deleteById(segundoId);
        }
        assertTrue(usuarioDao.findById(adminId).orElseThrow().getActivo());
    }

    @Test
    void nonAdminsCannotUseAdminApisOrPages() throws Exception {
        for (RequestPostProcessor quien : List.of(user("mesero").roles("MESERO"), user("cocina").roles("COCINA"))) {
            mvc().perform(get("/api/admin/usuarios").with(quien)).andExpect(status().isForbidden());
            mvc().perform(post("/api/admin/usuarios").with(quien).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
            mvc().perform(get("/api/admin/auditoria").with(quien)).andExpect(status().isForbidden());
            mvc().perform(get("/api/admin/resumen-hoy").with(quien)).andExpect(status().isForbidden());
            for (String ruta : List.of("/admin", "/admin/menu-hoy", "/admin/local", "/admin/usuarios",
                    "/admin/actividad", "/admin/apariencia", "/menu", "/configuracion")) {
                mvc().perform(get(ruta).with(quien)).andExpect(redirectedUrl("/?denegado"));
            }
        }
        mvc().perform(get("/api/admin/usuarios")).andExpect(status().isUnauthorized());
    }

    @Test
    void legacyAdminRoutesRedirectToNewPages() throws Exception {
        mvc().perform(get("/menu").with(admin())).andExpect(redirectedUrl("/admin/menu-hoy"));
        mvc().perform(get("/configuracion").with(admin())).andExpect(redirectedUrl("/admin/apariencia"));
        mvc().perform(get("/").with(admin())).andExpect(redirectedUrl("/admin"));
    }

    @Test
    void disabledUserCannotLogIn() throws Exception {
        String username = unico("inactivo.");
        long id = crearUsuario(username, "MESERO");
        actualizarUsuario(admin(), id, "Inactivo", "MESERO", false, 200);
        mvc().perform(post("/login").with(csrf()).param("username", username).param("password", CLAVE))
            .andExpect(redirectedUrl("/login?error"));
        assertTrue(auditoriaDao.findAll().stream().anyMatch(r -> "LOGIN_FALLIDO".equals(r.getAccion())
            && username.equals(r.getUsuario()) && r.getDetalle().contains("Cuenta desactivada")));
        actualizarUsuario(admin(), id, "Inactivo", "MESERO", true, 200);
        mvc().perform(post("/login").with(csrf()).param("username", username).param("password", CLAVE))
            .andExpect(redirectedUrl("/"));
    }

    @Test
    void loginRecordsLastAccessAndAuditAndDeactivationExpiresSessions() throws Exception {
        String username = unico("sesion.");
        long id = crearUsuario(username, "MESERO");
        MockMvc client = mvc();
        MockHttpSession sesion = (MockHttpSession) client.perform(post("/login").with(csrf())
                .param("username", username).param("password", CLAVE))
            .andExpect(redirectedUrl("/")).andReturn().getRequest().getSession(false);
        assertNotNull(sesion);
        assertNotNull(usuarioDao.findById(id).orElseThrow().getUltimoAcceso());
        assertTrue(hayAuditoria("LOGIN", null, username));
        client.perform(get("/api/pedidos/activos").session(sesion)).andExpect(status().isOk());
        assertTrue(sessionRegistry.getAllPrincipals().stream()
            .anyMatch(p -> p instanceof org.springframework.security.core.userdetails.UserDetails d
                && username.equals(d.getUsername())));

        actualizarUsuario(admin(), id, "Sesión", "MESERO", false, 200);

        client.perform(get("/api/pedidos/activos").session(sesion))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.codigo").value("SESION_EXPIRADA"));
        mvc().perform(get("/api/admin/usuarios").with(admin()))
            .andExpect(jsonPath("$[?(@.username == '" + username + "')].ultimoAcceso", everyItem(org.hamcrest.Matchers.notNullValue())));
    }

    @Test
    void roleChangeAlsoExpiresSessionsAndPagesRedirect() throws Exception {
        String username = unico("rol.");
        long id = crearUsuario(username, "MESERO");
        MockMvc client = mvc();
        MockHttpSession sesion = (MockHttpSession) client.perform(post("/login").with(csrf())
                .param("username", username).param("password", CLAVE))
            .andExpect(redirectedUrl("/")).andReturn().getRequest().getSession(false);
        actualizarUsuario(admin(), id, "Rol", "COCINA", true, 200);
        client.perform(get("/mesero").session(sesion)).andExpect(redirectedUrl("/login?expirado"));
    }

    @Test
    void ordersRecordWhoRegisteredAndCollectedAndAuditTrail() throws Exception {
        Plato p = new Plato();
        p.setNombre(unico("Plato auditado ")); p.setCategoria("EXTRAS"); p.setPrecio(18.0); p.setDisponible(true);
        p = platoDao.save(p);
        MockMvc client = mvc();
        String json = client.perform(post("/api/pedidos").with(user("mesero.audit").roles("MESERO")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tipo\":\"MESA\",\"numeroMesa\":4,\"detalles\":[{\"platoId\":" + p.getId() + ",\"cantidad\":3}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registradoPor").value("mesero.audit"))
            .andExpect(jsonPath("$.cobradoPor").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();
        String pedidoId = String.valueOf(id);
        assertTrue(auditoriaDao.findAll().stream().anyMatch(r -> "PEDIDO_CREADO".equals(r.getAccion())
            && pedidoId.equals(r.getEntidadId()) && "mesero.audit".equals(r.getUsuario())
            && r.getDetalle().equals("Mesa 4 · 3 productos · Bs. 54.00")));

        client.perform(put("/api/pedidos/" + id + "/estado").param("estado", "EN_PREPARACION")
                .with(user("cocina.audit").roles("COCINA")).with(csrf()))
            .andExpect(status().isOk());
        assertTrue(auditoriaDao.findAll().stream().anyMatch(r -> "PEDIDO_ESTADO".equals(r.getAccion())
            && pedidoId.equals(r.getEntidadId()) && "cocina.audit".equals(r.getUsuario())
            && r.getDetalle().contains("PENDIENTE → EN_PREPARACION")));

        client.perform(post("/api/pedidos/" + id + "/detalles").with(user("mesero.audit").roles("MESERO")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("[{\"platoId\":" + p.getId() + ",\"cantidad\":1}]"))
            .andExpect(status().isOk());
        assertTrue(hayAuditoria("PEDIDO_ITEMS_AGREGADOS", pedidoId, "mesero.audit"));

        client.perform(put("/api/pedidos/" + id + "/pagar").param("metodoPago", "QR")
                .with(user("cajero.audit").roles("MESERO")).with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registradoPor").value("mesero.audit"))
            .andExpect(jsonPath("$.cobradoPor").value("cajero.audit"));
        var pedido = pedidoDao.findById(id).orElseThrow();
        assertEquals("mesero.audit", pedido.getRegistradoPor());
        assertEquals("cajero.audit", pedido.getCobradoPor());
        assertTrue(auditoriaDao.findAll().stream().anyMatch(r -> "PEDIDO_COBRADO".equals(r.getAccion())
            && pedidoId.equals(r.getEntidadId()) && "cajero.audit".equals(r.getUsuario())
            && r.getDetalle().contains("QR") && r.getDetalle().contains("Bs. 72.00")));
    }

    @Test
    void auditApiFiltersPaginatesAndIsAdminOnly() throws Exception {
        String quien = unico("filtro.");
        long id = crearUsuario(quien, "MESERO");
        actualizarUsuario(admin(), id, "Filtro", "MESERO", false, 200);
        String hoy = java.time.LocalDate.now().toString();

        mvc().perform(get("/api/admin/auditoria").with(admin())
                .param("usuario", "admin").param("accion", "USUARIO_DESACTIVADO")
                .param("desde", hoy).param("hasta", hoy).param("tamano", "500"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tamano").value(100))
            .andExpect(jsonPath("$.pagina").value(0))
            .andExpect(jsonPath("$.contenido[*].accion", everyItem(is("USUARIO_DESACTIVADO"))))
            .andExpect(jsonPath("$.contenido[*].usuario", everyItem(is("admin"))))
            .andExpect(jsonPath("$.contenido[0].accionEtiqueta").value("Usuario desactivado"))
            .andExpect(jsonPath("$.contenido[0].entidadId").value(String.valueOf(id)))
            .andExpect(jsonPath("$.contenido[0].fecha", containsString(hoy)));

        String paginaJson = mvc().perform(get("/api/admin/auditoria").with(admin()).param("tamano", "1"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int total = ((Number) JsonPath.read(paginaJson, "$.total")).intValue();
        List<?> contenido = JsonPath.read(paginaJson, "$.contenido");
        assertEquals(1, contenido.size());
        assertEquals(total, ((Number) JsonPath.read(paginaJson, "$.paginas")).intValue());

        mvc().perform(get("/api/admin/auditoria").with(admin()).param("desde", "2000-01-01").param("hasta", "2000-01-02"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc().perform(get("/api/admin/auditoria").with(admin()).param("desde", "2026-02-02").param("hasta", "2026-02-01"))
            .andExpect(status().isBadRequest());
        mvc().perform(get("/api/admin/auditoria").with(admin()).param("desde", "ayer"))
            .andExpect(status().isBadRequest());
        mvc().perform(get("/api/admin/auditoria").with(admin()).param("accion", "INVENTADA"))
            .andExpect(status().isBadRequest());

        mvc().perform(get("/api/admin/auditoria/acciones").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.codigo == 'LOGIN')].etiqueta", hasItem("Inicio de sesión")))
            .andExpect(jsonPath("$[?(@.codigo == 'PEDIDO_COBRADO')].etiqueta", hasItem("Pedido cobrado")));
        mvc().perform(get("/api/admin/auditoria/usuarios").with(admin()))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasItem("admin")));
        mvc().perform(get("/api/admin/auditoria/acciones").with(user("mesero").roles("MESERO")))
            .andExpect(status().isForbidden());
    }

    @Test
    void auditTruncatesLongValues() {
        assertEquals(RegistroAuditoria.MAX_DETALLE, 500);
        String recortado = com.sistema.pedidosCori.service.AuditoriaService.recortar("x".repeat(600), 500);
        assertEquals(500, recortado.length());
    }

    @Test
    void todaySummaryReflectsNewOrders() throws Exception {
        String antesJson = mvc().perform(get("/api/admin/resumen-hoy").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fecha").value(java.time.LocalDate.now().toString()))
            .andReturn().getResponse().getContentAsString();
        double ventasAntes = ((Number) JsonPath.read(antesJson, "$.ventasCobradas")).doubleValue();
        int pedidosAntes = ((Number) JsonPath.read(antesJson, "$.pedidosHoy")).intValue();
        int activosAntes = ((Number) JsonPath.read(antesJson, "$.pedidosActivos")).intValue();

        Plato p = new Plato();
        String nombre = unico("Resumen ");
        p.setNombre(nombre); p.setCategoria("EXTRAS"); p.setPrecio(2.5); p.setDisponible(true);
        p = platoDao.save(p);
        String vendedor = unico("vendedor.");
        MockMvc client = mvc();
        long[] ids = new long[2];
        for (int i = 0; i < 2; i++) {
            String json = client.perform(post("/api/pedidos").with(user(vendedor).roles("MESERO")).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tipo\":\"LLEVAR\",\"nombreCliente\":\"Cliente\",\"detalles\":[{\"platoId\":" + p.getId() + ",\"cantidad\":90}]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            ids[i] = ((Number) JsonPath.read(json, "$.id")).longValue();
        }
        client.perform(put("/api/pedidos/" + ids[0] + "/pagar").param("metodoPago", "EFECTIVO")
                .with(user(vendedor).roles("MESERO")).with(csrf()))
            .andExpect(status().isOk());

        String json = mvc().perform(get("/api/admin/resumen-hoy").with(admin()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(ventasAntes + 225.0, ((Number) JsonPath.read(json, "$.ventasCobradas")).doubleValue(), 0.001);
        assertEquals(pedidosAntes + 2, ((Number) JsonPath.read(json, "$.pedidosHoy")).intValue());
        assertEquals(activosAntes + 2, ((Number) JsonPath.read(json, "$.pedidosActivos")).intValue());
        assertTrue(((Number) JsonPath.read(json, "$.ticketPromedio")).doubleValue() > 0);
        assertTrue(((Number) JsonPath.read(json, "$.productosActivosHoy")).longValue() >= 1);
        List<Map<String, Object>> top = JsonPath.read(json, "$.topProductos");
        assertTrue(top.size() <= 5);
        assertEquals(nombre, top.getFirst().get("nombre"));
        assertEquals(180, ((Number) top.getFirst().get("cantidad")).intValue());
        List<Map<String, Object>> empleados = JsonPath.read(json, "$.porEmpleado[?(@.usuario == '" + vendedor + "')]");
        assertEquals(1, empleados.size());
        assertEquals(2, ((Number) empleados.getFirst().get("pedidos")).intValue());
        assertEquals(225.0, ((Number) empleados.getFirst().get("ventas")).doubleValue(), 0.001);
    }
}
