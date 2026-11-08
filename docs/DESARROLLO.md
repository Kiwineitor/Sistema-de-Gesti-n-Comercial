# Desarrollo

Referencia técnica para quien mantenga o extienda el sistema: arquitectura, modelo de datos, convenciones, API y pruebas.

## Arquitectura

Aplicación monolítica Spring Boot que sirve las vistas Thymeleaf y una API JSON, con WebSocket STOMP/SockJS para las actualizaciones en tiempo real. El frontend es HTML, CSS y JavaScript sin frameworks ni proceso de build.

```text
src/main/java/com/sistema/pedidosCori/
  components/   DataInitializer (usuarios iniciales, categorías, menú de ejemplo) y
                RecuperacionAcceso (contraseña temporal emitida desde el servidor)
  config/       SecurityConfig, WebSocketConfig, AuditoriaConfig, LoginThrottleFilter,
                CuentaPendienteFilter
  controller/   Páginas (PageController, AdminPageController, CuentaController), API (ApiController,
                AdminApiController, CategoriaController, PlatoImagenController,
                ConfiguracionNegocioController), WsController, advices y manejo de errores
  models/       entity, dao (Spring Data JPA) y dto
  service/      Pedidos, usuarios, cuenta (primer ingreso), política de contraseñas,
                términos, categorías, fotos, configuración, tema, auditoría, resumen y reportes
src/main/resources/
  templates/    login, cuenta, terminos, mesero, cocina, catalogo, admin/* y fragments/*
  static/css/   theme.css (tokens), admin.css (panel), catalogo.css y una hoja por página
  static/js/    security.js, tema.js, sesion.js, branding.js, borradores.js, cuenta.js,
                admin-shell.js, catalogo.js, un script por página del panel y vendor/
                (SockJS, STOMP y html2canvas servidos localmente, sin CDN)
src/test/       Pruebas con H2 (src/test/resources/application.properties)
```

El esquema lo mantiene Hibernate (`spring.jpa.hibernate.ddl-auto=update`): las columnas nuevas deben ser nulables o tener valor por defecto para no romper bases existentes.

## Modelo de datos

| Entidad | Notas |
| --- | --- |
| `Pedido` | `tipo` MESA/LLEVAR, `numeroMesa` o `nombreCliente`, `estado`, `pagado`, `metodoPago`, `total`, `ultimoDetallePreparadoId`. Auditoría: `registradoPor`, `cobradoPor`, `fechaPago`. |
| `DetallePedido` | `plato`, `presentacion` (tamaño), `acompananteSopa` (acompañamiento), `cantidad` (1–99), `notas` (≤ 120), precio y subtotal. |
| `Plato` | `categoria` guarda el **código** de una `Categoria`. `disponible`, `stockDisponible` (null = sin límite), `archivado`, `imagenVersion` e `imagenUrl` calculada. |
| `PresentacionPlato` | Tamaños con precio, stock y `modalidad` (MESA, LLEVAR, AMBAS). |
| `Categoria` | `codigo` inmutable, `nombre`, `icono`, `orden`, `activa`, `llevaAcompanamiento`, `esAcompanamiento`, `cantidadSugerida`. |
| `PlatoImagen` | Bytes de la foto (`bytea`), separada de `Plato` para no cargarla en las listas. |
| `ConfiguracionNegocio` | Registro único con marca, colores, etiquetas, cantidad de puntos de atención, moneda y logo. |
| `Usuario` | `rol` ADMIN, MESERO o COCINA; `activo`; `ultimoAcceso`; `debeCambiarPassword`; `passwordActualizadaEn`; `terminosVersion` y `terminosAceptadosEn`. |
| `RegistroAuditoria` | Fecha, usuario, acción (`AccionAuditoria`), entidad, id y detalle legible. |

**Flujo de pedidos:** COCINA pasa PENDIENTE → EN_PREPARACION → LISTO; MESERO pasa LISTO → ENTREGADO; ADMIN puede hacer ambas cosas. El cobro es independiente del estado. Agregar productos a un pedido no pagado en EN_PREPARACION, LISTO o ENTREGADO lo devuelve a PENDIENTE, y `ultimoDetallePreparadoId` permite resaltar en cocina lo nuevo. El dominio no tiene anulación de pedidos.

**Stock:** al crear o ampliar un pedido se descuenta el stock del tamaño si la línea tiene tamaño y, si no, el del plato; también el del acompañamiento. Los cambios se publican en `/topic/stock`.

## Convenciones

