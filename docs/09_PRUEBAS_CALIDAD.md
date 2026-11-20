# 09 — Pruebas, cobertura, CI y mantenimiento

[Índice](README.md) · [Instalación](08_INSTALACION_OPERACION.md)

## 1. Qué se verifica

La suite combina pruebas unitarias de reglas, integración Spring/MockMvc, consistencia de persistencia, transacciones concurrentes, políticas de acceso y arquitectura. El frontend usa `node:test` con un DOM de prueba mínimo y sin dependencias npm.

En los reportes locales disponibles al verificar el 17 de noviembre de 2026 hay **122 verificaciones Java sin fallos**, incluidas seis reglas ArchUnit, y se verificaron **17 pruebas JavaScript**. Son conteos del estado revisado; pueden cambiar al añadir casos. El conjunto ampliado se añadió y se verificó con una ejecución limpia de las 122 pruebas. El 15 de noviembre de 2026 se ejecutó además su carga real en PostgreSQL 18.3: se comprobaron los 128 registros, factores, saldos, roles y las diez credenciales. Esta verificación de carga no equivale a ejecutar toda la suite Java sobre PostgreSQL.

## 2. Comandos

PowerShell:

```powershell
.\mvnw.cmd clean test
node --test frontend/tests/*.test.cjs
```

Linux/macOS:

```bash
sh ./mvnw clean test
node --test frontend/tests/*.test.cjs
```

Requisitos: JDK compatible con Java 21 y Node.js 22 para frontend. Maven puede necesitar red para resolver dependencias. El runtime usa PostgreSQL, pero las pruebas Java usan H2 y no requieren el servicio PostgreSQL local.

Seleccionar una suite:

```powershell
.\mvnw.cmd "-Dtest=ProteccionApiMockMvcTest" test
.\mvnw.cmd "-Dtest=ConsistenciaAplicacionTest,SaldoConcurrenteTest" test
```

Si `clean` encuentra archivos bloqueados, detener procesos que usan `target`; ejecutar solo `test` puede evitar el borrado. Después de eliminar clases o cambiar mucho la compilación conviene una ejecución limpia para descartar residuos.

## 3. Configuración y datos

[application.properties de pruebas](../backend/src/test/resources/application.properties) utiliza H2 en modo PostgreSQL, `create-drop`, `open-in-view=false`, SQL no mostrado y carga demo activada.

Los límites generales son altos en el contexto habitual para evitar que suites no relacionadas se interfieran. `LimitesApiMockMvcTest` configura límites pequeños y una base H2 separada para comprobar 429. No se desactivan los filtros de seguridad para conseguir que pasen las pruebas.

`SesionesPrueba` construye identidades del fixture real y un `SecurityContext`, y genera tokens CSRF válidos del repositorio de sesión. Las suites de protección prueban además un login real con token de formulario, cambio de ID de sesión y logout.

Muchas pruebas usan rollback transaccional. `SaldoConcurrenteTest` necesita transacciones independientes, crea su propio cliente/cuenta y elimina únicamente sus datos al terminar. Los IDs autogenerados no deben interpretarse como un contador sin huecos.

## 4. Inventario Java

