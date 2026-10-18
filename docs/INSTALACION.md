# Instalación y mantenimiento

Guía para la persona que instala el sistema en el local y lo mantiene funcionando: requisitos, configuración, acceso desde los celulares, puesta en producción, respaldos y actualizaciones.

## 1. Requisitos

- Una computadora que quede encendida mientras el local atiende (Windows, Linux o macOS).
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) o Docker Engine con Docker Compose.
- Los celulares y tablets del personal conectados a la **misma red Wi‑Fi** que esa computadora.

La aplicación y la base de datos PostgreSQL corren en contenedores; no hace falta instalar Java ni PostgreSQL aparte.

## 2. Primera instalación

### Para probar o revisar

1. Descarga el proyecto en la computadora.
2. En una terminal, dentro de la carpeta del proyecto, ejecuta:

   ```bash
   docker compose up -d --build
   ```

3. Abre `http://localhost:8080` e ingresa con los usuarios de demostración: `admin` / `admin123`, `mesero` / `mesero123` y `cocina` / `cocina123`.

No hace falta crear `.env`: Docker trae su propia base de datos y un menú de ejemplo.

### Para usarlo en el negocio

1. Copia `.env.example` como `.env`.
2. Edita `.env` y cambia, como mínimo:
   - `DB_PASSWORD`: contraseña de la base de datos.
   - `SEED_ADMIN_PASSWORD`: contraseña del usuario `admin` (el dueño).
   - `SEED_MESERO_PASSWORD` y `SEED_COCINA_PASSWORD`: contraseñas de los usuarios iniciales `mesero` y `cocina`.
   - `SEED_DEMO_DATA=false` si no quieres que se cargue un menú de ejemplo.
3. Ejecuta `docker compose up -d --build` y entra con tus contraseñas.

Las contraseñas `SEED_*` solo se usan la **primera vez**, cuando la base de datos está vacía. Después, el dueño administra las cuentas desde **Administración → Usuarios**; cambiar esas variables no modifica cuentas existentes. En el perfil `prod` el sistema **no arranca** si las contraseñas faltan o son las de demostración (`admin123`, `mesero123`, `cocina123`).

Si en el perfil `dev` dejaste alguna contraseña `SEED_*` vacía o con el valor `CAMBIAR_…`, el sistema genera una aleatoria y la muestra **una sola vez** en el registro:

```bash
docker compose logs app | grep "contraseña inicial"
```

## 3. Variables de `.env`

| Variable | Para qué sirve | Valor por defecto |
| --- | --- | --- |
| `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Base de datos interna. | `ekwe_iyawe`, `ekwe`, `ekwe_pass` |
| `DB_PORT` | Puerto para conectarse a la base desde la misma computadora (solo `127.0.0.1`). | `5434` |
| `APP_PORT` | Puerto de la aplicación. | `8080` |
| `SPRING_PROFILES_ACTIVE` | `dev` para pruebas, `prod` para producción. | `dev` |
| `SESSION_COOKIE_SECURE` | `true` solo si se accede por HTTPS. | `false` |
| `FORWARD_HEADERS_STRATEGY` | `native` si hay un proxy inverso con HTTPS delante. | `none` |
| `LOGIN_MAX_ATTEMPTS`, `LOGIN_BLOCK_MINUTES` | Intentos fallidos permitidos y minutos de bloqueo. | `5`, `15` |
| `SEED_ADMIN_PASSWORD`, `SEED_MESERO_PASSWORD`, `SEED_COCINA_PASSWORD` | Contraseñas de las cuentas iniciales. | `admin123`, `mesero123`, `cocina123` (solo demostración) |
| `SEED_DEMO_DATA` | Carga un menú de ejemplo si no hay productos. | `true` |
| `ADMINER_PORT` | Visor web opcional de la base de datos. | `8081` |

Los nombres internos `ekwe_iyawe` y el proyecto de Compose `ekwe-iyawe` se conservan por compatibilidad con instalaciones anteriores; no afectan lo que ve el negocio.

## 4. Usar el sistema desde los celulares del local

1. Averigua la IP de la computadora en la red local:
   - Windows: `ipconfig` → "Dirección IPv4" (por ejemplo `192.168.1.50`).
   - Linux/macOS: `ip addr` o `ifconfig`.
2. En Windows, permite el puerto en el firewall (una sola vez, en una terminal como administrador):

   ```powershell
   netsh advfirewall firewall add rule name="Gestion Comercial" dir=in action=allow protocol=TCP localport=8080
   ```

3. En cada celular, abre `http://192.168.1.50:8080` (con la IP de tu computadora).
4. Recomendado: en el navegador del celular, usa "Agregar a pantalla de inicio" para abrirlo como una aplicación.

