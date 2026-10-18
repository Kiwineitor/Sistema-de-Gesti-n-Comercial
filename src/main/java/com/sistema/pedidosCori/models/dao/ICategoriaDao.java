package com.sistema.pedidosCori.models.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.sistema.pedidosCori.models.entity.Categoria;

public interface ICategoriaDao extends JpaRepository<Categoria, Long> {
    List<Categoria> findAllByOrderByOrdenAscIdAsc();
    Optional<Categoria> findByCodigoIgnoreCase(String codigo);
    boolean existsByCodigo(String codigo);

    @Query("SELECT COALESCE(MAX(c.orden), 0) FROM Categoria c")
    int maximoOrden();
}
