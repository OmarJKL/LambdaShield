(() => {
    'use strict';

    const $ = (sel) => document.querySelector(sel);
    const form            = $('#formTransaccion');
    const resultadoVacio  = $('#resultadoVacio');
    const resultadoLleno  = $('#resultadoLleno');
    const anilloPuntaje   = $('#anilloPuntaje');
    const numeroPuntaje   = $('#numeroPuntaje');
    const insigniaNivel   = $('#insigniaNivel');
    const textoVeredicto  = $('#textoVeredicto');
    const contenedorRadar = $('#contenedorRadar');
    const listaFactores   = $('#listaFactores');

    const TAMANO_LOTE = 50;
    const ESTADOS = {
        APROBADA: { texto: 'Aprobada', icono: 'bi-check-circle-fill' },
        RETENIDA: { texto: 'Operación retenida', icono: 'bi-pause-circle-fill' },
        BLOQUEADA: { texto: 'Operación bloqueada', icono: 'bi-x-octagon-fill' }
    };

    let intervaloAutoId = null;
    let autoActivo = false;
    let enviando = false;
    let enviados = 0;
    const estadoTrafico = $('#estadoTrafico');
    const clienteSelect = $('#trCliente');
    const btnEvaluar = $('#btnEvaluar');
    const modoRegistrar = $('#modoRegistrar');
    const registrando = () => modoRegistrar?.checked === true;

    // Parámetros de la URL: ?candidata=RC001 preselecciona una regla; ?borrador=1 carga el del formulario.
    const parametros = (() => { try { return new URLSearchParams(window.location?.search || ''); } catch { return new URLSearchParams(); } })();
    let borrador = leerBorrador();

    function leerBorrador() {
        if (parametros.get('borrador') !== '1') return null;
        try { return JSON.parse(sessionStorage.getItem('ls.reglaBorrador')); } catch { return null; }
    }

    function candidatas() {
        return Array.from(document.querySelectorAll?.('.candidata-regla') || []).filter(c => c.checked).map(c => c.value);
    }

    function leerTransaccionFormulario() {
        const [hh] = $('#trHora').value.split(':');
        return {
            cliente: $('#trCliente').value,
            monto: Number($('#trMonto').value) || 0,
            categoria: $('#trCategoria').value,
            hora: Number(hh),
            pais: $('#trPais').value,
            dispositivo: $('#trDispositivo').value,
            operacionesUltimaHora: Number($('#trVelocidad').value),
            beneficiario: $('#trBeneficiario').value,
            ip: $('#trIp').value,
        };
    }

    function ocupar(valor) {
        enviando = valor;
        if (btnEvaluar) btnEvaluar.disabled = valor || autoActivo;
    }

    async function enviarYEvaluar(datos) {
        if (enviando) return null;
        ocupar(true);
        try {
            const resultado = await LSApi.json('/api/v1/evaluation', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(datos)
            });

            pintarResultado(resultado);
            ocultarComparacion();

            if (resultado.nivel === 'critico') {
                lanzarAlerta(resultado);
            }
            return resultado;
        } catch (error) {
            detenerTrafico('Tráfico detenido: no se pudo registrar la operación. Revisa el mensaje antes de reintentar.');
            LSApi.reportarError(error);
            return null;
        } finally {
            ocupar(false);
        }
    }

    function pedirPrueba(escenarios) {
        return LSApi.json('/api/v1/rule/simulation', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ escenarios, candidatas: candidatas(), borrador })
        });
    }

    /** Evalúa el escenario del formulario sin registrar la operación ni mover saldo. */
    async function probar(datos) {
        if (enviando) return null;
        ocupar(true);
        try {
            const { resultados } = await pedirPrueba([datos]);
            pintarPrueba(resultados[0], datos);
            return resultados[0];
        } catch (error) {
            LSApi.reportarError(error);
            return null;
        } finally {
            ocupar(false);
        }
    }

    function pintarPrueba(r, datos) {
        const principal = r.conCandidatas || r.vigente;
        const estado = ESTADOS[principal.estado] || ESTADOS.APROBADA;
        pintarResultado({ ...principal, id: 'Prueba sin guardar', estado: estado.texto, iconoEstado: estado.icono,
            colorNivel: `var(--riesgo-${principal.nivel})` }, efectoEnSaldo(principal.estado, r, datos.monto));
        const comparacion = $('#comparacionSimulador');
        if (!comparacion) return;
        if (r.conCandidatas) {
            const cambia = r.vigente.nivel !== r.conCandidatas.nivel;
            comparacion.textContent = `Catálogo vigente: ${r.vigente.puntaje} (${LSUi.nivel(r.vigente.nivel)}) → `
                + `con reglas candidatas: ${r.conCandidatas.puntaje} (${LSUi.nivel(r.conCandidatas.nivel)})`
                + (cambia ? ' · cambia el nivel de riesgo.' : ' · mismo nivel de riesgo.');
            comparacion.hidden = false;
        } else ocultarComparacion();
    }

    function ocultarComparacion() {
        const comparacion = $('#comparacionSimulador');
        if (comparacion) comparacion.hidden = true;
    }

    function efectoEnSaldo(estado, r, monto) {
        if (estado !== 'APROBADA') return `Si se registrara: ${ESTADOS[estado].texto.toLowerCase()}, sin mover saldo.`;
        return r.fondosSuficientes
            ? `Si se registrara: aprobada; descontaría S/ ${LSUi.moneda(monto)} del saldo de S/ ${LSUi.moneda(r.saldoDisponible)}.`
            : `Si se registrara: se rechazaría por saldo insuficiente (saldo S/ ${LSUi.moneda(r.saldoDisponible)}).`;
    }

    function pintarResultado(registro, detalle) {
        if (!resultadoVacio || !resultadoLleno) return;
        resultadoVacio.classList.add('d-none');
        resultadoLleno.classList.remove('d-none');

        const { puntaje, nivel, factores, id } = registro;

        anilloPuntaje.style.setProperty('--p', `${(puntaje / 100) * 360}deg`);
        anilloPuntaje.style.setProperty('--c', registro.colorNivel);
        numeroPuntaje.textContent = puntaje;

        insigniaNivel.textContent = `Riesgo ${LSUi.nivel(nivel)}`;
        insigniaNivel.className = `etiqueta-nivel ${LSUi.claseNivel(nivel)}`;

        textoVeredicto.replaceChildren();
        const iconoVeredicto = document.createElement('i');
        iconoVeredicto.className = `bi ${registro.iconoEstado}`;
        iconoVeredicto.style.color = registro.colorNivel;
        textoVeredicto.append(iconoVeredicto, document.createTextNode(` ${registro.estado} · ${id}`));

        const resumen = $('#resumenFactores');
        if (resumen) resumen.textContent = `${factores.length} reglas aplicadas en esta evaluación · `
            + (detalle || `Saldo disponible: S/ ${LSUi.moneda(registro.saldoDisponible)}`);
        contenedorRadar.replaceChildren();
        // Para catálogos grandes, el detalle por regla evita un radar ilegible.
        contenedorRadar.hidden = factores.length < 3 || factores.length > 12;
        if (!contenedorRadar.hidden) contenedorRadar.replaceChildren(construirRadarSVG(factores));
        listaFactores.replaceChildren(...factores.map(construirFactorElemento));
    }

    function construirFactorElemento(f) {
        const porcentaje = f.max > 0 ? Math.round((f.puntaje / f.max) * 100) : 0;
        let colorBarra = 'var(--riesgo-bajo)';
        if (porcentaje >= 45) colorBarra = 'var(--riesgo-alto)';
        if (porcentaje >= 75) colorBarra = 'var(--riesgo-critico)';

        const columna = document.createElement('div');
        columna.className = 'col-md-6';

        const item = document.createElement('div');
        item.className = 'item-factor';

        const cabecera = document.createElement('div');
        cabecera.className = 'd-flex justify-content-between mb-1';
        cabecera.style.fontSize = '0.85rem';
        cabecera.style.color = 'var(--texto-principal)';

        const etiqueta = document.createElement('span');
        etiqueta.textContent = f.etiqueta;

        const puntaje = document.createElement('strong');
        puntaje.className = 'dato-mono';
        puntaje.textContent = `${f.puntaje}/${f.max}`;

        cabecera.append(etiqueta, puntaje);

        const barra = document.createElement('div');
        barra.className = 'barra-factor';
        const relleno = document.createElement('span');
        relleno.style.width = `${porcentaje}%`;
        relleno.style.background = colorBarra;
        barra.appendChild(relleno);

        const motivo = document.createElement('div');
        motivo.className = 'text-secondary mt-1';
        motivo.style.fontSize = '0.75rem';
        motivo.style.lineHeight = '1.3';
        motivo.textContent = f.motivo;

        item.append(cabecera, barra, motivo);
        columna.appendChild(item);
        return columna;
    }

    function construirRadarSVG(factores) {
        const size = 240, center = size / 2, radius = 84, n = Math.max(1, factores.length);
        const svgNS = 'http://www.w3.org/2000/svg';
        const puntoEnEje = (i, ratio) => {
            const angulo = (Math.PI * 2 * i) / n - Math.PI / 2;
            return [center + Math.cos(angulo) * radius * ratio, center + Math.sin(angulo) * radius * ratio];
        };

        const svg = document.createElementNS(svgNS, 'svg');
        svg.setAttribute('viewBox', `0 0 ${size} ${size}`);
        svg.setAttribute('width', '100%');
        svg.setAttribute('height', '240');

        [0.25, 0.5, 0.75, 1].forEach(r => {
            const polygon = document.createElementNS(svgNS, 'polygon');
            polygon.setAttribute('points', factores.map((_, i) => puntoEnEje(i, r).join(',')).join(' '));
            polygon.setAttribute('fill', 'none');
            polygon.setAttribute('stroke', 'var(--borde-oscuro)');
            polygon.setAttribute('stroke-width', '1');
            svg.appendChild(polygon);
        });

        factores.forEach((_, i) => {
            const [x, y] = puntoEnEje(i, 1);
            const line = document.createElementNS(svgNS, 'line');
            line.setAttribute('x1', center);
            line.setAttribute('y1', center);
            line.setAttribute('x2', x);
            line.setAttribute('y2', y);
            line.setAttribute('stroke', 'var(--borde-oscuro)');
            line.setAttribute('stroke-width', '1');
            svg.appendChild(line);
        });

        const dato = document.createElementNS(svgNS, 'polygon');
        dato.setAttribute('points', factores.map((f, i) => puntoEnEje(i, f.max > 0 ? (f.puntaje / f.max) : 0).join(',')).join(' '));
        dato.setAttribute('fill', 'var(--utp-rojo-brillo)');
        dato.setAttribute('stroke', 'var(--utp-rojo)');
        dato.setAttribute('stroke-width', '2');
        svg.appendChild(dato);

        const etiquetasCortas = {
            monto: 'Monto (RF1)',
            dispositivo: 'Disp. (RF2)',
            ip: 'IP (RF3)',
            ubicacion: 'País (RF4)',
            horario: 'Hora (RF5)',
            velocidad: 'Veloc. (RF6)',
            viaje_imposible: 'Viaje (RF7)',
            beneficiario: 'Benef. (RF8)',
            categoria: 'Rubro'
        };

        factores.forEach((f, i) => {
            const [x, y] = puntoEnEje(i, 1.22);
            const text = document.createElementNS(svgNS, 'text');
            text.setAttribute('x', x);
            text.setAttribute('y', y);
            text.setAttribute('text-anchor', 'middle');
            text.setAttribute('dominant-baseline', 'middle');
            text.setAttribute('font-size', '10');
            text.setAttribute('fill', 'var(--texto-secundario)');
            text.setAttribute('font-family', 'system-ui');
            text.textContent = etiquetasCortas[f.id] || f.etiqueta;
            svg.appendChild(text);
        });

        return svg;
    }

    function lanzarAlerta(registro) {
        LSApi.mostrarAviso(`Transacción bloqueada: ${registro.id} · ${registro.clienteNombre} · S/ ${LSUi.moneda(registro.monto)} · Puntaje: ${registro.puntaje}`);
    }

    function generarTransaccionAleatoria(opt = clienteSelect.selectedOptions[0]) {
        if (!opt) return null;
        const cliente = opt.value;
        const promedio = Number(opt.dataset.promedio) || 100;
        const categorias = ['retail', 'restaurante', 'servicios', 'electronica', 'casino', 'cripto'];
        const paises = ['local', 'local', 'local', 'regional', 'alto_riesgo'];
        const esSospechosa = Math.random() < 0.25;

        return {
            cliente,
            monto: esSospechosa ? Math.round(promedio * (4 + Math.random() * 6)) : Math.round(promedio * (0.5 + Math.random() * 1.2)),
            categoria: esSospechosa ? categorias[Math.floor(Math.random() * categorias.length)] : categorias[Math.floor(Math.random() * 4)],
            hora: esSospechosa ? Math.floor(Math.random() * 5) : Math.floor(Math.random() * 24),
            pais: esSospechosa ? paises[3 + Math.floor(Math.random() * 2)] : paises[Math.floor(Math.random() * 3)],
            dispositivo: esSospechosa ? 'nuevo' : 'reconocido',
            operacionesUltimaHora: esSospechosa ? 4 + Math.floor(Math.random() * 8) : 1 + Math.floor(Math.random() * 2),
            beneficiario: esSospechosa ? 'nuevo' : 'recurrente',
            ip: esSospechosa ? 'nueva' : 'conocida'
        };
    }

    /** Prueba sin guardar 50 escenarios aleatorios de todos los titulares en una sola solicitud. */
    async function probarLote() {
        if (enviando) return;
        const titulares = Array.from(clienteSelect?.options || clienteSelect?.selectedOptions || []);
        if (!titulares.length) { estadoTrafico.textContent = 'No hay titulares disponibles para las pruebas.'; return; }
        const escenarios = Array.from({ length: TAMANO_LOTE },
            () => generarTransaccionAleatoria(titulares[Math.floor(Math.random() * titulares.length)]));
        ocupar(true);
        estadoTrafico.textContent = `Probando ${TAMANO_LOTE} escenarios sin guardar…`;
        try {
            const { resultados } = await pedirPrueba(escenarios);
            pintarLote(resultados, escenarios);
            estadoTrafico.textContent = `Lote de ${resultados.length} escenarios evaluado. No se registró ninguna operación.`;
        } catch (error) {
            estadoTrafico.textContent = 'No se pudo completar la prueba del lote.';
            LSApi.reportarError(error);
        } finally {
            ocupar(false);
        }
    }

    function pintarLote(resultados, escenarios) {
        const contenedor = $('#resumenLote');
        if (!contenedor) return;
        const niveles = ['bajo', 'medio', 'alto', 'critico'];
        const contar = clave => Object.fromEntries(niveles.map(n => [n, resultados.filter(r => r[clave]?.nivel === n).length]));
        const vigente = contar('vigente');
        const comparar = resultados.some(r => r.conCandidatas);
        const candidatasConteo = comparar ? contar('conCandidatas') : null;

        const tabla = document.createElement('table');
        tabla.className = 'table table-sm align-middle mb-2';
        const fila = (celdas, tag = 'td') => {
            const tr = document.createElement('tr');
            celdas.forEach(texto => { const c = document.createElement(tag); c.textContent = texto; tr.append(c); });
            return tr;
        };
        const cabecera = document.createElement('thead');
        cabecera.append(fila(['Nivel', 'Catálogo vigente', ...(comparar ? ['Con candidatas'] : [])], 'th'));
        const cuerpo = document.createElement('tbody');
        niveles.forEach(n => cuerpo.append(fila([LSUi.nivel(n), String(vigente[n]),
            ...(comparar ? [String(candidatasConteo[n])] : [])])));
        tabla.append(cabecera, cuerpo);

        const cambios = resultados.map((r, i) => ({ r, e: escenarios[i] }))
            .filter(({ r }) => r.conCandidatas && r.conCandidatas.nivel !== r.vigente.nivel);
        const resumen = document.createElement('p');
        resumen.className = 'small mb-1';
        resumen.textContent = comparar
            ? `${cambios.length} de ${resultados.length} escenarios cambian de nivel con las reglas candidatas.`
            : 'Selecciona reglas candidatas o carga un borrador para comparar su efecto.';
        const lista = document.createElement('ul');
        lista.className = 'small mb-0';
        cambios.slice(0, 10).forEach(({ r, e }) => {
            const item = document.createElement('li');
            item.textContent = `${e.cliente} · S/ ${LSUi.moneda(e.monto)} · ${String(e.hora).padStart(2, '0')}:00 · ${e.pais}: `
                + `${LSUi.nivel(r.vigente.nivel)} (${r.vigente.puntaje}) → ${LSUi.nivel(r.conCandidatas.nivel)} (${r.conCandidatas.puntaje})`;
            lista.append(item);
        });
        contenedor.replaceChildren(tabla, resumen, lista);
        contenedor.hidden = false;
    }

    function pintarBotonAuto(btn, icono, texto) {
        const i = document.createElement('i');
        i.className = `bi ${icono}`;
        btn.replaceChildren(i, document.createTextNode(` ${texto}`));
    }

    function etiquetaBotonAuto() {
        const btn = $('#btnAuto');
        if (!btn || autoActivo) return;
        pintarBotonAuto(btn, 'bi-lightning-charge-fill', registrando()
            ? 'Iniciar tráfico automático' : `Probar lote de ${TAMANO_LOTE} escenarios`);
    }

    function detenerTrafico(mensaje) {
        autoActivo = false;
        clearTimeout(intervaloAutoId);
        intervaloAutoId = null;
        if (clienteSelect) clienteSelect.disabled = false;
        if (btnEvaluar) btnEvaluar.disabled = enviando;
        const btn = $('#btnAuto');
        if (btn) {
            etiquetaBotonAuto();
            btn.classList.remove('btn-principal');
            btn.classList.add('btn-secundario');
        }
        if (estadoTrafico) estadoTrafico.textContent = mensaje;
    }

    async function ejecutarAutomatico() {
        if (!autoActivo) return;
        const datos = generarTransaccionAleatoria();
        if (!datos) { detenerTrafico('No hay titulares disponibles para las pruebas.'); return; }
        const resultado = await enviarYEvaluar(datos);
        if (!autoActivo || !resultado) return;
        enviados++;
        if (enviados >= 10) { detenerTrafico('Lote completado: 10 operaciones registradas.'); return; }
        estadoTrafico.textContent = `${enviados} de 10 operaciones registradas para ${datos.cliente}.`;
        intervaloAutoId = setTimeout(ejecutarAutomatico, 6000);
    }

    function alternarTraficoAutomatico() {
        if (!registrando()) { probarLote(); return; }
        if (autoActivo) { detenerTrafico('Tráfico detenido. Una solicitud ya enviada puede terminar de registrarse.'); return; }
        if (enviando || !clienteSelect?.selectedOptions.length) return;
        autoActivo = true;
        enviados = 0;
        clienteSelect.disabled = true;
        if (btnEvaluar) btnEvaluar.disabled = true;
        const btn = $('#btnAuto');
        pintarBotonAuto(btn, 'bi-stop-circle-fill', 'Detener tráfico automático');
        btn.classList.remove('btn-secundario');
        btn.classList.add('btn-principal');
        estadoTrafico.textContent = 'Registrando la primera operación…';
        ejecutarAutomatico();
    }

    function aplicarEscenario() {
        if (enviando || autoActivo) return;
        const opcion = clienteSelect?.selectedOptions[0];
        if (!opcion) return;
        const promedio = Number(opcion.dataset.promedio) || 100;
        const escenario = $('#escenarioSimulador').value;
        const riesgo = escenario === 'riesgo';
        $('#trMonto').value = (Math.max(0.01, promedio * (riesgo ? 5 : 0.5))).toFixed(2);
        $('#trHora').value = riesgo ? '02:00' : '14:00';
        $('#trCategoria').value = riesgo ? 'cripto' : 'servicios';
        $('#trPais').value = riesgo ? 'alto_riesgo' : 'local';
        $('#trDispositivo').value = escenario === 'habitual' ? 'reconocido' : 'nuevo';
        $('#trIp').value = escenario === 'habitual' ? 'conocida' : 'nueva';
        $('#trVelocidad').value = riesgo ? '6' : '0';
        $('#trBeneficiario').value = riesgo ? 'nuevo' : 'recurrente';
        estadoTrafico.textContent = registrando()
            ? 'Escenario preparado. Revisa los datos y ejecuta el motor para registrarlo; el resultado depende de las reglas activas.'
            : 'Escenario preparado. Ejecuta el motor para probarlo sin guardar.';
    }

    function mostrarBorrador() {
        const panel = $('#borradorSimulador');
        if (!panel) return;
        panel.hidden = !borrador;
        if (borrador) $('#borradorNombre').textContent = `${borrador.nombre} · peso ${borrador.puntos} (sin guardar)`;
    }

    function cambiarModo() {
        if (autoActivo) detenerTrafico('Tráfico detenido al cambiar de modo.');
        etiquetaBotonAuto();
        if (btnEvaluar) {
            const icono = document.createElement('i');
            icono.className = registrando() ? 'bi bi-cpu' : 'bi bi-eye';
            btnEvaluar.replaceChildren(icono, document.createTextNode(registrando() ? ' Ejecutar y registrar' : ' Probar sin guardar'));
        }
        const panelCandidatas = $('#panelCandidatas');
        if (panelCandidatas) panelCandidatas.hidden = registrando();
    }

    if (form) {
        form.addEventListener('submit', (e) => {
            e.preventDefault();
            if (autoActivo || enviando || !form.reportValidity()) return;
            const datos = leerTransaccionFormulario();
            if (registrando()) enviarYEvaluar(datos); else probar(datos);
        });
    }

    document.querySelectorAll?.('input[name="modoSimulador"]').forEach(radio => radio.addEventListener('change', cambiarModo));
    const candidataUrl = parametros.get('candidata');
    if (candidataUrl) document.querySelectorAll?.('.candidata-regla').forEach(c => { if (c.value === candidataUrl) c.checked = true; });
    if ($('#quitarBorrador')) $('#quitarBorrador').addEventListener('click', () => {
        borrador = null;
        try { sessionStorage.removeItem('ls.reglaBorrador'); } catch { /* almacenamiento no disponible */ }
        mostrarBorrador();
    });
    mostrarBorrador();
    cambiarModo();

    window.addEventListener('pagehide', () => detenerTrafico('Tráfico detenido.'));
    if ($('#btnAplicarEscenario')) $('#btnAplicarEscenario').addEventListener('click', aplicarEscenario);
    if ($('#btnAuto')) $('#btnAuto').addEventListener('click', alternarTraficoAutomatico);
})();
