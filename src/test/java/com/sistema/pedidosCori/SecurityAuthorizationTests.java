package com.sistema.pedidosCori;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dao.IPedidoDao;
import com.sistema.pedidosCori.models.entity.EstadoPedido;
import com.sistema.pedidosCori.models.entity.Plato;
import org.springframework.http.MediaType;

@SpringBootTest
class SecurityAuthorizationTests {
    @Autowired WebApplicationContext context;
    @Autowired IPlatoDao platoDao;
    @Autowired IPedidoDao pedidoDao;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanOpenAllViewsAndReports() throws Exception {
        mvc().perform(get("/menu")).andExpect(redirectedUrl("/admin/menu-hoy"));
        mvc().perform(get("/configuracion")).andExpect(redirectedUrl("/admin/apariencia"));
        mvc().perform(get("/")).andExpect(redirectedUrl("/admin"));
        mvc().perform(get("/cocina")).andExpect(status().isOk());
        mvc().perform(get("/mesero")).andExpect(status().isOk())
            .andExpect(content().string(containsString("id=\"s-reportes\"")))
            .andExpect(content().string(containsString("Menú, precios y configuración")));
        mvc().perform(get("/api/reportes/diarios")).andExpect(status().isOk());
        mvc().perform(get("/api/admin/inexistente")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "MESERO")
    void meseroCannotOpenAdminOrKitchen() throws Exception {
        mvc().perform(get("/mesero")).andExpect(status().isOk())
            .andExpect(content().string(not(containsString("id=\"s-reportes\""))))
            .andExpect(content().string(not(containsString("Menú, precios y configuración"))));
        mvc().perform(get("/menu")).andExpect(redirectedUrl("/?denegado"));
        mvc().perform(get("/cocina")).andExpect(redirectedUrl("/?denegado"));
        mvc().perform(get("/api/admin/inexistente")).andExpect(status().isForbidden());
        mvc().perform(get("/api/reportes/diarios")).andExpect(status().isForbidden());
        mvc().perform(post("/api/platos").with(csrf())).andExpect(status().isForbidden());
        mvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/pedidos/1/estado").param("estado", "EN_PREPARACION").with(csrf()))
            .andExpect(status().isForbidden());
        mvc().perform(get("/api/pedidos/cocina")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "COCINA")
    void cocinaCannotOpenAdminOrWaiter() throws Exception {
        mvc().perform(get("/cocina")).andExpect(status().isOk());
        mvc().perform(get("/mesero")).andExpect(redirectedUrl("/?denegado"));
        mvc().perform(get("/menu")).andExpect(redirectedUrl("/?denegado"));
        mvc().perform(get("/api/admin/inexistente")).andExpect(status().isForbidden());
        mvc().perform(get("/api/reportes/diarios")).andExpect(status().isForbidden());
        mvc().perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/pedidos/1/estado").param("estado", "ENTREGADO").with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminSessionMenuListsAllViewsAndMarksCurrent() throws Exception {
        mvc().perform(get("/mesero")).andExpect(status().isOk())
            .andExpect(content().string(containsString("class=\"sesion-menu\"")))
            .andExpect(content().string(containsString("href=\"/admin\"")))
            .andExpect(content().string(not(containsString("href=\"/configuracion\""))))
            .andExpect(content().string(containsString("Administrador")))
            .andExpect(content().string(not(containsString("sesion-barra"))))
            .andExpect(content().string(not(containsString("btn-logout"))));
        mvc().perform(get("/menu")).andExpect(redirectedUrl("/admin/menu-hoy"));
        mvc().perform(get("/configuracion")).andExpect(redirectedUrl("/admin/apariencia"));
    }

    @Test
    @WithMockUser(roles = "MESERO")
    void meseroSessionMenuHidesOtherViews() throws Exception {
        mvc().perform(get("/mesero")).andExpect(status().isOk())
            .andExpect(content().string(containsString("class=\"sesion-menu\"")))
            .andExpect(content().string(containsString("Cerrar sesión")))
            .andExpect(content().string(containsString("class=\"logo brand-home\" href=\"/\"")))
            .andExpect(content().string(not(containsString("href=\"/cocina\""))))
            .andExpect(content().string(not(containsString("href=\"/configuracion\""))));
    }

    @Test
    @WithMockUser(roles = "COCINA")
    void cocinaSessionMenuHidesOtherViews() throws Exception {
        mvc().perform(get("/cocina")).andExpect(status().isOk())
            .andExpect(content().string(containsString("class=\"sesion-menu\"")))
            .andExpect(content().string(not(containsString("href=\"/mesero\""))))
            .andExpect(content().string(not(containsString("href=\"/menu\""))));
    }

    @Test
    @WithMockUser(roles = "MESERO")
    void apiWritesRequireCsrf() throws Exception {
        mvc().perform(post("/api/pedidos")).andExpect(status().isForbidden());
    }

    @Test
    void publicLoginResourcesAreAvailable() throws Exception {
        mvc().perform(get("/login")).andExpect(status().isOk());
        mvc().perform(get("/img/logo.png")).andExpect(status().isOk());
        mvc().perform(get("/js/security.js")).andExpect(status().isOk());
    }

    @Test
    void waiterKitchenAndPaymentFlowWorksWithCsrf() throws Exception {
        Plato p = new Plato();
        p.setNombre("Producto de prueba");
        p.setCategoria("EXTRAS");
        p.setPrecio(10.0);
        p.setDisponible(true);
        p = platoDao.save(p);
        MockMvc client = mvc();
        String body = "{\"tipo\":\"MESA\",\"numeroMesa\":1,\"detalles\":[{\"platoId\":" + p.getId() + ",\"cantidad\":1}]}";
        String result = client.perform(post("/api/pedidos").with(user("mesero").roles("MESERO")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var matcher = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(result);
        org.junit.jupiter.api.Assertions.assertTrue(matcher.find(), "La respuesta debe contener el ID del pedido");
        long id = Long.parseLong(matcher.group(1));
        client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/pedidos/" + id + "/estado").param("estado", "EN_PREPARACION")
                .with(user("cocina").roles("COCINA")).with(csrf())).andExpect(status().isOk());
        client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/pedidos/" + id + "/estado").param("estado", "LISTO")
                .with(user("cocina").roles("COCINA")).with(csrf())).andExpect(status().isOk());
        client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/pedidos/" + id + "/estado").param("estado", "ENTREGADO")
                .with(user("mesero").roles("MESERO")).with(csrf())).andExpect(status().isOk());
        client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/pedidos/" + id + "/pagar").param("metodoPago", "EFECTIVO")
                .with(user("mesero").roles("MESERO")).with(csrf())).andExpect(status().isOk());
    }