| Suite | Verificaciones del reporte disponible |
| --- | --- |
| [ConsistenciaAplicacionTest](../backend/src/test/java/com/lambdashield/fraude/application/service/ConsistenciaAplicacionTest.java) | 8 |
| [DeteccionFraudeServiceTest](../backend/src/test/java/com/lambdashield/fraude/application/service/DeteccionFraudeServiceTest.java) | 4 |
| [SaldoConcurrenteTest](../backend/src/test/java/com/lambdashield/fraude/application/service/SaldoConcurrenteTest.java) | 3 |
| [DatasetDemoLoaderTest](../backend/src/test/java/com/lambdashield/fraude/config/seed/DatasetDemoLoaderTest.java) | 2 |
| [ArquitecturaCapasTest](../backend/src/test/java/com/lambdashield/fraude/architecture/ArquitecturaCapasTest.java) | 6 |
| [ReglasFraudeTest](../backend/src/test/java/com/lambdashield/fraude/domain/fraud/rule/ReglasFraudeTest.java) | 8 |
| [FraudeApplicationTests](../backend/src/test/java/com/lambdashield/fraude/FraudeApplicationTests.java) | 1 |
| [DashboardApiMockMvcTest](../backend/src/test/java/com/lambdashield/fraude/presentation/api/rest/DashboardApiMockMvcTest.java) | 3 |
| [LimitesApiMockMvcTest](../backend/src/test/java/com/lambdashield/fraude/presentation/api/rest/LimitesApiMockMvcTest.java) | 3 |
| [PortalClienteApiMockMvcTest](../backend/src/test/java/com/lambdashield/fraude/presentation/api/rest/PortalClienteApiMockMvcTest.java) | 4 |
| [ProteccionApiMockMvcTest](../backend/src/test/java/com/lambdashield/fraude/presentation/api/rest/ProteccionApiMockMvcTest.java) | 15 |
| [ReporteApiMockMvcTest](../backend/src/test/java/com/lambdashield/fraude/presentation/api/rest/ReporteApiMockMvcTest.java) | 7 |
| [SaludMockMvcTest](../backend/src/test/java/com/lambdashield/fraude/presentation/api/rest/SaludMockMvcTest.java) | 2 |
| [SeguridadApiMockMvcTest](../backend/src/test/java/com/lambdashield/fraude/presentation/api/rest/SeguridadApiMockMvcTest.java) | 14 |
| [UsuarioApiMockMvcTest](../backend/src/test/java/com/lambdashield/fraude/presentation/api/rest/UsuarioApiMockMvcTest.java) | 9 |
| [ValidacionApiMockMvcTest](../backend/src/test/java/com/lambdashield/fraude/presentation/api/rest/ValidacionApiMockMvcTest.java) | 16 |
| [AuthorizationPolicyTest](../backend/src/test/java/com/lambdashield/fraude/presentation/security/AuthorizationPolicyTest.java) | 6 |
| [RateLimitWindowTest](../backend/src/test/java/com/lambdashield/fraude/presentation/security/RateLimitWindowTest.java) | 1 |
| [OperacionClienteViewMockMvcTest](../backend/src/test/java/com/lambdashield/fraude/presentation/web/OperacionClienteViewMockMvcTest.java) | 1 |

### Reglas y motor

`ReglasFraudeTest` comprueba condiciones y límites de las ocho implementaciones. `DeteccionFraudeServiceTest` comprueba suma/tope y fronteras de clasificación. No son una validación predictiva contra fraude etiquetado.

### Consistencia de aplicación

`ConsistenciaAplicacionTest` verifica importes decimales, exclusión financiera de estados no aprobados, pesos/activación efectivos, conservación histórica, huellas reutilizables, compatibilidad con huellas antiguas, saldo tras reverso/reactivación y consultas por lote.

Los conteos SQL de dos consultas para historial y tres para usuarios corresponden al fixture y llamada de servicio medidos. No son un presupuesto de SQL de toda una petición con revalidación de seguridad.

### Concurrencia

`SaldoConcurrenteTest` cubre dos débitos simultáneos, fondos insuficientes para ambos y doble desactivación/reactivación. Comprueba efecto financiero y exclusión de dobles movimientos con transacciones separadas.

No prueba idempotencia de creación ni todos los patrones de bloqueo de PostgreSQL. H2 reproduce las pruebas del proyecto, pero el motor real puede tener otros tiempos y planes de bloqueo.

### Seguridad

- `SeguridadApiMockMvcTest`: roles, CRUD permitido, estados e historial aislado.
- `ProteccionApiMockMvcTest`: sesión suplantada, CSRF ausente/incorrecto/de otra sesión, revocación, degradación de rol, vínculo de cliente, firewall, cabeceras, login y logout.
- `LimitesApiMockMvcTest`: límite de lecturas, escrituras y login por IP.
- `AuthorizationPolicyTest`: capacidades explícitas y redirecciones.
- `RateLimitWindowTest`: independencia de claves y renovación de ventana con reloj controlado.

