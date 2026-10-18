(function () {
    'use strict';

    const API = '/api/admin/usuarios';
    const CLAVE_NUEVO = 'usuario-nuevo';
    const USUARIO_VALIDO = /^[a-z0-9._-]{3,40}$/;

    const estado = { usuarios: [], cargado: false, cerrarDialogo: null };
    let main, dialogo;

    const noop = () => {};
    function borradores() {
        const B = window.Borradores;
        return {
            vincular(form, clave, opciones) {
                if (B && typeof B.vincular === 'function') {
                    try { return B.vincular(form, clave, opciones) || { limpiar: noop, guardarAhora: noop, restaurado: false }; }
                    catch (e) { return { limpiar: noop, guardarAhora: noop, restaurado: false }; }
                }
                return { limpiar: noop, guardarAhora: noop, restaurado: false };
            },
            leer(clave) { try { return B && B.leer ? B.leer(clave) : null; } catch (e) { return null; } },
            borrar(clave) { try { if (B && B.borrar) B.borrar(clave); } catch (e) { } }
        };
    }

    function aviso(mensaje, tipo) {
        if (typeof window.showNotice === 'function') window.showNotice(mensaje, tipo);
    }

    function confirmar(mensaje, opciones) {
        if (typeof window.confirmAction === 'function') return window.confirmAction(mensaje, opciones);
        return Promise.resolve(false);
    }

    function etiquetaRol(rol) {
        const negocio = window.NEGOCIO || {};
        if (rol === 'ADMIN') return 'Administrador';
        if (rol === 'MESERO') return negocio.etiquetaRolMesero || main?.dataset.rolMesero || 'Mesero';
        if (rol === 'COCINA') return negocio.etiquetaRolCocina || main?.dataset.rolCocina || 'Cocina';
        return rol || '';
    }

    function claseRol(rol) {
        if (rol === 'ADMIN') return 'badge usr-badge-admin';
        if (rol === 'MESERO') return 'badge badge-info';
        return 'badge badge-warn';
    }

    function fechaHora(iso) {
        if (window.Admin && typeof window.Admin.fechaHora === 'function') {
            try { return window.Admin.fechaHora(iso); } catch (e) { }
        }
        const d = new Date(iso);
        return isNaN(d) ? '' : d.toLocaleString('es', { dateStyle: 'medium', timeStyle: 'short' });
    }

    function ultimoAcceso(iso) {
        if (!iso) return { texto: 'Nunca', titulo: 'Esta persona todavía no ha entrado' };
        const d = new Date(iso);
        if (isNaN(d)) return { texto: 'Nunca', titulo: '' };
        const completo = fechaHora(iso);
        const ahora = new Date();
        const minutos = Math.floor((ahora - d) / 60000);
        const hora = d.toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' });
        const inicioHoy = new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate());
        const inicioAyer = new Date(inicioHoy.getTime() - 86400000);
        let texto;
        if (minutos < 1) texto = 'Hace un momento';
        else if (minutos < 60) texto = `Hace ${minutos} min`;
        else if (d >= inicioHoy) texto = `Hoy, ${hora}`;
        else if (d >= inicioAyer) texto = `Ayer, ${hora}`;
        else texto = completo;
        return { texto, titulo: completo };
    }

    function crear(tag, clase, texto) {
        const el = document.createElement(tag);
        if (clase) el.className = clase;
        if (texto !== undefined && texto !== null) el.textContent = String(texto);
        return el;
    }

    async function pedir(url, opciones = {}) {
        const config = { method: opciones.method || 'GET', headers: { Accept: 'application/json' } };
        if (opciones.body !== undefined) {
            config.headers['Content-Type'] = 'application/json';
            config.body = JSON.stringify(opciones.body);
        }
        const respuesta = await window.secureFetch(url, config);
        if (!respuesta.ok) {
            if (typeof window.apiError === 'function') throw await window.apiError(respuesta);
            throw new Error(`Error HTTP ${respuesta.status}`);
        }
        if (respuesta.status === 204) return null;
        const tipo = respuesta.headers.get('Content-Type') || '';
        if (!tipo.includes('json')) {
            if (respuesta.redirected) { window.location.reload(); }
            throw new Error('Tu sesión terminó. Vuelve a entrar.');
        }
        return respuesta.json();
    }

    function adminsActivos() {
        return estado.usuarios.filter(u => u.rol === 'ADMIN' && u.activo).length;
    }

    function restricciones(u) {
        const ultimoAdmin = u.rol === 'ADMIN' && u.activo && adminsActivos() <= 1;
        if (u.esActual) return { bloqueado: true, motivo: 'No puedes desactivar, eliminar ni quitarte el rol de administrador a ti mismo.' };
        if (ultimoAdmin) return { bloqueado: true, motivo: 'Es el único administrador activo. Debe quedar al menos uno.' };
        return { bloqueado: false, motivo: '' };
    }

    function mostrarCargando(contenedor) {
        contenedor.replaceChildren();
        if (window.Admin && typeof window.Admin.cargando === 'function') {
            try { window.Admin.cargando(contenedor, 4); return; } catch (e) { }
        }
        for (let i = 0; i < 4; i++) contenedor.append(crear('div', 'admin-skeleton usr-skeleton'));
    }

    function mostrarVacio(contenedor, titulo, texto, accion) {
        contenedor.replaceChildren();
        const caja = crear('div', 'admin-empty');
        caja.append(crear('strong', null, titulo), crear('p', null, texto));
        if (accion) caja.append(accion);
        contenedor.append(caja);
    }

    async function cargar() {
        const contenedor = document.getElementById('usr-lista');
        if (!estado.cargado) mostrarCargando(contenedor);
        try {
            const datos = await pedir(API);
            estado.usuarios = Array.isArray(datos) ? datos : [];
            estado.cargado = true;
            pintarResumen();
            pintarLista();
            revisarBorradores();
        } catch (error) {
            const reintentar = crear('button', 'button-secondary usr-btn', 'Reintentar');
            reintentar.type = 'button';
            reintentar.addEventListener('click', cargar);
            mostrarVacio(contenedor, 'No se pudo cargar la lista', error.message || 'Revisa tu conexión e inténtalo otra vez.', reintentar);
        }
    }

    function pintarResumen() {
        const caja = document.getElementById('usr-stats');
        const u = estado.usuarios;
        const datos = [
            ['Personas', u.length],
            [etiquetaRol('ADMIN'), u.filter(x => x.rol === 'ADMIN').length],
            [etiquetaRol('MESERO'), u.filter(x => x.rol === 'MESERO').length],
            [etiquetaRol('COCINA'), u.filter(x => x.rol === 'COCINA').length],
            ['Desactivadas', u.filter(x => !x.activo).length]
        ];
        caja.replaceChildren(...datos.map(([etiqueta, valor]) => {
            const stat = crear('div', 'admin-stat');
            stat.append(crear('strong', null, valor), crear('span', null, etiqueta));
            return stat;
        }));
    }

    function normalizar(texto) {
        return String(texto || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
    }

    function filtrados() {
        const q = normalizar(document.getElementById('usr-buscar').value.trim());
        const rol = document.getElementById('usr-filtro-rol').value;
        const est = document.getElementById('usr-filtro-estado').value;
        return estado.usuarios.filter(u => {
            if (rol && u.rol !== rol) return false;
            if (est === 'activo' && !u.activo) return false;
            if (est === 'inactivo' && u.activo) return false;
            if (q && !normalizar(u.nombreCompleto).includes(q) && !normalizar(u.username).includes(q)) return false;
            return true;
        }).sort((a, b) => (b.esActual - a.esActual) || (b.activo - a.activo) || String(a.nombreCompleto).localeCompare(String(b.nombreCompleto), 'es'));
    }

    function boton(texto, accion, id, clase, opciones = {}) {
        const b = crear('button', `${clase} usr-btn usr-btn-fila`, texto);
        b.type = 'button';
        b.dataset.accion = accion;
        b.dataset.id = String(id);
        if (opciones.deshabilitado) {
            b.disabled = true;
            b.title = opciones.motivo;
            b.setAttribute('aria-describedby', `usr-motivo-${id}`);
        }
        return b;
    }

    function pintarLista() {
        const contenedor = document.getElementById('usr-lista');
        const resultado = document.getElementById('usr-resultado');
        const lista = filtrados();
        if (!estado.usuarios.length) {
            const agregar = crear('button', 'button-primary usr-btn', 'Agregar persona');
            agregar.type = 'button';
            agregar.addEventListener('click', () => abrirFormulario(null));
            resultado.textContent = '';
            mostrarVacio(contenedor, 'Todavía no hay cuentas', 'Agrega a tu personal para que cada persona pueda entrar con su propio usuario.', agregar);
            return;
        }
        resultado.textContent = lista.length === estado.usuarios.length
            ? `${lista.length} ${lista.length === 1 ? 'persona' : 'personas'}`
            : `Mostrando ${lista.length} de ${estado.usuarios.length}`;
        if (!lista.length) {
            const limpiar = crear('button', 'button-secondary usr-btn', 'Quitar filtros');
            limpiar.type = 'button';
            limpiar.addEventListener('click', () => {
                document.getElementById('usr-buscar').value = '';
                document.getElementById('usr-filtro-rol').value = '';
                document.getElementById('usr-filtro-estado').value = '';
                pintarLista();
            });
            mostrarVacio(contenedor, 'Nadie coincide con la búsqueda', 'Prueba con otro nombre o quita los filtros.', limpiar);
            return;
        }

        const tabla = crear('table', 'admin-table usr-tabla');
        const thead = crear('thead');
        const filaHead = crear('tr');
        ['Persona', 'Usuario', 'Rol', 'Estado', 'Último acceso', 'Acciones'].forEach(t => {
            const th = crear('th', null, t);
            th.scope = 'col';
            filaHead.append(th);
        });
        thead.append(filaHead);
        const tbody = crear('tbody');

        lista.forEach(u => {
            const tr = crear('tr', u.activo ? '' : 'usr-fila-inactiva');
            const celda = (etiqueta, ...hijos) => {
                const td = crear('td');
                td.dataset.label = etiqueta;
                td.append(...hijos);
                tr.append(td);
                return td;
            };

            const nombre = crear('span', 'usr-nombre', u.nombreCompleto);
            const persona = crear('div', 'usr-persona');
            persona.append(nombre);
            if (u.esActual) persona.append(crear('span', 'usr-tu', '(tú)'));
            celda('Persona', persona);
            celda('Usuario', crear('code', 'usr-username', u.username));
            celda('Rol', crear('span', claseRol(u.rol), etiquetaRol(u.rol)));
            celda('Estado', crear('span', u.activo ? 'badge badge-ok' : 'badge badge-off', u.activo ? 'Activo' : 'Desactivado'));
            const acceso = ultimoAcceso(u.ultimoAcceso);
            const t = crear('span', null, acceso.texto);
            if (acceso.titulo) t.title = acceso.titulo;
            celda('Último acceso', t);

            const r = restricciones(u);
            const acciones = crear('div', 'usr-acciones');
            acciones.append(
                boton('Editar', 'editar', u.id, 'button-secondary'),
                boton('Contraseña', 'password', u.id, 'button-secondary'),
                u.activo
                    ? boton('Desactivar', 'desactivar', u.id, 'button-secondary', { deshabilitado: r.bloqueado, motivo: r.motivo })
                    : boton('Activar', 'activar', u.id, 'button-secondary'),
                boton('Eliminar', 'eliminar', u.id, 'button-secondary usr-btn-peligro', { deshabilitado: r.bloqueado, motivo: r.motivo })
            );
            const td = celda('Acciones', acciones);
            if (r.bloqueado) {
                const motivo = crear('small', 'admin-help usr-motivo', r.motivo);
                motivo.id = `usr-motivo-${u.id}`;
                td.append(motivo);
            }
            tbody.append(tr);
        });

        tabla.append(thead, tbody);
        contenedor.replaceChildren(tabla);
    }

    function porId(id) {
        return estado.usuarios.find(u => String(u.id) === String(id));
    }

    function sugerirUsuario(nombre) {
        const partes = normalizar(nombre).replace(/[^a-z0-9\s]/g, ' ').trim().split(/\s+/).filter(Boolean);
        if (!partes.length) return '';
        const base = partes.length > 1 ? `${partes[0]}.${partes[1]}` : partes[0];
        let sugerido = base.slice(0, 40);
        const existentes = new Set(estado.usuarios.map(u => String(u.username).toLowerCase()));
        let n = 2;
        while (existentes.has(sugerido) && n < 100) {
            sugerido = `${base.slice(0, 37)}${n}`;
            n++;
        }
        return sugerido;
    }

    function limpiarUsuario(valor) {
        return normalizar(valor).replace(/\s+/g, '.').replace(/[^a-z0-9._-]/g, '');
    }

    function generarPassword() {
        const consonantes = 'bcdfghjkmnprstvz';
        const vocales = 'aeiou';
        const digitos = '23456789';
        const n = new Uint32Array(8);
        if (window.crypto && window.crypto.getRandomValues) window.crypto.getRandomValues(n);
        else for (let i = 0; i < n.length; i++) n[i] = Math.floor(Math.random() * 4294967296);
        let clave = '';
        for (let i = 0; i < 4; i++) clave += consonantes[n[i] % consonantes.length] + vocales[n[i + 4] % vocales.length];
        clave = clave.charAt(0).toUpperCase() + clave.slice(1);
        const extra = new Uint32Array(3);
        if (window.crypto && window.crypto.getRandomValues) window.crypto.getRandomValues(extra);
        else for (let i = 0; i < 3; i++) extra[i] = Math.floor(Math.random() * 4294967296);
        return `${clave}${digitos[extra[0] % 8]}${digitos[extra[1] % 8]}${digitos[extra[2] % 8]}`;
    }

    function mostrarError(form, campo, mensaje) {
        const el = form.querySelector(`[data-error="${campo}"]`);
        if (!el) return;
        el.textContent = mensaje || '';
        el.hidden = !mensaje;
        const input = form.elements[campo];
        if (input && input.setAttribute && !(input instanceof RadioNodeList)) input.setAttribute('aria-invalid', mensaje ? 'true' : 'false');
    }

    function prepararPassword(form) {
        const input = form.elements.password;
        const ver = form.querySelector('[data-ver-password]');
        const generar = form.querySelector('[data-generar]');
        if (!input) return;
        ver?.addEventListener('click', () => {
            const visible = input.type === 'text';
            input.type = visible ? 'password' : 'text';
            ver.textContent = visible ? 'Mostrar' : 'Ocultar';
            ver.setAttribute('aria-pressed', String(!visible));
        });
        generar?.addEventListener('click', () => {
            input.value = generarPassword();
            input.type = 'text';
            if (ver) { ver.textContent = 'Ocultar'; ver.setAttribute('aria-pressed', 'true'); }
            mostrarError(form, 'password', '');
            input.dispatchEvent(new Event('input', { bubbles: true }));
            input.focus();
        });
    }

    function abrirDialogo(contenido, alCerrar) {
        dialogo.replaceChildren(contenido);
        estado.cerrarDialogo = alCerrar;
        if (!dialogo.open) dialogo.showModal();
        const primero = dialogo.querySelector('input:not([type="hidden"]):not([disabled]):not(.visually-hidden), [data-cerrar].button-primary');
        if (primero) primero.focus();
    }

    function cerrarDialogo() {
        estado.cerrarDialogo = null;
        if (dialogo.open) dialogo.close();
        dialogo.replaceChildren();
    }

    async function intentarCerrar() {
        if (typeof estado.cerrarDialogo === 'function') await estado.cerrarDialogo();
        else cerrarDialogo();
    }

    function valoresForm(form) {
        const rol = form.querySelector('input[name="rol"]:checked');
        return {
            nombreCompleto: form.elements.nombreCompleto.value.trim(),
            username: form.elements.username ? form.elements.username.value.trim() : '',
            rol: rol ? rol.value : '',
            password: form.elements.password ? form.elements.password.value : '',
            activo: form.elements.activo ? form.elements.activo.checked : true
        };
    }

    function abrirFormulario(usuario) {
        const plantilla = document.getElementById('usr-tpl-form');
        const fragmento = plantilla.content.cloneNode(true);
        const form = fragmento.querySelector('form');
        const nuevo = !usuario;
        const clave = nuevo ? CLAVE_NUEVO : `usuario-${usuario.id}`;

        form.querySelector('h2').textContent = nuevo ? 'Agregar persona' : `Editar a ${usuario.nombreCompleto}`;
        form.querySelector('[data-intro]').textContent = nuevo
            ? 'Completa los datos. Al final te mostraremos el usuario y la contraseña para que se los entregues.'
            : 'Cambia el nombre, el rol o si la cuenta puede entrar.';
        form.querySelectorAll('[data-etiqueta-rol]').forEach(el => { el.textContent = etiquetaRol(el.dataset.etiquetaRol); });
        form.querySelectorAll('[data-solo-nuevo]').forEach(el => {
            if (nuevo) el.hidden = false;
            else el.remove();
        });
        form.querySelectorAll('[data-solo-edicion]').forEach(el => {
            if (nuevo) el.remove();
            else el.hidden = false;
        });
        form.querySelector('[data-guardar]').textContent = nuevo ? 'Crear cuenta' : 'Guardar cambios';

        let usuarioEditado = false;
        let tocado = false;

        if (!nuevo) {
            form.elements.nombreCompleto.value = usuario.nombreCompleto || '';
            form.querySelector('[data-usuario-fijo]').textContent = usuario.username;
            const radio = form.querySelector(`input[name="rol"][value="${usuario.rol}"]`);
            if (radio) radio.checked = true;
            form.elements.activo.checked = !!usuario.activo;
            const r = restricciones(usuario);
            if (r.bloqueado) {
                form.querySelectorAll('input[name="rol"]').forEach(x => { if (x.value !== 'ADMIN') x.disabled = true; });
                form.elements.activo.disabled = true;
                const ayudaRol = form.querySelector('[data-ayuda-rol]');
                ayudaRol.textContent = r.motivo;
                ayudaRol.hidden = false;
                form.querySelector('[data-ayuda-activo]').textContent = r.motivo;
            }
        } else {
            prepararPassword(form);
        }

        const pistas = form.querySelector('[data-pistas]');
        const revisarUsuario = () => {
            if (!pistas) return true;
            const v = form.elements.username.value.trim();
            const ok = {
                largo: v.length >= 3 && v.length <= 40,
                caracteres: v.length > 0 && /^[a-z0-9._-]+$/.test(v),
                libre: v.length > 0 && !estado.usuarios.some(u => String(u.username).toLowerCase() === v)
            };
            Object.entries(ok).forEach(([k, cumple]) => {
                const li = pistas.querySelector(`[data-pista="${k}"]`);
                li.classList.toggle('usr-pista-ok', cumple);
                li.classList.toggle('usr-pista-mal', v.length > 0 && !cumple);
            });
            return ok.largo && ok.caracteres && ok.libre;
        };

        const sincronizar = () => {
            if (nuevo && form.elements.username.value) usuarioEditado = form.elements.username.value !== sugerirUsuario(form.elements.nombreCompleto.value);
            revisarUsuario();
        };

        form.addEventListener('input', event => {
            tocado = true;
            const objetivo = event.target;
            if (nuevo && objetivo.name === 'nombreCompleto' && !usuarioEditado) {
                form.elements.username.value = sugerirUsuario(objetivo.value);
                revisarUsuario();
            }
            if (nuevo && objetivo.name === 'username') {
                const limpio = limpiarUsuario(objetivo.value);
                if (limpio !== objetivo.value) objetivo.value = limpio;
                usuarioEditado = limpio.length > 0;
                revisarUsuario();
            }
            if (objetivo.name && form.querySelector(`[data-error="${objetivo.name}"]`)) mostrarError(form, objetivo.name, '');
        });
        form.addEventListener('change', () => { tocado = true; });

        const borrador = borradores().vincular(form, clave, { excluir: ['password'], alRestaurar: () => { tocado = true; sincronizar(); } });
        if (borrador.restaurado) { tocado = true; sincronizar(); }

        const salir = async () => {
            if (tocado) {
                const ok = await confirmar('Lo que escribiste en este formulario no se ha guardado.', {
                    titulo: '¿Cerrar sin guardar?', aceptar: 'Cerrar y descartar', cancelar: 'Seguir editando', peligro: true
                });
                if (!ok) return;
            }
            borrador.limpiar();
            cerrarDialogo();
        };

        form.addEventListener('click', event => {
            if (event.target.closest('[data-cerrar]')) salir();
        });

        form.addEventListener('submit', async event => {
            event.preventDefault();
            const v = valoresForm(form);
            let valido = true;
            if (!v.nombreCompleto || v.nombreCompleto.length > 80) {
                mostrarError(form, 'nombreCompleto', 'Escribe el nombre de la persona (máximo 80 caracteres).');
                valido = false;
            }
            if (nuevo) {
                if (!USUARIO_VALIDO.test(v.username)) {
                    mostrarError(form, 'username', 'Revisa el usuario: entre 3 y 40 caracteres, solo minúsculas, números, punto, guion o guion bajo.');
                    valido = false;
                } else if (!revisarUsuario()) {
                    mostrarError(form, 'username', 'Ese usuario ya existe. Elige otro.');
                    valido = false;
                }
                if (v.password.length < 8 || v.password.length > 72) {
                    mostrarError(form, 'password', 'La contraseña debe tener entre 8 y 72 caracteres. Puedes usar el botón Generar.');
                    valido = false;
                }
            }
            if (!v.rol) {
                mostrarError(form, 'rol', 'Elige un rol.');
                valido = false;
            }
            if (!valido) {
                const primero = form.querySelector('.admin-error:not([hidden])');
                primero?.closest('.admin-field, fieldset')?.querySelector('input:not(.visually-hidden), input')?.focus();
                return;
            }

            const resumen = [
                { etiqueta: 'Nombre', valor: v.nombreCompleto },
                { etiqueta: 'Usuario', valor: nuevo ? v.username : usuario.username },
                { etiqueta: 'Rol', valor: etiquetaRol(v.rol) }
            ];
            if (!nuevo) resumen.push({ etiqueta: 'Estado', valor: v.activo ? 'Activa' : 'Desactivada' });
            const cambiaAcceso = !nuevo && (v.rol !== usuario.rol || (usuario.activo && !v.activo));
            const ok = await confirmar(
                nuevo ? 'Revisa que los datos estén bien antes de crear la cuenta.'
                    : (cambiaAcceso ? 'La persona tendrá que volver a entrar para usar su nueva configuración.' : 'Se guardarán los cambios.'),
                { titulo: nuevo ? '¿Crear esta cuenta?' : '¿Guardar los cambios?', resumen, aceptar: nuevo ? 'Crear cuenta' : 'Guardar', peligro: !nuevo && usuario.activo && !v.activo }
            );
            if (!ok) return;

            const guardar = form.querySelector('[data-guardar]');
            guardar.disabled = true;
            try {
                if (nuevo) {
                    const creado = await pedir(API, { method: 'POST', body: { username: v.username, nombreCompleto: v.nombreCompleto, rol: v.rol, password: v.password } });
                    borrador.limpiar();
                    mostrarCredenciales('Cuenta creada', `Entrégale estos datos a ${v.nombreCompleto} para que pueda entrar.`, (creado && creado.username) || v.username, v.password);
                    aviso('Cuenta creada correctamente.', 'ok');
                } else {
                    await pedir(`${API}/${encodeURIComponent(usuario.id)}`, { method: 'PUT', body: { nombreCompleto: v.nombreCompleto, rol: v.rol, activo: v.activo } });
                    borrador.limpiar();
                    cerrarDialogo();
                    aviso('Cambios guardados.', 'ok');
                }
                await cargar();
            } catch (error) {
                aviso(error.message || 'No se pudo guardar.', 'error');
                guardar.disabled = false;
            }
        });

        abrirDialogo(form, salir);
        ocultarAvisoBorrador(clave);
    }

    function abrirPassword(usuario) {
        const fragmento = document.getElementById('usr-tpl-password').content.cloneNode(true);
        const form = fragmento.querySelector('form');
        form.querySelector('h2').textContent = `Nueva contraseña para ${usuario.nombreCompleto}`;
        form.querySelector('[data-intro]').textContent = usuario.esActual
            ? 'Vas a cambiar tu propia contraseña. Úsala la próxima vez que entres.'
            : 'Úsalo si la persona olvidó su contraseña. Si tiene una sesión abierta, se cerrará y tendrá que entrar con la nueva.';
        prepararPassword(form);
        let tocado = false;
        form.addEventListener('input', () => { tocado = true; mostrarError(form, 'password', ''); });

        const salir = async () => {
            if (tocado && form.elements.password.value) {
                const ok = await confirmar('La contraseña no se ha cambiado todavía.', { titulo: '¿Cerrar sin guardar?', aceptar: 'Cerrar', cancelar: 'Seguir editando' });
                if (!ok) return;
            }
            cerrarDialogo();
        };
        form.addEventListener('click', event => { if (event.target.closest('[data-cerrar]')) salir(); });
        form.addEventListener('submit', async event => {
            event.preventDefault();
            const password = form.elements.password.value;
            if (password.length < 8 || password.length > 72) {
                mostrarError(form, 'password', 'La contraseña debe tener entre 8 y 72 caracteres. Puedes usar el botón Generar.');
                form.elements.password.focus();
                return;
            }
            const ok = await confirmar('La contraseña anterior dejará de funcionar.', {
                titulo: '¿Cambiar la contraseña?',
                resumen: [{ etiqueta: 'Persona', valor: usuario.nombreCompleto }, { etiqueta: 'Usuario', valor: usuario.username }],
                aceptar: 'Cambiar contraseña'
            });
            if (!ok) return;
            const guardar = form.querySelector('[data-guardar]');
            guardar.disabled = true;
            try {
                await pedir(`${API}/${encodeURIComponent(usuario.id)}/password`, { method: 'PUT', body: { password } });
                mostrarCredenciales('Contraseña cambiada', `Entrégale estos datos a ${usuario.nombreCompleto}.`, usuario.username, password);
                aviso('Contraseña cambiada.', 'ok');
                await cargar();
            } catch (error) {
                aviso(error.message || 'No se pudo cambiar la contraseña.', 'error');
                guardar.disabled = false;
            }
        });
        abrirDialogo(form, salir);
    }

    function mostrarCredenciales(titulo, intro, username, password) {
        const fragmento = document.getElementById('usr-tpl-credenciales').content.cloneNode(true);
        const caja = fragmento.querySelector('.usr-form');
        caja.querySelector('[data-titulo]').textContent = titulo;
        caja.querySelector('[data-intro]').textContent = intro;
        caja.querySelector('[data-cred-usuario]').textContent = username;
        caja.querySelector('[data-cred-password]').textContent = password;
        const copiar = caja.querySelector('[data-copiar]');
        const texto = `Usuario: ${username}\nContraseña: ${password}`;
        copiar.addEventListener('click', async () => {
            try {
                if (!navigator.clipboard || !window.isSecureContext) throw new Error('sin portapapeles');
                await navigator.clipboard.writeText(texto);
                copiar.textContent = 'Copiado';
                aviso('Usuario y contraseña copiados.', 'ok');
            } catch (e) {
                const seleccion = window.getSelection();
                const rango = document.createRange();
                rango.selectNodeContents(caja.querySelector('.usr-credenciales'));
                seleccion.removeAllRanges();
                seleccion.addRange(rango);
                aviso('No se pudo copiar automáticamente. Ya quedó seleccionado: cópialo o anótalo.', 'error');
            }
        });
        caja.addEventListener('click', event => { if (event.target.closest('[data-cerrar]')) cerrarDialogo(); });
        abrirDialogo(caja, cerrarDialogo);
    }

    async function cambiarActivo(usuario, activo) {
        const mensaje = activo
            ? 'La persona podrá volver a entrar con su usuario y contraseña.'
            : 'La persona no podrá entrar, pero su historial se conserva. Si tiene una sesión abierta, se cerrará ahora mismo. Puedes activarla de nuevo cuando quieras.';
        const ok = await confirmar(mensaje, {
            titulo: activo ? `¿Activar a ${usuario.nombreCompleto}?` : `¿Desactivar a ${usuario.nombreCompleto}?`,
            resumen: [{ etiqueta: 'Usuario', valor: usuario.username }, { etiqueta: 'Rol', valor: etiquetaRol(usuario.rol) }],
            aceptar: activo ? 'Activar' : 'Desactivar',
            peligro: !activo
        });
        if (!ok) return;
        try {
            await pedir(`${API}/${encodeURIComponent(usuario.id)}`, { method: 'PUT', body: { nombreCompleto: usuario.nombreCompleto, rol: usuario.rol, activo } });
            aviso(activo ? 'Cuenta activada.' : 'Cuenta desactivada.', 'ok');
            await cargar();
        } catch (error) {
            aviso(error.message || 'No se pudo cambiar el estado.', 'error');
        }
    }

    async function eliminar(usuario) {
        const ok = await confirmar(
            'La cuenta se borrará para siempre. Su nombre seguirá apareciendo en el historial de pedidos y actividad. Si solo quieres que no pueda entrar, es mejor usar "Desactivar": se puede deshacer.',
            {
                titulo: `¿Eliminar a ${usuario.nombreCompleto}?`,
                resumen: [{ etiqueta: 'Usuario', valor: usuario.username }, { etiqueta: 'Rol', valor: etiquetaRol(usuario.rol) }],
                aceptar: 'Eliminar para siempre',
                cancelar: 'No eliminar',
                peligro: true
            }
        );
        if (!ok) return;
        try {
            await pedir(`${API}/${encodeURIComponent(usuario.id)}`, { method: 'DELETE' });
            borradores().borrar(`usuario-${usuario.id}`);
            aviso('Cuenta eliminada.', 'ok');
            await cargar();
        } catch (error) {
            aviso(error.message || 'No se pudo eliminar.', 'error');
        }
    }

    function tieneBorrador(clave) {
        const datos = borradores().leer(clave);
        if (!datos) return false;
        if (typeof datos === 'object') return Object.keys(datos).length > 0;
        return true;
    }

    let borradorPendiente = null;

    function revisarBorradores() {
        if (dialogo.open) return;
        const caja = document.getElementById('usr-aviso-borrador');
        let pendiente = null;
        if (tieneBorrador(CLAVE_NUEVO)) pendiente = { clave: CLAVE_NUEVO, usuario: null };
        else {
            const u = estado.usuarios.find(x => tieneBorrador(`usuario-${x.id}`));
            if (u) pendiente = { clave: `usuario-${u.id}`, usuario: u };
        }
        borradorPendiente = pendiente;
        if (!pendiente) { caja.hidden = true; return; }
        document.getElementById('usr-aviso-texto').textContent = pendiente.usuario
            ? `Dejaste sin guardar unos cambios en la cuenta de ${pendiente.usuario.nombreCompleto}. ¿Quieres continuar donde lo dejaste?`
            : 'Dejaste sin terminar el alta de una persona nueva. ¿Quieres continuar donde lo dejaste?';
        caja.hidden = false;
    }

    function ocultarAvisoBorrador(clave) {
        if (borradorPendiente && borradorPendiente.clave === clave) {
            document.getElementById('usr-aviso-borrador').hidden = true;
            borradorPendiente = null;
        }
    }

    function iniciar() {
        main = document.getElementById('contenido');
        dialogo = document.getElementById('usr-dialogo');
        if (!main || !dialogo) return;

        dialogo.addEventListener('cancel', event => { event.preventDefault(); intentarCerrar(); });
        dialogo.addEventListener('click', event => {
            if (event.target !== dialogo) return;
            const r = dialogo.getBoundingClientRect();
            const fuera = event.clientX < r.left || event.clientX > r.right || event.clientY < r.top || event.clientY > r.bottom;
            if (fuera) intentarCerrar();
        });
        dialogo.addEventListener('close', () => { if (!dialogo.open) revisarBorradores(); });

        document.getElementById('usr-agregar').addEventListener('click', () => abrirFormulario(null));
        document.getElementById('usr-buscar').addEventListener('input', pintarLista);
        document.getElementById('usr-filtro-rol').addEventListener('change', pintarLista);
        document.getElementById('usr-filtro-estado').addEventListener('change', pintarLista);

        document.getElementById('usr-lista').addEventListener('click', event => {
            const b = event.target.closest('button[data-accion]');
            if (!b || b.disabled) return;
            const u = porId(b.dataset.id);
            if (!u) return;
            if (b.dataset.accion === 'editar') abrirFormulario(u);
            else if (b.dataset.accion === 'password') abrirPassword(u);
            else if (b.dataset.accion === 'desactivar') cambiarActivo(u, false);
            else if (b.dataset.accion === 'activar') cambiarActivo(u, true);
            else if (b.dataset.accion === 'eliminar') eliminar(u);
        });

        document.getElementById('usr-aviso-continuar').addEventListener('click', () => {
            if (!borradorPendiente) return;
            abrirFormulario(borradorPendiente.usuario);
        });
        document.getElementById('usr-aviso-descartar').addEventListener('click', () => {
            if (!borradorPendiente) return;
            borradores().borrar(borradorPendiente.clave);
            borradorPendiente = null;
            revisarBorradores();
        });

        cargar();
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar);
    else iniciar();
})();
