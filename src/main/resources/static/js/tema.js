(function () {
    'use strict';
    const CLAVE = 'tema';
    const consulta = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

    function preferencia() {
        try {
            const valor = localStorage.getItem(CLAVE);
            return valor === 'claro' || valor === 'oscuro' ? valor : 'sistema';
        } catch (e) {
            return 'sistema';
        }
    }

    function modoEfectivo(pref) {
        if (pref === 'claro' || pref === 'oscuro') return pref;
        return consulta && consulta.matches ? 'oscuro' : 'claro';
    }

    function aplicar() {
        const pref = preferencia();
        const modo = modoEfectivo(pref);
        document.documentElement.setAttribute('data-theme', modo === 'oscuro' ? 'dark' : 'light');
        document.querySelectorAll('[data-tema]').forEach(boton => {
            boton.setAttribute('aria-pressed', String(boton.dataset.tema === pref));
        });
        document.querySelectorAll('[data-tema-alternar]').forEach(boton => {
            const siguiente = modo === 'oscuro' ? 'claro' : 'oscuro';
            boton.textContent = siguiente === 'oscuro' ? 'Modo oscuro' : 'Modo claro';
            boton.setAttribute('aria-label', `Cambiar a modo ${siguiente}`);
        });
        document.dispatchEvent(new CustomEvent('tema:cambio', { detail: { modo, preferencia: pref } }));
    }

    function establecer(pref) {
        try {
            if (pref === 'sistema') localStorage.removeItem(CLAVE);
            else localStorage.setItem(CLAVE, pref);
        } catch (e) {
            document.documentElement.setAttribute('data-theme', modoEfectivo(pref) === 'oscuro' ? 'dark' : 'light');
            return;
        }
        aplicar();
    }

    window.Tema = { preferencia, establecer, modo: () => modoEfectivo(preferencia()) };

    document.addEventListener('click', e => {
        const opcion = e.target.closest('[data-tema]');
        if (opcion) {
            establecer(opcion.dataset.tema);
            return;
        }
        if (e.target.closest('[data-tema-alternar]')) {
            establecer(modoEfectivo(preferencia()) === 'oscuro' ? 'claro' : 'oscuro');
        }
    });
    if (consulta) {
        const alCambiar = () => { if (preferencia() === 'sistema') aplicar(); };
        if (consulta.addEventListener) consulta.addEventListener('change', alCambiar);
        else if (consulta.addListener) consulta.addListener(alCambiar);
    }
    window.addEventListener('storage', e => { if (e.key === CLAVE) aplicar(); });
    document.addEventListener('DOMContentLoaded', aplicar);
})();
