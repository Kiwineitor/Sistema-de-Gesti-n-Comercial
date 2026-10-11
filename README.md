<div align="center">

# 🍽️ Ekwe Iyawe

### Sistema de Gestión de Pedidos y Ventas para Restaurantes

*Gestiona pedidos, cocina, stock y ganancias en tiempo real desde un solo lugar.*

[![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)](https://www.java.com/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Thymeleaf](https://img.shields.io/badge/Thymeleaf-005F0F?style=for-the-badge&logo=thymeleaf&logoColor=white)](https://www.thymeleaf.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-316192?style=for-the-badge&logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![WebSocket](https://img.shields.io/badge/WebSocket-Tiempo_Real-010101?style=for-the-badge&logo=socket.io&logoColor=white)](https://developer.mozilla.org/en-US/docs/Web/API/WebSockets_API)
[![Railway](https://img.shields.io/badge/Railway-Deployed-0B0D0E?style=for-the-badge&logo=railway&logoColor=white)](https://ekwe-iyawe.up.railway.app)

[🌐 Ver Demo en Vivo](https://ekwe-iyawe.up.railway.app) · [🐛 Reportar Bug](https://github.com/Srkiwi2/ekwe-iyawe/issues) · [✨ Solicitar Feature](https://github.com/Srkiwi2/ekwe-iyawe/issues)

</div>

---

## 📸 Capturas del Sistema

<div align="center">
  <img src="screenshots/1e.jpeg" width="45%" />
  <img src="screenshots/2e.jpeg" width="45%" />
  <br><br>
  <img src="screenshots/3e.jpeg" width="45%" />
  <img src="screenshots/4e.jpeg" width="45%" />
  <br><br>
  <img src="screenshots/5e.jpeg" width="45%" />
  <img src="screenshots/6e.jpeg" width="45%" />
</div>

<div align="center">

### 🧑‍💼 Panel del Mesero — Inicio de Pedido
![Panel Mesero](screenshots/mesero-inicio.png)
*Selección del tipo de pedido: En Mesa, Para Llevar, Pedidos Activos o Panel del Dueño*

</div>

> 💡 *¿Quieres ver más? Prueba la [demo en vivo](https://ekwe-iyawe.up.railway.app)*

---

## 🧩 ¿Qué problema resuelve?

Los restaurantes pequeños y medianos suelen gestionar sus pedidos de forma manual, lo que genera errores, retrasos en cocina y pérdida de control sobre las ventas. **Ekwe Iyawe** digitaliza y automatiza todo el flujo: desde que el cliente hace el pedido hasta que la cocina lo prepara y el dueño revisa las ganancias del día.

---

## ✨ Funcionalidades Principales

### 🧑‍💼 Módulo de Mesero / Recepcionista
- [x] Registrar nuevo pedido **En Mesa** (selección de mesa)
- [x] Registrar nuevo pedido **Para Llevar**
- [x] Ver y gestionar **pedidos activos**
- [x] Cobrar pedidos y cerrar cuentas

### 👨‍🍳 Módulo de Cocina (Tiempo Real)
- [x] Visualización de pedidos en tiempo real vía **WebSocket**
- [x] Actualizar el estado de cada plato (pendiente → en preparación → listo)
- [x] Flujo ordenado y eficiente para el equipo de cocina

### 👔 Panel del Dueño
- [x] Configurar el **menú** (platos, precios, categorías)
- [x] Control de **stock** de ingredientes y platos disponibles
- [x] Reportes de **ventas diarias**
- [x] Control de **ganancias** y resumen financiero

### ⚙️ General
- [x] Interfaz responsiva y amigable
- [x] Actualizaciones en tiempo real sin recargar la página (WebSocket)
- [x] Base de datos robusta con PostgreSQL
- [x] Desplegado en la nube con Railway

---

## 🛠️ Stack Tecnológico

| Capa | Tecnología |
|------|-----------|
| **Backend** | Java 17 + Spring Boot 3 |
| **Frontend** | Thymeleaf + HTML/CSS/JS |
| **Base de Datos** | PostgreSQL |
| **Tiempo Real** | WebSocket (STOMP) |
| **Deploy** | Railway |
| **Build** | Maven |

---

## 🐳 Ejecución con Docker (recomendado)

Solo necesitas [Docker Desktop](https://www.docker.com/products/docker-desktop/). La base de datos PostgreSQL corre en su propio contenedor, con los datos en un volumen persistente.

```bash
cp .env.example .env        # opcional: ajusta puertos y contraseñas
docker compose up -d --build
```

Abre **http://localhost:8080** e ingresa con alguno de estos usuarios:

| Usuario | Contraseña | Rol | Qué puede ver |
|---------|-----------|-----|---------------|
| `admin`  | `admin123`  | Dueño  | Todas las vistas: Mesero, Cocina y Panel del Menú (sin PIN) |
| `mesero` | `mesero123` | Mesero | Toma de pedidos, cobros y reportes; el panel del menú requiere el PIN |
| `cocina` | `cocina123` | Cocina | Solo la pantalla de cocina en tiempo real |

- Los usuarios se crean al primer arranque si no existen. Para cambiar sus contraseñas, define `SEED_*_PASSWORD` en `.env` **antes** del primer arranque.
- Con `SEED_DEMO_DATA=true` (por defecto en Docker) se carga un menú de ejemplo si la tabla de platos está vacía.
- El botón 👤 en el borde izquierdo de cada vista muestra el usuario, permite cambiar de vista y cerrar sesión.
- PIN del panel del dueño: `ADMIN_PIN` (por defecto `1234`).

Comandos útiles:

```bash
docker compose logs -f app                 # ver logs
docker compose --profile tools up -d       # visor web de la BD (Adminer) en http://localhost:8081
docker compose down                        # detener (conserva los datos)
docker compose down -v                     # detener y BORRAR la base de datos
```

La BD también es accesible desde el host en `localhost:5434` (usuario `ekwe`, contraseña `ekwe_pass`, base `ekwe_iyawe`).

---

## 🚀 Instalación y Ejecución Local

### Prerrequisitos

Asegúrate de tener instalado:

- [Java 17+](https://adoptium.net/)
- [Maven 3.8+](https://maven.apache.org/)
- [PostgreSQL 14+](https://www.postgresql.org/download/)

### Pasos

**1. Clona el repositorio**
```bash
git clone https://github.com/Srkiwi2/ekwe-iyawe.git
cd ekwe-iyawe
```

**2. Crea la base de datos en PostgreSQL**
```sql
CREATE DATABASE ekwe_iyawe;
```

**3. Configura la conexión a la base de datos**

Por defecto se conecta a `localhost:5432/ekwe_iyawe` con usuario y contraseña `postgres`. Si los tuyos son distintos, define las variables de entorno:
```bash
PGHOST=localhost PGPORT=5432 PGDATABASE=ekwe_iyawe PGUSER=TU_USUARIO PGPASSWORD=TU_CONTRASEÑA
```

**4. Compila y ejecuta el proyecto**
```bash
mvn clean install
mvn spring-boot:run
```

**5. Abre en tu navegador**
```
http://localhost:8080
```

---

## 📁 Estructura del Proyecto

```
ekwe-iyawe/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/ekweiyawe/
│   │   │       ├── controller/     # Controladores Spring MVC
│   │   │       ├── model/          # Entidades JPA
│   │   │       ├── repository/     # Repositorios JPA
│   │   │       ├── service/        # Lógica de negocio
│   │   │       └── websocket/      # Configuración WebSocket
│   │   └── resources/
│   │       ├── templates/          # Vistas Thymeleaf
│   │       ├── static/             # CSS, JS, imágenes
│   │       └── application.properties
├── pom.xml
└── README.md
```

---

## 🌐 Demo en Vivo

El sistema está desplegado y disponible en:

**👉 [https://ekwe-iyawe.up.railway.app](https://ekwe-iyawe.up.railway.app)**

| Rol | Descripción |
|-----|------------|
| 🧑‍💼 Mesero | Gestión de pedidos en mesa y para llevar |
| 👨‍🍳 Cocina | Vista de pedidos en tiempo real |
| 👔 Dueño | Panel de administración, menú y reportes |

---

## 👤 Autor

**Srkiwi2**

[![GitHub](https://img.shields.io/badge/GitHub-Srkiwi2-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/Srkiwi2)

---

## 📄 Licencia

Este proyecto fue desarrollado como sistema freelance para un negocio privado.
Todos los derechos reservados © 2025 Srkiwi2.

---

<div align="center">

*Hecho con ❤️ y mucho café ☕ — Bolivia 🇧🇴*

</div>
