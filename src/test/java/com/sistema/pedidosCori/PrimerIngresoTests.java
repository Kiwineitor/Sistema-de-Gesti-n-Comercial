package com.sistema.pedidosCori;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.sistema.pedidosCori.components.RecuperacionAcceso;
import com.sistema.pedidosCori.models.dao.IRegistroAuditoriaDao;
import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.entity.Rol;
import com.sistema.pedidosCori.models.entity.Usuario;
import com.sistema.pedidosCori.service.PoliticaPassword;
import com.sistema.pedidosCori.service.Terminos;

@SpringBootTest
class PrimerIngresoTests {
    private static final String TEMPORAL = "Temporal-2026";
    private static final String PROPIA = "MiClavePropia-88";

    @Autowired WebApplicationContext context;
    @Autowired IUsuarioDao usuarioDao;
    @Autowired IRegistroAuditoriaDao auditoriaDao;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired RecuperacionAcceso recuperacion;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String crearUsuario(String rol) throws Exception {
        String username = "nuevo." + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"username\":\"" + username + "\",\"nombreCompleto\":\"Persona Nueva\",\"rol\":\"" + rol
            + "\",\"password\":\"" + TEMPORAL + "\"}";
        mvc().perform(post("/api/admin/usuarios").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.primerIngresoPendiente").value(true));
        return username;
    }

    private MockHttpSession ingresar(MockMvc client, String username, String password, String destino) throws Exception {
        return (MockHttpSession) client.perform(post("/login").with(csrf())
                .param("username", username).param("password", password))
            .andExpect(redirectedUrl(destino)).andReturn().getRequest().getSession(false);
    }

    @Test
    void newUserMustChangePasswordAndAcceptTermsBeforeUsingTheSystem() throws Exception {
        String username = crearUsuario("MESERO");
        MockMvc client = mvc();
        MockHttpSession sesion = ingresar(client, username, TEMPORAL, "/cuenta");

        client.perform(get("/mesero").session(sesion)).andExpect(redirectedUrl("/cuenta"));
        client.perform(get("/api/pedidos/activos").session(sesion))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.codigo").value("CUENTA_PENDIENTE"));
        client.perform(get("/cuenta").session(sesion)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Primer ingreso")))
            .andExpect(content().string(containsString("aceptaTerminos")));
        client.perform(get("/terminos").session(sesion)).andExpect(status().isOk());

        client.perform(post("/cuenta").session(sesion).with(csrf()).param("actual", TEMPORAL)
                .param("nueva", PROPIA).param("confirmacion", PROPIA))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Debes leer y aceptar")));
        client.perform(post("/cuenta").session(sesion).with(csrf()).param("actual", "incorrecta-1")
                .param("nueva", PROPIA).param("confirmacion", PROPIA).param("aceptaTerminos", "true"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("La contraseña actual no es correcta")));
        client.perform(post("/cuenta").session(sesion).with(csrf()).param("actual", TEMPORAL)
                .param("nueva", "soloLetrasLargas").param("confirmacion", "soloLetrasLargas").param("aceptaTerminos", "true"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("combinar letras y números")));
        client.perform(post("/cuenta").session(sesion).with(csrf()).param("actual", TEMPORAL)
                .param("nueva", PROPIA).param("confirmacion", PROPIA).param("aceptaTerminos", "true"))
            .andExpect(redirectedUrl("/"));

        client.perform(get("/api/pedidos/activos").session(sesion)).andExpect(status().isOk());
        Usuario u = usuarioDao.findByUsername(username).orElseThrow();
        assertFalse(u.getDebeCambiarPassword());
        assertEquals(Terminos.VERSION, u.getTerminosVersion());
        assertTrue(passwordEncoder.matches(PROPIA, u.getPassword()));
        assertTrue(auditoriaDao.findAll().stream().anyMatch(r -> "TERMINOS_ACEPTADOS".equals(r.getAccion()) && username.equals(r.getUsuario())));
        assertTrue(auditoriaDao.findAll().stream().anyMatch(r -> "PASSWORD_CAMBIADA".equals(r.getAccion()) && username.equals(r.getUsuario())));

        ingresar(mvc(), username, PROPIA, "/");
    }

    @Test
    void adminResetMakesPasswordTemporaryAgain() throws Exception {
        String username = crearUsuario("COCINA");
        MockMvc client = mvc();
        MockHttpSession sesion = ingresar(client, username, TEMPORAL, "/cuenta");
        client.perform(post("/cuenta").session(sesion).with(csrf()).param("actual", TEMPORAL)
                .param("nueva", PROPIA).param("confirmacion", PROPIA).param("aceptaTerminos", "true"))
            .andExpect(redirectedUrl("/"));
        String json = mvc().perform(get("/api/admin/usuarios").with(user("admin").roles("ADMIN")))
            .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.<java.util.List<Number>>read(json, "$[?(@.username == '" + username + "')].id").getFirst();
        mvc().perform(put("/api/admin/usuarios/" + id + "/password").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"Restablecida-31\"}"))
            .andExpect(status().isNoContent());
        assertTrue(usuarioDao.findByUsername(username).orElseThrow().getDebeCambiarPassword());
        ingresar(mvc(), username, "Restablecida-31", "/cuenta");
    }

    @Test
    void loggedUserCanChangeOwnPasswordAnytime() throws Exception {
        String username = crearUsuario("MESERO");
        MockMvc client = mvc();
        MockHttpSession sesion = ingresar(client, username, TEMPORAL, "/cuenta");
        client.perform(post("/cuenta").session(sesion).with(csrf()).param("actual", TEMPORAL)
                .param("nueva", PROPIA).param("confirmacion", PROPIA).param("aceptaTerminos", "true"))
            .andExpect(redirectedUrl("/"));
        client.perform(get("/cuenta").session(sesion)).andExpect(status().isOk())
            .andExpect(content().string(containsString("Cambiar mi contraseña")));
        client.perform(post("/cuenta").session(sesion).with(csrf()).param("actual", PROPIA)
                .param("nueva", "OtraPropia-99").param("confirmacion", "OtraDistinta-99"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("no coinciden")));
        client.perform(post("/cuenta").session(sesion).with(csrf()).param("actual", PROPIA)
                .param("nueva", "OtraPropia-99").param("confirmacion", "OtraPropia-99"))
            .andExpect(redirectedUrl("/cuenta"));
        assertTrue(passwordEncoder.matches("OtraPropia-99", usuarioDao.findByUsername(username).orElseThrow().getPassword()));
    }

    @Test
    void cuentaRequiresLoginAndTermsArePublic() throws Exception {
        mvc().perform(get("/cuenta").accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("/login")));
        mvc().perform(post("/cuenta").with(csrf()).param("nueva", PROPIA)).andExpect(status().is4xxClientError());
        mvc().perform(get("/terminos")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Términos y condiciones de uso")))
            .andExpect(content().string(containsString("no tiene acceso remoto")));
        mvc().perform(get("/login")).andExpect(content().string(containsString("href=\"/terminos\"")));
    }

    @Test
    void securityHeadersAreSent() throws Exception {
        mvc().perform(get("/login"))
            .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
            .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'self'")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().exists("Referrer-Policy"))
            .andExpect(header().exists("Permissions-Policy"));
    }

    @Test
    void passwordPolicy() {
        for (String debil : new String[]{null, "", "corta1", "sololetras", "12345678", "Admin123", "password1", " Espacio-12"}) {
            assertThrows(IllegalArgumentException.class, () -> PoliticaPassword.validar(debil, "mesero"));
        }
        assertThrows(IllegalArgumentException.class, () -> PoliticaPassword.validar("juan.perez-2026", "juan.perez"));
        assertThrows(IllegalArgumentException.class, () -> PoliticaPassword.validar("a1".repeat(37), "mesero"));
        assertDoesNotThrow(() -> PoliticaPassword.validar("Cafe-con-leche-7", "mesero"));
    }

    @Test
    void serverRecoveryIssuesTemporaryPasswordOnlyForAdmins() throws Exception {
        String username = "duenio." + UUID.randomUUID().toString().substring(0, 6);
        usuarioDao.save(Usuario.builder().username(username).nombreCompleto("Dueño").rol(Rol.ADMIN)
            .password(passwordEncoder.encode("Olvidada-2026")).activo(false).debeCambiarPassword(false).build());
        ReflectionTestUtils.setField(recuperacion, "usuarioSolicitado", username);
        try {
            recuperacion.run();
        } finally {
            ReflectionTestUtils.setField(recuperacion, "usuarioSolicitado", "");
        }
        Usuario recuperado = usuarioDao.findByUsername(username).orElseThrow();
        assertEquals(0, recuperacion.codigoSalida());
        assertTrue(recuperado.getActivo());
        assertTrue(recuperado.getDebeCambiarPassword());
        assertFalse(passwordEncoder.matches("Olvidada-2026", recuperado.getPassword()));
        assertTrue(auditoriaDao.findAll().stream().anyMatch(r -> "ACCESO_RECUPERADO".equals(r.getAccion())));

        String mesero = crearUsuario("MESERO");
        String antes = usuarioDao.findByUsername(mesero).orElseThrow().getPassword();
        ReflectionTestUtils.setField(recuperacion, "usuarioSolicitado", mesero);
        try {
            recuperacion.run();
            assertEquals(2, recuperacion.codigoSalida());
        } finally {
            ReflectionTestUtils.setField(recuperacion, "usuarioSolicitado", "");
            ReflectionTestUtils.setField(recuperacion, "codigoSalida", 0);
        }
        assertEquals(antes, usuarioDao.findByUsername(mesero).orElseThrow().getPassword());
    }
}
