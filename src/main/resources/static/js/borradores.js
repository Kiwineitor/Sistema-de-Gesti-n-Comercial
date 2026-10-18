(function () {
    'use strict';
    const PREFIJO = 'borrador:';
    const VIGENCIA = 7 * 24 * 60 * 60 * 1000;
    const ESPERA = 400;
    const TIPOS_IGNORADOS = ['password', 'file', 'submit', 'button', 'reset', 'image', 'hidden'];
    const vinculos = new Set();
    let fallo = false;

    function usuario() {
        return document.querySelector('meta[name="sesion-usuario"]')?.content || 'anonimo';
    }

    function claveCompleta(clave) {
        return `${PREFIJO}${usuario()}:${clave}`;
    }

    function purgar() {
        try {
            const ahora = Date.now();
            const vencidas = [];
            for (let i = 0; i < localStorage.length; i++) {
                const clave = localStorage.key(i);
                if (!clave || !clave.startsWith(PREFIJO)) continue;
                let registro = null;
                try { registro = JSON.parse(localStorage.getItem(clave)); } catch (e) { registro = null; }
                if (!registro || typeof registro.t !== 'number' || ahora - registro.t > VIGENCIA) vencidas.push(clave);
            }
            vencidas.forEach(clave => localStorage.removeItem(clave));
        } catch (e) {
            fallo = true;
        }
    }

    function leer(clave) {
        try {
            const texto = localStorage.getItem(claveCompleta(clave));
            if (!texto) return null;
            const registro = JSON.parse(texto);
            if (!registro || typeof registro.t !== 'number' || Date.now() - registro.t > VIGENCIA) {
                localStorage.removeItem(claveCompleta(clave));
                return null;
            }
            return registro.d ?? null;
        } catch (e) {
            return null;
        }
    }

    function guardar(clave, datos) {
        try {
            localStorage.setItem(claveCompleta(clave), JSON.stringify({ t: Date.now(), d: datos }));
            fallo = false;
            return true;
        } catch (e) {
            fallo = true;
            return false;
        }
    }

    function borrar(clave) {
        try { localStorage.removeItem(claveCompleta(clave)); } catch (e) { fallo = true; }
    }

    function campos(form, excluir) {
        return Array.from(form.elements).filter(el => el.name
            && !excluir.includes(el.name)
            && !TIPOS_IGNORADOS.includes((el.type || '').toLowerCase())
            && el.tagName !== 'BUTTON' && el.tagName !== 'FIELDSET' && el.tagName !== 'OUTPUT');
    }

    function serializar(form, excluir) {
        const datos = {};
        const lista = campos(form, excluir);
        const nombres = [...new Set(lista.map(el => el.name))];
        nombres.forEach(nombre => {
            const grupo = lista.filter(el => el.name === nombre);
            const tipo = (grupo[0].type || '').toLowerCase();
            if (tipo === 'checkbox') {
                datos[nombre] = grupo.length === 1 ? grupo[0].checked : grupo.filter(el => el.checked).map(el => el.value);
            } else if (tipo === 'radio') {
                datos[nombre] = grupo.find(el => el.checked)?.value ?? '';
            } else if (tipo === 'select-multiple') {
                datos[nombre] = Array.from(grupo[0].selectedOptions).map(op => op.value);
            } else {
                datos[nombre] = grupo[0].value;
            }
        });
        return datos;
    }

    function aplicar(form, excluir, datos) {
        const lista = campos(form, excluir);
        const tocados = new Set();
        Object.keys(datos || {}).forEach(nombre => {
            const grupo = lista.filter(el => el.name === nombre);
            if (!grupo.length) return;
            const valor = datos[nombre];
            const tipo = (grupo[0].type || '').toLowerCase();
            grupo.forEach(el => {
                if (tipo === 'checkbox') {
                    const marcado = Array.isArray(valor) ? valor.includes(el.value) : !!valor;
                    if (el.checked !== marcado) { el.checked = marcado; tocados.add(el); }
                } else if (tipo === 'radio') {
                    const marcado = el.value === valor;
                    if (el.checked !== marcado) { el.checked = marcado; tocados.add(el); }
                } else if (tipo === 'select-multiple') {
                    const valores = Array.isArray(valor) ? valor : [];
                    Array.from(el.options).forEach(op => { op.selected = valores.includes(op.value); });
                    tocados.add(el);
                } else if (el.value !== String(valor ?? '')) {
                    el.value = String(valor ?? '');
                    tocados.add(el);
                }
            });
        });
        tocados.forEach(el => {
            el.dispatchEvent(new Event('input', { bubbles: true }));
            el.dispatchEvent(new Event('change', { bubbles: true }));
        });
    }

    function iguales(a, b) {
        return JSON.stringify(ordenar(a)) === JSON.stringify(ordenar(b));
    }

    function ordenar(obj) {
        return Object.keys(obj || {}).sort().reduce((acc, k) => { acc[k] = obj[k]; return acc; }, {});
    }

    function aviso(opciones = {}) {
        const caja = document.createElement('div');
        caja.className = 'borrador-aviso';
        caja.setAttribute('role', 'status');
        const icono = document.createElement('span');
        icono.className = 'borrador-aviso-icono';
        icono.setAttribute('aria-hidden', 'true');
        icono.textContent = '📝';
        const texto = document.createElement('span');
        texto.className = 'borrador-aviso-texto';
        texto.textContent = opciones.texto || 'Recuperamos lo que estabas escribiendo.';
        const boton = document.createElement('button');
        boton.type = 'button';
        boton.className = 'borrador-aviso-descartar flat';
        boton.textContent = opciones.boton || 'Descartar';
        boton.addEventListener('click', () => {
            caja.remove();
            if (typeof opciones.alDescartar === 'function') opciones.alDescartar();
        });
        caja.append(icono, texto, boton);
        if (opciones.antesDe) opciones.antesDe.before(caja);
        else if (opciones.contenedor) opciones.contenedor.prepend(caja);
        return caja;
    }

    function vincular(form, clave, opciones = {}) {
        const excluir = Array.isArray(opciones.excluir) ? opciones.excluir : [];
        let base = serializar(form, excluir);
        let temporizador = null;
        let banner = null;
        let activo = true;

        const vinculo = {
            restaurado: false,
            limpiar() {
                clearTimeout(temporizador);
                temporizador = null;
                borrar(clave);
                base = serializar(form, excluir);
                banner?.remove();
                banner = null;
            },
            guardarAhora() {
                clearTimeout(temporizador);
                temporizador = null;
                if (!activo) return;
                const actual = serializar(form, excluir);
                if (iguales(actual, base)) borrar(clave);
                else guardar(clave, actual);
            },
            sucio() {
                return !iguales(serializar(form, excluir), base);
            },
            descartar() {
                clearTimeout(temporizador);
                temporizador = null;
                borrar(clave);
                aplicar(form, excluir, base);
                borrar(clave);
                banner?.remove();
                banner = null;
            },
            desvincular() {
                vinculo.guardarAhora();
                activo = false;
                form.removeEventListener('input', programar);
                form.removeEventListener('change', programar);
                banner?.remove();
                banner = null;
                vinculos.delete(vinculo);
            }
        };

        function programar() {
            if (!activo) return;
            clearTimeout(temporizador);
            temporizador = setTimeout(vinculo.guardarAhora, ESPERA);
        }

        const borradorGuardado = leer(clave);
        if (borradorGuardado && typeof borradorGuardado === 'object') {
            if (iguales({ ...base, ...borradorGuardado }, base)) {
                borrar(clave);
            } else {
                aplicar(form, excluir, borradorGuardado);
                vinculo.restaurado = true;
                banner = aviso({
                    texto: opciones.textoAviso || 'Recuperamos lo que estabas escribiendo.',
                    boton: opciones.textoDescartar || 'Descartar',
                    contenedor: opciones.contenedorAviso || form,
                    alDescartar: () => {
                        banner = null;
                        vinculo.descartar();
                        if (typeof opciones.alDescartar === 'function') opciones.alDescartar();
                    }
                });
                if (typeof opciones.alRestaurar === 'function') opciones.alRestaurar(borradorGuardado);
            }
        }

        form.addEventListener('input', programar);
        form.addEventListener('change', programar);
        vinculos.add(vinculo);
        return vinculo;
    }

    function guardarTodo() {
        vinculos.forEach(v => v.guardarAhora());
    }

    window.addEventListener('pagehide', guardarTodo);
    document.addEventListener('visibilitychange', () => { if (document.visibilityState === 'hidden') guardarTodo(); });
    window.addEventListener('beforeunload', e => {
        guardarTodo();
        if (!fallo) return;
        const pendiente = [...vinculos].some(v => v.sucio());
        if (!pendiente) return;
        e.preventDefault();
        e.returnValue = '';
    });

    purgar();
    window.Borradores = { vincular, leer, guardar, borrar, aviso };
})();
