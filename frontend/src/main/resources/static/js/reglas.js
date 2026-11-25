(() => {
    'use strict';
    const tabla = document.getElementById('tablaReglas');
    const filas = tabla ? Array.from(tabla.querySelectorAll('[data-fila-regla]')) : [];
    const buscar = document.getElementById('buscarReglas');
    const filtro = document.getElementById('filtrarEstadoReglas');
    const tamano = document.getElementById('tamanoPaginaReglas');
    const anterior = document.getElementById('reglasAnterior');
    const siguiente = document.getElementById('reglasSiguiente');
    let pagina = 1;
    const normalizar = texto => texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
    const busquedas = new Map(filas.map(fila => [fila, normalizar(fila.cells[0].textContent + ' ' + fila.cells[1].textContent)]));
    const peso = fila => {
        const valor = Number.parseInt(fila.dataset.peso, 10);
        return Number.isInteger(valor) && valor >= 0 && valor <= 100 ? valor : null;
    };

    /** Mismo reparto que el motor: peso ÷ suma de pesos activos × 100, con un decimal. */
    function participaciones(activas) {
        const validas = filas.filter(f => activas.has(f) && peso(f) !== null);
        const suma = validas.reduce((total, f) => total + peso(f), 0);
        return new Map(validas.map(f => [f, suma === 0 ? 0 : Math.round(peso(f) * 1000 / suma) / 10]));
    }
    const activasActuales = () => new Set(filas.filter(f => f.dataset.activa === 'true'));
    const porcentaje = valor => valor === undefined ? '—' : valor.toFixed(1) + ' %';

    function actualizarParticipacion() {
        const reparto = participaciones(activasActuales());
        filas.forEach(fila => {
            const celda = fila.querySelector('[data-participacion-regla]');
            if (celda) celda.textContent = porcentaje(reparto.get(fila));
        });
    }

    function mostrarCatalogo() {
        if (!tabla) return;
        const consulta = normalizar(buscar.value.trim());
        const coincidencias = filas.filter(fila => busquedas.get(fila).includes(consulta)
            && (filtro.value === 'todas' || fila.dataset.activa === filtro.value));
        const cantidad = Number(tamano.value);
        const paginas = Math.max(1, Math.ceil(coincidencias.length / cantidad));
        pagina = Math.min(pagina, paginas);
        filas.forEach(fila => { fila.hidden = true; });
        coincidencias.slice((pagina - 1) * cantidad, pagina * cantidad).forEach(fila => { fila.hidden = false; });
        document.getElementById('reglasSinResultados').hidden = coincidencias.length > 0;
        document.getElementById('paginaReglasInfo').textContent = coincidencias.length
            ? `${(pagina - 1) * cantidad + 1}–${Math.min(pagina * cantidad, coincidencias.length)} de ${coincidencias.length} reglas · Página ${pagina} de ${paginas}`
            : '0 reglas';
        document.getElementById('resumenCatalogoReglas').textContent = `${filas.length} reglas · ${filas.filter(f => f.dataset.activa === 'true').length} activas`;
        anterior.disabled = pagina === 1;
        siguiente.disabled = pagina === paginas;
    }
    if (tabla) {
        buscar.addEventListener('input', () => { pagina = 1; mostrarCatalogo(); });
        [filtro, tamano].forEach(control => control.addEventListener('change', () => { pagina = 1; mostrarCatalogo(); }));
        anterior.addEventListener('click', () => { pagina--; mostrarCatalogo(); });
        siguiente.addEventListener('click', () => { pagina++; mostrarCatalogo(); });
        mostrarCatalogo();
    }

    /** Modal de confirmación con el reparto de participación antes y después del cambio. */
    function confirmarCambio(fila, activa) {
        const modal = document.getElementById('modalEstadoRegla');
        if (!modal) return Promise.resolve(false);
        const codigo = fila.dataset.codigo;
        const antes = participaciones(activasActuales());
        const nuevas = activasActuales();
        if (activa) nuevas.add(fila); else nuevas.delete(fila);
        const despues = participaciones(nuevas);
        document.getElementById('modalEstadoReglaTitulo').textContent = `${activa ? 'Activar' : 'Desactivar'} ${codigo}`;
        document.getElementById('modalEstadoReglaTexto').textContent = activa
            ? `${codigo} empezará a aportar puntaje en las nuevas evaluaciones y las demás reglas reducirán su participación.`
            : `${codigo} dejará de aportar puntaje en las nuevas evaluaciones y su participación se repartirá entre las demás.`;
        const impacto = document.getElementById('modalEstadoReglaImpacto');
        impacto.replaceChildren(...filas.filter(f => antes.has(f) || despues.has(f)).map(f => {
            const tr = document.createElement('tr');
            [`${f.dataset.codigo} · ${f.dataset.nombre || ''}`, porcentaje(antes.get(f)), porcentaje(despues.get(f))].forEach((texto, i) => {
                const celda = document.createElement('td');
                celda.textContent = texto;
                if (i > 0) celda.className = 'text-end dato-mono';
                if (f === fila) celda.classList?.add('fw-bold');
                tr.append(celda);
            });
            return tr;
        }));
        const confirmar = document.getElementById('modalEstadoReglaConfirmar');
        confirmar.textContent = activa ? 'Activar regla' : 'Desactivar regla';
        const instancia = bootstrap.Modal.getOrCreateInstance(modal);
        return new Promise(resolve => {
            let aceptado = false;
            confirmar.onclick = () => { aceptado = true; instancia.hide(); };
            modal.addEventListener('hidden.bs.modal', () => { confirmar.onclick = null; resolve(aceptado); }, { once: true });
            instancia.show();
        });
    }

    document.querySelectorAll('[data-cambiar-regla]').forEach(boton => {
        boton.addEventListener('click', async () => {
            const activa = boton.dataset.activa !== 'true';
            const mensaje = document.getElementById('estadoReglasMensaje');
            const fila = boton.closest('[data-fila-regla]');
            boton.disabled = true;
            try {
                if (!await confirmarCambio(fila, activa)) return;
                mensaje.textContent = 'Guardando estado de la regla…';
                await LSApi.json('/api/v1/rule/' + encodeURIComponent(boton.dataset.codigo) + '/estado', {
                    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ activa })
                });
                boton.dataset.activa = String(activa);
                boton.textContent = activa ? 'Desactivar' : 'Activar';
                boton.setAttribute('aria-label', boton.textContent + ' ' + boton.dataset.codigo);
                const estado = fila.querySelector('[data-estado-regla]');
                fila.dataset.activa = String(activa);
                estado.textContent = activa ? 'Activa' : 'Inactiva';
                estado.className = 'badge ' + (activa ? 'bg-success' : 'bg-secondary');
                actualizarParticipacion();
                mostrarCatalogo();
                if (fila.hidden) filtro?.focus?.();
                mensaje.textContent = boton.dataset.codigo + (activa ? ' activada.' : ' desactivada.')
                    + ' La participación de las reglas se reajustó para las nuevas evaluaciones.';
            } catch (error) {
                mensaje.textContent = error.gestionado || !error.message
                    ? 'No se pudo cambiar el estado. Inténtalo nuevamente.'
                    : error.message;
                LSApi.reportarError(error);
            } finally { boton.disabled = false; }
        });
    });
})();
