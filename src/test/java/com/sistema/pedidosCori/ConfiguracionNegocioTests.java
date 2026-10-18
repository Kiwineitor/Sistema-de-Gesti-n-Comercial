package com.sistema.pedidosCori;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.sistema.pedidosCori.models.dto.ConfiguracionNegocioRequest;
import com.sistema.pedidosCori.service.ConfiguracionNegocioService;
import com.sistema.pedidosCori.service.LogoValidator;
import com.sistema.pedidosCori.service.TemaNegocio;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringBootTest
class ConfiguracionNegocioTests {
    @Autowired WebApplicationContext context;
    @Autowired ConfiguracionNegocioService service;
    @Autowired LogoValidator validator;

    private MockMvc mvc() { return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build(); }

    @Test
    void colorDerivativesMeetContrastRequirements() {
        for (String color : new String[]{"#FFFFFF", "#F7DC43", "#24324A", "#000000"}) {
            var colors = TemaNegocio.variables(color, color, color);
            String textoClaro = colors.get("--brand-primary-text-light");
            String textoOscuro = colors.get("--brand-primary-text-dark");
            assertTrue(TemaNegocio.contraste(textoClaro, TemaNegocio.SUPERFICIE_CLARA) >= 4.5);
            assertTrue(TemaNegocio.contraste(textoClaro, TemaNegocio.SUPERFICIE_CLARA_ELEVADA) >= 4.5);
            assertTrue(TemaNegocio.contraste(textoClaro, colors.get("--brand-primary-soft-light")) >= 4.5);
            assertTrue(TemaNegocio.contraste(textoOscuro, TemaNegocio.SUPERFICIE_OSCURA) >= 4.5);
            assertTrue(TemaNegocio.contraste(textoOscuro, TemaNegocio.SUPERFICIE_OSCURA_ELEVADA) >= 4.5);
            assertTrue(TemaNegocio.contraste(textoOscuro, colors.get("--brand-primary-soft-dark")) >= 4.5);
            assertTrue(TemaNegocio.contraste(colors.get("--brand-primary-on"), color) >= 4.5);
            String solidoOscuro = colors.get("--brand-primary-solid-dark");
            assertTrue(TemaNegocio.contraste(solidoOscuro, TemaNegocio.SUPERFICIE_OSCURA) >= 3.0);
            assertTrue(TemaNegocio.contraste(solidoOscuro, TemaNegocio.SUPERFICIE_OSCURA_TARJETA) >= 3.0);
            assertTrue(TemaNegocio.contraste(colors.get("--brand-primary-solid-dark-on"), solidoOscuro) >= 4.5);
            assertTrue(TemaNegocio.contraste(colors.get("--brand-primary-solid-dark-on-hover"), colors.get("--brand-primary-solid-dark-hover")) >= 4.5);
            assertNotNull(colors.get("--brand-primary-hover"));
            assertNotNull(colors.get("--brand-primary-border-light"));
            assertNotNull(colors.get("--brand-primary-border-dark"));
        }
    }

    @Test
    void rejectsInvalidColorAndPointCount() {
        var normal = service.vista();
        ConfiguracionNegocioRequest invalidHex = new ConfiguracionNegocioRequest(normal.getNombreNegocio(), "",
            "red", normal.getColorSecundario(), normal.getColorAcento(), normal.getPuntoAtencionSingular(),
            normal.getPuntoAtencionPlural(), normal.getEtiquetaParaLlevar(), normal.getEtiquetaRolMesero(),
            normal.getEtiquetaRolCocina(), normal.getSimboloMoneda(), 1);
        assertThrows(IllegalArgumentException.class, () -> service.actualizar(invalidHex));
        ConfiguracionNegocioRequest invalidCount = new ConfiguracionNegocioRequest(normal.getNombreNegocio(), "",
            normal.getColorPrimario(), normal.getColorSecundario(), normal.getColorAcento(), normal.getPuntoAtencionSingular(),
            normal.getPuntoAtencionPlural(), normal.getEtiquetaParaLlevar(), normal.getEtiquetaRolMesero(),
            normal.getEtiquetaRolCocina(), normal.getSimboloMoneda(), 201);
        assertThrows(IllegalArgumentException.class, () -> service.actualizar(invalidCount));
    }

