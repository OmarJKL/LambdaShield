(() => {
    'use strict';

    function mostrarAviso(mensaje) {
        let contenedor = document.getElementById('contenedorAlertas');
        if (!contenedor) {
            contenedor = document.createElement('div');
            contenedor.id = 'contenedorAlertas';
            contenedor.className = 'contenedor-alertas position-fixed';
            document.body.appendChild(contenedor);
        }

        const aviso = document.createElement('div');
        aviso.className = 'alerta-ls';
        aviso.setAttribute('role', 'alert');
        aviso.textContent = mensaje;
        contenedor.appendChild(aviso);

        setTimeout(() => {
            aviso.style.opacity = '0';
            aviso.style.transition = 'opacity 0.4s ease';
            setTimeout(() => aviso.remove(), 400);
        }, 5000);
    }

    /**
     * Envoltorio de fetch que centraliza el manejo de sesión. Sin sesión o sin permiso el servidor
     * responde con una redirección al login: no se sigue dentro de fetch, la página entera vuelve
     * al login sin mostrar avisos. 401/403 se tratan igual por compatibilidad.
     */
    async function fetchApi(url, opciones = {}) {
        const metodo = (opciones.method || 'GET').toUpperCase();
        const destino = new URL(url, window.location.href);
        if (destino.origin !== window.location.origin) throw new Error('La API solo admite el mismo origen.');
        const headers = new Headers(opciones.headers);
        if (!['GET', 'HEAD', 'OPTIONS'].includes(metodo)) {
            const token = document.querySelector('meta[name="_csrf"]')?.content;
            const cabecera = document.querySelector('meta[name="_csrf_header"]')?.content;
            if (!token || !cabecera) throw new Error('Recarga la página para renovar la protección de sesión.');
            headers.set(cabecera, token);
        }
        const res = await fetch(destino.href, { ...opciones, headers, credentials: 'same-origin', redirect: 'manual' });

        if (res.type === 'opaqueredirect' || res.status === 401 || res.status === 403) {
            window.location.replace('/login');
            throw Object.assign(new Error('Sesión no válida'), { gestionado: true });
        }

        return res;
    }

    async function mensajeError(res, defecto = 'No se pudo completar la operación.') {
        try {
            const datos = await res.clone().json();
            return datos.errores?.[0] || datos.error || defecto;
        } catch { return defecto; }
    }

    async function validar(res) {
        if (!res.ok) throw new Error(await mensajeError(res));
        return res;
    }

    async function json(url, opciones) {
        const res = await validar(await fetchApi(url, opciones));
        return res.status === 204 ? null : res.json();
    }

    function reportarError(error) {
        if (!error.gestionado) mostrarAviso(error.message || 'No se pudo conectar con el servidor.');
    }

    function escucharAccion(elemento, evento, accion) {
        elemento.addEventListener(evento, async e => {
            try { await accion(e); } catch (error) { reportarError(error); }
        });
    }

    window.LSApi = { fetch: fetchApi, json, validar, mensajeError, mostrarAviso, reportarError, escucharAccion };

    const etiquetas = { bajo: 'Bajo', medio: 'Medio', alto: 'Alto', critico: 'Crítico' };
    window.LSUi = {
        nivel: nivel => etiquetas[nivel] || 'Desconocido',
        claseNivel: nivel => Object.hasOwn(etiquetas, nivel) ? 'riesgo-' + nivel : '',
        moneda: valor => Number(valor).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
        escapeHtml(texto) {
            const span = document.createElement('span');
            span.textContent = texto == null ? '' : String(texto);
            return span.innerHTML;
        }
    };

})();
