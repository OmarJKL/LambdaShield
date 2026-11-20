# 07 — Referencia de API REST

[Índice](README.md) · [Seguridad](06_SEGURIDAD.md)

## 1. Convenciones

- Base local: `http://localhost:8080`; prefijo de negocio `/api/v1`.
- Autenticación: cookie de sesión obtenida por POST `/login`.
- Escrituras: `Content-Type: application/json` y CSRF de la misma sesión.
- `id` en rutas: ID numérico interno; en evaluaciones corresponde a `idInterno` de la respuesta, no a la etiqueta TR.
- Fechas de filtro: `YYYY-MM-DD`.
- Riesgo en JSON: `bajo`, `medio`, `alto`, `critico`.
- Roles y estados de usuario: mayúsculas.
- Importes: números JSON provenientes de `BigDecimal`; usar un parser decimal en consumidores que requieran precisión para importes grandes.
- No hay paginación general ni contrato OpenAPI generado en el repositorio.

La autorización admite HEAD en las rutas API de lectura declaradas. No hay un permiso universal de OPTIONS, CORS entre orígenes ni autorización implícita para endpoints futuros.

## 2. Acceso, CSRF y logout

Estas rutas no pertenecen a `/api/v1`:

| Método y ruta | Entrada/salida |
| --- | --- |
| `GET /login` | HTML con formulario y token `_csrf` |
| `POST /login` | Formulario URL-encoded: `usuario`, `clave`, `_csrf`; redirección al éxito o `/login?error` al fallar |
| `POST /logout` | Cookie y token CSRF; invalida la sesión y redirige a `/login` |
| `GET /actuator/health` | Estado agregado de salud sin detalles; no necesita login |

Un cliente HTTP debe obtener el token, iniciar sesión conservando cookies y obtener un token renovado desde una página protegida antes de escribir. En las páginas protegidas el token y el nombre del encabezado aparecen en meta `_csrf` y `_csrf_header`. No existe actualmente `/api/session`, `/api/me` ni un endpoint público JSON exclusivo para CSRF.

El logout efectivo requiere POST. No se debe construir un flujo que dependa de que un GET cierre la sesión.

## 3. Inventario de endpoints

Además de los errores específicos, todos los endpoints protegidos pueden ser rechazados por autenticación, autorización o límite; las escrituras también por CSRF.

| Método | Ruta | Roles | Éxito |
| --- | --- | --- | --- |
| POST | `/api/v1/evaluation` | ADMIN, CLIENTE | 200 con evaluación |
| GET | `/api/v1/evaluation` | ADMIN, ANALISTA | 200 con lista |
| PATCH | `/api/v1/evaluation/{id}` | ADMIN | 204 |
| PATCH | `/api/v1/evaluation/{id}/activar` | ADMIN | 204 |
| GET | `/api/v1/metric` | ADMIN, ANALISTA | 200 con KPIs |
| GET | `/api/v1/metric/summary` | ADMIN, ANALISTA | 200 con resumen |
| GET | `/api/v1/metric/movement` | ADMIN, ANALISTA | 200 con serie |
| GET | `/api/v1/metric/recent` | ADMIN, ANALISTA | 200 con hasta ocho movimientos |
| GET | `/api/v1/rule` | ADMIN, ANALISTA | 200 con catálogo activo |
| PATCH | `/api/v1/rule/{codigo}/estado` | ADMIN + CSRF | 204; cuerpo `{"activa":true}` o `{"activa":false}` |
| GET | `/api/v1/report/export` | ADMIN, ANALISTA | 200 con archivo |
| GET | `/api/v1/user` | ADMIN | 200 con lista |
| GET | `/api/v1/user/{id}` | ADMIN | 200 con usuario |
| POST | `/api/v1/user` | ADMIN | 201 con usuario |
| PATCH | `/api/v1/user/{id}` | ADMIN | 200 con usuario |
| PATCH | `/api/v1/user/{id}/clave` | ADMIN | 204 |
| DELETE | `/api/v1/user/{id}` | ADMIN | 204 |
| GET | `/api/v1/notification?pagina=0` | CLIENTE | 200 con bandeja propia |
| PATCH | `/api/v1/notification/{id}/leer` | CLIENTE | 204 |
| PATCH | `/api/v1/notification/leer-todas` | CLIENTE | 204 |

