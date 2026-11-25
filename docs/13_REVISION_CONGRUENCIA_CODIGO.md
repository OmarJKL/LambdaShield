# 13 — Revisión de congruencia y limpieza del código

Fecha: 18/11/2026. Alcance: código actual del backend, repositorios, permisos, flujo financiero, asistente IA, formularios, campana y pruebas. Se revisaron las referencias explícitas y los recorridos de ejecución; no se afirma ausencia absoluta de defectos ni se midió rendimiento bajo carga.

## Correcciones aplicadas

| Hallazgo | Corrección | Verificación |
| --- | --- | --- |
| CLIENTE podía seleccionar una categoría existente de naturaleza CREDITO y aumentar saldo si la evaluación se aprobaba | El flujo de cliente exige naturaleza DEBITO antes de evaluar o mover dinero. ADMIN y la carga demo conservan su recorrido separado | Prueba MVC con categoría de abono: 400 y saldo intacto |
| El resolver por nombre aceptaba beneficiarios desactivados, a diferencia del recorrido por número | Validación compartida de beneficiario activo en las dos rutas | Prueba de regresión por nombre |
| Dos bloques construían beneficiarios sin cuenta con los mismos campos | Constructor auxiliar único y marca coherente Lambda Shield | Suite de portal y conjunto demo |
| `objetivo` se presentaba como opcional pero null/omisión se rechazaba | Normalización a string vacío en la definición; se quitó la comprobación null redundante | Solicitud sin objetivo aceptada |
| Ratio máximo permitido por el formulario: 1 000 000; backend aceptaba importes mucho mayores | Límite backend específico de ratio alineado con el formulario | Rechazo de ratio 1 000 000.01 |
| Lectura individual, lectura masiva y refresco de campana podían solaparse y restar dos veces los pendientes | Serialización de cambios de lectura, invalidación de respuestas anteriores y refresco diferido | Dos pruebas JavaScript con promesas controladas |
| La cola de refresco podía perder una petición pendiente al recibir después una solicitud de paginación | Se conserva la marca de refresco hasta ejecutarlo | Regresiones de actualización concurrente |
| El parser del proveedor podía aceptar documentos concatenados o propiedades JSON repetidas | Rechazo de tokens finales y detección estricta de duplicados | Casos inválidos añadidos al test del adaptador |
| Textos prometían auditoría/telemetría, revisión de analista o probabilidad de fraude que el flujo no implementa | Matriz de reglas describe registro, retención/bloqueo y aviso real; recibo dice operación aprobada | Revisión de plantillas y pruebas existentes |

El control de naturaleza del portal está en la capa de aplicación, no depende del selector HTML. Los errores se producen dentro de la transacción: no se confirma el abono ni sus referencias. Las pruebas usan H2 con datos ficticios; no se enviaron operaciones a PostgreSQL ni llamadas de inferencia al proveedor durante esta revisión.

## Código sin uso eliminado

Se buscaron declaraciones y referencias en producción y pruebas. Se quitaron tres métodos de repositorio sin llamadas explícitas, junto con el import que quedó sin uso:

- `ClienteRepository.findByNumeroDocumento`.
- `EvaluacionFraudeRepository.findByTransaccion`.
- `ReglaFraudeRepository.findByEsActivaTrue`: el listado activo utiliza la variante ordenada.

No se encontró un archivo JavaScript estático sin enlace desde las plantillas actuales. Esto no demuestra que todas sus funciones se ejecuten en todos los roles: existen módulos que retornan cuando su formulario o componente no aparece, lo cual es intencional.

## Piezas conservadas con motivo

- `CuentaRepository.findFirst...` y `findTop...` parecen duplicadas, pero la primera mantiene un bloqueo de escritura y la segunda permite lectura sin él. Unificarlas sin considerar ese contrato introduciría bloqueos innecesarios o perdería protección financiera.
- La ruta de beneficiario sin número sigue siendo usada por el simulador y las cargas demo. Se redujo duplicación sin romper ese contrato.
- El constructor corto de `EvaluarTransaccionCommand` mantiene los casos que no envían banco/cuenta y sigue utilizado en producción/pruebas.
- Las entidades de alertas, casos, historial y auditoría forman parte del esquema académico aunque no tengan un flujo completo. Eliminarlas requeriría revisar el esquema y sus relaciones; no son equivalentes a un método privado sin llamadas.
- `parametros_json`, versión y vigencia de reglas describen una capacidad de persistencia que el evaluador todavía no implementa plenamente. Se mantienen como deuda explícita, no como funcionalidad dinámica ya terminada.

