const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
class Element {
    constructor(tag = 'div') { this.tag = tag; this.children = []; this.listeners = {}; this.attributes = {}; this.className = ''; this._value = undefined; }
    get value() { return this._value ?? (this.tag === 'select' ? this.children[0]?.value : '') ?? ''; }
    set value(value) { this._value = String(value); }
    append(...nodes) { nodes.forEach(n => { n.parent = this; this.children.push(n); }); }
    replaceChildren(...nodes) { this.children = []; if (this.tag === 'select') this._value = undefined; this.append(...nodes); }
    addEventListener(name, fn) { this.listeners[name] = fn; }
    click() { this.clicked = true; }
    reportValidity() { return true; }
    setAttribute(key, value) { this.attributes[key] = value; }
    remove() { this.parent.children = this.parent.children.filter(n => n !== this); }
    querySelector(selector) {
        for (const child of this.children) {
            if (selector.startsWith('.') ? child.className.split(' ').includes(selector.slice(1)) : child.tag === selector) return child;
            const found = child.querySelector(selector); if (found) return found;
        }
        return null;
    }
}
const settle = () => new Promise(resolve => setImmediate(resolve));
async function harness({ present = true, configured = true, review, guardar } = {}) {
    const ids = ['asistenteReglas', 'estadoAsistente', 'formAsistenteReglas', 'iaNombre', 'iaObjetivo', 'iaPuntos', 'iaCombinacion',
        'iaCondiciones', 'iaAgregar', 'iaRevisar', 'iaExportar', 'iaMensaje', 'iaResultado', 'iaGuardar', 'iaProbar', 'iaParticipacion'];
    const e = Object.fromEntries(ids.map(id => [id, new Element()]));
    e.iaPuntos.value = '25'; e.iaCombinacion.value = 'TODAS'; e.iaNombre.value = 'Regla nueva';
    const calls = [];
    const downloads = [], destinos = [], almacen = {};
    const filasCatalogo = [{ dataset: { activa: 'true', peso: '50' } }, { dataset: { activa: 'true', peso: '25' } }, { dataset: { activa: 'false', peso: '90' } }];
    const context = vm.createContext({ Blob, URL: { createObjectURL(blob) { downloads.push(blob); return 'blob:prueba'; }, revokeObjectURL() {} }, setTimeout() {},
        document: { getElementById: id => present ? e[id] : null, createElement: tag => new Element(tag), querySelectorAll: () => filasCatalogo },
        window: { location: { assign: url => destinos.push(url) } },
        sessionStorage: { setItem: (k, v) => { almacen[k] = v; } },
        LSApi: { json: async (url, options) => {
            calls.push({ url, options });
            if (url.endsWith('/status')) return { disponible: configured };
            if (url === '/api/v1/rule') return guardar ? guardar(options) : { codigo: 'RC001' };
            if (review) return review(url, options);
            return { definicion: JSON.parse(options.body), revision: { interpretacion: '<script>no ejecutar</script>', advertencias: [], sugerencias: ['Revisar el promedio'], pruebas: ['Caso 1', 'Caso 2'] } };
        } }
    });
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../src/main/resources/static/js/asistente-reglas.js'), 'utf8'), context);
    await settle();
    return { e, calls, downloads, destinos, almacen };
}
test('sin formulario no consulta; sin configuración mantiene revisión deshabilitada', async () => {
    assert.equal((await harness({ present: false })).calls.length, 0);
    const { e } = await harness({ configured: false });
    assert.equal(e.iaRevisar.disabled, true);
    assert.match(e.iaMensaje.textContent, /Configura/);
});
test('envía condiciones tipadas y conserva definición separada de sugerencias', async () => {
    const { e, calls, downloads } = await harness();
    await e.formAsistenteReglas.listeners.submit({ preventDefault() {} });
    const sent = JSON.parse(calls[1].options.body);
    assert.deepEqual(sent.condiciones, [{ campo: 'RATIO_PROMEDIO', operador: 'MAYOR', valor: '3' }, { campo: 'DISPOSITIVO', operador: 'IGUAL', valor: 'nuevo' }]);
    assert.equal(e.iaResultado.hidden, false);
    assert.equal(e.iaResultado.children[1].children[1].textContent, '<script>no ejecutar</script>');
    assert.equal(e.iaResultado.children[1].children[1].children.length, 0);
    assert.equal(e.iaExportar.disabled, false);
    e.iaExportar.listeners.click();
    const borrador = JSON.parse(await downloads[0].text());
    assert.equal(borrador.estado, 'BORRADOR_NO_PUBLICADO');
    assert.deepEqual(borrador.definicion, sent);
    e.formAsistenteReglas.listeners.input();
    assert.equal(e.iaExportar.disabled, true);
    assert.equal(e.iaResultado.hidden, true);
});
test('limita a ocho condiciones y conserva al menos una', async () => {
    const { e } = await harness();
    for (let i = 0; i < 10; i++) e.iaAgregar.listeners.click();
    assert.equal(e.iaCondiciones.children.length, 8);
    assert.equal(e.iaAgregar.disabled, true);
    while (e.iaCondiciones.children.length > 1) e.iaCondiciones.children[0].querySelector('button').listeners.click();
    assert.equal(e.iaCondiciones.children[0].querySelector('button').disabled, true);
});
test('un error del proveedor se muestra y permite volver a intentar', async () => {
    const { e } = await harness({ review: () => { throw new Error('Cuota agotada'); } });
    await e.formAsistenteReglas.listeners.submit({ preventDefault() {} });
    assert.equal(e.iaMensaje.textContent, 'Cuota agotada');
    assert.equal(e.iaRevisar.disabled, false);
    assert.equal(e.iaExportar.disabled, true);
});
test('muestra la participación que tendría la regla frente a las activas', async () => {
    const { e } = await harness();
    assert.match(e.iaParticipacion.textContent, /hasta 25\.0 %/);
    e.iaPuntos.value = '75'; e.iaPuntos.listeners.input();
    assert.match(e.iaParticipacion.textContent, /hasta 50\.0 %/);
});
test('guardar publica la definición original aunque OpenRouter no esté configurado', async () => {
    const { e, calls, destinos } = await harness({ configured: false });
    await e.iaGuardar.listeners.click();
    const envio = calls.find(c => c.url === '/api/v1/rule');
    assert.equal(envio.options.method, 'POST');
    assert.equal(JSON.parse(envio.options.body).condiciones.length, 2);
    assert.deepEqual(destinos, ['/reglas?creada=RC001']);
});
test('un rechazo al guardar muestra el motivo y probar envía el borrador al campo de desarrolladores', async () => {
    const { e, destinos, almacen } = await harness({ guardar: () => { throw new Error('Revisa nombre, puntos y condiciones'); } });
    await e.iaGuardar.listeners.click();
    assert.equal(e.iaMensaje.textContent, 'Revisa nombre, puntos y condiciones'); assert.equal(destinos.length, 0);
    e.iaProbar.listeners.click();
    assert.equal(JSON.parse(almacen['ls.reglaBorrador']).nombre, 'Regla nueva');
    assert.deepEqual(destinos, ['/simulador?borrador=1']);
});
