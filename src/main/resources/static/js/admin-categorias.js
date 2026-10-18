(function () {
    'use strict';

    const API_LISTA = '/api/categorias';
    const API = '/api/admin/categorias';
    const CLAVE_NUEVA = 'categoria-nueva';
    const ESPERA_ORDEN = 800;
    const EMOJIS = [
        { grupo: 'Comidas', lista: ['🍽️', '🍲', '🍛', '🥗', '🍟', '🍕', '🍔', '🌮', '🍣', '🍜', '🥘', '🍗', '🥩', '🐟', '🍝', '🥪', '🌭', '🍳', '🥞', '🧇', '🥐', '🍞', '🥟', '🍚', '🍱', '🥙', '🌯', '🍿', '🍉', '🍓', '🍎', '🍌', '🥑', '🥕', '🌽'] },
        { grupo: 'Bebidas', lista: ['🥤', '☕', '🍵', '🧃', '🧋', '🥛', '🍺', '🍷', '🍸', '🍹', '🧉', '💧'] },
        { grupo: 'Postres', lista: ['🍮', '🧁', '🍰', '🎂', '🍩', '🍪', '🍫', '🍦', '🍨', '🍧'] },
        { grupo: 'Otros negocios', lista: ['🛒', '🎁', '💊', '🧴', '📦', '🧺', '👕', '✂️', '🔧', '📚', '✏️', '⭐'] }
    ];

    const estado = {
        categorias: [],
        guardadas: [],
        cargado: false,
        cerrarDialogo: null,
        temporizadorOrden: null,
        guardandoOrden: false,
        ordenPendiente: false
    };
    let main, dialogo, contenido, estadoOrden, anunciador;

    function crear(tag, clase, texto) {
        const el = document.createElement(tag);
        if (clase) el.className = clase;
        if (texto !== undefined && texto !== null) el.textContent = String(texto);
        return el;
    }

    function aviso(mensaje, tipo) {
        if (typeof window.showNotice === 'function') window.showNotice(mensaje, tipo);
    }

    function confirmar(mensaje, opciones) {
        if (typeof window.confirmAction === 'function') return window.confirmAction(mensaje, opciones);
        return Promise.resolve(false);
    }

    const noop = () => {};
    const vinculoVacio = { restaurado: false, limpiar: noop, guardarAhora: noop, sucio: () => false, desvincular: noop };
    function vincularBorrador(form, clave, opciones) {
        const B = window.Borradores;
        if (!B || typeof B.vincular !== 'function') return vinculoVacio;
        try { return B.vincular(form, clave, opciones) || vinculoVacio; } catch (e) { return vinculoVacio; }
    }
    function leerBorrador(clave) {
        try { return window.Borradores ? window.Borradores.leer(clave) : null; } catch (e) { return null; }
    }
    function borrarBorrador(clave) {
        try { if (window.Borradores) window.Borradores.borrar(clave); } catch (e) { }
    }

    async function pedir(url, opciones = {}) {
        const config = { method: opciones.method || 'GET', headers: { Accept: 'application/json' } };
        if (opciones.keepalive) config.keepalive = true;
        if (opciones.body !== undefined) {
            config.headers['Content-Type'] = 'application/json';
            config.body = JSON.stringify(opciones.body);
        }
        const respuesta = await window.secureFetch(url, config);
        if (!respuesta.ok) {
            const error = typeof window.apiError === 'function' ? await window.apiError(respuesta) : new Error(`Error HTTP ${respuesta.status}`);
            error.status = respuesta.status;
            throw error;
        }
        if (respuesta.status === 204) return null;
        const tipo = respuesta.headers.get('Content-Type') || '';
        if (!tipo.includes('json')) {
            if (respuesta.redirected) {
                window.location.reload();
                throw new Error('Tu sesión terminó. Vuelve a entrar.');
            }
            return null;
        }
        return respuesta.json();
    }

    function normalizar(texto) {
        return String(texto || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
    }

    function plural(n, uno, varios) {
        return `${n} ${n === 1 ? uno : varios}`;
    }

    function textoPorciones(cantidad) {
        if (cantidad === null || cantidad === undefined) return 'Sin límite al activar';
        return `${plural(Number(cantidad), 'porción', 'porciones')} al activar`;
    }

    function textoAcompanamiento(c) {
        if (c.llevaAcompanamiento) return 'Pueden llevar acompañamiento';
        if (c.esAcompanamiento) return 'Sirven como acompañamiento';
        return 'No usa acompañamientos';
    }

    function anunciar(texto) {
        if (!anunciador) return;
        anunciador.textContent = '';
        setTimeout(() => { anunciador.textContent = texto; }, 30);
    }

    function mostrarEstadoOrden(texto, tipo) {
        if (!estadoOrden) return;
        estadoOrden.textContent = texto;
        estadoOrden.dataset.tipo = tipo || '';
    }

    function ordenar(lista) {
        return [...lista].sort((a, b) => (Number(a.orden) || 0) - (Number(b.orden) || 0) || String(a.nombre).localeCompare(String(b.nombre), 'es'));
    }

    async function cargar() {
        if (estado.temporizadorOrden) {
            clearTimeout(estado.temporizadorOrden);
            estado.temporizadorOrden = null;
            await guardarOrden();
        }
        if (!estado.cargado && window.Admin) window.Admin.cargando(contenido, 5);
        try {
            const datos = await pedir(API_LISTA);
            estado.categorias = ordenar(Array.isArray(datos) ? datos : []);
            estado.guardadas = estado.categorias.map(c => c.id);
            const primeraVez = !estado.cargado;
            estado.cargado = true;
            pintar();
            if (primeraVez) restaurarBorrador();
        } catch (error) {
            if (window.Admin) {
                window.Admin.vacio(contenido, {
                    icono: '⚠️',
                    titulo: 'No se pudieron cargar las categorías',
                    texto: error.message || 'Revisa tu conexión e inténtalo otra vez.',
                    accion: { texto: 'Reintentar', clase: 'button-secondary', alHacer: cargar }
                });
            }
        }
    }

    function boton(texto, accion, id, clase, etiqueta) {
        const b = crear('button', clase, texto);
        b.type = 'button';
        b.dataset.accion = accion;
        b.dataset.id = String(id);
        if (etiqueta) b.setAttribute('aria-label', etiqueta);
        return b;
    }

    function fila(c, i, total) {
        const li = crear('li', `cat-item${c.activa ? '' : ' cat-item-oculta'}`);
        li.dataset.id = String(c.id);

        const asa = crear('button', 'cat-asa flat', '⠿');
        asa.type = 'button';
        asa.dataset.id = String(c.id);
        asa.setAttribute('aria-label', `Mover ${c.nombre}. Usa las flechas arriba y abajo del teclado, o arrastra.`);

        const pos = crear('span', 'cat-pos', i + 1);
        pos.setAttribute('aria-hidden', 'true');

        const icono = crear('span', 'cat-icono', c.icono || '🍽️');
        icono.setAttribute('aria-hidden', 'true');

        const info = crear('div', 'cat-info');
        const cabeza = crear('div', 'cat-nombre-fila');
        cabeza.append(crear('strong', 'cat-nombre', c.nombre));
        cabeza.append(crear('span', c.activa ? 'badge badge-ok' : 'badge badge-off', c.activa ? 'Visible' : 'Oculta'));
        info.append(cabeza);

        const detalles = crear('div', 'cat-detalles');
        const productos = Number(c.productos) || 0;
        detalles.append(crear('span', 'cat-dato', productos ? plural(productos, 'producto', 'productos') : 'Sin productos'));
        detalles.append(crear('span', 'cat-dato', textoPorciones(c.cantidadSugerida)));
        if (c.llevaAcompanamiento) detalles.append(crear('span', 'badge badge-info', 'Lleva acompañamiento'));
        if (c.esAcompanamiento) detalles.append(crear('span', 'badge badge-warn', 'Es acompañamiento'));
        info.append(detalles);
        if (!c.activa) info.append(crear('small', 'admin-help', 'Tu personal no la ve al tomar pedidos.'));

        const acciones = crear('div', 'cat-acciones');
        const moverCaja = crear('div', 'cat-mover');
        const subir = boton('↑ Subir', 'subir', c.id, 'button-secondary cat-btn', `Subir ${c.nombre}`);
        const bajar = boton('↓ Bajar', 'bajar', c.id, 'button-secondary cat-btn', `Bajar ${c.nombre}`);
        subir.disabled = i === 0;
        bajar.disabled = i === total - 1;
        moverCaja.append(subir, bajar);

        const otras = crear('div', 'cat-mover');
        const ver = crear('a', 'button-secondary cat-btn', 'Ver productos');
        ver.href = `/admin/productos?categoria=${encodeURIComponent(c.codigo || '')}`;
        otras.append(
            boton('Editar', 'editar', c.id, 'button-secondary cat-btn', `Editar ${c.nombre}`),
            ver,
            boton('Eliminar', 'eliminar', c.id, 'button-secondary cat-btn cat-btn-peligro', `Eliminar ${c.nombre}`)
        );
        acciones.append(moverCaja, otras);

        li.append(asa, pos, icono, info, acciones);
        return li;
    }

    function pintar() {
        if (!estado.categorias.length) {
            if (window.Admin) {
                window.Admin.vacio(contenido, {
                    icono: '🗂️',
                    titulo: 'Todavía no tienes categorías',
                    texto: 'Crea la primera, por ejemplo «Platos principales» o «Bebidas». Después podrás agregarle productos.',
                    accion: { texto: 'Nueva categoría', alHacer: () => abrirFormulario(null) }
                });
            }
            return;
        }
        const lista = crear('ol', 'cat-lista');
        lista.id = 'cat-lista';
        lista.setAttribute('aria-label', 'Categorías en el orden en que las ve tu personal');
        const total = estado.categorias.length;
        estado.categorias.forEach((c, i) => lista.append(fila(c, i, total)));
        contenido.replaceChildren(lista);
        if (window.Admin) window.Admin.listo(contenido);
    }

    function porId(id) {
        return estado.categorias.find(c => String(c.id) === String(id));
    }

    function enfocar(id, selector) {
        const li = contenido.querySelector(`.cat-item[data-id="${CSS.escape(String(id))}"]`);
        if (!li) return;
        const destino = li.querySelector(selector);
        (destino && !destino.disabled ? destino : li.querySelector('.cat-asa'))?.focus();
    }

    function mover(id, delta, selectorFoco) {
        const i = estado.categorias.findIndex(c => String(c.id) === String(id));
        const j = i + delta;
        if (i < 0 || j < 0 || j >= estado.categorias.length) return;
        const [c] = estado.categorias.splice(i, 1);
        estado.categorias.splice(j, 0, c);
        pintar();
        enfocar(id, selectorFoco);
        anunciar(`${c.nombre} ahora está en el lugar ${j + 1} de ${estado.categorias.length}.`);
        programarOrden();
    }

    function programarOrden() {
        clearTimeout(estado.temporizadorOrden);
        mostrarEstadoOrden('Guardando orden…', 'pendiente');
        estado.temporizadorOrden = setTimeout(() => {
            estado.temporizadorOrden = null;
            guardarOrden();
        }, ESPERA_ORDEN);
    }

    function mismoOrden(a, b) {
        return a.length === b.length && a.every((x, i) => String(x) === String(b[i]));
    }

    async function guardarOrden(keepalive) {
        const ids = estado.categorias.map(c => c.id);
        if (mismoOrden(ids, estado.guardadas)) {
            mostrarEstadoOrden('', '');
            return;
        }
        if (estado.guardandoOrden) {
            estado.ordenPendiente = true;
            return;
        }
        estado.guardandoOrden = true;
        mostrarEstadoOrden('Guardando orden…', 'pendiente');
        try {
            await pedir(`${API}/orden`, { method: 'PUT', body: { ids }, keepalive: keepalive === true });
            estado.guardadas = ids.slice();
            estado.categorias.forEach((c, i) => { c.orden = i; });
            mostrarEstadoOrden('✓ Orden guardado', 'ok');
            setTimeout(() => { if (estadoOrden?.dataset.tipo === 'ok') mostrarEstadoOrden('', ''); }, 3000);
        } catch (error) {
            const mapa = new Map(estado.categorias.map(c => [String(c.id), c]));
            const previas = estado.guardadas.map(id => mapa.get(String(id))).filter(Boolean);
            const resto = estado.categorias.filter(c => !previas.includes(c));
            estado.categorias = [...previas, ...resto];
            pintar();
            mostrarEstadoOrden('No se guardó el orden', 'error');
            aviso(`No se pudo guardar el orden: ${error.message || 'error de conexión'}. Volvimos al orden anterior.`, 'error');
        } finally {
            estado.guardandoOrden = false;
            if (estado.ordenPendiente) {
                estado.ordenPendiente = false;
                guardarOrden();
            }
        }
    }

    function iniciarArrastre(evento) {
        const asa = evento.target.closest('.cat-asa');
        if (!asa || (evento.pointerType === 'mouse' && evento.button !== 0)) return;
        const li = asa.closest('.cat-item');
        const lista = li?.parentElement;
        if (!li || !lista) return;
        evento.preventDefault();
        try { asa.setPointerCapture(evento.pointerId); } catch (e) { }
        li.classList.add('cat-item-arrastrando');
        lista.classList.add('cat-lista-arrastrando');
        let movido = false;
        let rodar = null;

        const alMover = ev => {
            const y = ev.clientY;
            const hermanos = [...lista.children].filter(x => x !== li);
            let antes = null;
            for (const h of hermanos) {
                const r = h.getBoundingClientRect();
                if (y < r.top + r.height / 2) { antes = h; break; }
            }
            if (antes) {
                if (li.nextElementSibling !== antes) { lista.insertBefore(li, antes); movido = true; }
            } else if (lista.lastElementChild !== li) {
                lista.append(li);
                movido = true;
            }
            clearInterval(rodar);
            rodar = null;
            const margen = 72;
            if (y < margen) rodar = setInterval(() => window.scrollBy(0, -12), 16);
            else if (y > window.innerHeight - margen) rodar = setInterval(() => window.scrollBy(0, 12), 16);
        };

        const alSoltar = () => {
            clearInterval(rodar);
            asa.removeEventListener('pointermove', alMover);
            asa.removeEventListener('pointerup', alSoltar);
            asa.removeEventListener('pointercancel', alSoltar);
            li.classList.remove('cat-item-arrastrando');
            lista.classList.remove('cat-lista-arrastrando');
            if (!movido) return;
            const mapa = new Map(estado.categorias.map(c => [String(c.id), c]));
            const nuevo = [...lista.children].map(x => mapa.get(x.dataset.id)).filter(Boolean);
            const cambio = nuevo.some((c, i) => c !== estado.categorias[i]);
            estado.categorias = nuevo;
            const id = li.dataset.id;
            pintar();
            enfocar(id, '.cat-asa');
            if (cambio) {
                const pos = estado.categorias.findIndex(c => String(c.id) === id);
                anunciar(`${estado.categorias[pos].nombre} ahora está en el lugar ${pos + 1} de ${estado.categorias.length}.`);
                programarOrden();
            }
        };

        asa.addEventListener('pointermove', alMover);
        asa.addEventListener('pointerup', alSoltar);
        asa.addEventListener('pointercancel', alSoltar);
    }

    function abrirDialogo(contenidoDialogo, alCerrar) {
        dialogo.replaceChildren(contenidoDialogo);
        estado.cerrarDialogo = alCerrar;
        if (!dialogo.open) dialogo.showModal();
        const primero = dialogo.querySelector('input:not([type="hidden"]):not([disabled]):not(.visually-hidden), select:not([disabled])');
        if (primero && window.matchMedia('(min-width: 601px)').matches) primero.focus();
        else dialogo.querySelector('[data-cerrar]')?.focus();
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

    function mostrarError(form, campo, mensaje) {
        const el = form.querySelector(`[data-error="${campo}"]`);
        if (el) el.textContent = mensaje || '';
        const input = form.elements[campo];
        if (input && typeof input.setAttribute === 'function') input.setAttribute('aria-invalid', mensaje ? 'true' : 'false');
    }

    function pintarEmojis(form) {
        const caja = form.querySelector('[data-emojis]');
        EMOJIS.forEach(({ grupo, lista }) => {
            const seccion = crear('div', 'cat-emoji-grupo');
            const titulo = crear('span', 'cat-emoji-titulo', grupo);
            const rejilla = crear('div', 'cat-emoji-rejilla');
            rejilla.setAttribute('role', 'group');
            rejilla.setAttribute('aria-label', grupo);
            lista.forEach(emoji => {
                const b = crear('button', 'cat-emoji flat', emoji);
                b.type = 'button';
                b.dataset.emoji = emoji;
                b.setAttribute('aria-pressed', 'false');
                rejilla.append(b);
            });
            seccion.append(titulo, rejilla);
            caja.append(seccion);
        });
    }

    function abrirFormulario(cat) {
        const form = document.getElementById('cat-tpl-form').content.cloneNode(true).querySelector('form');
        const nuevo = !cat;
        const clave = nuevo ? CLAVE_NUEVA : `categoria-${cat.id}`;
        const campos = form.elements;
        const cuerpo = form.querySelector('[data-cuerpo]');

        form.querySelector('h2').textContent = nuevo ? 'Nueva categoría' : `Editar «${cat.nombre}»`;
        if (!nuevo) form.querySelector('[data-intro]').textContent = 'Cambia el nombre, el ícono o cómo se comporta. Los productos de esta categoría no se modifican.';
        form.querySelector('[data-guardar]').textContent = nuevo ? 'Crear categoría' : 'Guardar cambios';
        pintarEmojis(form);

        campos.nombre.value = nuevo ? '' : (cat.nombre || '');
        campos.icono.value = nuevo ? '🍽️' : (cat.icono || '');
        campos.activa.checked = nuevo ? true : !!cat.activa;
        campos.llevaAcompanamiento.checked = !nuevo && !!cat.llevaAcompanamiento;
        campos.esAcompanamiento.checked = !nuevo && !cat.llevaAcompanamiento && !!cat.esAcompanamiento;
        const sinLimite = !nuevo && (cat.cantidadSugerida === null || cat.cantidadSugerida === undefined);
        campos.sinLimite.checked = sinLimite;
        campos.cantidadSugerida.value = nuevo ? '20' : (sinLimite ? '' : String(cat.cantidadSugerida));

        const nota = form.querySelector('[data-nota-exclusiva]');
        const actualizar = () => {
            const icono = campos.icono.value.trim();
            form.querySelector('[data-preview-icono]').textContent = icono || '❔';
            form.querySelector('[data-preview-nombre]').textContent = campos.nombre.value.trim() || 'Nombre';
            form.querySelector('[data-icono-grande]').textContent = icono;
            form.querySelector('[data-preview-oculta]').hidden = campos.activa.checked;
            form.querySelector('.cat-preview').classList.toggle('cat-preview-apagada', !campos.activa.checked);
            form.querySelectorAll('.cat-emoji').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.emoji === icono)));
            campos.cantidadSugerida.disabled = campos.sinLimite.checked;
        };

        form.addEventListener('input', evento => {
            const nombre = evento.target.name;
            if (nombre) mostrarError(form, nombre, '');
            mostrarError(form, 'general', '');
            actualizar();
        });
        form.addEventListener('change', evento => {
            const objetivo = evento.target;
            if (objetivo.name === 'llevaAcompanamiento' && objetivo.checked && campos.esAcompanamiento.checked) {
                campos.esAcompanamiento.checked = false;
                if (evento.isTrusted) nota.hidden = false;
            }
            if (objetivo.name === 'esAcompanamiento' && objetivo.checked && campos.llevaAcompanamiento.checked) {
                campos.llevaAcompanamiento.checked = false;
                if (evento.isTrusted) nota.hidden = false;
            }
            if (objetivo.name === 'sinLimite') {
                mostrarError(form, 'cantidadSugerida', '');
                if (!objetivo.checked && evento.isTrusted && campos.cantidadSugerida.value === '') campos.cantidadSugerida.value = '20';
            }
            actualizar();
        });
        form.querySelector('[data-emojis]').addEventListener('click', evento => {
            const b = evento.target.closest('.cat-emoji');
            if (!b) return;
            campos.icono.value = b.dataset.emoji;
            campos.icono.dispatchEvent(new Event('input', { bubbles: true }));
        });

        const borrador = vincularBorrador(form, clave, {
            contenedorAviso: cuerpo,
            textoAviso: 'Recuperamos lo que estabas escribiendo en esta categoría.'
        });
        actualizar();

        const salir = async () => {
            if (borrador.sucio()) {
                const ok = await confirmar('Lo que cambiaste en esta categoría no se ha guardado.', {
                    titulo: '¿Cerrar sin guardar?', aceptar: 'Cerrar y descartar', cancelar: 'Seguir editando', peligro: true
                });
                if (!ok) return;
            }
            borrador.limpiar();
            if (borrador.desvincular) borrador.desvincular();
            cerrarDialogo();
        };

        form.addEventListener('click', evento => {
            if (evento.target.closest('[data-cerrar]')) salir();
        });

        form.addEventListener('submit', async evento => {
            evento.preventDefault();
            const nombre = campos.nombre.value.trim().replace(/\s+/g, ' ');
            const icono = campos.icono.value.trim();
            const ilimitado = campos.sinLimite.checked;
            const textoCantidad = campos.cantidadSugerida.value.trim();
            const cantidad = Number(textoCantidad);
            let valido = true;

            if (!nombre || nombre.length > 40) {
                mostrarError(form, 'nombre', 'Escribe un nombre de 1 a 40 letras.');
                valido = false;
            } else if (estado.categorias.some(c => (nuevo || c.id !== cat.id) && normalizar(c.nombre) === normalizar(nombre))) {
                mostrarError(form, 'nombre', 'Ya tienes una categoría con ese nombre. Elige otro.');
                valido = false;
            }
            if (!icono) {
                mostrarError(form, 'icono', 'Elige un ícono de la lista o escribe un emoji.');
                valido = false;
            } else if (icono.length > 16) {
                mostrarError(form, 'icono', 'El ícono es demasiado largo. Usa un solo emoji.');
                valido = false;
            }
            if (!ilimitado && (textoCantidad === '' || !Number.isInteger(cantidad) || cantidad < 0 || cantidad > 999)) {
                mostrarError(form, 'cantidadSugerida', 'Escribe un número entero entre 0 y 999, o marca «Sin límite».');
                valido = false;
            }
            if (!valido) {
                const primero = form.querySelector('[aria-invalid="true"]');
                primero?.focus();
                return;
            }

            const datos = {
                nombre,
                icono,
                activa: campos.activa.checked,
                llevaAcompanamiento: campos.llevaAcompanamiento.checked,
                esAcompanamiento: campos.esAcompanamiento.checked && !campos.llevaAcompanamiento.checked,
                cantidadSugerida: ilimitado ? null : cantidad
            };

            const ok = await confirmar(nuevo
                ? 'Se agregará al final de tus pestañas. Luego puedes moverla.'
                : 'Tu personal verá los cambios la próxima vez que abra la pantalla de pedidos.', {
                titulo: nuevo ? '¿Crear esta categoría?' : '¿Guardar los cambios?',
                resumen: [
                    { etiqueta: 'Pestaña', valor: `${icono} ${nombre}` },
                    { etiqueta: 'Visible', valor: datos.activa ? 'Sí' : 'No, oculta' },
                    { etiqueta: 'Acompañamientos', valor: textoAcompanamiento(datos) },
                    { etiqueta: 'Porciones al activar', valor: ilimitado ? 'Sin límite' : plural(cantidad, 'porción', 'porciones') }
                ],
                aceptar: nuevo ? 'Crear' : 'Guardar'
            });
            if (!ok) return;

            const guardar = form.querySelector('[data-guardar]');
            guardar.disabled = true;
            try {
                if (nuevo) await pedir(API, { method: 'POST', body: datos });
                else await pedir(`${API}/${encodeURIComponent(cat.id)}`, { method: 'PUT', body: datos });
                borrador.limpiar();
                if (borrador.desvincular) borrador.desvincular();
                borrarBorrador(clave);
                cerrarDialogo();
                aviso(nuevo ? 'Categoría creada.' : 'Cambios guardados.', 'ok');
                await cargar();
            } catch (error) {
                mostrarError(form, 'general', error.message || 'No se pudo guardar.');
                aviso(error.message || 'No se pudo guardar.', 'error');
                guardar.disabled = false;
            }
        });

        abrirDialogo(form, salir);
    }

    async function eliminar(cat) {
        const productos = Number(cat.productos) || 0;
        const otras = estado.categorias.filter(c => c.id !== cat.id);

        if (!productos) {
            const ok = await confirmar('La pestaña desaparecerá de la pantalla de pedidos. No tiene productos, así que no se pierde nada más.', {
                titulo: `¿Eliminar «${cat.nombre}»?`,
                resumen: [{ etiqueta: 'Pestaña', valor: `${cat.icono || ''} ${cat.nombre}`.trim() }],
                aceptar: 'Eliminar', cancelar: 'No eliminar', peligro: true
            });
            if (!ok) return;
            try {
                await pedir(`${API}/${encodeURIComponent(cat.id)}`, { method: 'DELETE' });
                borrarBorrador(`categoria-${cat.id}`);
                aviso('Categoría eliminada.', 'ok');
            } catch (error) {
                aviso(error.message || 'No se pudo eliminar.', 'error');
            }
            await cargar();
            return;
        }

        if (!otras.length) {
            const crearOtra = await confirmar(`«${cat.nombre}» tiene ${plural(productos, 'producto', 'productos')} y es tu única categoría. Para eliminarla, primero crea otra categoría a donde pasar esos productos.`, {
                titulo: 'Todavía no se puede eliminar', aceptar: 'Crear otra categoría', cancelar: 'Entendido'
            });
            if (crearOtra) abrirFormulario(null);
            return;
        }

        const form = document.getElementById('cat-tpl-borrar').content.cloneNode(true).querySelector('form');
        form.querySelector('[data-titulo]').textContent = `Eliminar «${cat.nombre}»`;
        form.querySelector('[data-intro]').textContent = `Esta categoría tiene ${plural(productos, 'producto', 'productos')}. Antes de eliminarla, elige a qué categoría pasarlos.`;
        const select = form.elements.moverA;
        otras.forEach(c => {
            const op = crear('option', null, `${c.icono || ''} ${c.nombre}${c.activa ? '' : ' (oculta)'}`.trim());
            op.value = String(c.id);
            select.append(op);
        });

        form.addEventListener('click', evento => { if (evento.target.closest('[data-cerrar]')) cerrarDialogo(); });
        form.addEventListener('submit', async evento => {
            evento.preventDefault();
            const destino = porId(select.value);
            if (!destino) return;
            const ok = await confirmar('Los productos pasarán a la otra categoría y esta pestaña desaparecerá.', {
                titulo: '¿Mover y eliminar?',
                resumen: [
                    { etiqueta: 'Se elimina', valor: `${cat.icono || ''} ${cat.nombre}`.trim() },
                    { etiqueta: 'Productos', valor: String(productos) },
                    { etiqueta: 'Pasan a', valor: `${destino.icono || ''} ${destino.nombre}`.trim() }
                ],
                aceptar: 'Mover y eliminar', cancelar: 'Cancelar', peligro: true
            });
            if (!ok) return;
            const guardar = form.querySelector('[data-guardar]');
            guardar.disabled = true;
            try {
                await pedir(`${API}/${encodeURIComponent(cat.id)}?moverA=${encodeURIComponent(destino.id)}`, { method: 'DELETE' });
                borrarBorrador(`categoria-${cat.id}`);
                cerrarDialogo();
                aviso(`Categoría eliminada. Sus productos ahora están en «${destino.nombre}».`, 'ok');
                await cargar();
            } catch (error) {
                form.querySelector('[data-error="general"]').textContent = error.message || 'No se pudo eliminar.';
                guardar.disabled = false;
                if (error.status === 409) cargar();
            }
        });
        abrirDialogo(form, cerrarDialogo);
    }

    function tieneBorrador(clave) {
        const datos = leerBorrador(clave);
        return !!datos && typeof datos === 'object' && Object.keys(datos).length > 0;
    }

    function restaurarBorrador() {
        if (dialogo.open) return;
        if (tieneBorrador(CLAVE_NUEVA)) {
            abrirFormulario(null);
            return;
        }
        const cat = estado.categorias.find(c => tieneBorrador(`categoria-${c.id}`));
        if (cat) abrirFormulario(cat);
    }

    function iniciar() {
        main = document.getElementById('contenido');
        dialogo = document.getElementById('cat-dialogo');
        contenido = document.getElementById('cat-contenido');
        estadoOrden = document.getElementById('cat-orden-estado');
        if (!main || !dialogo || !contenido) return;

        anunciador = crear('div', 'visually-hidden');
        anunciador.setAttribute('aria-live', 'polite');
        main.append(anunciador);

        dialogo.addEventListener('cancel', evento => { evento.preventDefault(); intentarCerrar(); });
        dialogo.addEventListener('click', evento => {
            if (evento.target !== dialogo) return;
            const r = dialogo.getBoundingClientRect();
            const fuera = evento.clientX < r.left || evento.clientX > r.right || evento.clientY < r.top || evento.clientY > r.bottom;
            if (fuera) intentarCerrar();
        });

        document.getElementById('cat-agregar').addEventListener('click', () => abrirFormulario(null));

        contenido.addEventListener('click', evento => {
            const b = evento.target.closest('button[data-accion]');
            if (!b || b.disabled) return;
            const cat = porId(b.dataset.id);
            if (!cat) return;
            const accion = b.dataset.accion;
            if (accion === 'subir') mover(cat.id, -1, '[data-accion="subir"]');
            else if (accion === 'bajar') mover(cat.id, 1, '[data-accion="bajar"]');
            else if (accion === 'editar') abrirFormulario(cat);
            else if (accion === 'eliminar') eliminar(cat);
        });
        contenido.addEventListener('pointerdown', iniciarArrastre);
        contenido.addEventListener('keydown', evento => {
            const asa = evento.target.closest('.cat-asa');
            if (!asa) return;
            if (evento.key === 'ArrowUp') { evento.preventDefault(); mover(asa.dataset.id, -1, '.cat-asa'); }
            else if (evento.key === 'ArrowDown') { evento.preventDefault(); mover(asa.dataset.id, 1, '.cat-asa'); }
        });

        window.addEventListener('pagehide', () => {
            if (estado.temporizadorOrden) {
                clearTimeout(estado.temporizadorOrden);
                estado.temporizadorOrden = null;
                guardarOrden(true);
            }
        });

        cargar();
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar);
    else iniciar();
})();