    @Test
    void deliveredUnpaidOrderCanReceiveExtrasButPaidOrderCannot() throws Exception {
        Plato p = new Plato();
        p.setNombre("Extra de prueba"); p.setCategoria("EXTRAS"); p.setPrecio(8.0); p.setDisponible(true);
        p = platoDao.save(p);
        MockMvc client = mvc();
        String detalle = "[{\"platoId\":" + p.getId() + ",\"cantidad\":1}]";
        String body = "{\"tipo\":\"MESA\",\"numeroMesa\":2,\"detalles\":" + detalle + "}";
        String result = client.perform(post("/api/pedidos").with(user("mesero").roles("MESERO"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var matcher = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(result);
        org.junit.jupiter.api.Assertions.assertTrue(matcher.find());
        long id = Long.parseLong(matcher.group(1));
        client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/pedidos/" + id + "/estado").param("estado", "LISTO")
                .with(user("cocina").roles("COCINA")).with(csrf()))
            .andExpect(status().isConflict()).andExpect(content().string(containsString("Transición de pedido")));
        for (String estado : new String[]{"EN_PREPARACION", "LISTO"}) {
            client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .put("/api/pedidos/" + id + "/estado").param("estado", estado)
                    .with(user("cocina").roles("COCINA")).with(csrf()))
                .andExpect(status().isOk());
        }
        client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/pedidos/" + id + "/estado").param("estado", "ENTREGADO")
                .with(user("mesero").roles("MESERO")).with(csrf())).andExpect(status().isOk());
        client.perform(get("/api/pedidos/activos").with(user("mesero").roles("MESERO")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("\"id\":" + id)));
        client.perform(post("/api/pedidos/" + id + "/detalles").with(user("mesero").roles("MESERO"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(detalle))
            .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertEquals(EstadoPedido.PENDIENTE,
            pedidoDao.findById(id).orElseThrow().getEstado());
        client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/pedidos/" + id + "/pagar").param("metodoPago", "EFECTIVO")
                .with(user("mesero").roles("MESERO")).with(csrf())).andExpect(status().isOk());
        client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/pedidos/" + id + "/pagar").param("metodoPago", "QR")
                .with(user("mesero").roles("MESERO")).with(csrf()))
            .andExpect(status().isConflict()).andExpect(content().string(containsString("ya está pagado")));
        client.perform(post("/api/pedidos/" + id + "/detalles").with(user("mesero").roles("MESERO"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(detalle))
            .andExpect(status().isConflict())
            .andExpect(content().string(containsString("No se pueden agregar productos a un pedido pagado")));
        client.perform(get("/api/pedidos/cocina").with(user("cocina").roles("COCINA")))
            .andExpect(content().string(containsString("\"id\":" + id)));
        client.perform(get("/api/pedidos/activos").with(user("mesero").roles("MESERO")))
            .andExpect(content().string(containsString("\"id\":" + id)));
        for (String estado : new String[]{"EN_PREPARACION", "LISTO"}) {
            client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .put("/api/pedidos/" + id + "/estado").param("estado", estado)
                    .with(user("cocina").roles("COCINA")).with(csrf()))
                .andExpect(status().isOk());
        }
        client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/pedidos/" + id + "/estado").param("estado", "ENTREGADO")
                .with(user("mesero").roles("MESERO")).with(csrf())).andExpect(status().isOk());
        client.perform(get("/api/pedidos/activos").with(user("mesero").roles("MESERO")))
            .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertFalse(pedidoDao
            .findByEstadoNotOrPagadoFalseOrderByFechaCreacionAsc(EstadoPedido.ENTREGADO)
            .stream().anyMatch(item -> item.getId() == id));
    }

