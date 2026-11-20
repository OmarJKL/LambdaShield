-- Agenda de cuentas y notificaciones personales. Requiere la migración base de soft delete.
BEGIN;

CREATE TABLE IF NOT EXISTS notificacion_usuario (
    id_notificacion BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_usuario BIGINT NOT NULL REFERENCES usuario_sistema(id_usuario) ON DELETE CASCADE,
    id_evaluacion BIGINT NOT NULL REFERENCES evaluacion_fraude(id_evaluacion) ON DELETE CASCADE,
    tipo VARCHAR(20) NOT NULL,
    titulo VARCHAR(100) NOT NULL,
    mensaje VARCHAR(1000) NOT NULL,
    fecha_creacion TIMESTAMPTZ NOT NULL,
    fecha_lectura TIMESTAMPTZ,
    CONSTRAINT uq_notificacion_usuario_evaluacion_tipo UNIQUE (id_usuario, id_evaluacion, tipo)
);
CREATE INDEX IF NOT EXISTS ix_notificacion_usuario_fecha
    ON notificacion_usuario(id_usuario, fecha_creacion DESC, id_notificacion DESC);
CREATE INDEX IF NOT EXISTS ix_notificacion_usuario_pendiente
    ON notificacion_usuario(id_usuario) WHERE fecha_lectura IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_beneficiario_cliente_cuenta
    ON beneficiario(id_cliente, numero_cuenta_destino);

-- Contactos mutuos de los titulares activos; una cuenta principal por cliente y sin agregarse a sí mismo.
WITH principales AS (
    SELECT DISTINCT ON (c.id_cliente) c.id_cliente, c.numero_cuenta,
           concat_ws(' ', cl.nombres, cl.apellido_paterno, cl.apellido_materno) AS nombre
    FROM cuenta c JOIN cliente cl USING (id_cliente)
    WHERE cl.es_activo = TRUE AND c.estado_cuenta = 'ACTIVA'
    ORDER BY c.id_cliente, c.id_cuenta
)
INSERT INTO beneficiario (id_cliente, nombre_beneficiario, entidad_financiera, numero_cuenta_destino,
                          codigo_pais, es_conocido, fecha_primer_uso, fecha_ultimo_uso, es_activo)
SELECT origen.id_cliente, destino.nombre, 'Lambda Shield', destino.numero_cuenta, 'PE', TRUE,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, TRUE
FROM principales origen CROSS JOIN principales destino
WHERE origen.id_cliente <> destino.id_cliente
ON CONFLICT (id_cliente, numero_cuenta_destino) DO NOTHING;

-- Cada usuario recibe su propio estado de lectura, también para las evaluaciones históricas.
INSERT INTO notificacion_usuario (id_usuario, id_evaluacion, tipo, titulo, mensaje, fecha_creacion)
SELECT u.id_usuario, e.id_evaluacion, t.estado_transaccion,
       CASE t.estado_transaccion WHEN 'BLOQUEADA' THEN 'Operación bloqueada' ELSE 'Operación retenida' END,
       LEFT('TR-' || lpad(e.id_evaluacion::text, greatest(5, length(e.id_evaluacion::text)), '0')
            || ' · S/ ' || t.monto_transaccion::text || '. Tu saldo no fue descontado. '
            || COALESCE((SELECT 'Factores: ' || string_agg(
                    COALESCE(er.etiqueta_aplicada, r.nombre_regla) || ' (' || er.peso_aplicado::integer::text || ' puntos)', ', '
                    ORDER BY er.id_evaluacion_regla)
                 FROM evaluacion_regla er JOIN regla_fraude r USING (id_regla)
                 WHERE er.id_evaluacion = e.id_evaluacion AND er.peso_aplicado > 0), 'Revisa el detalle de la operación.'), 1000),
       e.fecha_evaluacion
FROM evaluacion_fraude e JOIN transaccion t USING (id_transaccion) JOIN cuenta c USING (id_cuenta)
JOIN usuario_sistema u ON u.id_cliente = c.id_cliente
WHERE e.es_activa = TRUE AND t.estado_transaccion IN ('BLOQUEADA', 'RETENIDA')
  AND EXISTS (SELECT 1 FROM usuario_rol ur JOIN rol r USING (id_rol)
              WHERE ur.id_usuario = u.id_usuario AND r.codigo_rol = 'CLIENTE' AND r.es_activo = TRUE)
ON CONFLICT (id_usuario, id_evaluacion, tipo) DO NOTHING;

COMMIT;
