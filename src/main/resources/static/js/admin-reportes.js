(function () {
    'use strict';
    const API = '/api/admin/reportes';
    const CLAVE = 'reportes-filtros';
    const INTERVALO = 60000;
    const MAX_DIAS = 366;
    const TOP = 10;
    const SVG = 'http://www.w3.org/2000/svg';
    const PERIODOS = ['hoy', 'ayer', 'esta-semana', 'semana-pasada', 'este-mes', 'mes-pasado', 'ultimos-7', 'ultimos-30'];
    const AGRUPACIONES = ['auto', 'dia', 'semana', 'mes'];
    const DEFECTO = 'esta-semana';
    const COMPARA = {
        hoy: 'vs ayer',
        ayer: 'vs día anterior',
        'esta-semana': 'vs semana anterior',
        'semana-pasada': 'vs semana anterior',
        'este-mes': 'vs mes anterior',
        'mes-pasado': 'vs mes anterior',
        'ultimos-7': 'vs 7 días anteriores',
        'ultimos-30': 'vs 30 días anteriores',
        personalizado: 'vs período anterior'
    };
    const BARRA_DESC = {
        dia: 'Cada barra es un día.',
        semana: 'Cada barra es una semana (de lunes a domingo).',
        mes: 'Cada barra es un mes.'
    };
    const METODOS = {
        EFECTIVO: ['💵', 'Efectivo'],
        QR: ['📱', 'QR'],
        TARJETA: ['💳', 'Tarjeta'],
        TRANSFERENCIA: ['🏦', 'Transferencia']
    };
    const KPIS = [
        { clave: 'ventas', etiqueta: 'Ventas cobradas', dinero: true, destacado: true, ayuda: 'Dinero de los pedidos cobrados en estas fechas, según el día del cobro.' },
        { clave: 'pedidosCobrados', etiqueta: 'Pedidos cobrados', ayuda: 'Pedidos que ya se pagaron.' },
        { clave: 'ticketPromedio', etiqueta: 'Ticket promedio', dinero: true, ayuda: 'Lo que deja, en promedio, cada pedido cobrado.' },
        { clave: 'unidades', etiqueta: 'Productos vendidos', ayuda: 'Unidades vendidas en pedidos cobrados.' },
        { clave: 'pedidosRegistrados', etiqueta: 'Pedidos registrados', ayuda: 'Todos los pedidos tomados, se hayan cobrado o no.' }
    ];

    const fmtEntero = new Intl.NumberFormat('es', { maximumFractionDigits: 0 });
    const fmtDecimal = new Intl.NumberFormat('es', { maximumFractionDigits: 1 });
    const fmtEje = new Intl.NumberFormat('es', { maximumFractionDigits: 2 });
    const fmtCompacto = new Intl.NumberFormat('es', { notation: 'compact', maximumFractionDigits: 1 });

    const $ = id => document.getElementById(id);
    const el = {
        chips: [...document.querySelectorAll('[data-periodo]')],
        chipPersonalizado: $('rep-chip-personalizado'),
        form: $('rep-personalizado'),
        desde: $('rep-desde'),
        hasta: $('rep-hasta'),
        errorRango: $('rep-error-rango'),
        rangoTexto: $('rep-rango-texto'),
        comparado: $('rep-comparado'),
        agrupar: $('rep-agrupar'),
        error: $('rep-error'),
        kpis: $('rep-kpis'),
        actualizado: $('rep-actualizado'),
        actualizar: $('rep-actualizar'),
        verTabla: $('rep-ver-tabla'),
        serieDesc: $('rep-serie-desc'),
        serie: $('rep-serie'),
        topBotones: [...document.querySelectorAll('[data-top]')],
        top: $('rep-top'),
        categorias: $('rep-categorias'),
        pagos: $('rep-pagos'),
        modalidad: $('rep-modalidad'),
        equipo: $('rep-equipo'),
        horasFrase: $('rep-horas-frase'),
        horas: $('rep-horas'),
        diasFrase: $('rep-dias-frase'),
        dias: $('rep-dias'),
        csv: { pedidos: $('rep-csv-pedidos'), productos: $('rep-csv-productos'), empleados: $('rep-csv-empleados') }
    };

    let estado = null;
    let panelAbierto = false;
    let modoTabla = false;
    let modoTop = 'unidades';
    let topExpandido = false;
    let datosActuales = null;
    let peticion = 0;
    let enCurso = false;
    let ultimaCarga = 0;
    const graficos = { serie: null, horas: null, dias: null };
    const registro = new Map();
    const observador = window.ResizeObserver ? new ResizeObserver(alCambiarTamano) : null;

    function crear(tag, clase, texto) {
        const nodo = document.createElement(tag);
        if (clase) nodo.className = clase;
        if (texto !== undefined && texto !== null) nodo.textContent = String(texto);
        return nodo;
    }

    function crearSvg(tag, atributos) {
        const nodo = document.createElementNS(SVG, tag);
        Object.entries(atributos || {}).forEach(([k, v]) => nodo.setAttribute(k, String(v)));
        return nodo;
    }

    function num(valor) {
        const n = Number(valor);
        return Number.isFinite(n) ? n : 0;
    }

    function lista(valor) {
        return Array.isArray(valor) ? valor.filter(Boolean) : [];
    }

    function capitalizar(texto) {
        return texto ? texto.charAt(0).toUpperCase() + texto.slice(1) : '';
    }

    function simbolo() {
        return (window.NEGOCIO && window.NEGOCIO.simboloMoneda) || '';
    }

    function dinero(valor) {
        return window.Admin && typeof window.Admin.dinero === "function"
            ? window.Admin.dinero(num(valor))
            : `${simbolo()} ${num(valor).toFixed(2)}`.trim();
    }

    function dineroCorto(valor) {
        const n = num(valor);
        const texto = Math.abs(n) >= 1000 ? fmtCompacto.format(n) : fmtEje.format(n);
        return `${simbolo()} ${texto}`.trim();
    }

    function plural(n, singular, varios) {
        return `${fmtEntero.format(n)} ${n === 1 ? singular : varios}`;
    }

    function porcentaje(parte, total) {
        return total > 0 ? `${fmtDecimal.format((parte / total) * 100)} %` : '0 %';
    }

    function aIso(d) {
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    }

    function deIso(texto) {
        const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(texto || '');
        if (!m) return null;
        const d = new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3]));
        return d.getMonth() === Number(m[2]) - 1 && d.getDate() === Number(m[3]) ? d : null;
    }

    function hoy() {
        const d = new Date();
        return new Date(d.getFullYear(), d.getMonth(), d.getDate());
    }

    function sumar(d, n) {
        return new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
    }

    function contarDias(a, b) {
        return Math.round((Date.UTC(b.getFullYear(), b.getMonth(), b.getDate()) - Date.UTC(a.getFullYear(), a.getMonth(), a.getDate())) / 86400000) + 1;
    }

    function rangoDe(periodo) {
        const h = hoy();
        const lunes = sumar(h, -((h.getDay() + 6) % 7));
        const primeroMes = new Date(h.getFullYear(), h.getMonth(), 1);
        const rangos = {
            hoy: [h, h],
            ayer: [sumar(h, -1), sumar(h, -1)],
            'esta-semana': [lunes, h],
            'semana-pasada': [sumar(lunes, -7), sumar(lunes, -1)],
            'este-mes': [primeroMes, h],
            'mes-pasado': [new Date(h.getFullYear(), h.getMonth() - 1, 1), sumar(primeroMes, -1)],
            'ultimos-7': [sumar(h, -6), h],
            'ultimos-30': [sumar(h, -29), h]
        };
        const [a, b] = rangos[periodo] || rangos[DEFECTO];
        return { desde: aIso(a), hasta: aIso(b) };
    }

    function validarRango(desde, hasta) {
        const a = deIso(desde);
        const b = deIso(hasta);
        if (!a || !b) return 'Elige las dos fechas: «Desde» y «Hasta».';
        if (a > b) return 'La fecha «Desde» debe ser anterior o igual a «Hasta».';
        const total = contarDias(a, b);
        if (total > MAX_DIAS) return `Elige un período de hasta ${MAX_DIAS} días (un año). Ahora elegiste ${fmtEntero.format(total)} días.`;
        return null;
    }

    function normalizar(datos) {
        const origen = datos || {};
        const agrupar = AGRUPACIONES.includes(origen.agrupar) ? origen.agrupar : 'auto';
        if (PERIODOS.includes(origen.periodo)) return { periodo: origen.periodo, agrupar, ...rangoDe(origen.periodo) };
        if (origen.periodo === 'personalizado' && !validarRango(origen.desde, origen.hasta)) {
            return { periodo: 'personalizado', agrupar, desde: origen.desde, hasta: origen.hasta };
        }
        return { periodo: DEFECTO, agrupar, ...rangoDe(DEFECTO) };
    }

    function leerUrl() {
        const p = new URLSearchParams(window.location.search);
        if (p.has('periodo')) return normalizar({ periodo: p.get('periodo'), agrupar: p.get('agrupar') });
        if (p.has('desde') && p.has('hasta')) return normalizar({ periodo: 'personalizado', desde: p.get('desde'), hasta: p.get('hasta'), agrupar: p.get('agrupar') });
        return null;
    }

    function escribirUrl(modo) {
        const p = new URLSearchParams();
        if (estado.periodo === 'personalizado') {
            p.set('desde', estado.desde);
            p.set('hasta', estado.hasta);
        } else {
            p.set('periodo', estado.periodo);
        }
        if (estado.agrupar !== 'auto') p.set('agrupar', estado.agrupar);
        const url = `${window.location.pathname}?${p.toString()}`;
        if (url === `${window.location.pathname}${window.location.search}`) return;
        if (modo === 'push') history.pushState(null, '', url);
        else history.replaceState(null, '', url);
    }

    function guardarFiltros() {
        if (window.Borradores) window.Borradores.guardar(CLAVE, { periodo: estado.periodo, desde: estado.desde, hasta: estado.hasta, agrupar: estado.agrupar });
    }

    function incluyeHoy() {
        const h = aIso(hoy());
        return !!estado && estado.desde <= h && h <= estado.hasta;
    }

    function textoRango(desdeTexto, hastaTexto) {
        const a = deIso(desdeTexto);
        const b = deIso(hastaTexto);
        if (!a || !b) return '';
        const anioActual = hoy().getFullYear();
        const conDia = contarDias(a, b) <= 14;
        const dia = d => (conDia ? `${d.toLocaleDateString('es', { weekday: 'long' })} ${d.getDate()}` : String(d.getDate()));
        const mes = d => d.toLocaleDateString('es', { month: 'long' });
        const anio = d => (d.getFullYear() !== anioActual ? ` de ${d.getFullYear()}` : '');
        if (aIso(a) === aIso(b)) return capitalizar(`${dia(a)} de ${mes(a)}${anio(a)}`);
        if (a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth()) return `Del ${dia(a)} al ${dia(b)} de ${mes(b)}${anio(b)}`;
        if (a.getFullYear() === b.getFullYear()) return `Del ${dia(a)} de ${mes(a)} al ${dia(b)} de ${mes(b)}${anio(b)}`;
        return `Del ${dia(a)} de ${mes(a)} de ${a.getFullYear()} al ${dia(b)} de ${mes(b)} de ${b.getFullYear()}`;
    }

    function minusculaInicial(texto) {
        return texto ? texto.charAt(0).toLowerCase() + texto.slice(1) : '';
    }

    function pintarFiltros() {
        el.chips.forEach(chip => chip.setAttribute('aria-pressed', String(chip.dataset.periodo === estado.periodo)));
        const abierto = panelAbierto || estado.periodo === 'personalizado';
        el.form.hidden = !abierto;
        el.chipPersonalizado.setAttribute('aria-expanded', String(abierto));
        el.desde.value = estado.desde;
        el.hasta.value = estado.hasta;
        el.errorRango.textContent = '';
        el.desde.removeAttribute('aria-invalid');
        el.hasta.removeAttribute('aria-invalid');
        el.agrupar.value = estado.agrupar;
        el.rangoTexto.textContent = textoRango(estado.desde, estado.hasta);
        el.comparado.textContent = '';
        Object.entries(el.csv).forEach(([tipo, enlace]) => {
            const p = new URLSearchParams({ desde: estado.desde, hasta: estado.hasta, tipo });
            enlace.href = `${API}/csv?${p.toString()}`;
            enlace.setAttribute('download', `reporte-${tipo}-${estado.desde}-a-${estado.hasta}.csv`);
        });
    }

    function aplicar(modo) {
        guardarFiltros();
        escribirUrl(modo);
        pintarFiltros();
        cargar({ silencioso: false });
    }

    function esqueletoKpis() {
        const tiles = KPIS.map(k => ({ etiqueta: k.etiqueta, destacado: k.destacado })).concat([{ etiqueta: 'Por cobrar' }]);
        el.kpis.replaceChildren(...tiles.map(t => {
            const tile = crear('div', `admin-stat${t.destacado ? ' admin-stat-destacado' : ''}`);
            const linea = crear('span', 'admin-skeleton-linea admin-skeleton-bloque');
            linea.setAttribute('aria-hidden', 'true');
            tile.append(crear('span', 'admin-stat-label', t.etiqueta), linea, crear('span', 'visually-hidden', 'Cargando…'));
            return tile;
        }));
    }

    function esqueletos() {
        esqueletoKpis();
        graficos.serie = null;
        graficos.horas = null;
        graficos.dias = null;
        Admin.cargando(el.serie, 6);
        Admin.cargando(el.top, 5);
        Admin.cargando(el.categorias, 3);
        Admin.cargando(el.pagos, 3);
        Admin.cargando(el.modalidad, 2);
        Admin.cargando(el.equipo, 4);
        Admin.cargando(el.horas, 4);
        Admin.cargando(el.dias, 3);
        el.horasFrase.textContent = '';
        el.diasFrase.textContent = '';
        el.verTabla.disabled = true;
        el.topBotones.forEach(b => { b.disabled = true; });
    }

    function chipVariacion(valor, sufijo) {
        const chip = crear('span', 'rep-var');
        if (valor === null || valor === undefined || valor === '' || !Number.isFinite(Number(valor))) {
            chip.classList.add('rep-var-nd');
            chip.textContent = 'Sin datos antes para comparar';
            return chip;
        }
        const n = Number(valor);
        let clase;
        let flecha;
        let palabra;
        let texto;
        if (Math.abs(n) < 0.05) {
            clase = 'rep-var-igual';
            flecha = '=';
            palabra = 'Sin cambio';
            texto = `0 % ${sufijo}`;
        } else if (n > 0) {
            clase = 'rep-var-sube';
            flecha = '▲';
            palabra = 'Subió';
            texto = `+${fmtDecimal.format(n)} % ${sufijo}`;
        } else {
            clase = 'rep-var-baja';
            flecha = '▼';
            palabra = 'Bajó';
            texto = `−${fmtDecimal.format(Math.abs(n))} % ${sufijo}`;
        }
        chip.classList.add(clase);
        const icono = crear('span', null, flecha);
        icono.setAttribute('aria-hidden', 'true');
        chip.append(icono, crear('span', 'visually-hidden', `${palabra}: `), crear('span', null, texto));
        return chip;
    }

    function pintarKpis(datos) {
        const resumen = datos.resumen || {};
        const anterior = resumen.anterior || {};
        const variacion = resumen.variacion || {};
        const sufijo = COMPARA[estado.periodo] || COMPARA.personalizado;
        const tiles = KPIS.map(k => {
            const tile = crear('div', `admin-stat${k.destacado ? ' admin-stat-destacado' : ''}`);
            const formato = k.dinero ? dinero : v => fmtEntero.format(num(v));
            tile.append(
                crear('span', 'admin-stat-label', k.etiqueta),
                crear('strong', 'admin-stat-valor', formato(resumen[k.clave])),
                chipVariacion(variacion[k.clave], sufijo),
                crear('span', 'rep-antes', `Antes: ${formato(anterior[k.clave])}`),
                crear('span', 'admin-stat-hint', k.ayuda)
            );
            return tile;
        });
        const pendientes = num(resumen.porCobrarPedidos);
        const cobrar = crear('div', `admin-stat${pendientes > 0 ? ' rep-stat-alerta' : ''}`);
        cobrar.append(
            crear('span', 'admin-stat-label', 'Por cobrar'),
            crear('strong', 'admin-stat-valor', plural(pendientes, 'pedido', 'pedidos')),
            crear('span', 'rep-antes', pendientes > 0 ? `Suman ${dinero(resumen.porCobrarMonto)}` : 'Todo está cobrado'),
            crear('span', 'admin-stat-hint', 'Pedidos de estas fechas que aún no se pagaron.')
        );
        if (pendientes > 0) {
            const aviso = crear('span', 'badge badge-warn', '⚠️ Revisa estos cobros');
            cobrar.insertBefore(aviso, cobrar.children[2]);
        }
        tiles.push(cobrar);
        el.kpis.replaceChildren(...tiles);
    }

    function pintarComparado(datos) {
        const ant = datos.anterior || {};
        const texto = textoRango(ant.desde, ant.hasta);
        if (!texto) {
            el.comparado.textContent = '';
            return;
        }
        el.comparado.textContent = ant.desde === ant.hasta
            ? `Comparado con el día anterior: ${minusculaInicial(texto)}.`
            : `Comparado con el período anterior: ${minusculaInicial(texto)}.`;
    }

    function escala(maximo, entero) {
        if (!(maximo > 0)) return { paso: 1, tope: entero ? 4 : 1 };
        const bruto = maximo / 4;
        const magnitud = Math.pow(10, Math.floor(Math.log10(bruto)));
        const f = bruto / magnitud;
        let paso = (f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10) * magnitud;
        if (entero) paso = Math.max(1, Math.ceil(paso));
        const tope = Math.ceil(maximo / paso - 1e-9) * paso;
        return { paso, tope: tope || paso };
    }

    function rutaBarra(x, y, ancho, alto, radio) {
        const r = Math.max(0, Math.min(radio, ancho / 2, alto));
        const base = y + alto;
        return `M${x},${base}V${y + r}Q${x},${y} ${x + r},${y}H${x + ancho - r}Q${x + ancho},${y} ${x + ancho},${y + r}V${base}Z`;
    }

    function crearGrafico() {
        registro.forEach((g, nodo) => {
            if (nodo.isConnected) return;
            if (observador) observador.unobserve(nodo);
            registro.delete(nodo);
        });
        const contenedor = crear('div', 'rep-grafico');
        contenedor.tabIndex = 0;
        contenedor.setAttribute('role', 'group');
        const lienzo = crear('div', 'rep-grafico-lienzo');
        const tooltip = crear('div', 'rep-tooltip');
        tooltip.hidden = true;
        tooltip.setAttribute('aria-hidden', 'true');
        const vivo = crear('p', 'visually-hidden');
        vivo.setAttribute('aria-live', 'polite');
        contenedor.append(lienzo, tooltip, vivo);
        const g = { el: contenedor, lienzo, tooltip, vivo, cfg: null, items: [], activo: -1, ancho: -1, geo: null, barras: [], zonas: [], svg: null };

        lienzo.addEventListener('pointermove', e => {
            if (e.pointerType === 'touch') return;
            const i = indicePorPuntero(g, e);
            if (i >= 0 && i !== g.activo) activar(g, i, false);
        });
        lienzo.addEventListener('pointerdown', e => {
            const i = indicePorPuntero(g, e);
            if (i >= 0) activar(g, i, false);
        });
        lienzo.addEventListener('pointerleave', e => {
            if (e.pointerType !== 'touch' && document.activeElement !== contenedor) ocultar(g);
        });
        contenedor.addEventListener('focus', () => {
            if (g.activo >= 0 || !contenedor.matches(':focus-visible') || !g.items.length) return;
            const destacado = g.items.findIndex(it => it.destacado);
            activar(g, destacado >= 0 ? destacado : 0, true);
        });
        contenedor.addEventListener('blur', () => ocultar(g));
        contenedor.addEventListener('keydown', e => {
            const n = g.items.length;
            if (!n) return;
            let i = g.activo;
            if (e.key === 'ArrowRight' || e.key === 'ArrowDown') i = i < 0 ? 0 : Math.min(n - 1, i + 1);
            else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') i = i < 0 ? n - 1 : Math.max(0, i - 1);
            else if (e.key === 'Home') i = 0;
            else if (e.key === 'End') i = n - 1;
            else if (e.key === 'Escape') { ocultar(g); return; }
            else return;
            e.preventDefault();
            activar(g, i, true);
        });

        registro.set(contenedor, g);
        if (observador) observador.observe(contenedor);
        return g;
    }

    function indicePorPuntero(g, e) {
        if (!g.geo || !g.svg) return -1;
        const caja = g.svg.getBoundingClientRect();
        const x = e.clientX - caja.left;
        const i = Math.floor((x - g.geo.izq) / g.geo.banda);
        return Math.max(0, Math.min(g.items.length - 1, i));
    }

    function actualizarGrafico(g, cfg, animar) {
        g.cfg = cfg;
        g.el.setAttribute('aria-label', `${cfg.titulo}. Usa las flechas izquierda y derecha para recorrer cada barra.`);
        dibujar(g, animar);
    }

    function dibujar(g, animar) {
        const ancho = Math.floor(g.el.clientWidth);
        g.ancho = ancho;
        if (!ancho || !g.cfg) return;
        const cfg = g.cfg;
        const items = typeof cfg.items === 'function' ? cfg.items(ancho) : cfg.items;
        g.items = items;
        const n = items.length;
        if (!n) {
            g.lienzo.replaceChildren();
            return;
        }
        const alto = ancho < 480 ? 200 : 240;
        const maximo = Math.max(0, ...items.map(it => it.valor));
        const { paso, tope } = escala(maximo, cfg.entero);
        const ticks = [];
        for (let k = 0; k <= Math.round(tope / paso); k++) ticks.push(Number((k * paso).toPrecision(12)));
        const textosY = ticks.map(cfg.formatoEje);
        const largoY = Math.max(...textosY.map(t => t.length));
        const izq = Math.ceil(Math.min(ancho * 0.3, largoY * 6.6 + 12));
        const der = 6;
        const arriba = 14;
        const abajo = 28;
        const w = Math.max(10, ancho - izq - der);
        const h = alto - arriba - abajo;
        const banda = w / n;
        const anchoBarra = Math.max(3, Math.min(56, banda * 0.66));
        const y = v => arriba + h - (tope ? (v / tope) * h : 0);

        const svg = crearSvg('svg', { width: ancho, height: alto, viewBox: `0 0 ${ancho} ${alto}`, class: `rep-svg${animar ? ' rep-animar' : ''}`, 'aria-hidden': 'true', focusable: 'false' });
        ticks.forEach((v, k) => {
            const yy = Math.round(y(v)) + 0.5;
            svg.append(crearSvg('line', { x1: izq, x2: ancho - der, y1: yy, y2: yy, class: k === 0 ? 'rep-base' : 'rep-grid' }));
            const t = crearSvg('text', { x: izq - 8, y: yy + 4, 'text-anchor': 'end', class: 'rep-eje' });
            t.textContent = textosY[k];
            svg.append(t);
        });

        const zonas = [];
        const barras = [];
        items.forEach((it, i) => {
            const zona = crearSvg('rect', { x: izq + banda * i, y: arriba, width: banda, height: h, class: 'rep-hit' });
            svg.append(zona);
            zonas.push(zona);
        });
        items.forEach((it, i) => {
            if (!(it.valor > 0)) {
                barras.push(null);
                return;
            }
            const x = izq + banda * i + (banda - anchoBarra) / 2;
            const top = y(it.valor);
            const altoBarra = Math.max(2, arriba + h - top);
            const barra = crearSvg('path', { d: rutaBarra(x, arriba + h - altoBarra, anchoBarra, altoBarra, Math.min(6, anchoBarra / 3)), class: `rep-barra${it.destacado ? ' rep-barra-destacada' : ''}` });
            if (animar) barra.style.animationDelay = `${Math.min(i * 18, 300)}ms`;
            svg.append(barra);
            barras.push(barra);
        });

        const largoX = Math.max(...items.map(it => String(it.corta || it.etiqueta).length));
        const salto = Math.max(1, Math.ceil((largoX * 6.4 + 8) / banda));
        items.forEach((it, i) => {
            if (i % salto !== 0) return;
            const t = crearSvg('text', { x: izq + banda * (i + 0.5), y: alto - 8, 'text-anchor': 'middle', class: `rep-eje-x${it.destacado ? ' rep-eje-x-destacado' : ''}` });
            t.textContent = it.corta || it.etiqueta;
            svg.append(t);
        });

        g.svg = svg;
        g.barras = barras;
        g.zonas = zonas;
        g.geo = { izq, banda, y, arriba, h };
        g.lienzo.replaceChildren(svg);
        if (g.activo >= n) g.activo = -1;
        if (g.activo >= 0 && !g.tooltip.hidden) activar(g, g.activo, false);
        else if (g.activo >= 0) g.activo = -1;
    }

    function activar(g, i, anunciar) {
        const it = g.items[i];
        if (!it || !g.geo) return;
        g.activo = i;
        g.svg.classList.add('rep-con-activa');
        g.barras.forEach((b, k) => { if (b) b.classList.toggle('rep-activa', k === i); });
        g.zonas.forEach((z, k) => z.classList.toggle('rep-activa', k === i));
        const titulo = crear('strong', null, it.titulo || it.etiqueta);
        const lineas = (it.detalle || []).map((texto, k) => crear('span', k === 0 ? 'rep-tooltip-valor' : null, texto));
        g.tooltip.replaceChildren(titulo, ...lineas);
        g.tooltip.hidden = false;
        const centro = g.geo.izq + g.geo.banda * (i + 0.5);
        const cima = it.valor > 0 ? g.geo.y(it.valor) : g.geo.arriba + g.geo.h;
        const tw = g.tooltip.offsetWidth;
        const th = g.tooltip.offsetHeight;
        const izquierda = Math.max(0, Math.min(g.ancho - tw, centro - tw / 2));
        const arriba = Math.max(0, cima - th - 10);
        g.tooltip.style.left = `${Math.round(izquierda)}px`;
        g.tooltip.style.top = `${Math.round(arriba)}px`;
        if (anunciar) g.vivo.textContent = `${it.titulo || it.etiqueta}: ${(it.detalle || []).join(', ')}`;
    }

    function ocultar(g) {
        g.activo = -1;
        g.tooltip.hidden = true;
        if (g.svg) g.svg.classList.remove('rep-con-activa');
        g.barras.forEach(b => { if (b) b.classList.remove('rep-activa'); });
        g.zonas.forEach(z => z.classList.remove('rep-activa'));
    }

    function alCambiarTamano(entradas) {
        entradas.forEach(entrada => {
            const g = registro.get(entrada.target);
            if (!g) return;
            if (!g.el.isConnected) {
                observador.unobserve(g.el);
                registro.delete(g.el);
                return;
            }
            if (Math.floor(entrada.contentRect.width) !== g.ancho) requestAnimationFrame(() => dibujar(g, false));
        });
    }

    function grafico(clave, cuerpo) {
        let g = graficos[clave];
        if (!g || !g.el.isConnected || !cuerpo.contains(g.el)) {
            g = crearGrafico();
            graficos[clave] = g;
            return { g, nuevo: true };
        }
        return { g, nuevo: false };
    }

    function contieneHoy(desde, hasta) {
        const h = aIso(hoy());
        return !!desde && !!hasta && desde <= h && h <= hasta;
    }

    function pintarSerie(datos, animar) {
        const serie = lista(datos.serie);
        const agrupado = datos.rango && datos.rango.agrupar;
        el.serieDesc.textContent = `Cuánto dinero cobraste en cada parte del período. ${BARRA_DESC[agrupado] || ''} Toca o pasa sobre una barra para ver el detalle.`.replace(/\s+/g, ' ');
        if (!serie.some(s => num(s.ventas) > 0)) {
            graficos.serie = null;
            el.verTabla.disabled = true;
            Admin.vacio(el.serie, { icono: '📉', titulo: 'No hubo ventas en este período', texto: 'Cuando se cobren pedidos en estas fechas, aquí verás cómo van tus ventas.' });
            return;
        }
        el.verTabla.disabled = false;
        const { g, nuevo } = grafico('serie', el.serie);
        let tablaCaja = el.serie.querySelector('.rep-tabla-serie');
        if (nuevo || !tablaCaja) {
            tablaCaja = crear('div', 'rep-tabla-serie rep-tabla-borde admin-table-wrap admin-table-scroll');
            tablaCaja.dataset.scrollPista = '';
            Admin.listo(el.serie);
            el.serie.replaceChildren(g.el, tablaCaja);
        }
        const items = serie.map(s => {
            const esHoy = contieneHoy(s.desde, s.hasta);
            const detalle = [`${dinero(s.ventas)} cobrados`, plural(num(s.pedidosCobrados), 'pedido cobrado', 'pedidos cobrados')];
            if (s.desde && s.hasta && s.desde !== s.hasta) detalle.push(textoRango(s.desde, s.hasta));
            return { etiqueta: String(s.etiqueta ?? ''), titulo: `${s.etiqueta ?? ''}${esHoy ? ' (hoy)' : ''}`, valor: num(s.ventas), destacado: esHoy, detalle };
        });
        tablaCaja.replaceChildren(tabla(
            [['Período', ''], ['Ventas cobradas', 'num'], ['Pedidos cobrados', 'num']],
            serie.map(s => [String(s.etiqueta ?? '—'), dinero(s.ventas), fmtEntero.format(num(s.pedidosCobrados))]),
            serie.map(s => (contieneHoy(s.desde, s.hasta) ? 'rep-fila-hoy' : ''))
        ));
        aplicarModoTabla();
        actualizarGrafico(g, { titulo: 'Gráfico de ventas en el tiempo', entero: false, formatoEje: dineroCorto, items }, animar);
    }

    function aplicarModoTabla() {
        el.verTabla.setAttribute('aria-pressed', String(modoTabla));
        el.verTabla.textContent = modoTabla ? 'Ver como gráfico' : 'Ver como tabla';
        const g = graficos.serie;
        const caja = el.serie.querySelector('.rep-tabla-serie');
        if (g) g.el.hidden = modoTabla;
        if (caja) caja.hidden = !modoTabla;
    }

    function tabla(columnas, filas, clasesFila) {
        const t = crear('table', 'admin-table');
        const thead = crear('thead');
        const cabecera = crear('tr');
        columnas.forEach(([texto, clase]) => {
            const th = crear('th', clase || null, texto);
            th.scope = 'col';
            cabecera.append(th);
        });
        thead.append(cabecera);
        const tbody = crear('tbody');
        filas.forEach((fila, i) => {
            const tr = crear('tr', (clasesFila && clasesFila[i]) || null);
            fila.forEach((valor, k) => {
                const td = crear('td', columnas[k][1] || null);
                td.dataset.label = columnas[k][0];
                if (valor instanceof Node) td.append(valor);
                else td.textContent = valor;
                tr.append(td);
            });
            tbody.append(tr);
        });
        t.append(thead, tbody);
        return t;
    }

    function filaBarra(opciones) {
        const li = crear('li', `rep-fila${opciones.primero ? ' rep-fila-top' : ''}`);
        const icono = crear('span', 'rep-fila-icono', opciones.icono || '•');
        icono.setAttribute('aria-hidden', 'true');
        const texto = crear('span', 'rep-fila-texto');
        const nombre = crear('span', 'rep-fila-nombre');
        if (opciones.posicion) nombre.append(crear('span', 'rep-fila-pos', `${opciones.posicion}.`), ' ');
        nombre.append(String(opciones.nombre ?? '—'));
        texto.append(nombre);
        if (opciones.sub) texto.append(crear('span', 'rep-fila-sub', opciones.sub));
        const valor = crear('span', 'rep-fila-valor', opciones.valor);
        if (opciones.extra) valor.append(crear('small', null, opciones.extra));
        const barra = crear('span', 'rep-barra-h');
        barra.setAttribute('aria-hidden', 'true');
        const relleno = crear('span');
        relleno.style.width = `${Math.max(opciones.proporcion > 0 ? 2 : 0, Math.round(opciones.proporcion * 100))}%`;
        barra.append(relleno);
        li.append(icono, texto, valor, barra);
        return li;
    }

    function pintarTop(datos) {
        const productos = lista(datos.productos).filter(p => num(p.unidades) > 0 || num(p.ingresos) > 0);
        el.topBotones.forEach(b => {
            b.disabled = !productos.length;
            b.setAttribute('aria-pressed', String(b.dataset.top === modoTop));
        });
        if (!productos.length) {
            Admin.vacio(el.top, { icono: '🍽️', titulo: 'Todavía no hay productos vendidos', texto: 'Cuando se cobren pedidos en estas fechas, aquí verás lo que más se vende.' });
            return;
        }
        const clave = modoTop === 'ingresos' ? 'ingresos' : 'unidades';
        const otra = clave === 'ingresos' ? 'unidades' : 'ingresos';
        const orden = [...productos].sort((a, b) => num(b[clave]) - num(a[clave]) || num(b[otra]) - num(a[otra]));
        const visibles = topExpandido ? orden : orden.slice(0, TOP);
        const maximo = num(orden[0][clave]) || 1;
        const ol = crear('ol', 'rep-lista');
        visibles.forEach((p, i) => {
            const unidades = num(p.unidades);
            const textoUnidades = plural(unidades, 'vendido', 'vendidos');
            ol.append(filaBarra({
                posicion: i + 1,
                primero: i === 0,
                icono: p.icono || '🍽️',
                nombre: p.nombre,
                sub: p.categoria || '',
                valor: clave === 'ingresos' ? dinero(p.ingresos) : textoUnidades,
                extra: clave === 'ingresos' ? textoUnidades : dinero(p.ingresos),
                proporcion: num(p[clave]) / maximo
            }));
        });
        const nodos = [ol];
        if (orden.length > TOP) {
            const boton = crear('button', 'button-secondary rep-ver-todos', topExpandido ? `Ver solo los ${TOP} primeros` : `Ver todos (${orden.length})`);
            boton.type = 'button';
            boton.setAttribute('aria-expanded', String(topExpandido));
            boton.addEventListener('click', () => {
                topExpandido = !topExpandido;
                pintarTop(datosActuales);
                const nuevo = el.top.querySelector('.rep-ver-todos');
                if (nuevo) nuevo.focus();
            });
            nodos.push(boton);
        }
        Admin.listo(el.top);
        el.top.replaceChildren(...nodos);
    }

    function pintarCategorias(datos) {
        const categorias = lista(datos.categorias).filter(c => num(c.ingresos) > 0).sort((a, b) => num(b.ingresos) - num(a.ingresos));
        if (!categorias.length) {
            Admin.listo(el.categorias);
            el.categorias.replaceChildren(crear('p', 'admin-help', 'Sin ventas por categoría en estas fechas.'));
            return;
        }
        const total = categorias.reduce((s, c) => s + num(c.ingresos), 0);
        const maximo = num(categorias[0].ingresos) || 1;
        const ul = crear('ul', 'rep-lista rep-lista-compacta');
        categorias.forEach((c, i) => {
            ul.append(filaBarra({
                primero: i === 0,
                icono: c.icono || '🗂️',
                nombre: c.nombre || c.codigo,
                sub: plural(num(c.unidades), 'unidad', 'unidades'),
                valor: dinero(c.ingresos),
                extra: `${porcentaje(num(c.ingresos), total)} del total`,
                proporcion: num(c.ingresos) / maximo
            }));
        });
        Admin.listo(el.categorias);
        el.categorias.replaceChildren(ul);
    }

    function metodo(valor) {
        const clave = String(valor || '').trim().toUpperCase();
        if (!clave) return ['❔', 'Sin especificar'];
        return METODOS[clave] || ['💰', capitalizar(clave.toLowerCase().replace(/_/g, ' '))];
    }

    function pintarPagos(datos) {
        const metodos = lista(datos.metodosPago).filter(m => num(m.monto) > 0 || num(m.pedidos) > 0).sort((a, b) => num(b.monto) - num(a.monto));
        if (!metodos.length) {
            Admin.vacio(el.pagos, { icono: '💳', titulo: 'Todavía no hay cobros', texto: 'Cuando se cobren pedidos en estas fechas, aquí verás cómo te pagan.' });
            return;
        }
        const total = metodos.reduce((s, m) => s + num(m.monto), 0);
        const maximo = num(metodos[0].monto) || 1;
        const ul = crear('ul', 'rep-lista');
        metodos.forEach((m, i) => {
            const [icono, nombre] = metodo(m.metodo);
            ul.append(filaBarra({
                primero: i === 0,
                icono,
                nombre,
                sub: plural(num(m.pedidos), 'pedido', 'pedidos'),
                valor: dinero(m.monto),
                extra: `${porcentaje(num(m.monto), total)} del total`,
                proporcion: num(m.monto) / maximo
            }));
        });
        Admin.listo(el.pagos);
        el.pagos.replaceChildren(ul);
    }

    function pintarModalidad(datos) {
        const negocio = window.NEGOCIO || {};
        const modalidades = lista(datos.modalidades);
        const buscar = tipo => modalidades.find(m => String(m.tipo).toUpperCase() === tipo) || {};
        const partes = [
            { clase: 'a', nombre: negocio.puntoAtencionPlural || 'Mesas', datos: buscar('MESA') },
            { clase: 'b', nombre: negocio.etiquetaParaLlevar || 'Para llevar', datos: buscar('LLEVAR') }
        ];
        const total = partes.reduce((s, p) => s + num(p.datos.pedidos), 0);
        if (!total) {
            Admin.vacio(el.modalidad, { icono: '🪑', titulo: 'Todavía no hay pedidos', texto: 'Cuando se tomen pedidos en estas fechas, aquí verás cómo se reparten.' });
            return;
        }
        const barra = crear('div', 'rep-split');
        barra.setAttribute('aria-hidden', 'true');
        partes.forEach(p => {
            const pedidos = num(p.datos.pedidos);
            if (!pedidos) return;
            const tramo = crear('span', `rep-split-${p.clase}`);
            tramo.style.flexGrow = String(pedidos);
            barra.append(tramo);
        });
        const ul = crear('ul', 'rep-leyenda');
        partes.forEach(p => {
            const pedidos = num(p.datos.pedidos);
            const li = crear('li');
            const muestra = crear('span', `rep-muestra rep-split-${p.clase}`);
            muestra.setAttribute('aria-hidden', 'true');
            const texto = crear('span', 'rep-leyenda-texto');
            texto.append(crear('strong', null, p.nombre), crear('span', null, `${plural(pedidos, 'pedido', 'pedidos')} · ${dinero(p.datos.ventas)}`));
            li.append(muestra, texto, crear('span', 'rep-leyenda-pct', porcentaje(pedidos, total)));
            ul.append(li);
        });
        Admin.listo(el.modalidad);
        el.modalidad.replaceChildren(barra, ul);
    }

    function pintarEquipo(datos) {
        const empleados = lista(datos.empleados).sort((a, b) => num(b.ventas) - num(a.ventas) || num(b.pedidosRegistrados) - num(a.pedidosRegistrados));
        if (!empleados.length) {
            Admin.vacio(el.equipo, { icono: '👥', titulo: 'Sin movimiento del equipo', texto: 'Nadie de tu equipo tuvo pedidos en estas fechas.' });
            return;
        }
        const lider = num(empleados[0].ventas) > 0 ? empleados[0] : null;
        const filas = empleados.map(e => {
            const persona = crear('span', 'rep-persona');
            const nombre = e.nombre || e.usuario || '—';
            persona.append(crear('strong', null, nombre));
            if (e.nombre && e.usuario && e.nombre !== e.usuario) persona.append(crear('small', null, e.usuario));
            if (e === lider) persona.append(crear('span', 'badge badge-ok', '🏆 Más vendió'));
            return [persona, dinero(e.ventas), fmtEntero.format(num(e.pedidosCobrados)), dinero(e.ticketPromedio), fmtEntero.format(num(e.pedidosRegistrados)), fmtEntero.format(num(e.cobros))];
        });
        const caja = crear('div', 'rep-tabla-borde admin-table-wrap admin-table-scroll');
        caja.dataset.scrollPista = '';
        caja.append(tabla(
            [['Persona', ''], ['Ventas cobradas', 'num'], ['Pedidos cobrados', 'num'], ['Ticket promedio', 'num'], ['Pedidos registrados', 'num'], ['Cobros', 'num']],
            filas,
            empleados.map(e => (e === lider ? 'rep-fila-lider' : ''))
        ));
        Admin.listo(el.equipo);
        el.equipo.replaceChildren(caja);
    }

    function textoHora(h) {
        return `${h} h`;
    }

    function pintarHoras(datos, animar) {
        const fuente = lista(datos.horas);
        const horas = Array.from({ length: 24 }, (_, h) => {
            const x = fuente.find(o => num(o.hora) === h) || {};
            return { hora: h, pedidos: num(x.pedidos), ventas: num(x.ventas) };
        });
        const activas = horas.filter(h => h.pedidos > 0);
        if (!activas.length) {
            graficos.horas = null;
            el.horasFrase.textContent = '';
            Admin.vacio(el.horas, { icono: '🕒', titulo: 'Sin pedidos en estas fechas', texto: 'Cuando haya pedidos, aquí verás tus horas más movidas.' });
            return;
        }
        const pico = horas.reduce((a, b) => (b.pedidos > a.pedidos ? b : a));
        el.horasFrase.replaceChildren(
            'Tu hora más fuerte es ',
            crear('strong', 'admin-resaltado', `de ${textoHora(pico.hora)} a ${textoHora((pico.hora + 1) % 24)}`),
            `, con ${plural(pico.pedidos, 'pedido', 'pedidos')}.`
        );
        const primera = activas[0].hora;
        const ultima = activas[activas.length - 1].hora;
        const { g, nuevo } = grafico('horas', el.horas);
        if (nuevo) {
            Admin.listo(el.horas);
            el.horas.replaceChildren(g.el);
        }
        actualizarGrafico(g, {
            titulo: 'Gráfico de pedidos por hora',
            entero: true,
            formatoEje: v => fmtEntero.format(v),
            items: ancho => {
                const angosto = ancho < 600;
                const desde = angosto ? Math.max(0, primera - 1) : 0;
                const hasta = angosto ? Math.min(23, ultima + 1) : 23;
                return horas.slice(desde, hasta + 1).map(h => ({
                    etiqueta: textoHora(h.hora),
                    titulo: `De ${textoHora(h.hora)} a ${textoHora((h.hora + 1) % 24)}`,
                    valor: h.pedidos,
                    destacado: h.hora === pico.hora,
                    detalle: [plural(h.pedidos, 'pedido', 'pedidos'), `${dinero(h.ventas)} en ventas`]
                }));
            }
        }, animar);
    }

    function pintarDias(datos, animar) {
        const dias = lista(datos.diasSemana).slice().sort((a, b) => num(a.dia) - num(b.dia));
        if (!dias.some(d => num(d.pedidos) > 0)) {
            graficos.dias = null;
            el.diasFrase.textContent = '';
            Admin.vacio(el.dias, { icono: '📆', titulo: 'Sin pedidos en estas fechas', texto: 'Cuando haya pedidos, aquí verás qué días se mueven más.' });
            return;
        }
        const pico = dias.reduce((a, b) => (num(b.pedidos) > num(a.pedidos) ? b : a));
        el.diasFrase.replaceChildren(
            'Tu día más fuerte es el ',
            crear('strong', 'admin-resaltado', String(pico.nombre || '').toLowerCase()),
            `, con ${plural(num(pico.pedidos), 'pedido', 'pedidos')}.`
        );
        const { g, nuevo } = grafico('dias', el.dias);
        if (nuevo) {
            Admin.listo(el.dias);
            el.dias.replaceChildren(g.el);
        }
        actualizarGrafico(g, {
            titulo: 'Gráfico de pedidos por día de la semana',
            entero: true,
            formatoEje: v => fmtEntero.format(v),
            items: ancho => dias.map(d => {
                const nombre = capitalizar(String(d.nombre || ''));
                return {
                    etiqueta: nombre,
                    corta: ancho < 520 ? nombre.slice(0, 3) : nombre,
                    titulo: nombre,
                    valor: num(d.pedidos),
                    destacado: d === pico,
                    detalle: [plural(num(d.pedidos), 'pedido', 'pedidos'), `${dinero(d.ventas)} en ventas`]
                };
            })
        }, animar);
    }

    function pintar(datos, animar) {
        datosActuales = datos;
        pintarComparado(datos);
        pintarKpis(datos);
        pintarSerie(datos, animar);
        pintarTop(datos);
        pintarCategorias(datos);
        pintarPagos(datos);
        pintarModalidad(datos);
        pintarEquipo(datos);
        pintarHoras(datos, animar);
        pintarDias(datos, animar);
        const hora = Admin.hora(new Date());
        el.actualizado.textContent = incluyeHoy()
            ? `Actualizado a las ${hora}. Mientras estés en esta página, se actualiza solo cada minuto.`
            : `Actualizado a las ${hora}.`;
    }

    function mostrarError(mensaje) {
        datosActuales = null;
        graficos.serie = null;
        graficos.horas = null;
        graficos.dias = null;
        el.kpis.replaceChildren();
        el.comparado.textContent = '';
        el.horasFrase.textContent = '';
        el.diasFrase.textContent = '';
        el.verTabla.disabled = true;
        el.topBotones.forEach(b => { b.disabled = true; });
        const sinDatos = { icono: '📡', titulo: 'Sin datos por ahora', texto: 'Vuelve a intentarlo en un momento.' };
        [el.serie, el.top, el.pagos, el.modalidad, el.equipo, el.horas, el.dias].forEach(c => Admin.vacio(c, sinDatos));
        Admin.listo(el.categorias);
        el.categorias.replaceChildren();
        el.error.hidden = false;
        Admin.vacio(el.error, {
            icono: '⚠️',
            titulo: 'No pudimos cargar el reporte',
            texto: mensaje || 'Revisa tu conexión a internet e inténtalo de nuevo. Tus datos están a salvo.',
            accion: { texto: 'Reintentar', alHacer: () => cargar({ silencioso: false }) }
        });
        el.actualizado.textContent = '';
    }

    async function cargar({ silencioso }) {
        if (silencioso && enCurso) return;
        const id = ++peticion;
        enCurso = true;
        if (!silencioso) esqueletos();
        else el.actualizado.textContent = 'Actualizando…';
        try {
            const p = new URLSearchParams({ desde: estado.desde, hasta: estado.hasta, agrupar: estado.agrupar });
            const respuesta = await fetch(`${API}?${p.toString()}`, { credentials: 'same-origin', headers: { Accept: 'application/json' } });
            if (!respuesta.ok) {
                const error = await window.apiError(respuesta);
                error.amigable = respuesta.status === 400;
                throw error;
            }
            const datos = await respuesta.json();
            if (id !== peticion) return;
            el.error.hidden = true;
            el.error.replaceChildren();
            ultimaCarga = Date.now();
            pintar(datos, !silencioso);
        } catch (e) {
            if (id !== peticion) return;
            const mensaje = e && e.amigable ? e.message : '';
            if (!silencioso || !datosActuales) mostrarError(mensaje);
            else el.actualizado.textContent = `No se pudo actualizar a las ${Admin.hora(new Date())}. Lo intentaremos de nuevo en un minuto.`;
        } finally {
            if (id === peticion) enCurso = false;
        }
    }

    function refrescoAutomatico() {
        if (document.visibilityState !== 'visible' || !incluyeHoy() || enCurso) return;
        if (Date.now() - ultimaCarga < INTERVALO - 1000) return;
        cargar({ silencioso: true });
    }

    function elegirPeriodo(periodo) {
        if (periodo === 'personalizado') {
            if (estado.periodo !== 'personalizado') panelAbierto = !panelAbierto;
            pintarFiltros();
            if (!el.form.hidden) el.desde.focus();
            return;
        }
        panelAbierto = false;
        estado = normalizar({ periodo, agrupar: estado.agrupar });
        aplicar('push');
    }

    function aplicarPersonalizado(e) {
        e.preventDefault();
        const desde = el.desde.value;
        const hasta = el.hasta.value;
        const error = validarRango(desde, hasta);
        if (error) {
            el.errorRango.textContent = error;
            const faltaDesde = !deIso(desde);
            const faltaHasta = !deIso(hasta);
            const ambos = !faltaDesde && !faltaHasta;
            el.desde.setAttribute('aria-invalid', String(faltaDesde || ambos));
            el.hasta.setAttribute('aria-invalid', String(faltaHasta || ambos));
            (faltaDesde || ambos ? el.desde : el.hasta).focus();
            return;
        }
        panelAbierto = false;
        estado = normalizar({ periodo: 'personalizado', desde, hasta, agrupar: estado.agrupar });
        aplicar('push');
    }

    function iniciar() {
        estado = leerUrl() || normalizar(window.Borradores ? window.Borradores.leer(CLAVE) : null);
        guardarFiltros();
        escribirUrl('replace');
        pintarFiltros();

        el.chips.forEach(chip => chip.addEventListener('click', () => elegirPeriodo(chip.dataset.periodo)));
        el.form.addEventListener('submit', aplicarPersonalizado);
        el.agrupar.addEventListener('change', () => {
            estado = normalizar({ ...estado, agrupar: el.agrupar.value });
            aplicar('push');
        });
        el.actualizar.addEventListener('click', () => cargar({ silencioso: !!datosActuales }));
        el.verTabla.addEventListener('click', () => {
            modoTabla = !modoTabla;
            aplicarModoTabla();
        });
        el.topBotones.forEach(b => b.addEventListener('click', () => {
            if (modoTop === b.dataset.top) return;
            modoTop = b.dataset.top;
            topExpandido = false;
            if (datosActuales) pintarTop(datosActuales);
        }));
        document.addEventListener('pointerdown', e => {
            registro.forEach(g => {
                if (!g.el.contains(e.target) && document.activeElement !== g.el) ocultar(g);
            });
        });
        if (!observador) {
            window.addEventListener('resize', () => registro.forEach(g => {
                if (g.el.isConnected && Math.floor(g.el.clientWidth) !== g.ancho) dibujar(g, false);
            }));
        }
        window.addEventListener('popstate', () => {
            estado = leerUrl() || normalizar({ periodo: DEFECTO });
            panelAbierto = false;
            guardarFiltros();
            pintarFiltros();
            cargar({ silencioso: false });
        });
        document.addEventListener('visibilitychange', refrescoAutomatico);
        setInterval(refrescoAutomatico, INTERVALO);

        cargar({ silencioso: false });
    }

    iniciar();
})();
