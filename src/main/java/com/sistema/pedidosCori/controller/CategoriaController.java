package com.sistema.pedidosCori.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sistema.pedidosCori.models.dto.CategoriaRequest;
import com.sistema.pedidosCori.models.dto.CategoriaVista;
import com.sistema.pedidosCori.service.CategoriaService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CategoriaController {
    private final CategoriaService service;

    public record OrdenCategorias(List<Long> ids) {
    }

    @GetMapping("/api/categorias")
    public List<CategoriaVista> listar() { return service.listar(); }

    @PostMapping("/api/admin/categorias")
    public ResponseEntity<CategoriaVista> crear(@RequestBody CategoriaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @PutMapping("/api/admin/categorias/orden")
    public List<CategoriaVista> reordenar(@RequestBody OrdenCategorias orden) {
        return service.reordenar(orden == null ? null : orden.ids());
    }

    @PutMapping("/api/admin/categorias/{id}")
    public CategoriaVista actualizar(@PathVariable Long id, @RequestBody CategoriaRequest request) {
        return service.actualizar(id, request);
    }

    @DeleteMapping("/api/admin/categorias/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, @RequestParam(required = false) Long moverA) {
        service.eliminar(id, moverA);
        return ResponseEntity.noContent().build();
    }
}
