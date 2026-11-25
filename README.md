# Lambda Shield — Sistema de detección de fraude bancario

Aplicación académica que evalúa operaciones antes de modificar el saldo de una cuenta. Combina un portal de cliente, un panel de análisis y un motor explicable con ocho reglas en Java y reglas definidas por el administrador desde un formulario tipado. La evaluación, sus factores y las operaciones se persisten mediante JPA.

**Estado documentado: 24 de noviembre de 2026.** Se conserva Spring MVC con **Thymeleaf**, CSS y JavaScript vanilla. Las páginas y las APIs conviven en el mismo artefacto Maven; no se está migrando a un frontend separado.

## 1. Guía de lectura

| Necesidad | Documento |
| --- | --- |
| Instalar, configurar, migrar y diagnosticar | [Instalación y operación](docs/08_INSTALACION_OPERACION.md) |
| Entender capas, componentes y Thymeleaf | [Arquitectura](docs/01_ARQUITECTURA.md) |
| Conocer pantallas y flujos de negocio | [Funcionalidades](docs/02_FUNCIONALIDADES.md) |
| Revisar pesos, umbrales y factores | [Reglas antifraude](docs/03_REGLAS_ANTIFRAUDE.md) |
| Entender persistencia, consultas y bloqueos | [Datos y consultas JPA](docs/04_CONSULTAS_JPA.md) |
| Distinguir valor esperado de capacidades reales | [Valor de negocio y límites](docs/05_VALOR_NEGOCIO.md) |
| Comprender autenticación, CSRF y permisos | [Seguridad](docs/06_SEGURIDAD.md) |
| Integrar un cliente HTTP | [Referencia de API](docs/07_API_REST.md) |
| Ejecutar y mantener las verificaciones | [Pruebas y calidad](docs/09_PRUEBAS_CALIDAD.md) |
| Usar cuentas con historial ya preparado | [Cuentas y accesos demo](docs/11_CUENTAS_Y_ACCESOS_DEMO.md) |
| Estudiar evolución pendiente del motor | [Reglas dinámicas y LLM](docs/10_PROPUESTA_REGLAS_DINAMICAS_LLM.md) |
| Configurar el asistente de revisión con OpenRouter | [Asistente de reglas IA](docs/12_ASISTENTE_REGLAS_IA.md) |
| Revisar congruencia, limpieza y deuda técnica | [Revisión de código](docs/13_REVISION_CONGRUENCIA_CODIGO.md) |

El [índice de documentación](docs/README.md) propone recorridos según el perfil del lector.

## 2. Qué implementa

- CLIENTE: consulta de cuenta y saldo, envío de operaciones, historial propio y avisos de riesgo.
- ANALISTA: dashboard, historial de evaluaciones, catálogo de reglas y reportes.
- ADMIN: las capacidades de administración, simulador persistente, gestión de usuarios y desactivación/reactivación de evaluaciones.
- Decisiones por puntaje: aprobar, monitorear, retener o bloquear.
- Saldo en `BigDecimal`, operaciones transaccionales y bloqueo de cuenta para evitar sobrescrituras concurrentes.
- Exportación filtrada a CSV, XLSX y PDF.
- Spring Security con permisos explícitos, CSRF, revalidación de sesiones y límites de solicitudes.

Una operación aprobada modifica la cuenta local del cliente. El proyecto no integra una red bancaria ni acredita una cuenta externa del destinatario. El simulador también escribe operaciones y puede modificar saldos; se debe usar en una base de desarrollo.

## 3. Tecnologías y organización

| Área | Implementación |
| --- | --- |
| Compilación | Java 21, Maven Wrapper |
| Servidor | Spring Boot 4.1.1, Spring MVC |
| Persistencia | Spring Data JPA, Hibernate, PostgreSQL |
| Interfaz | Thymeleaf, Bootstrap, Bootstrap Icons, Chart.js, JavaScript vanilla |
| Seguridad | Spring Security y BCrypt |
| Exportaciones | Apache POI y OpenPDF |
| Verificación | JUnit, MockMvc, AssertJ, ArchUnit, JaCoCo, H2 y `node:test` |

Las versiones de dependencias están en [pom.xml](pom.xml); las de recursos CDN, en las plantillas.

```text
backend/src/main/java/com/lambdashield/fraude/
├── presentation/   # REST, MVC, DTO, mappers, exportadores y filtros
├── application/    # Casos de uso, modelos y servicios
├── domain/         # Entidades, enums y reglas puras
├── repository/     # Contratos Spring Data JPA
└── config/         # Composición, seguridad y sembradores opcionales
backend/src/main/resources/  # Configuración y SQL
backend/src/test/            # Pruebas Java y configuración H2
frontend/src/main/resources/
├── templates/              # Pantallas Thymeleaf y fragmentos compartidos
└── static/                 # CSS, JavaScript e imágenes
frontend/tests/             # Pruebas JavaScript sin dependencias npm
docs/                       # Guías detalladas
```

## 4. Inicio local

