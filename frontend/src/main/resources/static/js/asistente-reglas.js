(() => {
    'use strict';
    const raiz = document.getElementById('asistenteReglas');
    if (!raiz) return;
    const $ = id => document.getElementById(id);
    const campos = {
        MONTO: { nombre: 'Monto (S/)', min: 0, max: '9999999999999999.99', paso: '0.01' },
        RATIO_PROMEDIO: { nombre: 'Monto / promedio del cliente', min: 0, max: 1000000, paso: '0.01' },
        HORA: { nombre: 'Hora del servidor (0–23)', min: 0, max: 23, paso: '1' },
        OPERACIONES_ULTIMA_HORA: { nombre: 'Operaciones previas en la última hora', min: 0, max: 1000, paso: '1' },
        PAIS: { nombre: 'País declarado', opciones: { local: 'Local', regional: 'Regional', alto_riesgo: 'Alto riesgo' } },
        DISPOSITIVO: { nombre: 'Dispositivo', opciones: { reconocido: 'Reconocido', nuevo: 'Nuevo' } },
        IP: { nombre: 'IP', opciones: { conocida: 'Conocida', nueva: 'Nueva' } },
        BENEFICIARIO: { nombre: 'Beneficiario', opciones: { recurrente: 'Recurrente', nuevo: 'Nuevo' } }
    };
    const operadores = { MAYOR: 'Mayor que (>)', MAYOR_IGUAL: 'Mayor o igual (≥)', MENOR: 'Menor que (<)', MENOR_IGUAL: 'Menor o igual (≤)', IGUAL: 'Igual a (=)' };
    let secuencia = 0;
    let revision = null;
    let ocupado = false;
    function nodo(tag, clase, texto) {
        const el = document.createElement(tag);
        el.className = clase;
        if (texto != null) el.textContent = texto;
        return el;
    }
    function opciones(select, valores) {
        select.replaceChildren(...Object.entries(valores).map(([value, label]) => {
            const option = nodo('option', '', label); option.value = value; return option;
        }));
    }
    function invalidar() {
        revision = null;
        $('iaExportar').disabled = true;
        $('iaResultado').hidden = true;
        $('iaMensaje').textContent = 'Borrador editado. Solicita una revisión de esta definición.';
    }
    function actualizarBotones() {
        const filas = $('iaCondiciones').children;
        $('iaAgregar').disabled = filas.length >= 8 || ocupado;
        Array.from(filas).forEach(f => { f.querySelector('button').disabled = filas.length <= 1 || ocupado; });
    }
    function agregar(campoInicial = 'MONTO', valorInicial = '1000') {
        if ($('iaCondiciones').children.length >= 8) return;
        const fila = nodo('div', 'row g-2 align-items-end');
        const grupo = (label, elemento) => {
            const col = nodo('div', 'col-md');
            const etiqueta = nodo('label', 'form-label small mb-1', label);
            elemento.id = 'iaCondicion' + (++secuencia);
            etiqueta.htmlFor = elemento.id;
            col.append(etiqueta, elemento); fila.append(col);
        };
        const campo = nodo('select', 'form-select ia-campo');
        opciones(campo, Object.fromEntries(Object.entries(campos).map(([id, c]) => [id, c.nombre])));
        campo.value = campoInicial;
        const operador = nodo('select', 'form-select ia-operador');
        const contenedorValor = nodo('div', '');
        grupo('Dato', campo); grupo('Operador', operador); grupo('Valor', contenedorValor);
        function configurar() {
            const config = campos[campo.value];
            opciones(operador, config.opciones ? { IGUAL: operadores.IGUAL } : operadores);
            const valor = nodo(config.opciones ? 'select' : 'input', 'form-control ia-valor');
            valor.id = 'iaValor' + (++secuencia);
            valor.setAttribute('aria-label', 'Valor de ' + config.nombre);
            valor.required = true;
            if (config.opciones) { valor.className = 'form-select ia-valor'; opciones(valor, config.opciones); }
            else { valor.type = 'number'; valor.min = config.min; valor.max = config.max; valor.step = config.paso; valor.value = '0'; }
            contenedorValor.replaceChildren(valor);
        }
        campo.addEventListener('change', () => { configurar(); invalidar(); });
        configurar();
        fila.querySelector('.ia-valor').value = valorInicial;
        const borrar = nodo('button', 'btn btn-secundario', 'Quitar');
        borrar.type = 'button'; borrar.setAttribute('aria-label', 'Quitar condición');
        borrar.addEventListener('click', () => { fila.remove(); invalidar(); actualizarBotones(); });
        const acciones = nodo('div', 'col-md-auto'); acciones.append(borrar); fila.append(acciones);
        $('iaCondiciones').append(fila); actualizarBotones();
    }
    function definicion() {
        return {
            nombre: $('iaNombre').value.trim(), objetivo: $('iaObjetivo').value.trim(),
            puntos: Number($('iaPuntos').value), combinacion: $('iaCombinacion').value,
            condiciones: Array.from($('iaCondiciones').children).map(f => ({
                campo: f.querySelector('.ia-campo').value, operador: f.querySelector('.ia-operador').value,
                valor: f.querySelector('.ia-valor').value
            }))
        };
    }
    function mostrar(datos) {
        const resultado = $('iaResultado');
        const seccion = (titulo, contenido) => {
            const bloque = nodo('section', 'mb-3'); bloque.append(nodo('h5', 'fs-6 fw-bold', titulo));
            if (Array.isArray(contenido)) {
                const lista = nodo('ul', 'small mb-0');
                contenido.forEach(t => lista.append(nodo('li', '', t)));
                if (!contenido.length) lista.append(nodo('li', '', 'Sin observaciones.'));
                bloque.append(lista);
            } else if (typeof contenido === 'string') bloque.append(nodo('p', 'small mb-0', contenido));
            else bloque.append(contenido);
            return bloque;
        };
        const original = nodo('pre', 'small p-3 rounded border text-wrap');
        original.textContent = JSON.stringify(datos.definicion, null, 2);
        resultado.replaceChildren(seccion('Definición original del administrador', original),
            seccion('Interpretación de IA — revisa que corresponda a tu intención', datos.revision.interpretacion),
            seccion('Advertencias', datos.revision.advertencias), seccion('Sugerencias adicionales', datos.revision.sugerencias),
            seccion('Casos de prueba sugeridos — pendientes de ejecutar', datos.revision.pruebas));
        resultado.hidden = false;
    }
    $('iaAgregar').addEventListener('click', () => { agregar(); invalidar(); });
    $('iaExportar').addEventListener('click', () => {
        if (!revision) return;
        const contenido = { esquema: 'lambda-shield-regla-borrador-v1', fecha: new Date().toISOString(),
            estado: 'BORRADOR_NO_PUBLICADO', ...revision };
        const url = URL.createObjectURL(new Blob([JSON.stringify(contenido, null, 2)], { type: 'application/json' }));
        const enlace = document.createElement('a');
        enlace.href = url; enlace.download = 'regla-borrador.json'; enlace.click();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
    });
    $('formAsistenteReglas').addEventListener('input', invalidar);
    $('formAsistenteReglas').addEventListener('change', invalidar);
    $('formAsistenteReglas').addEventListener('submit', async e => {
        e.preventDefault();
        if (ocupado) return;
        const original = definicion();
        ocupado = true; $('iaRevisar').disabled = true;
        invalidar(); actualizarBotones(); $('iaMensaje').textContent = 'Consultando a OpenRouter…';
        try {
            const datos = await LSApi.json('/api/v1/rule/assistant/review', { method: 'POST',
                headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(original) });
            if (JSON.stringify(original) !== JSON.stringify(definicion())) {
                $('iaMensaje').textContent = 'Cambiaste el formulario durante la consulta. Revisa la nueva definición.';
                return;
            }
            revision = datos;
            mostrar(datos); $('iaExportar').disabled = false;
            $('iaMensaje').textContent = 'Revisión obtenida. El borrador no está publicado ni se conservó en el servidor; puedes descargarlo.';
        } catch (error) {
            $('iaMensaje').textContent = error.gestionado ? 'No se pudo completar la revisión.' : error.message;
        } finally { ocupado = false; $('iaRevisar').disabled = false; actualizarBotones(); }
    });
    agregar('RATIO_PROMEDIO', '3'); agregar('DISPOSITIVO', 'nuevo');
    LSApi.json('/api/v1/rule/assistant/status').then(datos => {
        $('estadoAsistente').textContent = datos.disponible ? 'Configurado' : 'Configuración pendiente';
        $('iaRevisar').disabled = !datos.disponible;
        if (!datos.disponible) $('iaMensaje').textContent = datos.detalle || 'Configura LLM_ENABLED, LLM_API_KEY, LLM_BASE_URL y LLM_MODEL en .env y reinicia la aplicación.';
    }).catch(error => { $('estadoAsistente').textContent = 'No disponible'; $('iaMensaje').textContent = error.message; });
})();
