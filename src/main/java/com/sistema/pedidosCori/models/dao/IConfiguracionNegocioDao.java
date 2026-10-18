package com.sistema.pedidosCori.models.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import com.sistema.pedidosCori.models.entity.ConfiguracionNegocio;

public interface IConfiguracionNegocioDao extends JpaRepository<ConfiguracionNegocio, Long> {
}
