package com.sistema.pedidosCori.controller;

import java.io.IOException;
import java.util.Arrays;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sistema.pedidosCori.models.dto.ConfiguracionNegocioRequest;
import com.sistema.pedidosCori.models.dto.ConfiguracionNegocioVista;
import com.sistema.pedidosCori.models.entity.ConfiguracionNegocio;
import com.sistema.pedidosCori.service.ConfiguracionNegocioService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/configuracion")
@RequiredArgsConstructor
class ConfiguracionApiController {
    private final ConfiguracionNegocioService service;

    @GetMapping
    ConfiguracionNegocioVista obtener() { return service.vista(); }

    @PutMapping
    ConfiguracionNegocioVista actualizar(@RequestBody ConfiguracionNegocioRequest request) {
        return service.actualizar(request);
    }

    @PutMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ConfiguracionNegocioVista logo(@RequestPart("archivo") MultipartFile archivo) {
        return service.actualizarLogo(archivo);
    }

    @PostMapping("/restablecer")
    ConfiguracionNegocioVista restablecer() { return service.restablecer(); }
}

@RestController
@RequiredArgsConstructor
class BrandingController {
    private final ConfiguracionNegocioService service;

    @GetMapping("/branding/logo")
    ResponseEntity<byte[]> logo(@RequestParam(required = false) Long v,
            @org.springframework.web.bind.annotation.RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch)
            throws IOException {
        ConfiguracionNegocio c = service.logoActual();
        byte[] bytes = c.getLogoDatos();
        boolean defecto = bytes == null;
        if (defecto) bytes = new ClassPathResource("static/img/logo-default.svg").getInputStream().readAllBytes();
        String mime = defecto ? "image/svg+xml" : c.getLogoMimeType();
        String etag = "\"logo-" + c.getLogoVersion() + "\"";
        HttpHeaders headers = new HttpHeaders();
        headers.setETag(etag);
        headers.set("X-Content-Type-Options", "nosniff");
        String extension = switch (mime) {
            case "image/svg+xml" -> ".svg";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            default -> ".png";
        };
        headers.setContentDisposition(org.springframework.http.ContentDisposition.inline().filename("logo" + extension).build());
        if ("image/svg+xml".equals(mime))
            headers.set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'");
        if (v != null && v.equals(c.getLogoVersion()))
            headers.setCacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable());
        else headers.setCacheControl(CacheControl.noCache());
        if (etag.equals(ifNoneMatch)) return ResponseEntity.status(304).headers(headers).build();
        headers.setContentType(MediaType.parseMediaType(mime));
        return ResponseEntity.ok().headers(headers).body(Arrays.copyOf(bytes, bytes.length));
    }
}
