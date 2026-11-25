(() => {
    'use strict';

    const tabla = document.getElementById('tablaUsuarios');
    const totalBadge = document.getElementById('totalUsuarios');
    const usuarioActual = document.body.dataset.usuario || '';

    let usuarios = [];
    let editandoId = null;
    let eliminandoId = null;

    const escapeHtml = LSUi.escapeHtml;

    function validarClave(campo) {
        const clave = campo.value;
        const valida = (!campo.required && !clave) || (clave.trim().length > 0
            && [...clave].length >= 8 && new TextEncoder().encode(clave).length <= 72);
        campo.setCustomValidity(valida ? '' : 'La clave debe tener al menos 8 caracteres y como máximo 72 bytes UTF-8.');
        return valida;
    }

    ['usuarioClave', 'claveNueva'].forEach(id => {
        const campo = document.getElementById(id);
        campo.addEventListener('input', () => validarClave(campo));
    });

    function colorEstado(estado) {
        if (estado === 'ACTIVO') return 'var(--riesgo-bajo)';
        if (estado === 'BLOQUEADO') return 'var(--utp-rojo)';
        return 'var(--texto-secundario)';
    }

    async function cargar() {
        usuarios = await LSApi.json('/api/v1/user');
        renderizar();
    }

    function renderizar() {
        totalBadge.textContent = usuarios.length;
        tabla.innerHTML = '';

        usuarios.forEach(u => {
            const tr = document.createElement('tr');
            if (u.usuario === usuarioActual) tr.classList.add('table-light');

            const estadoHtml = `<span class="etiqueta-nivel"
                style="color:${colorEstado(u.estado)}; border:1px solid ${colorEstado(u.estado)};">${escapeHtml(u.estado)}</span>`;

            tr.innerHTML = `
                <td class="dato-mono fw-bold">${u.id}</td>
                <td class="dato-mono">${escapeHtml(u.usuario)}${u.usuario === usuarioActual ? ' <span class="badge bg-secondary">tú</span>' : ''}</td>
                <td>${escapeHtml(u.nombres)}</td>
                <td>${escapeHtml(u.correo)}</td>
                <td><span class="badge bg-dark">${escapeHtml(u.rol)}</span></td>
                <td>${estadoHtml}</td>
                <td>${escapeHtml(u.codigoCliente || '—')}</td>
                <td class="dato-mono">${escapeHtml(u.ultimoAcceso)}</td>
                <td>
                    <button type="button" class="btn btn-sm btn-secundario btn-editar"><i class="bi bi-pencil-fill"></i> Editar</button>
                    <button type="button" class="btn btn-sm btn-secundario btn-clave"><i class="bi bi-key-fill"></i> Clave</button>
                    <button type="button" class="btn btn-sm btn-danger btn-eliminar"><i class="bi bi-trash-fill"></i> Eliminar</button>
                </td>
            `;

            tr.querySelector('.btn-editar').addEventListener('click', () => abrirEdicion(u));
            tr.querySelector('.btn-clave').addEventListener('click', () => abrirClave(u));
            tr.querySelector('.btn-eliminar').addEventListener('click', () => abrirEliminacion(u));
            tabla.appendChild(tr);
        });
    }

    const modal = (id) => bootstrap.Modal.getOrCreateInstance(document.getElementById(id));

    function abrirCreacion() {
        editandoId = null;
        document.getElementById('usuarioModalTitulo').textContent = 'Nuevo usuario';
        document.getElementById('usuarioNombre').readOnly = false;
        document.getElementById('grupoClave').classList.remove('d-none');
        // El alta siempre crea usuarios ACTIVO; el estado solo se edita después.
        document.getElementById('grupoEstado').classList.add('d-none');
        document.getElementById('usuarioClave').required = true;
        document.getElementById('usuarioClave').value = '';
        document.getElementById('usuarioClave').setCustomValidity('');
        document.getElementById('usuarioNombre').value = '';
        document.getElementById('usuarioNombres').value = '';
        document.getElementById('usuarioCorreo').value = '';
        document.getElementById('usuarioRol').value = 'CLIENTE';
        document.getElementById('usuarioEstado').value = 'ACTIVO';
        document.getElementById('usuarioCodigoCliente').value = '';
        modal('usuarioModal').show();
    }

    function abrirEdicion(u) {
        editandoId = u.id;
        document.getElementById('usuarioModalTitulo').textContent = 'Editar usuario: ' + u.usuario;
        document.getElementById('usuarioNombre').readOnly = true;
        document.getElementById('grupoClave').classList.add('d-none');
        document.getElementById('grupoEstado').classList.remove('d-none');
        document.getElementById('usuarioClave').required = false;
        document.getElementById('usuarioClave').value = '';
        document.getElementById('usuarioClave').setCustomValidity('');
        document.getElementById('usuarioNombre').value = u.usuario;
        document.getElementById('usuarioNombres').value = u.nombres;
        document.getElementById('usuarioCorreo').value = u.correo;
        document.getElementById('usuarioRol').value = u.rol;
        document.getElementById('usuarioEstado').value = u.estado;
        document.getElementById('usuarioCodigoCliente').value = u.codigoCliente || '';
        modal('usuarioModal').show();
    }

    function abrirClave(u) {
        editandoId = u.id;
        document.getElementById('claveModalUsuario').textContent = 'Usuario: ' + u.usuario;
        document.getElementById('claveNueva').value = '';
        document.getElementById('claveNueva').setCustomValidity('');
        modal('claveModal').show();
    }

    function abrirEliminacion(u) {
        eliminandoId = u.id;
        document.getElementById('eliminarModalMensaje').textContent =
            `El usuario ${u.usuario} y sus asignaciones de rol se eliminarán de forma definitiva.`;
        modal('eliminarModal').show();
    }

    document.getElementById('btnNuevoUsuario').addEventListener('click', abrirCreacion);

    LSApi.escucharAccion(document.getElementById('formUsuario'), 'submit', async (e) => {
        e.preventDefault();
        const clave = document.getElementById('usuarioClave');
        if (editandoId === null && !validarClave(clave)) { clave.reportValidity(); return; }
        const payload = {
            nombres: document.getElementById('usuarioNombres').value,
            correo: document.getElementById('usuarioCorreo').value,
            rol: document.getElementById('usuarioRol').value,
            codigoCliente: document.getElementById('usuarioCodigoCliente').value || null
        };

        await (editandoId === null
            ? LSApi.json('/api/v1/user', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    usuario: document.getElementById('usuarioNombre').value,
                    clave: document.getElementById('usuarioClave').value,
                    ...payload
                })
            })
            : LSApi.json('/api/v1/user/' + editandoId, {
                method: 'PATCH',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ ...payload, estado: document.getElementById('usuarioEstado').value })
            }));

        LSApi.mostrarAviso(editandoId === null ? 'Usuario creado.' : 'Usuario actualizado.');
        modal('usuarioModal').hide();
        await cargar();
    });

    LSApi.escucharAccion(document.getElementById('formClave'), 'submit', async (e) => {
        e.preventDefault();
        const clave = document.getElementById('claveNueva');
        if (!validarClave(clave)) { clave.reportValidity(); return; }
        await LSApi.json('/api/v1/user/' + editandoId + '/clave', {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ clave: document.getElementById('claveNueva').value })
        });
        LSApi.mostrarAviso('Clave actualizada.');
        modal('claveModal').hide();
    });

    LSApi.escucharAccion(document.getElementById('btnConfirmarEliminar'), 'click', async () => {
        await LSApi.json('/api/v1/user/' + eliminandoId, { method: 'DELETE' });
        LSApi.mostrarAviso('Usuario eliminado.');
        modal('eliminarModal').hide();
        await cargar();
    });

    cargar().catch(LSApi.reportarError);
})();
