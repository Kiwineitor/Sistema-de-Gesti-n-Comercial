(function () {
    'use strict';

    const NEGOCIO = window.NEGOCIO || {};
    const MESA = NEGOCIO.puntoAtencionSingular || 'Mesa';
    const LLEVAR = NEGOCIO.etiquetaParaLlevar || 'Para llevar';
    const CON_PUNTOS = Number(NEGOCIO.cantidadPuntosAtencion) > 0;
    const MONEDA = NEGOCIO.simboloMoneda || '';
    const MAX_BYTES = 1000 * 1000;
    const LADO_MAX = 1600;
    const PROPORCION = 4 / 3;
    const CLAVE_NUEVO = 'producto-nuevo';
    const SIN_CATEGORIA = '__sin';
    const MODALIDADES = {
        AMBAS: { icono: '🔄', texto: `${MESA} y ${LLEVAR}` },
        MESA: { icono: '🪑', texto: `Solo ${MESA}` },
        LLEVAR: { icono: '📦', texto: `Solo ${LLEVAR}` }
    };

    const estado = {
        productos: [],
        categorias: [],
        mapaCategorias: new Map(),
        cargado: false,
        filtroCat: '',
        filtroTexto: '',
        filtroFoto: '',
        cerrarDialogo: null,
        versiones: new Map()
    };
    let main, dialogo, contenido, resultado, chips;
    let soportaWebp = null;

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

    function dinero(valor) {
        if (window.Admin && typeof window.Admin.dinero === 'function') return window.Admin.dinero(valor);
        return `${MONEDA} ${(Number(valor) || 0).toFixed(2)}`.trim();
    }

    function plural(n, uno, varios) {
        return `${n} ${n === 1 ? uno : varios}`;
    }

    function normalizar(texto) {
        return String(texto || '').normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim();
    }

    function kb(bytes) {
        return bytes >= 1000 * 1000 ? `${(bytes / (1000 * 1000)).toFixed(2)} MB` : `${Math.max(1, Math.round(bytes / 1000))} KB`;
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
    function tieneBorrador(clave) {
        const datos = leerBorrador(clave);
        return !!datos && typeof datos === 'object' && Object.keys(datos).length > 0;
    }

    async function pedir(url, opciones = {}) {
        const config = { method: opciones.method || 'GET', headers: { Accept: 'application/json' } };
        if (opciones.formData) {
            config.body = opciones.formData;
        } else if (opciones.body !== undefined) {
            config.headers['Content-Type'] = 'application/json';
            config.body = JSON.stringify(opciones.body);
        }
        const respuesta = await window.secureFetch(url, config);
        if (!respuesta.ok) {
            const error = typeof window.apiError === 'function' ? await window.apiError(respuesta) : new Error(`Error HTTP ${respuesta.status}`);
            error.status = respuesta.status;
            if (respuesta.status === 413 && /^Error HTTP/.test(error.message)) error.message = 'La foto es demasiado pesada.';
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

    function categoriaDe(p) {
        const codigo = String(p?.categoria ?? '');
        return estado.mapaCategorias.get(codigo) || estado.mapaCategorias.get(codigo.toUpperCase()) || null;
    }

    function iconoDe(p) {
        return categoriaDe(p)?.icono || '🍽️';
    }

    function urlFoto(p) {
        if (!p || !p.imagenUrl) return null;
        const version = estado.versiones.get(p.id);
        if (!version) return p.imagenUrl;
        return `${p.imagenUrl}${p.imagenUrl.includes('?') ? '&' : '?'}v=${version}`;
    }

    function precioTexto(p) {
        const tam = Array.isArray(p.presentaciones) ? p.presentaciones : [];
        if (tam.length) return `desde ${dinero(Math.min(...tam.map(x => Number(x.precio) || 0)))}`;
        return dinero(p.precio);
    }

    async function cargar() {
        if (!estado.cargado && window.Admin) mostrarCargando();
        try {
            const [productos, categorias] = await Promise.all([
                pedir('/api/platos/todos'),
                pedir('/api/categorias').catch(() => null)
            ]);
            estado.productos = Array.isArray(productos) ? productos : [];
            if (Array.isArray(categorias)) {
                estado.categorias = [...categorias].sort((a, b) => (Number(a.orden) || 0) - (Number(b.orden) || 0));
                estado.mapaCategorias = new Map();
                estado.categorias.forEach(c => {
                    estado.mapaCategorias.set(String(c.codigo), c);
                    estado.mapaCategorias.set(String(c.codigo).toUpperCase(), c);
                });
            }
            const primeraVez = !estado.cargado;
            estado.cargado = true;
            pintarChips();
            pintarLista();
            if (primeraVez) abrirSegunEnlace();
        } catch (error) {
            resultado.textContent = '';
            chips.replaceChildren();
            if (window.Admin) {
                window.Admin.vacio(contenido, {
                    icono: '⚠️',
                    titulo: 'No se pudieron cargar los productos',
                    texto: error.message || 'Revisa tu conexión e inténtalo otra vez.',
                    accion: { texto: 'Reintentar', clase: 'button-secondary', alHacer: cargar }
                });
            }
        }
    }

    function mostrarCargando() {
        const rejilla = crear('div', 'prod-grid prod-grid-cargando');
        rejilla.setAttribute('aria-hidden', 'true');
        for (let i = 0; i < 6; i++) {
            const tarjeta = crear('div', 'prod-card prod-card-esqueleto');
            const foto = crear('span', 'prod-card-foto admin-skeleton-linea');
            const cuerpo = crear('span', 'prod-card-cuerpo admin-skeleton');
            cuerpo.append(crear('span', 'admin-skeleton-linea'), crear('span', 'admin-skeleton-linea'));
            tarjeta.append(foto, cuerpo);
            rejilla.append(tarjeta);
        }
        contenido.setAttribute('aria-busy', 'true');
        contenido.replaceChildren(rejilla, crear('span', 'visually-hidden', 'Cargando productos…'));
    }

    function contarCategoria(clave) {
        if (!clave) return estado.productos.length;
        if (clave === SIN_CATEGORIA) return estado.productos.filter(p => !categoriaDe(p)).length;
        return estado.productos.filter(p => categoriaDe(p)?.codigo === clave).length;
    }

    function pintarChips() {
        const opciones = [{ clave: '', icono: '', nombre: 'Todas' }];
        estado.categorias.forEach(c => opciones.push({ clave: c.codigo, icono: c.icono, nombre: c.nombre }));
        if (estado.productos.some(p => !categoriaDe(p))) opciones.push({ clave: SIN_CATEGORIA, icono: '❔', nombre: 'Sin categoría' });
        if (!opciones.some(o => o.clave === estado.filtroCat)) estado.filtroCat = '';
        chips.replaceChildren(...opciones.map(o => {
            const b = crear('button', 'prod-chip flat');
            b.type = 'button';
            b.dataset.cat = o.clave;
            b.setAttribute('aria-pressed', String(o.clave === estado.filtroCat));
            if (o.icono) {
                const ico = crear('span', 'prod-chip-icono', o.icono);
                ico.setAttribute('aria-hidden', 'true');
                b.append(ico);
            }
            b.append(crear('span', null, o.nombre), crear('span', 'prod-chip-cuenta', contarCategoria(o.clave)));
            return b;
        }));
    }

    function filtrados() {
        const q = normalizar(estado.filtroTexto);
        return estado.productos.filter(p => {
            const cat = categoriaDe(p);
            if (estado.filtroCat === SIN_CATEGORIA && cat) return false;
            if (estado.filtroCat && estado.filtroCat !== SIN_CATEGORIA && cat?.codigo !== estado.filtroCat) return false;
            if (estado.filtroFoto === 'con' && !p.imagenUrl) return false;
            if (estado.filtroFoto === 'sin' && p.imagenUrl) return false;
            if (q && !normalizar(p.nombre).includes(q) && !normalizar(p.descripcion).includes(q)) return false;
            return true;
        }).sort((a, b) => {
            const ca = categoriaDe(a), cb = categoriaDe(b);
            const oa = ca ? Number(ca.orden) || 0 : Number.MAX_SAFE_INTEGER;
            const ob = cb ? Number(cb.orden) || 0 : Number.MAX_SAFE_INTEGER;
            return oa - ob || String(a.nombre).localeCompare(String(b.nombre), 'es');
        });
    }

    function placeholder(icono) {
        const caja = crear('span', 'prod-card-vacia');
        const ico = crear('span', 'prod-card-vacia-icono', icono);
        ico.setAttribute('aria-hidden', 'true');
        caja.append(ico, crear('span', 'prod-card-vacia-texto', 'Sin foto'));
        return caja;
    }

    function tarjeta(p) {
        const li = crear('li');
        const b = crear('button', 'prod-card');
        b.type = 'button';
        b.dataset.id = String(p.id);
        const foto = crear('span', 'prod-card-foto');
        const url = urlFoto(p);
        if (url) {
            const img = crear('img');
            img.alt = '';
            img.loading = 'lazy';
            img.decoding = 'async';
            img.width = 400;
            img.height = 300;
            img.addEventListener('error', () => img.replaceWith(placeholder(iconoDe(p))), { once: true });
            img.src = url;
            foto.append(img);
        } else {
            foto.append(placeholder(iconoDe(p)));
        }
        if (p.disponible) foto.append(crear('span', 'badge badge-ok prod-card-hoy', 'En el menú de hoy'));

        const cuerpo = crear('span', 'prod-card-cuerpo');
        cuerpo.append(crear('span', 'prod-card-nombre', p.nombre));
        const cat = categoriaDe(p);
        cuerpo.append(crear('span', 'prod-card-cat', cat ? `${cat.icono || ''} ${cat.nombre}`.trim() : 'Sin categoría'));
        const pie = crear('span', 'prod-card-pie');
        pie.append(crear('span', 'prod-card-precio', precioTexto(p)));
        const tam = Array.isArray(p.presentaciones) ? p.presentaciones.length : 0;
        if (tam) pie.append(crear('span', 'prod-card-tam', plural(tam, 'tamaño', 'tamaños')));
        cuerpo.append(pie);
        b.append(foto, cuerpo);
        b.title = `Editar ${p.nombre}`;
        li.append(b);
        return li;
    }

    function pintarLista() {
        const lista = filtrados();
        const total = estado.productos.length;
        if (!total) {
            resultado.textContent = '';
            const sinCategorias = !estado.categorias.length;
            window.Admin.vacio(contenido, {
                icono: '🍽️',
                titulo: 'Todavía no tienes productos',
                texto: sinCategorias
                    ? 'Primero crea al menos una categoría (por ejemplo «Platos» o «Bebidas»). Después podrás agregar lo que vendes.'
                    : 'Agrega lo primero que vendes: un plato, una bebida o lo que ofrezca tu negocio.',
                accion: sinCategorias
                    ? { texto: 'Crear una categoría', href: '/admin/categorias' }
                    : { texto: 'Agregar producto', alHacer: () => abrirEditor(null) }
            });
            return;
        }
        resultado.textContent = lista.length === total
            ? plural(total, 'producto', 'productos')
            : `Mostrando ${lista.length} de ${total}`;
        if (!lista.length) {
            window.Admin.vacio(contenido, {
                icono: '🔍',
                titulo: 'Ningún producto coincide',
                texto: 'Prueba con otra palabra o quita los filtros.',
                accion: { texto: 'Quitar filtros', clase: 'button-secondary', alHacer: quitarFiltros }
            });
            return;
        }
        const ul = crear('ul', 'prod-grid');
        lista.forEach(p => ul.append(tarjeta(p)));
        contenido.replaceChildren(ul);
        window.Admin.listo(contenido);
    }

    function quitarFiltros() {
        estado.filtroCat = '';
        estado.filtroTexto = '';
        estado.filtroFoto = '';
        document.getElementById('prod-buscar').value = '';
        document.getElementById('prod-filtro-foto').value = '';
        pintarChips();
        pintarLista();
    }

    function porId(id) {
        return estado.productos.find(p => String(p.id) === String(id));
    }

    function abrirDialogo(contenidoDialogo, alCerrar) {
        dialogo.replaceChildren(contenidoDialogo);
        estado.cerrarDialogo = alCerrar;
        if (!dialogo.open) dialogo.showModal();
        const primero = dialogo.querySelector('input[name="nombre"]');
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

    function decodificar(archivo) {
        const conImagen = () => new Promise((resolve, reject) => {
            const url = URL.createObjectURL(archivo);
            const img = new Image();
            img.addEventListener('load', () => { URL.revokeObjectURL(url); resolve(img); }, { once: true });
            img.addEventListener('error', () => { URL.revokeObjectURL(url); reject(new Error('No pudimos abrir esta foto. Prueba con otra en formato JPG o PNG.')); }, { once: true });
            img.src = url;
        });
        if (typeof window.createImageBitmap === 'function') {
            return window.createImageBitmap(archivo, { imageOrientation: 'from-image' }).catch(conImagen);
        }
        return conImagen();
    }

    function aBlob(canvas, tipo, calidad) {
        return new Promise(resolve => {
            try { canvas.toBlob(blob => resolve(blob), tipo, calidad); } catch (e) { resolve(null); }
        });
    }

    function puedeWebp() {
        if (soportaWebp === null) {
            try {
                const prueba = document.createElement('canvas');
                prueba.width = 1;
                prueba.height = 1;
                soportaWebp = prueba.toDataURL('image/webp').startsWith('data:image/webp');
            } catch (e) {
                soportaWebp = false;
            }
        }
        return soportaWebp;
    }

    async function procesarImagen(archivo, recortar) {
        const fuente = await decodificar(archivo);
        const ancho = fuente.naturalWidth || fuente.width;
        const alto = fuente.naturalHeight || fuente.height;
        if (!ancho || !alto) throw new Error('No pudimos leer esta foto. Prueba con otra.');
        let sx = 0, sy = 0, sw = ancho, sh = alto;
        if (recortar) {
            if (sw / sh > PROPORCION) {
                sw = Math.round(sh * PROPORCION);
                sx = Math.round((ancho - sw) / 2);
            } else {
                sh = Math.round(sw / PROPORCION);
                sy = Math.round((alto - sh) / 2);
            }
        }
        const fondo = getComputedStyle(document.documentElement).getPropertyValue('--ink-light').trim();
        let lado = LADO_MAX;
        let calidad = 0.82;
        let salida = null;
        for (let intento = 0; intento < 14; intento++) {
            const escala = Math.min(1, lado / Math.max(sw, sh));
            const w = Math.max(1, Math.round(sw * escala));
            const h = Math.max(1, Math.round(sh * escala));
            const canvas = document.createElement('canvas');
            canvas.width = w;
            canvas.height = h;
            const ctx = canvas.getContext('2d');
            if (!ctx) throw new Error('Tu navegador no pudo preparar la foto.');
            ctx.imageSmoothingEnabled = true;
            ctx.imageSmoothingQuality = 'high';
            let blob = null;
            if (puedeWebp()) {
                ctx.drawImage(fuente, sx, sy, sw, sh, 0, 0, w, h);
                blob = await aBlob(canvas, 'image/webp', calidad);
                if (blob && blob.type !== 'image/webp') blob = null;
            }
            if (!blob) {
                if (fondo) {
                    ctx.fillStyle = fondo;
                    ctx.fillRect(0, 0, w, h);
                }
                ctx.drawImage(fuente, sx, sy, sw, sh, 0, 0, w, h);
                blob = await aBlob(canvas, 'image/jpeg', calidad);
            }
            if (!blob) throw new Error('Tu navegador no pudo preparar la foto.');
            salida = { blob, ancho: w, alto: h };
            if (blob.size <= MAX_BYTES) break;
            if (calidad > 0.55) calidad = Math.round((calidad - 0.1) * 100) / 100;
            else lado = Math.round(lado * 0.8);
        }
        if (typeof fuente.close === 'function') fuente.close();
        if (!salida || salida.blob.size > MAX_BYTES) throw new Error('La foto sigue siendo muy pesada. Prueba con otra.');
        return salida;
    }

    function opcionesModalidad(select, actual) {
        select.replaceChildren();
        const claves = CON_PUNTOS ? ['AMBAS', 'MESA', 'LLEVAR'] : ['LLEVAR'];
        if (actual && !claves.includes(actual) && MODALIDADES[actual]) claves.push(actual);
        claves.forEach(clave => {
            const op = crear('option', null, `${MODALIDADES[clave].icono} ${MODALIDADES[clave].texto}`);
            op.value = clave;
            select.append(op);
        });
        select.value = actual && claves.includes(actual) ? actual : claves[0];
    }

    function llenarCategorias(form, actual) {
        const select = form.elements.categoria;
        const ayuda = form.querySelector('[data-ayuda-categoria]');
        select.replaceChildren();
        if (!estado.categorias.length && !actual) {
            const op = crear('option', null, 'No hay categorías todavía');
            op.value = '';
            select.append(op);
            select.disabled = true;
            ayuda.replaceChildren(document.createTextNode('Necesitas al menos una categoría. '));
            const enlace = crear('a', 'prod-enlace', 'Crear una categoría');
            enlace.href = '/admin/categorias';
            ayuda.append(enlace);
            return;
        }
        estado.categorias.forEach(c => {
            const op = crear('option', null, `${c.icono || ''} ${c.nombre}${c.activa === false ? ' (oculta)' : ''}`.trim());
            op.value = c.codigo;
            select.append(op);
        });
        if (actual && !estado.mapaCategorias.has(String(actual)) && !estado.mapaCategorias.has(String(actual).toUpperCase())) {
            const op = crear('option', null, `${actual} (sin categoría)`);
            op.value = actual;
            select.append(op);
        }
        const encontrada = actual ? (estado.mapaCategorias.get(String(actual)) || estado.mapaCategorias.get(String(actual).toUpperCase())) : null;
        if (encontrada) select.value = encontrada.codigo;
        else if (actual) select.value = actual;
        else select.value = (estado.categorias.find(c => c.activa !== false) || estado.categorias[0]).codigo;
    }

    function abrirEditor(producto, opciones = {}) {
        const form = document.getElementById('prod-tpl-form').content.cloneNode(true).querySelector('form');
        const nuevo = !producto;
        const clave = nuevo ? CLAVE_NUEVO : `producto-${producto.id}`;
        const c = form.elements;
        const cuerpo = form.querySelector('[data-cuerpo]');
        let cambiosTamanos = false;
        let restaurando = false;
        let porcionesTocadas = !nuevo;

        form.querySelector('h2').textContent = nuevo ? 'Agregar producto' : `Editar «${producto.nombre}»`;
        if (!nuevo) form.querySelector('[data-intro]').textContent = 'Cambia lo que necesites. Los precios nuevos se aplican a los pedidos que se tomen desde ahora.';
        form.querySelector('[data-guardar]').textContent = nuevo ? 'Crear producto' : 'Guardar cambios';
        form.querySelectorAll('[data-solo-nuevo]').forEach(el => { if (nuevo) el.hidden = false; else el.remove(); });
        form.querySelectorAll('[data-solo-edicion]').forEach(el => { if (nuevo) el.remove(); else el.hidden = false; });
        if (MONEDA) {
            form.querySelector('[data-etiqueta-precio]').textContent = `Precio (${MONEDA})`;
            form.querySelector('[data-tam-etiqueta-precio]').textContent = `Precio (${MONEDA})`;
        }

        llenarCategorias(form, nuevo ? (opciones.categoria || null) : producto.categoria);
        c.nombre.value = nuevo ? '' : (producto.nombre || '');
        c.descripcion.value = nuevo ? '' : (producto.descripcion || '');
        c.precio.value = nuevo ? '' : (producto.precio ?? '');
        const sugerida = () => {
            const cat = estado.mapaCategorias.get(String(c.categoria.value));
            return cat && cat.cantidadSugerida !== null && cat.cantidadSugerida !== undefined ? String(cat.cantidadSugerida) : '';
        };
        c.porciones.value = nuevo ? sugerida() : (producto.stockDisponible ?? '');

        const contador = form.querySelector('[data-contador]');
        const fotoIcono = form.querySelector('[data-foto-icono]');
        const actualizarTextos = () => {
            contador.textContent = `${c.descripcion.value.length}/200`;
            const cat = estado.mapaCategorias.get(String(c.categoria.value));
            fotoIcono.textContent = cat?.icono || '🍽️';
        };

        const foto = { actualUrl: nuevo ? null : urlFoto(producto), original: null, nueva: null, nuevaUrl: null, quitar: false, procesando: false };
        const fotoImg = form.querySelector('[data-foto-img]');
        const fotoVacia = form.querySelector('[data-foto-vacia]');
        const fotoProcesando = form.querySelector('[data-foto-procesando]');
        const fotoEstado = form.querySelector('[data-foto-estado]');
        const btnQuitar = form.querySelector('[data-foto-quitar]');
        const btnDeshacer = form.querySelector('[data-foto-deshacer]');
        const btnCamara = form.querySelector('[data-foto-camara]');
        const btnGaleria = form.querySelector('[data-foto-galeria]');
        const inputCamara = form.querySelector('[data-foto-input-camara]');
        const inputGaleria = form.querySelector('[data-foto-input-galeria]');

        const pintarFoto = () => {
            const src = foto.nuevaUrl || (!foto.quitar ? foto.actualUrl : null);
            if (src) {
                if (fotoImg.getAttribute('src') !== src) fotoImg.src = src;
                fotoImg.hidden = false;
                fotoVacia.hidden = true;
            } else {
                fotoImg.removeAttribute('src');
                fotoImg.hidden = true;
                fotoVacia.hidden = false;
            }
            fotoProcesando.hidden = !foto.procesando;
            btnQuitar.hidden = !(foto.nueva || (foto.actualUrl && !foto.quitar));
            btnDeshacer.hidden = !foto.quitar;
            btnCamara.disabled = foto.procesando;
            btnGaleria.disabled = foto.procesando;
            const tieneAlgo = !!src;
            btnGaleria.lastChild.textContent = tieneAlgo ? ' Cambiar foto' : ' Elegir foto';
        };
        fotoImg.addEventListener('error', () => {
            if (!foto.nuevaUrl) {
                foto.actualUrl = null;
                pintarFoto();
            }
        });

        const ponerNueva = blob => {
            if (foto.nuevaUrl) URL.revokeObjectURL(foto.nuevaUrl);
            foto.nueva = blob;
            foto.nuevaUrl = blob ? URL.createObjectURL(blob) : null;
            if (blob) foto.quitar = false;
        };

        const procesarActual = async () => {
            if (!foto.original) return;
            mostrarError(form, 'foto', '');
            foto.procesando = true;
            fotoEstado.textContent = '';
            pintarFoto();
            try {
                const listo = await procesarImagen(foto.original, c.recortar.checked);
                if (!form.isConnected) return;
                ponerNueva(listo.blob);
                fotoEstado.textContent = `Foto lista (${listo.ancho} × ${listo.alto} px, ${kb(listo.blob.size)}). Se subirá al guardar.`;
            } catch (error) {
                mostrarError(form, 'foto', error.message || 'No pudimos preparar la foto.');
            } finally {
                foto.procesando = false;
                pintarFoto();
            }
        };

        const alElegir = evento => {
            const archivo = evento.target.files && evento.target.files[0];
            evento.target.value = '';
            if (!archivo) return;
            if (archivo.type && !archivo.type.startsWith('image/')) {
                mostrarError(form, 'foto', 'Ese archivo no es una foto. Elige una imagen.');
                return;
            }
            foto.original = archivo;
            procesarActual();
        };
        inputCamara.addEventListener('change', alElegir);
        inputGaleria.addEventListener('change', alElegir);
        btnCamara.addEventListener('click', () => inputCamara.click());
        btnGaleria.addEventListener('click', () => inputGaleria.click());
        btnQuitar.addEventListener('click', () => {
            mostrarError(form, 'foto', '');
            if (foto.nueva) {
                ponerNueva(null);
                foto.original = null;
                fotoEstado.textContent = foto.actualUrl ? 'Volviste a la foto que ya tenía.' : '';
            } else if (foto.actualUrl) {
                foto.quitar = true;
                fotoEstado.textContent = 'La foto se quitará al guardar.';
            }
            pintarFoto();
        });
        btnDeshacer.addEventListener('click', () => {
            foto.quitar = false;
            fotoEstado.textContent = '';
            pintarFoto();
        });
        c.recortar.addEventListener('change', () => { if (foto.original) procesarActual(); });

        const tam = { lista: [], editando: null };
        const tamLista = form.querySelector('[data-tam-lista]');
        const tamForm = form.querySelector('[data-tam-form]');
        const tamMostrar = form.querySelector('[data-tam-mostrar]');
        const tamCampos = tamForm.querySelector('.prod-tam-campos');
        opcionesModalidad(tamForm.querySelector('[data-tam="modalidad"]'), null);
        if (!CON_PUNTOS) tamForm.querySelector('[data-tam-modalidad-campo]').hidden = true;

        const ayudaPrecio = form.querySelector('[data-ayuda-precio]');
        const actualizarAyudaPrecio = () => {
            ayudaPrecio.textContent = tam.lista.length
                ? 'Como tiene tamaños, se cobra el precio de cada tamaño. Este precio solo se usa si no se elige un tamaño.'
                : 'Lo que cobras por una unidad.';
        };

        const leerTam = caja => {
            const campo = nombre => caja.querySelector(`[data-tam="${nombre}"]`);
            const nombre = campo('nombre').value.trim();
            const precioTexto = campo('precio').value.trim();
            const stockTexto = campo('stock').value.trim();
            const precio = Number(precioTexto);
            const stock = stockTexto === '' ? null : Number(stockTexto);
            if (!nombre) return { error: 'Escribe el nombre del tamaño.', foco: campo('nombre') };
            if (precioTexto === '' || !Number.isFinite(precio) || precio < 0) return { error: 'Escribe un precio válido para el tamaño.', foco: campo('precio') };
            if (stock !== null && (!Number.isInteger(stock) || stock < 0)) return { error: 'La cantidad debe ser un número entero, o déjala vacía.', foco: campo('stock') };
            return { datos: { nombre, precio, stockDisponible: stock, modalidad: campo('modalidad').value || 'LLEVAR' } };
        };

        const resumenTam = d => [
            { etiqueta: 'Tamaño', valor: d.nombre },
            { etiqueta: 'Precio', valor: dinero(d.precio) },
            CON_PUNTOS ? { etiqueta: 'Se vende', valor: (MODALIDADES[d.modalidad] || MODALIDADES.AMBAS).texto } : null,
            { etiqueta: 'Cantidad', valor: d.stockDisponible === null ? 'Sin límite' : String(d.stockDisponible) }
        ];

        const pintarTam = () => {
            tamLista.replaceChildren();
            if (!tam.lista.length) {
                tamLista.append(crear('p', 'prod-tam-vacio', 'Sin tamaños: se cobra el precio de arriba.'));
                actualizarAyudaPrecio();
                return;
            }
            const ul = crear('ul', 'prod-tam-items');
            tam.lista.forEach((t, idx) => {
                const li = crear('li', 'prod-tam-item');
                if (tam.editando === idx) {
                    li.classList.add('prod-tam-item-editando');
                    const campos = tamCampos.cloneNode(true);
                    campos.querySelector('[data-tam="nombre"]').value = t.nombre || '';
                    campos.querySelector('[data-tam="precio"]').value = t.precio ?? '';
                    campos.querySelector('[data-tam="stock"]').value = t.stockDisponible ?? '';
                    opcionesModalidad(campos.querySelector('[data-tam="modalidad"]'), t.modalidad || (CON_PUNTOS ? 'AMBAS' : 'LLEVAR'));
                    const campoModalidad = campos.querySelector('[data-tam-modalidad-campo]');
                    campoModalidad.hidden = campos.querySelector('[data-tam="modalidad"]').options.length < 2;
                    const botones = crear('div', 'prod-tam-botones');
                    const cancelar = crear('button', 'button-secondary', 'Cancelar');
                    cancelar.type = 'button';
                    cancelar.dataset.tamAccion = 'cancelar';
                    const guardar = crear('button', 'button-primary', 'Guardar tamaño');
                    guardar.type = 'button';
                    guardar.dataset.tamAccion = 'guardar';
                    botones.append(cancelar, guardar);
                    li.append(campos, botones);
                } else {
                    const info = crear('div', 'prod-tam-info');
                    const linea = crear('div', 'prod-tam-linea');
                    linea.append(crear('strong', 'prod-tam-nombre', t.nombre), crear('span', 'prod-tam-precio', dinero(t.precio)));
                    const meta = crear('div', 'prod-tam-meta');
                    if (CON_PUNTOS || t.modalidad !== 'LLEVAR') {
                        const m = MODALIDADES[t.modalidad || 'AMBAS'] || MODALIDADES.AMBAS;
                        meta.append(crear('span', 'badge badge-info', `${m.icono} ${m.texto}`));
                    }
                    meta.append(crear('span', 'badge', t.stockDisponible === null || t.stockDisponible === undefined ? 'Sin límite' : `${t.stockDisponible} disponibles`));
                    if (t.disponible === false) meta.append(crear('span', 'badge badge-off', 'Inactivo'));
                    if (!t.id) meta.append(crear('span', 'badge badge-warn', 'Se guarda con el producto'));
                    info.append(linea, meta);
                    const botones = crear('div', 'prod-tam-acciones');
                    const editar = crear('button', 'button-secondary prod-btn-chico', 'Editar');
                    editar.type = 'button';
                    editar.dataset.tamAccion = 'editar';
                    editar.setAttribute('aria-label', `Editar tamaño ${t.nombre}`);
                    const borrar = crear('button', 'button-secondary prod-btn-chico prod-btn-peligro', 'Eliminar');
                    borrar.type = 'button';
                    borrar.dataset.tamAccion = 'eliminar';
                    borrar.setAttribute('aria-label', `Eliminar tamaño ${t.nombre}`);
                    botones.append(editar, borrar);
                    li.append(info, botones);
                }
                li.dataset.idx = String(idx);
                ul.append(li);
            });
            tamLista.append(ul);
            actualizarAyudaPrecio();
        };

        const mostrarFormTam = () => {
            tamForm.hidden = false;
            tamMostrar.hidden = true;
            tamForm.querySelector('[data-tam="nombre"]').focus();
        };
        const ocultarFormTam = () => {
            tamForm.hidden = true;
            tamMostrar.hidden = false;
            tamForm.querySelectorAll('input').forEach(i => { i.value = ''; });
            opcionesModalidad(tamForm.querySelector('[data-tam="modalidad"]'), null);
        };

        const agregarTam = async () => {
            const r = leerTam(tamForm);
            if (r.error) { aviso(r.error, 'error'); r.foco.focus(); return; }
            if (!nuevo) {
                const ok = await confirmar('Se agregará este tamaño al producto.', {
                    titulo: '¿Agregar tamaño?', resumen: resumenTam(r.datos), aceptar: 'Agregar'
                });
                if (!ok) return;
                try {
                    const guardado = await pedir(`/api/platos/${encodeURIComponent(producto.id)}/presentaciones`, {
                        method: 'POST', body: { ...r.datos, disponible: true }
                    });
                    tam.lista.push(guardado || { ...r.datos, disponible: true });
                    cambiosTamanos = true;
                    aviso('Tamaño agregado.', 'ok');
                } catch (error) {
                    aviso(`No se pudo agregar el tamaño: ${error.message}`, 'error');
                    return;
                }
            } else {
                tam.lista.push({ ...r.datos, disponible: true });
            }
            ocultarFormTam();
            pintarTam();
            tamMostrar.focus();
        };

        const guardarTam = async idx => {
            const li = tamLista.querySelector(`[data-idx="${idx}"]`);
            const r = leerTam(li);
            if (r.error) { aviso(r.error, 'error'); r.foco.focus(); return; }
            const t = tam.lista[idx];
            if (t.id) {
                const ok = await confirmar('Se actualizarán los datos de este tamaño.', {
                    titulo: '¿Guardar cambios del tamaño?', resumen: resumenTam(r.datos), aceptar: 'Guardar'
                });
                if (!ok) return;
                try {
                    await pedir(`/api/presentaciones/${encodeURIComponent(t.id)}`, { method: 'PUT', body: r.datos });
                    cambiosTamanos = true;
                    aviso('Tamaño actualizado.', 'ok');
                } catch (error) {
                    aviso(`No se pudo guardar el tamaño: ${error.message}`, 'error');
                    return;
                }
            }
            Object.assign(t, r.datos);
            tam.editando = null;
            pintarTam();
        };

        const eliminarTam = async idx => {
            const t = tam.lista[idx];
            const ok = await confirmar(t.id ? 'Este tamaño dejará de existir. No se puede deshacer.' : 'Se quitará de la lista de tamaños del producto nuevo.', {
                titulo: '¿Eliminar tamaño?',
                resumen: [{ etiqueta: 'Tamaño', valor: t.nombre }, { etiqueta: 'Precio', valor: dinero(t.precio) }],
                aceptar: 'Eliminar', cancelar: 'Cancelar', peligro: true
            });
            if (!ok) return;
            if (t.id) {
                try {
                    await pedir(`/api/presentaciones/${encodeURIComponent(t.id)}`, { method: 'DELETE' });
                    cambiosTamanos = true;
                    aviso('Tamaño eliminado.', 'ok');
                } catch (error) {
                    aviso(`No se pudo eliminar el tamaño: ${error.message}`, 'error');
                    return;
                }
            }
            tam.lista.splice(idx, 1);
            if (tam.editando === idx) tam.editando = null;
            pintarTam();
            tamMostrar.focus();
        };

        tamMostrar.addEventListener('click', mostrarFormTam);
        tamForm.querySelector('[data-tam-cancelar]').addEventListener('click', () => { ocultarFormTam(); tamMostrar.focus(); });
        tamForm.querySelector('[data-tam-agregar]').addEventListener('click', agregarTam);
        tamLista.addEventListener('click', evento => {
            const b = evento.target.closest('[data-tam-accion]');
            if (!b) return;
            const idx = Number(b.closest('[data-idx]').dataset.idx);
            const accion = b.dataset.tamAccion;
            if (accion === 'editar') {
                tam.editando = idx;
                pintarTam();
                tamLista.querySelector(`[data-idx="${idx}"] [data-tam="nombre"]`)?.focus();
            } else if (accion === 'cancelar') {
                tam.editando = null;
                pintarTam();
            } else if (accion === 'guardar') {
                guardarTam(idx);
            } else if (accion === 'eliminar') {
                eliminarTam(idx);
            }
        });

        if (!nuevo) {
            tam.lista = Array.isArray(producto.presentaciones) ? producto.presentaciones.map(t => ({ ...t })) : [];
            pedir(`/api/platos/${encodeURIComponent(producto.id)}/presentaciones`).then(lista => {
                if (!Array.isArray(lista) || tam.editando !== null || !form.isConnected) return;
                tam.lista = lista;
                pintarTam();
            }).catch(() => {});
        }
        pintarTam();

        form.addEventListener('input', evento => {
            const nombre = evento.target.name;
            if (nombre) mostrarError(form, nombre, '');
            mostrarError(form, 'general', '');
            if (nombre === 'porciones' && evento.isTrusted) porcionesTocadas = true;
            actualizarTextos();
        });
        form.addEventListener('change', evento => {
            if (evento.target.name === 'categoria') {
                mostrarError(form, 'categoria', '');
                if (nuevo && !porcionesTocadas && !restaurando) c.porciones.value = sugerida();
            }
            actualizarTextos();
        });

        restaurando = true;
        const borrador = vincularBorrador(form, clave, {
            excluir: ['archivoCamara', 'archivoGaleria', 'recortar'],
            contenedorAviso: cuerpo,
            textoAviso: 'Recuperamos lo que estabas escribiendo. Si habías elegido una foto, vuelve a elegirla.'
        });
        restaurando = false;
        if (borrador.restaurado) porcionesTocadas = true;
        actualizarTextos();
        pintarFoto();

        const tienePendientes = () => borrador.sucio() || !!foto.nueva || foto.quitar || (nuevo && tam.lista.length > 0);

        const terminar = () => {
            if (foto.nuevaUrl) URL.revokeObjectURL(foto.nuevaUrl);
            if (borrador.desvincular) borrador.desvincular();
            cerrarDialogo();
            if (cambiosTamanos) cargar();
        };

        const salir = async () => {
            if (tienePendientes()) {
                const ok = await confirmar('Lo que cambiaste en este producto no se ha guardado.', {
                    titulo: '¿Cerrar sin guardar?', aceptar: 'Cerrar y descartar', cancelar: 'Seguir editando', peligro: true
                });
                if (!ok) return;
            }
            borrador.limpiar();
            terminar();
        };

        form.addEventListener('click', evento => {
            if (evento.target.closest('[data-cerrar]')) salir();
        });

        const eliminarBtn = form.querySelector('[data-eliminar]');
        if (eliminarBtn) {
            eliminarBtn.addEventListener('click', async () => {
                const cat = categoriaDe(producto);
                const ok = await confirmar('Si ya se vendió alguna vez, quedará archivado para conservar tu historial de ventas. Si nunca se vendió, se borrará. En ambos casos dejará de aparecer en el menú.', {
                    titulo: `¿Eliminar «${producto.nombre}»?`,
                    resumen: [
                        { etiqueta: 'Producto', valor: producto.nombre },
                        { etiqueta: 'Categoría', valor: cat ? `${cat.icono || ''} ${cat.nombre}`.trim() : null },
                        { etiqueta: 'Precio', valor: precioTexto(producto) }
                    ],
                    aceptar: 'Eliminar', cancelar: 'No eliminar', peligro: true
                });
                if (!ok) return;
                eliminarBtn.disabled = true;
                try {
                    await pedir(`/api/platos/${encodeURIComponent(producto.id)}`, { method: 'DELETE' });
                    borrador.limpiar();
                    borrarBorrador(clave);
                    cambiosTamanos = false;
                    terminar();
                    aviso('Producto eliminado. Si tenía ventas, quedó archivado.', 'ok');
                    await cargar();
                } catch (error) {
                    mostrarError(form, 'general', error.message || 'No se pudo eliminar.');
                    aviso(error.message || 'No se pudo eliminar.', 'error');
                    eliminarBtn.disabled = false;
                }
            });
        }

        form.addEventListener('submit', async evento => {
            evento.preventDefault();
            const activo = document.activeElement;
            if (activo && tamForm.contains(activo)) { agregarTam(); return; }
            const editandoItem = activo && activo.closest ? activo.closest('.prod-tam-item-editando') : null;
            if (editandoItem) { guardarTam(Number(editandoItem.dataset.idx)); return; }
            if (foto.procesando) { aviso('Espera a que la foto termine de prepararse.', 'error'); return; }

            const nombre = c.nombre.value.trim().replace(/\s+/g, ' ');
            const descripcion = c.descripcion.value.trim();
            const categoria = c.categoria.value;
            const precioTexto = c.precio.value.trim();
            const porcionesTexto = c.porciones.value.trim();
            let precio = Number(precioTexto);
            const porciones = porcionesTexto === '' ? null : Number(porcionesTexto);
            let valido = true;

            if (!nombre || nombre.length > 80) {
                mostrarError(form, 'nombre', 'Escribe el nombre del producto (máximo 80 letras).');
                valido = false;
            }
            if (!categoria) {
                mostrarError(form, 'categoria', 'Elige una categoría. Si no tienes ninguna, créala primero en «Categorías».');
                valido = false;
            }
            if (precioTexto === '' && tam.lista.length) {
                precio = Math.min(...tam.lista.map(t => Number(t.precio) || 0));
            } else if (precioTexto === '' || !Number.isFinite(precio) || precio < 0) {
                mostrarError(form, 'precio', 'Escribe el precio (un número, por ejemplo 15 o 12.50).');
                valido = false;
            }
            if (porciones !== null && (!Number.isInteger(porciones) || porciones < 0)) {
                mostrarError(form, 'porciones', 'Escribe un número entero, o déjalo vacío.');
                valido = false;
            }
            if (!valido) {
                form.querySelector('[aria-invalid="true"]')?.focus();
                return;
            }

            const cat = estado.mapaCategorias.get(String(categoria));
            const venderHoy = nuevo ? c.venderHoy.checked : null;
            const nuevosTam = nuevo ? tam.lista.length : 0;
            let textoFoto;
            if (foto.nueva) textoFoto = foto.actualUrl ? 'Se cambia' : 'Se agrega';
            else if (foto.quitar) textoFoto = 'Se quita';
            else textoFoto = foto.actualUrl ? 'Sin cambios' : 'Sin foto';
            let textoPorciones;
            if (porciones !== null) textoPorciones = String(porciones);
            else textoPorciones = nuevo ? 'Sin límite' : 'Sin cambios';

            const ok = await confirmar(nuevo
                ? (venderHoy ? 'Se guardará en tu catálogo y quedará a la venta hoy.' : 'Se guardará en tu catálogo. Podrás ponerlo a la venta desde «Menú de hoy».')
                : 'Se guardarán los cambios de este producto.', {
                titulo: nuevo ? '¿Crear este producto?' : '¿Guardar los cambios?',
                resumen: [
                    { etiqueta: 'Nombre', valor: nombre },
                    { etiqueta: 'Categoría', valor: cat ? `${cat.icono || ''} ${cat.nombre}`.trim() : categoria },
                    { etiqueta: 'Precio', valor: dinero(precio) },
                    { etiqueta: 'Porciones', valor: textoPorciones },
                    { etiqueta: 'Descripción', valor: descripcion ? (descripcion.length > 60 ? `${descripcion.slice(0, 57)}…` : descripcion) : 'Sin descripción' },
                    { etiqueta: 'Foto', valor: textoFoto },
                    nuevosTam ? { etiqueta: 'Tamaños', valor: String(nuevosTam) } : null,
                    nuevo ? { etiqueta: 'A la venta hoy', valor: venderHoy ? 'Sí' : 'No' } : null
                ],
                aceptar: nuevo ? 'Crear' : 'Guardar'
            });
            if (!ok) return;

            const guardar = form.querySelector('[data-guardar]');
            const textoBoton = guardar.textContent;
            guardar.disabled = true;
            guardar.textContent = 'Guardando…';
            const datos = { nombre, descripcion: descripcion || null, precio, categoria, stockDisponible: porciones };
            if (nuevo) datos.disponible = venderHoy;
            let guardado;
            try {
                guardado = nuevo
                    ? await pedir('/api/platos', { method: 'POST', body: datos })
                    : await pedir(`/api/platos/${encodeURIComponent(producto.id)}`, { method: 'PUT', body: datos });
            } catch (error) {
                mostrarError(form, 'general', error.message || 'No se pudo guardar.');
                aviso(error.message || 'No se pudo guardar.', 'error');
                guardar.disabled = false;
                guardar.textContent = textoBoton;
                return;
            }

            const id = (guardado && guardado.id) || (producto && producto.id);
            const fallos = [];
            if (nuevo && id) {
                for (const t of tam.lista) {
                    try {
                        await pedir(`/api/platos/${encodeURIComponent(id)}/presentaciones`, {
                            method: 'POST',
                            body: { nombre: t.nombre, precio: t.precio, stockDisponible: t.stockDisponible, modalidad: t.modalidad, disponible: true }
                        });
                    } catch (error) {
                        fallos.push(`el tamaño «${t.nombre}»`);
                    }
                }
            }
            if (id && foto.nueva) {
                try {
                    const datosFoto = new FormData();
                    datosFoto.append('archivo', foto.nueva, foto.nueva.type === 'image/webp' ? 'foto.webp' : 'foto.jpg');
                    await pedir(`/api/admin/platos/${encodeURIComponent(id)}/imagen`, { method: 'PUT', formData: datosFoto });
                    estado.versiones.set(id, Date.now());
                } catch (error) {
                    fallos.push(`la foto (${error.message || 'error'})`);
                }
            } else if (id && foto.quitar) {
                try {
                    await pedir(`/api/admin/platos/${encodeURIComponent(id)}/imagen`, { method: 'DELETE' });
                } catch (error) {
                    fallos.push(`el cambio de foto (${error.message || 'error'})`);
                }
            }

            borrador.limpiar();
            borrarBorrador(clave);
            cambiosTamanos = false;
            terminar();
            await cargar();
            if (fallos.length) {
                aviso(`El producto se guardó, pero no se pudo guardar ${fallos.join(' ni ')}. Ábrelo y vuelve a intentarlo.`, 'error');
            } else {
                aviso(nuevo ? 'Producto creado.' : 'Cambios guardados.', 'ok');
            }
        });

        abrirDialogo(form, salir);
    }

    function limpiarUrl() {
        try { window.history.replaceState(null, '', window.location.pathname); } catch (e) { }
    }

    function abrirSegunEnlace() {
        const params = new URLSearchParams(window.location.search);
        const categoria = params.get('categoria');
        const editar = params.get('editar');
        const nuevo = params.get('nuevo');
        if (params.toString()) limpiarUrl();
        if (editar) {
            const p = porId(editar);
            if (p) { abrirEditor(p); return; }
            aviso('No encontramos ese producto. Puede que se haya eliminado.', 'error');
        } else if (nuevo === '1' || nuevo === 'true') {
            abrirEditor(null, { categoria });
            return;
        } else if (categoria) {
            const cat = estado.mapaCategorias.get(categoria) || estado.mapaCategorias.get(categoria.toUpperCase());
            if (cat) {
                estado.filtroCat = cat.codigo;
                pintarChips();
                pintarLista();
            }
        }
        if (tieneBorrador(CLAVE_NUEVO)) {
            abrirEditor(null);
            return;
        }
        const conBorrador = estado.productos.find(p => tieneBorrador(`producto-${p.id}`));
        if (conBorrador) abrirEditor(conBorrador);
    }

    function iniciar() {
        main = document.getElementById('contenido');
        dialogo = document.getElementById('prod-dialogo');
        contenido = document.getElementById('prod-contenido');
        resultado = document.getElementById('prod-resultado');
        chips = document.getElementById('prod-chips');
        if (!main || !dialogo || !contenido) return;

        dialogo.addEventListener('cancel', evento => { evento.preventDefault(); intentarCerrar(); });
        dialogo.addEventListener('click', evento => {
            if (evento.target !== dialogo) return;
            const r = dialogo.getBoundingClientRect();
            const fuera = evento.clientX < r.left || evento.clientX > r.right || evento.clientY < r.top || evento.clientY > r.bottom;
            if (fuera) intentarCerrar();
        });

        document.getElementById('prod-agregar').addEventListener('click', () => {
            const cat = estado.filtroCat && estado.filtroCat !== SIN_CATEGORIA ? estado.filtroCat : null;
            abrirEditor(null, { categoria: cat });
        });

        let espera = null;
        document.getElementById('prod-buscar').addEventListener('input', evento => {
            clearTimeout(espera);
            const valor = evento.target.value;
            espera = setTimeout(() => {
                estado.filtroTexto = valor;
                if (estado.cargado) pintarLista();
            }, 120);
        });
        document.getElementById('prod-filtro-foto').addEventListener('change', evento => {
            estado.filtroFoto = evento.target.value;
            if (estado.cargado) pintarLista();
        });
        chips.addEventListener('click', evento => {
            const b = evento.target.closest('.prod-chip');
            if (!b) return;
            estado.filtroCat = b.dataset.cat;
            chips.querySelectorAll('.prod-chip').forEach(x => x.setAttribute('aria-pressed', String(x === b)));
            pintarLista();
            const barra = document.querySelector('.prod-toolbar');
            const tope = barra ? barra.getBoundingClientRect().bottom : 0;
            if (contenido.getBoundingClientRect().top < tope) {
                const reducido = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
                contenido.scrollIntoView({ behavior: reducido ? 'auto' : 'smooth', block: 'start' });
            }
        });
        contenido.addEventListener('click', evento => {
            const b = evento.target.closest('.prod-card[data-id]');
            if (!b) return;
            const p = porId(b.dataset.id);
            if (p) abrirEditor(p);
        });

        cargar();
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar);
    else iniciar();
})();
