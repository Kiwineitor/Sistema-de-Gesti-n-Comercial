package com.sistema.pedidosCori;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.sistema.pedidosCori.models.dao.IPedidoDao;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dao.IPresentacionPlatoDao;
import com.sistema.pedidosCori.models.dto.PedidoRequestDTO;
import com.sistema.pedidosCori.models.entity.DetallePedido;
import com.sistema.pedidosCori.models.entity.EstadoPedido;
import com.sistema.pedidosCori.models.entity.Pedido;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.service.PedidoServiceImpl;
import com.sistema.pedidosCori.service.ConfiguracionNegocioService;
import com.sistema.pedidosCori.models.dto.ConfiguracionNegocioVista;

class PedidoServiceImplTests {
    private final IPedidoDao pedidos = mock(IPedidoDao.class);
    private final IPlatoDao platos = mock(IPlatoDao.class);
    private final IPresentacionPlatoDao presentaciones = mock(IPresentacionPlatoDao.class);
    private final ConfiguracionNegocioService configuracion = mock(ConfiguracionNegocioService.class);
    private final PedidoServiceImpl service = new PedidoServiceImpl(pedidos, platos, presentaciones,
        configuracion, mock(SimpMessagingTemplate.class), mock(com.sistema.pedidosCori.service.AuditoriaService.class));
    private Pedido pedido;

    @BeforeEach
    void setup() {
        pedido = Pedido.builder().id(1L).estado(EstadoPedido.PENDIENTE).pagado(false)
            .detalles(new ArrayList<>()).total(0.0).build();
        when(pedidos.findById(1L)).thenReturn(Optional.of(pedido));
        when(pedidos.findByIdForUpdate(1L)).thenReturn(Optional.of(pedido));
        when(pedidos.save(any(Pedido.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void transitionsMustFollowPreparationDeliveryOrder() {
        assertThrows(IllegalStateException.class, () -> service.actualizarEstado(1L, EstadoPedido.LISTO));
        service.actualizarEstado(1L, EstadoPedido.EN_PREPARACION);
        assertThrows(IllegalStateException.class, () -> service.actualizarEstado(1L, EstadoPedido.ENTREGADO));
        service.actualizarEstado(1L, EstadoPedido.LISTO);
        service.actualizarEstado(1L, EstadoPedido.ENTREGADO);
        assertThrows(IllegalStateException.class, () -> service.actualizarEstado(1L, EstadoPedido.PENDIENTE));
    }

    @Test
    void paymentCanHappenBeforeDeliveryButOnlyOnce() {
        pedido.setEstado(EstadoPedido.EN_PREPARACION);
        service.registrarPago(1L, "EFECTIVO");
        assertTrue(pedido.getPagado());
        assertNotNull(pedido.getFechaPago());
        assertEquals(EstadoPedido.EN_PREPARACION, pedido.getEstado());
        assertThrows(IllegalStateException.class, () -> service.registrarPago(1L, "QR"));
    }

    @Test
    void paidOrderRejectsAdditionalProducts() {
        pedido.setPagado(true);
        assertThrows(IllegalStateException.class, () -> service.agregarDetalles(1L, List.of(detalle())));
        verify(platos, never()).findById(any());
    }

    @Test
    void deliveredOrderReturnsToPendingAndPreservesPreparedBoundary() {
        pedido.setEstado(EstadoPedido.ENTREGADO);
        pedido.getDetalles().add(DetallePedido.builder().id(10L).pedido(pedido).subtotal(5.0).build());
        when(platos.findById(7L)).thenReturn(Optional.of(Plato.builder().id(7L).nombre("Plato")
            .precio(8.0).build()));
        Pedido resultado = service.agregarDetalles(1L, List.of(detalle()));
        assertEquals(EstadoPedido.PENDIENTE, resultado.getEstado());
        assertEquals(10L, resultado.getUltimoDetallePreparadoId());
        assertEquals(2, resultado.getDetalles().size());
        assertEquals(13.0, resultado.getTotal());
    }

    @Test
    void rejectsPointOutsideConfiguredRangeAndAllPointsWhenCountIsZero() {
        var config = mock(ConfiguracionNegocioVista.class);
        when(configuracion.vista()).thenReturn(config);
        when(config.getCantidadPuntosAtencion()).thenReturn(1, 0);
        var request = new PedidoRequestDTO();
        request.setTipo("MESA");
        request.setNumeroMesa(2);
        assertThrows(IllegalArgumentException.class, () -> service.crearPedido(request));
        request.setNumeroMesa(1);
        assertThrows(IllegalArgumentException.class, () -> service.crearPedido(request));
        verify(pedidos, never()).save(any());
    }

    @Test
    void detailKeepsQuantityAndTrimmedNotePerLine() {
        when(platos.findById(7L)).thenReturn(Optional.of(Plato.builder().id(7L).nombre("Silpancho").precio(18.0).build()));
        var d = detalle();
        d.setCantidad(2);
        d.setNotas("  sin cebolla  ");
        DetallePedido detalle = service.construirDetalle(pedido, d);
        assertEquals(2, detalle.getCantidad());
        assertEquals(36.0, detalle.getSubtotal());
        assertEquals("sin cebolla", detalle.getNotas());
        d.setNotas("   ");
        assertNull(service.construirDetalle(pedido, d).getNotas());
    }

    @Test
    void rejectsInvalidQuantityLongNoteAndForeignSize() {
        when(platos.findById(7L)).thenReturn(Optional.of(Plato.builder().id(7L).nombre("Silpancho").precio(18.0).build()));
        var d = detalle();
        for (Integer cantidad : new Integer[]{null, 0, -1, PedidoServiceImpl.CANTIDAD_MAXIMA + 1}) {
            d.setCantidad(cantidad);
            assertThrows(IllegalArgumentException.class, () -> service.construirDetalle(pedido, d));
        }
        d.setCantidad(1);
        d.setNotas("x".repeat(PedidoServiceImpl.NOTA_MAXIMA + 1));
        assertThrows(IllegalArgumentException.class, () -> service.construirDetalle(pedido, d));
        d.setNotas(null);
        Plato otro = Plato.builder().id(8L).nombre("Refresco").precio(3.0).build();
        when(presentaciones.findById(5L)).thenReturn(Optional.of(
            com.sistema.pedidosCori.models.entity.PresentacionPlato.builder().id(5L).nombre("Jarra").precio(12.0).plato(otro).build()));
        d.setPresentacionId(5L);
        assertThrows(IllegalArgumentException.class, () -> service.construirDetalle(pedido, d));
    }

    private PedidoRequestDTO.DetalleRequestDTO detalle() {
        var d = new PedidoRequestDTO.DetalleRequestDTO();
        d.setPlatoId(7L);
        d.setCantidad(1);
        return d;
    }
}
