package com.sistema.pedidosCori.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

@Component
public class LogoValidator {
    public static final int MAX_BYTES = 1_048_576;
    public static final int MAX_DIMENSION = 4096;
    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final Map<String, Set<String>> EXTENSIONES = Map.of(
        "image/png", Set.of("png"), "image/jpeg", Set.of("jpg", "jpeg"),
        "image/webp", Set.of("webp"), "image/svg+xml", Set.of("svg"));
    private static final Set<String> SVG_ELEMENTOS = Set.of("svg", "g", "path", "rect", "circle",
        "ellipse", "line", "polyline", "polygon", "text", "tspan", "defs", "linearGradient",
        "radialGradient", "stop", "clipPath");
    private static final Set<String> SVG_ATRIBUTOS = Set.of("xmlns", "viewBox", "width", "height",
        "x", "y", "x1", "x2", "y1", "y2", "cx", "cy", "r", "rx", "ry", "d", "points",
        "fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "fill-rule",
        "clip-rule", "opacity", "fill-opacity", "stroke-opacity", "transform", "id", "class",
        "offset", "stop-color", "stop-opacity", "gradientUnits", "gradientTransform",
        "clip-path", "font-size", "font-family", "font-weight", "text-anchor");

    public record LogoValidado(byte[] datos, String mimeType) {
    }

    private record Textos(boolean logo, String seleccionar, String tamano, String tipoDeclarado,
            String extension, String formato, String dimensiones, String lectura) {
    }

    private static final Textos LOGO = new Textos(true, "Selecciona un logo", "El logo no puede superar 1 MB",
        "El tipo declarado no coincide con el contenido del logo", "La extensión no coincide con el tipo real del logo",
        "El logo debe ser PNG, JPG, SVG o WebP válido", "El logo no puede superar 4096 × 4096 píxeles",
        "No se pudo leer el logo");

    private static final Textos FOTO = new Textos(false, "Selecciona una foto", "La foto no puede superar 1 MB",
        "El tipo declarado no coincide con el contenido de la foto", "La extensión no coincide con el tipo real de la foto",
        "La foto debe ser una imagen PNG, JPG o WebP válida", "La foto no puede superar 4096 × 4096 píxeles",
        "No se pudo leer la foto");

    public LogoValidado validar(MultipartFile archivo) {
        return validar(archivo, LOGO);
    }

    public LogoValidado validarFoto(MultipartFile archivo) {
        return validar(archivo, FOTO);
    }

    private LogoValidado validar(MultipartFile archivo, Textos t) {
        if (archivo == null || archivo.isEmpty()) throw new IllegalArgumentException(t.seleccionar());
        if (archivo.getSize() > MAX_BYTES) throw new IllegalArgumentException(t.tamano());
        try {
            byte[] bytes = archivo.getBytes();
            if (bytes.length > MAX_BYTES) throw new IllegalArgumentException(t.tamano());
            String mime = detectar(bytes, t);
            if (!t.logo() && mime.equals("image/svg+xml")) throw new IllegalArgumentException(t.formato());
            String declarado = archivo.getContentType();
            String declaradoNormalizado = declarado == null ? ""
                : declarado.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            boolean declaradoGenerico = !t.logo() && declaradoNormalizado.equals("application/octet-stream");
            if (!declaradoNormalizado.isEmpty() && !declaradoGenerico && !mime.equals(declaradoNormalizado)) {
                throw new IllegalArgumentException(t.tipoDeclarado());
            }
            String nombre = archivo.getOriginalFilename();
            String extension = nombre == null || !nombre.contains(".") ? ""
                : nombre.substring(nombre.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            boolean omitirExtension = !t.logo() && extension.isEmpty();
            if (!omitirExtension && !EXTENSIONES.get(mime).contains(extension)) {
                throw new IllegalArgumentException(t.extension());
            }
            if (mime.equals("image/svg+xml")) validarSvg(bytes);
            else if (mime.equals("image/webp")) validarWebp(bytes, t);
            else validarRaster(bytes, mime, t);
            return new LogoValidado(Arrays.copyOf(bytes, bytes.length), mime);
        } catch (IOException ex) {
            throw new IllegalArgumentException(t.lectura(), ex);
        }
    }

    private String detectar(byte[] bytes, Textos t) {
        if (bytes.length >= 8 && Arrays.equals(Arrays.copyOf(bytes, 8),
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10})) return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
                && (bytes[2] & 255) == 255) return "image/jpeg";
        if (bytes.length >= 12 && ascii(bytes, 0, "RIFF") && ascii(bytes, 8, "WEBP")) return "image/webp";
        String inicio = new String(bytes, 0, Math.min(bytes.length, 256), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\uFEFF", "").stripLeading();
        if (inicio.startsWith("<svg") || inicio.startsWith("<?xml")) return "image/svg+xml";
        throw new IllegalArgumentException(t.formato());
    }

