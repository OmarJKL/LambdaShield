# 02 — Funcionalidades, pantallas y flujos

[Índice](README.md) · [Reglas](03_REGLAS_ANTIFRAUDE.md) · [API](07_API_REST.md)

## 1. Objetivo y alcance

Cada operación se evalúa antes de aplicar su efecto en la cuenta local. El resultado combina un puntaje de 0 a 100, un nivel de riesgo y factores explicativos. Los usuarios autorizados pueden consultar el historial y generar reportes.

El proyecto simula operaciones bancarias en una base de datos. Un beneficiario es un registro descriptivo; aprobar una operación no acredita una cuenta receptora ni ejecuta una transferencia interbancaria real.

## 2. Roles e identidad

| Rol | Capacidades de interfaz |
| --- | --- |
| CLIENTE | Cuenta, saldo, formulario de operación, historial propio y avisos |
| ANALISTA | Dashboard, historial, catálogo y reportes |
| ADMIN | Administración, simulador, usuarios, desactivación y reactivación |

`UsuarioSistema` identifica a quien inicia sesión. `Cliente` identifica al titular bancario y se vincula mediante `usuario_sistema.id_cliente`. Puede haber varios usuarios relacionados con un mismo cliente. Si una identidad tiene varios roles activos, prevalece ADMIN, luego ANALISTA y finalmente CLIENTE; sin ninguno se rechaza el acceso.

## 3. Acceso y cierre de sesión

1. `GET /login` muestra el formulario Thymeleaf e incluye un token CSRF.
2. Spring Security procesa `POST /login` con los campos `usuario`, `clave` y el token.
3. `AuthService` normaliza el usuario, comprueba BCrypt, estado activo y rol disponible.
4. Se renueva el identificador de sesión y se almacena un contexto de seguridad autenticado.
5. CLIENTE se redirige a `/operacion`; los demás roles, a `/`.
6. En posteriores peticiones se vuelve a comprobar la identidad y se actualizan rol y vínculo de cliente.
7. El formulario `POST /logout`, protegido por CSRF, invalida la sesión y elimina la cookie de sesión.

Un login fallido redirige a `/login?error`; no se revela si el usuario existe. Un exceso de intentos puede responder `429`. No hay registro público, recuperación de clave por correo ni MFA.

## 4. Pantallas actuales

### 4.1 Portal de operación — `/operacion`

El controlador prepara los datos del cliente de la sesión autenticada: número de cuenta, saldo, evaluaciones activas propias y agenda de beneficiarios con número de cuenta. Sin un cliente asociado no se usa un historial global como respaldo.

El formulario permite elegir un contacto guardado o introducir destinatario, banco y número de cuenta, además de monto, categoría y país declarado. JavaScript envía el contrato completo que exige la API, aunque el servidor reemplaza varias señales para CLIENTE.

Tras una respuesta correcta, la página muestra un recibo, añade la operación al historial y sustituye el saldo visible por `saldoDisponible` devuelto por el servidor. Una retención o un bloqueo se registran como evaluaciones, pero no descuentan saldo. La vista del portal omite evaluaciones desactivadas.

La campana muestra avisos persistentes de operaciones BLOQUEADA o RETENIDA. Incluye contador de pendientes, fecha relativa, detalle expandible, lectura individual y marcar todas como leídas. Consulta páginas de 20 elementos y se actualiza al abrir, al volver a la pestaña, después de una operación y cada 15 segundos si la pestaña está visible y la bandeja cerrada. La lectura pertenece al usuario, incluso si dos usuarios comparten cliente. No envía correo/SMS ni sustituye la investigación de casos.

### 4.2 Dashboard — `/`

Thymeleaf entrega el resumen inicial y la tabla reciente. `panel.js` consulta las APIs para cambiar el periodo y actualizar gráfico, resumen y actividad. Los ingresos y egresos proceden solo de transacciones aprobadas.

El saldo es la suma actual de cuentas activas. Las variaciones de ingresos/egresos comparan dos rangos de igual longitud; la variación de saldo usa una estimación de saldo inicial, no un saldo histórico auditado. Ver la sección 8.

### 4.3 Transacciones — `/transacciones`

JavaScript carga evaluaciones con `activas=false`, que significa incluir activas y desactivadas, y obtiene KPIs globales de evaluaciones activas.

Permite filtrar localmente por nivel, monto, mes/año o desactivadas. También muestra factores y el historial del cliente dentro del conjunto cargado. El indicador «Total evaluado» suma montos de evaluaciones; no representa el saldo disponible de la cuenta.

