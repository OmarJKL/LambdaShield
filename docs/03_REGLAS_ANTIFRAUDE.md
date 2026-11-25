# 03 — Motor de reglas antifraude

[Índice](README.md) · [Funcionalidades](02_FUNCIONALIDADES.md)

## 1. Modelo de evaluación

Cada implementación de `ReglaEvaluable` recibe `TransaccionEvaluable` y el promedio de gasto del cliente, y devuelve un `FactorEvaluado`: identidad, etiqueta, puntos, máximo de referencia y motivo.

`DeteccionFraudeService` ejecuta las implementaciones inyectadas. `CatalogoReglasService` relaciona sus factores con `regla_fraude`, ejecuta además las reglas definidas (RC), descarta las inactivas y reparte el 100 % del puntaje entre las activas según su peso (§11).

| Score | Nivel | Decisión | Estado de transacción |
| --- | --- | --- | --- |
| 0–29 | BAJO | APROBAR | APROBADA |
| 30–59 | MEDIO | MONITOREAR | APROBADA |
| 60–79 | ALTO | RETENER | RETENIDA |
| 80–100 | CRITICO | BLOQUEAR | BLOQUEADA |

Una decisión de riesgo no prueba fraude confirmado. El motor es heurístico y explicable; no es un modelo entrenado ni asigna una probabilidad estadística calibrada.

## 2. Catálogo e identidades

| Código | Identidad de factor | Regla | Máximo inicial/de referencia |
| --- | --- | --- | --- |
| RF001 | `monto` | Monto inusual | 25 |
| RF002 | `dispositivo` | Dispositivo nuevo | 15 |
| RF003 | `ip` | IP nueva | 10 |
| RF004 | `ubicacion` | País o región declarada | 30 |
| RF005 | `horario` | Horario inusual | 10 |
| RF006 | `velocidad` | Frecuencia de operaciones | 25 |
| RF007 | `viaje_imposible` | Heurística de país, frecuencia e IP | 40 |
| RF008 | `beneficiario` | Beneficiario nuevo con monto alto | 20 |

`FactorRegla` mantiene la relación estable código/identidad. Las implementaciones están en `domain/fraud/rule`; `FraudRuleConfiguration` registra sus beans sin anotar las reglas con Spring.

Los valores de las siguientes secciones son los puntos de referencia, antes del reparto porcentual (§11).

## 3. RF001 — Monto inusual

Se calcula `proporcion = monto / promedio`. Si el promedio no es positivo, esta regla utiliza 100 como respaldo.

| Proporción | Puntos antes de redondear |
| --- | --- |
| ≤ 1.5 | 0 |
| > 1.5 y ≤ 3 | Interpolación lineal de 5 a 15 |
| > 3 y < 5 | Interpolación lineal de 15 a 22 |
| ≥ 5 | 25 |

El resultado se redondea a entero con `Math.round`. Hay saltos intencionales entre tramos: en 1.5 exacto son 0 puntos y justo por encima se parte de 5; en 5 exacto se asignan 25 en lugar de continuar hasta 22.

El promedio es el campo `cliente.promedio_gasto`. El código no recalcula automáticamente una media móvil de 30 días, aunque algunos motivos incluyan ese lenguaje histórico.

## 4. RF002 — Dispositivo nuevo

Asigna 15 cuando `dispositivo` es `nuevo`, y 0 en otro caso válido.

Para CLIENTE la señal se deriva de `DeviceFingerprint`: combina código del cliente con navegador y sistema operativo normalizados a partir del User-Agent. `DispositivoService` busca la huella exacta y admite el formato antiguo con sufijo UUID; al reutilizarlo lo normaliza y actualiza el último uso.

Un dispositivo nuevo no se vuelve confiable automáticamente por repetir solicitudes. La señal depende de `es_conocido`. La normalización no identifica físicamente un dispositivo ni verifica criptográficamente al navegador; el User-Agent es declarable y varios equipos pueden compartir la misma huella.

El simulador sin User-Agent usa identidades separadas `Simulador|Reconocido` o `Simulador|Nuevo`.

## 5. RF003 — IP nueva

Asigna 10 cuando la señal IP es `nueva`, y 0 cuando es `conocida`.

Para CLIENTE se consulta la dirección remota de la conexión. Es conocida si aparece en un dispositivo conocido o en una transacción previa del cliente. Esa búsqueda histórica no exige que la transacción previa esté aprobada; por tanto no es una lista de IP verificadas por una autenticación adicional.

No se confía directamente en un encabezado `X-Forwarded-For` enviado por el cliente. Detrás de un proxy, la dirección observada debe interpretarse según la configuración real del despliegue.