No existen GET de evaluación por ID, GET REST del historial del cliente, edición REST de condiciones o pesos de reglas, creación REST de clientes ni transferencia a una cuenta externa.

## 4. Crear una evaluación

### Solicitud

```http
POST /api/v1/evaluation
Content-Type: application/json
X-CSRF-TOKEN: <token de la página y sesión actuales>
Cookie: JSESSIONID=<cookie de sesión>
```

```json
{
  "cliente": "C-001",
  "monto": 150.25,
  "categoria": "servicios",
  "hora": 12,
  "pais": "local",
  "dispositivo": "reconocido",
  "operacionesUltimaHora": 0,
  "beneficiario": "recurrente",
  "ip": "conocida",
  "beneficiarioNombre": "Destinatario de prueba"
}
```

El encabezado mostrado es el predeterminado; un cliente debe preferir el nombre publicado en `_csrf_header`.

| Campo | Tipo | Validación y significado |
| --- | --- | --- |
| `cliente` | string | No vacío; 1–20 caracteres `[A-Za-z0-9-]`; CLIENTE no elige con él el titular efectivo |
| `monto` | decimal | Obligatorio, > 0, hasta 16 enteros y 2 decimales |
| `categoria` | string/null | Opcional; hasta 40 letras o `_`; vacío/nulo se resuelve como `otros` |
| `hora` | entero | 0–23; CLIENTE usa la del servidor |
| `pais` | string | Obligatorio: `local`, `regional`, `alto_riesgo` |
| `dispositivo` | string | Obligatorio: `reconocido`, `nuevo`; CLIENTE lo deriva |
| `operacionesUltimaHora` | entero | 0–1000 en la entrada; CLIENTE lo vuelve a contar |
| `beneficiario` | string/null | Opcional: vacío, `recurrente`, `nuevo`; con nombre prevalece la resolución por historial |
| `ip` | string | Obligatorio: `conocida`, `nueva`; CLIENTE usa la conexión real |
| `beneficiarioNombre` | string/null | Opcional; hasta 150 caracteres |
| `numeroCuentaDestino` | string/null | Opcional; vacío o 6–50 dígitos, espacios o guiones |
| `bancoDestino` | string/null | Opcional; vacío, `lambda`, `bcp`, `bbva`, `interbank`, `scotiabank` |

`hora` y `operacionesUltimaHora` son primitivas Java y, si se omiten, pueden tomar 0 durante la deserialización. No debe interpretarse que todos los campos del ejemplo tienen una restricción `NotNull`.

Para CLIENTE deben enviarse valores formalmente válidos también en campos derivados: Bean Validation se ejecuta antes de reemplazarlos. Si el usuario no tiene código de cliente activo asociado, se devuelve 403. ADMIN utiliza señales manuales y puede indicar otro cliente existente.

### Respuesta

Ejemplo de forma del contrato; valores, cantidad de factores e IDs dependen de la base y del catálogo:

```json
{
  "id": "TR-00042",
  "clienteCodigo": "C-001",
  "clienteNombre": "Cliente de ejemplo",
  "monto": 150.25,
  "categoria": "servicios",
  "pais": "local",
  "hora": 12,
  "puntaje": 0,
  "nivel": "bajo",
  "estado": "Aprobada",
  "iconoEstado": "bi-check-circle-fill",
  "colorNivel": "var(--riesgo-bajo)",
  "fecha": "14/11/2026 12:30",
  "fechaHora": "2026-11-14T12:30:00",
  "factores": [],
  "idInterno": 42,
  "activa": true,
  "saldoDisponible": 4699.75
}
```

En una configuración con reglas activas, `factores` contiene elementos como:

```json
{
  "id": "dispositivo",
  "etiqueta": "Dispositivo nuevo",
  "puntaje": 15,
  "max": 15,
  "motivo": "Descripción de la señal evaluada"
}
```

`fechaHora` representa la fecha de evaluación sin offset en el DTO; `hora` corresponde a la hora de transacción evaluada. `saldoDisponible` es el saldo de cuenta al construir la respuesta: en un listado no equivale al saldo histórico posterior de cada operación.

`estado` es una etiqueta visual según riesgo. Para mostrar una evaluación desactivada se debe considerar `activa`; no se debe deducir el estado interno de la transacción solo de esa etiqueta.