ADMIN dispone de desactivación y reactivación mediante confirmación. ANALISTA consulta sin poder modificar. Los filtros locales no cambian automáticamente el ámbito de los KPIs globales.

### 4.4 Catálogo de reglas — `/reglas`

Muestra reglas activas e inactivas en una tabla con código, nombre, descripción, peso relativo, participación porcentual y estado. Las reglas creadas desde el formulario llevan la etiqueta «Definida». Permite buscar sin distinguir tildes y filtrar por estado, con páginas de 10, 25 o 50 filas. ADMIN puede activar o desactivar; antes de cualquiera de los dos cambios se muestra un modal con la participación de cada regla antes y después. ANALISTA consulta. El formulario de creación de borradores está en un panel desplegable debajo del catálogo. Cambiar pesos o condiciones de las reglas vigentes requiere un procedimiento administrativo externo a esta interfaz.

### 4.5 Campo para desarrolladores — `/simulador`

Solo ADMIN. Thymeleaf entrega el selector de clientes, sus promedios, las reglas activas con su participación y las reglas inactivas que se pueden probar. El administrador declara las señales de riesgo y elige un modo:

- **Probar sin guardar** (predeterminado): no crea transacciones, evaluaciones, contactos, dispositivos ni categorías y no mueve saldo. Permite marcar reglas candidatas (inactivas) y un borrador traído desde `/reglas`; el resultado compara el puntaje y nivel del catálogo vigente con los del catálogo con candidatas, e indica qué pasaría con el saldo si se registrara. El lote evalúa 50 escenarios aleatorios de todos los titulares en una sola solicitud y resume cuántos cambian de nivel.
- **Registrar en la base de datos**: envía la operación al mismo caso de uso persistente que antes.

El resultado incluye puntaje, estado, saldo y desglose de las reglas aplicadas. El radar se muestra para entre 3 y 12 factores; los demás casos conservan el detalle. Se pueden preparar escenarios habituales, de dispositivo/IP nuevos y de múltiples señales sin enviarlos. El tráfico automático usa el titular seleccionado entre los clientes cargados, registra hasta diez operaciones y espera seis segundos después de cada respuesta antes de continuar. Evita solicitudes simultáneas y se detiene al fallar o salir de la página. Cada evaluación se guarda y puede descontar saldo si se aprueba; se recomienda usar cuentas de demostración. El enlace aparece después de Usuarios y solo está disponible para ADMIN.

La hora del escenario define la fecha de la transacción: si es posterior a la hora actual, se registra el día anterior, nunca en el futuro.

**El modo registro tiene efectos reales en la base local:** crea evaluaciones, modifica saldos cuando corresponde y consume límites API. No es un modo de cálculo sin persistencia.

### 4.6 Reportes — `/reportes`

El servidor aplica filtros de fecha de evaluación, nivel y código de cliente; entrega la tabla y su resumen. JavaScript sincroniza enlaces para exportar los mismos filtros a CSV, XLSX o PDF.

Solo se incluyen evaluaciones activas. El intervalo de fechas incluye ambos días; `desde` posterior a `hasta` se rechaza. La consulta no está paginada y los exportadores construyen el archivo en memoria.

### 4.7 Usuarios — `/usuarios`

Solo ADMIN. La interfaz consume la API para listar, crear, editar, cambiar claves y eliminar usuarios.

- Usuario y correo deben ser únicos según las comprobaciones del servicio y las restricciones del esquema.
- El nombre de usuario se normaliza al crear; el contrato de edición no lo cambia.
- Un usuario nuevo se crea ACTIVO; el formulario de alta no muestra el selector de estado.
- CLIENTE requiere un código de cliente existente; crear el usuario no crea la entidad bancaria.
- Cambiar rol o estado no debe dejar al sistema sin otro administrador activo.
- No se puede eliminar al usuario que realiza la acción.
- La eliminación es física e incluye sus asignaciones de rol. Si el usuario tiene auditoría, alertas o casos asociados, se responde 409 y no se elimina; en ese caso se puede bloquear.
- Cambiar clave, bloquear o eliminar hace que las sesiones anteriores pierdan acceso en la siguiente petición admitida.

La protección de la cuenta propia se refiere a la eliminación. No significa que todas las modificaciones del propio perfil estén prohibidas.

## 5. Creación de una operación

### 5.1 Entrada y derivación

