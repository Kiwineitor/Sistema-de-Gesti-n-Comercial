package com.sistema.pedidosCori.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.ICategoriaDao;
import com.sistema.pedidosCori.models.dao.IPlatoDao;
import com.sistema.pedidosCori.models.dto.CategoriaRequest;
import com.sistema.pedidosCori.models.dto.CategoriaVista;
import com.sistema.pedidosCori.models.dto.NotificacionWsDTO;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;
import com.sistema.pedidosCori.models.entity.Categoria;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoriaService {
    public static final String ICONO_GENERICO = "🍽️";
    public static final int MAX_CANTIDAD_SUGERIDA = 999;

    private final ICategoriaDao dao;
    private final IPlatoDao platoDao;
    private final AuditoriaService auditoria;
    private final SimpMessagingTemplate messagingTemplate;

    private record Predeterminada(String codigo, String nombre, String icono, boolean lleva, boolean es,
            Integer sugerida) {
    }

    private static final List<Predeterminada> PREDETERMINADAS = List.of(
        new Predeterminada("SOPAS", "Sopas", "🍲", false, true, 20),
        new Predeterminada("SEGUNDOS", "Segundos", "🍛", true, false, 20),
        new Predeterminada("ENSALADAS", "Ensaladas", "🥗", false, false, 20),
        new Predeterminada("REFRESCOS", "Refrescos", "🥤", false, false, null),
        new Predeterminada("EXTRAS", "Platos extras", "🍟", false, false, 15),
        new Predeterminada("ANTOJITOS", "Antojitos", "🍮", false, false, 15));

    @Transactional
    public void inicializar() {
        if (dao.count() == 0) {
            int orden = 1;
            for (Predeterminada p : PREDETERMINADAS) {
                dao.save(Categoria.builder().codigo(p.codigo()).nombre(p.nombre()).icono(p.icono()).orden(orden++)
                    .activa(true).llevaAcompanamiento(p.lleva()).esAcompanamiento(p.es())
                    .cantidadSugerida(p.sugerida()).build());
            }
            log.info("Categorías predeterminadas creadas");
        }
        List<Categoria> todas = new ArrayList<>(dao.findAll());
        for (String valor : platoDao.categoriasDistintas()) {
            String limpio = valor == null ? "" : valor.trim();
            if (limpio.isEmpty()) continue;
            String normalizado = normalizarCodigo(limpio);
            Categoria destino = todas.stream().filter(c -> c.getCodigo().equalsIgnoreCase(limpio)).findFirst()
                .or(() -> todas.stream().filter(c -> c.getCodigo().equals(normalizado)).findFirst())
                .orElse(null);
            if (destino == null) {
                destino = dao.save(Categoria.builder()
                    .codigo(codigoUnico(normalizado))
                    .nombre(nombreUnico(nombreLegible(limpio), todas, null))
                    .icono(ICONO_GENERICO)
                    .orden(dao.maximoOrden() + 1)
                    .activa(true).llevaAcompanamiento(false).esAcompanamiento(false)
                    .build());
                todas.add(destino);
                log.info("Categoría {} creada a partir de productos existentes", destino.getCodigo());
            }
            if (!valor.equals(destino.getCodigo())) platoDao.cambiarCategoria(valor, destino.getCodigo());
        }
    }

    @Transactional(readOnly = true)
    public List<CategoriaVista> listar() {
        Map<String, Long> conteos = conteos();
        return dao.findAllByOrderByOrdenAscIdAsc().stream().map(c -> vista(c, conteos)).toList();
    }

    @Transactional(readOnly = true)
    public String codigoValido(String categoria) {
        if (categoria == null || categoria.isBlank()) throw new IllegalArgumentException("Elige una categoría válida");
        return dao.findByCodigoIgnoreCase(categoria.trim()).map(Categoria::getCodigo)
            .orElseThrow(() -> new IllegalArgumentException("Elige una categoría válida"));
    }

    @Transactional
    public CategoriaVista crear(CategoriaRequest request) {
        Datos d = validar(request, null);
        Categoria c = dao.save(Categoria.builder()
            .codigo(codigoUnico(normalizarCodigo(d.nombre())))
            .nombre(d.nombre()).icono(d.icono())
            .orden(dao.maximoOrden() + 1)
            .activa(request.activa() == null || request.activa())
            .llevaAcompanamiento(d.lleva()).esAcompanamiento(d.es())
            .cantidadSugerida(d.sugerida())
            .build());
        auditoria.registrar(AccionAuditoria.CATEGORIA_CREADA, "Categoria", c.getId(), () -> describir(c));
        notificar("Categoría creada");
        return vista(c, conteos());
    }

    @Transactional
    public CategoriaVista actualizar(Long id, CategoriaRequest request) {
        Categoria c = buscar(id);
        Datos d = validar(request, id);
        c.setNombre(d.nombre());
        c.setIcono(d.icono());
        if (request.activa() != null) c.setActiva(request.activa());
        c.setLlevaAcompanamiento(d.lleva());
        c.setEsAcompanamiento(d.es());
        c.setCantidadSugerida(d.sugerida());
        Categoria guardada = dao.save(c);
        auditoria.registrar(AccionAuditoria.CATEGORIA_EDITADA, "Categoria", guardada.getId(), () -> describir(guardada));
        notificar("Categoría actualizada");
        return vista(guardada, conteos());
    }

    @Transactional
    public List<CategoriaVista> reordenar(List<Long> ids) {
        List<Categoria> todas = dao.findAll();
        if (ids == null || ids.size() != todas.size() || new HashSet<>(ids).size() != ids.size()
                || ids.stream().anyMatch(java.util.Objects::isNull)
                || !new HashSet<>(ids).equals(new HashSet<>(todas.stream().map(Categoria::getId).toList()))) {
            throw new IllegalArgumentException("El nuevo orden debe incluir todas las categorías una sola vez");
        }
        Map<Long, Categoria> porId = new HashMap<>();
        todas.forEach(c -> porId.put(c.getId(), c));
        List<String> nombres = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Categoria c = porId.get(ids.get(i));
            c.setOrden(i + 1);
            nombres.add(c.getNombre());
        }
        dao.saveAll(todas);
        auditoria.registrar(AccionAuditoria.CATEGORIAS_REORDENADAS, "Categoria", null, () -> String.join(" → ", nombres));
        notificar("Orden de categorías actualizado");
        return listar();
    }

    @Transactional
    public void eliminar(Long id, Long moverA) {
        Categoria c = buscar(id);
        long productos = platoDao.countByCategoria(c.getCodigo());
        Categoria destino = null;
        if (moverA != null) {
            if (moverA.equals(id)) throw new IllegalArgumentException("Elige otra categoría para mover los productos");
            destino = dao.findById(moverA)
                .orElseThrow(() -> new IllegalArgumentException("Elige una categoría de destino válida"));
        }
        if (productos > 0 && destino == null) {
            throw new IllegalStateException("Esta categoría tiene " + productos
                + (productos == 1 ? " producto" : " productos")
                + ". Elige a qué categoría moverlos antes de eliminarla.");
        }
        int movidos = productos > 0 ? platoDao.cambiarCategoria(c.getCodigo(), destino.getCodigo()) : 0;
        dao.deleteById(id);
        String nombre = c.getNombre();
        String nombreDestino = destino == null ? null : destino.getNombre();
        auditoria.registrar(AccionAuditoria.CATEGORIA_ELIMINADA, "Categoria", id, () -> nombre + " (" + c.getCodigo() + ")"
            + (movidos > 0 ? " · " + movidos + (movidos == 1 ? " producto movido a " : " productos movidos a ") + nombreDestino : ""));
        notificar("Categoría eliminada");
    }

    private record Datos(String nombre, String icono, boolean lleva, boolean es, Integer sugerida) {
    }

    private Datos validar(CategoriaRequest request, Long idActual) {
        if (request == null) throw new IllegalArgumentException("Completa los datos de la categoría");
        String nombre = request.nombre() == null ? "" : request.nombre().trim().replaceAll("\\s+", " ");
        if (nombre.isEmpty()) throw new IllegalArgumentException("Escribe el nombre de la categoría");
        if (nombre.length() > Categoria.MAX_NOMBRE)
            throw new IllegalArgumentException("El nombre no puede superar " + Categoria.MAX_NOMBRE + " caracteres");
        String icono = request.icono() == null ? "" : request.icono().trim();
        if (icono.isEmpty()) throw new IllegalArgumentException("Elige un ícono para la categoría");
        if (icono.length() > Categoria.MAX_ICONO)
            throw new IllegalArgumentException("El ícono no puede superar " + Categoria.MAX_ICONO + " caracteres");
        boolean lleva = Boolean.TRUE.equals(request.llevaAcompanamiento());
        boolean es = Boolean.TRUE.equals(request.esAcompanamiento());
        if (lleva && es)
            throw new IllegalArgumentException("Una categoría no puede llevar acompañamiento y ser acompañamiento a la vez");
        Integer sugerida = request.cantidadSugerida();
        if (sugerida != null && (sugerida < 0 || sugerida > MAX_CANTIDAD_SUGERIDA))
            throw new IllegalArgumentException("La cantidad sugerida debe estar entre 0 y " + MAX_CANTIDAD_SUGERIDA);
        String clave = clave(nombre);
        if (dao.findAll().stream().anyMatch(c -> !c.getId().equals(idActual) && clave(c.getNombre()).equals(clave)))
            throw new IllegalStateException("Ya existe una categoría con ese nombre");
        return new Datos(nombre, icono, lleva, es, sugerida);
    }

    private Categoria buscar(Long id) {
        return dao.findById(id).orElseThrow(() -> new NoSuchElementException("Categoría no encontrada"));
    }

    private Map<String, Long> conteos() {
        Map<String, Long> conteos = new HashMap<>();
        for (Object[] fila : platoDao.contarActivosPorCategoria())
            conteos.put((String) fila[0], ((Number) fila[1]).longValue());
        return conteos;
    }

    private CategoriaVista vista(Categoria c, Map<String, Long> conteos) {
        return new CategoriaVista(c.getId(), c.getCodigo(), c.getNombre(), c.getIcono(), c.getOrden(), c.getActiva(),
            c.getLlevaAcompanamiento(), c.getEsAcompanamiento(), c.getCantidadSugerida(),
            conteos.getOrDefault(c.getCodigo(), 0L));
    }

    private String describir(Categoria c) {
        StringBuilder texto = new StringBuilder(c.getIcono() + " " + c.getNombre() + " (" + c.getCodigo() + ")");
        if (!Boolean.TRUE.equals(c.getActiva())) texto.append(" · Inactiva");
        if (Boolean.TRUE.equals(c.getLlevaAcompanamiento())) texto.append(" · Lleva acompañamiento");
        if (Boolean.TRUE.equals(c.getEsAcompanamiento())) texto.append(" · Es acompañamiento");
        texto.append(" · Cantidad sugerida ").append(c.getCantidadSugerida() == null ? "sin límite" : c.getCantidadSugerida());
        return texto.toString();
    }

    private void notificar(String mensaje) {
        NotificacionWsDTO notificacion = NotificacionWsDTO.builder().tipo("ACTUALIZACION_MENU").mensaje(mensaje).build();
        TrasConfirmar.ejecutar(() -> messagingTemplate.convertAndSend("/topic/menu", notificacion));
    }

    private String codigoUnico(String base) {
        String codigo = base;
        int sufijo = 2;
        while (dao.existsByCodigo(codigo)) {
            String extra = "_" + sufijo++;
            String raiz = base.length() + extra.length() > Categoria.MAX_CODIGO
                ? base.substring(0, Categoria.MAX_CODIGO - extra.length()) : base;
            codigo = raiz + extra;
        }
        return codigo;
    }

    private String nombreUnico(String base, List<Categoria> existentes, Long idActual) {
        Set<String> claves = new HashSet<>();
        existentes.stream().filter(c -> !c.getId().equals(idActual)).forEach(c -> claves.add(clave(c.getNombre())));
        String nombre = base;
        int sufijo = 2;
        while (claves.contains(clave(nombre))) {
            String extra = " " + sufijo++;
            nombre = (base.length() + extra.length() > Categoria.MAX_NOMBRE
                ? base.substring(0, Categoria.MAX_NOMBRE - extra.length()) : base) + extra;
        }
        return nombre;
    }

    public static String normalizarCodigo(String texto) {
        String codigo = sinAcentos(texto == null ? "" : texto).toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (codigo.length() > Categoria.MAX_CODIGO) codigo = codigo.substring(0, Categoria.MAX_CODIGO).replaceAll("_+$", "");
        return codigo.isEmpty() ? "CATEGORIA" : codigo;
    }

    private static String nombreLegible(String valor) {
        String texto = valor.chars().anyMatch(Character::isLowerCase) ? valor
            : valor.replace('_', ' ').toLowerCase(Locale.ROOT);
        texto = texto.trim().replaceAll("\\s+", " ");
        if (texto.isEmpty()) texto = "Categoría";
        texto = Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
        return texto.length() > Categoria.MAX_NOMBRE ? texto.substring(0, Categoria.MAX_NOMBRE).trim() : texto;
    }

    private static String clave(String nombre) {
        return sinAcentos(nombre == null ? "" : nombre).toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    private static String sinAcentos(String texto) {
        return Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