Una operación retenida o bloqueada también responde 200 porque la evaluación se completó correctamente. Un débito aprobado sin fondos responde 400 y no completa una evaluación persistida. Una referencia de cliente inexistente responde 404; un catálogo inconsistente produce error interno genérico y rollback.

## 5. Listado y cambios de estado

### GET `/api/v1/evaluation`

Parámetro `activas`, booleano con valor por defecto `true`:

- `true`: solo evaluaciones activas.
- `false`: todas, incluidas activas y desactivadas; no significa «solo inactivas».

Devuelve una lista de `EvaluacionResponse`, ordenada por fecha e ID descendentes. Los filtros de monto, nivel y mes/año de la pantalla de transacciones se aplican localmente; no son parámetros de este endpoint.

### PATCH `/api/v1/evaluation/{id}`

No requiere body. Desactiva, registra fecha y revierte el movimiento si la transacción estaba aprobada. Repetir sobre una evaluación inactiva devuelve 404. No borra filas.

### PATCH `/api/v1/evaluation/{id}/activar`

No requiere body. Restaura el estado según el nivel guardado y reaplica el movimiento si corresponde. Ya activa: 400; no encontrada: 404; fondos insuficientes: 400.

Se exigen ID positivo y CSRF. No hay DELETE autorizado para evaluaciones. El reintento de POST de creación no tiene una clave de idempotencia y puede crear otra operación.

## 6. Métricas

### GET `/api/v1/metric`

KPIs de evaluaciones activas, independientes del periodo del dashboard:

```json
{
  "total": 10,
  "aprobadas": 4,
  "monitoreadas": 2,
  "enRevision": 1,
  "bloqueadas": 3,
  "puntajePromedio": 45.2
}
```

`aprobadas` cuenta BAJO; MEDIO aparece en `monitoreadas` aunque su transacción también esté aprobada financieramente.

### Parámetro común `days`

En `/summary`, `/movement` y `/recent`: entero 1–365, por defecto 365. Incluye hoy y los `days` anteriores; por tanto 7 produce ocho fechas y 365 puede producir 366.

### GET `/api/v1/metric/summary`

```json
{
  "saldo": 10000.25,
  "ingresos": 1000.10,
  "egresos": 500.05,
  "variacionSaldo": 5.3,
  "variacionIngresos": 10.0,
  "variacionEgresos": -2.1
}
```

Los montos de movimientos son solo APROBADA; el saldo suma cuentas activas. La variación de saldo usa una estimación de base inicial, mientras ingresos/egresos comparan rangos consecutivos. Las fórmulas están en [funcionalidades](02_FUNCIONALIDADES.md).

### GET `/api/v1/metric/movement`

Lista diaria con `fecha`, `ingreso` y `egreso`, incluidos días sin movimientos con ceros. La fecha se presenta como `dd/MM` hasta 30 días o `dd/MM/yy` para rangos mayores.

### GET `/api/v1/metric/recent`

Hasta ocho elementos ordenados por fecha e ID descendentes:

```json
{"fecha":"14/11/2026","descripcion":"Servicios · C-001","esIngreso":false,"monto":10.01}
```

## 7. Catálogo y reportes

### GET `/api/v1/rule`

Lista de reglas activas ordenada por código, con `codigo`, `nombre`, `descripcion`, `pesoMaximo` y `activa`. El cambio de estado usa `PATCH /api/v1/rule/{codigo}/estado` con un booleano `activa` obligatorio; devuelve 404 si la regla no existe y 400 si falta el estado. Solo ADMIN con CSRF puede cambiarlo. Los pesos activos deben ser enteros 0–100.

### GET `/api/v1/report/export`

| Parámetro | Tipo / valor por defecto |
| --- | --- |
| `formato` | `csv` por defecto; también `xlsx`, `pdf` |
| `desde` | Fecha opcional incluida |
| `hasta` | Fecha opcional incluida |
| `nivel` | Opcional: `bajo`, `medio`, `alto`, `critico` |
| `cliente` | Código de cliente opcional, comparación exacta |

Ejemplo: `/api/v1/report/export?formato=csv&desde=2026-11-01&hasta=2026-11-14&nivel=alto&cliente=C-001`.

Se exportan evaluaciones activas filtradas por fecha de evaluación. `desde > hasta` es inválido. Un nivel no enviado no filtra; para la API conviene omitirlo en lugar de enviar una cadena vacía que incumpla su patrón.