Para ADMIN se aceptan señales manuales dentro del contrato validado. Para CLIENTE:

| Dato | Fuente efectiva |
| --- | --- |
| Cliente | Vínculo de la identidad revalidada; el body no elige otro cliente |
| Hora | Reloj del servidor |
| Frecuencia | Evaluaciones activas de ese cliente en la última hora |
| Dispositivo | Comparación de User-Agent normalizado con registros conocidos |
| IP | Dirección de conexión comparada con historial y dispositivos |
| Tipo de beneficiario | Con cuenta, se resuelve por titular y número; sin cuenta se conserva el modo por nombre/genérico |
| Monto, categoría y país | Declarados por el formulario |

La validación del DTO ocurre antes de sustituir las señales. Por ello el cliente HTTP debe enviar valores de formato válido también en los campos que después serán derivados.

### 5.2 Coordinación transaccional

1. Buscar el cliente y su primera cuenta ACTIVA, bloqueándola para escribir. Sin cuenta activa se responde 400.
2. Para CLIENTE, exigir que la categoría exista en el catálogo y esté activa; el portal no crea categorías nuevas.
3. Resolver/crear referencias de tipo, canal, dispositivo y beneficiario.
4. Ejecutar reglas y aplicar el catálogo vigente.
5. Repartir el 100 % del puntaje entre las reglas activas según su peso, sumar (con tope de seguridad 100 por redondeo) y determinar nivel, decisión y estado.
6. Aplicar el movimiento solo si el estado es APROBADA.
7. Guardar transacción, evaluación y factores en la misma transacción.
8. Responder con el resultado y el saldo actual de la cuenta.

Si ocurre un error, se revierten las escrituras de esa transacción. Un rechazo por saldo insuficiente no se guarda como una evaluación nueva completada.

## 6. Estados, saldo y precisión

| Nivel | Decisión | Estado persistido | Movimiento |
| --- | --- | --- | --- |
| BAJO | APROBAR | APROBADA | Se aplica |
| MEDIO | MONITOREAR | APROBADA | Se aplica |
| ALTO | RETENER | RETENIDA | No se aplica |
| CRITICO | BLOQUEAR | BLOQUEADA | No se aplica |

`MovimientoSaldoService` suma créditos y resta débitos; un reverso aplica el signo contrario. Los tipos creados por defecto para las operaciones de formulario son débitos. Una retención no descuenta ni reserva una cantidad del saldo.

Los importes de cuenta, operación, resultados y agregados financieros usan `BigDecimal`. El scoring relativo usa `double`, y la interfaz utiliza números JavaScript para presentación. Esto no equivale a precisión decimal arbitraria en todo navegador.

El texto `estado` de la respuesta HTTP es una etiqueta según riesgo. Para distinguir una evaluación desactivada se debe consultar también `activa`; el estado interno de su transacción es REVERSADA.

## 7. Desactivación y reactivación

### Desactivar

`PATCH /api/v1/evaluation/{id}` bloquea la evaluación y después su cuenta. Si estaba aprobada se aplica el reverso; luego se marca `es_activa=false`, se registra fecha de desactivación y la transacción queda REVERSADA.

Desactivar una retenida o bloqueada no devuelve dinero porque no lo había descontado. Repetir la desactivación de una evaluación ya inactiva devuelve `404`, sin otro reverso.

### Reactivar

`PATCH /api/v1/evaluation/{id}/activar` restaura el estado correspondiente al nivel guardado. No recalcula el riesgo con reglas actuales. Si vuelve a APROBADA, reaplica el movimiento y actualiza los saldos anterior/posterior registrados; puede fallar por saldo insuficiente.

Reactivar una evaluación que ya está activa devuelve `400`. Los bloqueos impiden que acciones simultáneas sobre la misma evaluación dupliquen su efecto financiero. No existe una clave de idempotencia para reintentos de creación de nuevas operaciones.

## 8. Lectura correcta de indicadores

| Indicador | Fuente y significado |
| --- | --- |
| Total de evaluaciones | Conteo de evaluaciones activas |
| Aprobadas del KPI | Nivel BAJO; MEDIO se cuenta aparte como monitoreadas |
| En revisión / bloqueadas | Niveles ALTO / CRITICO |
| Puntaje promedio | Media del score de evaluaciones activas |
| Saldo del dashboard | Suma del saldo disponible de cuentas ACTIVA |
| Ingresos/egresos | Montos de transacciones APROBADA por naturaleza y fecha de transacción |
| Monto total del reporte | Suma de montos del conjunto filtrado de evaluaciones activas |
| `montoBloqueado` del reporte | Suma de ALTO y CRITICO, aunque su nombre sugiera solo bloqueo |
| Tasa de bloqueo del reporte | CRITICO / total × 100, no incluye ALTO en el numerador |