    private void validarRaster(byte[] bytes, String mime, Textos t) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) throw new IllegalArgumentException("Imagen inválida");
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(input);
            if (!lectores.hasNext()) throw new IllegalArgumentException("Imagen inválida");
            ImageReader lector = lectores.next();
            try {
                String formato = lector.getFormatName().toLowerCase(Locale.ROOT);
                if (!formato.equals(mime.equals("image/png") ? "png" : "jpeg"))
                    throw new IllegalArgumentException("La imagen no coincide con su tipo real");
                lector.setInput(input, true, true);
                validarDimensiones(lector.getWidth(0), lector.getHeight(0), t);
                if (lector.read(0) == null) throw new IllegalArgumentException("Imagen inválida");
            } finally {
                lector.dispose();
            }
        }
    }

    private void validarWebp(byte[] b, Textos t) {
        if (b.length < 30 || u32(b, 4) + 8 != b.length) throw new IllegalArgumentException("WebP inválido");
        int posicion = 12;
        boolean imagen = false;
        boolean dimensiones = false;
        while (posicion + 8 <= b.length) {
            long longitud = u32(b, posicion + 4);
            long siguiente = posicion + 8L + longitud + (longitud & 1);
            if (siguiente > b.length || longitud > Integer.MAX_VALUE) throw new IllegalArgumentException("WebP inválido");
            int datos = posicion + 8;
            if (ascii(b, posicion, "VP8X")) {
                if (longitud < 10) throw new IllegalArgumentException("WebP inválido");
                validarDimensiones(1 + u24(b, datos + 4), 1 + u24(b, datos + 7), t);
                dimensiones = true;
            } else if (ascii(b, posicion, "VP8L")) {
                if (longitud < 5 || (b[datos] & 255) != 0x2F) throw new IllegalArgumentException("WebP inválido");
                int a = b[datos + 1] & 255, c = b[datos + 2] & 255;
                int d = b[datos + 3] & 255, e = b[datos + 4] & 255;
                validarDimensiones(1 + a + ((c & 63) << 8), 1 + (c >> 6) + (d << 2) + ((e & 15) << 10), t);
                dimensiones = imagen = true;
            } else if (ascii(b, posicion, "VP8 ")) {
                if (longitud < 10 || (b[datos + 3] & 255) != 0x9D || (b[datos + 4] & 255) != 1
                        || (b[datos + 5] & 255) != 0x2A) throw new IllegalArgumentException("WebP inválido");
                validarDimensiones(u16(b, datos + 6) & 0x3FFF, u16(b, datos + 8) & 0x3FFF, t);
                dimensiones = imagen = true;
            }
            posicion = (int) siguiente;
        }
        if (posicion != b.length || !imagen || !dimensiones) throw new IllegalArgumentException("WebP inválido");
    }

    private void validarSvg(byte[] bytes) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Element raiz = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getDocumentElement();
            if (!"svg".equals(raiz.getLocalName()) || !SVG_NS.equals(raiz.getNamespaceURI()))
                throw new IllegalArgumentException("SVG inválido");
            validarElementoSvg(raiz);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("SVG inválido o inseguro", ex);
        }
    }

    private void validarElementoSvg(Element elemento) {
        if (!SVG_NS.equals(elemento.getNamespaceURI()) || !SVG_ELEMENTOS.contains(elemento.getLocalName()))
            throw new IllegalArgumentException("SVG contiene elementos no permitidos");
        NamedNodeMap atributos = elemento.getAttributes();
        for (int i = 0; i < atributos.getLength(); i++) {
            Attr atributo = (Attr) atributos.item(i);
            String nombre = atributo.getName();
            String valor = atributo.getValue().toLowerCase(Locale.ROOT);
            if (!SVG_ATRIBUTOS.contains(nombre) || valor.contains("url(") || valor.contains("javascript:")
                    || valor.contains("data:") || valor.contains("@import")) {
                throw new IllegalArgumentException("SVG contiene atributos no permitidos");
            }
        }
        for (Node hijo = elemento.getFirstChild(); hijo != null; hijo = hijo.getNextSibling()) {
            if (hijo instanceof Element anidado) validarElementoSvg(anidado);
            else if (hijo.getNodeType() != Node.TEXT_NODE && hijo.getNodeType() != Node.COMMENT_NODE)
                throw new IllegalArgumentException("SVG contiene contenido no permitido");
        }
    }

    private void validarDimensiones(int ancho, int alto, Textos t) {
        if (ancho < 1 || alto < 1 || ancho > MAX_DIMENSION || alto > MAX_DIMENSION)
            throw new IllegalArgumentException(t.dimensiones());
    }

    private boolean ascii(byte[] bytes, int desde, String esperado) {
        for (int i = 0; i < esperado.length(); i++)
            if (desde + i >= bytes.length || bytes[desde + i] != esperado.charAt(i)) return false;
        return true;
    }

    private int u16(byte[] b, int i) { return (b[i] & 255) | ((b[i + 1] & 255) << 8); }
    private int u24(byte[] b, int i) { return (b[i] & 255) | ((b[i + 1] & 255) << 8) | ((b[i + 2] & 255) << 16); }
    private long u32(byte[] b, int i) { return (b[i] & 255L) | ((b[i + 1] & 255L) << 8)
        | ((b[i + 2] & 255L) << 16) | ((b[i + 3] & 255L) << 24); }
}
