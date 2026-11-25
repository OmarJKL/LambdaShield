# 01 — Arquitectura y decisiones técnicas

[Índice](README.md) · [Funcionalidades](02_FUNCIONALIDADES.md)

## 1. Forma de la aplicación

Lambda Shield es una aplicación Spring Boot con un solo `pom.xml` y un único artefacto desplegable. `backend/` y `frontend/` separan archivos por responsabilidad, no son dos servicios ni módulos Maven independientes.

Maven compila `backend/src/main/java`, toma las pruebas de `backend/src/test/java` e incorpora los recursos de `backend/src/main/resources` y `frontend/src/main/resources` al classpath. Thymeleaf renderiza las plantillas y Spring sirve los recursos estáticos.

```text
Navegador
  ├─ GET de página → filtros de seguridad → controlador MVC → Thymeleaf → HTML
  ├─ solicitud API → filtros de seguridad → controlador REST → DTO / archivo
  └─ CSS / JavaScript / imágenes → recursos estáticos permitidos
                                  ↓
                           servicios de aplicación
                                  ↓
                          dominio + repositorios JPA
                                  ↓
                              PostgreSQL
```

No se requiere Node.js en el servidor de la aplicación. Se usa únicamente para verificar JavaScript en desarrollo y CI.

## 2. Capas y paquetes

Todos los paquetes Java de producción cuelgan de `com.lambdashield.fraude`.

| Paquete | Responsabilidad | Ejemplos |
| --- | --- | --- |
| `presentation.api.rest` | Adaptar HTTP/JSON y despachar casos de uso | `EvaluationApiController`, `UserApiController` |
| `presentation.web` | Preparar modelos y seleccionar vistas | `DashboardController`, `OperacionClienteController` |
| `presentation.dto` | Contratos y validación HTTP | `TransaccionCrearRequest`, `EvaluacionResponse` |
| `presentation.mapper` | Formatos y etiquetas de interfaz | `EvaluacionResponseMapper`, `DashboardResponseMapper` |
| `presentation.export` | Serializar reportes | Exportadores CSV, XLSX y PDF |
| `presentation.security` | Políticas y filtros HTTP | `AuthorizationPolicy`, `SessionValidationFilter`, `RequestRateLimitFilter` |
| `presentation.exception` | Traducir excepciones a respuestas | `GlobalExceptionHandler` |
| `application.command` | Entradas de casos de uso | `EvaluarTransaccionCommand` |
| `application.model` | Resultados sin detalles HTTP | `EvaluacionResultado`, `ResumenFinanciero` |
| `application.mapper` | Traducir entidades a resultados | `EvaluacionResultadoMapper` |
| `application.service` | Coordinar negocio y persistencia | Evaluaciones, saldos, consultas, usuarios y catálogos |
| `domain.entity` | Mapeo del esquema de negocio | `Cuenta`, `Transaccion`, `EvaluacionRegla` |
| `domain.enums` | Estados y decisiones | `NivelRiesgo`, `EstadoTransaccion` |
| `domain.fraud` | Factores y reglas de riesgo | `FactorRegla`, `TransaccionEvaluable`, reglas RF |
| `domain.device` | Normalización de User-Agent | `DeviceFingerprint` |
| `domain.customer` | Formato del nombre de cliente | `NombreClienteFormatter` |
| `repository` | Consultas y operaciones JPA | `CuentaRepository`, `EvaluacionFraudeRepository` |
| `config` | Composición, seguridad y carga opcional | `SecurityConfig`, `FraudRuleConfiguration`, sembradores |

Las entidades usan JPA y Lombok: el dominio no es independiente de toda biblioteca. Las reglas antifraude sí son funciones de dominio sin anotaciones Spring. Se evita `@Data` en entidades para no generar igualdad o `toString` sobre relaciones bidireccionales.

## 3. Dependencias verificadas

`ArquitecturaCapasTest` contiene seis reglas:

| Regla | Restricción |
| --- | --- |
| `respetaLasCapas` | Presentación solo la accede configuración; aplicación la acceden presentación/configuración; persistencia, aplicación/configuración; dominio, aplicación/persistencia/configuración |
| `dominioSinSpring` | Sin dependencias Spring desde dominio |
| `aplicacionSinDetallesWeb` | Sin Servlet, Spring Web o UI desde aplicación |
| `controladoresEnPresentacion` | Todo controlador debe estar en presentación |
| `controladoresSinRepositorios` | Los controladores no dependen directamente de repositorios |
| `sinCiclosEntreModulos` | Sin ciclos entre paquetes de primer nivel |

Los servicios usan Spring Data directamente. No hay una capa adicional de puertos/adaptadores de persistencia. Las entidades JPA se convierten a modelos de aplicación antes de cruzar a presentación; no se exponen como respuestas REST.

## 4. Componentes de aplicación

| Componente | Función |
| --- | --- |
| `EvaluacionTransaccionService` | Crear/evaluar, persistir y cambiar el estado activo de operaciones |
| `DeteccionFraudeService` | Ejecutar implementaciones de reglas, sumar y clasificar |
| `CatalogoReglasService` | Ejecutar reglas RF y RC participantes y repartir el 100 % del puntaje según sus pesos |
| `ReglaDefinidaEvaluador` | Ejecutar, serializar y describir las definiciones tipadas de reglas RC |
| `ReglaPersonalizadaService` / `EstadoReglaService` | Crear reglas RC inactivas y activarlas/desactivarlas con auditoría |
| `SimulacionReglasService` | Probar escenarios sin persistir, comparando catálogo vigente y candidatas |
| `MovimientoSaldoService` | Aplicar débito/crédito y su reverso sobre una cuenta bloqueada |
| `TransaccionReferenciasResolver` | Resolver tipo, canal, dispositivo y beneficiario |
| `BeneficiarioConsultaService` | Agenda numerada del cliente autenticado |
| `NotificacionService` | Avisos persistentes, bandeja y estado de lectura por usuario |
| `DispositivoService` | Buscar/reutilizar huellas y atender formatos antiguos |
| `ClienteContextService` | Consultar señales de dispositivo e IP |
| `ClienteService` | Datos del cliente y elección de cuenta principal |
| `EvaluacionConsultaService` | Filtrar en BD y cargar factores por lote |
| `EvaluacionMetricasService` | Obtener agregados de riesgo |
| `DashboardResumenService` | Saldo, ingresos/egresos y variaciones |
| `DashboardMovimientosService` | Serie diaria y actividad reciente |
| `PeriodoConsulta` | Rangos actual/anterior del dashboard |
| `ReporteResumenService` | Resumir el conjunto filtrado del reporte |
| `UsuarioAdminService` | CRUD, claves, roles y reglas administrativas |
| `AuthService` / `RolUsuarioService` | Validar acceso y resolver la identidad/jerarquía vigente |

`NivelRiesgo` concentra la correspondencia entre nivel, decisión y estado de transacción. El texto, icono y color que se entregan a la interfaz son responsabilidad de `EvaluacionResponseMapper`.

## 5. Thymeleaf y JavaScript: reparto actual

Se mantiene esta combinación; no hay una migración a HTML estático en curso.

| Pantalla | Datos iniciales del servidor | JavaScript |
| --- | --- | --- |
| Dashboard | Resumen financiero y movimientos | Gráfico, cambio de periodo y actualización de cifras |
| Transacciones | Rol, navegación y estructura | Lista, KPIs, filtros locales, detalle y acciones |
| Usuarios | Usuario actual, rol y estructura | CRUD completo por API |
| Reglas | Catálogo completo (activas e inactivas) | Búsqueda, paginación, activación/desactivación y asistente IA (ADMIN) |
| Simulador | Clientes y promedios del selector | Envío, radar de factores y tráfico automático |
| Reportes | Resultado filtrado y resumen | Construcción de enlaces de exportación |
| Operación | Cliente, cuenta, saldo, historial y avisos propios | Envío y actualización del recibo, saldo e historial |
| Login | Formulario y error de acceso | El envío es un formulario procesado por Spring Security |

