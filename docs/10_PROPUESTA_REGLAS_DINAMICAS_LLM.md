# 10 — Propuesta: reglas dinámicas y evaluación contextual con Qwen

[Índice](README.md) · [Motor actual](03_REGLAS_ANTIFRAUDE.md) · [Seguridad actual](06_SEGURIDAD.md)

**Estado: propuesta de motor dinámico y evaluación contextual, todavía pendientes.** Se implementó un asistente básico de revisión de formularios con OpenRouter, documentado en [12 — Asistente IA](12_ASISTENTE_REGLAS_IA.md). No publica reglas ni evalúa operaciones reales; esas capacidades de este diseño siguen pendientes. Thymeleaf se mantiene.

## 1. Qué significa «dinámico»

Hay tres necesidades diferentes:

| Nivel | Objetivo | Estado actual |
| --- | --- | --- |
| Peso/activación | Cambiar cuánto aporta una regla y si se aplica | Se consulta desde BD; sin interfaz/API de edición |
| Parámetros y grado | Cambiar umbrales, ventanas y curvas sin desplegar Java | Las condiciones siguen principalmente en código |
| Interpretación contextual | Evaluar patrones difíciles de expresar solo con umbrales | Asistente de borradores disponible; evaluación contextual pendiente |

El motor ya evalúa algunas señales gradualmente: monto y frecuencia tienen varios tramos, ubicación tiene un valor intermedio y RF007 tiene dos intensidades. Las reglas de dispositivo, IP y horario son básicamente binarias. La propuesta extiende ese grado de infracción de manera configurable y reproducible.

## 2. Primera etapa recomendada: parámetros versionados

Mantener la identidad de `regla_fraude` y crear una entidad de versiones, por ejemplo `regla_fraude_version`, en lugar de sobrescribir las condiciones de una versión publicada.

| Campo propuesto | Propósito |
| --- | --- |
| Regla y número de versión | Identidad estable y revisión concreta |
| Tipo de evaluador | Determinista, contextual o híbrido |
| Peso máximo | Aporte permitido al score |
| Parámetros tipados | Umbrales, ventanas, listas o tramos admitidos |
| Estado | Borrador, validada, publicada o retirada |
| Fecha de aplicación | Controlar cuándo se selecciona la versión |
| Autor, publicador y motivo | Trazar el cambio administrativo |
| Versión de esquema de parámetros | Interpretar correctamente su estructura |

El campo actual `parametros_json` puede servir para un prototipo, pero hoy no basta con editarlo: el motor no interpreta esos parámetros. Para una solución mantenible, validar un esquema por tipo de regla y conservar versiones publicadas inmutables.

No ejecutar Java, SQL, SpEL o expresiones arbitrarias recibidas desde una pantalla. Usar operadores y tipos autorizados: comparaciones, rangos, listas y curvas definidas por el backend.

## 3. Puntuar cuánto se incumple

Separar dos valores:

```text
severidad ∈ [0, 1]
puntosRegla = redondear(severidad × pesoVersion)
scoreFinal = min(100, suma de puntos aplicados)
```

La severidad mide intensidad de una señal según una política; el peso decide su importancia relativa. No deben contarse dos veces puntos que el motor ya hubiera asignado.

Ejemplo **propuesto**, distinto de la curva actual de RF001:

| Relación monto/promedio | Severidad de una curva elegida | Con peso 25 |
| --- | --- | --- |
| 1.2× | 0.0 | 0 |
| 2.0× | 0.3 | 8 |
| 4.0× | 0.8 | 20 |
| 5.0× o más | 1.0 | 25 |

Los puntos intermedios pueden calcularse mediante interpolación entre tramos versionados. La política debe definir límites exactos, redondeo y comportamiento para datos ausentes. El administrador ve una gráfica y ejemplos antes de publicar.

Para una regla binaria, la severidad puede seguir siendo 0 o 1. Añadir decimales sin una señal adicional real no aporta información.