### Contratos y vistas

Las suites de validación, usuarios, portal, dashboard y reportes verifican las respuestas públicas y escenarios concretos de negocio. Las pruebas de salud verifican el estado agregado sin detalles. `OperacionClienteViewMockMvcTest` renderiza la plantilla y comprueba que no incluya operaciones de otros clientes.

`DatasetDemoLoaderTest` verifica además la carga ampliada: 128 operaciones, saldos de las ocho cuentas, los cuatro niveles, las diez credenciales y repetición sin duplicación ni sobrescritura de claves, bloqueos o reversos posteriores.

## 5. Arquitectura

`ArquitecturaCapasTest` verifica:

| Regla del código | Garantía |
| --- | --- |
| `respetaLasCapas` | Dependencias admitidas entre presentación, aplicación, dominio, persistencia y configuración |
| `dominioSinSpring` | Dominio sin dependencia Spring |
| `aplicacionSinDetallesWeb` | Aplicación sin Servlet, Spring Web o UI |
| `controladoresEnPresentacion` | Controladores ubicados en presentación |
| `controladoresSinRepositorios` | Sin repositorios directamente desde controladores |
| `sinCiclosEntreModulos` | Paquetes principales sin dependencias circulares |

Estas reglas no impiden cualquier diseño incorrecto ni prueban todos los detalles del negocio; fijan fronteras explícitas del repositorio.

## 6. Pruebas JavaScript

[api.test.cjs](../frontend/tests/api.test.cjs) verifica:

1. Mensaje de validación JSON conservado.
2. Respuesta 204 sin intento de leer un body JSON.
3. Redirección por 401 y marca de error gestionado.
4. Una sola notificación por 403.
5. Escape de HTML, formato monetario y saldo cero.
6. Saldo de portal tomado del servidor incluso cuando queda en cero.
7. Encabezado CSRF en escrituras y cookies del mismo origen.
8. Rechazo de destino externo sin enviar solicitud/token.
9. Rechazo local de escritura sin token.

El harness no es un navegador real. No acredita diseño visual, carga de CDN, accesibilidad, comportamiento completo de Bootstrap/Chart.js o ejecución de todas las pantallas. Un cambio de maquetación necesita además una revisión visual apropiada.

## 7. JaCoCo y reportes

El plugin prepara el agente antes de pruebas y genera cobertura al finalizar `test`.

- Reporte HTML: `target/site/jacoco/index.html`.
- Resultados Surefire: `target/surefire-reports/`.
- Datos de ejecución: `target/jacoco.exec`.

La configuración no fija un porcentaje mínimo de cobertura que falle el build. Tener cobertura no garantiza que una aserción compruebe el comportamiento correcto.

Mockito se carga como agente explícito en Surefire, junto al argumento de JaCoCo, para evitar depender de auto-attach restringido en Windows. Si cambian versiones, revisar esa configuración de `pom.xml` y ejecutar desde cero.

## 8. Integración continua

[ci.yml](../.github/workflows/ci.yml) se ejecuta en pushes a `main` y pull requests, sobre Ubuntu:

1. Checkout.
2. JDK 21 Temurin y caché Maven.
3. Node.js 22.
4. Pruebas frontend con `node --test`.
5. Pruebas Java/cobertura con Maven.
6. Publicación del reporte JaCoCo como artefacto, si existe.

No hay en ese workflow despliegue, migración PostgreSQL, prueba Docker, prueba de carga, escáner completo de vulnerabilidades ni navegador automatizado.

## 9. Qué debe probar cada cambio

