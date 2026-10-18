package com.sistema.pedidosCori.models.dao;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import com.sistema.pedidosCori.models.entity.EstadoPedido;
import com.sistema.pedidosCori.models.entity.Pedido;

public interface IPedidoDao extends JpaRepository<Pedido, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pedido p WHERE p.id = :id")
    Optional<Pedido> findByIdForUpdate(@Param("id") Long id);
    List<Pedido> findByEstadoOrderByFechaCreacionAsc(EstadoPedido estado);
    List<Pedido> findByEstadoInOrderByFechaCreacionAsc(List<EstadoPedido> estados);
    List<Pedido> findByEstadoNotOrPagadoFalseOrderByFechaCreacionAsc(EstadoPedido estado);

    List<Pedido> findByEstadoInAndPagadoFalseOrderByFechaCreacionAsc(List<EstadoPedido> estados);
    List<Pedido> findByNumeroMesaAndEstadoIn(Integer mesa, List<EstadoPedido> estados);

    List<Pedido> findByPagadoTrue();

    List<Pedido> findByFechaCreacionGreaterThanEqualAndFechaCreacionLessThan(LocalDateTime desde, LocalDateTime hasta);
    List<Pedido> findByPagadoTrueAndFechaPagoGreaterThanEqualAndFechaPagoLessThan(LocalDateTime desde, LocalDateTime hasta);

    long countByEstadoNotOrPagadoFalse(EstadoPedido estado);

    @Query("SELECT DISTINCT p FROM Pedido p LEFT JOIN FETCH p.detalles d LEFT JOIN FETCH d.plato LEFT JOIN FETCH d.presentacion " +
        "WHERE p.pagado = true AND ((p.fechaPago >= :desde AND p.fechaPago < :hasta) " +
        "OR (p.fechaPago IS NULL AND p.fechaCreacion >= :desde AND p.fechaCreacion < :hasta))")
    List<Pedido> findCobradosEntre(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("SELECT p.registradoPor, p.pagado, p.total FROM Pedido p " +
        "WHERE p.fechaCreacion >= :desde AND p.fechaCreacion < :hasta")
    List<Object[]> findRegistradosEntre(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);

    @Query("SELECT DISTINCT p.numeroMesa FROM Pedido p " +
        "WHERE p.estado IN :estados " +
        "AND p.pagado = false " +
        "AND p.numeroMesa IS NOT NULL")
    List<Integer> findMesasOcupadas(@Param("estados") List<EstadoPedido> estados);
}