El dashboard usa intervalos `[inicio del primer día, inicio del día siguiente al último)`. `days=n` abarca hoy y n días anteriores: `days=7` produce ocho puntos. Los periodos usan el reloj y zona del servidor.

Para ingresos y egresos se compara con el rango anterior de la misma longitud. La variación se calcula como `(actual - anterior) / abs(anterior) × 100`, redondeada a una décima; si la base es cero se devuelve 0 cuando ambos son cero o 100 en otro caso.

Para saldo, la base estimada es `saldoActual - ingresos + egresos`. No se usa un balance histórico independiente. Como una desactivación cambia el estado de la transacción original, puede cambiar totales de un periodo pasado; no se registra un asiento separado de reverso en la fecha de desactivación.

## 9. Capacidades que no están implementadas

No hay acreditación externa, reserva de fondos retenidos, aprobación manual posterior de una retención, edición de pesos o condiciones de reglas desde la interfaz (sí su activación, ver §4.4), investigación de casos, auditoría completa de acciones, notificaciones externas, MFA ni recuperación de contraseña.

Las tablas previstas para esas áreas no significan que exista su flujo de negocio. Estas distinciones se amplían en [valor de negocio](05_VALOR_NEGOCIO.md).

### Consulta del titular por número de cuenta

El portal pide banco y cuenta antes del titular. Los espacios y guiones del número son solo presentación: `0011-0145-0200847292` y `001101450200847292` identifican la misma cuenta y el mismo contacto, que se guarda con el formato de la cuenta interna. Al escribir o pegar una cuenta Lambda Shield, espera 350 ms y consulta el titular; también consulta al salir del campo y antes de enviar. El nombre interno es de solo lectura. Cambiar cuenta o banco limpia el nombre anterior, y una respuesta de una consulta anterior no puede sobrescribir el destinatario actual. Una cuenta inexistente, inactiva o propia no se admite. Elegir un contacto utiliza el mismo flujo.

Para otros bancos solo se completan los datos de contactos guardados; si el destinatario es nuevo, el nombre se escribe manualmente y la pantalla informa que no verifica la titularidad externa. La aplicación no dispone de una conexión con sus directorios bancarios.

## Asistente IA para definiciones

En `/reglas`, ADMIN completa condiciones tipadas y puede pedir a OpenRouter interpretación, advertencias, sugerencias y casos propuestos. Con el mismo formulario puede:

- **Probar sin guardar**: lleva el borrador al campo de desarrolladores (en la misma pestaña) y lo compara con el catálogo vigente.
- **Guardar como regla (inactiva)**: crea una regla RC001, RC002… con la definición del administrador, nunca con la sugerencia de la IA. No necesita que OpenRouter esté configurado. Queda inactiva hasta que se active desde el catálogo.

Una regla RC es binaria: si se cumplen sus condiciones (todas o alguna, según la combinación) aporta su máximo; si no, 0. No se pueden editar sus condiciones después de guardarla; para cambiarla se crea otra y se desactiva la anterior. [Configuración y límites](12_ASISTENTE_REGLAS_IA.md).

### Activación de reglas

En `/reglas` se muestran tanto reglas activas como inactivas. ADMIN puede activar o desactivar cada regla; ANALISTA solo consulta. El estado se conserva en BD y determina qué reglas aportan puntos y factores en las siguientes evaluaciones. Las evaluaciones anteriores conservan sus resultados. No requiere migración: se utiliza `regla_fraude.es_activa`.

Controles del cambio de estado:

- Antes de activar o desactivar se confirma en un modal que muestra cómo se reparte el porcentaje entre las reglas.
- No se puede desactivar la última regla activa: un motor sin reglas aprobaría todo con puntaje 0.
- Solo se activa una regla implementada y con peso entero 0–100; de lo contrario cada evaluación nueva fallaría.
- Una regla con peso inválido sigue visible en el catálogo como «Peso inválido» para poder corregirla.
- Cada cambio efectivo se registra en `auditoria` (usuario, IP, acción, estado anterior y nuevo). Repetir el mismo estado no genera registro.
- Dos cambios simultáneos se serializan mediante un bloqueo del catálogo.
