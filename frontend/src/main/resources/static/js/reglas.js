(() => {
    'use strict';
    document.querySelectorAll('[data-cambiar-regla]').forEach(boton => {
        boton.addEventListener('click', async () => {
            const activa = boton.dataset.activa !== 'true';
            const mensaje = document.getElementById('estadoReglasMensaje');
            boton.disabled = true;
            mensaje.textContent = 'Guardando estado de la regla…';
            try {
                await LSApi.json('/api/v1/rule/' + encodeURIComponent(boton.dataset.codigo) + '/estado', {
                    method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ activa })
                });
                boton.dataset.activa = String(activa);
                boton.textContent = activa ? 'Desactivar' : 'Activar';
                boton.setAttribute('aria-label', boton.textContent + ' ' + boton.dataset.codigo);
                const estado = boton.parentElement.querySelector('[data-estado-regla]');
                estado.textContent = activa ? 'Activa' : 'Inactiva';
                estado.className = 'badge ' + (activa ? 'bg-success' : 'bg-secondary');
                mensaje.textContent = boton.dataset.codigo + (activa ? ' activada.' : ' desactivada.') + ' Se aplicará en las nuevas evaluaciones.';
            } catch (error) {
                mensaje.textContent = 'No se pudo cambiar el estado. Inténtalo nuevamente.';
                LSApi.reportarError(error);
            } finally { boton.disabled = false; }
        });
    });
})();