Requisitos: JDK 21 o un JDK compatible con `release=21`, PostgreSQL o Docker, y navegador moderno. Node.js 22 se usa para las pruebas del frontend; no es necesario para servir la aplicación.

Desde la raíz del repositorio, en PowerShell:

```powershell
Copy-Item .env.example .env
```

Edita `.env` con las credenciales de tu base. Para una instalación local de demostración, configura explícitamente:

```properties
SPRING_PROFILES_ACTIVE=dev
DEMO_DATA_ENABLED=true
SESSION_COOKIE_SECURE=false
```

La carga demo está desactivada por defecto. Para ocho cuentas adicionales y 5 252 operaciones persistentes (un año de historial), ver [cuentas y accesos](docs/11_CUENTAS_Y_ACCESOS_DEMO.md). Sin ella, los clientes, usuarios, roles y reglas deben estar provisionados en la base. No existe registro público ni un endpoint de bootstrap del primer administrador.

Si usas el PostgreSQL de Compose:

```powershell
docker compose up -d
docker compose ps
```

Compose levanta solo la base y aplica el esquema al inicializar un volumen vacío. Para PostgreSQL instalado directamente o una base existente, sigue los comandos y las precauciones de [instalación](docs/08_INSTALACION_OPERACION.md).

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

En Linux/macOS:

