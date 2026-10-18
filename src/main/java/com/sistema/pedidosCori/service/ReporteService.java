package com.sistema.pedidosCori.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.ICategoriaDao;
import com.sistema.pedidosCori.models.dao.IPedidoDao;
import com.sistema.pedidosCori.models.dao.IUsuarioDao;
import com.sistema.pedidosCori.models.dto.ConfiguracionNegocioVista;
import com.sistema.pedidosCori.models.entity.Categoria;
import com.sistema.pedidosCori.models.entity.DetallePedido;
import com.sistema.pedidosCori.models.entity.Pedido;
import com.sistema.pedidosCori.models.entity.Usuario;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReporteService {
    public static final String SIN_DATO = ResumenService.SIN_DATO;
    public static final String SIN_CATEGORIA = "Sin categoría";
    public static final String SIN_METODO = "Sin especificar";
    public static final int MAX_DIAS = 366;

    private static final String[] DIAS_CORTOS = {"lun", "mar", "mié", "jue", "vie", "sáb", "dom"};
    private static final String[] DIAS_LARGOS = {"Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"};
    private static final String[] MESES = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};
    private static final DateTimeFormatter FECHA_CSV = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter HORA_CSV = DateTimeFormatter.ofPattern("HH:mm");

    private final IPedidoDao pedidoDao;
    private final IUsuarioDao usuarioDao;
    private final ICategoriaDao categoriaDao;
    private final ConfiguracionNegocioService configuracion;

    public record Rango(String desde, String hasta, int dias, String agrupar) { }
    public record RangoAnterior(String desde, String hasta) { }
    public record Metricas(double ventas, int pedidosCobrados, double ticketPromedio, int unidades, int pedidosRegistrados) { }
    public record Variacion(Double ventas, Double pedidosCobrados, Double ticketPromedio, Double unidades,
            Double pedidosRegistrados) { }
    public record Resumen(double ventas, int pedidosCobrados, double ticketPromedio, int unidades, int pedidosRegistrados,
            int porCobrarPedidos, double porCobrarMonto, Metricas anterior, Variacion variacion) { }
    public record PuntoSerie(String clave, String etiqueta, String desde, String hasta, double ventas, int pedidosCobrados) { }
    public record ProductoReporte(String nombre, String categoria, String icono, int unidades, double ingresos) { }
    public record CategoriaReporte(String codigo, String nombre, String icono, int unidades, double ingresos) { }
    public record EmpleadoReporte(String usuario, String nombre, double ventas, int pedidosCobrados, double ticketPromedio,
            int pedidosRegistrados, int cobros) { }
    public record MetodoPagoReporte(String metodo, int pedidos, double monto) { }
    public record HoraReporte(int hora, int pedidos, double ventas) { }
    public record DiaSemanaReporte(int dia, String nombre, int pedidos, double ventas) { }
    public record ModalidadReporte(String tipo, int pedidos, double ventas) { }
    public record Reporte(Rango rango, RangoAnterior anterior, Resumen resumen, List<PuntoSerie> serie,
            List<ProductoReporte> productos, List<CategoriaReporte> categorias, List<EmpleadoReporte> empleados,
            List<MetodoPagoReporte> metodosPago, List<HoraReporte> horas, List<DiaSemanaReporte> diasSemana,
            List<ModalidadReporte> modalidades) { }
    public record ArchivoCsv(String nombre, byte[] contenido) { }

    private record Periodo(LocalDate desde, LocalDate hasta) {
        LocalDateTime inicio() { return desde.atStartOfDay(); }
        LocalDateTime fin() { return hasta.plusDays(1).atStartOfDay(); }
        int dias() { return (int) ChronoUnit.DAYS.between(desde, hasta) + 1; }
        Periodo anterior() { return new Periodo(desde.minusDays(dias()), desde.minusDays(1)); }
    }

    private record Registrado(String usuario, boolean pagado, BigDecimal total) { }

    private static final class Acumulado {
        int unidades;
        int pedidos;
        BigDecimal monto = BigDecimal.ZERO;
    }

    private static final class Persona {
        BigDecimal ventas = BigDecimal.ZERO;
        int pedidosCobrados;
        int pedidosRegistrados;
        int cobros;
    }

    private record CategoriaInfo(String codigo, String nombre, String icono) { }

    @Transactional(readOnly = true)
    public Reporte generar(LocalDate desde, LocalDate hasta, String agrupar) {
        Periodo periodo = validar(desde, hasta);
        String agrupacion = resolverAgrupacion(agrupar, periodo.dias());
        Periodo previo = periodo.anterior();

        List<Pedido> cobrados = cobrados(periodo);
        List<Registrado> registrados = registrados(periodo);
        List<Pedido> cobradosPrevios = cobrados(previo);
        List<Registrado> registradosPrevios = registrados(previo);

        Metricas actual = metricas(cobrados, registrados);
        Metricas anterior = metricas(cobradosPrevios, registradosPrevios);
        Variacion variacion = new Variacion(variacion(actual.ventas(), anterior.ventas()),
            variacion(actual.pedidosCobrados(), anterior.pedidosCobrados()),
            variacion(actual.ticketPromedio(), anterior.ticketPromedio()),
            variacion(actual.unidades(), anterior.unidades()),
            variacion(actual.pedidosRegistrados(), anterior.pedidosRegistrados()));
        List<Registrado> pendientes = registrados.stream().filter(r -> !r.pagado()).toList();
        BigDecimal porCobrar = pendientes.stream().map(Registrado::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        Resumen resumen = new Resumen(actual.ventas(), actual.pedidosCobrados(), actual.ticketPromedio(), actual.unidades(),
            actual.pedidosRegistrados(), pendientes.size(), dinero(porCobrar), anterior, variacion);

        Map<String, CategoriaInfo> categorias = categorias();
        Map<String, String> nombres = nombresUsuarios();
        return new Reporte(
            new Rango(periodo.desde().toString(), periodo.hasta().toString(), periodo.dias(), agrupacion),
            new RangoAnterior(previo.desde().toString(), previo.hasta().toString()),
            resumen,
            serie(periodo, agrupacion, cobrados),
            productos(cobrados, categorias),
            categoriasReporte(cobrados, categorias),
            empleados(cobrados, registrados, nombres),
            metodosPago(cobrados),
            horas(cobrados),
            diasSemana(cobrados),
            modalidades(cobrados));
    }

    @Transactional(readOnly = true)
    public ArchivoCsv csv(LocalDate desde, LocalDate hasta, String tipo) {
        Periodo periodo = validar(desde, hasta);
        String clave = tipo == null ? "" : tipo.trim().toLowerCase(Locale.ROOT);
        List<List<String>> filas = new ArrayList<>();
        switch (clave) {
            case "pedidos" -> filasPedidos(periodo, filas);
            case "productos" -> {
                filas.add(texto("Producto", "Categoría", "Unidades", "Ingresos"));
                for (ProductoReporte p : productos(cobrados(periodo), categorias()))
                    filas.add(List.of(celda(p.nombre()), celda(p.categoria()), String.valueOf(p.unidades()), decimal(p.ingresos())));
            }
            case "empleados" -> {
                filas.add(texto("Persona", "Usuario", "Ventas cobradas", "Pedidos cobrados", "Ticket promedio",
                    "Pedidos registrados", "Cobros realizados"));
                for (EmpleadoReporte e : empleados(cobrados(periodo), registrados(periodo), nombresUsuarios()))
                    filas.add(List.of(celda(e.nombre()), celda(e.usuario()), decimal(e.ventas()),
                        String.valueOf(e.pedidosCobrados()), decimal(e.ticketPromedio()),
                        String.valueOf(e.pedidosRegistrados()), String.valueOf(e.cobros())));
            }
            default -> throw new IllegalArgumentException("Tipo de reporte no válido. Usa pedidos, productos o empleados");
        }
        StringBuilder sb = new StringBuilder("﻿");
        for (List<String> fila : filas) sb.append(String.join(";", fila)).append("\r\n");
        String nombre = "reporte-" + clave + "-" + periodo.desde() + "_" + periodo.hasta() + ".csv";
        return new ArchivoCsv(nombre, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void filasPedidos(Periodo periodo, List<List<String>> filas) {
        filas.add(texto("Fecha de cobro", "Hora", "Pedido", "Modalidad", "Mesa o cliente", "Productos", "Total",
            "Método de pago", "Registró", "Cobró"));
        ConfiguracionNegocioVista negocio = configuracion.vista();
        Map<String, String> nombres = nombresUsuarios();
        List<Pedido> pedidos = new ArrayList<>(cobrados(periodo));
        pedidos.sort(Comparator.comparing(ReporteService::momentoCobro).thenComparing(Pedido::getId));
        for (Pedido p : pedidos) {
            LocalDateTime momento = momentoCobro(p);
            boolean llevar = "LLEVAR".equals(modalidad(p));
            String modalidad = llevar ? negocio.getEtiquetaParaLlevar() : negocio.getPuntoAtencionSingular();
            String destino = llevar
                ? (vacio(p.getNombreCliente()) ? SIN_DATO : p.getNombreCliente().trim())
                : (p.getNumeroMesa() == null ? SIN_DATO : negocio.getPuntoAtencionSingular() + " " + p.getNumeroMesa());
            String productos = p.getDetalles() == null ? "" : p.getDetalles().stream()
                .map(d -> (d.getCantidad() == null ? 0 : d.getCantidad()) + " x " + nombreProducto(d))
                .collect(Collectors.joining(", "));
            filas.add(List.of(momento.format(FECHA_CSV), momento.format(HORA_CSV), String.valueOf(p.getId()),
                celda(modalidad), celda(destino), celda(productos), decimal(dinero(total(p))),
                celda(metodo(p)), celda(nombrePersona(p.getRegistradoPor(), nombres)),
                celda(nombrePersona(p.getCobradoPor(), nombres))));
        }
    }

    private Periodo validar(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) throw new IllegalArgumentException("Indica las fechas desde y hasta");
        if (desde.isAfter(hasta)) throw new IllegalArgumentException("La fecha desde no puede ser posterior a la fecha hasta");
        Periodo periodo = new Periodo(desde, hasta);
        if (periodo.dias() > MAX_DIAS)
            throw new IllegalArgumentException("El rango no puede superar " + MAX_DIAS + " días");
        return periodo;
    }

    private static String resolverAgrupacion(String agrupar, int dias) {
        String valor = agrupar == null || agrupar.isBlank() ? "auto" : agrupar.trim().toLowerCase(Locale.ROOT);
        return switch (valor) {
            case "auto" -> dias <= 31 ? "dia" : dias <= 120 ? "semana" : "mes";
            case "dia", "semana", "mes" -> valor;
            default -> throw new IllegalArgumentException("Agrupación no válida. Usa auto, dia, semana o mes");
        };
    }

    private List<Pedido> cobrados(Periodo periodo) {
        return pedidoDao.findCobradosEntre(periodo.inicio(), periodo.fin());
    }

    private List<Registrado> registrados(Periodo periodo) {
        return pedidoDao.findRegistradosEntre(periodo.inicio(), periodo.fin()).stream()
            .map(f -> new Registrado((String) f[0], Boolean.TRUE.equals(f[1]),
                f[2] == null ? BigDecimal.ZERO : BigDecimal.valueOf(((Number) f[2]).doubleValue())))
            .toList();
    }

    private static Metricas metricas(List<Pedido> cobrados, List<Registrado> registrados) {
        BigDecimal ventas = cobrados.stream().map(ReporteService::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        int unidades = cobrados.stream().filter(p -> p.getDetalles() != null).flatMap(p -> p.getDetalles().stream())
            .mapToInt(d -> d.getCantidad() == null ? 0 : d.getCantidad()).sum();
        return new Metricas(dinero(ventas), cobrados.size(), promedio(ventas, cobrados.size()), unidades, registrados.size());
    }

    private static Double variacion(double actual, double anterior) {
        if (anterior == 0) return null;
        return BigDecimal.valueOf((actual - anterior) * 100.0 / anterior).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static List<PuntoSerie> serie(Periodo periodo, String agrupacion, List<Pedido> cobrados) {
        Map<LocalDate, Acumulado> porDia = new HashMap<>();
        for (Pedido p : cobrados) {
            Acumulado a = porDia.computeIfAbsent(momentoCobro(p).toLocalDate(), k -> new Acumulado());
            a.pedidos++;
            a.monto = a.monto.add(total(p));
        }
        List<PuntoSerie> serie = new ArrayList<>();
        LocalDate cursor = periodo.desde();
        while (!cursor.isAfter(periodo.hasta())) {
            LocalDate inicio;
            LocalDate fin;
            String clave;
            String etiqueta;
            switch (agrupacion) {
                case "semana" -> {
                    inicio = cursor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                    fin = inicio.plusDays(6);
                    clave = inicio.toString();
                    etiqueta = "Sem. " + inicio.getDayOfMonth() + " " + MESES[inicio.getMonthValue() - 1];
                }
                case "mes" -> {
                    inicio = cursor.withDayOfMonth(1);
                    fin = cursor.with(TemporalAdjusters.lastDayOfMonth());
                    clave = inicio.toString().substring(0, 7);
                    etiqueta = MESES[inicio.getMonthValue() - 1] + " " + inicio.getYear();
                }
                default -> {
                    inicio = cursor;
                    fin = cursor;
                    clave = cursor.toString();
                    etiqueta = DIAS_CORTOS[cursor.getDayOfWeek().getValue() - 1] + " " + cursor.getDayOfMonth();
                }
            }
            LocalDate desde = inicio.isBefore(periodo.desde()) ? periodo.desde() : inicio;
            LocalDate hasta = fin.isAfter(periodo.hasta()) ? periodo.hasta() : fin;
            BigDecimal monto = BigDecimal.ZERO;
            int pedidos = 0;
            for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
                Acumulado a = porDia.get(d);
                if (a == null) continue;
                monto = monto.add(a.monto);
                pedidos += a.pedidos;
            }
            serie.add(new PuntoSerie(clave, etiqueta, desde.toString(), hasta.toString(), dinero(monto), pedidos));
            cursor = hasta.plusDays(1);
        }
        return serie;
    }

    private static List<ProductoReporte> productos(List<Pedido> cobrados, Map<String, CategoriaInfo> categorias) {
        Map<String, Acumulado> acumulados = new LinkedHashMap<>();
        Map<String, CategoriaInfo> categoriaDe = new HashMap<>();
        for (DetallePedido d : detalles(cobrados)) {
            String nombre = nombreProducto(d);
            Acumulado a = acumulados.computeIfAbsent(nombre, k -> new Acumulado());
            a.unidades += d.getCantidad() == null ? 0 : d.getCantidad();
            a.monto = a.monto.add(subtotal(d));
            categoriaDe.putIfAbsent(nombre, categoria(d, categorias));
        }
        return acumulados.entrySet().stream()
            .map(e -> new ProductoReporte(e.getKey(), categoriaDe.get(e.getKey()).nombre(), categoriaDe.get(e.getKey()).icono(),
                e.getValue().unidades, dinero(e.getValue().monto)))
            .sorted(Comparator.comparingInt(ProductoReporte::unidades).reversed()
                .thenComparing(Comparator.comparingDouble(ProductoReporte::ingresos).reversed())
                .thenComparing(ProductoReporte::nombre))
            .toList();
    }

    private static List<CategoriaReporte> categoriasReporte(List<Pedido> cobrados, Map<String, CategoriaInfo> categorias) {
        Map<CategoriaInfo, Acumulado> acumulados = new LinkedHashMap<>();
        for (DetallePedido d : detalles(cobrados)) {
            Acumulado a = acumulados.computeIfAbsent(categoria(d, categorias), k -> new Acumulado());
            a.unidades += d.getCantidad() == null ? 0 : d.getCantidad();
            a.monto = a.monto.add(subtotal(d));
        }
        return acumulados.entrySet().stream()
            .map(e -> new CategoriaReporte(e.getKey().codigo(), e.getKey().nombre(), e.getKey().icono(),
                e.getValue().unidades, dinero(e.getValue().monto)))
            .sorted(Comparator.comparingDouble(CategoriaReporte::ingresos).reversed()
                .thenComparing(Comparator.comparingInt(CategoriaReporte::unidades).reversed())
                .thenComparing(CategoriaReporte::nombre))
            .toList();
    }

    private static List<EmpleadoReporte> empleados(List<Pedido> cobrados, List<Registrado> registrados,
            Map<String, String> nombres) {
        Map<String, Persona> personas = new LinkedHashMap<>();
        for (Registrado r : registrados) personas.computeIfAbsent(usuario(r.usuario()), k -> new Persona()).pedidosRegistrados++;
        for (Pedido p : cobrados) {
            Persona vendedor = personas.computeIfAbsent(usuario(p.getRegistradoPor()), k -> new Persona());
            vendedor.pedidosCobrados++;
            vendedor.ventas = vendedor.ventas.add(total(p));
            personas.computeIfAbsent(usuario(p.getCobradoPor()), k -> new Persona()).cobros++;
        }
        return personas.entrySet().stream()
            .map(e -> new EmpleadoReporte(e.getKey(), nombrePersona(e.getKey(), nombres), dinero(e.getValue().ventas),
                e.getValue().pedidosCobrados, promedio(e.getValue().ventas, e.getValue().pedidosCobrados),
                e.getValue().pedidosRegistrados, e.getValue().cobros))
            .sorted(Comparator.comparingDouble(EmpleadoReporte::ventas).reversed()
                .thenComparing(Comparator.comparingInt(EmpleadoReporte::pedidosRegistrados).reversed())
                .thenComparing(EmpleadoReporte::nombre))
            .toList();
    }

    private static List<MetodoPagoReporte> metodosPago(List<Pedido> cobrados) {
        Map<String, Acumulado> acumulados = agrupar(cobrados, ReporteService::metodo);
        return acumulados.entrySet().stream()
            .map(e -> new MetodoPagoReporte(e.getKey(), e.getValue().pedidos, dinero(e.getValue().monto)))
            .sorted(Comparator.comparingDouble(MetodoPagoReporte::monto).reversed()
                .thenComparing(Comparator.comparingInt(MetodoPagoReporte::pedidos).reversed())
                .thenComparing(MetodoPagoReporte::metodo))
            .toList();
    }

    private static List<HoraReporte> horas(List<Pedido> cobrados) {
        Map<Integer, Acumulado> acumulados = agrupar(cobrados, p -> momentoCobro(p).getHour());
        List<HoraReporte> horas = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            Acumulado a = acumulados.getOrDefault(h, new Acumulado());
            horas.add(new HoraReporte(h, a.pedidos, dinero(a.monto)));
        }
        return horas;
    }

    private static List<DiaSemanaReporte> diasSemana(List<Pedido> cobrados) {
        Map<Integer, Acumulado> acumulados = agrupar(cobrados, p -> momentoCobro(p).getDayOfWeek().getValue());
        List<DiaSemanaReporte> dias = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            Acumulado a = acumulados.getOrDefault(d, new Acumulado());
            dias.add(new DiaSemanaReporte(d, DIAS_LARGOS[d - 1], a.pedidos, dinero(a.monto)));
        }
        return dias;
    }

    private static List<ModalidadReporte> modalidades(List<Pedido> cobrados) {
        Map<String, Acumulado> acumulados = agrupar(cobrados, ReporteService::modalidad);
        return List.of("MESA", "LLEVAR").stream().map(t -> {
            Acumulado a = acumulados.getOrDefault(t, new Acumulado());
            return new ModalidadReporte(t, a.pedidos, dinero(a.monto));
        }).toList();
    }

    private static <K> Map<K, Acumulado> agrupar(List<Pedido> pedidos, Function<Pedido, K> clave) {
        Map<K, Acumulado> acumulados = new LinkedHashMap<>();
        for (Pedido p : pedidos) {
            Acumulado a = acumulados.computeIfAbsent(clave.apply(p), k -> new Acumulado());
            a.pedidos++;
            a.monto = a.monto.add(total(p));
        }
        return acumulados;
    }

    private Map<String, CategoriaInfo> categorias() {
        Map<String, CategoriaInfo> mapa = new HashMap<>();
        for (Categoria c : categoriaDao.findAll())
            if (c.getCodigo() != null)
                mapa.put(c.getCodigo().toLowerCase(Locale.ROOT), new CategoriaInfo(c.getCodigo(), c.getNombre(), c.getIcono()));
        return mapa;
    }

    private Map<String, String> nombresUsuarios() {
        Map<String, String> mapa = new HashMap<>();
        for (Usuario u : usuarioDao.findAll())
            mapa.put(u.getUsername(), vacio(u.getNombreCompleto()) ? u.getUsername() : u.getNombreCompleto().trim());
        return mapa;
    }

    private static List<DetallePedido> detalles(List<Pedido> pedidos) {
        return pedidos.stream().filter(p -> p.getDetalles() != null).flatMap(p -> p.getDetalles().stream()).toList();
    }

    private static CategoriaInfo categoria(DetallePedido d, Map<String, CategoriaInfo> categorias) {
        String codigo = d.getPlato() == null ? null : d.getPlato().getCategoria();
        CategoriaInfo info = codigo == null ? null : categorias.get(codigo.trim().toLowerCase(Locale.ROOT));
        return info != null ? info : new CategoriaInfo(null, SIN_CATEGORIA, CategoriaService.ICONO_GENERICO);
    }

    private static String nombreProducto(DetallePedido d) {
        if (d.getPlato() == null) return SIN_DATO;
        return d.getPresentacion() != null
            ? d.getPlato().getNombre() + " (" + d.getPresentacion().getNombre() + ")"
            : d.getPlato().getNombre();
    }

    private static String modalidad(Pedido p) {
        if ("LLEVAR".equalsIgnoreCase(p.getTipo())) return "LLEVAR";
        if ("MESA".equalsIgnoreCase(p.getTipo())) return "MESA";
        return p.getNumeroMesa() != null ? "MESA" : "LLEVAR";
    }

    private static String metodo(Pedido p) {
        return vacio(p.getMetodoPago()) ? SIN_METODO : p.getMetodoPago().trim();
    }

    private static String usuario(String usuario) {
        return vacio(usuario) ? SIN_DATO : usuario.trim();
    }

    private static String nombrePersona(String usuario, Map<String, String> nombres) {
        if (vacio(usuario)) return SIN_DATO;
        return nombres.getOrDefault(usuario.trim(), usuario.trim());
    }

    private static LocalDateTime momentoCobro(Pedido p) {
        return p.getFechaPago() != null ? p.getFechaPago() : p.getFechaCreacion();
    }

    private static BigDecimal total(Pedido p) {
        return p.getTotal() == null ? BigDecimal.ZERO : BigDecimal.valueOf(p.getTotal());
    }

    private static BigDecimal subtotal(DetallePedido d) {
        return d.getSubtotal() == null ? BigDecimal.ZERO : BigDecimal.valueOf(d.getSubtotal());
    }

    private static boolean vacio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static double dinero(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static double promedio(BigDecimal total, int cantidad) {
        return cantidad == 0 ? 0.0 : total.divide(BigDecimal.valueOf(cantidad), 2, RoundingMode.HALF_UP).doubleValue();
    }

    private static String decimal(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }

    private static List<String> texto(String... valores) {
        List<String> celdas = new ArrayList<>();
        for (String v : valores) celdas.add(celda(v));
        return celdas;
    }

    static String celda(String valor) {
        String v = valor == null ? "" : valor;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        if (v.contains(";") || v.contains("\"") || v.contains("\n") || v.contains("\r"))
            return "\"" + v.replace("\"", "\"\"") + "\"";
        return v;
    }
}
