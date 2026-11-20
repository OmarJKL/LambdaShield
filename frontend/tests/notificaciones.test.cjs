const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
class Element {
    constructor() { this.children = []; this.listeners = {}; this.dataset = {}; this.attributes = {}; this.classList = { contains: () => false }; }
    append(...nodes) { this.children.push(...nodes); }
    replaceChildren(...nodes) { this.children = nodes; }
    setAttribute(key, value) { this.attributes[key] = value; }
    addEventListener(event, fn) { this.listeners[event] = fn; }
    querySelector(selector) { return this.children.find(n => n.dataset.notificacionId === selector.match(/"(.*?)"/)[1]); }
    focus() { this.focused = true; }
}
const settle = () => new Promise(resolve => setImmediate(resolve));
async function harness(count = 2, present = true, fail = false) {
    const ids = ['centroNotificaciones', 'btnNotificaciones', 'contadorNotificaciones', 'listaNotificaciones', 'resumenNotificaciones', 'estadoNotificaciones', 'btnLeerNotificaciones', 'btnMasNotificaciones'];
    const elements = Object.fromEntries(ids.map(id => [id, new Element()]));
    const data = Array.from({ length: count }, (_, i) => ({ id: i + 1, tipo: 'BLOQUEADA', titulo: '<script>alert(1)</script>', mensaje: 'Saldo protegido', fecha: new Date().toISOString(), leida: false }));
    const calls = [];
    let interceptar;
    const context = vm.createContext({ Intl, Date, Set, window: {}, setInterval() {},
        document: { getElementById: id => present ? elements[id] : null, createElement: () => new Element(), addEventListener() {}, hidden: false },
        LSApi: { reportarError() {}, json: async (url, options) => {
            calls.push({ url, options });
            const respuesta = interceptar?.(url, options);
            if (respuesta !== undefined) return respuesta;
            if (fail) throw new Error('sin conexión');
            if (options?.method === 'PATCH') {
                const id = Number(url.split('/')[4]);
                data.forEach(n => { if (url.endsWith('leer-todas') || n.id === id) n.leida = true; });
                return null;
            }
            const page = Number(url.split('=')[1]);
            return { items: structuredClone(data.slice(page * 20, (page + 1) * 20)), noLeidas: data.filter(n => !n.leida).length };
        } }
    });
    vm.runInContext(fs.readFileSync(path.join(__dirname, '../src/main/resources/static/js/notificaciones.js'), 'utf8'), context);
    await settle();
    return { e: elements, calls, context, data, interceptar: fn => { interceptar = fn; } };
}
test('campana muestra pendientes y trata títulos como texto', async () => {
    const { e } = await harness();
    assert.equal(e.contadorNotificaciones.textContent, '2');
    assert.equal(e.listaNotificaciones.children[0].children[1].children[0].textContent, '<script>alert(1)</script>');
    assert.equal(e.listaNotificaciones.children[0].children[1].children[0].children.length, 0);
});
test('abrir un aviso lo marca leído una sola vez y conserva el foco', async () => {
    const { e, calls } = await harness();
    await e.listaNotificaciones.children[0].listeners.click();
    assert.equal(e.contadorNotificaciones.textContent, '1');
    assert.equal(e.listaNotificaciones.children[0].focused, true);
    await e.listaNotificaciones.children[0].listeners.click();
    assert.equal(calls.filter(c => c.options?.method === 'PATCH').length, 1);
});
test('marcar todas oculta contador y desactiva el botón', async () => {
    const { e } = await harness();
    await e.btnLeerNotificaciones.listeners.click();
    assert.equal(e.contadorNotificaciones.hidden, true);
    assert.equal(e.btnLeerNotificaciones.disabled, true);
    assert.ok(e.listaNotificaciones.children.every(n => !n.className.includes('sin-leer')));
});
test('carga avisos anteriores por páginas sin perder los primeros', async () => {
    const { e } = await harness(21);
    assert.equal(e.listaNotificaciones.children.length, 20);
    await e.btnMasNotificaciones.listeners.click();
    assert.equal(e.listaNotificaciones.children.length, 21);
    assert.equal(e.btnMasNotificaciones.hidden, true);
});
test('sin campana no consulta API y los errores de carga permiten reintentar', async () => {
    assert.equal((await harness(2, false)).calls.length, 0);
    const { e } = await harness(2, true, true);
    assert.match(e.estadoNotificaciones.textContent, /Vuelve a abrir/);
    assert.equal(e.btnMasNotificaciones.disabled, false);
});
test('serializa lecturas y difiere actualizaciones mientras una lectura está pendiente', async () => {
    const h = await harness(3);
    let terminar;
    h.interceptar((url, options) => options?.method === 'PATCH' ? new Promise(resolve => { terminar = resolve; }) : undefined);
    const lectura = h.e.listaNotificaciones.children[0].listeners.click();
    assert.equal(h.e.btnLeerNotificaciones.disabled, true);
    await h.e.btnLeerNotificaciones.listeners.click();
    await h.context.window.LSNotifications.refresh();
    assert.equal(h.calls.length, 2);
    h.data[0].leida = true;
    terminar(null);
    await lectura; await settle();
    assert.equal(h.e.contadorNotificaciones.textContent, '2');
    assert.equal(h.calls.filter(c => c.options?.method === 'PATCH').length, 1);
});
test('una consulta anterior a la lectura no restaura un contador obsoleto', async () => {
    const h = await harness(3);
    let terminar;
    const anterior = structuredClone(h.data);
    h.interceptar((url, options) => {
        if (!options) { h.interceptar(null); return new Promise(resolve => { terminar = resolve; }); }
    });
    const refresco = h.context.window.LSNotifications.refresh();
    await h.e.listaNotificaciones.children[0].listeners.click();
    terminar({ items: anterior, noLeidas: 3 });
    await refresco; await settle();
    assert.equal(h.e.contadorNotificaciones.textContent, '2');
    assert.ok(!h.e.listaNotificaciones.children[0].className.includes('sin-leer'));
});
