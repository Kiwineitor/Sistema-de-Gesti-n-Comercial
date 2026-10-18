package com.sistema.pedidosCori.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import com.sistema.pedidosCori.service.UsuarioActual;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorActual")
public class AuditoriaConfig {
    @Bean
    public AuditorAware<String> auditorActual() {
        return UsuarioActual::nombre;
    }
}
