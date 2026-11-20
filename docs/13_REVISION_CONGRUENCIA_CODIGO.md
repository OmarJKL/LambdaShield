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

La ejecución completa final y sus reportes se encuentran en `target/surefire-reports` y `target/site/jacoco`. Las pruebas JavaScript se ejecutan con `node --test frontend/tests/*.test.cjs`. Resultado comprobado: **137 pruebas Java y 23 JavaScript, todas correctas**. Se regeneró la cobertura sin mezclar ejecuciones de versiones previas.
