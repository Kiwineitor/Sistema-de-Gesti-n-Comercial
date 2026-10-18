(function () {
    'use strict';
    const CAMPOS_API = ['nombreNegocio', 'eslogan', 'colorPrimario', 'colorSecundario', 'colorAcento',
        'puntoAtencionSingular', 'puntoAtencionPlural', 'etiquetaParaLlevar', 'etiquetaRolMesero',
        'etiquetaRolCocina', 'simboloMoneda', 'cantidadPuntosAtencion'];
    const CAMPOS = ['cantidadPuntosAtencion', 'puntoAtencionSingular', 'puntoAtencionPlural', 'etiquetaParaLlevar',
        'etiquetaRolMesero', 'etiquetaRolCocina', 'simboloMoneda'];
    const ETIQUETAS = {
        cantidadPuntosAtencion: 'Cantidad',
        puntoAtencionSingular: 'Nombre para una',
        puntoAtencionPlural: 'Nombre para varias',
        etiquetaParaLlevar: 'Pedidos fuera del local',
        etiquetaRolMesero: 'Vista de atención',
        etiquetaRolCocina: 'Vista de preparación',
        simboloMoneda: 'Moneda'
    };
    const MAX_VISTA = 24;
    const form = document.getElementById('form-local');
    const estado = document.getElementById('local-estado');
    const btnGuardar = document.getElementById('local-guardar');
    const btnDeshacer = document.getElementById('local-deshacer');
    const valor = nombre => form.elements[nombre].value;
    let guardado = Object.fromEntries(CAMPOS.map(n => [n, valor(n)]));

    function texto(id, contenido) {
        const el = document.getElementById(id);
        if (el) el.textContent = contenido;
    }

    function cantidad() {
        const n = parseInt(valor('cantidadPuntosAtencion'), 10);
        return Number.isNaN(n) ? 0 : Math.min(200, Math.max(0, n));
    }

    function vistaMesas() {
        const grid = document.getElementById('vista-mesas');
        const singular = valor('puntoAtencionSingular').trim() || 'Mesa';
        const llevar = valor('etiquetaParaLlevar').trim() || 'Para llevar';
        const n = cantidad();
        const fichas = [];
        for (let i = 1; i <= Math.min(n, MAX_VISTA); i++) {
            const ficha = document.createElement('span');
            ficha.className = 'local-mesa';
            ficha.textContent = `${singular} ${i}`;
            fichas.push(ficha);
        }
        if (n > MAX_VISTA) {
            const mas = document.createElement('span');
            mas.className = 'local-mesa local-mesa-mas';
            mas.textContent = `+${n - MAX_VISTA} más`;
            fichas.push(mas);
        }
        const fuera = document.createElement('span');
        fuera.className = 'local-mesa local-mesa-llevar';
        fuera.textContent = llevar;
        fichas.push(fuera);
        grid.replaceChildren(...fichas);
        texto('vista-mesas-nota', n === 0
            ? `Solo ${llevar.toLowerCase()}: tu personal no verá ${(valor('puntoAtencionPlural').trim() || 'mesas').toLowerCase()}, solo pedidos «${llevar}».`
            : '');
    }

    function ejemplos() {
        const plural = valor('puntoAtencionPlural').trim() || 'Mesas';
        texto('ej-singular', (valor('puntoAtencionSingular').trim() || 'Mesa').toLowerCase());
        texto('ej-plural', plural.toLowerCase());
        texto('ej-cantidad', String(cantidad()));
        texto('ej-llevar', (valor('etiquetaParaLlevar').trim() || 'Para llevar').toLowerCase());
        texto('ej-mesero', valor('etiquetaRolMesero').trim() || 'Mesero');
        texto('ej-cocina', valor('etiquetaRolCocina').trim() || 'Cocina');
        texto('ej-precio', `${valor('simboloMoneda').trim() || 'Bs'} 25.00`);
        vistaMesas();
    }

    function cambios() {
        return CAMPOS.filter(n => String(guardado[n] ?? '').trim() !== String(valor(n) ?? '').trim());
    }

    function actualizarEstado() {
        const pendientes = cambios().length;
        estado.textContent = pendientes
            ? `Tienes ${pendientes} ${pendientes === 1 ? 'cambio' : 'cambios'} sin guardar.`
            : 'Todo está guardado.';
        estado.classList.toggle('pendiente', pendientes > 0);
        btnDeshacer.disabled = pendientes === 0;
    }

    function marcarError(nombre, mensaje) {
        const campo = form.elements[nombre];
        const error = document.getElementById(`error-${nombre}`);
        if (error) error.textContent = mensaje || '';
        if (mensaje) campo.setAttribute('aria-invalid', 'true');
        else campo.removeAttribute('aria-invalid');
    }

    function validar() {
        let primero = null;
        CAMPOS.forEach(nombre => {
            let mensaje = '';
            const v = valor(nombre).trim();
            if (nombre === 'cantidadPuntosAtencion') {
                const n = Number(v);
                if (v === '' || !Number.isInteger(n) || n < 0 || n > 200) mensaje = 'Escribe un número entero entre 0 y 200.';
            } else if (!v) {
                mensaje = 'Este dato no puede quedar vacío.';
            }
            marcarError(nombre, mensaje);
            if (mensaje && !primero) primero = form.elements[nombre];
        });
        if (primero) primero.focus();
        return !primero;
    }

    const borrador = Borradores.vincular(form, 'local');

    form.addEventListener('input', e => {
        if (e.target.name) marcarError(e.target.name, '');
        ejemplos();
        actualizarEstado();
    });
    form.addEventListener('change', () => { ejemplos(); actualizarEstado(); });

    form.querySelectorAll('[data-paso]').forEach(boton => boton.addEventListener('click', () => {
        const campo = form.elements.cantidadPuntosAtencion;
        campo.value = String(Math.min(200, Math.max(0, cantidad() + Number(boton.dataset.paso))));
        campo.dispatchEvent(new Event('input', { bubbles: true }));
    }));

    btnDeshacer.addEventListener('click', () => {
        CAMPOS.forEach(n => { form.elements[n].value = guardado[n] ?? ''; marcarError(n, ''); });
        borrador.limpiar();
        ejemplos();
        actualizarEstado();
    });

    async function configuracionActual() {
        try {
            const r = await fetch('/api/admin/configuracion', { credentials: 'same-origin', headers: { Accept: 'application/json' } });
            if (r.ok) return await r.json();
        } catch (e) {
            return window.NEGOCIO || {};
        }
        return window.NEGOCIO || {};
    }

    form.addEventListener('submit', async e => {
        e.preventDefault();
        if (!validar()) { showNotice('Revisa los datos marcados en rojo'); return; }
        const lista = cambios();
        if (!lista.length) { showNotice('No hay cambios para guardar', 'ok'); return; }
        const resumen = lista.map(n => ({
            etiqueta: ETIQUETAS[n],
            valor: `${String(guardado[n] ?? '').trim() || '(vacío)'} → ${valor(n).trim() || '(vacío)'}`
        }));
        const ok = await confirmAction('Tu personal verá estos cambios en sus pantallas al recargar.', {
            titulo: '¿Guardar los cambios de tu local?', resumen, aceptar: 'Guardar cambios'
        });
        if (!ok) return;
        btnGuardar.disabled = true;
        btnGuardar.textContent = 'Guardando…';
        try {
            const actual = await configuracionActual();
            const datos = Object.fromEntries(CAMPOS_API.map(n => [n, actual[n] ?? '']));
            CAMPOS.forEach(n => { datos[n] = valor(n).trim(); });
            datos.cantidadPuntosAtencion = Number(datos.cantidadPuntosAtencion);
            const r = await secureFetch('/api/admin/configuracion', {
                method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(datos)
            });
            if (!r.ok) throw await apiError(r);
            const respuesta = await r.json();
            Admin.aplicarNegocio(respuesta);
            CAMPOS.forEach(n => { form.elements[n].value = respuesta[n] ?? ''; });
            guardado = Object.fromEntries(CAMPOS.map(n => [n, valor(n)]));
            borrador.limpiar();
            ejemplos();
            actualizarEstado();
            showNotice('Cambios guardados', 'ok');
        } catch (error) {
            showNotice(error.message || 'No se pudieron guardar los cambios');
        } finally {
            btnGuardar.disabled = false;
            btnGuardar.textContent = 'Guardar cambios';
        }
    });

    ejemplos();
    actualizarEstado();
})();
