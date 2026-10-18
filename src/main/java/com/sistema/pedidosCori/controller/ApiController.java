package com.sistema.pedidosCori.controller;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistema.pedidosCori.models.dao.IPedidoDao;
import com.sistema.pedidosCori.models.dao.IDetallePedidoDao;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dao.IPresentacionPlatoDao;
import com.sistema.pedidosCori.models.dto.MenuDiarioItemDTO;
import com.sistema.pedidosCori.models.dto.NotificacionWsDTO;
import com.sistema.pedidosCori.models.dto.PedidoRequestDTO;
import com.sistema.pedidosCori.models.dto.PlatoEdicionRequest;
import com.sistema.pedidosCori.models.dto.PresentacionRequestDTO;
import com.sistema.pedidosCori.models.dto.ReporteDiarioDTO;
import com.sistema.pedidosCori.models.dto.StockUpdateDTO;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.DetallePedido;
import com.sistema.pedidosCori.models.entity.EstadoPedido;
import com.sistema.pedidosCori.models.entity.Modalidad;
import com.sistema.pedidosCori.models.entity.Pedido;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.models.entity.PresentacionPlato;
import com.sistema.pedidosCori.service.AuditoriaService;
import com.sistema.pedidosCori.service.CategoriaService;
import com.sistema.pedidosCori.service.PlatoImagenService;
import com.sistema.pedidosCori.service.ConfiguracionNegocioService;
import com.sistema.pedidosCori.service.IPedidoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {
    
    private final IPedidoService        pedidoService;
    private final IPedidoDao            pedidoDao;
    private final IDetallePedidoDao     detallePedidoDao;
    private final IPlatoDao             platoDao;
    private final IPresentacionPlatoDao presentacionPlatoDao;
    private final SimpMessagingTemplate messagingTemplate;
    private final ConfiguracionNegocioService configuracionNegocioService;
    private final AuditoriaService auditoria;
    private final CategoriaService categoriaService;
    private final PlatoImagenService platoImagenService;

 
    @GetMapping("/platos/todos")
    public ResponseEntity<List<Plato>> getTodosLosPlatos() {
        return ResponseEntity.ok(platoDao.findByArchivadoFalse());
    }
 
    @GetMapping("/platos")
    public ResponseEntity<List<Plato>> getPlatos() {
        return ResponseEntity.ok(platoDao.findByDisponibleTrueAndArchivadoFalse());
    }
 
    @PostMapping("/platos")
    public ResponseEntity<Plato> crearPlato(@RequestBody Plato plato) {
        plato.setCategoria(categoriaService.codigoValido(plato.getCategoria()));
        plato.setId(null);
        plato.setImagenVersion(null);
        plato.setDescripcion(descripcionValida(plato.getDescripcion()));
        if (plato.getDisponible() == null) plato.setDisponible(true);
        plato.setArchivado(false);
        Plato guardado = platoDao.save(plato);
        auditoria.registrar(AccionAuditoria.PLATO_CREADO, "Plato", guardado.getId(), () -> describirPlato(guardado));
        return ResponseEntity.ok(guardado);
    }
 
    @PutMapping("/platos/{id}")
    public ResponseEntity<Plato> editarPlato(@PathVariable Long id, @RequestBody PlatoEdicionRequest d) {
        return platoDao.findById(id).filter(p -> !Boolean.TRUE.equals(p.getArchivado())).map(p -> {
            String categoria = categoriaService.codigoValido(d.getCategoria());
            String descripcion = d.isDescripcionPresente() ? descripcionValida(d.getDescripcion()) : p.getDescripcion();
            p.setNombre(d.getNombre()); p.setPrecio(d.getPrecio()); p.setCategoria(categoria);
            p.setDescripcion(descripcion);
            if (Boolean.TRUE.equals(d.getSinLimite())) p.setStockDisponible(null);
            else if (d.isStockPresente()) p.setStockDisponible(d.getStockDisponible());
            Plato guardado = platoDao.save(p);
            auditoria.registrar(AccionAuditoria.PLATO_EDITADO, "Plato", guardado.getId(), () -> describirPlato(guardado));
            return ResponseEntity.ok(guardado);
        }).orElseThrow(() -> new java.util.NoSuchElementException("Plato no encontrado: " + id));
    }

    @DeleteMapping("/platos/{id}")
    @Transactional
    public ResponseEntity<Void> eliminarPlato(@PathVariable Long id) {
        Plato plato = platoDao.findById(id)
            .orElseThrow(() -> new java.util.NoSuchElementException("Plato no encontrado: " + id));
        if (detallePedidoDao.existsByPlatoIdOrAcompananteSopaId(id, id)) {
            plato.setArchivado(true);
            plato.setDisponible(false);
            platoDao.save(plato);
            auditoria.registrar(AccionAuditoria.PLATO_ARCHIVADO, "Plato", id,
                () -> plato.getNombre() + " · Se archivó porque tiene pedidos registrados");
        } else {
            platoImagenService.borrarDatos(id);
            platoDao.delete(plato);
            auditoria.registrar(AccionAuditoria.PLATO_ELIMINADO, "Plato", id, () -> describirPlato(plato));
        }
        messagingTemplate.convertAndSend("/topic/menu",
            NotificacionWsDTO.builder().tipo("ACTUALIZACION_MENU").mensaje("Plato eliminado").build());
        return ResponseEntity.noContent().build();
    }
 
 
        @GetMapping("/platos/{id}/presentaciones")
    public ResponseEntity<List<PresentacionPlato>> getPresentaciones(
            @PathVariable Long id,
            @RequestParam(required = false) String tipo) {

        if (platoDao.findById(id).filter(p -> !Boolean.TRUE.equals(p.getArchivado())).isEmpty()) {
            throw new java.util.NoSuchElementException("Plato no encontrado: " + id);
        }
 
        if (tipo != null) {
            Modalidad mod;
            try { mod = Modalidad.valueOf(tipo.toUpperCase()); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("Modalidad no válida"); }
 
            List<Modalidad> filtro = List.of(mod, Modalidad.AMBAS);
            return ResponseEntity.ok(
                presentacionPlatoDao.findByPlatoIdAndDisponibleTrueAndModalidadIn(id, filtro)
            );
        }
 
        return ResponseEntity.ok(presentacionPlatoDao.findByPlatoIdOrderByPrecioAsc(id));
    }
 
    @PostMapping("/platos/{id}/presentaciones")
    public ResponseEntity<PresentacionPlato> crearPresentacion(
            @PathVariable Long id, @RequestBody PresentacionRequestDTO dto) {
        return platoDao.findById(id).filter(plato -> !Boolean.TRUE.equals(plato.getArchivado())).map(plato -> {
            PresentacionPlato pres = PresentacionPlato.builder()
                .plato(plato)
                .nombre(dto.getNombre())
                .precio(dto.getPrecio())
                .stockDisponible(dto.getStockDisponible())
                .disponible(dto.getDisponible() != null ? dto.getDisponible() : true)
                .modalidad(dto.getModalidad() != null ? dto.getModalidad() : Modalidad.AMBAS)
                .build();
            PresentacionPlato saved = presentacionPlatoDao.save(pres);
            auditoria.registrar(AccionAuditoria.PRESENTACION_CREADA, "PresentacionPlato", saved.getId(),
                () -> describirPresentacion(saved));
            messagingTemplate.convertAndSend("/topic/menu",
                NotificacionWsDTO.builder().tipo("ACTUALIZACION_MENU").build());
            return ResponseEntity.ok(saved);
        }).orElseThrow(() -> new java.util.NoSuchElementException("Plato no encontrado: " + id));
    }
 
    @PutMapping("/presentaciones/{id}")
    public ResponseEntity<PresentacionPlato> editarPresentacion(
            @PathVariable Long id, @RequestBody PresentacionRequestDTO dto) {
        return presentacionPlatoDao.findById(id).map(p -> {
            if (dto.getNombre()       != null) p.setNombre(dto.getNombre());
            if (dto.getPrecio()       != null) p.setPrecio(dto.getPrecio());
            if (dto.getStockDisponible() != null) p.setStockDisponible(dto.getStockDisponible());
            if (dto.getDisponible()   != null) p.setDisponible(dto.getDisponible());
            if (dto.getModalidad()    != null) p.setModalidad(dto.getModalidad());
            PresentacionPlato guardada = presentacionPlatoDao.save(p);
            auditoria.registrar(AccionAuditoria.PRESENTACION_EDITADA, "PresentacionPlato", guardada.getId(),
                () -> describirPresentacion(guardada));
            return ResponseEntity.ok(guardada);
        }).orElseThrow(() -> new java.util.NoSuchElementException("Presentación no encontrada: " + id));
    }
 
    @DeleteMapping("/presentaciones/{id}")
    public ResponseEntity<Void> eliminarPresentacion(@PathVariable Long id) {
        String descripcion = presentacionPlatoDao.findById(id).map(this::describirPresentacion).orElse(null);
        presentacionPlatoDao.deleteById(id);
        if (descripcion != null)
            auditoria.registrar(AccionAuditoria.PRESENTACION_ELIMINADA, "PresentacionPlato", id, descripcion);
        messagingTemplate.convertAndSend("/topic/menu",
            NotificacionWsDTO.builder().tipo("ACTUALIZACION_MENU").mensaje("Presentacion eliminada").build());
        return ResponseEntity.ok().build();
    }
 
 
    @PostMapping("/pedidos")
    @Transactional
    public ResponseEntity<Pedido> crearPedido(@RequestBody PedidoRequestDTO dto) {
        Pedido pedido = pedidoService.crearPedido(dto);
 
        Map<Long,Integer> descPlato=new HashMap<>(), descPres=new HashMap<>(), descSopa=new HashMap<>();
        pedido.getDetalles().forEach(d -> {
            if (d.getPresentacion() != null) descPres.merge(d.getPresentacion().getId(), d.getCantidad(), Integer::sum);
            else descPlato.merge(d.getPlato().getId(), d.getCantidad(), Integer::sum);
            if (d.getAcompananteSopa() != null) descSopa.merge(d.getAcompananteSopa().getId(), d.getCantidad(), Integer::sum);
        });
        aplicarDescuentoStock(descPlato, descPres, descSopa);
 
        NotificacionWsDTO notif = NotificacionWsDTO.builder()
            .pedidoId(pedido.getId()).numeroMesa(pedido.getNumeroMesa())
            .nombreCliente(pedido.getNombreCliente()).estado(EstadoPedido.PENDIENTE)
            .mensaje("Nuevo pedido — " + labelPedido(pedido)).tipo("NUEVO_PEDIDO").build();
        messagingTemplate.convertAndSend("/topic/cocina", notif);
        messagingTemplate.convertAndSend("/topic/mesero", notif);
        return ResponseEntity.ok(pedido);
    }
 
 
    @PostMapping("/pedidos/{id}/detalles")
    @Transactional
    public ResponseEntity<Pedido> agregarDetalles(
            @PathVariable Long id,
            @RequestBody List<PedidoRequestDTO.DetalleRequestDTO> nuevosDetalles) {
 
        Pedido saved = pedidoService.agregarDetalles(id, nuevosDetalles);
 
        Map<Long,Integer> descPlato=new HashMap<>(), descPres=new HashMap<>(), descSopa=new HashMap<>();
        nuevosDetalles.forEach(d -> {
            if (d.getPresentacionId() != null) descPres.merge(d.getPresentacionId(), d.getCantidad(), Integer::sum);
            else descPlato.merge(d.getPlatoId(), d.getCantidad(), Integer::sum);
            if (d.getAcompananteSopaId() != null) descSopa.merge(d.getAcompananteSopaId(), d.getCantidad(), Integer::sum);
        });
        aplicarDescuentoStock(descPlato, descPres, descSopa);
 
        NotificacionWsDTO notif = NotificacionWsDTO.builder()
            .pedidoId(saved.getId()).numeroMesa(saved.getNumeroMesa())
            .nombreCliente(saved.getNombreCliente()).estado(saved.getEstado())
            .mensaje("Extras agregados — " + labelPedido(saved)).tipo("ITEMS_AGREGADOS").build();
        messagingTemplate.convertAndSend("/topic/cocina", notif);
        messagingTemplate.convertAndSend("/topic/mesero", notif);
        return ResponseEntity.ok(saved);
    }
 
 
    @PutMapping("/pedidos/{id}/estado")
    public ResponseEntity<Pedido> actualizarEstado(@PathVariable Long id, @RequestParam EstadoPedido estado,
            Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean cocina = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_COCINA"));
        boolean mesero = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_MESERO"));
        if (!admin && !((cocina && (estado == EstadoPedido.EN_PREPARACION || estado == EstadoPedido.LISTO))
                || (mesero && estado == EstadoPedido.ENTREGADO))) {
            throw new AccessDeniedException("No tienes permiso para esa transición");
        }
        Pedido pedido = pedidoService.actualizarEstado(id, estado);
        String msg = switch (estado) {
            case EN_PREPARACION -> "En preparacion — " + labelPedido(pedido);
            case LISTO          -> "Listo para servir! — " + labelPedido(pedido);
            case ENTREGADO      -> "Entregado — " + labelPedido(pedido);
            default             -> "Estado actualizado";
        };
        NotificacionWsDTO n = NotificacionWsDTO.builder()
            .pedidoId(pedido.getId()).numeroMesa(pedido.getNumeroMesa())
            .nombreCliente(pedido.getNombreCliente()).estado(estado).mensaje(msg).tipo("ACTUALIZACION_ESTADO").build();
        messagingTemplate.convertAndSend("/topic/mesero", n);
        messagingTemplate.convertAndSend("/topic/cocina", n);
        return ResponseEntity.ok(pedido);
    }
 
    @GetMapping("/pedidos/activos")
    public ResponseEntity<List<Pedido>> pedidosActivos() {
        return ResponseEntity.ok(pedidoService.obtenerPedidosActivos());
    }

    @GetMapping("/pedidos/cocina")
    public ResponseEntity<List<Pedido>> pedidosCocina() {
        return ResponseEntity.ok(pedidoService.obtenerPedidosCocina());
    }
 
    @GetMapping("/pedidos/{id}")
    public ResponseEntity<Pedido> obtenerPedido(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.obtenerPorId(id));
    }
 
    @PutMapping("/pedidos/{id}/pagar")
    public ResponseEntity<Pedido> registrarPago(@PathVariable Long id, @RequestParam String metodoPago) {
        Pedido pedido = pedidoService.registrarPago(id, metodoPago);
        messagingTemplate.convertAndSend("/topic/mesero",
            NotificacionWsDTO.builder().pedidoId(pedido.getId()).estado(pedido.getEstado())
                .tipo("ACTUALIZACION_PAGO").mensaje("Pago recibido: " + labelPedido(pedido)).build());
        return ResponseEntity.ok(pedido);
    }
 
 
    @PostMapping("/menu-diario")
    @Transactional
    public ResponseEntity<?> actualizarMenuDiario(@RequestBody List<MenuDiarioItemDTO> items) {
        platoDao.marcarTodosComoNoDisponibles();
        int[] publicados = {0};
        if (items != null) items.forEach(item -> platoDao.findById(item.getId())
            .filter(p -> !Boolean.TRUE.equals(p.getArchivado())).ifPresent(p -> {
            publicados[0]++;
            p.setDisponible(true);
            p.setStockDisponible(item.getCantidad());
            platoDao.save(p);
            messagingTemplate.convertAndSend("/topic/stock",
                StockUpdateDTO.builder().platoId(p.getId()).stockRestante(p.getStockDisponible()).nombrePlato(p.getNombre()).build());
        }));
        messagingTemplate.convertAndSend("/topic/menu",
            NotificacionWsDTO.builder().tipo("ACTUALIZACION_MENU").mensaje("Menu del dia actualizado").build());
        auditoria.registrar(AccionAuditoria.MENU_PUBLICADO, "Menu", null,
            publicados[0] + (publicados[0] == 1 ? " producto disponible" : " productos disponibles"));
        return ResponseEntity.ok(Collections.singletonMap("status", "Menu actualizado"));
    }
 
 
    @GetMapping("/reportes/diarios")
    public ResponseEntity<List<ReporteDiarioDTO>> getReportesDiarios() {
        List<Pedido> pagados = pedidoService.obtenerTodosLosPagados();
        Map<LocalDate,List<Pedido>> porFecha = pagados.stream()
            .collect(Collectors.groupingBy(p -> p.getFechaCreacion().toLocalDate()));
        List<ReporteDiarioDTO> reportes = new ArrayList<>();
        porFecha.entrySet().stream().sorted(Map.Entry.<LocalDate,List<Pedido>>comparingByKey().reversed())
            .forEach(entry -> {
                List<Pedido> dias = entry.getValue();
                double total = dias.stream().mapToDouble(p -> p.getDetalles().stream().mapToDouble(DetallePedido::getSubtotal).sum()).sum();
                Map<String,int[]> itemMap = new HashMap<>();
                Map<String,double[]> subtotalMap = new HashMap<>();
                dias.forEach(p -> p.getDetalles().forEach(d -> {
                    String key = d.getPresentacion() != null
                        ? d.getPlato().getNombre() + " (" + d.getPresentacion().getNombre() + ")"
                        : d.getPlato().getNombre();
                    itemMap.computeIfAbsent(key, k -> new int[]{0})[0] += d.getCantidad();
                    subtotalMap.computeIfAbsent(key, k -> new double[]{0})[0] += d.getSubtotal();
                }));
                List<ReporteDiarioDTO.ItemReporteDTO> items = new ArrayList<>();
                itemMap.forEach((nombre, qtys) -> {
                    double subtotal = subtotalMap.getOrDefault(nombre, new double[]{0})[0];
                    ReporteDiarioDTO.ItemReporteDTO item = new ReporteDiarioDTO.ItemReporteDTO();
                    item.setNombre(nombre); item.setCantidad(qtys[0]); item.setSubtotal(subtotal);
                    items.add(item);
                });
                items.sort((a,b) -> b.getCantidad() - a.getCantidad());
                ReporteDiarioDTO r = new ReporteDiarioDTO();
                r.setFecha(entry.getKey()); r.setTotalVentas(total); r.setCantidadPedidos(dias.size());
                r.setPedidosPagados((int) dias.stream().filter(Pedido::getPagado).count());
                r.setItems(items); reportes.add(r);
            });
        return ResponseEntity.ok(reportes);
    }
 
 
    private String describirPlato(Plato p) {
        StringBuilder texto = new StringBuilder(String.valueOf(p.getNombre()));
        if (p.getCategoria() != null && !p.getCategoria().isBlank()) texto.append(" · ").append(p.getCategoria());
        if (p.getPrecio() != null) texto.append(" · ").append(dinero(p.getPrecio()));
        if (p.getStockDisponible() != null) texto.append(" · Stock ").append(p.getStockDisponible());
        return texto.toString();
    }

    private String descripcionValida(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) return null;
        String limpia = descripcion.trim();
        if (limpia.length() > 200) throw new IllegalArgumentException("La descripción no puede superar 200 caracteres");
        return limpia;
    }

    private String describirPresentacion(PresentacionPlato p) {
        String plato = p.getPlato() != null ? p.getPlato().getNombre() + " – " : "";
        return plato + p.getNombre() + (p.getPrecio() != null ? " · " + dinero(p.getPrecio()) : "")
            + (p.getModalidad() != null ? " · " + p.getModalidad() : "");
    }

    private String dinero(Double monto) {
        return configuracionNegocioService.vista().getSimboloMoneda() + " "
            + String.format(java.util.Locale.ROOT, "%.2f", monto == null ? 0.0 : monto);
    }

    private String labelPedido(Pedido p) {
        return p.getNumeroMesa() != null
            ? configuracionNegocioService.vista().getPuntoAtencionSingular() + " " + p.getNumeroMesa()
            : p.getNombreCliente();
    }
 
    private void aplicarDescuentoStock(Map<Long,Integer> descPlato, Map<Long,Integer> descPres, Map<Long,Integer> descSopa) {
        
        descPlato.forEach((pid, q) -> {
            platoDao.findById(pid).ifPresent(p -> {
                if (p.getStockDisponible() != null) {
                    int filasAfectadas = platoDao.descontarStockPreciso(pid, q);
                    if (filasAfectadas == 0) {
                        throw new IllegalStateException("Stock insuficiente para el plato: " + p.getNombre());
                    }
                    int nuevoStock = p.getStockDisponible() - q;
                    messagingTemplate.convertAndSend("/topic/stock", StockUpdateDTO.builder().platoId(p.getId()).stockRestante(nuevoStock).nombrePlato(p.getNombre()).build());
                }
            });
        });

        descPres.forEach((presId, q) -> {
            presentacionPlatoDao.findById(presId).ifPresent(pres -> {
                if (pres.getStockDisponible() != null) {
                    int filasAfectadas = presentacionPlatoDao.descontarStockPreciso(presId, q);
                    if (filasAfectadas == 0) {
                        throw new IllegalStateException("Stock insuficiente para el tamaño: " + pres.getNombre() + " de " + pres.getPlato().getNombre());
                    }
                    int nuevoStock = pres.getStockDisponible() - q;
                    messagingTemplate.convertAndSend("/topic/stock", StockUpdateDTO.builder().platoId(pres.getPlato().getId()).presentacionId(pres.getId()).stockRestante(nuevoStock).nombrePlato(pres.getPlato().getNombre() + " – " + pres.getNombre()).build());
                }
            });
        });

        descSopa.forEach((sid, q) -> {
            platoDao.findById(sid).ifPresent(s -> {
                if (s.getStockDisponible() != null) {
                    int filasAfectadas = platoDao.descontarStockPreciso(sid, q);
                    if (filasAfectadas == 0) {
                        throw new IllegalStateException("Stock insuficiente de la sopa: " + s.getNombre());
                    }
                    int nuevoStock = s.getStockDisponible() - q;
                    messagingTemplate.convertAndSend("/topic/stock", StockUpdateDTO.builder().platoId(s.getId()).stockRestante(nuevoStock).nombrePlato(s.getNombre()).build());
                }
            });
        });
    }
    
    @GetMapping("/mesas/ocupadas")
    public ResponseEntity<List<Integer>> getMesasOcupadas() {
        List<Integer> ocupadas = pedidoDao.findMesasOcupadas(
            List.of(EstadoPedido.PENDIENTE, EstadoPedido.EN_PREPARACION, EstadoPedido.LISTO)
        );
        return ResponseEntity.ok(ocupadas);
    }
}
