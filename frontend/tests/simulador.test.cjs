const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const settle = () => new Promise(resolve => setImmediate(resolve));
const evaluacion = (nivel, puntaje, estado = 'APROBADA') => ({ puntaje, nivel, estado, factores: [] });
function harness(request = async () => ({ puntaje: 0, nivel: 'bajo', factores: [], id: 'TEST', saldoDisponible: 100 }),
                 { registrar = true, search = '', almacen = {}, candidatas = [] } = {}) {
    const make = () => ({ value: '', listeners: {}, children: [], style: { setProperty() {} }, classList: { add() {}, remove() {} },
        addEventListener(event, handler) { this.listeners[event] = handler; },
        replaceChildren(...n) { this.children = n; }, append(...n) { this.children.push(...n); }, setAttribute() {}, reportValidity: () => true });
    const names = ['formTransaccion', 'resultadoVacio', 'resultadoLleno', 'anilloPuntaje', 'numeroPuntaje', 'insigniaNivel', 'textoVeredicto', 'contenedorRadar',
        'listaFactores', 'estadoTrafico', 'trCliente', 'btnEvaluar', 'btnAuto', 'btnAplicarEscenario', 'escenarioSimulador', 'trMonto', 'trHora', 'trCategoria',
        'trPais', 'trDispositivo', 'trIp', 'trVelocidad', 'trBeneficiario', 'resumenFactores', 'modoRegistrar', 'comparacionSimulador', 'resumenLote',
        'panelCandidatas', 'borradorSimulador', 'borradorNombre', 'quitarBorrador'];
    const ids = Object.fromEntries(names.map(id => [id, make()]));
    ids.modoRegistrar.checked = registrar;
    ids.trCliente.value = 'D-108'; ids.trCliente.selectedOptions = [{ value: 'D-108', dataset: { promedio: '250' } }];
    ids.trHora.value = '14:00'; ids.trMonto.value = '50';
    const casillas = candidatas.map(c => ({ ...make(), value: c, checked: false }));
    const calls = [], errors = [], timers = new Map(), events = {}; let next = 0;
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../src/main/resources/static/js/simulador.js'), 'utf8'), {
        document: { querySelector: s => ids[s.slice(1)], createElement: make, createTextNode: t => t,
            querySelectorAll: s => s === '.candidata-regla' ? casillas : [] },
        window: { addEventListener: (e, h) => { events[e] = h; }, location: { search } },
        URLSearchParams,
        sessionStorage: { getItem: k => almacen[k] ?? null, removeItem: k => { delete almacen[k]; } },
        setTimeout: (fn, delay) => { timers.set(++next, { fn, delay }); return next; }, clearTimeout: id => timers.delete(id),
        LSApi: { json: async (url, options) => { calls.push({ url, data: JSON.parse(options.body) }); return request(url); }, reportarError: e => errors.push(e), mostrarAviso() {} },
        LSUi: { nivel: s => s, claseNivel: s => s, moneda: n => String(n) }
    });
    return { ids, calls, errors, timers, events, casillas, almacen };
}
test('usa el titular seleccionado y no programa otro envio hasta recibir respuesta', async () => {
    let complete; const h = harness(() => new Promise(resolve => { complete = resolve; }));
    h.ids.btnAuto.listeners.click();
    assert.equal(h.calls[0].data.cliente, 'D-108'); assert.equal(h.calls.length, 1); assert.equal(h.timers.size, 0);
    h.ids.formTransaccion.listeners.submit({ preventDefault() {} }); assert.equal(h.calls.length, 1);
    h.ids.btnAuto.listeners.click();
    complete({ puntaje: 0, nivel: 'bajo', factores: [], id: 'T', saldoDisponible: 100 }); await settle();
    assert.equal(h.timers.size, 0); assert.equal(h.ids.trCliente.disabled, false);
});
test('el lote termina tras 10 solicitudes y respeta seis segundos entre envios', async () => {
    const h = harness(); h.ids.btnAuto.listeners.click(); await settle();
    for (let i = 1; i < 10; i++) {
        const [id, timer] = h.timers.entries().next().value;
        assert.equal(timer.delay, 6000); h.timers.delete(id); timer.fn(); await settle();
    }
    assert.equal(h.calls.length, 10); assert.equal(h.timers.size, 0);
    assert.match(h.ids.estadoTrafico.textContent, /Lote completado/);
});
test('un error detiene el trafico sin reintentos automaticos', async () => {
    const h = harness(() => { throw new Error('Saldo insuficiente'); });
    h.ids.btnAuto.listeners.click(); await settle();
    assert.equal(h.calls.length, 1); assert.equal(h.timers.size, 0); assert.equal(h.errors.length, 1);
    assert.equal(h.ids.trCliente.disabled, false); assert.equal(h.ids.btnEvaluar.disabled, false);
});
test('cargar un escenario no registra operaciones y salir cancela el trafico futuro', async () => {
    const h = harness(); h.ids.escenarioSimulador.value = 'riesgo'; h.ids.btnAplicarEscenario.listeners.click();
    assert.equal(h.ids.trMonto.value, '1250.00'); assert.equal(h.calls.length, 0);
    h.ids.btnAuto.listeners.click(); await settle(); h.events.pagehide(); assert.equal(h.timers.size, 0);
});
test('en modo prueba el formulario no registra y compara el catálogo con las candidatas y el borrador', async () => {
    const borrador = { nombre: 'Borrador', puntos: 25, combinacion: 'TODAS', condiciones: [] };
    const h = harness(async () => ({ resultados: [{ cliente: 'D-108', vigente: evaluacion('medio', 45),
        conCandidatas: evaluacion('alto', 62, 'RETENIDA'), saldoDisponible: 100, fondosSuficientes: true }] }),
        { registrar: false, search: '?candidata=RC001&borrador=1', almacen: { 'ls.reglaBorrador': JSON.stringify(borrador) }, candidatas: ['RC001', 'RF002'] });
    assert.equal(h.casillas[0].checked, true); assert.equal(h.casillas[1].checked, false);
    assert.equal(h.ids.borradorSimulador.hidden, false);
    h.ids.formTransaccion.listeners.submit({ preventDefault() {} }); await settle();
    assert.equal(h.calls.length, 1); assert.equal(h.calls[0].url, '/api/v1/rule/simulation');
    assert.deepEqual(h.calls[0].data.candidatas, ['RC001']); assert.equal(h.calls[0].data.borrador.nombre, 'Borrador');
    assert.equal(h.calls[0].data.escenarios[0].cliente, 'D-108');
    assert.match(h.ids.comparacionSimulador.textContent, /45 \(medio\) → con reglas candidatas: 62 \(alto\) · cambia el nivel/);
    assert.match(h.ids.resumenFactores.textContent, /retenida, sin mover saldo/);
    h.ids.quitarBorrador.listeners.click();
    assert.equal(h.ids.borradorSimulador.hidden, true); assert.equal(h.almacen['ls.reglaBorrador'], undefined);
});
test('el lote de prueba envía 50 escenarios en una solicitud y resume los cambios de nivel', async () => {
    const h = harness(async () => ({ resultados: Array.from({ length: 50 }, (_, i) => ({ cliente: 'D-108',
        vigente: evaluacion('bajo', 10), conCandidatas: evaluacion(i < 3 ? 'medio' : 'bajo', i < 3 ? 35 : 12),
        saldoDisponible: 100, fondosSuficientes: true })) }), { registrar: false, candidatas: ['RC001'] });
    h.ids.btnAuto.listeners.click(); await settle();
    assert.equal(h.calls.length, 1); assert.equal(h.calls[0].data.escenarios.length, 50);
    assert.equal(h.timers.size, 0); assert.equal(h.ids.resumenLote.hidden, false);
    const [, resumen, lista] = h.ids.resumenLote.children;
    assert.match(resumen.textContent, /^3 de 50 escenarios cambian de nivel/); assert.equal(lista.children.length, 3);
    assert.match(h.ids.estadoTrafico.textContent, /No se registró ninguna operación/);
});
