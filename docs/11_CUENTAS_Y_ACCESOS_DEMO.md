# 11 — Cuentas, accesos e historial de demostración

[Índice](README.md) · [Instalación](08_INSTALACION_OPERACION.md)

## Carga inicial y ampliación

**Carga completada y verificada en PostgreSQL el 15 de noviembre de 2026**, en la base `lambda_shield` (PostgreSQL 18.3).

Se confirmaron 8 clientes, 8 cuentas, 10 usuarios activos, 128 transacciones, 128 evaluaciones y 1 024 factores. El historial abarca del 11 de octubre al 14 de noviembre de 2026. Las diez contraseñas documentadas se comprobaron contra sus hashes BCrypt y los ocho saldos coinciden con los movimientos aprobados.

La carga automática quedó desactivada en `.env` (`DEMO_DATASET_ENABLED=false`); los datos ya están persistidos y no requieren volver a simularse.

Los nombres, documentos, correos, números y claves de este archivo son datos ficticios de demostración. Las contraseñas se publican aquí por solicitud explícita y no deben reutilizarse fuera de desarrollo. La contraseña de PostgreSQL no se incluye: permanece en `.env`, excluido de Git.

Una cuenta bancaria no tiene una contraseña separada en este modelo: se accede mediante el usuario vinculado al cliente titular.

**Ampliación del 20/11/2026 completada en PostgreSQL:** 732 movimientos nuevos. Se verificaron ingresos y egresos aprobados en las 366 fechas del rango anual. La carga automática sigue desactivada.

## Accesos administrativos

| Rol | Usuario | Contraseña inicial | Pantalla |
| --- | --- | --- | --- |
| ADMIN | `demo.admin` | `DemoAdmin!2026` | `/` y `/usuarios` |
| ANALISTA | `demo.analista` | `DemoAnalista!2026` | `/` y `/reportes` |

## Clientes y cuentas

Las ocho cuentas son de AHORROS, están activas y usan PEN. Los saldos de la tabla fueron **verificados al completar la carga en PostgreSQL**, antes de hacer operaciones adicionales o reversos. La aplicación muestra el saldo vigente de la base.

| Titular | Código de cliente | Número de cuenta | Usuario de acceso | Contraseña inicial | Saldo al cargar |
| --- | --- | --- | --- | --- | --- |
| Lucía Vargas Mora | D-101 | `0011-2026-0000000101` | `demo.lucia` | `DemoLucia!2026` | S/ 9,247.30 |
| Carlos Rojas Díaz | D-102 | `0011-2026-0000000102` | `demo.carlos` | `DemoCarlos!2026` | S/ 14,047.30 |
| Ana Torres Vega | D-103 | `0011-2026-0000000103` | `demo.ana` | `DemoAna!2026` | S/ 7,947.30 |
| Jorge Salas Mendoza | D-104 | `0011-2026-0000000104` | `demo.jorge` | `DemoJorge!2026` | S/ 19,647.30 |
| Valeria López Cruz | D-105 | `0011-2026-0000000105` | `demo.valeria` | `DemoValeria!2026` | S/ 4,647.30 |
| Diego Castro León | D-106 | `0011-2026-0000000106` | `demo.diego` | `DemoDiego!2026` | S/ 10,947.30 |
| Sofía Mendoza Ríos | D-107 | `0011-2026-0000000107` | `demo.sofia` | `DemoSofia!2026` | S/ 16,247.30 |
| Mateo Ruiz Flores | D-108 | `0011-2026-0000000108` | `demo.mateo` | `DemoMateo!2026` | S/ 3,847.30 |

Todos estos usuarios de cliente entran en `/operacion`. Sus correos ficticios siguen el formato `usuario@example.test`.

## Qué contiene cada historial

Cada cuenta conserva sus 16 operaciones originales durante los 35 días anteriores a la carga inicial:

