package com.sistema.pedidosCori.models.dto;

public record ConfiguracionNegocioRequest(
    String nombreNegocio,
    String eslogan,
    String colorPrimario,
    String colorSecundario,
    String colorAcento,
    String puntoAtencionSingular,
    String puntoAtencionPlural,
    String etiquetaParaLlevar,
    String etiquetaRolMesero,
    String etiquetaRolCocina,
    String simboloMoneda,
    Integer cantidadPuntosAtencion
) {
}
