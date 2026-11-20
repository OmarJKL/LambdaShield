# 12 — Asistente de reglas con Qwen Plus mediante OpenRouter

[Índice](README.md) · [Evolución del motor](10_PROPUESTA_REGLAS_DINAMICAS_LLM.md)

## Alcance implementado

El administrador abre `/reglas` y completa un formulario de definición. El backend valida sus campos antes de solicitar una revisión a OpenRouter. Devuelve la definición original separada de la interpretación, advertencias, sugerencias y casos propuestos por el modelo.

Esta entrega no publica reglas, no ejecuta los casos sugeridos, no cambia las ocho reglas RF001–RF008 y no añade inferencia a las transacciones financieras. El resultado es un borrador temporal en el navegador; puede descargarse como JSON. Recargar la página lo descarta. Una revisión generada no demuestra que una regla sea correcta ni que detecte fraude.

## Configuración local

En `.env`, que está excluido de Git:

```dotenv
LLM_ENABLED=true
LLM_API_KEY=
LLM_BASE_URL=https://openrouter.ai/api/v1
LLM_MODEL=qwen/qwen-plus
LLM_TIMEOUT_SECONDS=25
LLM_DISABLE_THINKING=true
```

Crear una clave de OpenRouter y completar `LLM_API_KEY` en el archivo local, nunca en HTML, commits, documentos o mensajes. La clave anterior de Alibaba no sirve para esta integración. No incluir `/chat/completions` en la URL: el adaptador lo agrega.

Solo se admite `https://openrouter.ai/api/v1`, con puerto predeterminado o 443 y barra final opcional. Se rechazan otros hosts, rutas, credenciales en URL, query o fragmento y no se siguen redirecciones. La URL nunca se toma del cuerpo de una solicitud del navegador.

Reiniciar Spring Boot después de editar `.env`. El indicador **Configurado** verifica presencia y formato de configuración, no la validez de la clave ni la disponibilidad del modelo. `detalle` señala la variable pendiente o el formato incorrecto sin revelar sus valores. La primera revisión permite comprobar el acceso real.

Se usa `qwen/qwen-plus`, el identificador de Qwen Plus en OpenRouter, sujeto a disponibilidad y saldo de la cuenta. Se solicita salida `json_object` y se valida el contrato en Java. `LLM_DISABLE_THINKING=true` envía `reasoning: {"enabled": false}`; con `false` omite ese parámetro. No se envía el parámetro específico de Alibaba `enable_thinking`.

