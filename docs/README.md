# Documentación técnica de Lambda Shield

Documentación del código presente en el repositorio, revisada el **14 de noviembre de 2026**. La interfaz permanece en **Spring MVC + Thymeleaf**, con CSS y JavaScript vanilla; una migración a páginas estáticas queda fuera del alcance actual.

## Documentos

| Guía | Qué explica |
| --- | --- |
| [README general](../README.md) | Propósito, inicio rápido, estructura y decisiones principales |
| [01 — Arquitectura](01_ARQUITECTURA.md) | Capas, componentes, filtros, renderizado, dependencias y extensión |
| [02 — Funcionalidades](02_FUNCIONALIDADES.md) | Pantallas, roles, operaciones, reversos, dashboard y gestión de usuarios |
| [03 — Reglas antifraude](03_REGLAS_ANTIFRAUDE.md) | Ocho reglas, umbrales exactos, ponderación, derivación y limitaciones |
| [04 — Datos y consultas JPA](04_CONSULTAS_JPA.md) | Esquema, relaciones, consultas, carga por lote, bloqueos y migración |
| [05 — Valor de negocio](05_VALOR_NEGOCIO.md) | Beneficios esperados, indicadores interpretables y capacidades pendientes |
| [06 — Seguridad](06_SEGURIDAD.md) | Sesiones, CSRF, autorización, límites, cabeceras y límites de protección |
| [07 — API REST](07_API_REST.md) | Rutas, parámetros, DTO, errores y ejemplo de cliente con sesión y CSRF |
| [08 — Instalación y operación](08_INSTALACION_OPERACION.md) | Requisitos, `.env`, PostgreSQL, Docker, arranque, migración y diagnóstico |
| [09 — Pruebas y calidad](09_PRUEBAS_CALIDAD.md) | Suites, comandos, datos de prueba, cobertura, CI y mantenimiento documental |
| [10 — Propuesta de reglas dinámicas y Qwen](10_PROPUESTA_REGLAS_DINAMICAS_LLM.md) | Diseño pendiente: parámetros versionados, severidad y LLM contextual |
| [11 — Cuentas y accesos demo](11_CUENTAS_Y_ACCESOS_DEMO.md) | Usuarios, contraseñas iniciales, cuentas, saldos e historial ampliado |

| [12 — Asistente de reglas IA](12_ASISTENTE_REGLAS_IA.md) | Formulario, conexión OpenRouter, configuración, límites y pruebas |

| [13 — Revisión de congruencia](13_REVISION_CONGRUENCIA_CODIGO.md) | Correcciones, código sin uso, decisiones conservadas y mejoras priorizadas |

## Recorridos de lectura

- **Quiero ejecutarlo:** README general → 08 → 06 → 09.
- **Voy a modificar código:** 01 → 02 → 03 → 04 → 09.
- **Voy a consumir las APIs:** 06 → 07; consultar 03 para interpretar riesgo.
- **Voy a revisar el proyecto:** 05 → 02 → 01 → 06 → 09.

## Convenciones

- Las rutas `/api/v1/**` devuelven JSON o archivos; las rutas MVC devuelven HTML.
- `ADMIN`, `ANALISTA` y `CLIENTE` son roles. Un usuario de acceso y un cliente bancario son entidades diferentes.
- Los ejemplos usan importes y datos ficticios. Los tokens, cookies y claves reales no se deben incorporar a documentos ni registros.
- Los ejemplos de respuesta no garantizan IDs, saldos o fechas: dependen del contenido de la base.
- Las secciones de limitaciones y próximos pasos describen trabajo pendiente, no funciones disponibles.

La referencia definitiva es el código y sus pruebas. Si cambia un contrato o una política, deben actualizarse también las guías afectadas; el procedimiento está en 09.
