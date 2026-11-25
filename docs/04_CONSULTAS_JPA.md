# 04 — Datos, consultas JPA y consistencia

[Índice](README.md) · [Operación y migración](08_INSTALACION_OPERACION.md)

## 1. Persistencia del proyecto

El esquema de referencia es [BD-Desarrollo web.sql](../backend/src/main/resources/db/postgresql/BD-Desarrollo%20web.sql). El runtime usa PostgreSQL, valida el mapeo al arrancar y mantiene `open-in-view=false`. Las transacciones y consultas necesarias se coordinan en servicios.

Existen 19 tablas y sus entidades, incluida `notificacion_usuario`. No todas tienen repositorio o caso de uso activo.

| Grupo | Tablas | Uso actual |
| --- | --- | --- |
| Identidad | `usuario_sistema`, `rol`, `usuario_rol` | Login, autorización y administración |
| Cliente | `cliente`, `cuenta` | Titular, promedio, cuenta principal y saldo |
| Contexto | `dispositivo`, `beneficiario` | Señales y referencias de operación |
| Catálogos | `canal`, `tipo_transaccion`, `regla_fraude` | Canal, naturaleza y pesos de riesgo |
| Operaciones | `transaccion`, `evaluacion_fraude`, `evaluacion_regla` | Persistencia y explicación del resultado |
| Extensión del esquema | `caracteristica_transaccion`, `alerta_fraude`, `caso_fraude`, `historial_caso`, `auditoria` | Entidades conservadas; sin flujo completo de aplicación. `auditoria` solo registra los cambios de estado de reglas |

## 2. Relaciones e identificadores

```text
Cliente → Cuenta → Transaccion → EvaluacionFraude → EvaluacionRegla → ReglaFraude
Cliente → Dispositivo
Cliente → Beneficiario
Transaccion → TipoTransaccion / Canal / Dispositivo / Beneficiario
UsuarioSistema → vínculo idCliente
UsuarioSistema ↔ UsuarioRol ↔ Rol
```

- `UsuarioRol` tiene clave compuesta (`UsuarioRolId`).
- `CaracteristicaTransaccion` comparte la clave de transacción mediante `@MapsId`; la migración elimina IDENTITY de esa clave compartida.
- El UUID de `transaccion.codigo_transaccion` identifica la operación interna de transacción.
- `EvaluacionResponse.id` es una etiqueta `TR-` derivada del ID de evaluación.
- `idInterno` es el identificador que usan las rutas PATCH de evaluación.

La cuenta principal es la primera cuenta ACTIVA por ID ascendente, obtenida con bloqueo de escritura. No existe una preferencia de cuenta configurable por pantalla. Si el cliente no tiene ninguna cuenta activa, la creación responde 400. Los números de cuenta se comparan sin espacios ni guiones (`replace` en JPQL), tanto al buscar la cuenta interna como al reutilizar un contacto del cliente.

## 3. Estrategias de consulta

| Estrategia | Aplicación |
| --- | --- |
| Métodos derivados | Buscar códigos, nombres, huellas y existencias |
| JPQL con `@Query` | Agregados, conteos, carga relacionada y bloqueos |
| `Specification` | Filtros opcionales de evaluaciones en BD |
| `EntityGraph` | Cargar las relaciones usadas al construir resultados |
| `Pageable` | Limitar movimientos recientes en BD |
| Procesamiento en memoria | Agrupación diaria, resumen de un reporte ya filtrado y adaptación de modelos |

El filtrado de reportes ya no se hace cargando todo el historial y descartándolo en Java. La validación de correo y el conteo de administradores tampoco requieren cargar todos los usuarios.

## 4. Historial y factores por lote

`EvaluacionConsultaService` construye una `Specification<EvaluacionFraude>` con:

- `esActiva` cuando se pide solo el historial activo.
- Fecha de evaluación desde el inicio del día solicitado.
- Fecha menor que el inicio del día siguiente a `hasta`.
- Nivel convertido a `NivelRiesgo`.
- Código de cliente mediante la relación transacción → cuenta → cliente.

Los resultados se ordenan por `fechaEvaluacion` y `idEvaluacion` descendentes.

