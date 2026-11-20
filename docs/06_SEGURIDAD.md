# 06 — Seguridad de sesión, páginas y APIs

[Índice](README.md) · [Referencia HTTP](07_API_REST.md) · [Operación](08_INSTALACION_OPERACION.md)

## 1. Modelo de protección

`SecurityConfig` aplica Spring Security antes de los controladores. `AuthorizationPolicy` contiene permisos explícitos; `SessionValidationFilter` revalida identidades y `RequestRateLimitFilter` limita solicitudes.

No se usa un interceptor MVC como frontera de seguridad. Tampoco se autentica a una persona por la presencia aislada de `usuario`, `rol` o `codigoCliente` en sesión: debe existir una identidad admitida en el `SecurityContext`.

El esquema actual es de sesión HTTP del mismo origen. No hay JWT, API key para consumidores ni autenticación HTTP Basic habilitada como alternativa.

## 2. Rutas públicas y protegidas

Se permiten GET de `/login`, `/actuator/health` y recursos bajo `/css/`, `/js/` e `/img/`. POST de `/login` es accesible para autenticarse, pero continúa requiriendo CSRF y respetando el límite de intentos.

El procesamiento interno de errores tiene una excepción por tipo de despacho. Esto no convierte cualquier URL que empiece por `/error` en un recurso público de negocio.

| Capacidad API | ADMIN | ANALISTA | CLIENTE |
| --- | --- | --- | --- |
| GET/HEAD de evaluaciones | Sí | Sí | No |
| POST de evaluación | Sí, señales manuales | No | Sí, cliente y señales derivados |
| PATCH de evaluación/reactivación | Sí | No | No |
| GET/HEAD de métricas, reglas y exportación | Sí | Sí | No |
| Gestión `/user` | Sí, métodos declarados | No | No |
| DELETE de evaluaciones | No | No | No |
| Ruta o método no declarado | No | No | No |

HEAD se admite en las rutas de lectura API expresamente declaradas. OPTIONS no tiene una autorización general de API ni se habilita CORS para otro origen.

Los IDs de rutas administrativas deben tener la forma admitida por la política. Un ID numérico no positivo llega a validación y puede devolver 400; uno no numérico puede ser rechazado con 403 antes de llegar al controlador. El firewall rechaza formas ambiguas como rutas con punto y coma.

## 3. Autenticación

1. El formulario Thymeleaf presenta CSRF.
2. Spring Security procesa `usuario` y `clave` en POST `/login`.
3. `AuthService` recorta y normaliza el nombre a minúsculas, valida la clave con BCrypt, estado ACTIVO y rol disponible.
4. Para usuarios inexistentes se ejecuta también una comparación BCrypt simulada, reduciendo diferencias triviales de tiempo; no es una garantía de tiempo constante de todo el login.
5. Se crea un principal `UsuarioSesion`; su texto no imprime el hash de credencial.
6. Spring Security rota el identificador de sesión al autenticar.

El hash se conserva dentro de la identidad del servidor para detectar cambios de clave al revalidar. No se devuelve en los DTO de usuarios ni en respuestas de evaluación.

No hay alta pública, flujo de recuperación ni MFA. Los límites y la validación no sustituyen esos mecanismos.

## 4. Revalidación y revocación

En cada petición admitida por el límite, si existe el principal de aplicación, se consulta al usuario y se resuelven estado, rol y cliente activos.

| Cambio en BD | Efecto sobre una sesión abierta |
| --- | --- |
| Usuario bloqueado/inactivo | Se invalida y pierde autenticación |
| Usuario eliminado | Se invalida |
| Cambio del hash de contraseña | Se invalida |
| Ningún rol activo reconocido | Se invalida |
| Cambio de rol | Los permisos se actualizan en la petición |
| Cambio de cliente asociado | Se usa el nuevo vínculo y se corrigen etiquetas de sesión |
| Cliente bancario inactivo | No se resuelve su código activo; el portal no recibe historial global y el POST del cliente sin vínculo se rechaza |

