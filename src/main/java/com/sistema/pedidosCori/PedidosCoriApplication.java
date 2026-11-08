package com.sistema.pedidosCori;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.sistema.pedidosCori.components.RecuperacionAcceso;

@SpringBootApplication
public class PedidosCoriApplication {

    public static void main(String[] args) {
        SpringApplication aplicacion = new SpringApplication(PedidosCoriApplication.class);
        if (RecuperacionAcceso.solicitada()) System.setProperty("server.port", "0");
        ConfigurableApplicationContext contexto = aplicacion.run(args);
        if (RecuperacionAcceso.solicitada()) {
            int codigo = contexto.getBean(RecuperacionAcceso.class).codigoSalida();
            System.exit(SpringApplication.exit(contexto, () -> codigo));
        }
    }
}
