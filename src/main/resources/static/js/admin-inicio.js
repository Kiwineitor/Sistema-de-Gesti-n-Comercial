(function () {
    'use strict';
    const INTERVALO = 30000;
    const kpis = {
        ventas: document.getElementById('kpi-ventas'),
        pedidos: document.getElementById('kpi-pedidos'),
        activos: document.getElementById('kpi-activos'),
        ticket: document.getElementById('kpi-ticket')
    };
    const top = document.getElementById('inicio-top');
    const empleados = document.getElementById('inicio-empleados');
    const actualizado = document.getElementById('inicio-actualizado');
    const errorBox = document.getElementById('inicio-error');
    let primeraCarga = true;
    let cargandoAhora = false;
    let temporizador = null;

    document.getElementById('hoy-fecha').textContent = capitalizar(Admin.fecha(new Date()));

    function capitalizar(texto) {
        return texto ? texto.charAt(0).toUpperCase() + texto.slice(1) : texto;
    }

    function crear(tag, clase, texto) {
        const el = document.createElement(tag);
        if (clase) el.className = clase;
        if (texto !== undefined && texto !== null) el.textContent = String(texto);
        return el;
    }

    function esqueletos() {
        Object.values(kpis).forEach(el => {
            const linea = crear('span', 'admin-skeleton-linea admin-skeleton-bloque');
            linea.setAttribute('aria-hidden', 'true');
            el.replaceChildren(linea);
        });
        Admin.cargando(top, 4);
        Admin.cargando(empleados, 3);
    }

    function pintarKpis(datos) {
        kpis.ventas.textContent = Admin.dinero(datos.ventasCobradas);
        kpis.pedidos.textContent = String(Number(datos.pedidosHoy) || 0);
        kpis.activos.textContent = String(Number(datos.pedidosActivos) || 0);
        kpis.ticket.textContent = Admin.dinero(datos.ticketPromedio);
    }

    function pintarTop(lista) {
        const productos = Array.isArray(lista) ? lista.filter(p => p && Number(p.cantidad) > 0) : [];
        if (!productos.length) {
            Admin.vacio(top, {
                icono: '🍽️',
                titulo: 'Todavía no hay ventas hoy',
                texto: 'Cuando tu personal tome pedidos, aquí verás lo que más se vende.'
            });
            return;
        }
        const maximo = Math.max(...productos.map(p => Number(p.cantidad) || 0), 1);
        const ol = crear('ol', 'inicio-top');
        productos.forEach((p, i) => {
            const li = crear('li');
            const pos = crear('span', 'inicio-top-pos', i + 1);
            pos.setAttribute('aria-hidden', 'true');
            const nombre = crear('span', 'inicio-top-nombre', p.nombre);
            const cantidad = Number(p.cantidad) || 0;
            const cant = crear('span', 'inicio-top-cant', `${cantidad} ${cantidad === 1 ? 'vendido' : 'vendidos'}`);
            const barra = crear('span', 'inicio-top-barra');
            barra.setAttribute('aria-hidden', 'true');
            const relleno = crear('span');
            relleno.style.width = `${Math.round((cantidad / maximo) * 100)}%`;
            barra.append(relleno);
            li.append(pos, nombre, cant, barra);
            ol.append(li);
        });
        Admin.listo(top);
        top.replaceChildren(ol);
    }

    function pintarEmpleados(lista) {
        const filas = Array.isArray(lista) ? lista.filter(Boolean) : [];
        if (!filas.length) {
            Admin.vacio(empleados, {
                icono: '👥',
                titulo: 'Nadie ha tomado pedidos hoy',
                texto: 'Aquí verás cuántos pedidos tomó cada persona de tu equipo.'
            });
            return;
        }
        const envoltura = crear('div', 'inicio-card-tabla admin-table-wrap');
        const tabla = crear('table', 'admin-table');
        const thead = crear('thead');
        const cabecera = crear('tr');
        [['Empleado', ''], ['Pedidos', 'num'], ['Ventas cobradas', 'num']].forEach(([texto, clase]) => {
            const th = crear('th', clase || null, texto);
            th.scope = 'col';
            cabecera.append(th);
        });
        thead.append(cabecera);
        const tbody = crear('tbody');
        filas.forEach(f => {
            const tr = crear('tr');
            const usuario = crear('td', null, f.usuario || '—');
            usuario.dataset.label = 'Empleado';
            const pedidos = crear('td', 'num', Number(f.pedidos) || 0);
            pedidos.dataset.label = 'Pedidos';
            const ventas = crear('td', 'num', Admin.dinero(f.ventas));
            ventas.dataset.label = 'Ventas cobradas';
            tr.append(usuario, pedidos, ventas);
            tbody.append(tr);
        });
        tabla.append(thead, tbody);
        envoltura.append(tabla);
        Admin.listo(empleados);
        empleados.replaceChildren(envoltura);
    }

    function pintarPasos(datos) {
        const paso = document.getElementById('paso-menu');
        const detalle = document.getElementById('paso-menu-detalle');
        const activos = Number(datos.productosActivosHoy) || 0;
        paso.classList.toggle('hecho', activos > 0);
        detalle.textContent = activos > 0
            ? `Listo: hoy tienes ${activos} ${activos === 1 ? 'producto a la venta' : 'productos a la venta'}.`
            : 'Elige qué vendes hoy y cuántas porciones tienes.';
    }

    function mostrarError() {
        Object.values(kpis).forEach(el => { el.textContent = '—'; });
        top.replaceChildren();
        empleados.replaceChildren();
        Admin.vacio(top, { icono: '📡', titulo: 'Sin datos por ahora', texto: 'Vuelve a intentarlo en un momento.' });
        Admin.vacio(empleados, { icono: '📡', titulo: 'Sin datos por ahora', texto: 'Vuelve a intentarlo en un momento.' });
        errorBox.hidden = false;
        Admin.vacio(errorBox, {
            icono: '⚠️',
            titulo: 'No pudimos cargar los números de hoy',
            texto: 'Revisa tu conexión a internet. Tus datos están a salvo.',
            accion: { texto: 'Reintentar', alHacer: () => cargar(true) }
        });
    }

    async function cargar(forzarEsqueleto = false) {
        if (cargandoAhora) return;
        cargandoAhora = true;
        if (primeraCarga || forzarEsqueleto) esqueletos();
        try {
            const respuesta = await fetch('/api/admin/resumen-hoy', { credentials: 'same-origin', headers: { Accept: 'application/json' } });
            if (!respuesta.ok) throw await apiError(respuesta);
            const datos = await respuesta.json();
            errorBox.hidden = true;
            errorBox.replaceChildren();
            pintarKpis(datos);
            pintarTop(datos.topProductos);
            pintarEmpleados(datos.porEmpleado);
            pintarPasos(datos);
            actualizado.textContent = `Actualizado a las ${Admin.hora(new Date())}. Se actualiza solo cada 30 segundos.`;
            primeraCarga = false;
        } catch (e) {
            if (primeraCarga || forzarEsqueleto) mostrarError();
            else actualizado.textContent = `No se pudo actualizar a las ${Admin.hora(new Date())}. Lo intentaremos de nuevo en unos segundos.`;
        } finally {
            cargandoAhora = false;
        }
    }

    function programar() {
        clearInterval(temporizador);
        temporizador = setInterval(() => { if (document.visibilityState === 'visible') cargar(); }, INTERVALO);
    }

    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible') { cargar(); programar(); }
    });
    window.addEventListener('focus', () => cargar());

    cargar();
    programar();
})();