| Operación | Antigüedad | Efecto con el catálogo estándar |
| --- | --- | --- |
| Depósito de apertura | 366 días; anterior al gráfico anual | Crédito aprobado |
| Servicios | 32 días | Débito aprobado |
| Restaurante | 29 días | Débito aprobado |
| Compras | 26 días | Débito aprobado |
| Haberes | 366 días, después de la apertura; anterior al gráfico anual | Crédito aprobado |
| Servicios | 19 días | Débito aprobado |
| Transferencia | 16 días | Débito aprobado |
| Compras | 13 días | Débito aprobado |
| Reembolso | 10 días | Crédito aprobado |
| Electrónica con señales inusuales | Una por mes: entre 8 y 218 días | Nivel MEDIO, aprobado con monitoreo |
| Restaurante | 6 días | Débito aprobado |
| Operación de riesgo alto | 5 días | RETENIDA, sin mover saldo |
| Operación crítica | 4 días | BLOQUEADA, sin mover saldo |
| Servicios | 3 días | Débito aprobado |
| Abono adicional | 2 días | Crédito aprobado |
| Compras | 1 día | Débito aprobado |

Desde el 20/11/2026, el loader añade **732 operaciones** a las 128 originales: un ingreso y un egreso por cada uno de los últimos 365 días y hoy (366 fechas), alternando las ocho cuentas demo. Los importes varían por día y cuenta; las categorías incluyen servicios, compras, restaurante y transferencia. Así se llenan los rangos 7d, 15d, 1m, 3m y 1a del gráfico.

La segunda ampliación incorpora otras **4.392 operaciones**: seis ingresos y seis gastos diarios durante las 366 fechas del rango anual. Una carga completa nueva contiene **5.252 transacciones/evaluaciones** y 42.016 factores si las ocho reglas están activas. Con los pesos estándar: 5.228 evaluaciones BAJO, 8 MEDIO, 8 ALTO y 8 CRITICO; 5.236 transacciones aprobadas, 8 retenidas y 8 bloqueadas. Los saldos de la tabla anterior corresponden a la carga inicial: los movimientos añadidos actualizan el saldo mediante el servicio de evaluación, sin restablecerlo.

Las primeras 16 operaciones mantienen sus identificadores; repetir la carga omite cualquier operación ya existente. Los registros anteriores conservan sus fechas, estados y saldos. La ampliación usa los días relativos a la primera carga ampliada; repetirla no mueve ese historial a fechas nuevas.

Los ingresos se registran como créditos reales en el modelo y los gastos aprobados como débitos. Los saldos anterior/posterior siguen el orden de inserción; las fechas históricas sirven a la demostración del gráfico. No son transferencias a una red bancaria externa.

El dashboard y los reportes ya tienen ingresos, egresos y varios niveles de riesgo para consultar; no hace falta iniciar el tráfico automático del simulador. El periodo de siete días muestra las operaciones recientes y los periodos de 30/365 días amplían el historial.

## Cómo cargar una vez

Completar los datos reales de conexión en `.env`:

```properties
DB_HOST=localhost
DB_PORT=5432
DB_NAME=lambda_shield
DB_USER=postgres
DB_PASSWORD=<completar en el archivo local>
DEMO_DATA_ENABLED=false
DEMO_DATASET_ENABLED=true
```

Host, puerto, base y usuario del ejemplo deben sustituirse por la conexión elegida. No se ejecuta una migración automáticamente: preparar primero una base nueva con el esquema o aplicar la migración idempotente a una existente.

Al arrancar normalmente:

```powershell
.\mvnw.cmd spring-boot:run
```