## 4. Pantalla de administración con Thymeleaf

Ampliar `/reglas` o crear una pantalla administrativa con:

1. Catálogo y versión publicada.
2. Editor de parámetros por tipo de regla, con validación del backend.
3. Simulación sin persistir ni modificar saldos.
4. Comparación de versión actual y candidata sobre un conjunto autorizado de ejemplos.
5. Publicación explícita, historial de cambios y retorno a una versión previa.

ADMIN publica; ANALISTA puede consultar y comparar según permisos que se declaren. Los cambios requieren CSRF y rutas nuevas expresamente autorizadas. Esas capacidades y un modo de simulación sin persistencia aún no existen en la aplicación.

La publicación debería seleccionar un conjunto coherente de versiones para toda una evaluación. Un cambio a mitad del procesamiento no debe mezclar pesos de una versión con umbrales de otra.

## 5. Dónde puede aportar un LLM

### Ayuda para redactar reglas

El administrador escribe una intención, por ejemplo «aumentar la intensidad progresivamente al superar el gasto habitual». El LLM propone parámetros del esquema autorizado, ejemplos y explicación. El backend valida y una persona revisa/publica; el texto generado no se ejecuta directamente.

Este uso permite estudiar el proveedor con menor impacto operativo y puede empezar con datos ficticios.

### Evaluación contextual complementaria

El LLM recibe un conjunto mínimo de hechos calculados y una política versionada. Evalúa una combinación compleja y devuelve severidad y evidencias de una regla contextual concreta.

Ejemplo: cambios simultáneos de comportamiento, secuencias poco habituales o descripción de un patrón que necesite interpretación. Para comparar `monto > umbral`, una fórmula Java es más reproducible, rápida y fácil de probar.

Las comprobaciones obligatorias de permisos, saldo y límites de operación siguen siendo deterministas. La interpretación del modelo no debe autorizar herramientas que muevan dinero o modifiquen reglas.

## 6. Proveedor OpenRouter

