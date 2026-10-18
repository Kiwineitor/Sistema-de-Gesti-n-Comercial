package com.sistema.pedidosCori.controller;

import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> archivoExcesivo(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(Map.of("mensaje", "El archivo no puede superar 1 MB", "codigo", "ARCHIVO_EXCESIVO"));
    }
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> conflicto(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("mensaje", ex.getMessage(), "codigo", "CONFLICTO"));
    }

    @ExceptionHandler({NoSuchElementException.class, NoResourceFoundException.class})
    public ResponseEntity<Map<String, String>> noEncontrado(Exception ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(Map.of("mensaje", ex instanceof NoSuchElementException ? ex.getMessage() : "Recurso no encontrado",
                "codigo", "NO_ENCONTRADO"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> solicitudInvalida(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("mensaje", ex.getMessage(), "codigo", "SOLICITUD_INVALIDA"));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> formatoInvalido(Exception ex) {
        return ResponseEntity.badRequest()
            .body(Map.of("mensaje", "Solicitud inválida", "codigo", "SOLICITUD_INVALIDA"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> prohibido(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("mensaje", ex.getMessage(), "codigo", "PROHIBIDO"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> errorInterno(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(Map.of("mensaje", "Error interno del servidor", "codigo", "ERROR_INTERNO"));
    }
}
