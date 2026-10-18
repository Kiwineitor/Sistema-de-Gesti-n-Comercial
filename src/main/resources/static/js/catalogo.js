(function () {
    'use strict';

    const POCAS_UNIDADES = 3;

    function el(tag, clase, texto) {
        const nodo = document.createElement(tag);
        if (clase) nodo.className = clase;
        if (texto !== undefined && texto !== null) nodo.textContent = String(texto);
        return nodo;
    }

    function normalizar(texto) {
        return String(texto ?? '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
    }

    function negocio() {
        return window.NEGOCIO || {};
    }

    function dinero(monto) {
        const simbolo = negocio().simboloMoneda || '';
        return `${simbolo} ${Number(monto || 0).toFixed(2)}`.trim();
    }

    function minuscula(texto) {
        const t = String(texto || '').trim();
        return t ? t.charAt(0).toLowerCase() + t.slice(1) : t;
    }

    function textoModalidad(modalidad) {
        if (modalidad === 'MESA') return `Solo en ${minuscula(negocio().puntoAtencionSingular || 'mesa')}`;
        if (modalidad === 'LLEVAR') return `Solo ${minuscula(negocio().etiquetaParaLlevar || 'para llevar')}`;
        return '';
    }

    function stockDe(item) {
        const s = item.stockDisponible;
        return s === null || s === undefined ? null : Number(s);
    }

    function estadoPresentacion(pres) {
        if (pres.disponible === false) return { tipo: 'agotado', texto: 'No disponible' };
        const s = stockDe(pres);
        if (s === null) return { tipo: 'ok', texto: 'Disponible' };
        if (s <= 0) return { tipo: 'agotado', texto: 'Agotado' };
        if (s <= POCAS_UNIDADES) return { tipo: 'pocas', texto: `Últimas ${s}` };
        return { tipo: 'ok', texto: 'Disponible' };
    }

    function estadoPlato(plato) {
        if (plato.disponible === false) return { tipo: 'off', texto: 'No disponible hoy', elegible: false };
        const propio = stockDe(plato);
        if (propio !== null && propio <= 0) return { tipo: 'agotado', texto: 'Agotado', elegible: false };
        let restante = propio;
        const pres = plato.presentaciones || [];
        if (pres.length) {
            const vivas = pres.filter(p => estadoPresentacion(p).tipo !== 'agotado');
            if (!vivas.length) return { tipo: 'agotado', texto: 'Agotado', elegible: false };
            const stocks = vivas.map(stockDe);
            if (!stocks.includes(null)) {
                const suma = stocks.reduce((a, b) => a + b, 0);
                restante = restante === null ? suma : Math.min(restante, suma);
            }
        }
        if (restante !== null && restante <= POCAS_UNIDADES) {
            return { tipo: 'pocas', texto: `Últimas ${restante}`, elegible: true };
        }
        return { tipo: 'ok', texto: 'Disponible', elegible: true };
    }

    function precioPlato(plato) {
        const pres = (plato.presentaciones || []);
        if (!pres.length) return { texto: dinero(plato.precio), desde: false };
        const vivas = pres.filter(p => estadoPresentacion(p).tipo !== 'agotado');
        const base = (vivas.length ? vivas : pres).map(p => Number(p.precio || 0));
        const minimo = Math.min(...base);
        const varias = new Set(base).size > 1 || pres.length > 1;
        return { texto: varias ? `desde ${dinero(minimo)}` : dinero(minimo), desde: varias };
    }

    function chipEstado(estado) {
        const chip = el('span', `ctl-estado ctl-estado-${estado.tipo}`, estado.texto);
        return chip;
    }

    function placeholder(icono, clase) {
        const caja = el('span', `ctl-foto-vacia ${clase || ''}`.trim());
        caja.setAttribute('aria-hidden', 'true');
        caja.append(el('span', 'ctl-foto-icono', icono || '🍽️'));
        return caja;
    }

    function foto(plato, icono, clase, perezosa) {
        if (!plato.imagenUrl) return placeholder(icono, clase);
        const img = el('img', clase);
        img.alt = '';
        img.decoding = 'async';
        if (perezosa) img.loading = 'lazy';
        img.src = plato.imagenUrl;
        img.addEventListener('error', () => img.replaceWith(placeholder(icono, clase)), { once: true });
        return img;
    }

    function fotoDetalle(plato, icono) {
        const lienzo = el('div', 'ctl-det-lienzo');
        const vacio = () => {
            lienzo.classList.add('ctl-det-lienzo-vacio');
            lienzo.replaceChildren(placeholder(icono, 'ctl-det-vacia'));
        };
        if (!plato.imagenUrl) {
            vacio();
            return lienzo;
        }
        const difuso = el('img', 'ctl-det-difuso');
        difuso.alt = '';
        difuso.setAttribute('aria-hidden', 'true');
        difuso.decoding = 'async';
        difuso.src = plato.imagenUrl;
        difuso.addEventListener('error', () => difuso.remove(), { once: true });
        const velo = el('span', 'ctl-det-velo');
        velo.setAttribute('aria-hidden', 'true');
        const img = el('img', 'ctl-det-foto');
        img.alt = '';
        img.decoding = 'async';
        img.draggable = false;
        img.src = plato.imagenUrl;
        img.addEventListener('error', vacio, { once: true });
        lienzo.append(difuso, velo, img);
        return lienzo;
    }

    function esqueleto(contenedor) {
        const raiz = el('div', 'ctl ctl-esqueleto');
        raiz.setAttribute('aria-busy', 'true');
        raiz.append(el('span', 'visually-hidden', 'Cargando catálogo…'));
        const chips = el('div', 'ctl-esq-chips');
        for (let i = 0; i < 4; i++) chips.append(el('span', 'ctl-esq ctl-esq-chip'));
        const grid = el('div', 'ctl-grid');
        for (let i = 0; i < 6; i++) {
            const card = el('div', 'ctl-esq-card');
            card.append(el('span', 'ctl-esq ctl-esq-foto'), el('span', 'ctl-esq ctl-esq-linea'), el('span', 'ctl-esq ctl-esq-linea ctl-esq-corta'));
            grid.append(card);
        }
        raiz.append(chips, grid);
        contenedor.replaceChildren(raiz);
        return raiz;
    }

    function montar(contenedor, opciones) {
        const op = Object.assign({ modo: 'pagina' }, opciones || {});
        const overlay = op.modo === 'overlay';
        let platos = [];
        let categorias = [];
        let secciones = [];
        let tarjetas = new Map();
        let busqueda = '';
        let activa = null;
        let bloqueoSpy = 0;
        let abierto = null;
        let focoPrevio = null;
        let rafScroll = 0;
        let destruido = false;

        const raiz = el('div', `ctl ctl-modo-${overlay ? 'overlay' : 'pagina'}`);
        if (overlay) {
            raiz.setAttribute('role', 'dialog');
            raiz.setAttribute('aria-modal', 'true');
        }

        const cabecera = el('header', 'ctl-cabecera');
        const marca = el('div', 'ctl-marca');
        const logoUrl = negocio().logoUrl;
        if (logoUrl) {
            const logo = el('img', 'ctl-logo');
            logo.src = logoUrl;
            logo.alt = '';
            logo.decoding = 'async';
            marca.append(logo);
        }
        const textos = el('div', 'ctl-marca-textos');
        const titulo = el('h1', 'ctl-titulo', op.titulo || negocio().nombreNegocio || 'Catálogo');
        titulo.id = `ctl-titulo-${Math.random().toString(36).slice(2, 8)}`;
        textos.append(titulo);
        if (negocio().eslogan) textos.append(el('p', 'ctl-eslogan', negocio().eslogan));
        marca.append(textos);
        if (overlay) {
            raiz.setAttribute('aria-labelledby', titulo.id);
            const cerrar = el('button', 'ctl-cerrar flat', '✕');
            cerrar.type = 'button';
            cerrar.setAttribute('aria-label', 'Cerrar catálogo');
            cerrar.addEventListener('click', () => { if (typeof op.alCerrar === 'function') op.alCerrar(); });
            marca.append(cerrar);
        }

        const buscador = el('div', 'ctl-buscar');
        buscador.setAttribute('role', 'search');
        const lupa = el('span', 'ctl-lupa', '⌕');
        lupa.setAttribute('aria-hidden', 'true');
        const campo = el('input', 'ctl-campo');
        campo.type = 'search';
        campo.placeholder = 'Buscar en el catálogo';
        campo.setAttribute('aria-label', 'Buscar productos');
        campo.autocomplete = 'off';
        campo.enterKeyHint = 'search';
        const limpiar = el('button', 'ctl-limpiar flat', '✕');
        limpiar.type = 'button';
        limpiar.hidden = true;
        limpiar.setAttribute('aria-label', 'Borrar búsqueda');
        buscador.append(lupa, campo, limpiar);

        const chips = el('nav', 'ctl-chips');
        chips.setAttribute('aria-label', 'Categorías');
        cabecera.append(marca, buscador, chips);

        const cuerpo = el('div', 'ctl-cuerpo');
        const vacio = el('div', 'ctl-vacio');
        vacio.hidden = true;
        const vivo = el('div', 'visually-hidden');
        vivo.setAttribute('aria-live', 'polite');
        vivo.setAttribute('role', 'status');

        const capa = el('div', 'ctl-detalle-capa');
        capa.hidden = true;
        const fondo = el('div', 'ctl-detalle-fondo');
        const panel = el('div', 'ctl-detalle');
        panel.setAttribute('role', 'dialog');
        panel.setAttribute('aria-modal', 'true');
        panel.tabIndex = -1;
        capa.append(fondo, panel);

        raiz.append(cabecera, cuerpo, vacio, vivo, capa);
        contenedor.append(raiz);

        function categoriaPorCodigo(codigo) {
            return categorias.find(c => c.codigo === codigo);
        }

        function construir() {
            const activas = categorias.filter(c => c.activa !== false)
                .slice().sort((a, b) => (a.orden ?? 0) - (b.orden ?? 0) || String(a.nombre).localeCompare(String(b.nombre)));
            secciones = activas.map(c => ({ cat: c, platos: platos.filter(p => p.categoria === c.codigo) }))
                .filter(s => s.platos.length);
            tarjetas = new Map();
            chips.replaceChildren();
            cuerpo.replaceChildren();
            secciones.forEach(sec => {
                const chip = el('button', 'ctl-chip flat');
                chip.type = 'button';
                chip.dataset.cat = sec.cat.codigo;
                if (sec.cat.icono) {
                    const ic = el('span', 'ctl-chip-icono', sec.cat.icono);
                    ic.setAttribute('aria-hidden', 'true');
                    chip.append(ic);
                }
                chip.append(el('span', null, sec.cat.nombre));
                chip.addEventListener('click', () => irA(sec.cat.codigo));
                sec.chip = chip;
                chips.append(chip);

                const seccion = el('section', 'ctl-seccion');
                seccion.dataset.cat = sec.cat.codigo;
                const h = el('h2', 'ctl-seccion-titulo');
                if (sec.cat.icono) {
                    const ic = el('span', 'ctl-seccion-icono', sec.cat.icono);
                    ic.setAttribute('aria-hidden', 'true');
                    h.append(ic);
                }
                h.append(el('span', null, sec.cat.nombre));
                const grid = el('div', 'ctl-grid');
                sec.platos.forEach(p => {
                    const card = tarjeta(p, sec.cat);
                    tarjetas.set(p.id, { card, plato: p, cat: sec.cat });
                    grid.append(card);
                });
                seccion.append(h, grid);
                sec.seccion = seccion;
                cuerpo.append(seccion);
            });
            filtrar(false);
        }

        function tarjeta(plato, cat) {
            const estado = estadoPlato(plato);
            const card = el('button', `ctl-card ctl-card-${estado.tipo}`);
            card.type = 'button';
            card.dataset.id = plato.id;
            const media = el('span', 'ctl-card-media');
            media.append(foto(plato, cat.icono, 'ctl-card-foto', true));
            const chip = chipEstado(estado);
            media.append(chip);
            const info = el('span', 'ctl-card-info');
            info.append(el('span', 'ctl-card-nombre', plato.nombre));
            if (plato.descripcion) info.append(el('span', 'ctl-card-desc', plato.descripcion));
            const precio = precioPlato(plato);
            info.append(el('span', 'ctl-card-precio', precio.texto));
            card.append(media, info);
            card.setAttribute('aria-label', `${plato.nombre}, ${precio.texto}, ${estado.texto}`);
            card.addEventListener('click', () => abrirProducto(plato.id));
            return card;
        }

        function refrescarTarjeta(id) {
            const reg = tarjetas.get(id);
            if (!reg) return;
            const nueva = tarjeta(reg.plato, reg.cat);
            nueva.hidden = reg.card.hidden;
            const tenia = document.activeElement === reg.card;
            reg.card.replaceWith(nueva);
            reg.card = nueva;
            if (tenia) nueva.focus({ preventScroll: true });
        }

        function coincide(plato, cat) {
            if (!busqueda) return true;
            const texto = normalizar(`${plato.nombre} ${plato.descripcion || ''} ${cat.nombre || ''} ${(plato.presentaciones || []).map(p => p.nombre).join(' ')}`);
            return busqueda.split(/\s+/).every(t => texto.includes(t));
        }

        function filtrar(animar) {
            let total = 0;
            secciones.forEach(sec => {
                let visibles = 0;
                sec.platos.forEach(p => {
                    const reg = tarjetas.get(p.id);
                    const ver = coincide(p, sec.cat);
                    reg.card.hidden = !ver;
                    if (ver) visibles++;
                });
                sec.seccion.hidden = visibles === 0;
                sec.chip.hidden = visibles === 0;
                total += visibles;
            });
            vacio.replaceChildren();
            vacio.hidden = total > 0;
            if (!total) {
                vacio.append(el('span', 'ctl-vacio-icono', busqueda ? '🔍' : '🍽️'));
                if (busqueda) {
                    vacio.append(el('p', 'ctl-vacio-titulo', `No encontramos productos para «${campo.value.trim()}»`));
                    const btn = el('button', 'button-secondary', 'Ver todo el catálogo');
                    btn.type = 'button';
                    btn.addEventListener('click', () => { campo.value = ''; alBuscar(); campo.focus(); });
                    vacio.append(btn);
                } else {
                    vacio.append(el('p', 'ctl-vacio-titulo', 'Todavía no hay productos a la venta hoy'));
                }
            }
            if (animar) {
                cuerpo.classList.remove('ctl-fade');
                void cuerpo.offsetWidth;
                cuerpo.classList.add('ctl-fade');
            }
            const primera = secciones.find(s => !s.seccion.hidden);
            if (!activa || !secciones.some(s => s.cat.codigo === activa && !s.seccion.hidden)) {
                marcarActiva(primera ? primera.cat.codigo : null, false);
            }
        }

        function alBuscar() {
            busqueda = normalizar(campo.value);
            limpiar.hidden = !campo.value;
            filtrar(true);
            if (busqueda) desplazar(0, false);
        }

        campo.addEventListener('input', alBuscar);
        campo.addEventListener('keydown', e => { if (e.key === 'Enter') campo.blur(); });
        limpiar.addEventListener('click', () => { campo.value = ''; alBuscar(); campo.focus(); });

        function marcarActiva(codigo, centrar) {
            activa = codigo;
            secciones.forEach(sec => {
                const es = sec.cat.codigo === codigo;
                sec.chip.classList.toggle('ctl-chip-activo', es);
                if (es) sec.chip.setAttribute('aria-current', 'true');
                else sec.chip.removeAttribute('aria-current');
                if (es && centrar) {
                    const izq = sec.chip.offsetLeft - (chips.clientWidth - sec.chip.offsetWidth) / 2;
                    chips.scrollTo({ left: Math.max(0, izq), behavior: suave() });
                }
            });
        }

        function suave() {
            return window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth';
        }

        function scrollActual() {
            return overlay ? raiz.scrollTop : window.scrollY;
        }

        function desplazar(top, animado) {
            const opciones = { top: Math.max(0, top), behavior: animado ? suave() : 'auto' };
            if (overlay) raiz.scrollTo(opciones); else window.scrollTo(opciones);
        }

        function bordeSuperior() {
            const base = overlay ? raiz.getBoundingClientRect().top : 0;
            return base + cabecera.getBoundingClientRect().height;
        }

        function irA(codigo) {
            const sec = secciones.find(s => s.cat.codigo === codigo);
            if (!sec) return;
            marcarActiva(codigo, true);
            bloqueoSpy = Date.now() + 900;
            const destino = sec.seccion.getBoundingClientRect().top - bordeSuperior() + scrollActual() - 8;
            desplazar(destino, true);
        }

        function espiar() {
            rafScroll = 0;
            if (Date.now() < bloqueoSpy) return;
            const limite = bordeSuperior() + 24;
            let actual = null;
            for (const sec of secciones) {
                if (sec.seccion.hidden) continue;
                if (!actual) actual = sec;
                if (sec.seccion.getBoundingClientRect().top <= limite) actual = sec;
                else break;
            }
            const fondoAlcanzado = overlay
                ? raiz.scrollTop + raiz.clientHeight >= raiz.scrollHeight - 4
                : window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 4;
            if (fondoAlcanzado) {
                const ultimas = secciones.filter(s => !s.seccion.hidden);
                if (ultimas.length) actual = ultimas[ultimas.length - 1];
            }
            if (actual && actual.cat.codigo !== activa) marcarActiva(actual.cat.codigo, true);
        }

        function alDesplazar() {
            if (!rafScroll) rafScroll = requestAnimationFrame(espiar);
            cabecera.classList.toggle('ctl-cabecera-flotando', scrollActual() > 8);
        }

        const objetivoScroll = overlay ? raiz : window;
        objetivoScroll.addEventListener('scroll', alDesplazar, { passive: true });

        function visiblesOrdenados() {
            const lista = [];
            secciones.forEach(sec => {
                if (sec.seccion.hidden) return;
                sec.platos.forEach(p => { if (!tarjetas.get(p.id).card.hidden) lista.push(p.id); });
            });
            return lista;
        }

        function renderDetalle(id, direccion) {
            const reg = tarjetas.get(id);
            if (!reg) return false;
            const { plato, cat } = reg;
            const estado = estadoPlato(plato);
            const lista = visiblesOrdenados();
            const idx = lista.indexOf(id);

            panel.replaceChildren();
            panel.className = `ctl-detalle ctl-detalle-${estado.tipo}`;
            if (direccion) panel.classList.add(direccion > 0 ? 'ctl-entra-der' : 'ctl-entra-izq');
            const tituloId = `${titulo.id}-det`;
            panel.setAttribute('aria-labelledby', tituloId);

            const media = el('div', 'ctl-det-media');
            media.append(el('span', 'ctl-det-asa'));
            media.append(fotoDetalle(plato, cat.icono));
            const cerrar = el('button', 'ctl-det-cerrar', '✕');
            cerrar.type = 'button';
            cerrar.setAttribute('aria-label', 'Cerrar detalle');
            cerrar.addEventListener('click', cerrarDetalle);
            if (lista.length > 1) {
                const ant = el('button', 'ctl-det-nav ctl-det-ant', '‹');
                ant.type = 'button';
                ant.setAttribute('aria-label', 'Producto anterior');
                ant.disabled = idx <= 0;
                ant.addEventListener('click', () => mover(-1));
                const sig = el('button', 'ctl-det-nav ctl-det-sig', '›');
                sig.type = 'button';
                sig.setAttribute('aria-label', 'Producto siguiente');
                sig.disabled = idx < 0 || idx >= lista.length - 1;
                sig.addEventListener('click', () => mover(1));
                media.append(ant, sig);
                if (idx >= 0) media.append(el('span', 'ctl-det-pos', `${idx + 1} / ${lista.length}`));
            }

            const info = el('div', 'ctl-det-info');
            info.setAttribute('data-scroll-pista', '');
            const cab = el('div', 'ctl-det-cab');
            const catTxt = el('span', 'ctl-det-cat', `${cat.icono ? cat.icono + ' ' : ''}${cat.nombre}`);
            cab.append(catTxt, chipEstado(estado));
            const nombre = el('h2', 'ctl-det-nombre', plato.nombre);
            nombre.id = tituloId;
            info.append(cab, nombre);
            if (plato.descripcion) info.append(el('p', 'ctl-det-desc', plato.descripcion));
            const pres = plato.presentaciones || [];
            if (pres.length) {
                info.append(el('h3', 'ctl-det-sub', 'Tamaños'));
                const ul = el('ul', 'ctl-det-pres');
                pres.forEach(pr => {
                    const est = estadoPresentacion(pr);
                    const li = el('li', `ctl-pres ctl-pres-${est.tipo}`);
                    const izq = el('div', 'ctl-pres-izq');
                    izq.append(el('span', 'ctl-pres-nombre', pr.nombre));
                    const etiquetas = el('div', 'ctl-pres-etiquetas');
                    const mod = textoModalidad(pr.modalidad);
                    if (mod) etiquetas.append(el('span', 'ctl-modalidad', mod));
                    if (est.tipo !== 'ok') etiquetas.append(chipEstado(est));
                    if (etiquetas.childNodes.length) izq.append(etiquetas);
                    li.append(izq, el('span', 'ctl-pres-precio', dinero(pr.precio)));
                    ul.append(li);
                });
                info.append(ul);
            } else {
                info.append(el('p', 'ctl-det-precio', dinero(plato.precio)));
            }

            if (typeof op.alElegir === 'function') {
                const acciones = el('div', 'ctl-det-acciones');
                if (estado.elegible) {
                    const elegir = el('button', 'button-primary ctl-elegir', 'Agregar al pedido');
                    elegir.type = 'button';
                    elegir.addEventListener('click', () => {
                        const elegido = plato;
                        cerrarDetalle();
                        op.alElegir(elegido);
                    });
                    acciones.append(elegir);
                } else {
                    acciones.append(el('p', 'ctl-det-aviso', estado.tipo === 'off' ? 'Este producto no está a la venta hoy.' : 'Este producto está agotado.'));
                }
                info.append(acciones);
            }

            panel.append(cerrar, media, info);
            abierto = id;
            return true;
        }

        function abrirProducto(id) {
            const clave = [...tarjetas.keys()].find(k => String(k) === String(id));
            if (clave === undefined) return;
            if (!abierto) focoPrevio = document.activeElement;
            if (!renderDetalle(clave, 0)) return;
            capa.hidden = false;
            raiz.classList.add('ctl-con-detalle');
            if (!overlay) document.documentElement.classList.add('ctl-bloqueado');
            requestAnimationFrame(() => {
                const cerrar = panel.querySelector('.ctl-det-cerrar');
                (cerrar || panel).focus({ preventScroll: true });
            });
        }

        function cerrarDetalle() {
            if (!abierto) return;
            const ultimo = abierto;
            abierto = null;
            capa.hidden = true;
            panel.replaceChildren();
            raiz.classList.remove('ctl-con-detalle');
            document.documentElement.classList.remove('ctl-bloqueado');
            const reg = tarjetas.get(ultimo);
            const destino = reg && !reg.card.hidden ? reg.card : focoPrevio;
            if (destino && document.contains(destino)) {
                destino.focus({ preventScroll: destino !== (reg && reg.card) });
                if (reg && destino === reg.card) reg.card.scrollIntoView({ block: 'nearest' });
            }
            focoPrevio = null;
        }

        function mover(paso) {
            if (!abierto) return;
            const lista = visiblesOrdenados();
            const idx = lista.indexOf(abierto);
            const nuevo = lista[idx + paso];
            if (nuevo === undefined) return;
            const foco = document.activeElement;
            const claseFoco = foco && foco.classList.contains('ctl-det-ant') ? '.ctl-det-ant'
                : foco && foco.classList.contains('ctl-det-sig') ? '.ctl-det-sig' : null;
            renderDetalle(nuevo, paso);
            let destino = claseFoco ? panel.querySelector(claseFoco) : null;
            if (!destino || destino.disabled) destino = panel.querySelector('.ctl-det-cerrar');
            if (destino) destino.focus({ preventScroll: true });
        }

        fondo.addEventListener('click', cerrarDetalle);

        let gesto = null;
        panel.addEventListener('pointerdown', e => {
            if (e.pointerType === 'mouse' || e.target.closest('button, a, input')) return;
            const enMedia = !!e.target.closest('.ctl-det-media');
            gesto = { id: e.pointerId, x: e.clientX, y: e.clientY, enMedia, t: Date.now() };
        });
        panel.addEventListener('pointermove', e => {
            if (!gesto || gesto.id !== e.pointerId || !gesto.enMedia) return;
            const dy = e.clientY - gesto.y;
            const dx = e.clientX - gesto.x;
            if (dy > 0 && Math.abs(dy) > Math.abs(dx)) panel.style.transform = `translateY(${dy}px)`;
            else if (Math.abs(dx) > Math.abs(dy)) panel.style.transform = `translateX(${dx * 0.35}px)`;
        });
        function finGesto(e, cancelado) {
            if (!gesto || gesto.id !== e.pointerId) return;
            const dx = e.clientX - gesto.x;
            const dy = e.clientY - gesto.y;
            const enMedia = gesto.enMedia;
            gesto = null;
            panel.style.transform = '';
            if (cancelado && !enMedia) return;
            if (Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.4) mover(dx < 0 ? 1 : -1);
            else if (enMedia && dy > 90 && dy > Math.abs(dx) * 1.4) cerrarDetalle();
        }
        panel.addEventListener('pointerup', e => finGesto(e, false));
        panel.addEventListener('pointercancel', e => {
            if (gesto && gesto.id === e.pointerId) { gesto = null; panel.style.transform = ''; }
        });

        function focables(nodo) {
            return [...nodo.querySelectorAll('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])')]
                .filter(x => !x.disabled && !x.hidden && x.offsetParent !== null);
        }

        function atrapar(e, nodo) {
            const lista = focables(nodo);
            if (!lista.length) { e.preventDefault(); nodo.focus(); return; }
            const primero = lista[0];
            const ultimo = lista[lista.length - 1];
            if (e.shiftKey && (document.activeElement === primero || !nodo.contains(document.activeElement))) {
                e.preventDefault(); ultimo.focus();
            } else if (!e.shiftKey && (document.activeElement === ultimo || !nodo.contains(document.activeElement))) {
                e.preventDefault(); primero.focus();
            }
        }

        function alTeclado(e) {
            if (destruido) return;
            if (abierto) {
                if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); cerrarDetalle(); }
                else if (e.key === 'ArrowLeft') { e.preventDefault(); mover(-1); }
                else if (e.key === 'ArrowRight') { e.preventDefault(); mover(1); }
                else if (e.key === 'Tab') atrapar(e, panel);
                return;
            }
            if (!overlay || !raiz.isConnected || raiz.getClientRects().length === 0) return;
            const dentro = raiz.contains(document.activeElement) || document.activeElement === document.body;
            if (!dentro) return;
            if (e.key === 'Escape') {
                if (document.activeElement === campo && campo.value) { e.preventDefault(); campo.value = ''; alBuscar(); return; }
                if (typeof op.alCerrar === 'function') { e.preventDefault(); op.alCerrar(); }
            } else if (e.key === 'Tab') atrapar(e, raiz);
        }
        document.addEventListener('keydown', alTeclado, true);

        function anunciar(texto) {
            vivo.textContent = '';
            setTimeout(() => { vivo.textContent = texto; }, 60);
        }

        function actualizar(datos) {
            const scroll = scrollActual();
            const prevAbierto = abierto;
            const prevActiva = activa;
            if (datos && Array.isArray(datos.platos)) platos = datos.platos;
            if (datos && Array.isArray(datos.categorias)) categorias = datos.categorias;
            construir();
            if (prevActiva && secciones.some(s => s.cat.codigo === prevActiva && !s.seccion.hidden)) marcarActiva(prevActiva, false);
            desplazar(scroll, false);
            if (prevAbierto !== null) {
                if (tarjetas.has(prevAbierto)) renderDetalle(prevAbierto, 0);
                else cerrarDetalle();
            }
        }

        function actualizarStock(msg) {
            if (!msg || msg.platoId === undefined || msg.platoId === null) return;
            const reg = [...tarjetas.values()].find(r => String(r.plato.id) === String(msg.platoId));
            const plato = reg ? reg.plato : platos.find(p => String(p.id) === String(msg.platoId));
            if (!plato) return;
            const antes = estadoPlato(plato).tipo;
            let nombre = plato.nombre;
            if (msg.presentacionId !== undefined && msg.presentacionId !== null) {
                const pr = (plato.presentaciones || []).find(p => String(p.id) === String(msg.presentacionId));
                if (!pr) return;
                pr.stockDisponible = msg.stockRestante;
                nombre = `${plato.nombre} (${pr.nombre})`;
            } else {
                plato.stockDisponible = msg.stockRestante;
            }
            if (!reg) return;
            refrescarTarjeta(reg.plato.id);
            if (abierto === reg.plato.id) {
                const foco = document.activeElement;
                const clase = foco && panel.contains(foco) ? [...foco.classList].find(c => c.startsWith('ctl-det-') || c === 'ctl-elegir') : null;
                const scrollInfo = panel.querySelector('.ctl-det-info')?.scrollTop || 0;
                renderDetalle(reg.plato.id, 0);
                const info = panel.querySelector('.ctl-det-info');
                if (info) info.scrollTop = scrollInfo;
                const destino = (clase && panel.querySelector(`.${clase}`)) || panel.querySelector('.ctl-det-cerrar');
                if (destino && !destino.disabled) destino.focus({ preventScroll: true });
            }
            const despues = estadoPlato(plato);
            const s = Number(msg.stockRestante);
            if (despues.tipo !== antes || (s >= 0 && s <= POCAS_UNIDADES)) {
                anunciar(s <= 0 ? `${nombre}: agotado` : `${nombre}: quedan ${s}`);
            }
        }

        function destruir() {
            if (destruido) return;
            destruido = true;
            objetivoScroll.removeEventListener('scroll', alDesplazar);
            document.removeEventListener('keydown', alTeclado, true);
            if (rafScroll) cancelAnimationFrame(rafScroll);
            document.documentElement.classList.remove('ctl-bloqueado');
            raiz.remove();
        }

        actualizar({ platos: op.platos || [], categorias: op.categorias || [] });

        return {
            elemento: raiz,
            actualizar,
            actualizarStock,
            abrirProducto,
            cerrarDetalle,
            destruir,
            enfocarBusqueda: () => campo.focus()
        };
    }

    window.Catalogo = { montar, esqueleto };
})();
