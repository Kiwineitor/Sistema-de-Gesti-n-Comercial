package com.sistema.pedidosCori;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.JsonPath;
import com.sistema.pedidosCori.models.dao.ICategoriaDao;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dao.IPlatoImagenDao;
import com.sistema.pedidosCori.models.dao.IRegistroAuditoriaDao;
import com.sistema.pedidosCori.models.entity.Categoria;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.service.CategoriaService;

@SpringBootTest
class CategoriasYFotosTests {
    @Autowired WebApplicationContext context;
    @Autowired ICategoriaDao categoriaDao;
    @Autowired IPlatoDao platoDao;
    @Autowired IPlatoImagenDao imagenDao;
    @Autowired IRegistroAuditoriaDao auditoriaDao;
    @Autowired CategoriaService categoriaService;
    @Autowired JdbcTemplate jdbc;

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static RequestPostProcessor admin() { return user("admin").roles("ADMIN"); }
    private static RequestPostProcessor mesero() { return user("mesero").roles("MESERO"); }
    private static RequestPostProcessor cocina() { return user("cocina").roles("COCINA"); }

    private static String sufijo() { return UUID.randomUUID().toString().substring(0, 6); }

    private static String cuerpo(String nombre, String icono, boolean lleva, boolean es, Integer sugerida) {
        return "{\"nombre\":" + (nombre == null ? "null" : "\"" + nombre + "\"")
            + ",\"icono\":" + (icono == null ? "null" : "\"" + icono + "\"")
            + ",\"activa\":true,\"llevaAcompanamiento\":" + lleva + ",\"esAcompanamiento\":" + es
            + ",\"cantidadSugerida\":" + sugerida + "}";
    }

    private ResultActions crearCategoria(String nombre, String icono, boolean lleva, boolean es, Integer sugerida)
            throws Exception {
        return mvc().perform(post("/api/admin/categorias").with(admin()).with(csrf())
            .contentType(MediaType.APPLICATION_JSON).content(cuerpo(nombre, icono, lleva, es, sugerida)));
    }

