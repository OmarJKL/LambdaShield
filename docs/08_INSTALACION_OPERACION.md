# 08 — Instalación, configuración, migración y operación

[Índice](README.md) · [Seguridad](06_SEGURIDAD.md) · [Pruebas](09_PRUEBAS_CALIDAD.md)

## 1. Requisitos y alcance

| Componente | Uso |
| --- | --- |
| JDK 21 | Versión objetivo de compilación y versión configurada en CI |
| Maven Wrapper | Incluido: `mvnw.cmd` y `mvnw`; puede descargar Maven/dependencias |
| PostgreSQL | Base de runtime; Compose declara la imagen `postgres:18` |
| Docker Compose | Opcional, para la base local; no ejecuta la aplicación |
| Node.js 22 | Solo para pruebas JavaScript, sin instalación npm |
| Python 3 | Opcional, para el ejemplo HTTP de la guía API |

Los comandos se ejecutan desde la raíz del repositorio. La última validación local Java usó JDK 24 con `release=21`; eso no garantiza compatibilidad con todos los JDK posteriores. H2 se usa en pruebas y no reemplaza el runtime PostgreSQL.

## 2. Preparar el entorno

PowerShell:

```powershell
java -version
Copy-Item .env.example .env
```

Si `java` apunta a una versión anterior a 21, selecciona tu instalación de JDK para el proceso actual. Ejemplo, ajustando la ruta a una instalación real:

```powershell
$env:JAVA_HOME = 'C:\ruta\al\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
```

No se necesita modificar archivos del proyecto para elegir el JDK.

Linux/macOS:

```bash
cp .env.example .env
java -version
sh ./mvnw --version
```

El Wrapper puede requerir conexión la primera vez. El modo Maven offline solo funciona si todos los artefactos ya están en caché; no debe utilizarse como requisito de instalación.

## 3. Variables y configuración

Spring importa `optional:file:./.env[.properties]` desde el directorio de ejecución. Por eso se recomienda arrancar desde la raíz. Este archivo se interpreta como propiedades, no como un script de shell.

| Variable | Predeterminado | Observación |
| --- | --- | --- |
| `DB_HOST` | `localhost` | Host accesible desde el proceso Java |
| `DB_PORT` | `5432` | Puerto del host; Compose lo publica al 5432 del contenedor |
| `DB_NAME` | `lambda_shield` | La base debe existir |
| `SPRING_PROFILES_ACTIVE` | Sin perfil | Usar `dev` localmente y `prod` en producción |
| `DB_USER` | Obligatorio; `postgres` en `dev` | Usar un usuario apropiado para cada entorno |
| `DB_PASSWORD` | Obligatorio; `postgres` en `dev` | Credencial propia fuera de desarrollo |
| `DEMO_DATA_ENABLED` | `false` | Activa `DataInitializer` al ser true |
| `DEMO_DATASET_ENABLED` | `false` | Activa la carga ampliada de cuentas e historial |
| `SESSION_COOKIE_SECURE` | `true`; `false` en `dev` | En `prod` se fija siempre en true |
| `LOGIN_RATE_LIMIT` | `10` | Intentos de login/minuto/IP |
| `API_RATE_LIMIT` | `120` | API/minuto/identidad o IP |
| `API_WRITE_RATE_LIMIT` | `40` | Escrituras/minuto/identidad o IP |

Variables reales del proceso prevalecen sobre `.env`. Cambiar `.env` normalmente requiere reiniciar la aplicación; no hay recarga dinámica de esa configuración.

Otras propiedades relevantes:

| Propiedad | Configuración en runtime |
| --- | --- |
| `server.port` | 8080 |
| `spring.jpa.hibernate.ddl-auto` | validate |
| `spring.jpa.open-in-view` | false |
| `spring.jpa.show-sql` | true en la configuración suministrada |
| `spring.thymeleaf.cache` | false en desarrollo |
| `server.servlet.session.timeout` | 20m |
| `server.servlet.session.tracking-modes` | cookie |
| `server.servlet.session.cookie.http-only` | true |
| `server.servlet.session.cookie.same-site` | lax |