- **Sin comentarios en el código.** Nombres claros en español en lugar de comentarios.
- **JavaScript sin frameworks.** Sin CDN: las librerías de terceros van en `static/js/vendor/` (la CSP solo permite `self`). Sin dependencias nuevas de Maven sin justificarlo.
- **Seguridad en el backend:** toda ruta nueva se declara en `SecurityConfig` con su rol; ocultar algo en la interfaz no es seguridad.
- **Peticiones mutantes** con `secureFetch()` (añade el token CSRF). Errores de API con `apiError()`; las respuestas de error son `{mensaje, codigo}`.
- **Nunca interpolar datos en `innerHTML` sin `escapeHtml()`**; preferir `textContent` y APIs del DOM. Nada de `onclick` con datos interpolados: usar `data-*` y delegación.
- **Sin `alert`, `confirm` ni `prompt`:** usar `showNotice(mensaje, 'ok'|'error')` y `confirmAction(mensaje, {titulo, resumen, items, total, aceptar, cancelar, peligro})`. Toda acción que cambia datos se confirma con un resumen.
- **Colores solo con tokens de `theme.css`.** No hay colores literales fuera de ese archivo (lo verifica `TemaVisualTests`). Reglas de uso:
  - Texto: `--text-main` y `--text-muted`.
  - Fondos suaves de estado: `--status-X-soft` + `--status-X-text` + `--status-X-border`.
  - Botones sólidos: `--action-primary` / `--action-accent` con su `-on`.
  - Superficies: `--surface-*`.
  - Los tokens de marca (`--brand-*`) los genera `TemaNegocio` a partir de los colores configurados, con variantes calculadas para claro y oscuro que garantizan el contraste.
- **Modo claro/oscuro:** `data-theme` en `<html>`, aplicado antes de pintar desde `fragments/head.html`; la preferencia se guarda en `localStorage` (`tema`).
- **Interacción:** todos los `button` y `.btn` tienen elevación y estados hover/press globales; `.flat` los desactiva. Animaciones cortas que respetan `prefers-reduced-motion`.
- **Formularios del panel:** `Borradores.vincular(form, clave)` guarda lo escrito por usuario y formulario para no perderlo al cambiar de sección.
- **Móvil primero:** objetivos táctiles de 44 px como mínimo, campos con fuente de 16 px o más y sin desbordamiento horizontal a 360 px.

## Rutas y permisos

| Ruta | Roles |
| --- | --- |
| `/login`, `/terminos`, `/css/**`, `/js/**`, `/img/**`, `/branding/logo` | Público |
| `/cuenta` | Cualquier usuario autenticado (primer ingreso y cambio de contraseña propia) |
| `/mesero`, `/catalogo` | ADMIN, MESERO |
| `/cocina` | ADMIN, COCINA |
| `/admin/**` (inicio, menu-hoy, productos, categorias, local, usuarios, reportes, actividad, apariencia) | ADMIN |

### API

| Método y ruta | Roles | Uso |
| --- | --- | --- |
| `GET /api/platos` | ADMIN, MESERO | Productos a la venta hoy. |
| `GET /api/platos/todos` | ADMIN | Catálogo completo. |
| `POST/PUT/DELETE /api/platos/**`, `/api/presentaciones/**` | ADMIN | Productos y tamaños. |
| `GET /api/platos/{id}/imagen` | ADMIN, MESERO, COCINA | Foto (caché inmutable con `?v=`). |
| `PUT/DELETE /api/admin/platos/{id}/imagen` | ADMIN | Subir o quitar foto (PNG, JPEG, WebP ≤ 1 MB). |
| `GET /api/categorias` | ADMIN, MESERO, COCINA | Categorías ordenadas. |
| `POST/PUT/DELETE /api/admin/categorias/**` | ADMIN | Administrar categorías; `PUT .../orden`, `DELETE ...?moverA=`. |
| `POST /api/menu-diario` | ADMIN | Publicar el menú del día. |
| `POST /api/pedidos`, `POST /api/pedidos/{id}/detalles` | ADMIN, MESERO | Crear o ampliar pedidos. |
| `PUT /api/pedidos/{id}/estado` | ADMIN, COCINA, MESERO | Cambiar estado según el flujo. |
| `PUT /api/pedidos/{id}/pagar?metodoPago=` | ADMIN, MESERO | Cobrar. |
| `GET /api/pedidos/activos`, `/api/pedidos/{id}` | ADMIN, MESERO, COCINA | Consultas. |
| `GET /api/pedidos/cocina` | ADMIN, COCINA | Tablero de cocina. |
| `GET /api/mesas/ocupadas` | ADMIN, MESERO | Mesas con pedidos abiertos. |
| `GET/PUT /api/admin/configuracion`, `PUT .../logo`, `POST .../restablecer` | ADMIN | Configuración del negocio. |
| `GET/POST/PUT/DELETE /api/admin/usuarios/**` | ADMIN | Usuarios; `PUT .../{id}/password` asigna una contraseña temporal. Cambios de rol, estado o contraseña expiran las sesiones del usuario. |
| `GET /api/admin/auditoria`, `.../acciones`, `.../usuarios` | ADMIN | Registro de actividad paginado y filtrable. |
| `GET /api/admin/resumen-hoy` | ADMIN | Resumen del día para el Inicio. |
| `GET /api/admin/reportes`, `GET /api/admin/reportes/csv?tipo=pedidos\|productos\|empleados` | ADMIN | Reportes y exportación CSV. |