| Formato | Content-Type | Nombre de descarga |
| --- | --- | --- |
| CSV | `text/csv; charset=UTF-8` | `reporte_transacciones_lambda.csv` |
| XLSX | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` | `reporte_transacciones_lambda.xlsx` |
| PDF | `application/pdf` | `reporte_transacciones_lambda.pdf` |

El encabezado `Content-Disposition` solicita descarga. No hay un endpoint JSON de resumen/listado filtrado del reporte: la pantalla MVC prepara esos datos. Los archivos se generan completos en memoria.

## 8. Gestión de usuarios

### POST `/api/v1/user`

```json
{
  "usuario": "analista.demo",
  "nombres": "Analista de prueba",
  "correo": "analista.demo@example.test",
  "clave": "ClaveDeEjemplo123",
  "rol": "ANALISTA",
  "codigoCliente": null
}
```

La clave es un ejemplo ficticio para una base de desarrollo; no se recomienda usarla en una instalación real.

| Campo | Regla |
| --- | --- |
| `usuario` | 3–20 minúsculas, números, `.`, `_`, `-`; único |
| `nombres` | Obligatorio, hasta 120 |
| `correo` | Obligatorio, formato correo, hasta 150 y único |
| `clave` | Obligatoria, 8–72 caracteres en el DTO; BCrypt impone además sus límites de representación |
| `rol` | ADMIN, ANALISTA o CLIENTE |
| `codigoCliente` | CLIENTE requiere código existente; los demás pueden no tener vínculo |

La creación responde 201 y estado ACTIVO. No se recibe un `estado` de alta para controlar ese valor.

### PATCH `/api/v1/user/{id}`

Envía `nombres`, `correo`, `estado`, `rol` y `codigoCliente`. `estado` admite ACTIVO, BLOQUEADO o INACTIVO. No modifica nombre de usuario ni clave; no es un parche parcial por campo, pues los campos obligatorios deben acompañar la solicitud.

### PATCH `/api/v1/user/{id}/clave`

Body `{"clave":"OtraClaveDeEjemplo123"}`; éxito 204. Las sesiones con el hash anterior pierden acceso en la siguiente petición revalidada.

### GET y DELETE

GET de colección o por ID devuelve `id`, `usuario`, `nombres`, `correo`, `estado`, `rol`, `codigoCliente` y `ultimoAcceso`. No devuelve hash ni contraseña. DELETE elimina el usuario y sus roles, con protección de cuenta propia y último administrador. Las restricciones referenciales de otros datos pueden impedir una baja.

## 9. Errores y precedencia

| Estado | Causas habituales | Forma habitual |
| --- | --- | --- |
| 400 | DTO/parámetro inválido, body ilegible, saldo insuficiente, regla administrativa; también firewall | Aplicación: `errores`; firewall: no asumir ese mismo body |
| 401 | Identidad ausente, expirada o revocada | `{"error":"No autenticado"}` |
| 403 | Rol, ruta/método no declarado o CSRF inválido | `{"error":"..."}`; algunos rechazos del controlador pueden no tener body |
| 404 | Cliente, usuario o evaluación no encontrados en ruta autorizada | `{"errores":["..."]}` |
| 415 | Tipo de contenido no admitido en escritura JSON | `{"errores":["..."]}` |
| 429 | Ventana de solicitudes agotada | `error` y `Retry-After: 60` |
| 500 | Error no traducido, catálogo inconsistente u otro fallo interno | `{"errores":["Error interno del servidor"]}` |

Los controles anteriores al controlador pueden ganar precedencia: una escritura sin CSRF responde 403 aunque su body sea inválido; un límite agotado puede responder 429 antes de revalidar sesión. Una ruta no autorizada no garantiza un 404 de entidad o un 405 de método.

Ejemplos:

```json
{"error":"Token CSRF ausente o inválido"}
```

```json
{"errores":["El monto debe ser mayor que cero"]}
```

## 10. Ejemplo de cliente Python con cookie y CSRF

Ejemplo de uso local con un usuario ADMIN provisionado. Solo consulta reglas y cierra la sesión; no modifica saldos. Usa biblioteca estándar, no registra la clave y conserva la cookie. Los formularios y meta se analizan como HTML, sin asumir que el token sea texto estático.

```python
from getpass import getpass
from html.parser import HTMLParser
from http.cookiejar import CookieJar
from urllib.parse import urlencode
from urllib.request import build_opener, HTTPCookieProcessor, Request
import json

