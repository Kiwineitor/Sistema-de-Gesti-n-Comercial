(function () {
    'use strict';
    if (window.ScrollPistas) return;

    const SELECTORES = [
        'dialog[open]',
        '.dialog-ui',
        '.sheet-body',
        '.admin-table-wrap',
        '[data-scroll-pista]',
        '.ctl-det-info',
        '.prod-dialog-body',
        '.cat-dialog-body',
        '.usr-dialog-body',
        '.cat-emojis'
    ].join(', ');
    const RAICES = 'dialog[open], .drawer.open, .ctl-detalle';
    const EXCLUIDAS = new Set(['UL', 'OL', 'DL', 'TABLE', 'THEAD', 'TBODY', 'TFOOT', 'TR', 'SELECT', 'TEXTAREA', 'INPUT', 'IMG', 'VIDEO', 'IFRAME', 'CANVAS', 'svg']);
    const UMBRAL = 8;
    const ALTO_ZONA = 56;

    const estados = new WeakMap();
    const duenos = new WeakMap();
    const vigilados = new Set();
    const manuales = new Set();
    const movimiento = window.matchMedia ? window.matchMedia('(prefers-reduced-motion: reduce)') : null;
    let temporizador = 0;
    let ro = null;

    const esHost = n => n && n.nodeType === 1 && n.classList.contains('sp-host');
    const desplazable = v => v === 'auto' || v === 'scroll' || v === 'overlay';
    const num = v => parseFloat(v) || 0;

    function transparente(c) {
        if (!c || c === 'transparent') return true;
        const m = c.match(/rgba?\(([^)]+)\)/);
        if (!m) return false;
        const partes = m[1].split(/[\s,/]+/).filter(Boolean);
        return partes.length >= 4 && num(partes[3]) === 0;
    }

    function fondoDe(el) {
        let n = el;
        while (n && n.nodeType === 1) {
            const c = getComputedStyle(n).backgroundColor;
            if (!transparente(c)) return c;
            n = n.parentElement;
        }
        return '';
    }

    function apto(el, cs) {
        if (!el || el.nodeType !== 1 || EXCLUIDAS.has(el.tagName) || esHost(el) || el.closest('.sp-host')) return false;
        if (el.getAttribute('data-scroll-pista') === 'no') return false;
        if (!desplazable(cs.overflowY)) return false;
        const d = cs.display;
        if (d === 'none' || d === 'contents' || d.startsWith('inline') && d !== 'inline-block' && d !== 'inline-flex' && d !== 'inline-grid') return false;
        if (d.includes('flex') && cs.flexDirection.startsWith('row') && cs.flexWrap === 'nowrap') return false;
        return true;
    }

    function crearHost() {
        const host = document.createElement('div');
        host.className = 'sp-host';
        const arriba = document.createElement('div');
        arriba.className = 'sp-fade-arriba';
        arriba.setAttribute('aria-hidden', 'true');
        const zona = document.createElement('div');
        zona.className = 'sp-zona-abajo';
        const abajo = document.createElement('div');
        abajo.className = 'sp-fade-abajo';
        abajo.setAttribute('aria-hidden', 'true');
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'sp-boton';
        boton.tabIndex = -1;
        boton.setAttribute('aria-label', 'Ver más contenido abajo');
        const texto = document.createElement('span');
        texto.textContent = 'Más abajo';
        const chevron = document.createElement('span');
        chevron.className = 'sp-chevron';
        chevron.setAttribute('aria-hidden', 'true');
        boton.append(texto, chevron);
        zona.append(abajo, boton);
        host.append(arriba, zona);
        return { host, boton };
    }

    function vigilar(el) {
        if (estados.has(el)) return estados.get(el);
        const { host, boton } = crearHost();
        const st = { host, boton, raf: 0, reboto: false, abajo: false, arriba: false, hijos: [] };
        st.onScroll = () => programar(el);
        boton.addEventListener('click', () => {
            const suave = !(movimiento && movimiento.matches);
            el.scrollBy({ top: Math.round(el.clientHeight * 0.8), behavior: suave ? 'smooth' : 'auto' });
        });
        boton.addEventListener('animationend', () => boton.classList.remove('sp-rebotar'));
        el.addEventListener('scroll', st.onScroll, { passive: true });
        estados.set(el, st);
        vigilados.add(el);
        if (ro) ro.observe(el);
        return st;
    }

    function soltar(el) {
        const st = estados.get(el);
        vigilados.delete(el);
        if (!st) return;
        el.removeEventListener('scroll', st.onScroll);
        if (st.raf) cancelAnimationFrame(st.raf);
        if (ro) {
            ro.unobserve(el);
            st.hijos.forEach(h => ro.unobserve(h));
        }
        if (st.host.parentNode) st.host.remove();
        el.classList.remove('sp-contenedor');
        ['--sp-alto', '--sp-inferior', '--sp-pl', '--sp-pr', '--sp-pt', '--sp-gap', '--sp-fondo'].forEach(p => el.style.removeProperty(p));
        estados.delete(el);
    }

    function insertarHost(el, st) {
        if (st.host.parentNode === el && el.firstChild === st.host) return;
        el.insertBefore(st.host, el.firstChild);
    }

    function observarHijos(el, st) {
        if (!ro) return;
        const actuales = [...el.children].filter(h => h !== st.host);
        st.hijos.forEach(h => { if (!actuales.includes(h)) ro.unobserve(h); });
        actuales.forEach(h => { if (!st.hijos.includes(h)) { ro.observe(h); duenos.set(h, el); } });
        st.hijos = actuales;
    }

    function medir(el, st) {
        const cs = getComputedStyle(el);
        const ultimo = [...el.children].reverse().find(h => h !== st.host);
        let inferior = 0;
        if (ultimo) {
            const cu = getComputedStyle(ultimo);
            if (cu.position === 'sticky' && cu.bottom !== 'auto') inferior = ultimo.offsetHeight;
        }
        const gap = cs.display.includes('flex') || cs.display.includes('grid') ? num(cs.rowGap) : 0;
        el.style.setProperty('--sp-alto', el.clientHeight + 'px');
        el.style.setProperty('--sp-inferior', inferior + 'px');
        el.style.setProperty('--sp-pt', cs.paddingTop);
        el.style.setProperty('--sp-pl', cs.paddingLeft);
        el.style.setProperty('--sp-pr', cs.paddingRight);
        el.style.setProperty('--sp-gap', gap + 'px');
        const fondo = fondoDe(el);
        if (fondo) el.style.setProperty('--sp-fondo', fondo);
        else el.style.removeProperty('--sp-fondo');
        el.classList.add('sp-contenedor');
    }

    function pintar(el) {
        const st = estados.get(el);
        if (!st) return;
        st.raf = 0;
        const visible = el.getClientRects().length > 0 && el.clientHeight > 0;
        const sobra = visible && el.scrollHeight > el.clientHeight + UMBRAL;
        const abajo = sobra && el.scrollHeight - el.clientHeight - el.scrollTop > UMBRAL;
        const arriba = sobra && el.scrollTop > UMBRAL;
        if (abajo !== st.abajo) {
            st.host.classList.toggle('sp-abajo', abajo);
            if (abajo && !st.reboto) {
                st.reboto = true;
                st.boton.classList.add('sp-rebotar');
            }
            st.abajo = abajo;
        }
        if (arriba !== st.arriba) {
            st.host.classList.toggle('sp-arriba', arriba);
            st.arriba = arriba;
        }
        if (!visible) st.reboto = false;
    }

    function programar(el) {
        const st = estados.get(el);
        if (!st || st.raf) return;
        st.raf = requestAnimationFrame(() => pintar(el));
    }

    function evaluar(el) {
        if (!el.isConnected) { soltar(el); return; }
        const cs = getComputedStyle(el);
        if (!apto(el, cs)) { soltar(el); return; }
        const st = vigilar(el);
        insertarHost(el, st);
        observarHijos(el, st);
        medir(el, st);
        pintar(el);
    }

    function candidatos() {
        const set = new Set(document.querySelectorAll(SELECTORES));
        document.querySelectorAll(RAICES).forEach(raiz => {
            set.add(raiz);
            raiz.querySelectorAll('*').forEach(n => {
                if (EXCLUIDAS.has(n.tagName) || n.closest('.sp-host')) return;
                if (desplazable(getComputedStyle(n).overflowY)) set.add(n);
            });
        });
        manuales.forEach(m => { if (m.isConnected) set.add(m); else manuales.delete(m); });
        return set;
    }

    function actualizar() {
        clearTimeout(temporizador);
        temporizador = 0;
        const actuales = candidatos();
        [...vigilados].forEach(el => { if (!actuales.has(el)) soltar(el); });
        actuales.forEach(evaluar);
    }

    function diferir() {
        if (temporizador) return;
        temporizador = setTimeout(actualizar, 120);
    }

    function propia(r) {
        if (r.target.nodeType === 1 && (esHost(r.target) || r.target.closest('.sp-host'))) return true;
        if (r.type !== 'childList') return false;
        const nodos = [...r.addedNodes, ...r.removedNodes];
        return nodos.length > 0 && nodos.every(esHost);
    }

    function observar(el) {
        if (!el || el.nodeType !== 1) return;
        manuales.add(el);
        evaluar(el);
    }

    function iniciar() {
        if ('ResizeObserver' in window) {
            ro = new ResizeObserver(entradas => {
                const tocados = new Set();
                entradas.forEach(e => {
                    const el = estados.has(e.target) ? e.target : duenos.get(e.target);
                    if (el && estados.has(el)) tocados.add(el);
                });
                tocados.forEach(el => {
                    const st = estados.get(el);
                    if (!el.isConnected) { soltar(el); return; }
                    medir(el, st);
                    programar(el);
                });
            });
        }
        new MutationObserver(registros => {
            if (registros.every(propia)) return;
            diferir();
        }).observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['open', 'class', 'hidden'] });
        new MutationObserver(diferir).observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
        document.addEventListener('load', e => { if (e.target && e.target.tagName === 'IMG') diferir(); }, true);
        document.addEventListener('toggle', diferir, true);
        window.addEventListener('resize', diferir, { passive: true });
        actualizar();
    }

    window.ScrollPistas = { observar, actualizar };

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar, { once: true });
    else iniciar();
})();
