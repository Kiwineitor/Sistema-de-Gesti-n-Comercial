package com.sistema.pedidosCori.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.sistema.pedidosCori.models.dao.IRegistroAuditoriaDao;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.RegistroAuditoria;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AuditoriaService {
    public static final int TAMANO_MAXIMO = 100;

    private final IRegistroAuditoriaDao dao;
    private final TransactionTemplate transaccionPropia;

    public AuditoriaService(IRegistroAuditoriaDao dao, PlatformTransactionManager transacciones) {
        this.dao = dao;
        this.transaccionPropia = new TransactionTemplate(transacciones);
        this.transaccionPropia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void registrar(AccionAuditoria accion, String entidad, Object entidadId, String detalle) {
        registrarComo(null, accion, entidad, entidadId, () -> detalle);
    }

    public void registrar(AccionAuditoria accion, String entidad, Object entidadId, Supplier<String> detalle) {
        registrarComo(null, accion, entidad, entidadId, detalle);
    }

    public void registrarComo(String usuario, AccionAuditoria accion, String entidad, Object entidadId,
            Supplier<String> detalle) {
        RegistroAuditoria registro;
        try {
            String autor = usuario != null ? usuario : UsuarioActual.nombre().orElse(null);
            registro = RegistroAuditoria.builder()
                .fecha(LocalDateTime.now())
                .usuario(recortar(autor, RegistroAuditoria.MAX_USUARIO))
                .accion(recortar(accion.name(), RegistroAuditoria.MAX_ACCION))
                .entidad(recortar(entidad, RegistroAuditoria.MAX_ENTIDAD))
                .entidadId(entidadId == null ? null : recortar(String.valueOf(entidadId), RegistroAuditoria.MAX_ENTIDAD_ID))
                .detalle(recortar(detalle == null ? null : detalle.get(), RegistroAuditoria.MAX_DETALLE))
                .build();
        } catch (RuntimeException e) {
            log.warn("No se pudo preparar la auditoría {}: {}", accion, e.getMessage());
            return;
        }
        TrasConfirmar.ejecutar(() -> persistir(registro));
    }

    private void persistir(RegistroAuditoria registro) {
        try {
            transaccionPropia.executeWithoutResult(estado -> dao.save(registro));
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar la auditoría {}: {}", registro.getAccion(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<RegistroAuditoria> buscar(String usuario, String accion, LocalDate desde, LocalDate hasta,
            int pagina, int tamano) {
        if (pagina < 0) throw new IllegalArgumentException("La página no puede ser negativa");
        if (tamano < 1) throw new IllegalArgumentException("El tamaño de página debe ser mayor que cero");
        if (desde != null && hasta != null && desde.isAfter(hasta))
            throw new IllegalArgumentException("La fecha inicial no puede ser posterior a la final");
        String accionNormalizada = vacioANulo(accion);
        if (accionNormalizada != null && Arrays.stream(AccionAuditoria.values()).noneMatch(a -> a.name().equals(accionNormalizada)))
            throw new IllegalArgumentException("Acción de auditoría desconocida");
        return dao.buscar(vacioANulo(usuario), accionNormalizada,
            desde == null ? null : desde.atStartOfDay(),
            hasta == null ? null : hasta.plusDays(1).atStartOfDay(),
            PageRequest.of(pagina, Math.min(tamano, TAMANO_MAXIMO), Sort.by(Sort.Order.desc("fecha"), Sort.Order.desc("id"))));
    }

    @Transactional(readOnly = true)
    public List<String> usuarios() { return dao.usuariosDistintos(); }

    public List<Map<String, String>> acciones() {
        return Arrays.stream(AccionAuditoria.values())
            .map(a -> Map.of("codigo", a.name(), "etiqueta", a.getEtiqueta())).toList();
    }

    public static String recortar(String valor, int max) {
        if (valor == null) return null;
        if (valor.length() <= max) return valor;
        int fin = max - 1;
        if (Character.isHighSurrogate(valor.charAt(fin - 1))) fin--;
        return valor.substring(0, fin) + "…";
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
