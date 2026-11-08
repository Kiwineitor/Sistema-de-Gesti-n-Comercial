package com.sistema.pedidosCori.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

public final class PoliticaPassword {
    public static final int MINIMO = 8;
    public static final int MAXIMO = 72;

    private static final Set<String> COMUNES = Set.of(
        "admin123", "mesero123", "cocina123", "password", "password1", "password123", "contraseña", "contrasena",
        "contrasena1", "contraseña1", "12345678", "123456789", "1234567890", "qwerty123", "abc12345", "abcd1234",
        "admin1234", "administrador", "11111111", "00000000", "iloveyou1", "bolivia123", "restaurante1");

    private PoliticaPassword() { }

    public static String validar(String password, String username) {
        if (password == null || password.length() < MINIMO || password.getBytes(StandardCharsets.UTF_8).length > MAXIMO)
            throw new IllegalArgumentException("La contraseña debe tener entre " + MINIMO + " y " + MAXIMO + " caracteres");
        if (password.isBlank() || !password.equals(password.strip()))
            throw new IllegalArgumentException("La contraseña no puede empezar ni terminar con espacios");
        if (password.chars().noneMatch(Character::isLetter) || password.chars().noneMatch(Character::isDigit))
            throw new IllegalArgumentException("La contraseña debe combinar letras y números");
        String normalizada = password.toLowerCase(Locale.ROOT);
        if (COMUNES.contains(normalizada))
            throw new IllegalArgumentException("Esa contraseña es demasiado común; elige otra");
        if (username != null && username.length() >= 3 && normalizada.contains(username.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("La contraseña no debe contener el nombre de usuario");
        return password;
    }

    public static boolean esDeDemostracion(String password) {
        return password != null && COMUNES.contains(password.trim().toLowerCase(Locale.ROOT));
    }
}