| Cambio | Evidencia esperada |
| --- | --- |
| Umbral/curva de regla | Valores de frontera y ejemplos de nivel final |
| Catálogo/pesos | Activación, pesos inválidos e historial inmutable de factores |
| Saldo/estado de operación | Importes exactos, rechazo, reverso, reactivación y concurrencia |
| API | Validación, rol, sesión/CSRF, estado HTTP y efectos persistidos |
| Seguridad | Escenarios de acceso legítimo y de rechazo sin desactivar filtros |
| Consulta | Resultado correcto y medición cuando se quiere evitar N+1 |
| Vista/JS | Comportamiento de carga/error, token y revisión visual cuando corresponda |
| Migración | Copia PostgreSQL representativa y comprobación de arranque, además de H2 |
| Solo documentación | Enlaces locales, ejemplos, rutas y concordancia con el código; no exige repetir toda la suite |

## 10. Mantener la documentación

- Contrato REST: actualizar DTO, controlador, pruebas y guía 07.
- Permisos/CSRF/sesión: actualizar política, pruebas, guía 06 y rutas de 07.
- Configuración: actualizar `application.properties`, `.env.example` y guía 08 cuando el cambio de código lo requiera.
- Regla o señales: actualizar guía 03, fixtures y pruebas de límites.
- Persistencia: actualizar esquema/migración y guía 04.
- Cambio de negocio: reflejarlo en 02 y separar capacidades reales de propuestas en 05.
- Propuestas de diseño: marcarlas como no implementadas y no mezclarlas con instrucciones de runtime.

Los conteos de pruebas y fechas deben indicar la evidencia usada; no afirmar que se ejecutó PostgreSQL, un proveedor externo o un navegador si solo se inspeccionó código o se usó H2/harness.

## Verificación de contactos y campana — 17/11/2026

La ejecución completa `mvnw clean test` pasó 122 pruebas Java. `node --test frontend/tests/*.test.cjs` pasó 17 pruebas JavaScript. `NotificacionesApiMockMvcTest` cubre bloqueos, retenciones, aislamiento de usuario, lectura idempotente, lectura masiva, roles, CSRF, validaciones, contactos en el portal, resolución del titular interno y rechazo de cuenta propia. `DatasetDemoLoaderTest` verifica siete contactos numerados por persona.

Las pruebas de la campana comprueban contador, texto sin interpretar HTML, lectura única, conservación del foco, lectura masiva, paginación y errores de carga. Se verificó además en navegador el acceso de demo.lucia, los dos avisos propios y el autocompletado de nombre y cuenta desde contactos; el desplegable cabe en una vista estrecha. No se enviaron nuevas operaciones desde esta comprobación visual.

## Verificación del formulario por cuenta

Se añadieron pruebas del titular automático, nombre interno de solo lectura, limpieza al cambiar la cuenta, rechazo de envío cuando la cuenta no existe y entrada manual explícita para otros bancos. La prueba API verifica autenticación, rol, formato, titular correcto, ausencia de saldo en la respuesta y rechazo de cuenta propia/inexistente.

## Integración del asistente — 18/11/2026

La suite incorpora el adaptador OpenRouter con transporte simulado, validación del formulario, errores de acceso/cuota/formato, timeout, autorización y CSRF, representación del borrador y límite específico de revisiones. Las pruebas no usan la clave real ni consumen tokens. La conectividad real se comprueba por separado, una vez configurado `.env`. [Detalles](12_ASISTENTE_REGLAS_IA.md).

## Revisión de congruencia — 18/11/2026

La ejecución completa final pasó **137 pruebas Java** sin fallos, errores ni omisiones; las **23 pruebas JavaScript** también pasaron. Se verificaron la prohibición de abonos desde el portal, rechazo de beneficiarios inactivos por nombre, objetivo opcional y límite de ratio del formulario IA, JSON duplicado/concatenado y concurrencia de lectura/refresco en campana. Se regeneró el archivo de cobertura para evitar mezclar bytecode de compilaciones anteriores. No se llamó a Alibaba durante esta revisión. [Hallazgos y pendientes](13_REVISION_CONGRUENCIA_CODIGO.md).
