package com.sistema.pedidosCori.models.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "pedidos")
@EntityListeners(AuditingEntityListener.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pedido {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_mesa")
    private Integer numeroMesa;

    @Column(name = "nombre_cliente")
    private String nombreCliente;

    @Column(name = "tipo")
    private String tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPedido estado;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    @Column(name = "observaciones")
    private String observaciones;

    @Column(name = "pagado", nullable = false)
    private Boolean pagado = false;

    @Column(name = "metodo_pago")
    private String metodoPago;

    @Column(nullable = false)
    private Double total = 0.0;

    @Builder.Default
    @Column(name = "ultimo_detalle_preparado_id", nullable = false, columnDefinition = "bigint default 0")
    private Long ultimoDetallePreparadoId = 0L;

    @CreatedBy
    @Column(name = "registrado_por", length = 60)
    private String registradoPor;

    @Column(name = "cobrado_por", length = 60)
    private String cobradoPor;

    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    @OneToMany(mappedBy = "pedido", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JsonIgnoreProperties("pedido")
    private List<DetallePedido> detalles;

    @PrePersist
    public void prePersist() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = EstadoPedido.PENDIENTE;
        }
        if (this.pagado == null) {
            this.pagado = false;
        }
    }
}
