package com.sistema.pedidosCori.service;

import java.util.LinkedHashMap;
import java.util.Map;

public final class TemaNegocio {
    public static final String SUPERFICIE_CLARA = "#F5F7FB";
    public static final String SUPERFICIE_CLARA_ELEVADA = "#ECF0F5";
    public static final String SUPERFICIE_OSCURA = "#111827";
    public static final String SUPERFICIE_OSCURA_ELEVADA = "#263142";
    public static final String SUPERFICIE_OSCURA_TARJETA = "#1B2433";
    private static final String OSCURO = "#050505";
    private static final String NEGRO = "#000000";
    private static final String BLANCO = "#FFFFFF";

    private TemaNegocio() {
    }

    public static Map<String, String> variables(String primario, String secundario, String acento) {
        Map<String, String> variables = new LinkedHashMap<>();
        agregar(variables, "primary", primario);
        agregar(variables, "secondary", secundario);
        agregar(variables, "accent", acento);
        return variables;
    }

    private static void agregar(Map<String, String> variables, String nombre, String color) {
        String base = "--brand-" + nombre;
        String hover = mezclar(color, OSCURO, 0.12);
        String suaveClaro = mezclar(SUPERFICIE_CLARA, color, 0.12);
        String solidoOscuro = visible(color, BLANCO, 6.0, SUPERFICIE_OSCURA, SUPERFICIE_OSCURA_TARJETA, SUPERFICIE_OSCURA_ELEVADA);
        String solidoOscuroHover = mezclar(solidoOscuro, BLANCO, 0.1);
        String suaveOscuro = mezclar(SUPERFICIE_OSCURA, solidoOscuro, 0.22);
        variables.put(base, color);
        variables.put(base + "-hover", hover);
        variables.put(base + "-on", textoSobre(color));
        variables.put(base + "-on-hover", textoSobre(hover));
        variables.put(base + "-soft-light", suaveClaro);
        variables.put(base + "-border-light", mezclar(SUPERFICIE_CLARA, color, 0.32));
        variables.put(base + "-text-light", legible(color, OSCURO, suaveClaro, SUPERFICIE_CLARA, SUPERFICIE_CLARA_ELEVADA, BLANCO));
        variables.put(base + "-soft-dark", suaveOscuro);
        variables.put(base + "-border-dark", mezclar(SUPERFICIE_OSCURA, solidoOscuro, 0.6));
        variables.put(base + "-solid-dark", solidoOscuro);
        variables.put(base + "-solid-dark-hover", solidoOscuroHover);
        variables.put(base + "-solid-dark-on", textoSobre(solidoOscuro));
        variables.put(base + "-solid-dark-on-hover", textoSobre(solidoOscuroHover));
        variables.put(base + "-text-dark", visible(color, BLANCO, 7.0, suaveOscuro, SUPERFICIE_OSCURA, SUPERFICIE_OSCURA_ELEVADA));
    }

    public static String textoSobre(String fondo) {
        double claro = contraste(BLANCO, fondo);
        double oscuro = contraste(OSCURO, fondo);
        String elegido = claro >= oscuro ? BLANCO : OSCURO;
        return Math.max(claro, oscuro) >= 4.5 ? elegido : NEGRO;
    }

    private static String legible(String color, String destino, String... fondos) {
        return visible(color, destino, 4.5, fondos);
    }

    private static String visible(String color, String destino, double minimo, String... fondos) {
        for (int porcentaje = 0; porcentaje <= 100; porcentaje++) {
            String candidato = mezclar(color, destino, porcentaje / 100.0);
            if (contrasteMinimo(candidato, fondos) >= minimo) return candidato;
        }
        return destino;
    }

    private static double contrasteMinimo(String color, String... fondos) {
        double minimo = Double.MAX_VALUE;
        for (String fondo : fondos) minimo = Math.min(minimo, contraste(color, fondo));
        return minimo;
    }

    public static double contraste(String primero, String segundo) {
        double l1 = luminancia(primero);
        double l2 = luminancia(segundo);
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    }

    private static double luminancia(String hex) {
        int[] rgb = canales(hex);
        double r = lineal(rgb[0]);
        double g = lineal(rgb[1]);
        double b = lineal(rgb[2]);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double lineal(int canal) {
        double valor = canal / 255.0;
        return valor <= 0.04045 ? valor / 12.92 : Math.pow((valor + 0.055) / 1.055, 2.4);
    }

    private static String mezclar(String inicio, String destino, double factor) {
        int[] a = canales(inicio);
        int[] b = canales(destino);
        return String.format("#%02X%02X%02X",
            Math.round(a[0] * (1 - factor) + b[0] * factor),
            Math.round(a[1] * (1 - factor) + b[1] * factor),
            Math.round(a[2] * (1 - factor) + b[2] * factor));
    }

    private static int[] canales(String hex) {
        return new int[]{Integer.parseInt(hex.substring(1, 3), 16),
            Integer.parseInt(hex.substring(3, 5), 16), Integer.parseInt(hex.substring(5, 7), 16)};
    }
}