## Trabajo recomendado siguiente

| Prioridad | Punto | Motivo y solución propuesta |
| --- | --- | --- |
| Alta | Identidad del destinatario por banco y cuenta | Hoy la búsqueda y unicidad usan solo cliente y número. Dos bancos pueden usar el mismo número y una coincidencia interna prevalece. Definir la identidad completa, migrar la unicidad y actualizar consulta, contactos y pruebas |
| Alta | Frecuencia bajo concurrencia | El conteo de operaciones del cliente ocurre antes de obtener el bloqueo de cuenta. Dos solicitudes simultáneas pueden observar el mismo conteo. Obtener el bloqueo antes de derivar hechos dependientes del historial, con una prueba concurrente del score |
| Media | Cálculos de reglas con double | Los saldos se operan con BigDecimal, pero monto/promedio se convierten a double para puntuar. Migrar comparaciones y curvas para conservar fronteras decimales, con pruebas de límites |
| Media | Fechas de avisos en una carga demo nueva | El loader cambia la fecha de evaluación después de crear el aviso, que ya copió la fecha anterior. Establecer la fecha histórica antes de notificar o sincronizarla exclusivamente en la carga demo; preservar lecturas |
| Media | Catálogo de campos del asistente | Se alinearon límites, pero etiquetas y opciones siguen en Java y JS. Exponer metadatos tipados desde el backend para evitar nuevas divergencias y construir los controles a partir de ellos |
| Media | Listados y exportaciones generales | Siguen sin paginación/streaming. La campana sí pagina. Introducir límites y paginación donde el volumen lo justifique |
| Funcional | Reglas dinámicas y revisión humana | Falta publicar versiones, activarlas/desactivarlas y ejecutar casos de prueba del borrador. La revisión IA actual no realiza esas acciones |

No deben mezclarse cambios de configuración del motor con la baja de una operación: esta última puede revertir dinero. Las modificaciones de reglas deben afectar operaciones nuevas y conservar el conjunto aplicado a cada evaluación histórica.

## Resultado de verificación

La ejecución completa final y sus reportes se encuentran en `target/surefire-reports` y `target/site/jacoco`. Las pruebas JavaScript se ejecutan con `node --test frontend/tests/*.test.cjs`. Resultado comprobado el 18/11: **137 pruebas Java y 23 JavaScript, todas correctas**. Ver la segunda revisión para el estado actual. Se regeneró la cobertura sin mezclar ejecuciones de versiones previas.

## Segunda revisión — 24/11/2026

Se contrastaron los documentos con el código y se corrigieron los flujos incompletos encontrados.

### Código

| Hallazgo | Corrección | Prueba |
| --- | --- | --- |
| Activar una regla no validaba su peso: con un peso no entero, todas las evaluaciones posteriores respondían 500 | `EstadoReglaService` valida implementación y peso entero 0–100 antes de activar (400) | `pesoInvalidoNoSeActivaNiImpideVerElCatalogo` |
| `/reglas` respondía 500 si cualquier regla, incluso inactiva, tenía peso inválido | `ReglaConsultaService` devuelve `pesoMaximo` nulo y la vista muestra «Peso inválido» | Misma prueba |
| Se podían desactivar todas las reglas: el puntaje quedaba en 0 y todo se aprobaba, sin confirmación ni registro | Mínimo de una regla activa, bloqueo del catálogo, registro en `auditoria` y confirmación en la interfaz, que ahora muestra el motivo del rechazo | `noSePuedeDesactivarLaUltimaReglaActiva`, `cadaCambioRealQuedaAuditadoConSuAutor`, prueba JS |
| Eliminar un usuario con auditoría, alertas o casos respondía 500 | `flush` dentro del caso de uso y `DataIntegrityViolationException` → 409 | `usuarioConHistorialAuditadoNoSeEliminaYResponde409` |
| El portal creaba un `tipo_transaccion` por cada categoría desconocida | Para CLIENTE la categoría debe existir y estar activa (400); ADMIN y la carga demo conservan el catálogo abierto | `clienteNoPuedeCrearCategoriasNuevasDesdeElPortal` |
| `0011-0145-…` y `00110145…` se trataban como cuentas distintas | Comparación sin espacios ni guiones en cuenta interna, contactos y consulta de titular; el contacto guarda el formato de la cuenta | `cuentaDestinoSeReconoceConOSinGuionesSinDuplicarElContacto` |
| Si la primera cuenta estaba inactiva, se rechazaba con 500 y «no tiene una cuenta registrada» aunque existiera otra activa | Se usa la primera cuenta ACTIVA; sin ninguna, 400 «no tiene una cuenta activa» | `operacionUsaLaPrimeraCuentaActivaYSinCuentaActivaSeRechazaCon400` |
| El simulador registraba transacciones con fecha futura | Una hora posterior a la actual se registra el día anterior | `horaDeclaradaPosteriorALaActualSeRegistraElDiaAnterior` |
| El alta de usuarios mostraba un selector de estado que el servidor ignoraba | El selector solo aparece al editar | Prueba JS de usuarios |

