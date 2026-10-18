(function () {
    'use strict';
    const BRANDING = window.NEGOCIO || {};
    const MONEDA = BRANDING.simboloMoneda;
    const MONEDA_HTML = escapeHtml(MONEDA);
    const TEXTO_PUBLICAR = 'Publicar menú del día';
    const CLAVE_MENU = 'menu-hoy';
    const TODAS = '__todas__';
    const SIN_CATEGORIA = '__sin_categoria__';
    const URL_PRODUCTOS = '/admin/productos';
    const URL_CATEGORIAS = '/admin/categorias';

    Borradores.borrar('menu-hoy-modal');

    const normalizar = s => String(s ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
    const dinero = v => `${MONEDA} ${(Number(v) || 0).toFixed(2)}`;
    const plural = (n, uno, varios) => `${n} ${n === 1 ? uno : varios}`;
    const textoStock = q => (q === null || q === undefined) ? 'Sin límite' : plural(q, 'porción', 'porciones');
    const codigoDe = v => String(v ?? '').trim().toUpperCase();
    const tienePresentaciones = p => Array.isArray(p.presentaciones) && p.presentaciones.length > 0;
    const precioMinimo = p => Math.min(...p.presentaciones.map(x => Number(x.precio) || 0));

    const fechaHoy = document.getElementById('menu-hoy-fecha');
    if (fechaHoy) {
        const texto = new Date().toLocaleDateString('es-BO', { weekday: 'long', day: 'numeric', month: 'long' });
        fechaHoy.textContent = texto.charAt(0).toUpperCase() + texto.slice(1);
    }

    let catalogoMaestro = [], seleccionadosHoy = new Set(), cantidadesHoy = {};
    let grupos = [], grupoPorCodigo = new Map(), platosPorGrupo = new Map();
    let categoriasCargadas = false;
    let filtroCat = TODAS, filtroTexto = '';
    let publicado = new Map(), catalogoCargado = false;
    let avisoMenu = null;
    let cargando = false;
    let ocultasAbiertas = false;
    let secuencia = 0;

    const API = {
        async req(url) {
            const r = await secureFetch(url, { headers: { 'Accept': 'application/json' } });
            if (!r.ok) throw await apiError(r);
            return r.json();
        },
        getPlatos: () => API.req('/api/platos/todos'),
        getCategorias: () => API.req('/api/categorias')
    };

    function nombreDesdeCodigo(codigo) {
        const texto = codigo.replace(/_+/g, ' ').toLowerCase();
        return texto.charAt(0).toUpperCase() + texto.slice(1);
    }

    function categoriasDesdeProductos(lista) {
        return [...new Set(lista.map(p => codigoDe(p.categoria)).filter(Boolean))]
            .map((codigo, i) => ({ codigo, nombre: nombreDesdeCodigo(codigo), orden: i, activa: true, cantidadSugerida: null }));
    }

    function construirGrupos(categorias) {
        const lista = categorias
            .filter(c => c && codigoDe(c.codigo))
            .map((c, i) => ({ c, i }))
            .sort((a, b) => ((Number(a.c.orden) || 0) - (Number(b.c.orden) || 0)) || (a.i - b.i))
            .map(({ c }) => ({
                codigo: codigoDe(c.codigo),
                codigoOriginal: String(c.codigo),
                nombre: String(c.nombre || nombreDesdeCodigo(codigoDe(c.codigo))),
                icono: c.icono ? String(c.icono) : '',
                activa: c.activa !== false,
                cantidadSugerida: c.cantidadSugerida === null || c.cantidadSugerida === undefined
                    ? null
                    : Math.max(0, parseInt(c.cantidadSugerida, 10) || 0),
                sinCategoria: false
            }));
        grupos = [...lista, {
            codigo: SIN_CATEGORIA, codigoOriginal: '', nombre: 'Sin categoría', icono: '',
            activa: true, cantidadSugerida: null, sinCategoria: true
        }];
        grupoPorCodigo = new Map(grupos.map(g => [g.codigo, g]));
    }

    function grupoDe(p) {
        return grupoPorCodigo.get(codigoDe(p.categoria)) || grupoPorCodigo.get(SIN_CATEGORIA);
    }

    function indexar() {
        platosPorGrupo = new Map(grupos.map(g => [g.codigo, []]));
        catalogoMaestro.forEach(p => platosPorGrupo.get(grupoDe(p).codigo).push(p));
    }

    const platosDe = g => platosPorGrupo.get(g.codigo) || [];

    function capturarPublicado() {
        publicado = new Map(catalogoMaestro
            .filter(p => seleccionadosHoy.has(p.id))
            .map(p => [p.id, cantidadesHoy[p.id] ?? null]));
    }

    function hayCambios() {
        return catalogoMaestro.some(p => {
            const sel = seleccionadosHoy.has(p.id);
            if (sel !== publicado.has(p.id)) return true;
            return sel && (cantidadesHoy[p.id] ?? null) !== publicado.get(p.id);
        });
    }

    function guardarBorradorMenu() {
        if (!catalogoCargado) return;
        if (hayCambios()) {
            Borradores.guardar(CLAVE_MENU, { sel: Array.from(seleccionadosHoy), cant: cantidadesHoy });
        } else {
            Borradores.borrar(CLAVE_MENU);
            avisoMenu?.remove();
            avisoMenu = null;
        }
    }

    function restaurarBorradorMenu() {
        const borrador = Borradores.leer(CLAVE_MENU);
        if (!borrador || !Array.isArray(borrador.sel)) return;
        const ids = new Set(catalogoMaestro.map(p => p.id));
        const selPrevio = new Set(seleccionadosHoy);
        const cantPrevia = { ...cantidadesHoy };
        seleccionadosHoy = new Set(borrador.sel.filter(id => ids.has(id)));
        Object.entries(borrador.cant || {}).forEach(([id, q]) => {
            const num = Number(id);
            if (ids.has(num)) cantidadesHoy[num] = q === null ? null : Math.max(0, parseInt(q, 10) || 0);
        });
        if (!hayCambios()) {
            seleccionadosHoy = selPrevio;
            cantidadesHoy = cantPrevia;
            Borradores.borrar(CLAVE_MENU);
            return;
        }
        avisoMenu = Borradores.aviso({
            texto: 'Recuperamos los cambios que no habías publicado. Revísalos y pulsa «Publicar» cuando estés listo.',
            boton: 'Descartar cambios',
            contenedor: document.getElementById('menu-hoy-avisos'),
            alDescartar: descartarCambiosMenu
        });
    }

    function descartarCambiosMenu() {
        avisoMenu = null;
        seleccionadosHoy = new Set(publicado.keys());
        cantidadesHoy = {};
        publicado.forEach((q, id) => { cantidadesHoy[id] = q; });
        Borradores.borrar(CLAVE_MENU);
        renderizarCategorias();
        showNotice('Volviste al menú publicado', 'ok');
    }

    function aplicarCatalogo(lista) {
        const primeraVez = !catalogoCargado;
        const conocidos = new Set(catalogoMaestro.map(p => p.id));
        const sincronizar = primeraVez || !hayCambios();
        const ids = new Set(lista.map(p => p.id));
        catalogoMaestro = lista;
        if (sincronizar) {
            seleccionadosHoy = new Set();
            cantidadesHoy = {};
            lista.forEach(p => {
                if (!p.disponible) return;
                seleccionadosHoy.add(p.id);
                cantidadesHoy[p.id] = p.stockDisponible ?? null;
            });
            capturarPublicado();
        } else {
            seleccionadosHoy = new Set([...seleccionadosHoy].filter(id => ids.has(id)));
            publicado = new Map([...publicado].filter(([id]) => ids.has(id)));
            lista.forEach(p => {
                if (conocidos.has(p.id) || !p.disponible) return;
                seleccionadosHoy.add(p.id);
                cantidadesHoy[p.id] = p.stockDisponible ?? null;
                publicado.set(p.id, cantidadesHoy[p.id]);
            });
        }
        if (primeraVez) {
            catalogoCargado = true;
            restaurarBorradorMenu();
        }
    }

    async function cargarCatalogo() {
        if (cargando) return;
        cargando = true;
        try {
            const [platos, categorias] = await Promise.all([
                API.getPlatos(),
                API.getCategorias()
                    .then(lista => ({ ok: true, lista: Array.isArray(lista) ? lista : [] }))
                    .catch(error => ({ ok: false, error }))
            ]);
            const lista = Array.isArray(platos) ? platos : [];
            if (categorias.ok) {
                categoriasCargadas = true;
                construirGrupos(categorias.lista);
            } else if (!catalogoCargado) {
                categoriasCargadas = false;
                construirGrupos(categoriasDesdeProductos(lista));
                showNotice('No se pudieron cargar las categorías. Mostramos tus productos agrupados como estaban guardados.');
            }
            aplicarCatalogo(lista);
            renderizarCategorias();
        } catch (e) {
            console.error('cargarCatalogo:', e);
            if (!catalogoCargado) {
                const container = document.getElementById('menu-container');
                const msg = document.createElement('p');
                msg.className = 'empty-msg';
                msg.textContent = 'No se pudo cargar el menú. Revisa tu conexión y vuelve a abrir esta página.';
                container.replaceChildren(msg);
            }
            showNotice(e.message);
        } finally {
            cargando = false;
        }
    }

    function tarjetaVacia({ icono, titulo, texto, accion, href }) {
        const caja = document.createElement('div');
        caja.className = 'admin-empty menu-hoy-vacio';
        const ico = document.createElement('span');
        ico.className = 'admin-empty-icono';
        ico.setAttribute('aria-hidden', 'true');
        ico.textContent = icono;
        const h = document.createElement('h2');
        h.className = 'admin-empty-titulo';
        h.textContent = titulo;
        const p = document.createElement('p');
        p.className = 'admin-empty-texto';
        p.textContent = texto;
        const a = document.createElement('a');
        a.className = 'button-primary admin-empty-accion';
        a.href = href;
        a.textContent = accion;
        caja.append(ico, h, p, a);
        return caja;
    }

    function hrefNuevo(g) {
        return g.sinCategoria
            ? `${URL_PRODUCTOS}?nuevo=1`
            : `${URL_PRODUCTOS}?nuevo=1&categoria=${encodeURIComponent(g.codigoOriginal)}`;
    }

    function construirSeccion(g) {
        const platos = platosDe(g);
        const tituloId = `ct-${++secuencia}`;
        const nombreHtml = escapeHtml(g.nombre);
        const section = document.createElement('section');
        section.className = `cat-section${g.activa ? '' : ' oculta'}`;
        section.dataset.cat = g.codigo;
        section.setAttribute('aria-labelledby', tituloId);
        section.innerHTML = `
            <div class="cat-head">
                <h2 class="cat-title" id="${tituloId}">${g.icono ? `<span class="cat-icono" aria-hidden="true">${escapeHtml(g.icono)}</span>` : ''}<span class="cat-nombre">${nombreHtml}</span></h2>
                <span class="cat-count"></span>
                <a class="btn-add-producto" href="${escapeHtml(hrefNuevo(g))}"
                   aria-label="Agregar producto en ${nombreHtml}">+ Agregar producto</a>
            </div>
            ${g.sinCategoria ? '<p class="cat-nota">Estos productos tienen una categoría que ya no existe. Pulsa «Editar» para asignarles una.</p>' : ''}
            <div class="platos-list"></div>`;
        const list = section.querySelector('.platos-list');
        if (!platos.length) {
            const vacio = document.createElement('p');
            vacio.className = 'empty-msg';
            vacio.textContent = 'Todavía no hay productos en esta categoría.';
            list.append(vacio);
        } else {
            platos.forEach(p => list.appendChild(buildPlatoRow(p)));
        }
        return section;
    }

    function construirOcultas(ocultos) {
        const det = document.createElement('details');
        det.className = 'cat-ocultas';
        det.open = ocultasAbiertas;
        const total = ocultos.reduce((s, g) => s + platosDe(g).length, 0);
        const resumen = document.createElement('summary');
        resumen.className = 'cat-ocultas-resumen';
        const titulo = document.createElement('span');
        titulo.className = 'cat-ocultas-titulo';
        titulo.textContent = `Categorías ocultas (${ocultos.length})`;
        const detalle = document.createElement('span');
        detalle.className = 'cat-ocultas-detalle';
        detalle.textContent = plural(total, 'producto', 'productos');
        resumen.append(titulo, detalle);
        const nota = document.createElement('p');
        nota.className = 'cat-ocultas-nota';
        const enlace = document.createElement('a');
        enlace.href = URL_CATEGORIAS;
        enlace.textContent = 'Categorías';
        nota.append('Estas categorías están ocultas para tu personal: sus productos no aparecen al tomar pedidos aunque los actives aquí. Para mostrarlas, actívalas en ', enlace, '.');
        const cuerpo = document.createElement('div');
        cuerpo.className = 'cat-ocultas-cuerpo';
        ocultos.forEach(g => cuerpo.append(construirSeccion(g)));
        det.append(resumen, nota, cuerpo);
        det.addEventListener('toggle', () => { if (!normalizar(filtroTexto)) ocultasAbiertas = det.open; });
        return det;
    }

    function renderizarCategorias() {
        const container = document.getElementById('menu-container');
        container.replaceChildren();
        secuencia = 0;
        indexar();

        if (categoriasCargadas && grupos.length === 1) {
            container.append(tarjetaVacia({
                icono: '🗂️',
                titulo: 'Todavía no tienes categorías',
                texto: 'Las categorías ordenan tus productos (por ejemplo: Bebidas, Comidas, Postres). Crea al menos una para empezar.',
                accion: 'Crear categorías',
                href: URL_CATEGORIAS
            }));
        }
        if (!catalogoMaestro.length) {
            container.append(tarjetaVacia({
                icono: '🛒',
                titulo: 'Aún no tienes productos',
                texto: 'Agrega los productos que vendes. Después, aquí eliges cuáles ofreces cada día.',
                accion: 'Agregar tu primer producto',
                href: `${URL_PRODUCTOS}?nuevo=1`
            }));
        }

        grupos
            .filter(g => g.activa && (!g.sinCategoria || platosDe(g).length))
            .forEach(g => container.append(construirSeccion(g)));

        const ocultos = grupos.filter(g => !g.activa && platosDe(g).length);
        if (ocultos.length) container.append(construirOcultas(ocultos));

        renderizarChips();
        aplicarFiltros();
        actualizarEstado();
    }

    function buildPlatoRow(p) {
        const activo = seleccionadosHoy.has(p.id);
        const qty = cantidadesHoy[p.id];
        const hasPres = tienePresentaciones(p);
        const nombreHtml = escapeHtml(p.nombre);
        const id = Number(p.id);

        const precioDisplay = hasPres
            ? `desde ${MONEDA_HTML} ${escapeHtml(precioMinimo(p).toFixed(0))}`
            : `${MONEDA_HTML} ${escapeHtml(p.precio)}`;

        const stockBadge = (qty === null || qty === undefined)
            ? '<span class="plato-stock sin-control">∞ sin límite</span>'
            : `<span class="plato-stock" id="sbadge-${id}">${Number(qty)} porciones</span>`;

        const presBadge = hasPres
            ? `<span class="pres-badge">${p.presentaciones.length} tamaños</span>`
            : '';

        const qtyControl = (qty !== null && qty !== undefined) ? `
            <div class="qty-inline">
                <button type="button" class="qty-btn-sm" data-accion="menos" data-id="${id}"
                        aria-label="Restar una porción a ${nombreHtml}" title="Restar una porción">−</button>
                <input  class="qty-num-sm" id="qn-${id}" type="number" inputmode="numeric"
                        value="${Number(qty)}" min="0" step="1" data-id="${id}"
                        aria-label="Porciones de ${nombreHtml}">
                <button type="button" class="qty-btn-sm" data-accion="mas" data-id="${id}"
                        aria-label="Sumar una porción a ${nombreHtml}" title="Sumar una porción">+</button>
                <button type="button" class="limite-btn flat" data-accion="sinlimite" data-id="${id}"
                        aria-label="Quitar el límite de porciones a ${nombreHtml}" title="Vender sin límite de porciones">Sin límite</button>
            </div>` : `
            <div class="qty-inline">
                <button type="button" class="limite-btn" data-accion="limitar" data-id="${id}"
                        aria-label="Poner un límite de porciones a ${nombreHtml}" title="Indicar cuántas porciones hay">Poner límite</button>
            </div>`;

        const row = document.createElement('div');
        row.className = `plato-row${activo ? ' activo' : ''}`;
        row.id = `row-${id}`;
        row.dataset.nombre = normalizar(p.nombre);

        row.innerHTML = `
            <div class="plato-row-top">
                <button type="button" class="toggle-track ${activo ? 'on' : ''}" id="tog-${id}"
                    role="switch" aria-checked="${activo ? 'true' : 'false'}"
                    aria-label="Vender ${nombreHtml} hoy" title="Vender hoy"
                    data-accion="toggle" data-id="${id}">
                    <span class="toggle-thumb"></span>
                </button>
                <div class="plato-info">
                    <div class="plato-nombre">${nombreHtml}</div>
                    <div class="plato-meta">
                        <span class="plato-precio">${precioDisplay}</span>
                        ${stockBadge}
                        ${presBadge}
                    </div>
                </div>
            </div>

            <div class="plato-row-bottom">
                ${qtyControl}
                <div class="plato-actions">
                    <a class="act-btn edit" href="${URL_PRODUCTOS}?editar=${id}" aria-label="Editar ${nombreHtml}"><svg class="act-svg" viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 20h9"/><path d="M16.5 3.5a2.12 2.12 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg><span class="act-label">Editar</span></a>
                </div>
            </div>`;

        if (p.imagenUrl) {
            const img = document.createElement('img');
            img.className = 'plato-thumb';
            img.src = String(p.imagenUrl);
            img.alt = '';
            img.width = 48;
            img.height = 48;
            img.loading = 'lazy';
            img.decoding = 'async';
            img.addEventListener('error', () => img.remove(), { once: true });
            row.querySelector('.plato-info').before(img);
        }

        return row;
    }

    function contar(clave) {
        const lista = clave === TODAS ? catalogoMaestro : platosDe(grupoPorCodigo.get(clave) || {});
        return { total: lista.length, activos: lista.filter(p => seleccionadosHoy.has(p.id)).length };
    }

    function renderizarChips() {
        const cont = document.getElementById('chips');
        const presentes = grupos.filter(g => g.activa && platosDe(g).length);
        if (filtroCat !== TODAS && !presentes.some(g => g.codigo === filtroCat)) filtroCat = TODAS;
        cont.hidden = presentes.length === 0;
        const opciones = [{ codigo: TODAS, nombre: 'Todas', icono: '' }, ...presentes];
        cont.replaceChildren(...opciones.map(g => {
            const chip = document.createElement('button');
            chip.type = 'button';
            chip.className = 'chip';
            chip.dataset.cat = g.codigo;
            chip.setAttribute('aria-pressed', String(g.codigo === filtroCat));
            const nombre = document.createElement('span');
            nombre.textContent = g.icono ? `${g.icono} ${g.nombre}` : g.nombre;
            const cuenta = document.createElement('span');
            cuenta.className = 'chip-count';
            cuenta.dataset.cuenta = g.codigo;
            chip.append(nombre, cuenta);
            chip.addEventListener('click', () => seleccionarCategoria(g.codigo));
            return chip;
        }));
    }

    function subirAlContenido() {
        const toolbar = document.querySelector('.toolbar');
        const contenido = document.getElementById('menu-scroll');
        const cabecera = document.querySelector('.admin-header');
        if (!toolbar || !contenido) return;
        const fija = getComputedStyle(toolbar).position === 'sticky';
        const tope = (cabecera ? cabecera.offsetHeight : 0) + 8 + (fija ? toolbar.offsetHeight + 8 : 0);
        const destino = contenido.getBoundingClientRect().top + window.scrollY - tope;
        if (window.scrollY > destino) window.scrollTo({ top: Math.max(0, destino) });
    }

    function seleccionarCategoria(cat) {
        filtroCat = cat;
        document.querySelectorAll('#chips .chip').forEach(chip =>
            chip.setAttribute('aria-pressed', String(chip.dataset.cat === cat)));
        aplicarFiltros();
        subirAlContenido();
    }

    function aplicarFiltros() {
        const q = normalizar(filtroTexto);
        let seccionesVisibles = 0;
        const container = document.getElementById('menu-container');
        container.querySelectorAll('.cat-section').forEach(sec => {
            const enCat = filtroCat === TODAS || sec.dataset.cat === filtroCat;
            let coincidencias = 0;
            sec.querySelectorAll('.plato-row').forEach(row => {
                const ok = enCat && (!q || row.dataset.nombre.includes(q));
                row.hidden = !ok;
                if (ok) coincidencias++;
            });
            sec.hidden = !enCat || (q !== '' && coincidencias === 0);
            if (!sec.hidden) seccionesVisibles++;
        });
        const ocultas = container.querySelector('.cat-ocultas');
        if (ocultas) {
            const alguna = [...ocultas.querySelectorAll('.cat-section')].some(sec => !sec.hidden);
            ocultas.hidden = !alguna;
            const abrir = q ? alguna : ocultasAbiertas;
            if (ocultas.open !== abrir) ocultas.open = abrir;
        }
        const vacio = document.getElementById('empty-state');
        const sinResultados = catalogoCargado && catalogoMaestro.length > 0 && q !== '' && seccionesVisibles === 0;
        vacio.hidden = !sinResultados;
        if (sinResultados) {
            const texto = filtroTexto.trim();
            const grupo = grupoPorCodigo.get(filtroCat);
            document.getElementById('empty-text').textContent = grupo
                ? `Sin resultados para «${texto}» en ${grupo.nombre}`
                : `Sin resultados para «${texto}»`;
        }
    }

    function limpiarFiltros() {
        filtroTexto = '';
        const buscar = document.getElementById('buscar');
        buscar.value = '';
        seleccionarCategoria(TODAS);
        buscar.focus();
    }

    function actualizarEstado() {
        document.querySelectorAll('#chips .chip-count').forEach(el => {
            const c = contar(el.dataset.cuenta);
            el.textContent = `${c.activos}/${c.total}`;
            el.parentElement.title = `${c.activos} activos de ${plural(c.total, 'producto', 'productos')}`;
        });
        document.querySelectorAll('#menu-container .cat-section').forEach(sec => {
            const el = sec.querySelector('.cat-count');
            if (!el) return;
            const c = contar(sec.dataset.cat);
            el.textContent = c.total ? `${c.activos}/${c.total} activos` : '';
        });
        const total = contar(TODAS).activos;
        document.getElementById('tb-resumen').textContent = `${total} ${total === 1 ? 'activo' : 'activos'} hoy`;
        document.getElementById('ab-activos').textContent = plural(total, 'producto activo', 'productos activos');
        const cambios = hayCambios();
        document.getElementById('ab-pendiente').hidden = !cambios;
        document.getElementById('ab-al-dia').hidden = cambios;
        guardarBorradorMenu();
    }

    function togglePlato(id) {
        const tog = document.getElementById(`tog-${id}`);
        const row = document.getElementById(`row-${id}`);
        if (seleccionadosHoy.has(id)) {
            seleccionadosHoy.delete(id);
            tog?.classList.remove('on');
            tog?.setAttribute('aria-checked', 'false');
            row?.classList.remove('activo');
        } else {
            seleccionadosHoy.add(id);
            if (cantidadesHoy[id] === undefined) {
                const p = catalogoMaestro.find(x => x.id === id);
                cantidadesHoy[id] = p ? grupoDe(p).cantidadSugerida : null;
                refrescarFila(id);
            } else {
                tog?.classList.add('on');
                tog?.setAttribute('aria-checked', 'true');
                row?.classList.add('activo');
            }
        }
        actualizarEstado();
    }

    function refrescarFila(id) {
        const row = document.getElementById(`row-${id}`);
        const p = catalogoMaestro.find(x => x.id === id);
        if (!row || !p) return;
        const enfocado = row.contains(document.activeElement) ? document.activeElement.id : null;
        const nueva = buildPlatoRow(p);
        nueva.hidden = row.hidden;
        row.replaceWith(nueva);
        if (enfocado) document.getElementById(enfocado)?.focus();
    }

    function ajustarAvisos() {
        const bar = document.querySelector('.actions-bar');
        if (bar) document.body.style.setProperty('--notice-offset', `${bar.offsetHeight + 8}px`);
    }

    function adjQty(id, delta) {
        if (cantidadesHoy[id] === null) return;
        cantidadesHoy[id] = Math.max(0, (cantidadesHoy[id] ?? 0) + delta);
        const el = document.getElementById(`qn-${id}`); if (el) el.value = cantidadesHoy[id];
        const badge = document.getElementById(`sbadge-${id}`); if (badge) badge.textContent = `${cantidadesHoy[id]} porciones`;
        actualizarEstado();
    }

    function setQty(id, v) {
        cantidadesHoy[id] = v === '' ? null : Math.max(0, parseInt(v, 10) || 0);
        const badge = document.getElementById(`sbadge-${id}`);
        if (badge && cantidadesHoy[id] !== null) badge.textContent = `${cantidadesHoy[id]} porciones`;
        actualizarEstado();
    }

    async function guardarMenuDelDia() {
        const btn = document.getElementById('btn-save');
        const activos = catalogoMaestro.filter(p => seleccionadosHoy.has(p.id));
        const resumen = grupos
            .map(g => ({ g, n: platosDe(g).filter(p => seleccionadosHoy.has(p.id)).length }))
            .filter(x => x.n > 0)
            .map(x => ({
                etiqueta: x.g.activa ? x.g.nombre : `${x.g.nombre} (oculta)`,
                valor: plural(x.n, 'producto', 'productos')
            }));
        const items = activos.map(p => ({ nombre: p.nombre, detalle: textoStock(cantidadesHoy[p.id]) }));
        const mensaje = activos.length
            ? `Se publicarán ${plural(activos.length, 'producto', 'productos')}. Los productos no activos dejarán de estar disponibles para la venta.`
            : 'No hay productos activos: al publicar, ningún producto quedará disponible para la venta.';
        const ok = await confirmAction(mensaje, {
            titulo: '¿Publicar menú del día?', resumen, items,
            aceptar: 'Publicar', cancelar: 'Cancelar', peligro: activos.length === 0
        });
        if (!ok) { btn.focus(); return; }

        btn.textContent = 'Publicando…'; btn.disabled = true;
        const ids = new Set(catalogoMaestro.map(p => p.id));
        const payload = Array.from(seleccionadosHoy)
            .filter(id => ids.has(id))
            .map(id => ({ id, cantidad: cantidadesHoy[id] ?? null }));
        try {
            const r = await secureFetch('/api/menu-diario', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            if (!r.ok) throw await apiError(r);
            capturarPublicado();
            actualizarEstado();
            showNotice('Menú del día publicado', 'ok');
        } catch (e) {
            showNotice('Error: ' + e.message);
        } finally {
            btn.textContent = TEXTO_PUBLICAR; btn.disabled = false;
        }
    }

    document.getElementById('menu-container').addEventListener('click', e => {
        const boton = e.target.closest('button[data-accion]');
        if (!boton) return;
        const id = Number(boton.dataset.id);
        switch (boton.dataset.accion) {
            case 'toggle': togglePlato(id); break;
            case 'menos': adjQty(id, -1); break;
            case 'mas': adjQty(id, 1); break;
            case 'limitar': {
                const p = catalogoMaestro.find(x => x.id === id);
                cantidadesHoy[id] = (p && grupoDe(p).cantidadSugerida) || 20;
                refrescarFila(id);
                document.getElementById(`qn-${id}`)?.focus();
                actualizarEstado();
                break;
            }
            case 'sinlimite':
                cantidadesHoy[id] = null;
                refrescarFila(id);
                document.querySelector(`[data-accion="limitar"][data-id="${id}"]`)?.focus();
                actualizarEstado();
                break;
            default: break;
        }
    });

    document.getElementById('menu-container').addEventListener('change', e => {
        const input = e.target.closest('.qty-num-sm');
        if (input) setQty(Number(input.dataset.id), input.value);
    });

    document.getElementById('btn-save').addEventListener('click', guardarMenuDelDia);
    document.getElementById('btn-img').addEventListener('click', generarImagenMenu);
    document.getElementById('btn-limpiar').addEventListener('click', limpiarFiltros);

    document.getElementById('buscar').addEventListener('input', e => {
        filtroTexto = e.target.value;
        aplicarFiltros();
    });

    document.getElementById('chips').addEventListener('keydown', e => {
        const chips = [...e.currentTarget.querySelectorAll('.chip')];
        const i = chips.indexOf(document.activeElement);
        if (i < 0) return;
        const n = chips.length;
        const destino = { ArrowRight: (i + 1) % n, ArrowLeft: (i - 1 + n) % n, Home: 0, End: n - 1 }[e.key];
        if (destino === undefined) return;
        e.preventDefault();
        chips[destino].focus();
        chips[destino].scrollIntoView({ block: 'nearest', inline: 'nearest' });
    });

    async function generarImagenMenu() {
        const btn = document.getElementById('btn-img');
        const dias = ['DOMINGO', 'LUNES', 'MARTES', 'MIÉRCOLES', 'JUEVES', 'VIERNES', 'SÁBADO'];
        const meses = ['ENERO', 'FEBRERO', 'MARZO', 'ABRIL', 'MAYO', 'JUNIO', 'JULIO', 'AGOSTO', 'SEPTIEMBRE', 'OCTUBRE', 'NOVIEMBRE', 'DICIEMBRE'];
        const d = new Date();
        document.getElementById('cv-date').textContent = `HOY ${dias[d.getDay()]} ${d.getDate()} DE ${meses[d.getMonth()]}`;
        const contenedor = document.getElementById('cv-categorias');
        contenedor.replaceChildren();
        grupos
            .filter(g => g.activa)
            .forEach(g => {
                const platos = platosDe(g).filter(p => seleccionadosHoy.has(p.id));
                if (!platos.length) return;
                const bloque = document.createElement('section');
                bloque.className = 'mc-cat';
                const titulo = document.createElement('div');
                titulo.className = 'mc-cat-title';
                titulo.textContent = g.sinCategoria ? 'Otros' : g.nombre;
                const lista = document.createElement('ul');
                lista.className = 'mc-list';
                platos.forEach(p => {
                    const item = document.createElement('li');
                    const nombre = document.createElement('span');
                    nombre.className = 'mc-item-nombre';
                    nombre.textContent = p.nombre;
                    const precio = document.createElement('span');
                    precio.className = 'mc-item-precio';
                    precio.textContent = tienePresentaciones(p) ? `desde ${dinero(precioMinimo(p))}` : dinero(p.precio);
                    item.append(nombre, precio);
                    lista.append(item);
                });
                bloque.append(titulo, lista);
                contenedor.append(bloque);
            });
        if (!contenedor.children.length) {
            const vacio = document.createElement('div');
            vacio.className = 'mc-vacio';
            vacio.textContent = 'No hay productos activos hoy';
            contenedor.append(vacio);
        }
        if (typeof window.html2canvas !== 'function') {
            showNotice('No se pudo preparar la imagen. Revisa tu conexión a internet e inténtalo de nuevo.');
            return;
        }
        btn.disabled = true;
        try {
            const fondo = getComputedStyle(document.documentElement).getPropertyValue('--palette-dark-page').trim();
            const canvas = await html2canvas(document.getElementById('menu-canvas'), { scale: 2, useCORS: true, allowTaint: true, backgroundColor: fondo });
            const link = document.createElement('a');
            const nombreArchivo = String(BRANDING.nombreNegocio || 'Menu').replace(/[^a-zA-Z0-9_-]+/g, '_');
            link.download = `Menu_${nombreArchivo}_${d.getDate()}_${meses[d.getMonth()]}.jpg`;
            link.href = canvas.toDataURL('image/jpeg', .95); link.click();
            showNotice('Imagen del menú descargada', 'ok');
        } catch (err) {
            showNotice('Error al generar imagen.');
        } finally {
            btn.disabled = false;
        }
    }

    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible' && catalogoCargado) cargarCatalogo();
    });
    window.addEventListener('pageshow', e => {
        if (e.persisted && catalogoCargado) cargarCatalogo();
    });

    ajustarAvisos();
    window.addEventListener('resize', ajustarAvisos);
    if (window.ResizeObserver) new ResizeObserver(ajustarAvisos).observe(document.querySelector('.actions-bar'));
    cargarCatalogo();
})();
