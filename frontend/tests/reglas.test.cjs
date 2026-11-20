const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
function harness(json) {
    const estado = { textContent: 'Activa', className: 'badge bg-success' };
    const mensaje = { textContent: '' };
    const calls = [], errors = [];
    const boton = { dataset: { codigo: 'RF002', activa: 'true' }, textContent: 'Desactivar',
        parentElement: { querySelector: () => estado }, setAttribute() {},
        addEventListener(event, handler) { this.click = handler; } };
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../src/main/resources/static/js/reglas.js'), 'utf8'), {
        document: { querySelectorAll: () => [boton], getElementById: () => mensaje },
        LSApi: { json: async (url, options) => { calls.push({ url, options }); return json(); }, reportarError: e => errors.push(e) }
    });
    return { boton, estado, mensaje, calls, errors };
}
test('persiste el estado solicitado y actualiza solo tras confirmar el servidor', async () => {
    let complete;
    const h = harness(() => new Promise(resolve => { complete = resolve; }));
    const pending = h.boton.click();
    assert.equal(h.boton.disabled, true);
    assert.equal(h.estado.textContent, 'Activa');
    assert.equal(h.calls[0].url, '/api/v1/rule/RF002/estado');
    assert.equal(h.calls[0].options.method, 'PATCH');
    assert.deepEqual(JSON.parse(h.calls[0].options.body), { activa: false });
    complete(null); await pending;
    assert.equal(h.estado.textContent, 'Inactiva');
    assert.equal(h.boton.textContent, 'Activar');
    assert.equal(h.boton.disabled, false);
});
test('si falla conserva el estado y permite reintentar', async () => {
    const h = harness(() => { throw new Error('Fallo'); });
    await h.boton.click();
    assert.equal(h.estado.textContent, 'Activa');
    assert.equal(h.boton.dataset.activa, 'true');
    assert.equal(h.boton.disabled, false);
    assert.equal(h.errors.length, 1);
});
