(function () {
    'use strict';

    const API = '/api/admin/auditoria';
    const CLAVE = 'actividad-filtros';
    const TAMANO = 50;
    const REFRESCO_MS = 30000;
    const CAMPOS = ['usuario', 'accion', 'desde', 'hasta'];

    const FAMILIAS = [
        { id: 'fallido', etiqueta: 'Intentos de acceso fallidos', clase: 'badge badge-warn', prueba: c => c.includes('FALLID') },
        { id: 'cobros', etiqueta: 'Cobros', clase: 'badge badge-ok', prueba: c => /PAG|COBR|CAJA/.test(c) },
        { id: 'pedidos', etiqueta: 'Pedidos', clase: 'badge badge-info', prueba: c => c.includes('PEDIDO') || c.includes('DETALLE') || c.includes('ENTREG') || c.includes('PREPARA') },
        { id: 'usuarios', etiqueta: 'Usuarios', clase: 'badge act-fam-usuarios', prueba: c => /USUARIO|PASSWORD|CONTRASEN|ROL/.test(c) },
        { id: 'acceso', etiqueta: 'Acceso al sistema', clase: 'badge badge-off', prueba: c => /LOGIN|LOGOUT|SESION|ACCESO|SALIDA|ENTRADA/.test(c) },
        { id: 'config', etiqueta: 'Configuración del negocio', clase: 'badge act-fam-config', prueba: c => /CONFIG|NEGOCIO|LOGO|TEMA|MARCA/.test(c) },
        { id: 'menu', etiqueta: 'Menú y productos', clase: 'badge act-fam-menu', prueba: c => /PLATO|MENU|PRODUCTO|STOCK|CATEGORIA|PRESENTACION|PRECIO/.test(c) },
        { id: 'otros', etiqueta: 'Otras acciones', clase: 'badge badge-off', prueba: () => true }
    ];

    const ENTIDADES = {
        PEDIDO: 'Pedido', DETALLEPEDIDO: 'Detalle de pedido', PLATO: 'Producto', PRODUCTO: 'Producto',
        PRESENTACIONPLATO: 'Presentación', USUARIO: 'Usuario', CONFIGURACIONNEGOCIO: 'Configuración',
        CONFIGURACION: 'Configuración', CATEGORIA: 'Categoría'
    };

    const estado = { filtros: { usuario: '', accion: '', desde: '', hasta: '' }, pagina: 0, paginas: 0, total: 0, secuencia: 0, cargando: false };
    let form, lista;

    function borradores() {
        const B = window.Borradores;
        return {
            leer(clave) { try { return B && B.leer ? B.leer(clave) : null; } catch (e) { return null; } },
            guardar(clave, valor) { try { if (B && B.guardar) B.guardar(clave, valor); } catch (e) { } }
        };
    }

    function crear(tag, clase, texto) {
        const el = document.createElement(tag);
        if (clase) el.className = clase;
        if (texto !== undefined && texto !== null) el.textContent = String(texto);
        return el;
    }

    function fechaHora(iso) {
        if (window.Admin && typeof window.Admin.fechaHora === 'function') {
            try { return window.Admin.fechaHora(iso); } catch (e) { }
        }
        const d = new Date(iso);
        return isNaN(d) ? '' : d.toLocaleString('es', { dateStyle: 'medium', timeStyle: 'short' });
    }

    function iso(d) {
        const m = String(d.getMonth() + 1).padStart(2, '0');
        const dia = String(d.getDate()).padStart(2, '0');
        return `${d.getFullYear()}-${m}-${dia}`;
    }

    function rangos() {
        const hoy = new Date();
        const base = new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate());
        const restar = n => new Date(base.getFullYear(), base.getMonth(), base.getDate() - n);
        return {
            hoy: { desde: iso(base), hasta: iso(base) },
            ayer: { desde: iso(restar(1)), hasta: iso(restar(1)) },
            semana: { desde: iso(restar(6)), hasta: iso(base) },
            mes: { desde: iso(new Date(base.getFullYear(), base.getMonth(), 1)), hasta: iso(base) }
        };
    }

    function familia(codigo, entidad) {
        const c = String(codigo || '').toUpperCase();
        const e = String(entidad || '').toUpperCase();
        return FAMILIAS.find(f => f.id !== 'otros' && f.prueba(c)) || FAMILIAS.find(f => f.id !== 'otros' && f.id !== 'fallido' && e && f.prueba(e)) || FAMILIAS[FAMILIAS.length - 1];
    }

    function nombreEntidad(entidad) {
        if (!entidad) return '';
        const clave = String(entidad).toUpperCase().replace(/[^A-Z]/g, '');
        return ENTIDADES[clave] || String(entidad);
    }

    function limpiarFiltros(datos) {
        const f = { usuario: '', accion: '', desde: '', hasta: '' };
        if (!datos || typeof datos !== 'object') return f;
        CAMPOS.forEach(c => {
            const v = datos[c];
            if (typeof v !== 'string') return;
            if ((c === 'desde' || c === 'hasta') && !/^\d{4}-\d{2}-\d{2}$/.test(v)) return;
            f[c] = v.slice(0, 80);
        });
        return f;
    }

    function leerUrl() {
        const p = new URLSearchParams(window.location.search);
        const tiene = CAMPOS.some(c => p.has(c)) || p.has('pagina');
        if (!tiene) return null;
        const datos = {};
        CAMPOS.forEach(c => { datos[c] = p.get(c) || ''; });
        const pagina = Math.max(0, parseInt(p.get('pagina') || '0', 10) || 0);
        return { filtros: limpiarFiltros(datos), pagina };
    }

    function escribirUrl(modo) {
        const p = new URLSearchParams();
        CAMPOS.forEach(c => { if (estado.filtros[c]) p.set(c, estado.filtros[c]); });
        p.set('pagina', String(estado.pagina));
        const url = `${window.location.pathname}?${p.toString()}`;
        if (url === `${window.location.pathname}${window.location.search}`) return;
        if (modo === 'push') history.pushState(null, '', url);
        else history.replaceState(null, '', url);
    }

    function pasarAlFormulario() {
        CAMPOS.forEach(c => {
            const el = form.elements[c];
            if (!el) return;
            if (el.tagName === 'SELECT' && estado.filtros[c] && !Array.from(el.options).some(o => o.value === estado.filtros[c])) {
                const extra = crear('option', null, estado.filtros[c]);
                extra.value = estado.filtros[c];
                el.append(extra);
            }
            el.value = estado.filtros[c];
        });
        marcarRapidos();
    }

    function rangoActivo() {
        const r = rangos();
        const { desde, hasta } = estado.filtros;
        return Object.keys(r).find(k => r[k].desde === desde && r[k].hasta === hasta) || '';
    }

    function marcarRapidos() {
        const activo = rangoActivo();
        form.querySelectorAll('[data-rango]').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.rango === activo)));
    }

    function fechasValidas() {
        const error = document.getElementById('act-error-fechas');
        const { desde, hasta } = estado.filtros;
        const mal = desde && hasta && desde > hasta;
        error.textContent = mal ? 'La fecha "Desde" no puede ser posterior a "Hasta".' : '';
        error.hidden = !mal;
        return !mal;
    }

    function aplicar(modoUrl) {
        marcarRapidos();
        if (!fechasValidas()) return;
        borradores().guardar(CLAVE, { ...estado.filtros });
        escribirUrl(modoUrl);
        cargar({ silencioso: false });
    }

    function leerFormulario() {
        CAMPOS.forEach(c => { estado.filtros[c] = (form.elements[c]?.value || '').trim(); });
    }

    function mostrarCargando() {
        lista.replaceChildren();
        if (window.Admin && typeof window.Admin.cargando === 'function') {
            try { window.Admin.cargando(lista, 6); return; } catch (e) { }
        }
        for (let i = 0; i < 6; i++) lista.append(crear('div', 'admin-skeleton act-skeleton'));
    }

    function mostrarVacio(titulo, texto, acciones) {
        const caja = crear('div', 'admin-empty');
        caja.append(crear('strong', null, titulo), crear('p', null, texto));
        if (acciones && acciones.length) {
            const fila = crear('div', 'act-vacio-acciones');
            fila.append(...acciones);
            caja.append(fila);
        }
        lista.replaceChildren(caja);
    }

    function botonSimple(texto, clase, alPulsar) {
        const b = crear('button', `${clase} act-btn`, texto);
        b.type = 'button';
        b.addEventListener('click', alPulsar);
        return b;
    }

    async function cargar({ silencioso }) {
        const secuencia = ++estado.secuencia;
        estado.cargando = true;
        const scroll = window.scrollY;
        if (!silencioso) mostrarCargando();
        lista.setAttribute('aria-busy', 'true');
        const p = new URLSearchParams();
        CAMPOS.forEach(c => { if (estado.filtros[c]) p.set(c, estado.filtros[c]); });
        p.set('pagina', String(estado.pagina));
        p.set('tamano', String(TAMANO));
        try {
            const respuesta = await window.secureFetch(`${API}?${p.toString()}`, { headers: { Accept: 'application/json' } });
            if (!respuesta.ok) {
                if (typeof window.apiError === 'function') throw await window.apiError(respuesta);
                throw new Error(`Error HTTP ${respuesta.status}`);
            }
            if (!(respuesta.headers.get('Content-Type') || '').includes('json')) throw new Error('Tu sesión terminó. Vuelve a entrar.');
            const datos = await respuesta.json();
            if (secuencia !== estado.secuencia) return;
            estado.total = Number(datos.total) || 0;
            estado.paginas = Number(datos.paginas) || 0;
            if (estado.pagina > 0 && estado.pagina >= estado.paginas && estado.paginas > 0) {
                estado.pagina = estado.paginas - 1;
                escribirUrl('replace');
                cargar({ silencioso });
                return;
            }
            pintar(Array.isArray(datos.contenido) ? datos.contenido : []);
            if (silencioso) window.scrollTo(0, scroll);
        } catch (error) {
            if (secuencia !== estado.secuencia) return;
            if (silencioso) return;
            document.getElementById('act-paginacion').hidden = true;
            document.getElementById('act-estado').textContent = '';
            mostrarVacio('No se pudo cargar la actividad', error.message || 'Revisa tu conexión e inténtalo otra vez.',
                [botonSimple('Reintentar', 'button-secondary', () => cargar({ silencioso: false }))]);
        } finally {
            if (secuencia === estado.secuencia) {
                estado.cargando = false;
                lista.setAttribute('aria-busy', 'false');
            }
        }
    }

    function hayFiltros() {
        return CAMPOS.some(c => estado.filtros[c]);
    }

    function pintar(registros) {
        const estadoTexto = document.getElementById('act-estado');
        const auto = autoActivo();
        estadoTexto.textContent = `${estado.total} ${estado.total === 1 ? 'registro' : 'registros'}${auto ? ' · se actualiza solo cada 30 segundos' : ''}`;
        pintarPaginacion();

        if (!registros.length) {
            const acciones = [];
            if (rangoActivo() === 'hoy') acciones.push(botonSimple('Ver últimos 7 días', 'button-primary', () => elegirRango('semana')));
            if (hayFiltros()) acciones.push(botonSimple('Limpiar filtros', 'button-secondary', limpiarTodo));
            mostrarVacio('No hay actividad con estos filtros', rangoActivo() === 'hoy'
                ? 'Todavía no se ha registrado nada hoy. Prueba con otro rango de fechas.'
                : 'Prueba cambiando la persona, la acción o las fechas.', acciones);
            return;
        }

        const tabla = crear('table', 'admin-table act-tabla');
        const thead = crear('thead');
        const filaHead = crear('tr');
        ['Cuándo', 'Persona', 'Acción', 'Sobre qué', 'Detalle'].forEach(t => {
            const th = crear('th', null, t);
            th.scope = 'col';
            filaHead.append(th);
        });
        thead.append(filaHead);
        const tbody = crear('tbody');

        registros.forEach(r => {
            const fam = familia(r.accion, r.entidad);
            const tr = crear('tr', fam.id === 'fallido' ? 'act-fila-alerta' : '');
            const celda = (etiqueta, ...hijos) => {
                const td = crear('td');
                td.dataset.label = etiqueta;
                td.append(...hijos);
                tr.append(td);
                return td;
            };
            const tiempo = crear('time', 'act-fecha', fechaHora(r.fecha));
            if (r.fecha) tiempo.dateTime = String(r.fecha);
            celda('Cuándo', tiempo);
            celda('Persona', crear('span', 'act-persona', r.usuario || 'Sistema'));
            const badge = crear('span', fam.clase, r.accionEtiqueta || r.accion || 'Acción');
            badge.title = fam.etiqueta;
            celda('Acción', badge);
            const entidad = nombreEntidad(r.entidad);
            const sobre = entidad ? (r.entidadId !== null && r.entidadId !== undefined && r.entidadId !== '' ? `${entidad} n.º ${r.entidadId}` : entidad) : '—';
            celda('Sobre qué', crear('span', null, sobre));
            celda('Detalle', crear('span', 'act-detalle', r.detalle || '—'));
            tbody.append(tr);
        });

        tabla.append(thead, tbody);
        lista.replaceChildren(tabla);
    }

    function pintarPaginacion() {
        const nav = document.getElementById('act-paginacion');
        const paginas = Math.max(estado.paginas, 1);
        nav.hidden = estado.paginas <= 1;
        document.getElementById('act-pagina-texto').textContent = `Página ${estado.pagina + 1} de ${paginas}`;
        document.getElementById('act-anterior').disabled = estado.pagina <= 0;
        document.getElementById('act-siguiente').disabled = estado.pagina + 1 >= estado.paginas;
    }

    function irAPagina(n) {
        if (n < 0 || (estado.paginas && n >= estado.paginas)) return;
        estado.pagina = n;
        escribirUrl('push');
        cargar({ silencioso: false });
        document.getElementById('act-resultados-titulo').scrollIntoView({ block: 'start', behavior: 'smooth' });
    }

    function elegirRango(clave) {
        const r = rangos()[clave];
        if (!r) return;
        estado.filtros.desde = r.desde;
        estado.filtros.hasta = r.hasta;
        estado.pagina = 0;
        pasarAlFormulario();
        aplicar('push');
    }

    function limpiarTodo() {
        estado.filtros = { usuario: '', accion: '', desde: '', hasta: '' };
        estado.pagina = 0;
        pasarAlFormulario();
        aplicar('push');
    }

    function autoActivo() {
        return estado.pagina === 0 && rangoActivo() === 'hoy';
    }

    async function cargarOpciones() {
        const pedir = async url => {
            const r = await window.secureFetch(url, { headers: { Accept: 'application/json' } });
            if (!r.ok) throw new Error(String(r.status));
            return r.json();
        };
        const [usuarios, acciones] = await Promise.allSettled([pedir(`${API}/usuarios`), pedir(`${API}/acciones`)]);

        const selUsuario = form.elements.usuario;
        if (usuarios.status === 'fulfilled' && Array.isArray(usuarios.value)) {
            usuarios.value.filter(u => typeof u === 'string' && u).sort((a, b) => a.localeCompare(b, 'es')).forEach(u => {
                if (Array.from(selUsuario.options).some(o => o.value === u)) return;
                const o = crear('option', null, u);
                o.value = u;
                selUsuario.append(o);
            });
        }

        const selAccion = form.elements.accion;
        if (acciones.status === 'fulfilled' && Array.isArray(acciones.value)) {
            const extras = Array.from(selAccion.options).filter(o => o.value);
            extras.forEach(o => o.remove());
            const grupos = new Map();
            acciones.value.filter(a => a && a.codigo).forEach(a => {
                const fam = familia(a.codigo);
                if (!grupos.has(fam.id)) grupos.set(fam.id, []);
                grupos.get(fam.id).push(a);
            });
            FAMILIAS.forEach(f => {
                const items = grupos.get(f.id);
                if (!items || !items.length) return;
                const grupo = document.createElement('optgroup');
                grupo.label = f.etiqueta;
                items.sort((a, b) => String(a.etiqueta || a.codigo).localeCompare(String(b.etiqueta || b.codigo), 'es')).forEach(a => {
                    const o = crear('option', null, a.etiqueta || a.codigo);
                    o.value = a.codigo;
                    grupo.append(o);
                });
                selAccion.append(grupo);
            });
        }
        pasarAlFormulario();
    }

    function refrescoAutomatico() {
        if (document.visibilityState !== 'visible' || estado.cargando || !autoActivo()) return;
        cargar({ silencioso: true });
    }

    function iniciar() {
        form = document.getElementById('act-form');
        lista = document.getElementById('act-lista');
        if (!form || !lista) return;

        const desdeUrl = leerUrl();
        if (desdeUrl) {
            estado.filtros = desdeUrl.filtros;
            estado.pagina = desdeUrl.pagina;
        } else {
            const guardado = borradores().leer(CLAVE);
            if (guardado && typeof guardado === 'object') estado.filtros = limpiarFiltros(guardado);
            else estado.filtros = { usuario: '', accion: '', desde: rangos().hoy.desde, hasta: rangos().hoy.hasta };
            estado.pagina = 0;
        }

        pasarAlFormulario();
        if (fechasValidas()) {
            escribirUrl('replace');
            cargar({ silencioso: false });
        }
        cargarOpciones();

        form.addEventListener('submit', event => event.preventDefault());
        form.addEventListener('change', event => {
            if (!CAMPOS.includes(event.target.name)) return;
            leerFormulario();
            estado.pagina = 0;
            aplicar('push');
        });
        form.querySelectorAll('[data-rango]').forEach(b => b.addEventListener('click', () => elegirRango(b.dataset.rango)));
        document.getElementById('act-limpiar').addEventListener('click', limpiarTodo);
        document.getElementById('act-actualizar').addEventListener('click', () => cargar({ silencioso: false }));
        document.getElementById('act-anterior').addEventListener('click', () => irAPagina(estado.pagina - 1));
        document.getElementById('act-siguiente').addEventListener('click', () => irAPagina(estado.pagina + 1));

        window.addEventListener('popstate', () => {
            const datos = leerUrl() || { filtros: { usuario: '', accion: '', desde: '', hasta: '' }, pagina: 0 };
            estado.filtros = datos.filtros;
            estado.pagina = datos.pagina;
            pasarAlFormulario();
            if (fechasValidas()) cargar({ silencioso: false });
        });

        setInterval(refrescoAutomatico, REFRESCO_MS);
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible') refrescoAutomatico();
        });
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar);
    else iniciar();
})();
