const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function harness() {
    const fields = {}, calls = [];
    function field(id) {
        if (fields[id]) return fields[id];
        fields[id] = {
            value: '', required: true, listeners: {}, validityMessage: '', reported: 0,
            classes: new Set(), classList: { add(c) { this.owner.classes.add(c); }, remove(c) { this.owner.classes.delete(c); } },
            addEventListener(event, callback) { this.listeners[event] = callback; },
            setCustomValidity(message) { this.validityMessage = message; },
            reportValidity() { this.reported++; }
        };
        fields[id].classList.owner = fields[id];
        return fields[id];
    }
    vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../src/main/resources/static/js/usuarios.js'), 'utf8'), {
        document: { body: { dataset: {} }, getElementById: field }, TextEncoder,
        LSUi: { escapeHtml: text => text }, bootstrap: { Modal: { getOrCreateInstance: () => ({ show() {}, hide() {} }) } },
        LSApi: {
            async json(url, options) { calls.push({ url, options }); return []; },
            escucharAccion(element, event, action) { element.addEventListener(event, action); },
            mostrarAviso() {}, reportarError(error) { throw error; }
        }
    });
    return { fields, calls };
}

test('alta rechaza claves multibyte excesivas y admite la frontera de 72 bytes', async () => {
    const { fields, calls } = harness();
    fields.btnNuevoUsuario.listeners.click();
    fields.usuarioClave.value = 'á'.repeat(40);
    await fields.formUsuario.listeners.submit({ preventDefault() {} });
    assert.equal(calls.filter(c => c.options?.method === 'POST').length, 0);
    assert.match(fields.usuarioClave.validityMessage, /72 bytes UTF-8/);
    assert.equal(fields.usuarioClave.reported, 1);
    fields.usuarioClave.value = 'á'.repeat(36);
    fields.usuarioClave.listeners.input();
    await fields.formUsuario.listeners.submit({ preventDefault() {} });
    assert.equal(fields.usuarioClave.validityMessage, '');
    assert.equal(JSON.parse(calls.find(c => c.options?.method === 'POST').options.body).clave, 'á'.repeat(36));
});

test('los dos campos validan caracteres Unicode y reiniciar el alta borra errores anteriores', () => {
    const { fields } = harness();
    for (const field of [fields.usuarioClave, fields.claveNueva]) {
        field.value = '😀'.repeat(7);
        field.listeners.input();
        assert.notEqual(field.validityMessage, '');
        field.value = '😀'.repeat(18);
        field.listeners.input();
        assert.equal(field.validityMessage, '');
        field.value += '😀';
        field.listeners.input();
        assert.notEqual(field.validityMessage, '');
    }
    fields.btnNuevoUsuario.listeners.click();
    assert.equal(fields.usuarioClave.value, '');
    assert.equal(fields.usuarioClave.validityMessage, '');
});

test('el alta oculta el estado y no lo envía porque todo usuario nuevo queda ACTIVO', async () => {
    const { fields, calls } = harness();
    fields.btnNuevoUsuario.listeners.click();
    assert.equal(fields.grupoEstado.classes.has('d-none'), true);
    fields.usuarioClave.value = 'ClaveSegura123';
    await fields.formUsuario.listeners.submit({ preventDefault() {} });
    assert.equal('estado' in JSON.parse(calls.find(c => c.options?.method === 'POST').options.body), false);
});
