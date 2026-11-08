# Instalación y mantenimiento

Guía para quien instala el sistema y lo mantiene funcionando: requisitos, primer arranque, entrega al cliente, acceso desde celulares, producción, respaldos, actualizaciones y recuperación de acceso.

## 1. Requisitos

- Una computadora o servidor que quede encendido mientras el local atiende (Windows, Linux o macOS).
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (Windows/macOS) o Docker Engine con Docker Compose (Linux). En Windows, Docker Desktop debe estar **abierto** antes de usar los comandos.
- [Git](https://git-scm.com/) para descargar y actualizar el proyecto.
- Los celulares y tablets del personal conectados a la **misma red Wi‑Fi** que esa computadora.

La aplicación y su base de datos PostgreSQL corren en contenedores; no hace falta instalar Java ni PostgreSQL.

## 2. Primer arranque (pruebas y desarrollo)

```bash
git clone https://github.com/Kiwineitor/Sistema-de-Gesti-n-Comercial.git
cd Sistema-de-Gesti-n-Comercial
docker compose up -d --build
```

La primera vez tarda varios minutos (descarga imágenes y compila). Cuando termine, abre `http://localhost:8080`.

### Credenciales de primer arranque

Se crean **solo cuando la base de datos está vacía**:

| Usuario | Contraseña temporal | Rol |
| --- | --- | --- |
| `admin` | `admin123` | Administrador |
| `mesero` | `mesero123` | Atención |
| `cocina` | `cocina123` | Preparación |

Son **contraseñas temporales**: en el primer ingreso cada cuenta debe crear su propia contraseña y aceptar los Términos y condiciones de uso. Hasta completarlo, el sistema no permite usar ninguna otra pantalla ni la API.

- La nueva contraseña debe tener al menos 8 caracteres, combinar letras y números, no contener el nombre de usuario y no ser una contraseña común (por ejemplo `admin123` o `12345678`).
- Si una base existente todavía tiene alguna de estas contraseñas de demostración, el sistema la marca al arrancar y obliga a cambiarla en el siguiente ingreso.
- Para volver a empezar con la base vacía (se **borran todos los datos**): `docker compose down -v` y luego `docker compose up -d --build`.

Docker carga además un menú de ejemplo. No hace falta crear `.env` para probar.

## 3. Instalación para el negocio (entrega al cliente)

1. Copia `.env.example` como `.env` y edita como mínimo:
   - `SPRING_PROFILES_ACTIVE=prod`
   - `DB_PASSWORD`: una contraseña larga para la base de datos.
   - `SEED_ADMIN_PASSWORD`: contraseña **temporal** del usuario `admin`. Se usa una sola vez.
   - `SEED_DEMO_DATA=false` para no cargar el menú de ejemplo.
   - Deja vacías `SEED_MESERO_PASSWORD` y `SEED_COCINA_PASSWORD`: en `prod` solo se crea `admin`; el dueño crea luego las cuentas de su personal desde **Usuarios**.
2. Ejecuta `docker compose up -d --build`.
3. En `prod` el sistema **no arranca** si falta `SEED_ADMIN_PASSWORD` o si es una contraseña de demostración.

### Entrega segura: el proveedor no conserva accesos

El sistema está diseñado para que, una vez entregado, **solo el cliente** tenga acceso:

1. Instala con una `SEED_ADMIN_PASSWORD` temporal.
2. En la entrega, el dueño ingresa con `admin` y esa contraseña temporal **en su propio dispositivo**, crea su contraseña personal y acepta los términos. Desde ese momento el proveedor ya no la conoce.
3. Recomienda al dueño crear un **segundo administrador** de confianza (por ejemplo, el encargado), para que uno pueda asignar una contraseña temporal al otro si la olvida.
4. Entrega al cliente: el manual de usuario (`docs/Manual-de-Usuario.pdf`), esta guía y el archivo `.env` (contiene la contraseña de la base de datos). No te quedes con copias del `.env` ni de respaldos de la base.
5. Cualquier soporte posterior que requiera ver el sistema o los datos debe autorizarlo el cliente **por escrito** (cláusula 4 de los Términos).

Los Términos y condiciones de uso están en la ruta `/terminos` del sistema y cada usuario los acepta en su primer ingreso; la aceptación queda registrada en **Actividad**.

## 4. Variables de `.env`

| Variable | Para qué sirve | Valor por defecto |
| --- | --- | --- |
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Base de datos interna. | `ekwe_iyawe`, `ekwe`, `ekwe_pass` |
| `DB_PORT` | Puerto para conectarse a la base desde la misma computadora (solo `127.0.0.1`). | `5434` |
| `APP_PORT` | Puerto de la aplicación. | `8080` |
| `SPRING_PROFILES_ACTIVE` | `dev` para pruebas, `prod` para producción. | `dev` |
| `SESSION_COOKIE_SECURE` | `true` solo si se accede por HTTPS. | `false` |
| `FORWARD_HEADERS_STRATEGY` | `native` si hay un proxy inverso con HTTPS delante. | `none` |
| `LOGIN_MAX_ATTEMPTS`, `LOGIN_BLOCK_MINUTES` | Intentos fallidos permitidos y minutos de bloqueo. | `5`, `15` |
| `SEED_ADMIN_PASSWORD` | Contraseña temporal de `admin` al crear la base. | `admin123` (solo `dev`) |
| `SEED_MESERO_PASSWORD`, `SEED_COCINA_PASSWORD` | Contraseñas temporales de `mesero` y `cocina`. En `prod` solo se crean si se definen. | `mesero123`, `cocina123` (solo `dev`) |
| `SEED_DEMO_DATA` | Carga un menú de ejemplo si no hay productos. | `true` |
| `ADMINER_PORT` | Visor web opcional de la base de datos. | `8081` |

Las variables `SEED_*` solo se usan cuando la base está vacía. Cambiarlas después no modifica cuentas existentes.

## 5. Usar el sistema desde los celulares del local

1. Averigua la IP de la computadora en la red local:
   - Windows: `ipconfig` → «Dirección IPv4» (por ejemplo `192.168.1.50`).
   - Linux/macOS: `ip addr` o `ifconfig`.
2. En Windows, permite el puerto en el firewall (una sola vez, en una terminal como administrador):

   ```powershell
   netsh advfirewall firewall add rule name="Gestion Comercial" dir=in action=allow protocol=TCP localport=8080
   ```

3. En cada celular abre `http://192.168.1.50:8080` (con la IP de tu computadora).
4. Recomendado: «Agregar a pantalla de inicio» en el navegador del celular.

Mientras se use por `http://` en la red local, `SESSION_COOKIE_SECURE` debe quedar en `false`; con `true` el navegador no guarda la sesión. Conviene asignar una IP fija a la computadora en el router.

El sistema no depende de internet: todas sus librerías se sirven desde la propia instalación.

## 6. Producción con acceso desde internet

1. En `.env`: `SPRING_PROFILES_ACTIVE=prod`, `SEED_DEMO_DATA=false` y contraseñas fuertes.
2. Pon delante un proxy inverso con HTTPS (por ejemplo Caddy o Nginx) que reenvíe a `http://localhost:8080`.
3. Configura `FORWARD_HEADERS_STRATEGY=native` y `SESSION_COOKIE_SECURE=true`.
4. No expongas el puerto de la base de datos; por defecto solo escucha en `127.0.0.1`.
5. Reinicia: `docker compose up -d`.

Ejemplo mínimo de Caddy (`Caddyfile`):

```
pedidos.minegocio.com {
    reverse_proxy localhost:8080
}
```

`FORWARD_HEADERS_STRATEGY=native` solo debe usarse detrás de un proxy de confianza, porque el límite de intentos de ingreso usa la IP que este informa.

## 7. Respaldos

Todo vive en la base de datos: pedidos, productos, **fotos**, logo, configuración, usuarios y registro de actividad. Un respaldo de la base es un respaldo completo.

**Crear un respaldo** (Linux/macOS, desde la carpeta del proyecto):

```bash
docker compose exec -T db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > respaldo-$(date +%F).dump
```

En Windows (PowerShell) genera el respaldo dentro del contenedor y cópialo; no uses `>` porque PowerShell altera los archivos binarios:

```powershell
docker compose exec db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB" -f /tmp/respaldo.dump'
docker compose cp db:/tmp/respaldo.dump "respaldo-$(Get-Date -Format yyyy-MM-dd).dump"
```

- Haz un respaldo diario al cerrar y antes de cada actualización.
- Guarda copias fuera de la computadora (memoria USB, nube del negocio).
- En Windows puedes automatizarlo con el Programador de tareas.

**Restaurar un respaldo** (reemplaza los datos actuales):

```bash
docker compose stop app
docker compose cp respaldo.dump db:/tmp/respaldo.dump
docker compose exec db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists /tmp/respaldo.dump'
docker compose start app
```

## 8. Actualizar a una versión nueva

1. Haz un respaldo (sección 7).
2. Descarga la versión nueva: `git pull`.
3. Reconstruye y reinicia: `docker compose up -d --build`.

Al arrancar, el sistema agrega solo las tablas y columnas nuevas. Los datos existentes se conservan. Si la versión de los Términos cambió, cada usuario deberá aceptarla en su siguiente ingreso.

## 9. Recuperar el acceso de un administrador

Úsalo solo si **ningún** administrador puede entrar (si queda uno, que asigne una contraseña temporal desde **Usuarios → Contraseña**).

Desde la carpeta del proyecto, en la computadora o servidor donde está instalado:

```bash
docker compose run --rm -e RECUPERAR_ACCESO=admin app
```

Cambia `admin` por el usuario administrador que quieras recuperar. El comando arranca una instancia temporal, reactiva la cuenta, emite una **contraseña temporal** y termina. Al final verás:

```
================================================================
  Acceso recuperado
================================================================
  Usuario: admin
  Contraseña temporal: Xk7m-9Qp2-Rt4w
  Al ingresar, el sistema pedirá elegir una contraseña nueva.
  Esta contraseña no se guarda en ningún registro: anótala ahora.
================================================================
```

- La contraseña temporal solo aparece en esa terminal; no queda en los registros del servidor.
- Al ingresar con ella, el sistema obliga a crear una contraseña nueva.
- Si el usuario no existe, se crea como administrador. Si existe pero no es administrador, el comando lo rechaza (las cuentas del personal se restablecen desde **Usuarios**).
- La acción queda registrada en **Actividad** como «Acceso recuperado desde el servidor».
- Si al ingresar aparece «Demasiados intentos», espera los minutos indicados o reinicia la aplicación con `docker compose restart app`.

Este procedimiento requiere acceso a la computadora o servidor del negocio; no existe ninguna otra vía de acceso.

## 10. Visor de la base de datos (opcional)

```bash
docker compose --profile tools up -d
```

Abre `http://localhost:8081`, servidor `db`, con el usuario y la contraseña de `.env`. Úsalo solo para consultas; los cambios deben hacerse desde la aplicación. Apágalo cuando no lo uses: `docker compose --profile tools stop adminer`.

## 11. Problemas frecuentes

| Problema | Qué hacer |
| --- | --- |
| `docker` no responde o da error de conexión | Abre Docker Desktop y espera a que diga «Running». |
| El puerto 8080 está ocupado | Define otro en `.env`, por ejemplo `APP_PORT=8085`, y vuelve a ejecutar `docker compose up -d`. |
| No abre en el celular | Misma Wi‑Fi, IP correcta y puerto permitido en el firewall. |
| El ingreso vuelve siempre a la pantalla de ingreso | Si usas `http://`, `SESSION_COOKIE_SECURE` debe ser `false`. |
| «Demasiados intentos» | Espera los minutos indicados (`LOGIN_BLOCK_MINUTES`). |
| Alguien olvidó su contraseña | Un administrador le asigna una temporal en **Usuarios → Contraseña**. |
| Ningún administrador puede entrar | Sección 9. |
| Con `prod` el sistema no arranca | Revisa `docker compose logs app`: probablemente falta `SEED_ADMIN_PASSWORD` o es de demostración. |
| La página no refleja cambios | Recarga; si persiste, revisa `docker compose logs app`. |