La consulta usa un `EntityGraph` para transacción, cuenta, cliente, tipo y característica. Incluir la característica evita consultas adicionales por la relación uno a uno inversa, aunque normalmente no tenga datos.

Después se cargan todos los factores de las evaluaciones encontradas mediante `findByEvaluacion_IdEvaluacionInOrderByIdEvaluacionReglaAsc`, con su regla. Se agrupan por ID de evaluación y se construyen los resultados.

La prueba de regresión verifica **dos sentencias preparadas** para un historial no vacío en su fixture: una de evaluaciones/relaciones y una de factores/reglas. Un resultado vacío no requiere la segunda. Esto no significa que una petición HTTP completa ejecute solo dos SQL: la seguridad revalida también la identidad.

El listado no está paginado; un rango amplio puede seguir devolver muchas filas y un conjunto grande de IDs para el lote.

## 5. KPIs de evaluaciones

`EvaluacionFraudeRepository.calcularMetricas()` obtiene en una consulta:

- Total activo.
- Conteos por BAJO, MEDIO, ALTO y CRITICO mediante `case`.
- Promedio del fraude score.

`EvaluacionMetricasService` adapta esa proyección a `KpisResultado` y utiliza promedio cero cuando no hay evaluaciones. No carga todas las entidades solo para contarlas.

## 6. Agregaciones financieras

### Saldo

`CuentaRepository.sumarSaldoPorEstado` suma `saldoDisponible` con `coalesce` para un estado de cuenta. El dashboard solicita ACTIVA.

### Montos por naturaleza

`TransaccionRepository.sumarMontosPorNaturaleza` devuelve una proyección tipada `TotalNaturaleza`, con naturaleza y monto decimal. La consulta filtra fecha y estado APROBADA, y agrupa por crédito/débito.

`DashboardResumenService` ejecuta dos agregaciones: periodo actual y anterior. Cada una ya incluye ambas naturalezas; no se repite una consulta idéntica para extraer ingresos y egresos por separado. Se suma además el saldo de cuentas activas.

### Serie diaria y movimientos recientes

`buscarDelPeriodo` utiliza `join fetch` sobre cuenta, cliente y tipo. Filtra APROBADA y ordena por fecha e ID de transacción descendentes.

- Serie: usa `Pageable.unpaged()`, carga operaciones del periodo y las agrupa por día en Java con `BigDecimal`.
- Recientes: usa `PageRequest.of(0, cantidad)`; el endpoint solicita ocho filas. El límite se aplica en BD y no después de cargar el periodo entero.

No se debe afirmar que todos los recorridos del dashboard tienen un número constante mínimo de SQL: las relaciones y el tamaño de la serie siguen requiriendo seguimiento de rendimiento.

## 7. Usuarios, roles y validación

`UsuarioAdminService.listar` ordena usuarios por ID descendente, obtiene sus roles en un lote y resuelve clientes con `findAllById`.

En el fixture de consistencia se verifican **tres sentencias** para ese listado no vacío: usuarios, asignaciones/roles y clientes. Si no hay clientes vinculados o usuarios, el comportamiento puede usar menos consultas.

Las búsquedas de correo usan `existsByCorreoIgnoreCase` y su variante que excluye un ID. El conteo de otros administradores activos se calcula con un JPQL `count(distinct ...)` sobre usuarios y roles activos.

`RolUsuarioService` concentra la jerarquía de privilegios para login, revalidación y administración. La validación de unicidad previa mejora el mensaje al usuario; las restricciones de BD siguen siendo necesarias ante concurrencia.

## 8. Bloqueos de escritura

### Crear

`CuentaRepository.findFirstByCliente_IdClienteOrderByIdCuentaAsc` tiene `PESSIMISTIC_WRITE`. La transacción del caso de uso mantiene el bloqueo mientras decide y aplica el movimiento, y persiste el resultado.

### Desactivar o reactivar

1. `EvaluacionFraudeRepository.bloquearPorId` bloquea la evaluación.
2. Se comprueba su estado activo.
3. `CuentaRepository.bloquearPorId` bloquea la cuenta relacionada.
4. Se aplica el movimiento o reverso y se actualizan estados.

