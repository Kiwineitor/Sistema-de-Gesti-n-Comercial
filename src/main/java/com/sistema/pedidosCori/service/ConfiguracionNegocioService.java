package com.sistema.pedidosCori.service;

import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistema.pedidosCori.models.dao.IConfiguracionNegocioDao;
import com.sistema.pedidosCori.models.dto.ConfiguracionNegocioRequest;
import com.sistema.pedidosCori.models.dto.ConfiguracionNegocioVista;
import com.sistema.pedidosCori.models.entity.ConfiguracionNegocio;
import com.sistema.pedidosCori.models.entity.AccionAuditoria;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConfiguracionNegocioService {
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private final IConfiguracionNegocioDao dao;
    private final LogoValidator logoValidator;
    private final AuditoriaService auditoria;

    @Bean
    ApplicationRunner iniciarConfiguracion(ConfiguracionNegocioService service) {
        return args -> service.inicializar();
    }

    @Transactional
    public void inicializar() {
        if (!dao.existsById(1L)) dao.save(nuevaPorDefecto());
    }

    @Transactional(readOnly = true)
    public ConfiguracionNegocioVista vista() {
        ConfiguracionNegocio c = actual();
        Map<String, String> tema = TemaNegocio.variables(c.getColorPrimario(), c.getColorSecundario(), c.getColorAcento());
        return new ConfiguracionNegocioVista(c.getNombreNegocio(), c.getEslogan(), c.getColorPrimario(),
            c.getColorSecundario(), c.getColorAcento(), c.getPuntoAtencionSingular(), c.getPuntoAtencionPlural(),
            c.getEtiquetaParaLlevar(), c.getEtiquetaRolMesero(), c.getEtiquetaRolCocina(), c.getSimboloMoneda(),
            c.getCantidadPuntosAtencion(), "/branding/logo?v=" + c.getLogoVersion(), c.getLogoMimeType(), tema);
    }

    @Transactional(readOnly = true)
    public ConfiguracionNegocio logoActual() { return actual(); }

    @Transactional
    public ConfiguracionNegocioVista actualizar(ConfiguracionNegocioRequest r) {
        if (r == null) throw new IllegalArgumentException("Configuración requerida");
        String nombre = texto(r.nombreNegocio(), "nombre del negocio", 120);
        String eslogan = r.eslogan() == null ? "" : r.eslogan().trim();
        if (eslogan.length() > 240) throw new IllegalArgumentException("El eslogan no puede superar 240 caracteres");
        String primario = color(r.colorPrimario(), "primario");
        String secundario = color(r.colorSecundario(), "secundario");
        String acento = color(r.colorAcento(), "acento");
        String singular = texto(r.puntoAtencionSingular(), "punto de atención singular", 40);
        String plural = texto(r.puntoAtencionPlural(), "punto de atención plural", 40);
        String llevar = texto(r.etiquetaParaLlevar(), "etiqueta para llevar", 40);
        String mesero = texto(r.etiquetaRolMesero(), "etiqueta del rol mesero", 40);
        String cocina = texto(r.etiquetaRolCocina(), "etiqueta del rol cocina", 40);
        String moneda = texto(r.simboloMoneda(), "símbolo de moneda", 12);
        if (r.cantidadPuntosAtencion() == null || r.cantidadPuntosAtencion() < 0 || r.cantidadPuntosAtencion() > 200)
            throw new IllegalArgumentException("La cantidad de puntos de atención debe estar entre 0 y 200");
        ConfiguracionNegocio c = actual();
        c.setNombreNegocio(nombre); c.setEslogan(eslogan);
        c.setColorPrimario(primario); c.setColorSecundario(secundario); c.setColorAcento(acento);
        c.setPuntoAtencionSingular(singular); c.setPuntoAtencionPlural(plural);
        c.setEtiquetaParaLlevar(llevar); c.setEtiquetaRolMesero(mesero);
        c.setEtiquetaRolCocina(cocina); c.setSimboloMoneda(moneda);
        c.setCantidadPuntosAtencion(r.cantidadPuntosAtencion());
        dao.saveAndFlush(c);
        auditoria.registrar(AccionAuditoria.CONFIGURACION_ACTUALIZADA, "ConfiguracionNegocio", c.getId(),
            "Nombre: " + nombre + " · " + r.cantidadPuntosAtencion() + " " + plural.toLowerCase(java.util.Locale.ROOT)
                + " · Colores " + primario + ", " + secundario + ", " + acento);
        return vista();
    }

    @Transactional
    public ConfiguracionNegocioVista actualizarLogo(org.springframework.web.multipart.MultipartFile archivo) {
        LogoValidator.LogoValidado logo = logoValidator.validar(archivo);
        ConfiguracionNegocio c = actual();
        c.setLogoDatos(logo.datos());
        c.setLogoMimeType(logo.mimeType());
        c.setLogoVersion(c.getLogoVersion() + 1);
        dao.saveAndFlush(c);
        auditoria.registrar(AccionAuditoria.LOGO_ACTUALIZADO, "ConfiguracionNegocio", c.getId(),
            "Formato " + logo.mimeType() + " · " + (logo.datos() == null ? 0 : (logo.datos().length + 1023) / 1024) + " KB");
        return vista();
    }

    @Transactional
    public ConfiguracionNegocioVista restablecer() {
        ConfiguracionNegocio c = actual();
        ConfiguracionNegocio defaults = nuevaPorDefecto();
        c.setNombreNegocio(defaults.getNombreNegocio()); c.setEslogan(defaults.getEslogan());
        c.setColorPrimario(defaults.getColorPrimario()); c.setColorSecundario(defaults.getColorSecundario());
        c.setColorAcento(defaults.getColorAcento());
        c.setPuntoAtencionSingular(defaults.getPuntoAtencionSingular());
        c.setPuntoAtencionPlural(defaults.getPuntoAtencionPlural());
        c.setEtiquetaParaLlevar(defaults.getEtiquetaParaLlevar());
        c.setEtiquetaRolMesero(defaults.getEtiquetaRolMesero());
        c.setEtiquetaRolCocina(defaults.getEtiquetaRolCocina());
        c.setSimboloMoneda(defaults.getSimboloMoneda());
        c.setCantidadPuntosAtencion(defaults.getCantidadPuntosAtencion());
        c.setLogoDatos(null); c.setLogoMimeType(null);
        c.setLogoVersion(c.getLogoVersion() + 1);
        dao.saveAndFlush(c);
        auditoria.registrar(AccionAuditoria.CONFIGURACION_RESTABLECIDA, "ConfiguracionNegocio", c.getId(),
            "Se restauraron los valores predeterminados y el logo");
        return vista();
    }

    private ConfiguracionNegocio actual() {
        return dao.findById(1L).orElseThrow(() -> new IllegalStateException("Falta la configuración del negocio"));
    }

    private String texto(String valor, String campo, int max) {
        if (valor == null || valor.isBlank() || valor.trim().length() > max)
            throw new IllegalArgumentException("El campo " + campo + " es obligatorio y admite hasta " + max + " caracteres");
        return valor.trim();
    }

    private String color(String valor, String campo) {
        if (valor == null || !HEX.matcher(valor).matches())
            throw new IllegalArgumentException("El color " + campo + " debe tener formato #RRGGBB");
        return valor.toUpperCase(java.util.Locale.ROOT);
    }

    private ConfiguracionNegocio nuevaPorDefecto() {
        ConfiguracionNegocio c = new ConfiguracionNegocio();
        c.setNombreNegocio("Gestión Comercial"); c.setEslogan("");
        c.setColorPrimario("#24324A"); c.setColorSecundario("#64748B"); c.setColorAcento("#C56A3D");
        c.setPuntoAtencionSingular("Mesa"); c.setPuntoAtencionPlural("Mesas");
        c.setEtiquetaParaLlevar("Para llevar"); c.setEtiquetaRolMesero("Mesero");
        c.setEtiquetaRolCocina("Cocina"); c.setSimboloMoneda("Bs.");
        c.setCantidadPuntosAtencion(20);
        return c;
    }
}
