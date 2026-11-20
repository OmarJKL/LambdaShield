# 05 — Valor de negocio, indicadores y límites

[Índice](README.md) · [Funcionalidades](02_FUNCIONALIDADES.md)

## 1. Problema abordado

Lambda Shield permite experimentar con decisiones de riesgo previas al movimiento de saldo. Hace visibles las señales que contribuyeron a una decisión y mantiene un historial consultable de operaciones y factores.

Su valor actual es demostrativo y técnico: reúne controles de acceso, reglas explicables, persistencia y consistencia de saldo. No hay resultados de campo que permitan cuantificar reducción de fraude, ahorro económico, exactitud o cumplimiento normativo.

## 2. Valor por participante

| Participante | Capacidad disponible | Beneficio esperado |
| --- | --- | --- |
| Cliente | Saldo, operaciones propias y recibos con resultado | Comprender el estado de la operación y sus causas visibles |
| Analista | Historial, indicadores y exportaciones | Revisar patrones y priorizar consultas |
| Administrador | Usuarios, simulador y cambios de estado de evaluaciones | Probar escenarios y gestionar acceso operativo |
| Equipo técnico | Reglas aisladas, capas y pruebas | Modificar con evidencia de regresión |
| Institución hipotética | Prototipo de decisión previa a una operación | Base para estudiar una integración real, no sustituto de ella |

El administrador consulta reglas, pero no dispone aún de una pantalla o API para editarlas. La baja lógica conserva registros, pero no genera por sí sola una auditoría completa con autor, motivo y eventos inmutables.

## 3. Capacidades comprobables

- Ocho reglas con factores explicativos y ponderación por catálogo.
- Separación entre operaciones aprobadas, monitoreadas, retenidas y bloqueadas.
- Descuento de débito solo al aprobar; nivel MEDIO mueve dinero y nivel ALTO lo retiene sin moverlo.
- Aislamiento de operaciones del cliente y autorización explícita por ruta/método.
- Revalidación de sesiones, CSRF y limitación de solicitudes.
- Conservación de copias de etiqueta, máximo y versión por factor nuevo.
- Bloqueos de cuenta y evaluación verificados en escenarios concurrentes.
- Reportes CSV/XLSX/PDF y consultas por lote en recorridos importantes.

Estas capacidades están probadas en el entorno descrito en [pruebas](09_PRUEBAS_CALIDAD.md). No se ha demostrado una latencia de servicio objetivo ni un volumen máximo de producción; `duracionMs=0` es un valor fijo del modelo actual.

## 4. Indicadores y sus interpretaciones

| Indicador | Interpretación válida | Interpretación que no debe hacerse |
| --- | --- | --- |
| Score | Suma limitada de señales ponderadas | Probabilidad estadística de fraude |
| Cantidad de bloqueadas | Evaluaciones activas de nivel CRITICO | Casos de fraude confirmados |
| Puntaje promedio | Promedio del conjunto activo | Exactitud o eficacia del detector |
| Monto ALTO + CRITICO | Volumen de operaciones retenidas/bloqueadas en el reporte | Pérdida evitada demostrada |
| Tasa de bloqueo | CRITICO / total del conjunto | Tasa de detección de fraude real |
| Ingresos/egresos | Transacciones locales aprobadas por naturaleza | Liquidaciones verificadas por una red bancaria |
| Saldo | Disponible actual de cuentas activas | Estado de cuenta conciliado o libro mayor |

La variación de saldo usa una base estimada; no es la misma comparación de periodos que se aplica a ingresos/egresos. Desactivar evaluaciones puede modificar agregados históricos porque cambia la transacción original.

## 5. Evidencia necesaria para medir eficacia

Antes de afirmar que un cambio mejora el detector, se necesitaría:

1. Un conjunto de operaciones con etiquetas verificadas de fraude/no fraude.
2. Separación temporal entre ajuste de reglas y validación.
3. Matriz de confusión, precisión, recall y tasa de falsos positivos.
4. Costo por bloqueo erróneo, fraude omitido y revisión manual.
5. Latencia, disponibilidad y costo de ejecución bajo carga representativa.
6. Seguimiento de cambios de versión y posible deriva de las señales.

Estos análisis no están implementados como un producto de evaluación en el repositorio. Las pruebas actuales verifican comportamiento del software, no la eficacia predictiva sobre fraude real.

## 6. Límites operativos y de datos

- No hay conexión con compensación bancaria ni acreditación externa del beneficiario.
- La retención no reserva fondos ni tiene una pantalla de resolución manual.
- El país es declarado; RF007 no usa distancias ni localización real.
- La huella depende de User-Agent y no constituye identidad de dispositivo fuerte.
- La recurrencia del beneficiario se basa en contactos guardados por cuenta (o nombre en el contrato antiguo); no exige una transferencia anterior aprobada.
- La IP conocida puede provenir de transacciones previas no aprobadas.
- No hay canal de notificaciones por correo, SMS o push.
- Alertas, casos y auditoría están modelados, pero sin un flujo completo.
- La sesión y los límites son locales por instancia; faltan componentes compartidos para alta disponibilidad.
- Los listados/exportaciones pueden crecer en memoria y no todos están paginados.
- H2 no acredita que el despliegue PostgreSQL, Docker o el proxy estén validados.

## 7. Seguridad y uso productivo

La sesión HTTP protegida con CSRF es el esquema actual de autenticación. Cambiar a JWT no es una mejora automática ni una condición necesaria para esta aplicación del mismo origen.

Un despliegue real requiere decidir identidad de usuarios, MFA si procede, aprovisionamiento, protección de secretos, HTTPS, red, gestión de datos personales, copias y recuperación. Estas decisiones no se sustituyen con el hecho de usar Spring Security.

La creación demo está desactivada por defecto; las cuentas creadas anteriormente deben revisarse por separado. No hay certificación ni afirmación de cumplimiento de una regulación específica.

## 8. Evolución propuesta

| Prioridad orientativa | Trabajo pendiente | Resultado buscado |
| --- | --- | --- |
| Alta | Versionar parámetros y publicar reglas con validación/auditoría | Control reproducible del motor |
| Alta | Auditoría de acciones e idempotencia de creación | Explicar cambios y controlar reintentos |
| Alta | Validación con PostgreSQL y pruebas de carga | Medir límites reales de operación |
| Media | Investigación de casos y resolución de retenciones | Cerrar el ciclo del analista |
| Media | Geolocalización y verificación de señales | Reducir dependencia de datos declarados |
| Media | Paginación, exportación por streaming y migraciones versionadas | Operar con más datos y cambios controlados |
| Exploratoria | Evaluador contextual LLM en modo de observación | Medir si aporta valor antes de influir en decisiones |

La propuesta específica de reglas dinámicas y Qwen se documenta en [la propuesta de evolución](10_PROPUESTA_REGLAS_DINAMICAS_LLM.md) como diseño pendiente, no como una función implementada.