## 6. RF004 — Ubicación

| `pais` | Puntos |
| --- | --- |
| `local` | 0 |
| `regional` | 15 |
| `alto_riesgo` | 30 |

La ubicación se declara en el formulario; no se deriva de una base GeoIP. La persistencia representa `local` como PE, `regional` como CL y alto riesgo como XX; la ciudad se guarda actualmente como Lima. Estos datos son representaciones simplificadas, no geolocalización validada.

## 7. RF005 — Horario inusual

Asigna 10 si la hora está entre 0 y 4 inclusive; de 5 a 23 asigna 0. La hora del portal la decide el servidor; ADMIN puede introducirla manualmente.

La fecha de transacción se guarda con la hora evaluada y minutos/segundos en cero. La fecha de evaluación conserva el instante del procesamiento. Los reportes filtran por fecha de evaluación; los movimientos del dashboard usan fecha de transacción.

## 8. RF006 — Frecuencia

| Operaciones previas en la última hora | Puntos |
| --- | --- |
| 0–1 | 0 |
| 2 | 5 |
| 3 | 12 |
| 4 | 15 |
| 5 | 20 |
| ≥ 6 | 25 |

Los tramos 2–3 y 4–5 se expresan en código mediante interpolación. Para CLIENTE se cuentan evaluaciones activas posteriores a `ahora - 1 hora`, antes de insertar la operación actual. No se cuentan todos los intentos HTTP ni los errores de validación; las evaluaciones desactivadas quedan fuera.

Este conteo y los límites HTTP son mecanismos distintos. El bloqueo de cuenta protege el saldo, pero no convierte este cálculo previo de frecuencia en un contador atómico de todos los intentos concurrentes.

## 9. RF007 — Viaje imposible

Las condiciones se evalúan en este orden:

1. País `regional` o `alto_riesgo` y frecuencia ≥ 2: 40 puntos.
2. Si no se cumplió lo anterior, país `alto_riesgo` e IP nueva: 28 puntos.
3. En otro caso: 0.

No se calculan distancia, velocidad de desplazamiento ni coordenadas. La regla detecta una combinación de señales simplificadas y no demuestra físicamente un viaje imposible.

## 10. RF008 — Beneficiario nuevo y monto alto

El umbral es `promedio × 1.5` cuando el promedio es positivo. Si no lo es, el umbral de respaldo es 500. **No existe un mínimo general de 500** para clientes con promedio positivo.

Se asignan 20 puntos si el beneficiario es nuevo y `monto > umbral`. En el umbral exacto son 0; un recurrente también recibe 0.

Con número de destino se busca primero por cliente y cuenta. Los contactos precargados se reconocen como conocidos; el servidor usa sus datos guardados. Para cuentas internas nuevas resuelve el titular real y rechaza la cuenta propia; para cuentas externas exige nombre al crear el contacto. La existencia del contacto no prueba una transferencia previa aprobada ni una titularidad externa verificada.

Sin número, con nombre de destinatario se busca por cliente y nombre recortado: la primera creación queda como nueva y una siguiente resolución marca ese registro como conocido. No se exige que una operación anterior haya sido aprobada. La identidad no se basa en una cuenta bancaria verificada ni se normaliza ignorando mayúsculas.

Sin nombre, el resolver admite beneficiarios genéricos según el tipo declarado. La pantalla del portal envía nombre y cuenta, pero el DTO conserva ambos opcionales para compatibilidad; no debe afirmarse que todos los clientes HTTP obtengan siempre un beneficiario verificado por historial nominal.

## 11. Reparto porcentual de pesos y errores de catálogo

El peso de cada regla es relativo. Las reglas activas se reparten el 100 % del puntaje en proporción a su peso:

```text
maximo_i = redondear_HALF_UP(peso_i × 100 / Σ pesos activos)
puntos_i = redondear_HALF_UP(puntosDominio_i / maximoDominio_i × peso_i × 100 / Σ pesos activos)
score    = min(100, Σ puntos_i)
```

Los puntos se calculan desde la fracción exacta, no desde el máximo redondeado. El tope de 100 solo actúa por redondeo: si todas las señales llegan a su máximo, el puntaje es 100. Activar, desactivar o crear una regla cambia la suma y reajusta la participación de todas las demás.

Con los pesos iniciales (25, 15, 10, 30, 10, 25, 40 y 20; suma 175), RF004 participa con el 17,1 %. Al activar una RC de peso 25 (suma 200) baja al 15,0 % y la RC tiene el 12,5 %. Si después se desactiva RF007 (suma 160), RF004 sube al 18,8 %.