Mientras se use por `http://` en la red local, `SESSION_COOKIE_SECURE` debe quedar en `false`; con `true` el navegador no guarda la sesión y el inicio de sesión no funciona. Con Docker Compose el valor por defecto ya es `false`; si ejecutas la aplicación sin Docker con el perfil `prod`, el valor por defecto es `true` y debes definir `SESSION_COOKIE_SECURE=false` para usarla por `http://`.

Conviene asignar una IP fija a la computadora en el router para que la dirección no cambie.

## 5. Producción con acceso desde internet

Para publicar el sistema fuera del local:

1. En `.env`: `SPRING_PROFILES_ACTIVE=prod`, `SEED_DEMO_DATA=false` y contraseñas fuertes. En `prod` el sistema no arranca si faltan las contraseñas `SEED_*`.
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

El límite de intentos de inicio de sesión usa la IP que entrega el proxy; por eso `FORWARD_HEADERS_STRATEGY=native` solo debe usarse detrás de un proxy de confianza.

## 6. Respaldos

Todo vive en la base de datos: pedidos, productos, **fotos**, logo, configuración, usuarios y registro de actividad. Un respaldo de la base es un respaldo completo.

**Crear un respaldo** (desde la carpeta del proyecto):

```bash
docker compose exec -T db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB"' > respaldo-$(date +%F).dump
```

En Windows (PowerShell), genera el respaldo dentro del contenedor y cópialo; no uses `>` porque PowerShell altera los archivos binarios:

```powershell
docker compose exec db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_dump -U "$POSTGRES_USER" -Fc "$POSTGRES_DB" -f /tmp/respaldo.dump'
docker compose cp db:/tmp/respaldo.dump "respaldo-$(Get-Date -Format yyyy-MM-dd).dump"
```

**Recomendaciones:**

- Haz un respaldo diario al cerrar y antes de cada actualización.
- Guarda copias fuera de la computadora (memoria USB, nube).
- En Windows puedes automatizarlo con el Programador de tareas ejecutando el comando anterior todos los días.

**Restaurar un respaldo** (reemplaza los datos actuales):

```bash
docker compose stop app
docker compose cp respaldo.dump db:/tmp/respaldo.dump
docker compose exec db sh -c 'PGPASSWORD="$POSTGRES_PASSWORD" pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists /tmp/respaldo.dump'
docker compose start app
```

Comprueba pedidos, productos y usuarios después de restaurar.

## 7. Actualizar a una versión nueva

1. Haz un respaldo (sección 6).
2. Descarga la versión nueva del proyecto (por ejemplo `git pull`).
3. Ejecuta `docker compose up -d --build`.

Al arrancar, el sistema agrega por sí mismo las tablas y columnas nuevas, y crea las categorías iniciales si no existen. Los datos existentes se conservan.

## 8. Visor de la base de datos (opcional)

```bash
docker compose --profile tools up -d
```

Abre `http://localhost:8081`, servidor `db`, con el usuario y la contraseña de `.env`. Úsalo solo para consultas; los cambios deben hacerse desde la aplicación.

## 9. Problemas frecuentes

| Problema | Qué hacer |
| --- | --- |
| No abre en el celular | Verifica que esté en la misma Wi‑Fi, que la IP sea correcta y que el firewall permita el puerto 8080. |
| El inicio de sesión vuelve siempre a la pantalla de ingreso | Si usas `http://`, `SESSION_COOKIE_SECURE` debe ser `false`. |
| "Demasiados intentos" | Espera los minutos indicados (`LOGIN_BLOCK_MINUTES`) o pide a un administrador que revise la cuenta. |
| Alguien olvidó su contraseña | El administrador la restablece en **Administración → Usuarios → Contraseña**. |
| El dueño olvidó la contraseña de `admin` y no hay otro administrador | Ver el procedimiento técnico debajo. |
| La página no refleja cambios | Recarga la página; si persiste, revisa `docker compose logs app`. |

**Restablecer la contraseña de un administrador desde la terminal** (solo si no queda ningún otro administrador con acceso):

```bash
docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 "" 'NuevaClaveSegura123' | tr -d ':\n'
```

Copia el resultado (empieza con `$2y$`) y aplícalo:

```bash
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "update usuarios set password = '"'"'{bcrypt}PEGA_AQUI_EL_HASH'"'"', activo = true where username = '"'"'admin'"'"';"'
```

Ingresa con la contraseña nueva y cámbiala desde **Usuarios**.