```bash
sh ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Abre [la aplicación local](http://localhost:8080). Las claves demo están en el sembrador del repositorio y se destinan exclusivamente a desarrollo. Desactivar la carga demo no elimina ni cambia cuentas ya creadas.

## 5. Configuración y base de datos

[application.properties](backend/src/main/resources/application.properties) importa el archivo `.env` de la raíz. Las variables reales del proceso tienen precedencia sobre los valores del archivo. No se deben versionar credenciales reales.

| Variable | Valor por defecto | Uso |
| --- | --- | --- |
| `DB_HOST` | `localhost` | Servidor PostgreSQL |
| `DB_PORT` | `5432` | Puerto de conexión y puerto publicado por Compose |
| `DB_NAME` | `lambda_shield` | Base de datos |
| `SPRING_PROFILES_ACTIVE` | Sin perfil | `dev` para desarrollo local; `prod` para HTTPS |
| `DB_USER` / `DB_PASSWORD` | Obligatorios; `postgres` / `postgres` solo en `dev` | Credenciales de la base |
| `DEMO_DATA_ENABLED` | `false` | Crear los fixtures demo originales al arrancar |
| `DEMO_DATASET_ENABLED` | `false` | Cargar ocho cuentas adicionales y 5 252 operaciones históricas, sin duplicarlas |
| `SESSION_COOKIE_SECURE` | `true`; `false` en `dev` | En `prod` la cookie siempre es Secure |
| `LOGIN_RATE_LIMIT` | `10` | Intentos de login por IP y minuto |
| `API_RATE_LIMIT` | `120` | Peticiones API por identidad y minuto |
| `API_WRITE_RATE_LIMIT` | `40` | Escrituras API por identidad y minuto |

El servidor usa `ddl-auto=validate`: valida el esquema, no lo crea ni migra automáticamente.

- Base nueva: [esquema canónico](backend/src/main/resources/db/postgresql/BD-Desarrollo%20web.sql).
- Base existente: [migración idempotente](backend/src/main/resources/db/postgresql/migracion_soft_delete_postgresql.sql) y después [contactos y notificaciones](backend/src/main/resources/db/postgresql/migracion_contactos_notificaciones.sql).

La migración incluye las copias históricas `maximo_aplicado`, `etiqueta_aplicada` y `version_aplicada` de cada factor. El backfill toma el catálogo disponible al migrar; no puede reconstruir configuraciones anteriores que nunca se guardaron.

## 6. Pantallas y acceso

| Ruta | Pantalla | Acceso |
| --- | --- | --- |
| `GET /login` | Formulario de acceso | Público |
| `/` | Dashboard | ADMIN y ANALISTA |
| `/transacciones` | Historial y detalle | ADMIN y ANALISTA |
| `/reglas` | Catálogo consultable | ADMIN y ANALISTA |
| `/reportes` | Filtros, resumen y exportaciones | ADMIN y ANALISTA |
| `/simulador` | Evaluación manual y tráfico demo | ADMIN |
| `/usuarios` | Gestión de usuarios | ADMIN |
| `/operacion` | Portal de cuenta y operaciones | CLIENTE; ADMIN también está autorizado, pero sin cliente asociado puede ver una vista vacía |

`POST /login` y `POST /logout` los procesa Spring Security. El cierre de sesión efectivo requiere POST y CSRF. Mostrar u ocultar botones no sustituye los controles del servidor.

## 7. APIs y seguridad

Las APIs viven bajo `/api/v1`. Se autentican con sesión HTTP, no con JWT, API keys ni HTTP Basic. Una ruta nueva queda denegada hasta declararla en la política.

| Familia | Función | Roles |
| --- | --- | --- |
| `/evaluation` | Crear, listar, desactivar y reactivar evaluaciones | ADMIN; CLIENTE solo crea para su cliente; ANALISTA solo lee |
| `/metric` | KPIs, resumen financiero y movimientos | ADMIN y ANALISTA |
| `/rule` | Consultar reglas activas; activar/desactivar y asistente IA solo ADMIN | ADMIN y ANALISTA |
| `/notification` | Bandeja de avisos propia | CLIENTE |
| `/account/destination` | Titular de una cuenta interna de destino | CLIENTE |
| `/report/export` | Exportar evaluaciones filtradas | ADMIN y ANALISTA |
| `/user` | Gestión de usuarios | ADMIN |

Las escrituras exigen CSRF incluso durante login y logout. Los tokens se integran mediante Thymeleaf y `auth.js`. Las sesiones se revalidan contra la base: bloquear/eliminar usuarios o cambiar su clave revoca el acceso en la siguiente petición admitida por los límites.

Respuestas habituales: una API sin identidad válida, sin permiso o con CSRF inválido responde con una redirección 302 a `/login`, sin cuerpo ni mensaje; si había una sesión, se cierra. `400` por validación, `404` por una entidad ausente, `409` por datos relacionados y `429` por exceso de solicitudes. Consulta la [referencia completa](docs/07_API_REST.md).

Para producción, ejecuta el JAR con `--spring.profiles.active=prod` y configura `DB_USER` y `DB_PASSWORD`. Este perfil redirige HTTP a HTTPS, exige cookies Secure y deshabilita ambas cargas demo y el SQL de depuración. Debes proporcionar TLS en el servidor o en un proxy confiable; el perfil no instala certificados. No reutilices el archivo `.env` local. Las sesiones y los límites viven en memoria por instancia; no constituyen una configuración de alta disponibilidad distribuida.

## 8. Reglas y consistencia financiera

| Puntaje | Nivel | Decisión | Estado de transacción | Saldo |
| --- | --- | --- | --- | --- |
| 0–29 | BAJO | APROBAR | APROBADA | Se aplica el movimiento |
| 30–59 | MEDIO | MONITOREAR | APROBADA | Se aplica el movimiento |
| 60–79 | ALTO | RETENER | RETENIDA | Sin movimiento |
| 80–100 | CRITICO | BLOQUEAR | BLOQUEADA | Sin movimiento |

El catálogo controla activación y peso. El peso es relativo: las reglas activas se reparten el 100 % del puntaje (máximo de cada una = peso ÷ suma de pesos activos × 100), y activar, desactivar o agregar una regla reajusta al resto. Las implementaciones Java y las reglas definidas (RC) calculan la intensidad de la señal. Se guardan puntajes, motivos y copias de etiqueta, máximo y versión. Desactivar revierte el movimiento cuando lo hubo; reactivar restaura el estado derivado del nivel guardado, sin ejecutar otra vez el motor.

El dashboard financiero incluye únicamente transacciones `APROBADA`. Los KPIs de riesgo cuentan evaluaciones activas, incluidas retenidas y bloqueadas: son métricas distintas. `days=7` significa hoy más los siete días anteriores, es decir, ocho fechas.

## 9. Pruebas y CI

```powershell
.\mvnw.cmd clean test
node --test frontend/tests/*.test.cjs
```

La última ejecución disponible registra **166 verificaciones Java** y **40 pruebas JavaScript**, sin fallos. Java se ejecutó localmente con JDK 21. Maven usa el JDK de `JAVA_HOME` o del `PATH`: con JDK 17 la suite falla con «class file version 65.0» antes de ejecutar pruebas; CI configura JDK 21 y Node.js 22. Los conteos son una referencia del estado documentado, no un mínimo de cobertura obligatorio.

Las pruebas Java usan H2 en modo PostgreSQL y no necesitan una instancia PostgreSQL. Incluyen seguridad, CSRF, concurrencia, importes exactos, consultas por lote y arquitectura. El reporte de cobertura se genera en `target/site/jacoco/index.html`.

[CI](.github/workflows/ci.yml) ejecuta ambos grupos en pull requests y pushes a `main`. No despliega la aplicación ni prueba una base PostgreSQL real. Los alcances y límites de validación se describen en [pruebas y calidad](docs/09_PRUEBAS_CALIDAD.md).

## 10. Alcance actual

Se conservan las 18 entidades del esquema académico y se incorpora `NotificacionUsuario` (19 entidades). Alertas, casos, historial de casos y características no tienen un flujo completo de aplicación; `auditoria` solo registra la activación y desactivación de reglas. Los avisos del portal se persisten por usuario para bloqueos y retenciones, con estado de lectura independiente. No sustituyen la entidad de alerta formal ni un caso de investigación.

El país es declarado, la huella de dispositivo es una normalización del User-Agent y RF007 no mide distancias reales. No hay MFA, notificaciones externas, reconciliación bancaria ni evidencia de cumplimiento regulatorio. Las limitaciones y futuras mejoras están separadas de lo implementado en [valor de negocio](docs/05_VALOR_NEGOCIO.md).
