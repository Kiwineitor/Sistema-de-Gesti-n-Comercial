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
class CatalogoVistaTests {
    @Autowired WebApplicationContext context;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(username = "mesero", roles = "MESERO")
    void waiterOpensCatalogAndSeesItInSessionMenu() throws Exception {
        mvc().perform(get("/catalogo")).andExpect(status().isOk())
            .andExpect(content().string(containsString("catalogo.js")));
        mvc().perform(get("/mesero")).andExpect(status().isOk())
            .andExpect(content().string(containsString("href=\"/catalogo\"")))
            .andExpect(content().string(containsString("catalogo.js")));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminOpensCatalogAndHasNavLink() throws Exception {
        mvc().perform(get("/catalogo")).andExpect(status().isOk());
        mvc().perform(get("/admin")).andExpect(status().isOk())
            .andExpect(content().string(containsString("href=\"/catalogo\"")));
    }

    @Test
    @WithMockUser(username = "cocina", roles = "COCINA")
    void kitchenCannotOpenCatalog() throws Exception {
        mvc().perform(get("/catalogo")).andExpect(redirectedUrl("/?denegado"));
    }
}