    @Test
    void logoChecksRealTypeDimensionsAndUnsafeSvg() {
        var fakePng = new MockMultipartFile("archivo", "logo.png", "image/png", "texto".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> validator.validar(fakePng));
        var unsafeSvg = new MockMultipartFile("archivo", "logo.svg", "image/svg+xml",
            "<!DOCTYPE svg [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><svg xmlns='http://www.w3.org/2000/svg'>&x;</svg>"
                .getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> validator.validar(unsafeSvg));
        var png = new byte[33];
        byte[] signature = {(byte)137,80,78,71,13,10,26,10};
        System.arraycopy(signature, 0, png, 0, signature.length);
        png[11] = 13; png[12] = 'I'; png[13] = 'H'; png[14] = 'D'; png[15] = 'R';
        png[18] = 0x20; png[19] = 0x00; png[22] = 0x10; png[23] = 0x00;
        var oversize = new MockMultipartFile("archivo", "logo.png", "image/png", png);
        assertThrows(IllegalArgumentException.class, () -> validator.validar(oversize));
    }

    @Test
    void adminOnlyApiAndPageAndLogoHeaders() throws Exception {
        mvc().perform(get("/configuracion").with(user("mesero").roles("MESERO")))
            .andExpect(redirectedUrl("/?denegado"));
        mvc().perform(get("/api/admin/configuracion").with(user("mesero").roles("MESERO")))
            .andExpect(status().isForbidden());
        mvc().perform(get("/configuracion").with(user("admin").roles("ADMIN")))
            .andExpect(redirectedUrl("/admin/apariencia"));
        mvc().perform(get("/api/admin/configuracion").with(user("admin").roles("ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nombreNegocio").exists());
        mvc().perform(put("/api/admin/configuracion").with(user("mesero").roles("MESERO"))
                .with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc().perform(put("/api/admin/configuracion").with(user("admin").roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mvc().perform(get("/branding/logo"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("inline")));
    }

    @Test
    void updatesBrandingAndInvalidatesLogoCache() throws Exception {
        MockMvc client = mvc();
        var original = service.vista();
        String json = "{" +
            "\"nombreNegocio\":\"Comercio de prueba\"," +
            "\"eslogan\":\"Atención para todos\"," +
            "\"colorPrimario\":\"#FFFFFF\"," +
            "\"colorSecundario\":\"#64748B\"," +
            "\"colorAcento\":\"#C56A3D\"," +
            "\"puntoAtencionSingular\":\"Puesto\"," +
            "\"puntoAtencionPlural\":\"Puestos\"," +
            "\"etiquetaParaLlevar\":\"Retiro\"," +
            "\"etiquetaRolMesero\":\"Atención\"," +
            "\"etiquetaRolCocina\":\"Preparación\"," +
            "\"simboloMoneda\":\"Bs.\"," +
            "\"cantidadPuntosAtencion\":1}";
        try {
            client.perform(put("/api/admin/configuracion").with(user("admin").roles("ADMIN"))
                    .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreNegocio").value("Comercio de prueba"));
            client.perform(get("/login")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Comercio de prueba")));
            byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 10 10'><rect width='10' height='10'/></svg>"
                .getBytes(StandardCharsets.UTF_8);
            var file = new MockMultipartFile("archivo", "logo.svg", "image/svg+xml", svg);
            client.perform(multipart("/api/admin/configuracion/logo").file(file)
                    .with(req -> { req.setMethod("PUT"); return req; })
                    .with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoMimeType").value("image/svg+xml"));
            String newUrl = service.vista().getLogoUrl();
            assertNotEquals(original.getLogoUrl(), newUrl);
            client.perform(get(newUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age")))
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'"));
        } finally {
            service.restablecer();
        }
    }

    @Test
    void zeroPointsHideLocalServiceAndBackendRejectsIt() throws Exception {
        var original = service.vista();
        var zero = new ConfiguracionNegocioRequest(original.getNombreNegocio(), original.getEslogan(),
            original.getColorPrimario(), original.getColorSecundario(), original.getColorAcento(),
            original.getPuntoAtencionSingular(), original.getPuntoAtencionPlural(), original.getEtiquetaParaLlevar(),
            original.getEtiquetaRolMesero(), original.getEtiquetaRolCocina(), original.getSimboloMoneda(), 0);
        try {
            service.actualizar(zero);
            mvc().perform(get("/mesero").with(user("mesero").roles("MESERO")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                    org.hamcrest.Matchers.containsString("onclick=\"App.pickTipo('mesa')\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                    org.hamcrest.Matchers.containsString("id=\"s-mesa\""))));
            mvc().perform(post("/api/pedidos").with(user("mesero").roles("MESERO"))
                    .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"tipo\":\"MESA\",\"numeroMesa\":1,\"detalles\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        } finally {
            service.actualizar(new ConfiguracionNegocioRequest(original.getNombreNegocio(), original.getEslogan(),
                original.getColorPrimario(), original.getColorSecundario(), original.getColorAcento(),
                original.getPuntoAtencionSingular(), original.getPuntoAtencionPlural(), original.getEtiquetaParaLlevar(),
                original.getEtiquetaRolMesero(), original.getEtiquetaRolCocina(), original.getSimboloMoneda(),
                original.getCantidadPuntosAtencion()));
        }
    }
}