La aplicación no configura límites de gasto en la cuenta. HTTP 402 produce un mensaje de créditos insuficientes; 401/403 indican revisar clave y permisos; 429 indica cuota o límite de solicitudes. No se muestra el cuerpo del proveedor. Referencias oficiales: [Qwen Plus](https://openrouter.ai/qwen/qwen-plus), [razonamiento](https://openrouter.ai/docs/guides/best-practices/reasoning-tokens), [errores](https://openrouter.ai/docs/api/reference/errors-and-debugging). Consultado el 20/11/2026.

## Formulario y significado técnico

Nombre obligatorio de hasta 150 caracteres; objetivo opcional de hasta 1000; puntos enteros 1–100; combinación `TODAS` (AND) o `CUALQUIERA` (OR); entre una y ocho condiciones.

| Campo | Valores y significado |
| --- | --- |
| `MONTO` | Importe PEN no negativo, hasta dos decimales |
| `RATIO_PROMEDIO` | Monto dividido por el promedio de gasto guardado en cliente; no existe aquí un promedio móvil calculado |
| `HORA` | Entero 0–23, hora del servidor; su zona depende de la configuración del proceso |
| `OPERACIONES_ULTIMA_HORA` | Entero 0–1000, evaluaciones activas previas, incluidas bloqueadas y retenidas |
| `PAIS` | `local`, `regional`, `alto_riesgo`; valor declarado, no geolocalización |
| `DISPOSITIVO` | `reconocido`, `nuevo`; huella derivada de User-Agent |
| `IP` | `conocida`, `nueva`; conexión real comparada con registros previos |
| `BENEFICIARIO` | `recurrente`, `nuevo`; un contacto recurrente no prueba una transferencia anterior aprobada |

Los numéricos admiten `MAYOR`, `MAYOR_IGUAL`, `MENOR`, `MENOR_IGUAL`, `IGUAL`; las categorías admiten solo `IGUAL` y valores del catálogo. Los valores se transportan como strings, se validan como decimales y no contienen expresiones ejecutables. Contradicciones o datos ausentes se revisan como observaciones; no existe publicación automática ni política de decisión nueva.

Cambiar un campo invalida la revisión anterior y deshabilita su descarga. Si el formulario cambia durante una consulta, se descarta el resultado recibido y se solicita revisar nuevamente.

## Contrato API

Solo ADMIN, con cookie de sesión. Escrituras protegidas por CSRF:

| Método y ruta | Resultado |
| --- | --- |
| `GET /api/v1/rule/assistant/status` | `{ "disponible": false, "detalle": "Configuración pendiente" }` (o disponible `true`); no expone valores de configuración |
| `POST /api/v1/rule/assistant/review` | Definición original y revisión separadas |

Ejemplo de entrada:

```json
{
  "nombre": "Monto alto desde dispositivo nuevo",
  "objetivo": "Revisar límites y casos de prueba",
  "combinacion": "TODAS",
  "puntos": 25,
  "condiciones": [
    { "campo": "RATIO_PROMEDIO", "operador": "MAYOR", "valor": "3" },
    { "campo": "DISPOSITIVO", "operador": "IGUAL", "valor": "nuevo" }
  ]
}
```

Respuesta: `{ "definicion": <entrada>, "revision": { "interpretacion": "...", "advertencias": [], "sugerencias": [], "pruebas": ["...", "..."] } }`.

Errores: 400 validación local; 401 sin sesión; 403 rol o CSRF; 429 límite local; 503 configuración ausente, ocupación, conectividad, acceso rechazado o cuota del proveedor; 502 respuesta inválida/truncada u otro error del proveedor. Los cuerpos del proveedor y la API key no se devuelven al navegador ni se registran desde el adaptador.

## Transporte y controles

El adaptador Java usa `HttpClient` directamente, sin nuevas dependencias ni acceso a repositorios bancarios. Envía solo el formulario y un diccionario técnico fijo en el prompt. El administrador debe evitar datos personales en los campos libres. El prompt trata esos campos como datos no confiables y exige conservar la definición; la respuesta del modelo solo se representa como texto, sin ejecutarla ni usarla para decisiones financieras.

Se solicita JSON Object y se vuelve a validar en Java: exactamente cuatro campos, interpretación no vacía hasta 1500 caracteres, hasta seis observaciones/sugerencias/casos de 500 caracteres y al menos dos casos. Se rechazan respuestas incompletas, tipos incorrectos, campos extra y finalización distinta de `stop`. La validación de forma no garantiza fidelidad semántica: el administrador debe revisar el análisis.

Máximo de salida solicitado: 1600 tokens. Dos consultas simultáneas por instancia, sin reintentos automáticos; conexión hasta cinco segundos y límite total de consulta configurable entre 1 y 60 segundos. Se limita el cuerpo de respuesta a 64 KiB. Por defecto se permiten seis revisiones por minuto y usuario, además del límite general. La propiedad del servidor es `security.rate-limit.assistant-per-minute`. Estos límites son locales por instancia, no un presupuesto global de facturación.

## Verificación y pendientes

Pruebas del adaptador con transporte simulado: hosts, errores, salida estructurada, truncamiento y timeout. Pruebas MVC: roles, CSRF, condiciones inválidas y separación entre definición y sugerencias. Pruebas JavaScript: controles tipados, cantidad de condiciones, revisión invalidada al editar y mensajes del proveedor.

La prueba real requiere configurar una clave de OpenRouter válida y ejecutar una revisión ficticia. Las pruebas simuladas no demuestran conexión real, calidad del modelo ni cuota disponible. Sigue pendiente publicar/activar nuevas reglas, conservar versiones y revisiones en BD, ejecutar comparaciones y evaluar transacciones mediante el LLM. Esas capacidades se describen en la propuesta 10.


## Migración a OpenRouter (20/11/2026)

Se reemplazó el adaptador de Alibaba por OpenRouter, manteniendo Qwen Plus. Se actualizaron el endpoint permitido, el identificador del modelo, el parámetro de razonamiento y los mensajes de error. La prueba anterior de Alibaba del 18/11/2026 terminó en HTTP 403 y no valida esta integración.

Las pruebas automatizadas usan transporte simulado y claves ficticias. La conectividad e inferencia reales con OpenRouter quedan pendientes de configurar una clave válida y ejecutar una revisión ficticia.
