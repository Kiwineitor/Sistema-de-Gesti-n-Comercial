package com.sistema.pedidosCori.models.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.sistema.pedidosCori.models.entity.Plato;

public interface IPlatoDao extends JpaRepository<Plato, Long> {
    List<Plato> findByDisponibleTrue();
    List<Plato> findByDisponibleTrueAndArchivadoFalse();
    List<Plato> findByArchivadoFalse();
    long countByDisponibleTrueAndArchivadoFalse();
    List<Plato> findByCategoriaAndDisponibleTrue(String categoria);

    @Modifying
    @Query("UPDATE Plato p SET p.disponible = false")
    void marcarTodosComoNoDisponibles();

    @Modifying
    @Query("UPDATE Plato p SET p.disponible = true WHERE p.id IN :ids AND p.archivado = false")
    void marcarComoDisponibles(@Param("ids") List<Long> ids);

    Optional<Plato> findByNombre(String nombre);

    @Modifying
    @Query("UPDATE Plato p SET p.stockDisponible = p.stockDisponible - :cantidad WHERE p.id = :id AND p.stockDisponible >= :cantidad")
    int descontarStockPreciso(@Param("id") Long id, @Param("cantidad") Integer cantidad);

    long countByCategoria(String categoria);

    @Query("SELECT p.categoria, COUNT(p) FROM Plato p WHERE p.archivado = false AND p.categoria IS NOT NULL GROUP BY p.categoria")
    List<Object[]> contarActivosPorCategoria();

    @Query("SELECT DISTINCT p.categoria FROM Plato p WHERE p.categoria IS NOT NULL")
    List<String> categoriasDistintas();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Plato p SET p.categoria = :nueva WHERE p.categoria = :actual")
    int cambiarCategoria(@Param("actual") String actual, @Param("nueva") String nueva);
}