ADMIN prevalece sobre ANALISTA y CLIENTE. Las etiquetas de sesión sirven para vistas, pero se regeneran a partir de la identidad vigente.

Las peticiones que exceden el límite pueden responder 429 antes de consultar la identidad. La revocación se comprueba en la siguiente petición que continúe por la cadena.

## 5. CSRF y mismo origen

Todas las escrituras —POST, PATCH, PUT, DELETE y demás métodos no seguros— quedan sujetas a CSRF, incluido login y logout.

Se utiliza el repositorio de token de sesión y el manejo enmascarado de Spring Security. Thymeleaf integra tokens en formularios; el fragmento de cabecera expone `_csrf` y `_csrf_header` para JavaScript.

`LSApi.fetch`:

- Resuelve la URL y exige el mismo origen.
- Lee las etiquetas CSRF para escrituras y añade el encabezado indicado.
- Envía cookies con `credentials: same-origin`.
- Redirige a login ante 401 y presenta un aviso ante 403.
- No envía el token a un destino externo.

El token no sustituye la cookie autenticada. Un token de otra sesión no sirve. Tras login debe usarse un token renovado; un cliente HTTP debe conservar la cookie y volver a obtener el token desde una página protegida.

Si falta CSRF, puede recibirse 403 incluso sin sesión autenticada, antes de llegar a autorización. Nunca se debe desactivar CSRF solo para facilitar una prueba de POST con cookies.

## 6. Aislamiento de cliente y validación

El controlador de evaluación ignora el cliente declarado para el rol CLIENTE y usa el vínculo revalidado. Hora, frecuencia, dispositivo e IP se vuelven a derivar en el servicio. El historial del portal se consulta por ese código; no hay un fallback a todas las operaciones.

Bean Validation restringe valores, longitudes y rangos de DTO y parámetros. El dato sigue validándose aunque después sea sustituido por una señal derivada. Las entidades no se serializan directamente como JSON.

La derivación no hace confiables todos los datos: el país continúa declarado, la huella usa User-Agent y la titularidad de cuentas externas no se verifica con una entidad bancaria. No hay una prueba fuerte de identidad del dispositivo o de titularidad del destinatario.

## 7. Límites de solicitudes

| Límite | Valor inicial | Clave |
| --- | --- | --- |
| Login | 10/minuto | IP de conexión |
| API total | 120/minuto | Nombre de identidad; IP cuando no hay autenticación disponible |
| Escrituras API | 40/minuto | La misma clave de identidad/IP, con ventana independiente |

Se usan ventanas fijas de minuto calculadas con el reloj, no una ventana móvil. Cambiar de sesión no evita el límite de login por IP. La clave usa la conexión remota y no confía en un X-Forwarded-For arbitrario.

Cada ventana tiene almacenamiento acotado a 10 000 claves. Si se llena y no puede retirar ventanas vencidas, rechaza nuevas claves en lugar de crecer sin límite. Reiniciar la instancia reinicia los contadores.

El rechazo devuelve 429, JSON y `Retry-After: 60`; el valor es conservador y no calcula exactamente el tiempo restante del minuto. Los intentos rechazados por permisos o CSRF también pueden consumir el límite porque este se ejecuta antes.

Configuración mediante `LOGIN_RATE_LIMIT`, `API_RATE_LIMIT` y `API_WRITE_RATE_LIMIT`. Deben ser positivos. En despliegues con múltiples instancias se necesita además una política compartida o de proxy; la implementación no ofrece un contador distribuido.

## 8. Cookies, sesión y cabeceras

