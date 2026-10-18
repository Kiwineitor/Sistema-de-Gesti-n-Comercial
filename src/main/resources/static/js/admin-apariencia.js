(function () {
    'use strict';
    const CAMPOS_API = ['nombreNegocio', 'eslogan', 'colorPrimario', 'colorSecundario', 'colorAcento',
        'puntoAtencionSingular', 'puntoAtencionPlural', 'etiquetaParaLlevar', 'etiquetaRolMesero',
        'etiquetaRolCocina', 'simboloMoneda', 'cantidadPuntosAtencion'];
    const CAMPOS = ['nombreNegocio', 'eslogan', 'colorPrimario', 'colorSecundario', 'colorAcento'];
    const ETIQUETAS = {
        nombreNegocio: 'Nombre', eslogan: 'Frase corta', colorPrimario: 'Color principal',
        colorSecundario: 'Color secundario', colorAcento: 'Color de resaltado'
    };
    const form = document.getElementById('form-apariencia');
    const warning = document.getElementById('contrast-warning');
    const estado = document.getElementById('apariencia-estado');
    const btnGuardar = document.getElementById('apariencia-guardar');
    const btnDeshacer = document.getElementById('apariencia-deshacer');
    const inputLogo = document.getElementById('logo');
    const btnLogo = document.getElementById('upload-logo');
    const valor = nombre => form.elements[nombre].value;
    const token = nombre => getComputedStyle(document.documentElement).getPropertyValue(nombre).trim();
    let guardado = Object.fromEntries(CAMPOS.map(n => [n, valor(n)]));
    let urlVistaLogo = null;

    function preview() {
        const fondo = token('--surface-page');
        const textoOscuro = token('--text-main');
        const claro = token('--ink-light');
        const oscuro = token('--ink-dark');
        const primario = valor('colorPrimario');
        const secundario = valor('colorSecundario');
        const acento = valor('colorAcento');
        const nombre = valor('nombreNegocio').trim() || 'Nombre del negocio';
        document.getElementById('preview-name').textContent = nombre;
        document.getElementById('preview-name-header').textContent = nombre;
        document.getElementById('preview-slogan').textContent = valor('eslogan');
        ['colorPrimario', 'colorSecundario', 'colorAcento'].forEach(n => {
            document.getElementById(`codigo-${n}`).textContent = valor(n).toUpperCase();
        });
        const cabecera = document.getElementById('preview-header');
        cabecera.style.backgroundColor = primario;
        cabecera.style.color = brandTextOnColor(primario, claro, oscuro);
        document.getElementById('preview-name').style.color = brandTextOnLight(primario, fondo, textoOscuro);
        document.querySelectorAll('.apariencia-mock-tags span').forEach(tag => {
            const fondoTag = brandMix(fondo, secundario, 0.12);
            tag.style.backgroundColor = fondoTag;
            tag.style.color = brandTextOnLight(secundario, fondoTag, textoOscuro);
        });
        const boton = document.getElementById('preview-button');
        boton.style.backgroundColor = acento;
        const textoBoton = brandTextOnColor(acento, claro, oscuro);
        boton.style.color = brandContrast(textoBoton, acento) >= 4.5 ? textoBoton : token('--ink-black');
        const nombres = { colorPrimario: 'principal', colorSecundario: 'secundario', colorAcento: 'resaltado' };
        const bajos = Object.keys(nombres).filter(n => brandContrast(valor(n), fondo) < 4.5).map(n => nombres[n]);
        warning.hidden = bajos.length === 0;
        warning.textContent = bajos.length
            ? `El color ${bajos.join(', ')} es muy claro sobre el fondo actual. No te preocupes: el sistema lo oscurecerá un poco en los textos para que se lean bien.`
            : '';
    }

    function cambios() {
        return CAMPOS.filter(n => String(guardado[n] ?? '').trim().toLowerCase() !== String(valor(n) ?? '').trim().toLowerCase());
    }

    function actualizarEstado() {
        const pendientes = cambios().length;
        estado.textContent = pendientes
            ? `Tienes ${pendientes} ${pendientes === 1 ? 'cambio' : 'cambios'} sin guardar.`
            : 'Todo está guardado.';
        estado.classList.toggle('pendiente', pendientes > 0);
        btnDeshacer.disabled = pendientes === 0;
    }

    function error(nombre, mensaje) {
        const campo = form.elements[nombre];
        const caja = document.getElementById(`error-${nombre}`);
        if (caja) caja.textContent = mensaje || '';
        if (mensaje) campo.setAttribute('aria-invalid', 'true');
        else campo.removeAttribute('aria-invalid');
    }

    function soltarVistaLogo() {
        if (urlVistaLogo) URL.revokeObjectURL(urlVistaLogo);
        urlVistaLogo = null;
    }

    function render(datos) {
        soltarVistaLogo();
        Admin.aplicarNegocio(datos);
        CAMPOS.forEach(n => { form.elements[n].value = datos[n] ?? ''; });
        guardado = Object.fromEntries(CAMPOS.map(n => [n, valor(n)]));
        document.getElementById('logo-actual').src = datos.logoUrl;
        document.getElementById('preview-logo').src = datos.logoUrl;
        inputLogo.value = '';
        btnLogo.disabled = true;
        borrador.limpiar();
        preview();
        actualizarEstado();
    }

    async function configuracionActual() {
        try {
            const r = await fetch('/api/admin/configuracion', { credentials: 'same-origin', headers: { Accept: 'application/json' } });
            if (r.ok) return await r.json();
        } catch (e) {
            return window.NEGOCIO || {};
        }
        return window.NEGOCIO || {};
    }

    const borrador = Borradores.vincular(form, 'apariencia', { excluir: ['logo'] });

    form.addEventListener('input', e => {
        if (e.target.name === 'nombreNegocio') error('nombreNegocio', '');
        preview();
        actualizarEstado();
    });
    form.addEventListener('change', () => { preview(); actualizarEstado(); });

    btnDeshacer.addEventListener('click', () => {
        CAMPOS.forEach(n => { form.elements[n].value = guardado[n] ?? ''; });
        error('nombreNegocio', '');
        borrador.limpiar();
        preview();
        actualizarEstado();
    });

    form.addEventListener('submit', async e => {
        e.preventDefault();
        if (!valor('nombreNegocio').trim()) {
            error('nombreNegocio', 'Escribe el nombre de tu negocio.');
            form.elements.nombreNegocio.focus();
            return;
        }
        const lista = cambios();
        if (!lista.length) { showNotice('No hay cambios para guardar', 'ok'); return; }
        const resumen = lista.map(n => ({
            etiqueta: ETIQUETAS[n],
            valor: `${String(guardado[n] ?? '').trim() || '(vacío)'} → ${valor(n).trim() || '(vacío)'}`
        }));
        const ok = await confirmAction('Estos cambios se aplicarán a todas las vistas del sistema.', {
            titulo: '¿Guardar la apariencia?', resumen, aceptar: 'Guardar cambios'
        });
        if (!ok) return;
        btnGuardar.disabled = true;
        btnGuardar.textContent = 'Guardando…';
        try {
            const actual = await configuracionActual();
            const datos = Object.fromEntries(CAMPOS_API.map(n => [n, actual[n] ?? '']));
            CAMPOS.forEach(n => { datos[n] = valor(n).trim(); });
            datos.cantidadPuntosAtencion = Number(datos.cantidadPuntosAtencion) || 0;
            const r = await secureFetch('/api/admin/configuracion', {
                method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(datos)
            });
            if (!r.ok) throw await apiError(r);
            render(await r.json());
            showNotice('Apariencia guardada', 'ok');
        } catch (err) {
            showNotice(err.message || 'No se pudo guardar');
        } finally {
            btnGuardar.disabled = false;
            btnGuardar.textContent = 'Guardar cambios';
        }
    });

    inputLogo.addEventListener('change', () => {
        const archivo = inputLogo.files[0];
        soltarVistaLogo();
        if (!archivo) {
            btnLogo.disabled = true;
            document.getElementById('logo-actual').src = window.NEGOCIO?.logoUrl || '';
            document.getElementById('preview-logo').src = window.NEGOCIO?.logoUrl || '';
            return;
        }
        if (archivo.size > 1048576) {
            showNotice('El logo no puede superar 1 MB. Prueba con una imagen más liviana.');
            inputLogo.value = '';
            btnLogo.disabled = true;
            return;
        }
        urlVistaLogo = URL.createObjectURL(archivo);
        document.getElementById('logo-actual').src = urlVistaLogo;
        document.getElementById('preview-logo').src = urlVistaLogo;
        btnLogo.disabled = false;
    });

    btnLogo.addEventListener('click', async () => {
        const archivo = inputLogo.files[0];
        if (!archivo) { showNotice('Primero elige una imagen'); return; }
        const ok = await confirmAction('El nuevo logo reemplazará al actual en el inicio de sesión y en todas las vistas.', {
            titulo: '¿Guardar el logo?',
            resumen: [{ etiqueta: 'Archivo', valor: archivo.name }, { etiqueta: 'Tamaño', valor: `${Math.ceil(archivo.size / 1024)} KB` }],
            aceptar: 'Guardar logo'
        });
        if (!ok) return;
        const cuerpo = new FormData();
        cuerpo.append('archivo', archivo);
        btnLogo.disabled = true;
        try {
            const r = await secureFetch('/api/admin/configuracion/logo', { method: 'PUT', body: cuerpo });
            if (!r.ok) throw await apiError(r);
            const datos = await r.json();
            const pendientes = Object.fromEntries(CAMPOS.map(n => [n, valor(n)]));
            render(datos);
            CAMPOS.forEach(n => { form.elements[n].value = pendientes[n]; });
            borrador.guardarAhora();
            preview();
            actualizarEstado();
            showNotice('Logo guardado', 'ok');
        } catch (err) {
            btnLogo.disabled = false;
            showNotice(err.message || 'No se pudo guardar el logo');
        }
    });

    document.getElementById('reset-config').addEventListener('click', async () => {
        if (!await confirmAction('Se perderán el nombre, los colores, los nombres de tu local y el logo personalizados.', {
            titulo: '¿Restablecer valores por defecto?', aceptar: 'Restablecer', peligro: true
        })) return;
        try {
            const r = await secureFetch('/api/admin/configuracion/restablecer', { method: 'POST' });
            if (!r.ok) throw await apiError(r);
            render(await r.json());
            Borradores.borrar('local');
            showNotice('Valores por defecto restaurados', 'ok');
        } catch (err) {
            showNotice(err.message || 'No se pudo restablecer');
        }
    });

    document.addEventListener('tema:cambio', preview);
    preview();
    actualizarEstado();
})();
