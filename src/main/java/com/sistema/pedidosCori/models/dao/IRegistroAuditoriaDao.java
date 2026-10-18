package com.sistema.pedidosCori.models.dao;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import com.sistema.pedidosCori.models.entity.RegistroAuditoria;

import jakarta.persistence.criteria.Predicate;

public interface IRegistroAuditoriaDao extends JpaRepository<RegistroAuditoria, Long>,
        JpaSpecificationExecutor<RegistroAuditoria> {

    @Query("SELECT DISTINCT r.usuario FROM RegistroAuditoria r WHERE r.usuario IS NOT NULL ORDER BY r.usuario")
    List<String> usuariosDistintos();

    default Page<RegistroAuditoria> buscar(String usuario, String accion, LocalDateTime desde,
            LocalDateTime hasta, Pageable pageable) {
        Specification<RegistroAuditoria> filtro = (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (usuario != null) condiciones.add(cb.equal(root.get("usuario"), usuario));
            if (accion != null) condiciones.add(cb.equal(root.get("accion"), accion));
            if (desde != null) condiciones.add(cb.greaterThanOrEqualTo(root.<LocalDateTime>get("fecha"), desde));
            if (hasta != null) condiciones.add(cb.lessThan(root.<LocalDateTime>get("fecha"), hasta));
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        return findAll(filtro, pageable);
    }
}