    @Test
    void deletingReferencedProductArchivesItWithoutRemovingOrderHistory() throws Exception {
        Plato p = new Plato();
        p.setNombre("Plato histórico"); p.setCategoria("EXTRAS"); p.setPrecio(9.0); p.setDisponible(true);
        p = platoDao.save(p);
        String body = "{\"tipo\":\"MESA\",\"numeroMesa\":3,\"detalles\":[{\"platoId\":"
            + p.getId() + ",\"cantidad\":1}]}";
        MockMvc client = mvc();
        String creado = client.perform(post("/api/pedidos").with(user("mesero").roles("MESERO"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var matcher = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(creado);
        org.junit.jupiter.api.Assertions.assertTrue(matcher.find());
        long pedidoId = Long.parseLong(matcher.group(1));
        client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/pedidos/" + pedidoId + "/pagar").param("metodoPago", "EFECTIVO")
                .with(user("mesero").roles("MESERO")).with(csrf())).andExpect(status().isOk());
        client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/platos/" + p.getId()).with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isNoContent());
        var archivado = platoDao.findById(p.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(archivado.getArchivado());
        org.junit.jupiter.api.Assertions.assertFalse(archivado.getDisponible());
        client.perform(get("/api/platos").with(user("mesero").roles("MESERO")))
            .andExpect(content().string(not(containsString("Plato histórico"))));
        client.perform(post("/api/menu-diario").with(user("admin").roles("ADMIN")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[{\"id\":" + p.getId() + ",\"cantidad\":5}]"))
            .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertFalse(platoDao.findById(p.getId()).orElseThrow().getDisponible());
        client.perform(get("/api/reportes/diarios").with(user("admin").roles("ADMIN")))
            .andExpect(content().string(containsString("Plato histórico")));
    }

    @Test
    void deletingUnusedProductRemovesItPhysicallyAndRequiresAdmin() throws Exception {
        Plato p = new Plato();
        p.setNombre("Plato sin pedidos"); p.setCategoria("EXTRAS"); p.setPrecio(7.0); p.setDisponible(true);
        p = platoDao.save(p);
        MockMvc client = mvc();
        client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/platos/" + p.getId()).with(user("mesero").roles("MESERO")).with(csrf()))
            .andExpect(status().isForbidden());
        client.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/platos/" + p.getId()).with(user("admin").roles("ADMIN")).with(csrf()))
            .andExpect(status().isNoContent());
        org.junit.jupiter.api.Assertions.assertFalse(platoDao.existsById(p.getId()));
    }

    @Test
    void loginIsBlockedAfterFiveFailuresForSameUserAndIp() throws Exception {
        MockMvc client = mvc();
        String username = "admin";
        for (int i = 0; i < 4; i++) {
            client.perform(post("/login").with(csrf()).with(req -> { req.setRemoteAddr("192.0.2.10"); return req; })
                    .param("username", username).param("password", "wrong"))
                .andExpect(redirectedUrl("/login?error"));
        }
        client.perform(post("/login").with(csrf()).with(req -> { req.setRemoteAddr("192.0.2.10"); return req; })
                .param("username", username).param("password", "wrong"))
            .andExpect(redirectedUrl("/login?blocked=15"));
        client.perform(post("/login").with(csrf()).with(req -> { req.setRemoteAddr("192.0.2.10"); return req; })
                .param("username", username).param("password", "TestAdminPassword-2026"))
            .andExpect(redirectedUrl("/login?blocked=15"));
    }

    @Test
    void successfulLoginResetsFailureCounter() throws Exception {
        MockMvc client = mvc();
        for (int i = 0; i < 2; i++) {
            client.perform(post("/login").with(csrf()).with(req -> { req.setRemoteAddr("192.0.2.11"); return req; })
                    .param("username", "admin").param("password", "wrong"))
                .andExpect(redirectedUrl("/login?error"));
        }
        client.perform(post("/login").with(csrf()).with(req -> { req.setRemoteAddr("192.0.2.11"); return req; })
                .param("username", "admin").param("password", "TestAdminPassword-2026"))
            .andExpect(redirectedUrl("/"));
        for (int i = 0; i < 4; i++) {
            client.perform(post("/login").with(csrf()).with(req -> { req.setRemoteAddr("192.0.2.11"); return req; })
                    .param("username", "admin").param("password", "wrong"))
                .andExpect(redirectedUrl("/login?error"));
        }
    }

}
