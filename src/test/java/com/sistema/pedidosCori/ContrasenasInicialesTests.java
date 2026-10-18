package com.sistema.pedidosCori;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.sistema.pedidosCori.components.DataInitializer;

class ContrasenasInicialesTests {

    @Test
    void productionRejectsMissingPlaceholderAndDemoPasswords() {
        for (String valor : new String[]{null, "", "   ", "CAMBIAR_ADMIN", "admin123", "mesero123", "cocina123", " Admin123 "}) {
            assertThrows(IllegalStateException.class, () -> DataInitializer.exigirPassword("SEED_ADMIN_PASSWORD", valor));
        }
    }

    @Test
    void productionAcceptsOwnPassword() {
        assertDoesNotThrow(() -> DataInitializer.exigirPassword("SEED_ADMIN_PASSWORD", "Una-Clave-Propia-2026"));
    }
}