El asistente implementado consume Chat Completions en `https://openrouter.ai/api/v1` con el modelo `qwen/qwen-plus` (Qwen Plus). La credencial corresponde a OpenRouter. [Modelo y API](https://openrouter.ai/qwen/qwen-plus).

Se solicita salida `json_object` y se mantiene validación estructural y semántica en Java. El JSON válido por sí solo no garantiza el contrato ni la corrección de las sugerencias. La configuración y límites del adaptador se describen en [Asistente IA](12_ASISTENTE_REGLAS_IA.md).

Antes de enviar datos reales, revisar las políticas de tratamiento y retención de OpenRouter y de los proveedores elegidos en su enrutamiento. [Tratamiento de datos](https://openrouter.ai/docs/guides/privacy/data-collection).

## 7. Entrada y salida acotadas

Preferir datos como relación monto/promedio, número de operaciones, categoría normalizada y señales booleanas. No enviar por defecto nombres, documentos, números de cuenta, claves, cookies, tokens CSRF o IP completas.

Asignar IDs a hechos calculados, por ejemplo F01 = relación 4× y F02 = dispositivo nuevo. Una salida ilustrativa:

```json
{
  "regla": "RC001",
  "version": 3,
  "severidad": 0.7,
  "evidencias": ["F01", "F02"],
  "abstencion": false,
  "motivo": "Combinación de incremento de monto y cambio de contexto"
}
```

Con peso 30, la severidad 0.7 aporta 21 puntos si la política híbrida permite aplicar ese factor. `RC001` es un código propuesto, no una regla actual del catálogo.

El servidor comprueba código/versión autorizados, rango, hechos existentes, campos inesperados, tamaño de salida y duplicados. Una explicación convincente no sustituye evidencia de entrada; referencias inventadas provocan rechazo o abstención.

La severidad no es probabilidad de fraude. Una confianza declarada por el propio modelo tampoco es una confianza calibrada; no debería ser la única puerta para afectar una decisión.

## 8. Integración y concurrencia

Flujo propuesto:

```text
hechos mínimos + conjunto de versiones
  → reglas deterministas
  → evaluador contextual, cuando corresponda
  → validación de salida y política de combinación
  → decisión calculada por el backend
  → transacción corta: revalidar datos críticos, bloquear cuenta y persistir
```

En el código actual se bloquea la cuenta antes de evaluar y persistir. **No se debe insertar una llamada de red lenta en ese tramo sin rediseñar el flujo.** Una petición al proveedor manteniendo el bloqueo aumentaría esperas y problemas de disponibilidad.

La inferencia debería ejecutarse fuera del bloqueo financiero, usando una instantánea de hechos/versiones. Antes de confirmar se revalidan saldo y hechos críticos. Si cambiaron, la política define si recalcular, abstenerse o enviar a revisión; nunca usar una respuesta obsoleta sin verificarla.

Crear un puerto de aplicación para evaluación contextual y un adaptador del proveedor. Si se introduce un paquete de infraestructura nuevo, actualizar las reglas ArchUnit manteniendo la dirección de dependencias y la composición desde configuración.

## 9. Disponibilidad, seguridad y trazabilidad

- Tiempo máximo y presupuesto de tokens por evaluación.
- Reintentos limitados, circuit breaker y modo degradado definido.
- Ante timeout, error, respuesta inválida o abstención, continuar con el motor determinista o la revisión establecida; no convertir el fallo en riesgo cero.
- Credencial únicamente en el servidor y almacén de secretos/configuración; nunca en HTML o JS.
- Tratar nombres y textos de usuario como datos no confiables, no instrucciones.
- No dar al modelo acceso libre a herramientas, repositorios de usuarios o publicación de reglas.
- Guardar proveedor, versión de modelo/prompt, versiones de reglas, IDs de hechos, respuesta validada, puntos aplicados y causa de fallback, con minimización de datos.
- Medir costo, latencia, abstención y divergencia frente al motor base.

Guardar una salida y su contexto permite auditar lo ocurrido; no garantiza que repetir una inferencia produzca exactamente el mismo resultado. Conviene fijar versiones y parámetros de generación compatibles y probar estabilidad.

## 10. Secuencia de implementación y aceptación

| Fase | Entrega | Criterio de aceptación |
| --- | --- | --- |
| 1 | Parámetros tipados, curvas y versiones | Fronteras probadas, mismo resultado reproducible y snapshots conservados |
| 2 | Administración Thymeleaf y publicación | Borradores, validación, permisos/CSRF, auditoría y rollback |
| 3 | Adaptador Qwen con datos ficticios | Esquema validado, secretos protegidos, timeout y fallback probados |
| 4 | Modo de observación | El LLM se registra/compara sin afectar saldos o decisiones del motor vigente |
| 5 | Evaluación con etiquetas y costos | Medir falsos positivos, fraude omitido, estabilidad, latencia y gasto |
| 6 | Influencia contextual acotada | Solo tras criterios explícitos de evaluación y política de combinación aprobada |

La propuesta inicial es implementar fases 1 y 2 como base. Un LLM puede ayudar a configurarlas o aportar contexto después; no es necesario para conseguir que los umbrales y la intensidad sean dinámicos.

## 11. Pruebas necesarias

Agregar límites de parámetros, publicación concurrente, selección consistente de versión, relectura histórica, permisos y CSRF. Para el adaptador: 429/5xx, timeout, JSON inválido, campos extra, severidad fuera de rango, referencias inventadas, inyección de instrucciones y cambio de modelo.

La evaluación de calidad debe usar un conjunto separado de ajuste y validación. Las pruebas de formato o de infraestructura no demuestran que el modelo detecte más fraude. Se necesitan criterios de negocio y datos etiquetados antes de afirmar esa mejora.

Fuentes del proveedor consultadas el 14 de noviembre de 2026. Su catálogo, regiones y condiciones pueden cambiar; confirmar las opciones de la cuenta antes de implementar o contratar.
