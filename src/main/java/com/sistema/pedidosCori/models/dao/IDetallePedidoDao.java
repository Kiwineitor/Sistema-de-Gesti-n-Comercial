package com.sistema.pedidosCori.models.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import com.sistema.pedidosCori.models.entity.DetallePedido;

public interface IDetallePedidoDao extends JpaRepository<DetallePedido, Long> {
    boolean existsByPlatoIdOrAcompananteSopaId(Long platoId, Long acompananteSopaId);
}