`fragments/head.html` comparte dependencias y etiquetas CSRF; `fragments/navbar.html` comparte navegación y formulario POST de logout. `auth.js` concentra transporte, notificaciones, CSRF y utilidades `LSUi`.

## 6. Seguridad antes de MVC

`SecurityConfig` configura la cadena de Spring Security. Los controles personalizados tienen este orden relativo:

```text
contexto de seguridad de sesión
  → RequestRateLimitFilter
  → SessionValidationFilter
  → validación CSRF
  → autenticación de formulario / logout, según la solicitud
  → autorización
  → controlador
```

Este esquema resume responsabilidades; Spring añade otros filtros de cabeceras, firewall y tratamiento de excepciones. Ya no se usa `AuthInterceptor` ni `WebConfig` para proteger rutas.

La sesión contiene una identidad autenticada; las etiquetas de usuario/rol/cliente se regeneran con datos de la base. Las reglas completas y los límites se documentan en [seguridad](06_SEGURIDAD.md).

## 7. Persistencia y transacciones

- Runtime: PostgreSQL y `ddl-auto=validate`.
- Pruebas: H2 en modo PostgreSQL y `create-drop`.
- `open-in-view=false`: la carga necesaria se realiza durante los servicios.
- Importes: `BigDecimal` en persistencia, comandos y respuestas. El scoring relativo y los porcentajes utilizan también `double`.
- Crear una evaluación ocurre dentro de una transacción; un error revierte las escrituras de esa operación.
- La cuenta principal se bloquea para escribir. Desactivar/reactivar bloquea primero la evaluación y después la cuenta.
- No hay un libro mayor de movimientos separado ni una integración con compensación bancaria.

Los detalles de consultas y límites de concurrencia están en [datos y JPA](04_CONSULTAS_JPA.md).

## 8. Inicialización y configuración

`DataInitializer` solo se activa con `app.demo.enabled=true`, enlazado a `DEMO_DATA_ENABLED`. Coordina roles, clientes/cuentas/dispositivos, catálogos, reglas, usuarios y evaluaciones de ejemplo.

Los sembradores intentan evitar duplicados mediante búsquedas previas. No son un gestor de migraciones ni un mecanismo seguro de aprovisionamiento productivo. Mantener la carga demo activa puede volver a crear elementos ausentes o reponer asignaciones demo al reiniciar.

El motor recibe las implementaciones `ReglaEvaluable` desde `FraudRuleConfiguration`. El catálogo de BD controla activación y pesos; las dos piezas deben mantener identidades compatibles.

## 9. Cómo extender sin romper contratos

### Añadir una regla

1. Implementar `ReglaEvaluable` y probar sus límites.
2. Registrar su identidad estable en `FactorRegla` y su bean en `FraudRuleConfiguration`.
3. Provisionar el catálogo y su peso/versión; actualizar fixtures cuando corresponda.
4. Revisar identificación del modelo, documentación y visualizaciones que dependan de las reglas.
5. Ejecutar pruebas de dominio, consistencia y arquitectura.

Una implementación nueva sin catálogo no queda aceptada silenciosamente. No basta con añadir una clase.

### Añadir un endpoint

1. Definir DTO, validaciones y caso de uso.
2. Crear el controlador en presentación.
3. Autorizar explícitamente ruta y método en `AuthorizationPolicy` o en la configuración si es una excepción justificada.
4. Conservar CSRF en escrituras y probar acceso por cada rol.
5. Documentar la respuesta, los errores y sus efectos en persistencia.

### Añadir una pantalla

Reutilizar Thymeleaf, los fragmentos y `auth.js`. Los datos privados se obtienen desde un caso de uso o API autorizada; ocultar controles en la vista no concede ni retira permisos.