Se pueden sobrescribir propiedades con los mecanismos de configuración de Spring Boot. Para un despliegue, revisar caché de plantillas, logs SQL y configuración del servidor sin editar secretos en el repositorio.

## 4. Crear una base PostgreSQL local

### Opción A: Docker Compose

```powershell
docker compose up -d
docker compose ps
docker compose logs db
```

El servicio se llama `db` y el contenedor declarado es `lambda-shield-db`. Usa un volumen persistente `lambda_shield_pgdata`, un healthcheck `pg_isready` y monta el esquema como `01-esquema.sql` en el directorio de inicialización de la imagen.

El esquema se aplica **solo cuando se inicializa un volumen vacío**. Reiniciar o recrear un contenedor con el mismo volumen no vuelve a ejecutar el script ni migra sus tablas.

Si el host ya utiliza 5432, puede configurarse `DB_PORT=5433`. Esa misma variable permite que Java use el puerto publicado. `DB_HOST=localhost` corresponde al proceso Java ejecutado en el host; ejecutar Java dentro de otro contenedor requeriría otra configuración de red, que no está incluida.

No elimines volúmenes para aplicar una migración. `docker compose down` detiene servicios conservando el volumen; añadir `-v` elimina datos persistentes y no es un paso de actualización normal.

Los comandos anteriores describen el Compose del repositorio. La validación de código documentada usa H2; no debe confundirse con una prueba ejecutada de la imagen, sus volúmenes o una actualización de versión de PostgreSQL.

### Opción B: PostgreSQL instalado

Con herramientas PostgreSQL en PATH y sustituyendo usuario/host/puerto si corresponde:

```powershell
createdb -h localhost -p 5432 -U postgres -W lambda_shield
psql -h localhost -p 5432 -U postgres -W -d lambda_shield -v ON_ERROR_STOP=1 -f "backend/src/main/resources/db/postgresql/BD-Desarrollo web.sql"
```

Las herramientas `psql` y `createdb` no leen el `.env` de Spring automáticamente. Introduce la clave por el mecanismo seguro de tu entorno; los ejemplos usan solicitud interactiva y no incluyen una clave en el comando.

El script canónico es para una base nueva. No se debe volver a aplicar sobre tablas existentes como si fuera una migración.

## 5. Actualizar una base existente

1. Identificar la base y la versión del esquema realmente usada.
2. Hacer una copia y comprobar que puede restaurarse en un entorno separado.
3. Probar la migración sobre esa copia con la versión PostgreSQL prevista.
4. Detener escrituras o realizar el mantenimiento en una ventana controlada.
5. Aplicar la migración con detención ante errores.
6. Arrancar la aplicación y comprobar validación de esquema, salud, acceso y una consulta autorizada.

Ejemplo de copia con los nombres de desarrollo predeterminados:

```powershell
pg_dump -h localhost -p 5432 -U postgres -W -Fc -f lambda_shield.backup lambda_shield
```

Migración desde el host:

```powershell
psql -h localhost -p 5432 -U postgres -W -d lambda_shield -v ON_ERROR_STOP=1 -f "backend/src/main/resources/db/postgresql/migracion_soft_delete_postgresql.sql"
psql -h localhost -p 5432 -U postgres -W -d lambda_shield -v ON_ERROR_STOP=1 -f "backend/src/main/resources/db/postgresql/migracion_contactos_notificaciones.sql"
```

Alternativa dentro de Compose, para los nombres de usuario/base predeterminados:

```powershell
docker compose cp "backend/src/main/resources/db/postgresql/migracion_soft_delete_postgresql.sql" db:/tmp/lambda-migracion.sql
docker compose exec db psql -U postgres -d lambda_shield -v ON_ERROR_STOP=1 -f /tmp/lambda-migracion.sql
```

