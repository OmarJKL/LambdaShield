const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const scripts = path.join(__dirname, '../src/main/resources/static/js');
class Element {
    constructor(value = '') {
        this.value = value;
        this.textContent = '';
        this.children = [];
        this.listeners = {};
        this.style = {};
        this.classList = { add() {}, remove() {} };
    }
    addEventListener(event, action) { this.listeners[event] = action; }
    append(...children) { this.children.push(...children); }
    appendChild(child) { this.append(child); }
    replaceChildren(...children) { this.children = children; }
    setAttribute() {}
    get innerHTML() {
        return String(this.textContent).replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
    }
}
function harness(response) {
    const elements = new Map();
    const ready = [];
    const body = new Element();
    const redirects = [];
    const calls = [];
    const metas = new Map([['_csrf', 'token-de-prueba'], ['_csrf_header', 'X-CSRF-TOKEN']]);
    const context = vm.createContext({
        URL, Headers,
        document: {
            querySelector: selector => { const key = selector.match(/name="([^"]+)"/)?.[1]; return metas.has(key) ? { content: metas.get(key) } : null; },
            getElementById: id => elements.get(id) || null,
            createElement: () => new Element(),
            createTextNode: text => ({ textContent: text }),
            addEventListener: (event, action) => { if (event === 'DOMContentLoaded') ready.push(action); },
            body
        },
        window: { location: { href: 'http://localhost:8080/operacion', origin: 'http://localhost:8080', replace: url => redirects.push(url) } },
        fetch: async (url, options) => { calls.push({ url, options }); return response; },
        setTimeout: () => 0,
        clearTimeout() {},
        console
    });
    vm.runInContext(fs.readFileSync(path.join(scripts, 'auth.js'), 'utf8'), context);
    Object.assign(context, context.window);
    return { context, elements, ready, body, redirects, calls, metas };
}
function response(status, data) {
    return { status, ok: status >= 200 && status < 300,
        json: async () => data, clone() { return this; } };
}

test('JSON centralizado rechaza validaciones y conserva el mensaje del servidor', async () => {
    const { context } = harness(response(400, { errores: ['Saldo insuficiente'] }));
    await assert.rejects(context.LSApi.json('/api'), /Saldo insuficiente/);
});
test('204 no intenta interpretar un cuerpo JSON ausente', async () => {
    const res = response(204);
    res.json = () => { throw new Error('No debe leer JSON'); };
    assert.equal(await harness(res).context.LSApi.json('/api'), null);
});
test('401 redirige al login y marca el error como gestionado', async () => {
    const h = harness(response(401));
    await assert.rejects(h.context.LSApi.json('/api'), error => error.gestionado === true);
    assert.deepEqual(h.redirects, ['/login']);
});
test('403 muestra una sola notificación aunque el consumidor reporte el error', async () => {
    const h = harness(response(403, { error: 'No autorizado' }));
    try { await h.context.LSApi.json('/api'); }
    catch (error) { h.context.LSApi.reportarError(error); }
    assert.equal(h.body.children.length, 1);
    assert.equal(h.body.children[0].children.length, 1);
    assert.equal(h.body.children[0].children[0].textContent, 'No autorizado');
});
test('las utilidades escapan HTML y mantienen céntimos y saldo cero', () => {
    const { context } = harness();
    assert.equal(context.LSUi.escapeHtml('<script>'), '&lt;script&gt;');
    assert.equal(context.LSUi.moneda(0), '0.00');
    assert.equal(context.LSUi.moneda(10.01), '10.01');
    assert.equal(context.LSUi.claseNivel('bajo'), 'riesgo-bajo');
    assert.equal(context.LSUi.claseNivel('desconocido'), '');
});
test('portal muestra el saldo confirmado por el servidor incluso si queda en cero', async () => {
    const h = harness(response(200, { nivel: 'bajo', saldoDisponible: 0, monto: 10.01,
        id: 'TR-1', fecha: '14/11/2026', estado: 'Aprobada', factores: [] }));
    for (const id of ['formTransferencia', 'resultadoTransferencia', 'btnTransferir', 'trCliente', 'trMonto',
        'trCategoria', 'trPais', 'destinatarioNombre', 'cuentaDestino', 'saldoDisponibleTexto']) {
        h.elements.set(id, new Element());
    }
    h.elements.get('saldoDisponibleTexto').textContent = '500.00';
    h.elements.get('trMonto').value = '10.01';
    vm.runInContext(fs.readFileSync(path.join(scripts, 'operacion.js'), 'utf8'), h.context);
    h.ready.forEach(action => action());
    await h.elements.get('formTransferencia').listeners.submit({ preventDefault() {} });
    assert.equal(h.elements.get('saldoDisponibleTexto').textContent, '0.00');
    assert.equal(h.elements.get('btnTransferir').disabled, false);
});