Bloquear la evaluación evita que dos acciones sobre la misma evaluación ejecuten dos reversos. Bloquear la cuenta evita sobrescribir saldos entre distintas operaciones. Las lecturas ordinarias usan `findTopByCliente_IdClienteOrderByIdCuentaAsc`, sin ese bloqueo de escritura.

`MovimientoSaldoService` opera sobre la entidad bloqueada dentro de la transacción existente; no abre una transacción financiera separada. Hibernate persiste el saldo modificado mediante seguimiento de cambios.

Las pruebas concurrentes usan transacciones independientes. No demuestran idempotencia de POST ni eliminan todos los posibles bloqueos/esperas de PostgreSQL bajo carga.

## 9. Precisión, fechas e historial

Los campos monetarios principales usan escala 2. El DTO exige hasta 16 enteros y 2 decimales; no se convierte primero el importe a `double` para descontarlo. Las respuestas monetarias son números JSON exactos generados desde `BigDecimal`; un consumidor debe elegir un parser decimal si necesita conservar importes muy grandes sin la limitación de números JavaScript.

Las fechas persistidas principales usan `OffsetDateTime` y columnas de tiempo con zona. Los filtros diarios usan la zona por defecto del servidor. El DTO de evaluación convierte su fecha a `LocalDateTime`, de modo que `fechaHora` no incluye offset; `fecha` es una representación para mostrar.

Los máximos, etiquetas y versiones de factores se copian al evaluar. La versión de modelo y duración tienen actualmente valores fijos. No se guarda un evento de auditoría separado por cada acción administrativa ni un asiento financiero separado por reverso.

## 10. Scripts y migración

El esquema canónico contiene creación de tablas y está destinado a una base nueva. No se debe asumir idempotente ni ejecutarlo sobre una base en uso como sustituto de una migración.

La [migración existente](../backend/src/main/resources/db/postgresql/migracion_soft_delete_postgresql.sql) es transaccional e idempotente para los cambios que incluye:

- Código, promedio y estado activo de cliente.
- Dirección IP de dispositivo y normalización de tipos IP/JSON.
- Estado activo y fecha de desactivación de evaluación.
- Vínculo usuario → cliente.
- Corrección de la clave compartida de características.
- Copias históricas de máximo, etiqueta y versión de factores, con backfill desde el catálogo.

El runtime no aplica estos scripts automáticamente. Las instrucciones de copia de seguridad, ejecución y comprobación están en [operación](08_INSTALACION_OPERACION.md).

## 11. Límites pendientes

- Listados y exportaciones generales sin paginación/streaming; la bandeja de notificaciones sí está paginada.
- Agrupación diaria en memoria sobre las transacciones del periodo.
- Consulta de cuenta por cliente en algunos recorridos del selector.
- No hay migraciones versionadas mediante Flyway/Liquibase.
- Las pruebas H2 no sustituyen la comprobación del SQL y los bloqueos sobre PostgreSQL real.
- La regla del último administrador tiene validación de negocio; no existe en este cambio una prueba de su serialización entre modificaciones administrativas simultáneas.

Estos puntos describen el estado real y no deben presentarse como problemas ya resueltos ni como garantías de escalabilidad.

## Persistencia de contactos y notificaciones

Después de la migración general se aplica [contactos y notificaciones](../backend/src/main/resources/db/postgresql/migracion_contactos_notificaciones.sql). Crea `notificacion_usuario` con unicidad por usuario, evaluación y tipo, e índices de bandeja y pendientes. Añade unicidad de beneficiario por cliente y número; los contactos antiguos sin número pueden coexistir. Los duplicados previos con número deben corregirse antes de crear el índice.

El script precarga contactos entre clientes activos con cuenta activa y reconstruye avisos históricos de evaluaciones activas bloqueadas/retenidas para usuarios con rol CLIENTE. Repetirlo no duplica registros ni reinicia lecturas. No se ejecuta automáticamente al arrancar.

La consulta paginada usa `EntityGraph` para evaluación, transacción y características. Las actualizaciones de lectura incluyen siempre al usuario en el filtro. La creación del aviso participa en la misma transacción que la evaluación; un rollback revierte ambas.
