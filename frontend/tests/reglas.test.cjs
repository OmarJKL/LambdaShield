const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const settle = () => new Promise(resolve => setImmediate(resolve));
function harness(json = async () => null, count = 50, pesos = null) {
    const make = (value = '') => ({ value, textContent: '', listeners: {}, children: [], classList: { add() {} },
        addEventListener(event, handler, opciones) { (this.listeners[event] ||= []).push({ handler, once: opciones?.once }); },
        replaceChildren(...nodos) { this.children = nodos; }, append(...nodos) { this.children.push(...nodos); } });
    const disparar = (el, evento) => { const lista = el.listeners[evento] || []; el.listeners[evento] = lista.filter(l => !l.once); lista.forEach(l => l.handler()); };
    const ids = Object.fromEntries(['tablaReglas', 'buscarReglas', 'filtrarEstadoReglas', 'tamanoPaginaReglas', 'reglasAnterior', 'reglasSiguiente',
        'reglasSinResultados', 'paginaReglasInfo', 'resumenCatalogoReglas', 'estadoReglasMensaje', 'modalEstadoRegla', 'modalEstadoReglaTitulo',
        'modalEstadoReglaTexto', 'modalEstadoReglaImpacto', 'modalEstadoReglaConfirmar'].map(id => [id, make()]));
    ids.filtrarEstadoReglas.value = 'todas'; ids.tamanoPaginaReglas.value = '10';
    const filas = Array.from({ length: count }, (_, i) => {
        const estado = { textContent: 'Activa', className: 'badge bg-success' };
        const participacion = { textContent: '' };
        const codigo = 'RF' + String(i + 1).padStart(3, '0');
        const fila = { dataset: { activa: 'true', codigo, nombre: 'Regla ' + i, peso: String(pesos ? pesos[i] : 10) },
            cells: [{ textContent: codigo }, { textContent: i ? 'Regla de prueba ' + i : 'Ubicación declarada' }],
            querySelector: s => s === '[data-estado-regla]' ? estado : participacion };
        const boton = { ...make(), dataset: { codigo, activa: 'true' }, textContent: 'Desactivar', closest: () => fila, setAttribute() {} };
        return Object.assign(fila, { boton, estado, participacion });
    });
    ids.tablaReglas.querySelectorAll = () => filas;
    const calls = [], errors = [], modal = { mostrado: 0 };
    const instancia = { show() { modal.mostrado++; }, hide() { disparar(ids.modalEstadoRegla, 'hidden.bs.modal'); } };
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../src/main/resources/static/js/reglas.js'), 'utf8'), {
        document: { querySelectorAll: () => filas.map(f => f.boton), getElementById: id => ids[id], createElement: () => make() },
        bootstrap: { Modal: { getOrCreateInstance: () => instancia } },
        LSApi: { json: async (url, options) => { calls.push({ url, options }); return json(); }, reportarError: e => errors.push(e) }
    });
    // Abre el modal de la fila, decide y espera a que termine la acción.
    const cambiar = async (fila, aceptar, antesDeDecidir) => {
        const pendiente = fila.boton.listeners.click[0].handler();
        await settle();
        antesDeDecidir?.();
        if (aceptar) ids.modalEstadoReglaConfirmar.onclick(); else instancia.hide();
        return pendiente;
    };
    return { ids, filas, calls, errors, modal, cambiar };
}
test('pagina 50 reglas y permite mostrar 25 o 50 sin perder filas', () => {
    const h = harness();
    const click = el => el.listeners.click[0].handler(), change = el => el.listeners.change[0].handler();
    assert.equal(h.filas.filter(f => !f.hidden).length, 10);
    click(h.ids.reglasSiguiente);
    assert.equal(h.filas[0].hidden, true); assert.equal(h.filas[10].hidden, false);
    h.ids.tamanoPaginaReglas.value = '25'; change(h.ids.tamanoPaginaReglas);
    assert.equal(h.filas.filter(f => !f.hidden).length, 25);
    h.ids.tamanoPaginaReglas.value = '50'; change(h.ids.tamanoPaginaReglas);
    assert.equal(h.filas.filter(f => !f.hidden).length, 50);
    assert.equal(h.ids.reglasSiguiente.disabled, true);
});
test('busca sin tildes y muestra un estado vacio cuando no hay coincidencias', () => {
    const h = harness(); const input = () => h.ids.buscarReglas.listeners.input[0].handler();
    h.ids.buscarReglas.value = 'ubicacion'; input();
    assert.equal(h.filas.filter(f => !f.hidden).length, 1);
    h.ids.buscarReglas.value = 'inexistente'; input();
    assert.equal(h.filas.filter(f => !f.hidden).length, 0);
    assert.equal(h.ids.reglasSinResultados.hidden, false);
});
test('el modal muestra el reparto antes y después y solo el servidor confirma el cambio', async () => {
    let complete; const h = harness(() => new Promise(resolve => { complete = resolve; }), 3, [25, 15, 10]);
    const fila = h.filas[0];
    const pendiente = h.cambiar(fila, true, () => {
        assert.equal(h.modal.mostrado, 1); assert.equal(h.calls.length, 0);
        assert.equal(h.ids.modalEstadoReglaTitulo.textContent, 'Desactivar RF001');
        const [rf001, rf002] = h.ids.modalEstadoReglaImpacto.children.map(tr => tr.children.map(c => c.textContent));
        assert.deepEqual(rf001.slice(1), ['50.0 %', '—']); assert.deepEqual(rf002.slice(1), ['30.0 %', '60.0 %']);
    });
    await settle();
    assert.equal(h.calls[0].url, '/api/v1/rule/RF001/estado');
    assert.deepEqual(JSON.parse(h.calls[0].options.body), { activa: false });
    assert.equal(fila.estado.textContent, 'Activa');
    complete(null); await pendiente;
    assert.equal(fila.estado.textContent, 'Inactiva'); assert.equal(fila.boton.textContent, 'Activar');
    assert.equal(fila.participacion.textContent, '—'); assert.equal(h.filas[2].participacion.textContent, '40.0 %');
});
test('activar también pide confirmación y cancelar no envía nada', async () => {
    const h = harness(async () => null, 3);
    h.filas[1].dataset.activa = 'false'; h.filas[1].boton.dataset.activa = 'false';
    await h.cambiar(h.filas[1], false, () => assert.equal(h.ids.modalEstadoReglaTitulo.textContent, 'Activar RF002'));
    assert.equal(h.calls.length, 0); assert.equal(h.filas[1].boton.disabled, false);
    await h.cambiar(h.filas[0], false);
    assert.equal(h.calls.length, 0); assert.equal(h.filas[0].estado.textContent, 'Activa');
});
test('si el servidor rechaza conserva el estado y muestra su motivo', async () => {
    const h = harness(() => { throw new Error('Debe quedar al menos una regla activa en el motor.'); });
    await h.cambiar(h.filas[0], true);
    assert.equal(h.filas[0].estado.textContent, 'Activa'); assert.equal(h.filas[0].dataset.activa, 'true');
    assert.equal(h.filas[0].boton.disabled, false); assert.equal(h.errors.length, 1);
    assert.equal(h.ids.estadoReglasMensaje.textContent, 'Debe quedar al menos una regla activa en el motor.');
});