test('escrituras incluyen el token CSRF y mantienen las cookies en el mismo origen', async () => {
    const h = harness(response(204));
    await h.context.LSApi.json('/api/v1/user/1', { method: 'PATCH' });
    assert.equal(h.calls[0].options.headers.get('X-CSRF-TOKEN'), 'token-de-prueba');
    assert.equal(h.calls[0].options.credentials, 'same-origin');
});
test('no envía tokens ni solicitudes a un origen externo', async () => {
    const h = harness(response(204));
    await assert.rejects(h.context.LSApi.json('https://evil.example/api', { method: 'POST' }), /mismo origen/);
    assert.equal(h.calls.length, 0);
});
test('sin token CSRF no inicia una escritura', async () => {
    const h = harness(response(204));
    h.metas.clear();
    await assert.rejects(h.context.LSApi.json('/api', { method: 'POST' }), /Recarga la página/);
    assert.equal(h.calls.length, 0);
});

function portalDestino(res) {
    const h = harness(res);
    for (const id of ['formTransferencia', 'resultadoTransferencia', 'btnTransferir', 'trCliente', 'trMonto',
        'trCategoria', 'trPais', 'destinatarioNombre', 'cuentaDestino', 'bancoDestino', 'estadoDestinatario', 'contactoGuardado']) {
        h.elements.set(id, new Element());
    }
    h.elements.get('contactoGuardado').options = [];
    h.elements.get('bancoDestino').value = 'lambda';
    h.elements.get('cuentaDestino').value = '0011-2026-0000000102';
    vm.runInContext(fs.readFileSync(path.join(scripts, 'operacion.js'), 'utf8'), h.context);
    h.ready.forEach(action => action());
    return h;
}
const terminarConsulta = () => new Promise(resolve => setImmediate(resolve));
test('escribir una cuenta interna consulta el titular y evita editar su nombre', async () => {
    const h = portalDestino(response(200, { nombre: 'Carlos Rojas', numeroCuenta: '0011-2026-0000000102' }));
    h.elements.get('cuentaDestino').listeners.blur();
    await terminarConsulta();
    assert.equal(h.elements.get('destinatarioNombre').value, 'Carlos Rojas');
    assert.equal(h.elements.get('destinatarioNombre').readOnly, true);
    assert.match(h.calls[0].url, /account\/destination\?numero=/);
    h.elements.get('cuentaDestino').listeners.input();
    assert.equal(h.elements.get('destinatarioNombre').value, '');
});
test('cuenta inexistente impide enviar la operación y elimina nombres anteriores', async () => {
    const h = portalDestino(response(404));
    h.elements.get('destinatarioNombre').value = 'Anterior';
    await h.elements.get('formTransferencia').listeners.submit({ preventDefault() {} });
    assert.equal(h.elements.get('destinatarioNombre').value, '');
    assert.ok(h.calls.every(c => c.options.method !== 'POST'));
    assert.equal(h.elements.get('btnTransferir').disabled, false);
});
test('otro banco permite nombre manual e informa que no verifica titularidad', async () => {
    const h = portalDestino(response(200));
    h.elements.get('bancoDestino').value = 'bcp';
    h.elements.get('bancoDestino').listeners.change();
    await terminarConsulta();
    assert.equal(h.elements.get('destinatarioNombre').readOnly, false);
    assert.match(h.elements.get('estadoDestinatario').textContent, /No podemos consultar/);
    assert.equal(h.calls.length, 0);
});
