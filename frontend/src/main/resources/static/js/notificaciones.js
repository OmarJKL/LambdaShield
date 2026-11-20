(() => {
    'use strict';
    const centro = document.getElementById('centroNotificaciones');
    if (!centro) return;
    const boton = document.getElementById('btnNotificaciones');
    const contador = document.getElementById('contadorNotificaciones');
    const lista = document.getElementById('listaNotificaciones');
    const resumen = document.getElementById('resumenNotificaciones');
    const estado = document.getElementById('estadoNotificaciones');
    const leerTodas = document.getElementById('btnLeerNotificaciones');
    const anteriores = document.getElementById('btnMasNotificaciones');
    let pagina = 0;
    let items = [];
    let cargando = false;
    let pendientes = 0;
    let refrescoPendiente = false;
    let revision = 0;
    let marcando = false;
    const abiertas = new Set();
    const fechaCompleta = new Intl.DateTimeFormat('es-PE', { dateStyle: 'medium', timeStyle: 'short' });
    const relativo = new Intl.RelativeTimeFormat('es', { numeric: 'auto' });

    function tiempo(fecha) {
        const segundos = Math.round((new Date(fecha).getTime() - Date.now()) / 1000);
        if (Math.abs(segundos) < 60) return 'Ahora';
        if (Math.abs(segundos) < 3600) return relativo.format(Math.round(segundos / 60), 'minute');
        if (Math.abs(segundos) < 86400) return relativo.format(Math.round(segundos / 3600), 'hour');
        return relativo.format(Math.round(segundos / 86400), 'day');
    }
    function elemento(tag, clase, texto) {
        const nodo = document.createElement(tag);
        if (clase) nodo.className = clase;
        if (texto != null) nodo.textContent = texto;
        return nodo;
    }
    function pintar() {
        contador.hidden = pendientes === 0;
        contador.textContent = pendientes > 99 ? '99+' : String(pendientes);
        boton.setAttribute('aria-label', pendientes ? `Notificaciones, ${pendientes} sin leer` : 'Notificaciones');
        resumen.textContent = pendientes ? `${pendientes} ${pendientes === 1 ? 'aviso sin leer' : 'avisos sin leer'}` : 'Estás al día';
        leerTodas.disabled = marcando || pendientes === 0;
        if (!items.length) {
            const vacio = elemento('div', 'notificaciones-vacio');
            vacio.append(elemento('i', 'bi bi-bell-slash'), elemento('strong', '', 'No tienes notificaciones'),
                elemento('span', '', 'Aquí verás si una operación se bloquea o queda retenida.'));
            lista.replaceChildren(vacio);
            return;
        }
        const filas = items.map(item => {
            const fila = elemento('button', 'notificacion-item' + (item.leida ? '' : ' sin-leer') + (abiertas.has(item.id) ? ' expandida' : ''));
            fila.type = 'button';
            fila.disabled = marcando;
            fila.dataset.notificacionId = String(item.id);
            fila.setAttribute('aria-expanded', String(abiertas.has(item.id)));
            const icono = elemento('span', 'notificacion-icono' + (item.tipo === 'BLOQUEADA' ? ' bloqueada' : ' retenida'));
            icono.append(elemento('i', item.tipo === 'BLOQUEADA' ? 'bi bi-shield-x' : 'bi bi-shield-exclamation'));
            const contenido = elemento('span', 'notificacion-contenido');
            contenido.append(elemento('strong', 'notificacion-titulo', item.titulo),
                elemento('span', 'notificacion-mensaje', item.mensaje));
            const fecha = elemento('time', 'notificacion-fecha', tiempo(item.fecha));
            fecha.dateTime = item.fecha;
            fecha.title = fechaCompleta.format(new Date(item.fecha));
            contenido.append(fecha);
            fila.append(icono, contenido, elemento('span', 'notificacion-punto'));
            fila.addEventListener('click', async () => {
                if (marcando) return;
                if (abiertas.has(item.id)) abiertas.delete(item.id); else abiertas.add(item.id);
                if (!item.leida) {
                    marcando = true;
                    revision++;
                    pintar();
                    try {
                        await LSApi.json(`/api/v1/notification/${item.id}/leer`, { method: 'PATCH' });
                        item.leida = true;
                        pendientes = Math.max(0, pendientes - 1);
                    } catch (error) { LSApi.reportarError(error); }
                    finally { marcando = false; revision++; }
                }
                pintar();
                lista.querySelector(`[data-notificacion-id="${item.id}"]`)?.focus();
                if (refrescoPendiente) { refrescoPendiente = false; cargar(); }
            });
            return fila;
        });
        lista.replaceChildren(...filas);
    }
    async function cargar(mas = false) {
        if (marcando || cargando) { refrescoPendiente ||= !mas; return; }
        cargando = true;
        anteriores.disabled = true;
        try {
            const siguiente = mas ? pagina + 1 : 0;
            const version = revision;
            const datos = await LSApi.json(`/api/v1/notification?pagina=${siguiente}`);
            if (version !== revision) { refrescoPendiente = true; return; }
            pagina = siguiente;
            items = mas ? [...items, ...datos.items.filter(n => !items.some(actual => actual.id === n.id))] : datos.items;
            pendientes = datos.noLeidas;
            anteriores.hidden = datos.items.length < 20;
            estado.textContent = 'Solo tú puedes ver estos avisos.';
            pintar();
        } catch (error) {
            if (!error.gestionado) estado.textContent = 'No se pudieron actualizar los avisos. Vuelve a abrir la campana.';
        } finally {
            cargando = false;
            anteriores.disabled = false;
            if (refrescoPendiente) { refrescoPendiente = false; cargar(); }
        }
    }
    leerTodas.addEventListener('click', async () => {
        if (marcando) return;
        marcando = true;
        revision++;
        pintar();
        try {
            await LSApi.json('/api/v1/notification/leer-todas', { method: 'PATCH' });
            items.forEach(item => { item.leida = true; });
            pendientes = 0;
        } catch (error) { LSApi.reportarError(error); }
        finally {
            marcando = false; revision++; pintar();
            if (refrescoPendiente) { refrescoPendiente = false; cargar(); }
        }
    });
    anteriores.addEventListener('click', () => cargar(true));
    boton.addEventListener('shown.bs.dropdown', () => cargar());
    document.addEventListener('visibilitychange', () => { if (!document.hidden) cargar(); });
    setInterval(() => {
        if (!document.hidden && !boton.classList.contains('show')) cargar();
    }, 15000);
    window.LSNotifications = { refresh: () => cargar() };
    cargar();
})();