El peso de BD debe ser un entero entre 0 y 100, aunque su columna sea decimal. Una regla activa con peso 0 conserva su factor con 0 % de participación; una inactiva no aparece entre los factores aplicados.

Ejemplo: RF002 con señal nueva produce 15/15 en dominio. Con peso BD 30 (suma 190) aporta 16 de 100.

Se rechaza la evaluación si falta una regla correspondiente a un factor, si una regla activa no tiene implementación (Java o definición RC válida) o si el peso activo no es válido. El error no se transforma en una evaluación incompleta aceptada. Las fechas de vigencia no se interpretan como parámetros del motor.

Este reparto empezó el 25/11/2026 (`versionModelo` 2.0). Con la escala anterior los pesos se sumaban con tope 100, por lo que las mismas señales daban puntajes mayores: la operación de prueba con 132 de 175 puntos de referencia pasaba de 100 a 77 %. Las evaluaciones anteriores conservan su puntaje y nivel.

## 12. Conservación histórica

Cada `evaluacion_regla` guarda:

- `peso_aplicado`: puntos obtenidos, no el peso máximo.
- `maximo_aplicado`: máximo configurado al evaluar.
- `etiqueta_aplicada`: nombre mostrado al evaluar.
- `version_aplicada`: versión de catálogo utilizada.
- `se_disparo` y `valor_detectado`: condición y motivo, este último limitado a 250 caracteres al persistir.

Cambiar nombre, peso o activación del catálogo no reescribe esas copias nuevas. Registros anteriores sin copia usan el catálogo como respaldo; la migración rellena lo que puede con la configuración disponible al ejecutarse.

Reactivar una evaluación conserva su nivel guardado; no genera otro score ni aplica los pesos vigentes de nuevo. `versionModelo` se registra como 2.0 desde el reparto porcentual (1.0 en evaluaciones anteriores) y `duracionMs` como 0; no son prueba de una medición de latencia.

## 13. Ejemplo completo

Con pesos iniciales: promedio 180, monto 900, dispositivo nuevo, IP nueva, país regional, hora 2, tres operaciones previas y beneficiario nuevo:

| Regla | Puntos de referencia | Peso | Aporta (de 100) |
| --- | --- | --- | --- |
| RF001 | 25/25 | 25 | 14 |
| RF002 | 15/15 | 15 | 9 |
| RF003 | 10/10 | 10 | 6 |
| RF004 | 15/30 | 30 | 9 |
| RF005 | 10/10 | 10 | 6 |
| RF006 | 12/25 | 25 | 7 |
| RF007 | 40/40 | 40 | 23 |
| RF008 | 20/20 | 20 | 11 |
| Score | | 175 | 85 |

Resultado: CRITICO, BLOQUEAR, transacción BLOQUEADA y sin descuento de saldo. Un escenario habitual sin señales obtiene 0 y puede aprobarse, siempre que se cumplan las demás condiciones del caso de uso, incluido el saldo disponible para un débito.

## 14. Reglas definidas (RC)

Se crean desde el formulario de `/reglas` con `POST /api/v1/rule` y quedan **inactivas**. Su definición se guarda en `regla_fraude.parametros_json`; el peso del formulario se guarda en `peso` y la descripción es un resumen legible de las condiciones.

| Elemento | Valores admitidos |
| --- | --- |
| Campos | MONTO, RATIO_PROMEDIO (monto ÷ `promedio_gasto`), HORA, OPERACIONES_ULTIMA_HORA, PAIS, DISPOSITIVO, IP, BENEFICIARIO |
| Operadores | Numéricos: >, ≥, <, ≤, =. Categóricos: solo = con valores del catálogo |
| Combinación | TODAS (y) o CUALQUIERA (o), entre 1 y 8 condiciones |
| Peso | Entero 1–100, relativo como el de las reglas RF |

La regla recibe los mismos datos que las RF. Para CLIENTE, hora, frecuencia, dispositivo e IP los deriva el servidor. Es binaria: si se cumple aporta todo su máximo y su motivo enumera las condiciones cumplidas. Sin promedio guardado, una condición de RATIO_PROMEDIO no se cumple. No se ejecutan expresiones, SQL ni código recibidos del navegador: cada campo y operador se interpreta en Java.

Una RC puede repetir una señal que ya puntúa una RF (por ejemplo, «dispositivo nuevo» y RF002) y sumar dos veces esa señal. Conviene revisarlo con el asistente y en el campo de desarrolladores antes de activarla.

Las RC no se editan: para cambiar una definición se crea otra regla y se desactiva la anterior. Las evaluaciones guardadas conservan etiqueta, máximo y versión de cada RC aunque después se desactive.
