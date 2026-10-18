package com.sistema.pedidosCori;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sistema.pedidosCori.controller.ApiController;
import com.sistema.pedidosCori.models.entity.DetallePedido;
import com.sistema.pedidosCori.models.entity.Pedido;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.service.IPedidoService;

class ReporteDiarioTests {
    @Test
    void sameProductAtDifferentPricesSumsRecordedSubtotals() {
        IPedidoService service = mock(IPedidoService.class);
        Plato plato = Plato.builder().id(1L).nombre("Plato").precio(20.0).build();
        DetallePedido primero = DetallePedido.builder().plato(plato).cantidad(2)
            .precioUnitario(10.0).subtotal(20.0).build();
        DetallePedido segundo = DetallePedido.builder().plato(plato).cantidad(1)
            .precioUnitario(15.0).subtotal(15.0).build();
        Pedido pedido = Pedido.builder().fechaCreacion(LocalDateTime.of(2026, 10, 13, 12, 0))
            .pagado(true).detalles(List.of(primero, segundo)).build();
        when(service.obtenerTodosLosPagados()).thenReturn(List.of(pedido));
        ApiController controller = new ApiController(service, null, null, null, null, null, null, null, null, null);
        var reporte = controller.getReportesDiarios().getBody().getFirst();
        assertEquals(3, reporte.getItems().getFirst().getCantidad());
        assertEquals(35.0, reporte.getItems().getFirst().getSubtotal());
        assertEquals(35.0, reporte.getTotalVentas());
    }
}