    private long idDe(ResultActions acciones) throws Exception {
        return ((Number) JsonPath.read(acciones.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private Plato plato(String nombre, String categoria) {
        Plato p = new Plato();
        p.setNombre(nombre); p.setCategoria(categoria); p.setPrecio(10.0); p.setDisponible(true);
        return platoDao.save(p);
    }

    private boolean hayAuditoria(String accion, String entidadId) {
        return auditoriaDao.findAll().stream().anyMatch(r -> accion.equals(r.getAccion())
            && (entidadId == null || entidadId.equals(r.getEntidadId())) && "admin".equals(r.getUsuario()));
    }

    private static byte[] imagen(String formato) throws Exception {
        BufferedImage img = new BufferedImage(4, 3, BufferedImage.TYPE_INT_RGB);
        img.setRGB(1, 1, 0x336699);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(img, formato, salida));
        return salida.toByteArray();
    }

    private ResultActions subir(long platoId, MockMultipartFile archivo, RequestPostProcessor quien) throws Exception {
        return mvc().perform(multipart(HttpMethod.PUT, "/api/admin/platos/" + platoId + "/imagen").file(archivo)
            .with(quien).with(csrf()));
    }

    @Test
    void seedingCreatesDefaultsAndMapsExistingPlateCategories() throws Exception {
        List<String> codigos = categoriaDao.findAllByOrderByOrdenAscIdAsc().stream().map(Categoria::getCodigo).toList();
        assertEquals(List.of("SOPAS", "SEGUNDOS", "ENSALADAS", "REFRESCOS", "EXTRAS", "ANTOJITOS"), codigos.subList(0, 6));
        Categoria sopas = categoriaDao.findByCodigoIgnoreCase("SOPAS").orElseThrow();
        assertTrue(sopas.getEsAcompanamiento());
        assertFalse(sopas.getLlevaAcompanamiento());
        assertEquals(20, sopas.getCantidadSugerida());
        assertEquals("🍲", sopas.getIcono());
        assertTrue(categoriaDao.findByCodigoIgnoreCase("SEGUNDOS").orElseThrow().getLlevaAcompanamiento());
        assertNull(categoriaDao.findByCodigoIgnoreCase("REFRESCOS").orElseThrow().getCantidadSugerida());
        assertEquals("Platos extras", categoriaDao.findByCodigoIgnoreCase("EXTRAS").orElseThrow().getNombre());
        assertEquals(15, categoriaDao.findByCodigoIgnoreCase("ANTOJITOS").orElseThrow().getCantidadSugerida());

        String s = sufijo();
        Plato minuscula = plato("Huevo " + s, "extras");
        Plato nueva = plato("Flan " + s, "Postres de la casa " + s);
        long antes = categoriaDao.count();
        categoriaService.inicializar();
        categoriaService.inicializar();
        assertEquals(antes + 1, categoriaDao.count());
        assertEquals("EXTRAS", platoDao.findById(minuscula.getId()).orElseThrow().getCategoria());
        String codigo = ("POSTRES_DE_LA_CASA_" + s).toUpperCase();
        Categoria creada = categoriaDao.findByCodigoIgnoreCase(codigo).orElseThrow();
        assertEquals(creada.getCodigo(), platoDao.findById(nueva.getId()).orElseThrow().getCategoria());
        assertEquals("Postres de la casa " + s, creada.getNombre());
        assertEquals("🍽️", creada.getIcono());
        assertTrue(creada.getActiva());

        mvc().perform(get("/api/categorias").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].codigo").value("SOPAS"))
            .andExpect(jsonPath("$[0].esAcompanamiento").value(true))
            .andExpect(jsonPath("$[?(@.codigo == '" + creada.getCodigo() + "')].productos", hasItem(1)));
    }

    @Test
    void crudAndValidations() throws Exception {
        String s = sufijo();
        ResultActions creada = crearCategoria("Bebidas Cálidas " + s, "☕", false, false, 12)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.codigo").value(("BEBIDAS_CALIDAS_" + s).toUpperCase()))
            .andExpect(jsonPath("$.nombre").value("Bebidas Cálidas " + s))
            .andExpect(jsonPath("$.icono").value("☕"))
            .andExpect(jsonPath("$.activa").value(true))
            .andExpect(jsonPath("$.cantidadSugerida").value(12))
            .andExpect(jsonPath("$.productos").value(0));
        long id = idDe(creada);
        int maximo = categoriaDao.findAll().stream().mapToInt(Categoria::getOrden).max().orElseThrow();
        assertEquals(maximo, categoriaDao.findById(id).orElseThrow().getOrden());
        assertTrue(hayAuditoria("CATEGORIA_CREADA", String.valueOf(id)));

        crearCategoria("  bebidas CALIDAS " + s.toUpperCase() + " ", "☕", false, false, null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.mensaje").value("Ya existe una categoría con ese nombre"));
        crearCategoria("Otra " + s, "x", true, true, null)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.mensaje").value("Una categoría no puede llevar acompañamiento y ser acompañamiento a la vez"));
        crearCategoria("Otra " + s, "   ", false, false, null).andExpect(status().isBadRequest());
        crearCategoria("Otra " + s, null, false, false, null).andExpect(status().isBadRequest());
        crearCategoria("   ", "x", false, false, null).andExpect(status().isBadRequest());
        crearCategoria("N".repeat(41), "x", false, false, null).andExpect(status().isBadRequest());
        crearCategoria("Otra " + s, "x".repeat(17), false, false, null).andExpect(status().isBadRequest());
        crearCategoria("Otra " + s, "x", false, false, 1000).andExpect(status().isBadRequest());
        crearCategoria("Otra " + s, "x", false, false, -1).andExpect(status().isBadRequest());

        mvc().perform(put("/api/admin/categorias/" + id).with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Infusiones " + s, "🍵", true, false, null)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.codigo").value(("BEBIDAS_CALIDAS_" + s).toUpperCase()))
            .andExpect(jsonPath("$.nombre").value("Infusiones " + s))
            .andExpect(jsonPath("$.llevaAcompanamiento").value(true))
            .andExpect(jsonPath("$.cantidadSugerida").doesNotExist());
        assertTrue(hayAuditoria("CATEGORIA_EDITADA", String.valueOf(id)));
        mvc().perform(put("/api/admin/categorias/" + id).with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Platos Extras", "🍵", false, false, null)))
            .andExpect(status().isConflict());
        mvc().perform(put("/api/admin/categorias/999999").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Nada " + s, "x", false, false, null)))
            .andExpect(status().isNotFound());

        mvc().perform(delete("/api/admin/categorias/" + id).with(admin()).with(csrf()))
            .andExpect(status().isNoContent());
        assertFalse(categoriaDao.existsById(id));
        assertTrue(hayAuditoria("CATEGORIA_ELIMINADA", String.valueOf(id)));
    }

    @Test
    void generatedCodesAreUniqueWithSuffix() throws Exception {
        String s = sufijo();
        long primera = idDe(crearCategoria("Café " + s, "☕", false, false, null).andExpect(status().isCreated()));
        mvc().perform(put("/api/admin/categorias/" + primera).with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Renombrada " + s, "☕", false, false, null)))
            .andExpect(status().isOk());
        crearCategoria("Cafe " + s, "☕", false, false, null)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.codigo").value(("CAFE_" + s).toUpperCase() + "_2"));
    }

    @Test
    void reorderRequiresFullIdSet() throws Exception {
        List<Long> ids = new ArrayList<>(categoriaDao.findAllByOrderByOrdenAscIdAsc().stream().map(Categoria::getId).toList());
        List<Long> invertidos = new ArrayList<>(ids);
        Collections.reverse(invertidos);
        String incompleto = invertidos.subList(1, invertidos.size()).toString();
        mvc().perform(put("/api/admin/categorias/orden").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":" + incompleto + "}"))
            .andExpect(status().isBadRequest());
        List<Long> repetidos = new ArrayList<>(invertidos);
        repetidos.set(0, repetidos.get(1));
        mvc().perform(put("/api/admin/categorias/orden").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":" + repetidos + "}"))
            .andExpect(status().isBadRequest());
        mvc().perform(put("/api/admin/categorias/orden").with(mesero()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":" + invertidos + "}"))
            .andExpect(status().isForbidden());
        mvc().perform(put("/api/admin/categorias/orden").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":" + invertidos + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(invertidos.get(0)))
            .andExpect(jsonPath("$[0].orden").value(1));
        assertTrue(hayAuditoria("CATEGORIAS_REORDENADAS", null));
        mvc().perform(put("/api/admin/categorias/orden").with(admin()).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":" + ids + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].codigo").value("SOPAS"));
    }

    @Test
    void deleteWithProductsRequiresDestinationAndMovesThem() throws Exception {
        String s = sufijo();
        long origen = idDe(crearCategoria("Origen " + s, "🅰", false, false, null).andExpect(status().isCreated()));
        long destino = idDe(crearCategoria("Destino " + s, "🅱", false, false, null).andExpect(status().isCreated()));
        String codigoOrigen = categoriaDao.findById(origen).orElseThrow().getCodigo();
        String codigoDestino = categoriaDao.findById(destino).orElseThrow().getCodigo();
        Plato activo = plato("Activo " + s, codigoOrigen);
        Plato archivado = plato("Archivado " + s, codigoOrigen);
        archivado.setArchivado(true);
        platoDao.save(archivado);

        mvc().perform(get("/api/categorias").with(cocina()))
            .andExpect(jsonPath("$[?(@.id == " + origen + ")].productos", hasItem(1)));
        mvc().perform(delete("/api/admin/categorias/" + origen).with(admin()).with(csrf()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.mensaje").value("Esta categoría tiene 2 productos. Elige a qué categoría moverlos antes de eliminarla."));
        mvc().perform(delete("/api/admin/categorias/" + origen).param("moverA", String.valueOf(origen))
                .with(admin()).with(csrf()))
            .andExpect(status().isBadRequest());
        mvc().perform(delete("/api/admin/categorias/" + origen).param("moverA", "999999")
                .with(admin()).with(csrf()))
            .andExpect(status().isBadRequest());
        mvc().perform(delete("/api/admin/categorias/" + origen).with(mesero()).with(csrf()))
            .andExpect(status().isForbidden());
        assertTrue(categoriaDao.existsById(origen));

        mvc().perform(delete("/api/admin/categorias/" + origen).param("moverA", String.valueOf(destino))
                .with(admin()).with(csrf()))
            .andExpect(status().isNoContent());
        assertFalse(categoriaDao.existsById(origen));
        assertEquals(codigoDestino, platoDao.findById(activo.getId()).orElseThrow().getCategoria());
        assertEquals(codigoDestino, platoDao.findById(archivado.getId()).orElseThrow().getCategoria());
        assertTrue(auditoriaDao.findAll().stream().anyMatch(r -> "CATEGORIA_ELIMINADA".equals(r.getAccion())
            && String.valueOf(origen).equals(r.getEntidadId())
            && r.getDetalle().contains("2 productos movidos a Destino " + s)));
        mvc().perform(delete("/api/admin/categorias/999999").with(admin()).with(csrf()))
            .andExpect(status().isNotFound());
    }

    @Test
    void platesRequireExistingCategory() throws Exception {
        String s = sufijo();
        mvc().perform(post("/api/platos").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Sin cat " + s + "\",\"precio\":5,\"categoria\":\"NO_EXISTE_" + s + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.mensaje").value("Elige una categoría válida"));
        mvc().perform(post("/api/platos").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Sin cat " + s + "\",\"precio\":5}"))
            .andExpect(status().isBadRequest());
        String json = mvc().perform(post("/api/platos").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Con cat " + s + "\",\"precio\":5,\"categoria\":\" extras \",\"imagenVersion\":77}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoria").value("EXTRAS"))
            .andExpect(jsonPath("$.imagenUrl").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();
        assertNull(platoDao.findById(id).orElseThrow().getImagenVersion());
        mvc().perform(put("/api/platos/" + id).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Con cat " + s + "\",\"precio\":5,\"categoria\":\"inventada\"}"))
            .andExpect(status().isBadRequest());
        mvc().perform(put("/api/platos/" + id).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Con cat " + s + "\",\"precio\":5,\"categoria\":\"sopas\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoria").value("SOPAS"));
    }

    @Test
    void plateDescriptionAvailabilityAndUnlimitedStock() throws Exception {
        String s = sufijo();
        String json = mvc().perform(post("/api/platos").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Oculto " + s + "\",\"precio\":5,\"categoria\":\"EXTRAS\",\"disponible\":false,"
                    + "\"descripcion\":\"  Con queso  \",\"stockDisponible\":8}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.disponible").value(false))
            .andExpect(jsonPath("$.descripcion").value("Con queso"))
            .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(json, "$.id")).longValue();
        mvc().perform(post("/api/platos").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Visible " + s + "\",\"precio\":5,\"categoria\":\"EXTRAS\",\"descripcion\":\"   \"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.disponible").value(true))
            .andExpect(jsonPath("$.descripcion").doesNotExist());
        mvc().perform(post("/api/platos").with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Largo " + s + "\",\"precio\":5,\"categoria\":\"EXTRAS\",\"descripcion\":\""
                    + "d".repeat(201) + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.mensaje").value("La descripción no puede superar 200 caracteres"));

        String base = "\"nombre\":\"Oculto " + s + "\",\"precio\":6,\"categoria\":\"EXTRAS\"";
        mvc().perform(put("/api/platos/" + id).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{" + base + "}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockDisponible").value(8))
            .andExpect(jsonPath("$.descripcion").value("Con queso"));
        mvc().perform(put("/api/platos/" + id).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{" + base + ",\"descripcion\":\" Nueva \",\"stockDisponible\":3}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockDisponible").value(3))
            .andExpect(jsonPath("$.descripcion").value("Nueva"));
        mvc().perform(put("/api/platos/" + id).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{" + base + ",\"descripcion\":\"" + "d".repeat(201) + "\"}"))
            .andExpect(status().isBadRequest());
        mvc().perform(put("/api/platos/" + id).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{" + base + ",\"descripcion\":\"\",\"stockDisponible\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stockDisponible").doesNotExist())
            .andExpect(jsonPath("$.descripcion").doesNotExist());
        assertNull(platoDao.findById(id).orElseThrow().getStockDisponible());
        mvc().perform(put("/api/platos/" + id).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{" + base + ",\"stockDisponible\":4}"))
            .andExpect(jsonPath("$.stockDisponible").value(4));
        mvc().perform(put("/api/platos/" + id).with(admin()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{" + base + ",\"stockDisponible\":9,\"sinLimite\":true}"))
            .andExpect(status().isOk());
        assertNull(platoDao.findById(id).orElseThrow().getStockDisponible());
    }

    @Test
    void categoryReadIsSharedButMutationsAreAdminOnly() throws Exception {
        mvc().perform(get("/api/categorias").with(mesero())).andExpect(status().isOk());
        mvc().perform(get("/api/categorias").with(cocina())).andExpect(status().isOk());
        mvc().perform(get("/api/categorias")).andExpect(status().isUnauthorized());
        for (RequestPostProcessor rol : List.of(mesero(), cocina())) {
            mvc().perform(post("/api/admin/categorias").with(rol).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Prohibida", "x", false, false, null)))
                .andExpect(status().isForbidden());
            mvc().perform(put("/api/admin/categorias/1").with(rol).with(csrf())
                    .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Prohibida", "x", false, false, null)))
                .andExpect(status().isForbidden());
            mvc().perform(delete("/api/admin/categorias/1").with(rol).with(csrf()))
                .andExpect(status().isForbidden());
            subir(1, new MockMultipartFile("archivo", "f.png", "image/png", imagen("png")), rol)
                .andExpect(status().isForbidden());
            mvc().perform(delete("/api/admin/platos/1/imagen").with(rol).with(csrf()))
                .andExpect(status().isForbidden());
        }
        mvc().perform(post("/api/admin/categorias").with(admin())
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo("Sin csrf", "x", false, false, null)))
            .andExpect(status().isForbidden());
    }

    @Test
    void photoUploadServeAndDelete() throws Exception {
        Plato p = plato("Con foto " + sufijo(), "EXTRAS");
        byte[] png = imagen("png");
        String json = subir(p.getId(), new MockMultipartFile("archivo", "foto.png", "image/png", png), admin())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(p.getId()))
            .andExpect(jsonPath("$.imagenUrl", startsWith("/api/platos/" + p.getId() + "/imagen?v=")))
            .andExpect(jsonPath("$.datos").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        long v1 = ((Number) JsonPath.read(json, "$.imagenVersion")).longValue();
        assertTrue(hayAuditoria("PLATO_IMAGEN_ACTUALIZADA", String.valueOf(p.getId())));

        byte[] servido = mvc().perform(get("/api/platos/" + p.getId() + "/imagen").param("v", String.valueOf(v1)).with(cocina()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/png"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Cache-Control", "private, max-age=31536000, immutable"))
            .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(png, servido);
        mvc().perform(get("/api/platos/" + p.getId() + "/imagen").with(mesero()))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-cache"));
        mvc().perform(get("/api/platos/" + p.getId() + "/imagen")).andExpect(status().isUnauthorized());

        byte[] jpg = imagen("jpg");
        String json2 = subir(p.getId(), new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", jpg), admin())
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long v2 = ((Number) JsonPath.read(json2, "$.imagenVersion")).longValue();
        assertTrue(v2 > v1);
        mvc().perform(get("/api/platos/" + p.getId() + "/imagen").param("v", String.valueOf(v1)).with(admin()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "image/jpeg"))
            .andExpect(header().string("Cache-Control", "no-cache"));
        subir(p.getId(), new MockMultipartFile("archivo", "blob", "application/octet-stream", png), admin())
            .andExpect(status().isOk());

        mvc().perform(get("/api/platos/todos").with(admin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + p.getId() + ")].imagenUrl",
                hasItem(startsWith("/api/platos/" + p.getId() + "/imagen?v="))))
            .andExpect(content().string(not(containsString("\"datos\""))));

        mvc().perform(delete("/api/admin/platos/" + p.getId() + "/imagen").with(admin()).with(csrf()))
            .andExpect(status().isNoContent());
        assertTrue(hayAuditoria("PLATO_IMAGEN_ELIMINADA", String.valueOf(p.getId())));
        assertNull(platoDao.findById(p.getId()).orElseThrow().getImagenVersion());
        assertFalse(imagenDao.existsById(p.getId()));
        mvc().perform(get("/api/platos/" + p.getId() + "/imagen").with(admin()))
            .andExpect(status().isNotFound());
        mvc().perform(get("/api/platos/todos").with(admin()))
            .andExpect(jsonPath("$[?(@.id == " + p.getId() + ")].imagenUrl", hasItem((Object) null)));
        mvc().perform(delete("/api/admin/platos/999999/imagen").with(admin()).with(csrf()))
            .andExpect(status().isNotFound());
        subir(999999, new MockMultipartFile("archivo", "foto.png", "image/png", png), admin())
            .andExpect(status().isNotFound());
    }

    @Test
    void photoRejectsFakeOversizedAndSvg() throws Exception {
        Plato p = plato("Rechazos " + sufijo(), "EXTRAS");
        subir(p.getId(), new MockMultipartFile("archivo", "foto.png", "image/png",
                "no soy imagen".getBytes(StandardCharsets.UTF_8)), admin())
            .andExpect(status().isBadRequest());
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg' width='4' height='4'><rect width='4' height='4'/></svg>"
            .getBytes(StandardCharsets.UTF_8);
        subir(p.getId(), new MockMultipartFile("archivo", "foto.svg", "image/svg+xml", svg), admin())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.mensaje").value("La foto debe ser una imagen PNG, JPG o WebP válida"));
        byte[] grande = new byte[1_048_577];
        System.arraycopy(imagen("png"), 0, grande, 0, 8);
        subir(p.getId(), new MockMultipartFile("archivo", "foto.png", "image/png", grande), admin())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.mensaje").value("La foto no puede superar 1 MB"));
        byte[] enorme = new byte[33];
        byte[] firma = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
        System.arraycopy(firma, 0, enorme, 0, firma.length);
        enorme[11] = 13; enorme[12] = 'I'; enorme[13] = 'H'; enorme[14] = 'D'; enorme[15] = 'R';
        enorme[18] = 0x20; enorme[19] = 0x00; enorme[22] = 0x10; enorme[23] = 0x00;
        subir(p.getId(), new MockMultipartFile("archivo", "foto.png", "image/png", enorme), admin())
            .andExpect(status().isBadRequest());
        subir(p.getId(), new MockMultipartFile("archivo", "foto.png", "image/jpeg", imagen("png")), admin())
            .andExpect(status().isBadRequest());
        subir(p.getId(), new MockMultipartFile("archivo", "foto.jpg", "image/png", imagen("png")), admin())
            .andExpect(status().isBadRequest());
        assertNull(platoDao.findById(p.getId()).orElseThrow().getImagenVersion());
        assertFalse(imagenDao.existsById(p.getId()));
    }

    @Test
    void physicalPlateDeleteRemovesPhotoRow() throws Exception {
        Plato p = plato("Borrable " + sufijo(), "EXTRAS");
        subir(p.getId(), new MockMultipartFile("archivo", "foto.png", "image/png", imagen("png")), admin())
            .andExpect(status().isOk());
        assertTrue(imagenDao.existsById(p.getId()));
        mvc().perform(delete("/api/platos/" + p.getId()).with(admin()).with(csrf()))
            .andExpect(status().isNoContent());
        assertFalse(platoDao.existsById(p.getId()));
        assertFalse(imagenDao.existsById(p.getId()));
    }

    @Test
    void photoColumnIsBinaryNotLob() {
        String tipo = jdbc.queryForObject("SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
            + "WHERE UPPER(TABLE_NAME) = 'PLATO_IMAGEN' AND UPPER(COLUMN_NAME) = 'DATOS'", String.class);
        assertEquals("BINARY VARYING", tipo);
        assertEquals("bytea", ddl(org.hibernate.dialect.PostgreSQLDialect.class));
    }

    private static String ddl(Class<?> dialecto) {
        var registro = new org.hibernate.boot.registry.StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", dialecto.getName())
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .build();
        try {
            var metadata = new org.hibernate.boot.MetadataSources(registro)
                .addAnnotatedClass(com.sistema.pedidosCori.models.entity.PlatoImagen.class).buildMetadata();
            return metadata.getEntityBinding(com.sistema.pedidosCori.models.entity.PlatoImagen.class.getName())
                .getTable().getColumn(org.hibernate.boot.model.naming.Identifier.toIdentifier("datos"))
                .getSqlType(metadata).toLowerCase(java.util.Locale.ROOT);
        } finally {
            org.hibernate.boot.registry.StandardServiceRegistryBuilder.destroy(registro);
        }
    }
}
