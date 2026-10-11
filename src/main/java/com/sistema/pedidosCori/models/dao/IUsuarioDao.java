package com.sistema.pedidosCori.models.dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistema.pedidosCori.models.entity.Usuario;

public interface IUsuarioDao extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsername(String username);
    boolean existsByUsername(String username);
}