BASE = "http://localhost:8080"
http = build_opener(HTTPCookieProcessor(CookieJar()))

class Tokens(HTMLParser):
    def __init__(self):
        super().__init__()
        self.form = None
        self.meta = {}

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "input" and attrs.get("name") == "_csrf":
            self.form = attrs.get("value")
        if tag == "meta" and attrs.get("name") in ("_csrf", "_csrf_header"):
            self.meta[attrs["name"]] = attrs.get("content")

def html_tokens(response):
    parser = Tokens()
    parser.feed(response.read().decode("utf-8"))
    return parser

with http.open(BASE + "/login") as response:
    initial = html_tokens(response)
if not initial.form:
    raise RuntimeError("No se encontró el token del formulario")

payload = urlencode({"usuario": input("Usuario ADMIN: "), "clave": getpass(),
                     "_csrf": initial.form}).encode()
with http.open(Request(BASE + "/login", data=payload)) as response:
    if "/login" in response.geturl():
        raise RuntimeError("No se completó el acceso")
    current = html_tokens(response)
if not current.meta.get("_csrf"):
    raise RuntimeError("No se obtuvo un token de la página protegida")

with http.open(BASE + "/api/v1/rule") as response:
    print(json.load(response))

headers = {current.meta["_csrf_header"]: current.meta["_csrf"]}
with http.open(Request(BASE + "/logout", data=b"", headers=headers)):
    pass
```

Para una escritura JSON, usar la misma cookie y token renovado, `Content-Type: application/json` y el método autorizado. Un cliente de producción debe manejar explícitamente errores HTTP, expiración y reintentos; no reintentar ciegamente un POST financiero.

## Bandeja de notificaciones

`GET /api/v1/notification?pagina=0` devuelve `{ "items": [...], "noLeidas": 2 }`. Página inicial 0, rango 0–10000, hasta 20 elementos por página ordenados por fecha e ID descendentes. `noLeidas` cuenta todos los pendientes del usuario, incluidos los de otras páginas.

Cada elemento contiene `id`, `tipo` (`BLOQUEADA` o `RETENIDA`), `titulo`, `mensaje`, `operacion` (etiqueta TR), `monto`, `puntaje`, `fecha` (ISO con zona) y `leida`. El mensaje incluye los factores activados. No hay avisos para operaciones aprobadas.

`PATCH /api/v1/notification/{id}/leer` y `PATCH /api/v1/notification/leer-todas` no requieren cuerpo y sí exigen CSRF; devuelven 204. Leer un aviso ya leído es idempotente. Un ID inexistente o de otro usuario devuelve 404. La identidad se obtiene de la sesión y ADMIN/ANALISTA reciben 403. Desactivar una evaluación no borra el aviso del evento original.

## Consultar titular de cuenta interna

`GET /api/v1/account/destination?numero=0011-2026-0000000102` requiere sesión CLIENTE. Devuelve 200 con `nombre`, `numeroCuenta` y `banco` (`lambda`). No expone saldo, documento, correo ni identificadores internos. Solo busca el número completo, sin búsquedas parciales ni listados. El parámetro admite 6–34 caracteres numéricos, espacios y guiones; un formato inválido devuelve 400. Cuenta inexistente, inactiva, cliente desactivado o cuenta propia devuelve 404; otros roles reciben 403 y sin sesión 401. Se aplica el límite general de peticiones autenticadas.

La creación de una operación vuelve a comprobar el destino en el servidor; el autocompletado no sustituye esa validación. Un número inexistente con banco `lambda` se rechaza y los datos de contactos internos se actualizan con el titular real al usarlos.

## Asistente de reglas IA

`GET /api/v1/rule/assistant/status` y `POST /api/v1/rule/assistant/review` requieren ADMIN; POST también exige CSRF. La revisión devuelve definición original y análisis separados, sin publicar cambios. [Contrato, validaciones y errores](12_ASISTENTE_REGLAS_IA.md).

## Restricción de naturaleza para CLIENTE

En POST `/api/v1/evaluation`, seleccionar una categoría cuyo tipo sea CREDITO devuelve 400 para CLIENTE y no modifica saldo. ADMIN conserva el recorrido de pruebas separado. La activación del destinatario se verifica tanto por número como por nombre.
