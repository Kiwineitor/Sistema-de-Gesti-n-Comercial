package com.sistema.pedidosCori.controller;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.service.PlatoImagenService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class PlatoImagenController {
    private final PlatoImagenService service;

    @PutMapping(value = "/api/admin/platos/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Plato subir(@PathVariable Long id, @RequestPart("archivo") MultipartFile archivo) {
        return service.actualizar(id, archivo);
    }

    @DeleteMapping("/api/admin/platos/{id}/imagen")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        service.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/platos/{id}/imagen")
    public ResponseEntity<byte[]> obtener(@PathVariable Long id, @RequestParam(required = false) Long v) {
        PlatoImagenService.ImagenPlato imagen = service.obtener(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(imagen.mimeType()));
        headers.set("X-Content-Type-Options", "nosniff");
        String extension = switch (imagen.mimeType()) {
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> ".png";
        };
        headers.setContentDisposition(ContentDisposition.inline().filename("producto-" + id + extension).build());
        headers.set(HttpHeaders.CACHE_CONTROL, v != null && v.equals(imagen.version())
            ? "private, max-age=31536000, immutable" : "no-cache");
        return ResponseEntity.ok().headers(headers).body(imagen.datos());
    }
}
