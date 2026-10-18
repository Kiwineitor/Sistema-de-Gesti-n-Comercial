package com.sistema.pedidosCori.service;

import java.util.Arrays;
import java.util.NoSuchElementException;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dao.IPlatoImagenDao;
import com.sistema.pedidosCori.models.dto.NotificacionWsDTO;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.Plato;
import com.sistema.pedidosCori.models.entity.PlatoImagen;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PlatoImagenService {
    private final IPlatoDao platoDao;
    private final IPlatoImagenDao imagenDao;
    private final LogoValidator validator;
    private final AuditoriaService auditoria;
    private final SimpMessagingTemplate messagingTemplate;

    public record ImagenPlato(byte[] datos, String mimeType, Long version) {
    }

    @Transactional
    public Plato actualizar(Long platoId, MultipartFile archivo) {
        Plato plato = platoDao.findById(platoId).filter(p -> !Boolean.TRUE.equals(p.getArchivado()))
            .orElseThrow(() -> new NoSuchElementException("Producto no encontrado"));
        LogoValidator.LogoValidado foto = validator.validarFoto(archivo);
        PlatoImagen imagen = imagenDao.findById(platoId).orElseGet(PlatoImagen::new);
        imagen.setPlatoId(platoId);
        imagen.setDatos(foto.datos());
        imagen.setMimeType(foto.mimeType());
        imagenDao.save(imagen);
        Long actual = plato.getImagenVersion();
        plato.setImagenVersion(Math.max(actual == null ? 1L : actual + 1, System.currentTimeMillis()));
        Plato guardado = platoDao.save(plato);
        auditoria.registrar(AccionAuditoria.PLATO_IMAGEN_ACTUALIZADA, "Plato", platoId,
            () -> guardado.getNombre() + " · Formato " + foto.mimeType() + " · " + (foto.datos().length + 1023) / 1024 + " KB");
        notificar("Foto del producto actualizada");
        return guardado;
    }

    @Transactional
    public void eliminar(Long platoId) {
        Plato plato = platoDao.findById(platoId)
            .orElseThrow(() -> new NoSuchElementException("Producto no encontrado"));
        boolean existia = imagenDao.existsById(platoId);
        if (existia) imagenDao.deleteById(platoId);
        if (plato.getImagenVersion() != null) {
            plato.setImagenVersion(null);
            platoDao.save(plato);
        }
        if (existia) {
            auditoria.registrar(AccionAuditoria.PLATO_IMAGEN_ELIMINADA, "Plato", platoId, plato.getNombre());
            notificar("Foto del producto eliminada");
        }
    }

    @Transactional(readOnly = true)
    public ImagenPlato obtener(Long platoId) {
        Plato plato = platoDao.findById(platoId)
            .orElseThrow(() -> new NoSuchElementException("Producto no encontrado"));
        if (plato.getImagenVersion() == null) throw new NoSuchElementException("Este producto no tiene foto");
        PlatoImagen imagen = imagenDao.findById(platoId)
            .orElseThrow(() -> new NoSuchElementException("Este producto no tiene foto"));
        return new ImagenPlato(Arrays.copyOf(imagen.getDatos(), imagen.getDatos().length), imagen.getMimeType(),
            plato.getImagenVersion());
    }

    @Transactional
    public void borrarDatos(Long platoId) {
        if (imagenDao.existsById(platoId)) imagenDao.deleteById(platoId);
    }

    private void notificar(String mensaje) {
        NotificacionWsDTO notificacion = NotificacionWsDTO.builder().tipo("ACTUALIZACION_MENU").mensaje(mensaje).build();
        TrasConfirmar.ejecutar(() -> messagingTemplate.convertAndSend("/topic/menu", notificacion));
    }
}