Si se cambiaron `DB_USER` o `DB_NAME`, sustituirlos también en esos comandos. No existe un volumen que aplique automáticamente este archivo de migración al reiniciar.

La migración usa BEGIN/COMMIT y añade las columnas y ajustes documentados en [persistencia](04_CONSULTAS_JPA.md). Es idempotente para su conjunto de cambios, pero no un sistema de versiones completo.

Los factores antiguos reciben un backfill de etiqueta/máximo/versión desde el catálogo disponible. Guardar una copia previa es importante porque esa operación no recupera estados históricos desconocidos.

## 6. Datos y usuarios iniciales

Para una demostración **local**, configurar `DEMO_DATA_ENABLED=true` antes de arrancar. El coordinador ejecuta, en orden:

1. Roles.
2. Clientes, cuentas y dispositivos demo.
3. Canal WEB y tipos de transacción.
4. Reglas RF001–RF008.
5. Usuarios demo y sus asignaciones.
6. Diez evaluaciones iniciales si el repositorio de evaluaciones está vacío.

Los sembradores utilizan búsquedas para evitar duplicados habituales. No garantizan un resultado idéntico sobre una base parcialmente modificada ni migran datos antiguos. Algunos reponen elementos ausentes o roles de usuarios demo; las evaluaciones iniciales solo se generan cuando no hay evaluaciones.

Los usuarios demo incluyen `admin`, `analista`, `usuario`, `maria` y `cliente`; las claves están definidas en `UsuarioDemoSeeder` y no se repiten en esta guía. Las evaluaciones demo pasan por el caso de uso real y pueden modificar los saldos iniciales de las cuentas.

Con `DEMO_DATA_ENABLED=false`, no se provisionan automáticamente ni usuarios ni catálogos. No hay alta pública ni bootstrap HTTP del primer administrador: una base no demo requiere un procedimiento de aprovisionamiento propio, con hash BCrypt, roles, cuentas y reglas coherentes.

Desactivar la siembra **no elimina ni bloquea** cuentas demo ya persistidas. Antes de reutilizar una base de desarrollo fuera de ese entorno, revisar y cambiar/desactivar esas credenciales.

### Conjunto ampliado con cuentas e historial

`DEMO_DATASET_ENABLED=true` activa una carga independiente con ocho clientes, ocho cuentas, diez usuarios y 5.252 operaciones históricas, con ingresos y egresos diarios durante un año. Provisiona los catálogos necesarios sin exigir que se activen los fixtures originales. Las cuentas, claves iniciales y saldos esperados están en [cuentas y accesos demo](11_CUENTAS_Y_ACCESOS_DEMO.md).

La carga usa identidades de operación estables y no restablece contraseñas o saldos al repetirse. Se puede desactivar la propiedad después de completarla; los datos permanecen. El modo `app.demo.dataset.exit-after-load=true` permite terminar después de confirmar la carga, con un servidor en puerto aleatorio para no interferir con otra instancia.

## 7. Ejecutar y empaquetar

Desarrollo, PowerShell:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Linux/macOS:

```bash
sh ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Empaquetar, ejecutando las pruebas:

```powershell
.\mvnw.cmd clean package
java -jar target/fraude-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

Ejecutar desde la raíz permite cargar `.env`. El JAR contiene backend, plantillas Thymeleaf y recursos estáticos. No existe un proceso frontend separado que haya que levantar.

El runtime valida el esquema durante el arranque. No se recomienda usar un arranque sin entorno web como comando de validación genérico: la aplicación contiene configuración de seguridad servlet y el procedimiento normal es arrancar su contexto web con una base preparada.

## 8. Verificación de funcionamiento

