(function () {
    'use strict';
    const nav = document.getElementById('admin-nav');
    const fondo = document.querySelector('.admin-nav-fondo');
    const boton = document.querySelector('[data-admin-abrir]');
    const main = document.querySelector('.admin-main');
    const escritorio = window.matchMedia('(min-width: 960px)');
    const ENFOCABLES = 'a[href], button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

    function navAbierta() {
        return !!nav && nav.classList.contains('abierta');
    }

    function abrirNav() {
        if (!nav || escritorio.matches || navAbierta()) return;
        nav.classList.add('abierta');
        if (fondo) fondo.hidden = false;
        if (boton) boton.setAttribute('aria-expanded', 'true');
        document.body.classList.add('admin-nav-abierta');
        if (main) main.inert = true;
        nav.setAttribute('role', 'dialog');
        nav.setAttribute('aria-modal', 'true');
        const destino = nav.querySelector('[aria-current="page"]') || nav.querySelector(ENFOCABLES);
        setTimeout(() => destino?.focus(), 30);
    }

    function cerrarNav(devolverFoco = true) {
        if (!nav || !navAbierta()) return;
        nav.classList.remove('abierta');
        if (fondo) fondo.hidden = true;
        if (boton) boton.setAttribute('aria-expanded', 'false');
        document.body.classList.remove('admin-nav-abierta');
        if (main) main.inert = false;
        nav.removeAttribute('role');
        nav.removeAttribute('aria-modal');
        if (devolverFoco) boton?.focus();
    }

    if (nav) {
        boton?.addEventListener('click', () => (navAbierta() ? cerrarNav() : abrirNav()));
        document.querySelectorAll('[data-admin-cerrar]').forEach(el => el.addEventListener('click', () => cerrarNav()));
        nav.addEventListener('click', e => {
            if (e.target.closest('a[href]')) cerrarNav(false);
        });
        document.addEventListener('keydown', e => {
            if (!navAbierta()) return;
            if (e.key === 'Escape') {
                e.preventDefault();
                cerrarNav();
                return;
            }
            if (e.key !== 'Tab') return;
            const focos = [...nav.querySelectorAll(ENFOCABLES)].filter(el => el.offsetParent !== null);
            if (!focos.length) return;
            const primero = focos[0];
            const ultimo = focos[focos.length - 1];
            if (!nav.contains(document.activeElement)) {
                e.preventDefault();
                (e.shiftKey ? ultimo : primero).focus();
            } else if (e.shiftKey && document.activeElement === primero) {
                e.preventDefault();
                ultimo.focus();
            } else if (!e.shiftKey && document.activeElement === ultimo) {
                e.preventDefault();
                primero.focus();
            }
        });
        const alCambiarAncho = () => { if (escritorio.matches) cerrarNav(false); };
        if (escritorio.addEventListener) escritorio.addEventListener('change', alCambiarAncho);
        else if (escritorio.addListener) escritorio.addListener(alCambiarAncho);
        window.addEventListener('pageshow', () => cerrarNav(false));
    }

    const titulo = document.getElementById('admin-seccion-actual');
    const actual = nav?.querySelector('[aria-current="page"] .admin-nav-label');
    if (titulo) titulo.textContent = actual ? actual.textContent.trim() : (document.querySelector('.admin-page-head h1')?.textContent.trim() || '');

    function crear(tag, clase, texto) {
        const el = document.createElement(tag);
        if (clase) el.className = clase;
        if (texto !== undefined && texto !== null) el.textContent = String(texto);
        return el;
    }

    function aFecha(valor) {
        if (valor instanceof Date) return valor;
        if (valor === null || valor === undefined || valor === '') return null;
        const texto = String(valor);
        const soloFecha = /^(\d{4})-(\d{2})-(\d{2})$/.exec(texto);
        const fecha = soloFecha ? new Date(Number(soloFecha[1]), Number(soloFecha[2]) - 1, Number(soloFecha[3])) : new Date(texto);
        return Number.isNaN(fecha.getTime()) ? null : fecha;
    }

    function dinero(valor) {
        const simbolo = window.NEGOCIO?.simboloMoneda || '';
        return `${simbolo} ${(Number(valor) || 0).toFixed(2)}`.trim();
    }

    function fechaHora(iso) {
        const fecha = aFecha(iso);
        if (!fecha) return '—';
        return fecha.toLocaleString('es', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
    }

    function fecha(iso) {
        const valor = aFecha(iso);
        if (!valor) return '—';
        return valor.toLocaleDateString('es', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
    }

    function hora(iso) {
        const valor = aFecha(iso);
        if (!valor) return '—';
        return valor.toLocaleTimeString('es', { hour: '2-digit', minute: '2-digit' });
    }

    function cargando(el, filas = 3) {
        if (!el) return;
        const caja = crear('div', 'admin-skeleton');
        caja.setAttribute('aria-hidden', 'true');
        for (let i = 0; i < Math.max(1, filas); i++) caja.append(crear('div', 'admin-skeleton-linea'));
        const texto = crear('span', 'visually-hidden', 'Cargando…');
        el.setAttribute('aria-busy', 'true');
        el.replaceChildren(caja, texto);
    }

    function vacio(el, opciones = {}) {
        const caja = crear('div', 'admin-empty');
        if (opciones.icono) {
            const icono = crear('div', 'admin-empty-icono', opciones.icono);
            icono.setAttribute('aria-hidden', 'true');
            caja.append(icono);
        }
        if (opciones.titulo) caja.append(crear('p', 'admin-empty-titulo', opciones.titulo));
        if (opciones.texto) caja.append(crear('p', 'admin-empty-texto', opciones.texto));
        const accion = opciones.accion;
        if (accion) {
            const contenedor = crear('div', 'admin-empty-accion');
            if (accion instanceof Node) {
                contenedor.append(accion);
            } else if (accion.href) {
                const enlace = crear('a', `btn ${accion.clase || 'button-primary'}`, accion.texto);
                enlace.href = accion.href;
                contenedor.append(enlace);
            } else {
                const btn = crear('button', accion.clase || 'button-primary', accion.texto);
                btn.type = 'button';
                if (typeof accion.alHacer === 'function') btn.addEventListener('click', accion.alHacer);
                contenedor.append(btn);
            }
            caja.append(contenedor);
        }
        if (el) {
            el.removeAttribute('aria-busy');
            el.replaceChildren(caja);
        }
        return caja;
    }

    function listo(el) {
        el?.removeAttribute('aria-busy');
    }

    function aplicarNegocio(datos) {
        if (!datos) return;
        window.NEGOCIO = { ...(window.NEGOCIO || {}), ...datos };
        if (datos.tema) Object.entries(datos.tema).forEach(([nombre, valor]) => document.documentElement.style.setProperty(nombre, valor));
        document.querySelectorAll('[data-negocio-texto]').forEach(el => {
            const valor = datos[el.dataset.negocioTexto];
            if (valor !== undefined && valor !== null) el.textContent = valor;
        });
        if (datos.logoUrl) {
            document.querySelectorAll('[data-negocio-logo]').forEach(img => { img.src = datos.logoUrl; });
            const icono = document.querySelector('link[rel="icon"]');
            if (icono) icono.href = datos.logoUrl;
        }
        if (datos.nombreNegocio) {
            const partes = document.title.split(' — ');
            if (partes.length > 1) document.title = `${partes[0]} — ${datos.nombreNegocio}`;
        }
    }

    const barraAcciones = document.querySelector('.admin-actionbar');
    if (barraAcciones) {
        const ajustar = () => document.body.style.setProperty('--notice-offset', `${barraAcciones.offsetHeight + 8}px`);
        ajustar();
        window.addEventListener('resize', ajustar);
        if (window.ResizeObserver) new ResizeObserver(ajustar).observe(barraAcciones);
    }

    const ALTO_MINIMO = 280;
    let ajusteAltosPendiente = 0;

    const separaciones = new WeakMap();

    function pixeles(nombre) {
        return parseFloat(getComputedStyle(document.documentElement).getPropertyValue(nombre)) || 0;
    }

    function ajustarAltosVentana() {
        ajusteAltosPendiente = 0;
        const cabecera = pixeles('--admin-header-h') + pixeles('--space-2');
        document.querySelectorAll('[data-alto-ventana]').forEach(el => {
            const reserva = Number(el.dataset.altoVentana) || 16;
            const ancla = el.dataset.altoAncla ? document.querySelector(el.dataset.altoAncla) : null;
            let disponible;
            if (ancla && getComputedStyle(ancla).position === 'sticky') {
                if (window.scrollY < 1 || !separaciones.has(el)) {
                    separaciones.set(el, el.getBoundingClientRect().top - ancla.getBoundingClientRect().bottom);
                }
                disponible = window.innerHeight - cabecera - ancla.offsetHeight - separaciones.get(el) - reserva;
            } else {
                disponible = window.innerHeight - cabecera - reserva;
            }
            el.style.setProperty('--alto-disponible', `${Math.max(ALTO_MINIMO, Math.floor(disponible))}px`);
        });
    }

    function programarAjusteAltos() {
        if (!ajusteAltosPendiente) ajusteAltosPendiente = requestAnimationFrame(ajustarAltosVentana);
    }

    window.addEventListener('resize', programarAjusteAltos);
    window.addEventListener('load', programarAjusteAltos);
    if (window.ResizeObserver) new ResizeObserver(programarAjusteAltos).observe(document.body);
    programarAjusteAltos();

    window.Admin = { dinero, fechaHora, fecha, hora, cargando, vacio, listo, aplicarNegocio, abrirNav, cerrarNav, ajustarAltosVentana };
})();
