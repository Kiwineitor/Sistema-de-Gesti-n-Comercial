package com.sistema.pedidosCori.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.IPedidoDao;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dao.IPresentacionPlatoDao;
import com.sistema.pedidosCori.models.dto.PedidoRequestDTO;
import com.sistema.pedidosCori.models.dto.ConfiguracionNegocioVista;
import com.sistema.pedidosCori.models.dto.StockUpdateDTO;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.DetallePedido;
import com.sistema.pedidosCori.models.entity.EstadoPedido;
import com.sistema.pedidosCori.models.entity.Pedido;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.models.entity.PresentacionPlato;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PedidoServiceImpl implements IPedidoService{

    public static final int CANTIDAD_MAXIMA = 99;
    public static final int NOTA_MAXIMA = 120;

    private final IPedidoDao            pedidoDao;
    private final IPlatoDao             platoDao;
    private final IPresentacionPlatoDao presentacionPlatoDao;
    private final ConfiguracionNegocioService configuracionNegocioService;

    private final SimpMessagingTemplate messagingTemplate;
    private final AuditoriaService auditoria;

    @Override
    @Transactional
    public Pedido crearPedido(PedidoRequestDTO dto) {
        if (dto == null || dto.getTipo() == null)
            throw new IllegalArgumentException("Selecciona una modalidad de pedido");
        if ("MESA".equals(dto.getTipo())) {
            int cantidad = configuracionNegocioService.vista().getCantidadPuntosAtencion();
            if (dto.getNumeroMesa() == null || dto.getNumeroMesa() < 1 || dto.getNumeroMesa() > cantidad)
                throw new IllegalArgumentException("El número de punto de atención debe estar entre 1 y " + cantidad);
        } else if (!"LLEVAR".equals(dto.getTipo())) {
            throw new IllegalArgumentException("Modalidad de pedido inválida");
        }
        Pedido pedido = Pedido.builder()
            .numeroMesa(dto.getNumeroMesa())
            .nombreCliente(dto.getNombreCliente())
            .tipo(dto.getTipo())
            .observaciones(dto.getObservaciones())
            .estado(EstadoPedido.PENDIENTE)
            .pagado(dto.getPagado() != null ? dto.getPagado() : false)
            .metodoPago(dto.getMetodoPago())
            .build();
 
        List<DetallePedido> detalles = dto.getDetalles().stream()
            .map(d -> construirDetalle(pedido, d))
            .collect(Collectors.toList());
 
        double total = detalles.stream().mapToDouble(DetallePedido::getSubtotal).sum();
        pedido.setDetalles(detalles);
        pedido.setTotal(total);
        pedido.setRegistradoPor(UsuarioActual.nombre().orElse(null));
        if (Boolean.TRUE.equals(pedido.getPagado())) {
            pedido.setCobradoPor(pedido.getRegistradoPor());
            pedido.setFechaPago(LocalDateTime.now());
        }
        Pedido guardado = pedidoDao.save(pedido);
        auditoria.registrar(AccionAuditoria.PEDIDO_CREADO, "Pedido", guardado.getId(),
            () -> describir(guardado) + " · " + productos(guardado.getDetalles()) + " · " + dinero(guardado.getTotal()));
        if (Boolean.TRUE.equals(guardado.getPagado())) registrarCobro(guardado);
        return guardado;
    }

    public DetallePedido construirDetalle(Pedido pedido, PedidoRequestDTO.DetalleRequestDTO d) {
        if (d == null || d.getPlatoId() == null)
            throw new IllegalArgumentException("Cada producto del pedido debe indicar el plato");
        if (d.getCantidad() == null || d.getCantidad() < 1 || d.getCantidad() > CANTIDAD_MAXIMA)
            throw new IllegalArgumentException("La cantidad debe estar entre 1 y " + CANTIDAD_MAXIMA);
        String notas = d.getNotas() == null ? null : d.getNotas().trim();
        if (notas != null && notas.length() > NOTA_MAXIMA)
            throw new IllegalArgumentException("La nota no puede superar " + NOTA_MAXIMA + " caracteres");
        if (notas != null && notas.isEmpty()) notas = null;

        Plato plato = platoDao.findById(d.getPlatoId())
            .filter(p -> !Boolean.TRUE.equals(p.getArchivado()))
            .orElseThrow(() -> new java.util.NoSuchElementException("Plato no encontrado: " + d.getPlatoId()));
 
        Plato acompSopa = null;
        if (d.getAcompananteSopaId() != null) {
            acompSopa = platoDao.findById(d.getAcompananteSopaId())
                .filter(p -> !Boolean.TRUE.equals(p.getArchivado()))
                .orElseThrow(() -> new java.util.NoSuchElementException(
                    "Acompañante no encontrado: " + d.getAcompananteSopaId()));
        }
 
        PresentacionPlato presentacion = null;
        if (d.getPresentacionId() != null) {
            presentacion = presentacionPlatoDao.findById(d.getPresentacionId())
                .filter(p -> !Boolean.TRUE.equals(p.getPlato().getArchivado()))
                .orElseThrow(() -> new java.util.NoSuchElementException(
                    "Presentación no encontrada: " + d.getPresentacionId()));
            if (!presentacion.getPlato().getId().equals(plato.getId()))
                throw new IllegalArgumentException("El tamaño elegido no corresponde a " + plato.getNombre());
        }
 
        Double precio = (presentacion != null) ? presentacion.getPrecio() : plato.getPrecio();
 
        return DetallePedido.builder()
            .pedido(pedido)
            .plato(plato)
            .cantidad(d.getCantidad())
            .precioUnitario(precio)
            .subtotal(precio * d.getCantidad())
            .notas(notas)
            .acompananteSopa(acompSopa)
            .presentacion(presentacion)
            .build();
    }
 
    @Override
    @Transactional
    public Pedido actualizarEstado(Long pedidoId, EstadoPedido nuevoEstado) {
        Pedido pedido = obtenerPorIdBloqueado(pedidoId);
        EstadoPedido actual = pedido.getEstado();
        boolean permitida = (actual == EstadoPedido.PENDIENTE && nuevoEstado == EstadoPedido.EN_PREPARACION)
            || (actual == EstadoPedido.EN_PREPARACION && nuevoEstado == EstadoPedido.LISTO)
            || (actual == EstadoPedido.LISTO && nuevoEstado == EstadoPedido.ENTREGADO);
        if (!permitida) {
            throw new IllegalStateException("Transición de pedido no permitida: " + actual + " → " + nuevoEstado);
        }
        if (nuevoEstado == EstadoPedido.LISTO) {
            pedido.setUltimoDetallePreparadoId(pedido.getDetalles().stream()
                .map(DetallePedido::getId).filter(id -> id != null).mapToLong(Long::longValue).max().orElse(0L));
        }
        pedido.setEstado(nuevoEstado);
        Pedido guardado = pedidoDao.save(pedido);
        auditoria.registrar(AccionAuditoria.PEDIDO_ESTADO, "Pedido", guardado.getId(),
            () -> describir(guardado) + " · " + actual + " → " + nuevoEstado);
        return guardado;
    }

    @Override
    @Transactional
    public Pedido agregarDetalles(Long id, List<PedidoRequestDTO.DetalleRequestDTO> detalles) {
        Pedido pedido = obtenerPorIdBloqueado(id);
        if (Boolean.TRUE.equals(pedido.getPagado())) {
            throw new IllegalStateException("No se pueden agregar productos a un pedido pagado");
        }
        if (detalles == null || detalles.isEmpty()) {
            throw new IllegalArgumentException("Agrega al menos un producto");
        }
        EstadoPedido estadoAnterior = pedido.getEstado();
        if (pedido.getEstado() != EstadoPedido.PENDIENTE) {
            long ultimoAnterior = pedido.getDetalles().stream().map(DetallePedido::getId)
                .filter(detalleId -> detalleId != null).mapToLong(Long::longValue).max().orElse(0L);
            pedido.setUltimoDetallePreparadoId(Math.max(
                pedido.getUltimoDetallePreparadoId() == null ? 0L : pedido.getUltimoDetallePreparadoId(), ultimoAnterior));
            pedido.setEstado(EstadoPedido.PENDIENTE);
        }
        List<DetallePedido> nuevos = detalles.stream().map(d -> construirDetalle(pedido, d)).toList();
        pedido.getDetalles().addAll(nuevos);
        pedido.setTotal(pedido.getDetalles().stream().mapToDouble(DetallePedido::getSubtotal).sum());
        Pedido guardado = pedidoDao.save(pedido);
        auditoria.registrar(AccionAuditoria.PEDIDO_ITEMS_AGREGADOS, "Pedido", guardado.getId(),
            () -> describir(guardado) + " · " + productos(nuevos) + " agregados · Total " + dinero(guardado.getTotal())
                + (estadoAnterior != EstadoPedido.PENDIENTE ? " · " + estadoAnterior + " → " + EstadoPedido.PENDIENTE : ""));
        return guardado;
    }
 
    @Override
    public List<Pedido> obtenerPedidosActivos() {
        return pedidoDao.findByEstadoNotOrPagadoFalseOrderByFechaCreacionAsc(EstadoPedido.ENTREGADO);
    }

    @Override
    public List<Pedido> obtenerPedidosCocina() {
        return pedidoDao.findByEstadoInOrderByFechaCreacionAsc(
            List.of(EstadoPedido.PENDIENTE, EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO));
    }
 
    @Override
    public List<Pedido> obtenerPedidosPorEstado(EstadoPedido estado) {
        return pedidoDao.findByEstadoOrderByFechaCreacionAsc(estado);
    }
 
    @Override
    public Pedido obtenerPorId(Long id) {
        return pedidoDao.findById(id)
            .orElseThrow(() -> new java.util.NoSuchElementException("Pedido no encontrado: " + id));
    }

    private Pedido obtenerPorIdBloqueado(Long id) {
        return pedidoDao.findByIdForUpdate(id)
            .orElseThrow(() -> new java.util.NoSuchElementException("Pedido no encontrado: " + id));
    }
 
    @Override
    @Transactional
    public Pedido registrarPago(Long id, String metodoPago) {
        Pedido pedido = obtenerPorIdBloqueado(id);
        if (Boolean.TRUE.equals(pedido.getPagado())) {
            throw new IllegalStateException("El pedido ya está pagado");
        }
        pedido.setPagado(true);
        pedido.setMetodoPago(metodoPago);
        pedido.setCobradoPor(UsuarioActual.nombre().orElse(null));
        pedido.setFechaPago(LocalDateTime.now());
        Pedido guardado = pedidoDao.save(pedido);
        registrarCobro(guardado);
        return guardado;
    }

    private void registrarCobro(Pedido pedido) {
        auditoria.registrar(AccionAuditoria.PEDIDO_COBRADO, "Pedido", pedido.getId(),
            () -> describir(pedido) + " · " + (pedido.getMetodoPago() == null || pedido.getMetodoPago().isBlank()
                ? "Sin método" : pedido.getMetodoPago()) + " · " + dinero(pedido.getTotal()));
    }

    private String describir(Pedido pedido) {
        ConfiguracionNegocioVista negocio = configuracionNegocioService.vista();
        if (pedido.getNumeroMesa() != null)
            return (negocio != null ? negocio.getPuntoAtencionSingular() : "Mesa") + " " + pedido.getNumeroMesa();
        String llevar = negocio != null ? negocio.getEtiquetaParaLlevar() : "Para llevar";
        String cliente = pedido.getNombreCliente();
        return cliente == null || cliente.isBlank() ? llevar : llevar + " · " + cliente.trim();
    }

    private String productos(List<DetallePedido> detalles) {
        int cantidad = detalles == null ? 0 : detalles.stream()
            .mapToInt(d -> d.getCantidad() == null ? 0 : d.getCantidad()).sum();
        return cantidad + (cantidad == 1 ? " producto" : " productos");
    }

    private String dinero(Double monto) {
        ConfiguracionNegocioVista negocio = configuracionNegocioService.vista();
        String simbolo = negocio != null ? negocio.getSimboloMoneda() : "Bs.";
        return simbolo + " " + String.format(Locale.ROOT, "%.2f", monto == null ? 0.0 : monto);
    }
 
    @Override
    public List<Pedido> obtenerTodosLosPagados() {
        return pedidoDao.findByPagadoTrue();
    }
}