- Leer logs hasta confirmar que el contexto arrancó sin errores de esquema o conexión.
- Abrir `/actuator/health`: con dependencias sanas debería devolver `{"status":"UP"}` sin detalles; un fallo real puede cambiar su estado y código HTTP.
- Abrir `/login`, autenticarse y confirmar la pantalla correspondiente al rol.
- Consultar una API de lectura con la misma sesión y confirmar que no redirige a login.
- Probar un acceso sin permisos y comprobar el rechazo.
- Si se hace una prueba financiera, usar una base de desarrollo y comprobar el saldo de servidor, no solo el texto de la pantalla.

## 9. Diagnóstico

| Síntoma | Comprobaciones |
| --- | --- |
| `release version 21 not supported` | JAVA_HOME y `java -version`; el proceso usa un JDK anterior |
| Conexión PostgreSQL rechazada | Servicio, puerto, DB_HOST, credenciales y red |
| Error de validación de esquema | Base correcta, script nuevo frente a migración y columnas pendientes |
| Base nueva sin usuarios o reglas | Siembra desactivada o aprovisionamiento incompleto |
| Un login vuelve a `/login?error` | Estado, clave, rol activo y resultado del formulario; no se revela públicamente qué comprobación falló |
| API redirige a `/login` | Cookie ausente, sesión vencida, usuario eliminado/bloqueado o clave cambiada; también rol sin permiso o CSRF inválido |
| Escritura redirige a `/login` | Verificar token y cookie de la misma sesión; después ruta, método y rol. La sesión anterior queda cerrada |
| 429 | Esperar el plazo indicado; revisar tráfico automático/polling y configuración del límite |
| No aparece un historial en portal | Vínculo de cliente activo y evaluaciones activas propias |
| Simulador falla con códigos demo | El generador automático presupone C-001/C-002/C-003 |
| El esquema no cambia al reiniciar Compose | El volumen ya estaba inicializado; aplicar migración, no borrar datos |
| `clean` falla por archivos bloqueados | Detener procesos que usan target; si solo se verifica código, `mvnw.cmd test` puede evitar el borrado |
| Node informa que no hay versión activa | Instalar/seleccionar Node 22 en el gestor del equipo; no afecta al servidor Java |

Los errores internos de API no exponen stacktraces al cliente. Investigar el detalle en logs protegidos, evitando publicar credenciales o datos personales.

## 10. Configuración de despliegue

Antes de exponer la aplicación: HTTPS, cookies Secure, credenciales de base propias, demo desactivada, revisión de cuentas existentes, secretos fuera del repositorio, copias/restauración y política de logs.

Ejecuta `java -jar target/fraude-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod` con `DB_USER` y `DB_PASSWORD` definidos. El perfil `prod` fija cookies Secure, redirección a HTTPS, SQL desactivado y ambas cargas demo deshabilitadas. Usa un archivo de entorno propio del despliegue, sin el perfil `dev`. El perfil no configura certificados: proporciona TLS en el servidor o en un proxy. Si TLS termina en un proxy, configura únicamente sus direcciones como proxies confiables para que las peticiones reenviadas se reconozcan como HTTPS; no habilites confianza global en cabeceras de clientes externos.

El servidor conserva sesiones en memoria por defecto. Un reinicio requiere volver a iniciar sesión; varias réplicas necesitan una estrategia compartida o afinidad apropiada. Los límites también son por instancia; el proxy debe cubrir la política global y la IP confiable.

El workflow CI prueba y publica cobertura; **no despliega**. El proyecto no proporciona una receta completa de producción, aprovisionamiento de infraestructura, migración automática ni gestión distribuida de sesiones.

## Configuración opcional de OpenRouter

El asistente es independiente de PostgreSQL y no requiere migración. Completar `LLM_ENABLED`, `LLM_API_KEY`, `LLM_BASE_URL` y `LLM_MODEL` en `.env` y reiniciar. Sin configuración, la aplicación sigue funcionando y muestra el asistente pendiente. [Guía completa](12_ASISTENTE_REGLAS_IA.md).