| Control | Configuración actual |
| --- | --- |
| Tiempo de inactividad | 20 minutos |
| Seguimiento de sesión | Cookie, sin identificadores en URL |
| `HttpOnly` | Activado |
| `SameSite` | Lax |
| `Secure` | Variable `SESSION_COOKIE_SECURE`, false para desarrollo HTTP |
| Caché de respuestas sensibles | Cabeceras de Spring Security con `no-store` |
| Marcos | `X-Frame-Options: DENY` y CSP `frame-ancestors 'none'` |
| Interpretación de MIME | `X-Content-Type-Options: nosniff` |
| Referente | `same-origin` |
| HSTS | Cabecera estándar en solicitudes que Spring reconoce como seguras |

La CSP permite scripts del mismo origen y jsDelivr; estilos del mismo origen, jsDelivr y Google Fonts, con estilos inline para las plantillas actuales. Restringe conexiones a self y bloquea objetos, marcos y formularios a otros destinos. Las dependencias CDN siguen siendo una dependencia externa de la interfaz.

En HTTPS se debe configurar `SESSION_COOKIE_SECURE=true`. Si TLS termina en un proxy, hay que comprobar qué entiende el servidor por conexión segura; no se debe aceptar cualquier encabezado de proxy sin una frontera de confianza definida.

## 9. Errores y datos expuestos

Los filtros usan `{"error":"..."}`; la validación y excepciones de aplicación suelen usar `{"errores":[...]}`. Las APIs privadas no redirigen a un formulario HTML por falta de autenticación: reciben 401, salvo un rechazo anterior como CSRF o límite.

El manejador de errores internos devuelve un mensaje genérico en la API. La configuración evita incluir stacktraces, mensajes de servidor y errores de binding en la respuesta estándar de Spring Boot. Esto no significa que toda página de error de aplicación tenga exactamente el mismo formato o cuerpo.

No deben registrarse cookies, tokens CSRF, claves ni cuerpos con información personal innecesaria. La configuración actual muestra SQL en desarrollo; el despliegue puede desactivarlo mediante configuración.

## 10. Datos demo y límites de la protección

`DEMO_DATA_ENABLED=false` evita ejecutar el coordinador de siembra. No borra cuentas demo anteriores ni cambia sus claves. Activarlo en producción puede crear usuarios con claves conocidas en el repositorio o reponer relaciones de fixtures.

La seguridad de aplicación no incluye gestión centralizada de secretos, WAF, MFA, sesiones compartidas, defensa completa frente a ataques distribuidos ni auditoría inmutable. Las pruebas cubren escenarios específicos; no equivalen a una auditoría de infraestructura o certificación.

Los pasos operativos se describen en [instalación](08_INSTALACION_OPERACION.md) y la evidencia de pruebas en [calidad](09_PRUEBAS_CALIDAD.md).

## Protección de la bandeja personal

Las tres rutas `/api/v1/notification` requieren CLIENTE. La bandeja, el contador y las actualizaciones se filtran por el usuario autenticado. Dos usuarios del mismo cliente tienen lecturas independientes. Las escrituras PATCH requieren CSRF; un aviso ajeno devuelve el mismo 404 que uno inexistente. El navegador construye títulos y mensajes con `textContent`, evitando interpretar contenido como HTML.

## Asistente administrativo IA

Las rutas del asistente requieren ADMIN y las revisiones POST requieren CSRF. El proveedor solo recibe el formulario; el adaptador no consulta datos bancarios. La clave permanece en el servidor. Se aplican límite propio de seis revisiones/minuto, concurrencia acotada y timeout. Los resultados se muestran como texto y no se ejecutan. Consulte [el alcance y controles](12_ASISTENTE_REGLAS_IA.md).

## Naturaleza financiera del portal

`crearYEvaluarCliente` rechaza tipos de naturaleza CREDITO antes de evaluar o modificar dinero. La categoría del cuerpo HTTP no puede convertir la operación del cliente en un abono. Los ingresos demo y las pruebas administrativas usan el flujo separado autorizado. Los beneficiarios desactivados tampoco pueden reutilizarse omitiendo la cuenta y enviando solo su nombre.
