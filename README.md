# Sistema de Gestión Comercial

Sistema para tomar pedidos, prepararlos, entregarlos y cobrarlos, pensado para restaurantes y cualquier negocio que atienda en mesas, mostrador o para llevar. Cada negocio lo adapta desde el panel de administración: nombre, logo, colores, cómo llama a sus mesas, categorías de productos, fotos y usuarios.

## Qué incluye

- **Vista de atención (mesero):** pensada para el celular. Toma pedidos por mesa o para llevar, con cantidades, tamaños, acompañamientos y notas por comensal. Muestra un catálogo con fotos al cliente, cobra y entrega.
- **Vista de preparación (cocina):** tablero con pedidos pendientes, en preparación y listos, que se actualiza en tiempo real.
- **Panel de administración:** inicio con el resumen del día, menú de hoy, productos con foto, categorías, mesas, usuarios, reportes de ventas, registro de actividad y apariencia.
- **Catálogo para el cliente:** fotos grandes, descripciones y disponibilidad del día.
- **Reportes:** ventas por día, semana o mes con comparación, productos más vendidos, ventas por empleado, métodos de pago, horarios fuertes y exportación a Excel (CSV).
- **Seguridad y auditoría:** acceso por roles, límite de intentos de inicio de sesión, protección CSRF, registro de quién hizo cada acción y de quién registró y cobró cada pedido.
- **Modo claro y oscuro:** cada persona elige el suyo en su menú de sesión.

## Guías

| Documento | Para quién |
| --- | --- |
| [Guía del dueño](docs/GUIA-DEL-DUENO.md) | Quien administra el negocio: preparar el día, productos, fotos, usuarios y reportes. |
| [Guía del personal](docs/GUIA-DEL-PERSONAL.md) | Meseros y cocina: tomar pedidos, mostrar el catálogo, cobrar y preparar. |
| [Instalación y mantenimiento](docs/INSTALACION.md) | Quien instala el sistema: Docker, variables, acceso desde celulares, producción, respaldos y actualizaciones. |
| [Desarrollo](docs/DESARROLLO.md) | Programadores: arquitectura, convenciones, API y pruebas. |

## Inicio rápido con Docker

```bash
docker compose up -d --build
```

Abre `http://localhost:8080`. Docker incluye su propia base de datos PostgreSQL y un menú de ejemplo; no hace falta configurar nada para probar.

| Usuario | Contraseña de demostración | Vista |
| --- | --- | --- |
| `admin` | `admin123` | Panel de administración |
| `mesero` | `mesero123` | Toma de pedidos |
| `cocina` | `cocina123` | Preparación |

Estas contraseñas solo se usan al crear la base por primera vez y sirven para desarrollo y revisión. En producción (`SPRING_PROFILES_ACTIVE=prod`) el sistema no arranca con ellas: define otras en un archivo `.env` (ver `.env.example`). Si el puerto 8080 está ocupado por otro programa, define `APP_PORT` en `.env`.

Los detalles, incluido cómo entrar desde los celulares del local, están en [Instalación y mantenimiento](docs/INSTALACION.md).

## Roles

| Rol | Qué ve |
| --- | --- |
| Administrador (`ADMIN`) | Todo: panel de administración, catálogo, vista de atención y vista de preparación. |
| Atención (`MESERO`) | Vista de atención y catálogo. Toma pedidos, cobra y entrega. |
| Preparación (`COCINA`) | Vista de preparación. |

Los nombres que ve el personal ("Mesero", "Cocina", "Mesa"…) se cambian en **Administración → Mi local**.

## Stack

Java 21 · Spring Boot 4 · Spring Security · Spring Data JPA · PostgreSQL 16 · Thymeleaf con HTML, CSS y JavaScript sin frameworks · WebSocket STOMP/SockJS · Maven Wrapper · Docker Compose.

## Desarrollo local

```bash
./mvnw spring-boot:run        # requiere PostgreSQL y las variables PG*
./mvnw -DskipTests compile
./mvnw test                   # usa H2, no necesita PostgreSQL
```

En Windows usa `.\mvnw.cmd`. Más detalles en [Desarrollo](docs/DESARROLLO.md).
