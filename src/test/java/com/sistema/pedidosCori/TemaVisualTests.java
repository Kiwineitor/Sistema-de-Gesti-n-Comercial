package com.sistema.pedidosCori;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.sistema.pedidosCori.service.TemaNegocio;

class TemaVisualTests {
    private static final Path RECURSOS = Path.of("src/main/resources");
    private static final Path TEMA = RECURSOS.resolve("static/css/theme.css");
    private static final Pattern DECLARACION = Pattern.compile("(--[a-z0-9-]+)\\s*:\\s*([^;]+);");
    private static final Pattern USO = Pattern.compile("var\\((--[a-z0-9-]+)");
    private static final Pattern LITERAL = Pattern.compile(
        "(?i)(color|background|border|fill|stroke|shadow|outline)[^;{}\\n]*(#[0-9a-f]{3,8}\\b|rgba?\\(|hsla?\\()");
    private static final List<String> ESTADOS = List.of("success", "warning", "error", "info");
    private static final List<String> SUPERFICIES = List.of("--surface-page", "--surface-card", "--surface-raised");

    @Test
    void textPairsMeetContrastInLightAndDarkModes() throws IOException {
        String css = Files.readString(TEMA, StandardCharsets.UTF_8);
        Map<String, String> claro = declaraciones(bloque(css, ":root {"));
        Map<String, String> oscuro = new HashMap<>(claro);
        oscuro.putAll(declaraciones(bloque(css, ":root[data-theme=\"dark\"] {")));

        List<String> fallos = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> modo : Map.of("claro", claro, "oscuro", oscuro).entrySet()) {
            Map<String, String> v = modo.getValue();
            for (String superficie : SUPERFICIES) {
                verificar(fallos, modo.getKey(), v, "--text-main", superficie, 4.5);
                verificar(fallos, modo.getKey(), v, "--text-muted", superficie, 4.5);
            }
            verificar(fallos, modo.getKey(), v, "--border-strong", "--surface-card", 3.0);
            for (String estado : ESTADOS) {
                String base = "--status-" + estado;
                verificar(fallos, modo.getKey(), v, base + "-on", base, 4.5);
                verificar(fallos, modo.getKey(), v, base + "-text", base + "-soft", 4.5);
                for (String superficie : SUPERFICIES) verificar(fallos, modo.getKey(), v, base + "-text", superficie, 4.5);
            }
        }
        assertTrue(fallos.isEmpty(), String.join("\n", fallos));
    }

    @Test
    void viewsOnlyUseDefinedThemeVariables() throws IOException {
        Set<String> definidas = new TreeSet<>(declaraciones(Files.readString(TEMA, StandardCharsets.UTF_8)).keySet());
        definidas.addAll(TemaNegocio.variables("#24324A", "#64748B", "#C56A3D").keySet());
        Set<String> faltantes = new TreeSet<>();
        for (Path archivo : archivosDeVista()) {
            Matcher m = USO.matcher(Files.readString(archivo, StandardCharsets.UTF_8));
            while (m.find()) if (!definidas.contains(m.group(1))) faltantes.add(archivo.getFileName() + " " + m.group(1));
        }
        assertEquals(Set.of(), faltantes);
    }

    @Test
    void viewsDoNotHardcodeColors() throws IOException {
        List<String> literales = new ArrayList<>();
        for (Path archivo : archivosDeVista()) {
            if (archivo.equals(TEMA)) continue;
            Matcher m = LITERAL.matcher(Files.readString(archivo, StandardCharsets.UTF_8));
            while (m.find()) literales.add(archivo.getFileName() + ": " + m.group());
        }
        assertEquals(List.of(), literales);
    }

    private static List<Path> archivosDeVista() throws IOException {
        try (Stream<Path> rutas = Stream.concat(Files.walk(RECURSOS.resolve("templates")), Files.walk(RECURSOS.resolve("static")))) {
            return rutas.filter(p -> p.toString().matches(".*\\.(html|js|css)$")).toList();
        }
    }

    private static String bloque(String css, String inicio) {
        int desde = css.indexOf(inicio);
        assertTrue(desde >= 0, "No se encontró el bloque " + inicio);
        return css.substring(desde + inicio.length(), css.indexOf('}', desde));
    }

    private static Map<String, String> declaraciones(String texto) {
        Map<String, String> mapa = new HashMap<>();
        Matcher m = DECLARACION.matcher(texto);
        while (m.find()) mapa.put(m.group(1), m.group(2).trim());
        return mapa;
    }

    private static String resolver(Map<String, String> variables, String nombre) {
        String valor = variables.get(nombre);
        assertNotNull(valor, "Variable no definida: " + nombre);
        return valor.startsWith("var(") ? resolver(variables, valor.substring(4, valor.length() - 1).trim()) : valor;
    }

    private static void verificar(List<String> fallos, String modo, Map<String, String> v, String texto, String fondo, double minimo) {
        double contraste = TemaNegocio.contraste(resolver(v, texto), resolver(v, fondo));
        if (contraste < minimo)
            fallos.add(String.format("%s: %s sobre %s = %.2f (mínimo %.1f)", modo, texto, fondo, contraste, minimo));
    }
}