Las cuentas de usuario que hayan cambiado el estado de una regla quedan referenciadas por `auditoria`: ya no se pueden eliminar (409) y deben bloquearse. Es el comportamiento esperado de un registro de auditoría.

### Documentación

Se unificó el tamaño de la carga demo (5 252 operaciones), se actualizaron conteos de pruebas y fechas, se resolvió la contradicción de `02` sobre administración de reglas, se completaron las tablas de permisos y endpoints (`06`, `07`, README), se alineó la política de claves (72 bytes UTF-8) y se reparó la tabla del índice.

### Pendiente

Siguen abiertos los puntos de la tabla «Trabajo recomendado siguiente»: identidad del destino por banco y cuenta, frecuencia bajo concurrencia, cálculos con `double`, fechas de avisos en la carga demo, catálogo del asistente, paginación de listados y reglas dinámicas.

Resultado: **156 pruebas Java y 35 JavaScript**, todas correctas, con `mvnw clean test` sobre JDK 21 y H2.

## Reglas definidas y reparto porcentual — 25/11/2026

| Cambio | Detalle | Prueba |
| --- | --- | --- |
| Guardar una regla desde el formulario | `POST /api/v1/rule` crea RCnnn inactiva con la definición del administrador en `parametros_json` y la audita | `ReglasDefinidasApiMockMvcTest`, prueba JS del asistente |
| Motor con reglas RC | `ReglaDefinidaEvaluador` interpreta campos y operadores cerrados; `CatalogoReglasService` ejecuta RF y RC | `ReglaDefinidaEvaluadorTest`, `elMotorEjecutaLaReglaDefinida…` |
| Reparto porcentual | Máximo = peso ÷ suma de activas × 100; activar, desactivar o crear reajusta al resto (`versionModelo` 2.0) | `activarDesactivarOAgregarReglasReparteDeNuevoElPorcentaje` |
| Probar sin guardar | `POST /api/v1/rule/simulation` compara vigente y candidatas/borrador sin persistir; el campo de desarrolladores usa este modo por defecto y prueba lotes de 50 | `probarSinGuardar…`, pruebas JS del simulador |
| Confirmación al activar o desactivar | Modal con la participación antes y después | Pruebas JS de `/reglas` |

Efecto de la nueva escala: las mismas señales producen puntajes menores que con la suma con tope. Se ajustaron tres pruebas a la escala o a la nueva API del catálogo y dos operaciones del conjunto demo para conservar un caso MEDIO, uno ALTO y uno CRITICO por cuenta. Las evaluaciones históricas no se recalculan.

Pendiente: editar o versionar una RC, severidad graduada en RC (hoy binaria) y aviso automático de señales repetidas entre RC y RF.

Resultado: **166 pruebas Java y 40 JavaScript**, todas correctas (`mvnw clean test` con JDK 21 y `node --test`).

## Acceso denegado en APIs — 25/11/2026

Sin identidad válida, sin permiso o con CSRF inválido, toda ruta `/api/**` responde con una redirección 302 a `/login` sin cuerpo ni mensaje, y cierra la sesión si existía (el login devuelve a su inicio a quien sigue autenticado). `LSApi.fetch` usa `redirect: manual` y lleva la página completa al login sin avisos. Las páginas conservan su comportamiento: sin sesión van al login y con un rol sin acceso, a la página de inicio de su rol. Pruebas: matcher `SesionesPrueba.alLogin()` en las suites MockMvc y prueba JS de redirección silenciosa.

