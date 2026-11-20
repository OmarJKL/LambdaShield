document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('formTransferencia');
    const resultadoDiv = document.getElementById('resultadoTransferencia');
    const btnTransferir = document.getElementById('btnTransferir');

    if (!form) return;

    const contactoGuardado = document.getElementById('contactoGuardado');
    const cuentaDestino = document.getElementById('cuentaDestino');
    const nombreDestino = document.getElementById('destinatarioNombre');
    const bancoDestino = document.getElementById('bancoDestino');
    const estadoDestino = document.getElementById('estadoDestinatario');
    const bancos = { 'Lambda Shield': 'lambda', BCP: 'bcp', BBVA: 'bbva', Interbank: 'interbank', Scotiabank: 'scotiabank' };
    let consulta = 0;
    let temporizador;
    function estado(texto) { if (estadoDestino) estadoDestino.textContent = texto; }
    function limpiarTitular() {
        consulta++;
        clearTimeout(temporizador);
        nombreDestino.value = '';
        nombreDestino.readOnly = bancoDestino?.value === 'lambda';
        nombreDestino.placeholder = nombreDestino.readOnly ? 'Se completa al ingresar la cuenta' : 'Nombre del destinatario';
        cuentaDestino.setCustomValidity?.('');
    }
    async function resolverTitular() {
        if (!bancoDestino) return true;
        const version = ++consulta;
        const numero = cuentaDestino.value.replace(/ /g, '').trim();
        if (!numero) { estado('Ingresa la cuenta para consultar su titular.'); return false; }
        if (!/^[0-9 -]{6,50}$/.test(numero)) { estado('Revisa el formato del número de cuenta.'); return false; }
        const contacto = Array.from(contactoGuardado?.options || []).find(o => o.value
            && o.dataset.cuenta.replace(/ /g, '') === numero && bancos[o.dataset.banco] === bancoDestino.value);
        if (contacto && bancoDestino.value !== 'lambda') {
            nombreDestino.value = contacto.dataset.nombre;
            nombreDestino.readOnly = true;
            contactoGuardado.value = contacto.value;
            estado('Destinatario de tu agenda. Confirma sus datos antes de continuar.');
            return true;
        }
        if (bancoDestino.value !== 'lambda') {
            nombreDestino.readOnly = false;
            estado('No podemos consultar titulares de otros bancos. Escribe y confirma el nombre del destinatario.');
            return Boolean(nombreDestino.value.trim());
        }
        nombreDestino.readOnly = true;
        estado('Consultando titular…');
        try {
            const res = await LSApi.fetch('/api/v1/account/destination?numero=' + encodeURIComponent(numero));
            if (version !== consulta) return false;
            if (!res.ok) throw new Error(res.status === 404
                ? 'Cuenta no disponible. Revisa el número; no puede ser tu propia cuenta.' : 'No se pudo consultar el titular. Inténtalo otra vez.');
            const destino = await res.json();
            if (version !== consulta) return false;
            nombreDestino.value = destino.nombre;
            cuentaDestino.value = destino.numeroCuenta;
            cuentaDestino.setCustomValidity?.('');
            if (contactoGuardado) contactoGuardado.value = contacto?.value || '';
            estado('Titular confirmado en Lambda Shield.');
            return true;
        } catch (error) {
            if (version !== consulta) return false;
            nombreDestino.value = '';
            estado(error.message || 'No se pudo consultar el titular.');
            cuentaDestino.setCustomValidity?.('Consulta una cuenta de destino válida antes de continuar.');
            return false;
        }
    }
    cuentaDestino.addEventListener('input', () => {
        limpiarTitular();
        if (contactoGuardado) contactoGuardado.value = '';
        estado('Ingresa la cuenta para consultar su titular.');
        temporizador = setTimeout(resolverTitular, 350);
    });
    cuentaDestino.addEventListener('blur', () => { clearTimeout(temporizador); resolverTitular(); });
    bancoDestino?.addEventListener('change', () => { limpiarTitular(); resolverTitular(); });
    contactoGuardado?.addEventListener('change', () => {
        const contacto = contactoGuardado.selectedOptions[0];
        if (!contacto?.value) return;
        document.getElementById('destinatarioNombre').value = contacto.dataset.nombre;
        document.getElementById('cuentaDestino').value = contacto.dataset.cuenta;
        document.getElementById('bancoDestino').value = bancos[contacto.dataset.banco] || 'lambda';
        cuentaDestino.setCustomValidity?.('');
        clearTimeout(temporizador);
        resolverTitular();
        document.getElementById('trMonto').focus();
    });

    function crearLineaRecibo(etiqueta, valor) {
        const linea = document.createElement('div');
        const strong = document.createElement('strong');
        strong.textContent = etiqueta;
        linea.append(strong, document.createTextNode(' ' + valor));
        return linea;
    }

    function pintarReciboAprobado(data) {
        const alerta = document.createElement('div');
        alerta.className = 'alert alert-success p-3 rounded';
        alerta.style.borderLeft = '5px solid #146c2e';

        const cabecera = document.createElement('div');
        cabecera.className = 'd-flex align-items-center gap-2 mb-2';
        const icono = document.createElement('i');
        icono.className = 'bi bi-check-circle-fill fs-4 text-success';
        const titulo = document.createElement('strong');
        titulo.className = 'fs-5';
        titulo.textContent = 'Operación aprobada';
        cabecera.append(icono, titulo);

        const mensaje = document.createElement('p');
        mensaje.className = 'mb-2 small text-secondary';
        mensaje.textContent = 'La operación fue aprobada y el cargo se registró en tu cuenta.';

        const detalle = document.createElement('div');
        detalle.className = 'p-2 rounded bg-white border small dato-mono text-dark';
        detalle.append(
            crearLineaRecibo('N° Operación:', data.id),
            crearLineaRecibo('Monto:', 'S/ ' + Number(data.monto).toFixed(2)),
            crearLineaRecibo('Fecha y Hora:', data.fecha),
            crearLineaRecibo('Estado:', data.estado)
        );

        alerta.append(cabecera, mensaje, detalle);
        resultadoDiv.replaceChildren(alerta);
    }

    function pintarReciboRevision(data) {
        const esBloqueo = data.nivel === 'critico';
        const alerta = document.createElement('div');
        alerta.className = 'alert alert-danger p-3 rounded';
        alerta.style.borderLeft = '5px solid var(--utp-rojo)';

        const cabecera = document.createElement('div');
        cabecera.className = 'd-flex align-items-center gap-2 mb-2';
        const icono = document.createElement('i');
        icono.className = 'bi bi-shield-x fs-4 text-danger';
        const titulo = document.createElement('strong');
        titulo.className = 'fs-5';
        titulo.textContent = esBloqueo ? 'Operación Bloqueada por Seguridad' : 'Operación en Validación Preventiva';
        cabecera.append(icono, titulo);

        const mensaje = document.createElement('p');
        mensaje.className = 'mb-2 small';
        mensaje.style.color = 'var(--utp-rojo-oscuro)';
        mensaje.textContent = esBloqueo
            ? 'El motor antifraude detectó indicadores de riesgo crítico y detuvo la operación. El dinero no fue transferido.'
            : 'La operación quedó retenida por indicadores de riesgo. Tu saldo no fue descontado; puedes consultar el aviso en la campana.';

        const detalle = document.createElement('div');
        detalle.className = 'p-2 rounded bg-white border small dato-mono text-dark';
        detalle.append(
            crearLineaRecibo('Código de Incidencia:', data.id),
            crearLineaRecibo('Estado:', data.estado),
            crearLineaRecibo('Puntaje de riesgo:', data.puntaje + ' pts')
        );

        const causas = (data.factores || []).filter(f => f.puntaje > 0);
        if (causas.length) {
            const bloque = document.createElement('div');
            bloque.className = 'mt-2 small';
            const tituloCausas = document.createElement('strong');
            tituloCausas.textContent = 'Causas detectadas:';
            bloque.appendChild(tituloCausas);
            const lista = document.createElement('ul');
            lista.className = 'mb-0 mt-1';
            causas.forEach(factor => {
                const li = document.createElement('li');
                li.textContent = factor.etiqueta + ' (' + factor.puntaje + ' / ' + factor.max + ' pts): ' + factor.motivo;
                lista.appendChild(li);
            });
            bloque.appendChild(lista);
            detalle.appendChild(bloque);
        }

        const soporte = document.createElement('p');
        soporte.className = 'mt-2 mb-0 small';
        soporte.textContent = 'El detalle de este evento queda guardado en tu campana de notificaciones.';
        detalle.appendChild(soporte);

        alerta.append(cabecera, mensaje, detalle);
        resultadoDiv.replaceChildren(alerta);
    }

    function pintarError(mensaje) {
        const alerta = document.createElement('div');
        alerta.className = 'alert alert-danger p-3 rounded';
        alerta.style.borderLeft = '5px solid var(--utp-rojo)';
        const cabecera = document.createElement('div');
        cabecera.className = 'd-flex align-items-center gap-2 mb-2';
        const icono = document.createElement('i');
        icono.className = 'bi bi-exclamation-triangle-fill fs-4 text-danger';
        const titulo = document.createElement('strong');
        titulo.className = 'fs-5';
        titulo.textContent = 'No se pudo completar la operación';
        cabecera.append(icono, titulo);
        const texto = document.createElement('p');
        texto.className = 'mb-0 small';
        texto.textContent = mensaje;
        alerta.append(cabecera, texto);
        resultadoDiv.classList.remove('d-none');
        resultadoDiv.replaceChildren(alerta);
    }

    form.addEventListener('submit', async (e) => {
        e.preventDefault();

        if (btnTransferir.disabled) return;

        btnTransferir.disabled = true;
        const spinner = document.createElement('span');
        spinner.className = 'spinner-border spinner-border-sm';
        btnTransferir.replaceChildren(spinner, document.createTextNode(' Verificando con motor antifraude...'));

        const payload = {
            cliente: document.getElementById('trCliente').value,
            monto: Number(document.getElementById('trMonto').value) || 0,
            categoria: document.getElementById('trCategoria').value,
            // Valores de relleno válidos: el servidor deriva hora, frecuencia,
            // dispositivo e IP para usuarios con rol CLIENTE.
            hora: new Date().getHours(),
            pais: document.getElementById('trPais').value,
            dispositivo: 'reconocido',
            operacionesUltimaHora: 1,
            // El servidor deriva hora, frecuencia, dispositivo, IP y el tipo de
            // beneficiario a partir del historial real del cliente.
            beneficiarioNombre: document.getElementById('destinatarioNombre').value.trim(),
            numeroCuentaDestino: document.getElementById('cuentaDestino').value.trim(),
            bancoDestino: document.getElementById('bancoDestino')?.value || 'lambda',
            ip: 'conocida'
        };

        try {
            if (!await resolverTitular()) {
                pintarError('Revisa la cuenta y el titular antes de continuar.');
                return;
            }
            payload.beneficiarioNombre = nombreDestino.value.trim();
            payload.numeroCuentaDestino = cuentaDestino.value.trim();
            const res = await LSApi.fetch('/api/v1/evaluation', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });

            if (!res.ok) {
                pintarError(await LSApi.mensajeError(res, 'No se pudo procesar la transferencia.'));
                return;
            }
            const data = await res.json();
            window.LSNotifications?.refresh();
            const saldoEl = document.getElementById('saldoDisponibleTexto');
            if (saldoEl) saldoEl.textContent = LSUi.moneda(data.saldoDisponible);
            resultadoDiv.classList.remove('d-none');

            if (data.nivel === 'bajo' || data.nivel === 'medio') {
                pintarReciboAprobado(data);
            } else {
                pintarReciboRevision(data);
            }

            // 1. Quitar fila vacía si existe
            const tablaOperaciones = document.getElementById('tablaMisOperaciones');
            if (tablaOperaciones) {
                const trVacia = tablaOperaciones.querySelector('.tr-vacia');
                if (trVacia) trVacia.remove();

                // 2. Crear nueva fila dinámica

                const diccCat = {
                    servicios: 'Servicios',
                    retail: 'Compras / Retail',
                    restaurante: 'Restaurante / Consumo',
                    electronica: 'Electrónica',
                    casino: 'Apuestas / Juegos',
                    cripto: 'Inversión / Cripto'
                };
                const catTexto = diccCat[payload.categoria] || payload.categoria;

                const tdId = document.createElement('td');
                tdId.className = 'dato-mono fw-bold';
                tdId.textContent = data.id;

                const tdFecha = document.createElement('td');
                tdFecha.textContent = data.fecha;

                const tdMonto = document.createElement('td');
                tdMonto.className = 'dato-mono fw-bold';
                tdMonto.textContent = 'S/ ' + Number(data.monto).toFixed(2);

                const tdCategoria = document.createElement('td');
                tdCategoria.textContent = catTexto;

                const tdEstado = document.createElement('td');
                const badge = document.createElement('span');
                badge.className = 'etiqueta-nivel ' + LSUi.claseNivel(data.nivel);
                const iconoBadge = document.createElement('i');
                iconoBadge.className = 'bi ' + data.iconoEstado;
                badge.append(iconoBadge, document.createTextNode(' ' + data.estado));
                tdEstado.appendChild(badge);

                const nuevaFila = document.createElement('tr');
                nuevaFila.style.backgroundColor = 'rgba(200, 16, 46, 0.08)';
                nuevaFila.style.transition = 'background-color 1.5s ease';
                nuevaFila.append(tdId, tdFecha, tdMonto, tdCategoria, tdEstado);
                tablaOperaciones.prepend(nuevaFila);
                setTimeout(() => nuevaFila.style.backgroundColor = 'transparent', 1500);


            }

            if (contactoGuardado) contactoGuardado.value = '';
            document.getElementById('destinatarioNombre').value = '';
            document.getElementById('cuentaDestino').value = '';
            limpiarTitular();
            estado('Ingresa la cuenta para consultar su titular.');
            document.getElementById('trMonto').value = '';

        } catch (err) {
            if (!err.gestionado) pintarError(err.message || 'No se pudo conectar con el servidor.');
        } finally {
            btnTransferir.disabled = false;
            const iconoBoton = document.createElement('i');
            iconoBoton.className = 'bi bi-shield-lock';
            btnTransferir.replaceChildren(iconoBoton, document.createTextNode(' Confirmar y Realizar Transferencia'));
        }
    });
});
