package com.sistema.pedidosCori;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.sistema.pedidosCori.models.dao.ICategoriaDao;
import com.sistema.pedidosCori.models.dao.IPedidoDao;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dao.IPresentacionPlatoDao;
import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.dto.ConfiguracionNegocioVista;
import com.sistema.pedidosCori.models.entity.Categoria;
import com.sistema.pedidosCori.models.entity.DetallePedido;
import com.sistema.pedidosCori.models.entity.EstadoPedido;
import com.sistema.pedidosCori.models.entity.Pedido;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.models.entity.PresentacionPlato;
import com.sistema.pedidosCori.models.entity.Rol;
import com.sistema.pedidosCori.models.entity.Usuario;
import com.sistema.pedidosCori.service.ConfiguracionNegocioService;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReportesAvanzadosTests {
    private static final String RANGO = "desde=2021-03-08&hasta=2021-03-21";

    @Autowired WebApplicationContext context;
    @Autowired IUsuarioDao usuarioDao;
    @Autowired ICategoriaDao categoriaDao;
    @Autowired IPlatoDao platoDao;
    @Autowired IPresentacionPlatoDao presentacionDao;
    @Autowired IPedidoDao pedidoDao;
    @Autowired JdbcTemplate jdbc;
    @Autowired ConfiguracionNegocioService configuracion;

    private Plato sopa;
    private Plato pollo;
    private Plato jugo;
    private Plato misterio;
    private PresentacionPlato grande;
    private PresentacionPlato mediano;
    private final Map<String, Long> ids = new java.util.HashMap<>();

    private MockMvc mvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private static RequestPostProcessor admin() { return user("admin").roles("ADMIN"); }

    @BeforeAll
    void datos() {
        usuario("rep.ana", "Ana Ruiz", Rol.MESERO);
        usuario("rep.beto", "Beto Paz", Rol.MESERO);
        usuario("rep.caja", "Caja Central", Rol.ADMIN);
        categoriaDao.save(Categoria.builder().codigo("rep_platos").nombre("Platos rep").icono("P").orden(900).build());
        categoriaDao.save(Categoria.builder().codigo("rep_bebidas").nombre("Bebidas rep").icono("B").orden(901).build());
        sopa = plato("Sopa", "rep_platos", 10.0);
        pollo = plato("Pollo", "rep_platos", 25.0);
        jugo = plato("Jugo", "rep_bebidas", 5.0);
        misterio = plato("Misterio", "rep_inexistente", 7.0);
        grande = presentacionDao.save(PresentacionPlato.builder().plato(pollo).nombre("Grande").precio(25.0).build());
        mediano = presentacionDao.save(PresentacionPlato.builder().plato(pollo).nombre("Mediano").precio(18.0).build());

        pedido("P1", "MESA", 3, null, t(8, 12, 10), t(8, 12, 40), true, "rep.ana", "rep.caja", "EFECTIVO",
            d(sopa, null, 2, 10.0), d(jugo, null, 1, 5.0));
        pedido("P2", "LLEVAR", null, "Cliente; \"Raro\"", t(9, 19, 0), t(9, 19, 30), true, "rep.beto", "rep.beto", "QR",
            d(pollo, grande, 1, 25.0), d(jugo, null, 2, 5.0));
        pedido("P3", "MESA", 5, null, t(10, 13, 5), null, true, "rep.ana", null, null,
            d(misterio, null, 1, 7.0));
        pedido("P4", "LLEVAR", null, "=HYPERLINK(1)", t(14, 23, 30), t(15, 0, 15), true, "rep.beto", "rep.ana", "TARJETA",
            d(pollo, mediano, 2, 18.0));
        pedido("P5", "MESA", 2, null, t(18, 20, 0), null, false, "rep.ana", null, null,
            d(sopa, null, 1, 10.0));
        pedido("P6", "MESA", 1, null, t(20, 12, 0), t(20, 12, 20), true, "rep.caja", "rep.caja", "EFECTIVO",
            d(sopa, null, 3, 10.0), d(pollo, grande, 1, 25.0));
        pedido("P7", "LLEVAR", null, "Luis", t(7, 22, 0), t(8, 9, 0), true, "rep.beto", "rep.ana", "QR",
            d(jugo, null, 1, 5.0));
        pedido("P8", "MESA", 6, null, t(21, 23, 0), t(22, 8, 0), true, "rep.ana", "rep.caja", "EFECTIVO",
            d(sopa, null, 1, 10.0));
        pedido("P9", "MESA", 4, null, t(1, 10, 0),
            t(1, 10, 30), true, "rep.ana", "rep.ana", "EFECTIVO", d(sopa, null, 2, 10.0));
    }

    private static LocalDateTime t(int dia, int hora, int minuto) {
        return LocalDateTime.of(2021, 3, dia, hora, minuto);
    }

    private void usuario(String username, String nombre, Rol rol) {
        if (usuarioDao.existsByUsername(username)) return;
        usuarioDao.save(Usuario.builder().username(username).nombreCompleto(nombre).password("{noop}x").rol(rol).build());
    }

    private Plato plato(String nombre, String categoria, double precio) {
        return platoDao.save(Plato.builder().nombre(nombre).categoria(categoria).precio(precio).disponible(true).build());
    }

    private static DetallePedido d(Plato plato, PresentacionPlato presentacion, int cantidad, double precio) {
        return DetallePedido.builder().plato(plato).presentacion(presentacion).cantidad(cantidad)
            .precioUnitario(precio).subtotal(precio * cantidad).build();
    }

    private void pedido(String clave, String tipo, Integer mesa, String cliente, LocalDateTime creado, LocalDateTime pagadoEn,
            boolean pagado, String registro, String cobro, String metodo, DetallePedido... detalles) {
        List<DetallePedido> lista = new ArrayList<>(Arrays.asList(detalles));
        Pedido p = Pedido.builder().tipo(tipo).numeroMesa(mesa).nombreCliente(cliente).estado(EstadoPedido.ENTREGADO)
            .pagado(pagado).fechaPago(pagadoEn).cobradoPor(cobro).metodoPago(metodo)
            .total(lista.stream().mapToDouble(DetallePedido::getSubtotal).sum()).detalles(lista).build();
        lista.forEach(x -> x.setPedido(p));
        Pedido guardado = pedidoDao.save(p);
        jdbc.update("UPDATE pedidos SET fecha_creacion = ?, registrado_por = ? WHERE id = ?", creado, registro, guardado.getId());
        ids.put(clave, guardado.getId());
    }

    private DocumentContext reporte(String query) throws Exception {
        String json = mvc().perform(get("/api/admin/reportes?" + query).with(admin()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.parse(json);
    }

    private static double suma(DocumentContext doc, String path) {
        List<Number> valores = doc.read(path);
        return valores.stream().mapToDouble(Number::doubleValue).sum();
    }

    private static double num(DocumentContext doc, String path) {
        return ((Number) doc.read(path)).doubleValue();
    }

    @Test
    void resumenYTotalesConcilian() throws Exception {
        DocumentContext r = reporte(RANGO);
        assertEquals("2021-03-08", r.read("$.rango.desde"));
        assertEquals(14, (int) r.read("$.rango.dias"));
        assertEquals("dia", r.read("$.rango.agrupar"));
        assertEquals("2021-02-22", r.read("$.anterior.desde"));
        assertEquals("2021-03-07", r.read("$.anterior.hasta"));

        assertEquals(163.0, num(r, "$.resumen.ventas"));
        assertEquals(6, (int) r.read("$.resumen.pedidosCobrados"));
        assertEquals(27.17, num(r, "$.resumen.ticketPromedio"));
        assertEquals(14, (int) r.read("$.resumen.unidades"));
        assertEquals(7, (int) r.read("$.resumen.pedidosRegistrados"));
        assertEquals(1, (int) r.read("$.resumen.porCobrarPedidos"));
        assertEquals(10.0, num(r, "$.resumen.porCobrarMonto"));

        assertEquals(20.0, num(r, "$.resumen.anterior.ventas"));
        assertEquals(1, (int) r.read("$.resumen.anterior.pedidosCobrados"));
        assertEquals(2, (int) r.read("$.resumen.anterior.unidades"));
        assertEquals(2, (int) r.read("$.resumen.anterior.pedidosRegistrados"));
        assertEquals(715.0, num(r, "$.resumen.variacion.ventas"));
        assertEquals(500.0, num(r, "$.resumen.variacion.pedidosCobrados"));
        assertEquals(35.9, num(r, "$.resumen.variacion.ticketPromedio"), 0.051);
        assertEquals(600.0, num(r, "$.resumen.variacion.unidades"));
        assertEquals(250.0, num(r, "$.resumen.variacion.pedidosRegistrados"));

        assertEquals(163.0, suma(r, "$.serie[*].ventas"), 0.001);
        assertEquals(163.0, suma(r, "$.productos[*].ingresos"), 0.001);
        assertEquals(163.0, suma(r, "$.metodosPago[*].monto"), 0.001);
        assertEquals(163.0, suma(r, "$.categorias[*].ingresos"), 0.001);
        assertEquals(163.0, suma(r, "$.empleados[*].ventas"), 0.001);
        assertEquals(163.0, suma(r, "$.horas[*].ventas"), 0.001);
        assertEquals(163.0, suma(r, "$.diasSemana[*].ventas"), 0.001);
        assertEquals(163.0, suma(r, "$.modalidades[*].ventas"), 0.001);
    }

    @Test
    void variacionNulaSinPeriodoAnterior() throws Exception {
        DocumentContext r = reporte("desde=2021-03-01&hasta=2021-03-01");
        assertEquals(20.0, num(r, "$.resumen.ventas"));
        assertNull(r.read("$.resumen.variacion.ventas"));
        assertNull(r.read("$.resumen.variacion.ticketPromedio"));
    }

    @Test
    void productosCategoriasYMetodos() throws Exception {
        DocumentContext r = reporte(RANGO);
        assertEquals(List.of("Sopa", "Jugo", "Pollo (Grande)", "Pollo (Mediano)", "Misterio"), r.read("$.productos[*].nombre"));
        assertEquals(List.of(5, 4, 2, 2, 1), r.read("$.productos[*].unidades"));
        assertEquals("Platos rep", r.read("$.productos[0].categoria"));
        assertEquals("P", r.read("$.productos[0].icono"));
        assertEquals("Sin categoría", r.read("$.productos[4].categoria"));

        assertEquals(List.of("Platos rep", "Bebidas rep", "Sin categoría"), r.read("$.categorias[*].nombre"));
        assertEquals("rep_platos", r.read("$.categorias[0].codigo"));
        assertEquals(136.0, num(r, "$.categorias[0].ingresos"));
        assertEquals(9, (int) r.read("$.categorias[0].unidades"));
        assertNull(r.read("$.categorias[2].codigo"));

        assertEquals(List.of("EFECTIVO", "QR", "TARJETA", "Sin especificar"), r.read("$.metodosPago[*].metodo"));
        assertEquals(List.of(2, 2, 1, 1), r.read("$.metodosPago[*].pedidos"));
        assertEquals(80.0, num(r, "$.metodosPago[0].monto"));
    }

    @Test
    void empleadosPorRol() throws Exception {
        DocumentContext r = reporte(RANGO);
        assertEquals(List.of("rep.beto", "rep.caja", "rep.ana", "Sin dato"), r.read("$.empleados[*].usuario"));
        assertEquals(List.of("Beto Paz", "Caja Central", "Ana Ruiz", "Sin dato"), r.read("$.empleados[*].nombre"));
        assertEquals(76.0, num(r, "$.empleados[0].ventas"));
        assertEquals(3, (int) r.read("$.empleados[0].pedidosCobrados"));
        assertEquals(25.33, num(r, "$.empleados[0].ticketPromedio"));
        assertEquals(List.of(2, 1, 4, 0), r.read("$.empleados[*].pedidosRegistrados"));
        assertEquals(List.of(1, 2, 2, 1), r.read("$.empleados[*].cobros"));
        assertEquals(List.of(3, 1, 2, 0), r.read("$.empleados[*].pedidosCobrados"));
    }

    @Test
    void horasDiasYModalidades() throws Exception {
        DocumentContext r = reporte(RANGO);
        assertEquals(24, ((List<?>) r.read("$.horas")).size());
        assertEquals(0, (int) r.read("$.horas[0].hora"));
        assertEquals(1, (int) r.read("$.horas[0].pedidos"));
        assertEquals(36.0, num(r, "$.horas[0].ventas"));
        assertEquals(2, (int) r.read("$.horas[12].pedidos"));
        assertEquals(80.0, num(r, "$.horas[12].ventas"));
        assertEquals(1, (int) r.read("$.horas[13].pedidos"));
        assertEquals(0, (int) r.read("$.horas[23].pedidos"));

        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), r.read("$.diasSemana[*].dia"));
        assertEquals("Lunes", r.read("$.diasSemana[0].nombre"));
        assertEquals("Domingo", r.read("$.diasSemana[6].nombre"));
        assertEquals(List.of(3, 1, 1, 0, 0, 1, 0), r.read("$.diasSemana[*].pedidos"));
        assertEquals(66.0, num(r, "$.diasSemana[0].ventas"));

        assertEquals(List.of("MESA", "LLEVAR"), r.read("$.modalidades[*].tipo"));
        assertEquals(87.0, num(r, "$.modalidades[0].ventas"));
        assertEquals(76.0, num(r, "$.modalidades[1].ventas"));
        assertEquals(List.of(3, 3), r.read("$.modalidades[*].pedidos"));
    }

    @Test
    void serieDiariaIncluyeDiasVacios() throws Exception {
        DocumentContext r = reporte(RANGO);
        assertEquals(14, ((List<?>) r.read("$.serie")).size());
        assertEquals("2021-03-08", r.read("$.serie[0].clave"));
        assertEquals("lun 8", r.read("$.serie[0].etiqueta"));
        assertEquals(30.0, num(r, "$.serie[0].ventas"));
        assertEquals(2, (int) r.read("$.serie[0].pedidosCobrados"));
        assertEquals("mié 10", r.read("$.serie[2].etiqueta"));
        assertEquals(0.0, num(r, "$.serie[3].ventas"));
        assertEquals("sáb 20", r.read("$.serie[12].etiqueta"));
        assertEquals(55.0, num(r, "$.serie[12].ventas"));
        assertEquals("2021-03-21", r.read("$.serie[13].hasta"));
    }

    @Test
    void serieSemanalEmpiezaEnLunes() throws Exception {
        DocumentContext r = reporte(RANGO + "&agrupar=semana");
        assertEquals(List.of("2021-03-08", "2021-03-15"), r.read("$.serie[*].clave"));
        assertEquals(List.of("Sem. 8 mar", "Sem. 15 mar"), r.read("$.serie[*].etiqueta"));
        assertEquals(List.of(72.0, 91.0), r.read("$.serie[*].ventas"));

        DocumentContext parcial = reporte("desde=2021-03-10&hasta=2021-03-16&agrupar=semana");
        assertEquals(List.of("2021-03-08", "2021-03-15"), parcial.read("$.serie[*].clave"));
        assertEquals(List.of("2021-03-10", "2021-03-15"), parcial.read("$.serie[*].desde"));
        assertEquals(List.of("2021-03-14", "2021-03-16"), parcial.read("$.serie[*].hasta"));
        assertEquals(List.of(7.0, 36.0), parcial.read("$.serie[*].ventas"));
    }

    @Test
    void serieMensualYAgrupacionAutomatica() throws Exception {
        DocumentContext r = reporte("desde=2021-02-20&hasta=2021-03-21&agrupar=mes");
        assertEquals(List.of("2021-02", "2021-03"), r.read("$.serie[*].clave"));
        assertEquals(List.of("feb 2021", "mar 2021"), r.read("$.serie[*].etiqueta"));
        assertEquals(List.of("2021-02-20", "2021-03-01"), r.read("$.serie[*].desde"));
        assertEquals(List.of("2021-02-28", "2021-03-21"), r.read("$.serie[*].hasta"));
        assertEquals(List.of(0.0, 183.0), r.read("$.serie[*].ventas"));

        assertEquals("dia", reporte("desde=2021-03-01&hasta=2021-03-31").read("$.rango.agrupar"));
        assertEquals("semana", reporte("desde=2021-01-01&hasta=2021-03-31").read("$.rango.agrupar"));
        assertEquals("mes", reporte("desde=2021-01-01&hasta=2021-12-31").read("$.rango.agrupar"));
    }

    @Test
    void validaParametros() throws Exception {
        mvc().perform(get("/api/admin/reportes?desde=2021-01-01&hasta=2022-01-02").with(admin()))
            .andExpect(status().isBadRequest());
        mvc().perform(get("/api/admin/reportes?desde=2020-01-01&hasta=2020-12-31").with(admin()))
            .andExpect(status().isOk());
        mvc().perform(get("/api/admin/reportes?desde=2021-03-10&hasta=2021-03-01").with(admin()))
            .andExpect(status().isBadRequest());
        mvc().perform(get("/api/admin/reportes?" + RANGO + "&agrupar=hora").with(admin()))
            .andExpect(status().isBadRequest());
        mvc().perform(get("/api/admin/reportes?hasta=2021-03-01").with(admin()))
            .andExpect(status().isBadRequest());
        mvc().perform(get("/api/admin/reportes?desde=ayer&hasta=2021-03-01").with(admin()))
            .andExpect(status().isBadRequest());
        mvc().perform(get("/api/admin/reportes/csv?" + RANGO + "&tipo=otro").with(admin()))
            .andExpect(status().isBadRequest());
        mvc().perform(get("/api/admin/reportes/csv?desde=2021-01-01&hasta=2022-06-01&tipo=pedidos").with(admin()))
            .andExpect(status().isBadRequest());
    }

    private String csv(String tipo) throws Exception {
        byte[] bytes = mvc().perform(get("/api/admin/reportes/csv?" + RANGO + "&tipo=" + tipo).with(admin()))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
            .andExpect(header().string("Content-Disposition",
                "attachment; filename=\"reporte-" + tipo + "-2021-03-08_2021-03-21.csv\""))
            .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOf(bytes, 3));
        return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
    }

    @Test
    void csvDePedidos() throws Exception {
        ConfiguracionNegocioVista negocio = configuracion.vista();
        String[] lineas = csv("pedidos").split("\r\n");
        assertEquals("Fecha de cobro;Hora;Pedido;Modalidad;Mesa o cliente;Productos;Total;Método de pago;Registró;Cobró",
            lineas[0]);
        assertEquals(7, lineas.length);
        assertTrue(lineas[1].startsWith("08/03/2021;09:00;" + ids.get("P7") + ";"));
        assertTrue(lineas[1].endsWith(";1 x Jugo;5,00;QR;Beto Paz;Ana Ruiz"));
        assertTrue(lineas[2].startsWith("08/03/2021;12:40;" + ids.get("P1") + ";" + negocio.getPuntoAtencionSingular() + ";"
            + negocio.getPuntoAtencionSingular() + " 3;"));
        assertTrue(lineas[2].endsWith(";25,00;EFECTIVO;Ana Ruiz;Caja Central"));
        assertTrue(lineas[3].contains(";\"Cliente; \"\"Raro\"\"\";"));
        assertTrue(lineas[3].contains(negocio.getEtiquetaParaLlevar()));
        assertTrue(lineas[3].contains("1 x Pollo (Grande)"));
        assertTrue(lineas[4].endsWith(";1 x Misterio;7,00;Sin especificar;Ana Ruiz;Sin dato"));
        assertTrue(lineas[5].startsWith("15/03/2021;00:15;" + ids.get("P4") + ";"));
        assertTrue(lineas[5].contains(";'=HYPERLINK(1);2 x Pollo (Mediano);36,00;TARJETA;"));
    }

    @Test
    void csvDeProductosYEmpleados() throws Exception {
        String[] productos = csv("productos").split("\r\n");
        assertEquals("Producto;Categoría;Unidades;Ingresos", productos[0]);
        assertEquals("Sopa;Platos rep;5;50,00", productos[1]);
        assertEquals("Misterio;Sin categoría;1;7,00", productos[5]);

        String[] empleados = csv("empleados").split("\r\n");
        assertEquals("Persona;Usuario;Ventas cobradas;Pedidos cobrados;Ticket promedio;Pedidos registrados;Cobros realizados",
            empleados[0]);
        assertEquals("Beto Paz;rep.beto;76,00;3;25,33;2;1", empleados[1]);
        assertEquals("Sin dato;Sin dato;0,00;0;0,00;0;1", empleados[4]);
    }

    @Test
    void neutralizaFormulasYEscapa() throws Exception {
        java.lang.reflect.Method celda = com.sistema.pedidosCori.service.ReporteService.class
            .getDeclaredMethod("celda", String.class);
        celda.setAccessible(true);
        assertEquals("'+1", celda.invoke(null, "+1"));
        assertEquals("'-2", celda.invoke(null, "-2"));
        assertEquals("'@SUM(A1)", celda.invoke(null, "@SUM(A1)"));
        assertEquals("\"'=1;2\"", celda.invoke(null, "=1;2"));
        assertEquals("\"a\"\"b\"", celda.invoke(null, "a\"b"));
        assertEquals("\"a\nb\"", celda.invoke(null, "a\nb"));
        assertEquals("normal", celda.invoke(null, "normal"));
    }

    @Test
    void soloAdminPuedeVerReportes() throws Exception {
        for (String rol : List.of("MESERO", "COCINA")) {
            mvc().perform(get("/api/admin/reportes?" + RANGO).with(user("x").roles(rol)))
                .andExpect(status().isForbidden());
            mvc().perform(get("/api/admin/reportes/csv?" + RANGO + "&tipo=pedidos").with(user("x").roles(rol)))
                .andExpect(status().isForbidden());
        }
    }
}
