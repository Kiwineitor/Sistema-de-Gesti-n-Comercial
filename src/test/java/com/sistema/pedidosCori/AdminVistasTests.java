package com.sistema.pedidosCori;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class AdminVistasTests {
    private static final String[][] SECCIONES = {
        {"/admin", "Inicio"},
        {"/admin/menu-hoy", "Menú de hoy"},
        {"/admin/productos", "Productos"},
        {"/admin/categorias", "Categorías"},
        {"/admin/local", "Mi local"},
        {"/admin/usuarios", "Usuarios"},
        {"/admin/actividad", "Actividad"},
        {"/admin/reportes", "Reportes"},
        {"/admin/apariencia", "Apariencia"}
    };

    @Autowired WebApplicationContext context;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void everyAdminSectionRendersWithNavigationAndCurrentItem() throws Exception {
        for (String[] seccion : SECCIONES) {
            mvc().perform(get(seccion[0])).andExpect(status().isOk())
                .andExpect(content().string(containsString("admin-nav")))
                .andExpect(content().string(containsString("aria-current=\"page\"")))
                .andExpect(content().string(containsString(seccion[1])))
                .andExpect(content().string(containsString("class=\"sesion-menu\"")));
        }
    }

    @Test
    @WithMockUser(username = "mesero", roles = "MESERO")
    void waiterCannotOpenAdminSections() throws Exception {
        for (String[] seccion : SECCIONES)
            mvc().perform(get(seccion[0])).andExpect(redirectedUrl("/?denegado"));
    }

    @Test
    @WithMockUser(username = "cocina", roles = "COCINA")
    void kitchenCannotOpenAdminSections() throws Exception {
        for (String[] seccion : SECCIONES)
            mvc().perform(get(seccion[0])).andExpect(redirectedUrl("/?denegado"));
    }
}
