package com.sistema.pedidosCori.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.IPedidoDao;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.entity.DetallePedido;
import com.sistema.pedidosCori.models.entity.EstadoPedido;
import com.sistema.pedidosCori.models.entity.Pedido;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ResumenService {
    public static final String SIN_DATO = "Sin dato";

    private final IPedidoDao pedidoDao;
    private final IPlatoDao platoDao;

    public record ProductoTop(String nombre, int cantidad) { }
    public record VentasEmpleado(String usuario, int pedidos, double ventas) { }
    public record ResumenHoy(String fecha, double ventasCobradas, int pedidosHoy, long pedidosActivos,
            double ticketPromedio, long productosActivosHoy, List<ProductoTop> topProductos,
            List<VentasEmpleado> porEmpleado) { }

    @Transactional(readOnly = true)
    public ResumenHoy hoy() {
        LocalDate hoy = LocalDate.now();
        List<Pedido> pedidos = pedidoDao.findByFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(
            hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
        Map<Long, Pedido> cobrados = new LinkedHashMap<>();
        pedidoDao.findByPagadoTrueAndFechaPagoGreaterThanEqualAndFechaPagoLessThan(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay())
            .forEach(p -> cobrados.put(p.getId(), p));
        pedidos.stream().filter(p -> Boolean.TRUE.equals(p.getPagado()) && p.getFechaPago() == null)
            .forEach(p -> cobrados.putIfAbsent(p.getId(), p));
        List<Pedido> pagados = new ArrayList<>(cobrados.values());
        double ventas = pagados.stream().mapToDouble(ResumenService::total).sum();
        double ticket = pagados.isEmpty() ? 0.0 : ventas / pagados.size();

        Map<String, int[]> unidades = new LinkedHashMap<>();
        Map<String, Object[]> empleados = new LinkedHashMap<>();
        for (Pedido p : pedidos) {
            if (p.getDetalles() != null) for (DetallePedido d : p.getDetalles()) {
                if (d.getPlato() == null) continue;
                String nombre = d.getPresentacion() != null
                    ? d.getPlato().getNombre() + " (" + d.getPresentacion().getNombre() + ")"
                    : d.getPlato().getNombre();
                unidades.computeIfAbsent(nombre, k -> new int[1])[0] += d.getCantidad() == null ? 0 : d.getCantidad();
            }
            String usuario = p.getRegistradoPor() == null || p.getRegistradoPor().isBlank() ? SIN_DATO : p.getRegistradoPor();
            Object[] acumulado = empleados.computeIfAbsent(usuario, k -> new Object[]{0, 0.0});
            acumulado[0] = (Integer) acumulado[0] + 1;
        }
        for (Pedido p : pagados) {
            String usuario = p.getRegistradoPor() == null || p.getRegistradoPor().isBlank() ? SIN_DATO : p.getRegistradoPor();
            Object[] acumulado = empleados.computeIfAbsent(usuario, k -> new Object[]{0, 0.0});
            acumulado[1] = (Double) acumulado[1] + total(p);
        }

        List<ProductoTop> top = unidades.entrySet().stream()
            .sorted(Map.Entry.<String, int[]>comparingByValue(Comparator.comparingInt(v -> -v[0]))
                .thenComparing(Map.Entry.comparingByKey()))
            .limit(5).map(e -> new ProductoTop(e.getKey(), e.getValue()[0])).toList();
        List<VentasEmpleado> porEmpleado = new ArrayList<>();
        empleados.forEach((usuario, v) -> porEmpleado.add(new VentasEmpleado(usuario, (Integer) v[0], redondear((Double) v[1]))));
        porEmpleado.sort(Comparator.comparingDouble(VentasEmpleado::ventas).reversed()
            .thenComparing(Comparator.comparingInt(VentasEmpleado::pedidos).reversed())
            .thenComparing(VentasEmpleado::usuario));

        return new ResumenHoy(hoy.toString(), redondear(ventas), pedidos.size(),
            pedidoDao.countByEstadoNotOrPagadoFalse(EstadoPedido.ENTREGADO), redondear(ticket),
            platoDao.countByDisponibleTrueAndArchivadoFalse(), top, porEmpleado);
    }

    private static double total(Pedido p) {
        return p.getTotal() == null ? 0.0 : p.getTotal();
    }

    private static double redondear(double valor) {
        return BigDecimal.valueOf(valor).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
