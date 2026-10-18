package com.sistema.pedidosCori.models.entity;

public enum AccionAuditoria {
    PEDIDO_CREADO("Pedido creado"),
    PEDIDO_ITEMS_AGREGADOS("Productos agregados al pedido"),
    PEDIDO_ESTADO("Cambio de estado del pedido"),
    PEDIDO_COBRADO("Pedido cobrado"),
    PLATO_CREADO("Producto creado"),
    PLATO_EDITADO("Producto editado"),
    PLATO_ELIMINADO("Producto eliminado"),
    PLATO_ARCHIVADO("Producto archivado"),
    PRESENTACION_CREADA("Presentación creada"),
    PRESENTACION_EDITADA("Presentación editada"),
    PRESENTACION_ELIMINADA("Presentación eliminada"),
    PLATO_IMAGEN_ACTUALIZADA("Foto del producto actualizada"),
    PLATO_IMAGEN_ELIMINADA("Foto del producto eliminada"),
    CATEGORIA_CREADA("Categoría creada"),
    CATEGORIA_EDITADA("Categoría editada"),
    CATEGORIA_ELIMINADA("Categoría eliminada"),
    CATEGORIAS_REORDENADAS("Orden de categorías cambiado"),
    MENU_PUBLICADO("Menú del día publicado"),
    CONFIGURACION_ACTUALIZADA("Configuración actualizada"),
    LOGO_ACTUALIZADO("Logo actualizado"),
    CONFIGURACION_RESTABLECIDA("Configuración restablecida"),
    USUARIO_CREADO("Usuario creado"),
    USUARIO_EDITADO("Usuario editado"),
    USUARIO_ACTIVADO("Usuario activado"),
    USUARIO_DESACTIVADO("Usuario desactivado"),
    USUARIO_ELIMINADO("Usuario eliminado"),
    PASSWORD_RESTABLECIDA("Contraseña restablecida"),
    LOGIN("Inicio de sesión"),
    LOGIN_FALLIDO("Intento de acceso fallido");

    private final String etiqueta;

    AccionAuditoria(String etiqueta) { this.etiqueta = etiqueta; }

    public String getEtiqueta() { return etiqueta; }

    public static String etiquetaDe(String codigo) {
        for (AccionAuditoria a : values()) if (a.name().equals(codigo)) return a.etiqueta;
        return codigo;
    }
}