**WebSocket** (`/ws`, STOMP): `/topic/cocina`, `/topic/mesero`, `/topic/stock` (`{platoId, presentacionId?, stockRestante}`) y `/topic/menu` (`ACTUALIZACION_MENU` tras cambios de menú, productos o categorías).

## Seguridad de acceso

- **Primer ingreso obligatorio:** las cuentas creadas por el sistema o por un administrador, y las contraseñas restablecidas, quedan con `debeCambiarPassword = true`. Al iniciar sesión, si falta cambiar la contraseña o aceptar la versión vigente de los términos (`Terminos.VERSION`), el login marca la sesión y `CuentaPendienteFilter` redirige toda página a `/cuenta` y responde `403 CUENTA_PENDIENTE` en la API y el WebSocket.
- **Política de contraseñas** (`PoliticaPassword`): 8 a 72 bytes, letras y números, sin espacios al inicio o al final, sin el nombre de usuario y fuera de una lista de contraseñas comunes. El navegador replica estas reglas, pero la validación que cuenta es la del servidor.
- **Contraseñas de demostración:** al arrancar, si `admin`, `mesero` o `cocina` conservan su contraseña de demostración, se marcan para cambio obligatorio. En `prod` no se arranca con `SEED_ADMIN_PASSWORD` vacía o de demostración, y `mesero`/`cocina` solo se crean si se definen sus variables.
- **Términos:** el texto está en `templates/terminos.html`. Al modificarlo de forma sustancial, sube `Terminos.VERSION` y `Terminos.VIGENTE_DESDE`: todos los usuarios deberán aceptarlo de nuevo. Cada aceptación se audita con versión e IP.
- **Cambio de contraseña propio:** exige la contraseña actual y expira las demás sesiones del usuario.
- **Recuperación:** `RECUPERAR_ACCESO=<usuario>` arranca la aplicación en un puerto aleatorio, emite una contraseña temporal para un administrador (o lo crea si no existe), la imprime solo en la consola, la audita y termina con código 0 (2 si el usuario no es administrador). No hay credenciales maestras ni accesos del proveedor.
- **Cabeceras:** CSP (`default-src 'self'`, `frame-ancestors 'self'`, `object-src 'none'`), `X-Frame-Options`, `Referrer-Policy: same-origin`, `Permissions-Policy`, cookie de sesión `HttpOnly` y `SameSite=Lax` (`Secure` en `prod`), sesión solo por cookie y errores sin trazas.

## Reportes: definiciones

- **Ventas:** pedidos con `pagado = true` cuya fecha de cobro (`fechaPago`, o `fechaCreacion` en registros antiguos sin ella) está en el período.
- **Pedidos registrados:** pedidos creados en el período. **Por cobrar:** creados en el período y aún no pagados.
- Productos, categorías, métodos de pago, horarios y modalidades se calculan sobre las ventas cobradas, así que todos los totales cuadran con las ventas.
- Por empleado: las ventas se atribuyen a quien registró el pedido; `cobros` cuenta los pagos registrados por cada persona.
- CSV con BOM UTF-8, separador `;` y coma decimal, compatible con Excel en español; se neutralizan celdas que empiezan con `=`, `+`, `-` o `@`.

## Pruebas

```bash
./mvnw test     # .\mvnw.cmd test en Windows
```

Usan H2 en memoria. Destacan:

| Clase | Cubre |
| --- | --- |
| `SecurityAuthorizationTests` | Permisos por rol, CSRF, flujo de pedidos y bloqueo de inicio de sesión. |
| `PrimerIngresoTests` | Primer ingreso obligatorio, términos, política de contraseñas, cambio propio, restablecimiento, cabeceras de seguridad y recuperación desde el servidor. |
| `PedidoServiceImplTests` | Transiciones de estado, cobro, validación de cantidad, nota y tamaño. |
| `AdminGestionTests` | Usuarios, sesiones, auditoría y resumen del día. |
| `CategoriasYFotosTests` | Categorías, migración inicial y fotos. |
| `ReportesAvanzadosTests` | Reportes, períodos, comparación y CSV. |
| `AdminVistasTests`, `CatalogoVistaTests` | Renderizado y acceso a las páginas. |
| `TemaVisualTests` | Contraste de los tokens en claro y oscuro, variables CSS definidas y ausencia de colores literales. |
| `ConfiguracionNegocioTests` | Configuración, logo y variantes de color de marca. |

Antes de entregar un cambio: compilar, ejecutar `./mvnw test` y probar manualmente en el navegador en claro y oscuro, en computadora y en celular.

## Capturas del manual

Las imágenes de `docs/manual/img` se generan con Playwright contra una instancia con datos de ejemplo: cada marca numerada se dibuja sobre el elemento real y su texto alimenta la tabla del manual. Si cambia una pantalla, vuelve a generar la captura correspondiente para que los números sigan coincidiendo.
