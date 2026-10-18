package com.sistema.pedidosCori.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistema.pedidosCori.models.dto.UsuarioRequest;
import com.sistema.pedidosCori.models.dto.UsuarioVista;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.RegistroAuditoria;
import com.sistema.pedidosCori.service.AuditoriaService;
import com.sistema.pedidosCori.service.ReporteService;
import com.sistema.pedidosCori.service.ResumenService;
import com.sistema.pedidosCori.service.UsuarioService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminApiController {
    private final UsuarioService usuarioService;
    private final AuditoriaService auditoriaService;
    private final ResumenService resumenService;
    private final ReporteService reporteService;

    public record RegistroVista(Long id, String fecha, String usuario, String accion, String accionEtiqueta,
            String entidad, String entidadId, String detalle) { }
    public record PaginaAuditoria(List<RegistroVista> contenido, int pagina, int tamano, long total, int paginas) { }

    @GetMapping("/usuarios")
    public List<UsuarioVista> usuarios() { return usuarioService.listar(); }

    @PostMapping("/usuarios")
    public ResponseEntity<UsuarioVista> crearUsuario(@RequestBody UsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crear(request));
    }

    @PutMapping("/usuarios/{id}")
    public UsuarioVista actualizarUsuario(@PathVariable Long id, @RequestBody UsuarioRequest request) {
        return usuarioService.actualizar(id, request);
    }

    @PutMapping("/usuarios/{id}/password")
    public ResponseEntity<Void> cambiarPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        usuarioService.cambiarPassword(id, body == null ? null : body.get("password"));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/usuarios/{id}")
    public ResponseEntity<Void> eliminarUsuario(@PathVariable Long id) {
        usuarioService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/auditoria")
    public PaginaAuditoria auditoria(
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "50") int tamano) {
        Page<RegistroAuditoria> page = auditoriaService.buscar(usuario, accion, desde, hasta, pagina, tamano);
        List<RegistroVista> contenido = page.getContent().stream().map(r -> new RegistroVista(r.getId(),
            r.getFecha() == null ? null : r.getFecha().toString(), r.getUsuario(), r.getAccion(),
            AccionAuditoria.etiquetaDe(r.getAccion()), r.getEntidad(), r.getEntidadId(), r.getDetalle())).toList();
        return new PaginaAuditoria(contenido, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @GetMapping("/auditoria/acciones")
    public List<Map<String, String>> acciones() { return auditoriaService.acciones(); }

    @GetMapping("/auditoria/usuarios")
    public List<String> usuariosAuditoria() { return auditoriaService.usuarios(); }

    @GetMapping("/resumen-hoy")
    public ResumenService.ResumenHoy resumenHoy() { return resumenService.hoy(); }

    @GetMapping("/reportes")
    public ReporteService.Reporte reportes(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "auto") String agrupar) {
        return reporteService.generar(desde, hasta, agrupar);
    }

    @GetMapping("/reportes/csv")
    public ResponseEntity<byte[]> reporteCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String tipo) {
        ReporteService.ArchivoCsv archivo = reporteService.csv(desde, hasta, tipo);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo.nombre() + "\"")
            .body(archivo.contenido());
    }
}