La carga ampliada es independiente de los tres clientes demo antiguos. También se puede ejecutar puntualmente sin dejarla activada en `.env`:

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--app.demo.dataset.enabled=true --app.demo.dataset.exit-after-load=true --server.port=0 --spring.jpa.show-sql=false"
```

El modo puntual cierra la aplicación después del evento de arranque completo, cuando la transacción de carga ya se confirmó. No arranca otro servicio de base de datos ni cambia credenciales existentes.

Una vez cargado, configurar `DEMO_DATASET_ENABLED=false`: los registros continúan en PostgreSQL y no es necesario volver a generarlos para usar las pantallas.

## Repetición y conservación

- Cada operación del conjunto tiene un UUID determinista por cliente e índice.
- Repetir la carga omite las operaciones ya creadas y no descuenta el mismo gasto dos veces.
- No reinicia saldos ni contraseñas y no reactiva operaciones o usuarios modificados después de la carga.
- Si se cambia una contraseña, la tabla de este archivo conserva su valor inicial; el nuevo valor se administra desde `/usuarios`.
- Una colisión incompatible de usuario, cliente, cuenta o tipo provoca rollback en lugar de apropiarse de un registro ajeno.
- Las reglas y los roles faltantes se provisionan con el catálogo demo; los existentes no se sobrescriben. Cambiar sus pesos puede producir resultados distintos; si se retiene un ingreso, la carga se rechaza para no dejar el conjunto sin financiación.
- La carga se prueba como una ejecución a la vez. No debe lanzarse simultáneamente desde varias instancias.

## Dónde se define y cómo se verifica

- `DatasetDemo`: personas y credenciales iniciales.
- `DatasetDemoLoader`: creación aditiva, historial, saldos y referencias.
- `DatasetDemoRunner`: activación por propiedad y modo puntual.
- `DatasetDemoLoaderTest`: alta, saldos, niveles, acceso con las diez credenciales y repetición sin sobrescribir cambios.

Las contraseñas se almacenan como hashes BCrypt en la base; este archivo solo documenta las claves iniciales conocidas del conjunto ficticio.

## Contactos y notificaciones del portal

Las ocho cuentas tienen el número indicado en la tabla de accesos. Cada persona tiene agregadas a las otras siete como beneficiarios conocidos: 56 contactos numerados. En `/operacion`, **Mis contactos** completa nombre, banco y cuenta; el número se envía y se guarda con la operación. El servidor usa los datos internos y rechaza la cuenta propia como destino.

La migración `migracion_contactos_notificaciones.sql` se aplicó a PostgreSQL el 17 de noviembre de 2026: creó 56 contactos y 16 avisos históricos (un bloqueo y una retención por persona). No modificó saldos ni contraseñas. La campana permite expandir cada aviso para marcarlo leído o marcar todos; la lectura se conserva al cerrar sesión. Las futuras operaciones bloqueadas/retenidas generan avisos en la misma transacción de base de datos.

La carga demo también agrega los contactos de forma aditiva. No necesita permanecer habilitada para usar agenda y avisos. Aprobar una operación sigue sin acreditar una cuenta receptora: se guarda la referencia del destinatario, sin compensación bancaria.

### Ajuste del pico del 11/10/2026

Los ocho depósitos de apertura (S/77.600 en total) se trasladaron a una fecha anterior al rango anual, conservando importes y saldos. El 11/10/2026 mantiene sus dos movimientos diarios: ingreso de S/206,45 y egreso de S/107,80. Las nuevas cargas también sitúan la apertura fuera del rango anual.

### Ajuste del pico del 24/10/2026

Los ocho abonos de haberes demo (S/23.400) se trasladaron junto a la apertura, fuera del rango anual. Se conservaron importes y saldos. El 24/10/2026 mantiene un ingreso de S/160,85 y un egreso de S/69,40. Las cargas nuevas aplican la misma fecha para evitar acumular los ocho haberes dentro del gráfico anual.

### Distribución del pico del 07/11 y segunda ampliación anual

Las ocho compras de electrónica del 07/11/2026 (S/8.400) se repartieron en ocho meses, conservando importes y resultados de evaluación. La compra de D-101 permanece el 07/11; las demás retroceden 30 días adicionales por cuenta. Las futuras cargas también separan esas ocho compras.

Se añaden 4.392 operaciones con identificadores nuevos al final de cada historial, respetando las 860 existentes. El gráfico cuenta así con al menos 14 movimientos habituales por día, más los casos originales y las operaciones manuales. Los nuevos ingresos oscilan entre S/90 y S/212,55 y los gastos entre S/20 y S/142,55, con categorías y horas variadas.

La segunda ampliación se confirmó en PostgreSQL el 20/11/2026: **4.392 transacciones nuevas**, con un mínimo de 14 movimientos aprobados y con ingresos y egresos en las 366 fechas del rango anual. El 07/11 quedó con 15 movimientos, S/913,65 de ingresos y S/1.101,60 de egresos al verificar la carga.
