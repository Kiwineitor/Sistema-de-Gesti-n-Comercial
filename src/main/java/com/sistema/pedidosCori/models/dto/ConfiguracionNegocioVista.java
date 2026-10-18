package com.sistema.pedidosCori.models.dto;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ConfiguracionNegocioVista {
    private final String nombreNegocio;
    private final String eslogan;
    private final String colorPrimario;
    private final String colorSecundario;
    private final String colorAcento;
    private final String puntoAtencionSingular;
    private final String puntoAtencionPlural;
    private final String etiquetaParaLlevar;
    private final String etiquetaRolMesero;
    private final String etiquetaRolCocina;
    private final String simboloMoneda;
    private final Integer cantidadPuntosAtencion;
    private final String logoUrl;
    private final String logoMimeType;
    private final Map<String, String> tema;
}
