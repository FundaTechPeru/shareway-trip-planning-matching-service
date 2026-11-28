# ShareWay Trip Planning & Matching Service

Microservicio de ShareWay encargado de planificar solicitudes de viaje y generar propuestas de matching entre pasajeros y conductores disponibles.

## Stack

- Java 21 LTS
- Spring Boot 4.1.1
- Spring Web MVC
- Spring Data JPA
- jjwt 0.12.6 (validacion de tokens HS256)
- Bean Validation
- Flyway
- PostgreSQL 16
- Docker Compose
- Swagger / OpenAPI
- Postman

## Responsabilidad del microservicio

Este servicio concentra las reglas de negocio relacionadas con:

- Registro de solicitudes de viaje de pasajeros.
- Registro de disponibilidad operativa de conductores.
- Busqueda de conductores candidatos por zona, fecha, ventana horaria y cupos.
- Generacion de propuestas de viaje con tarifa y duracion estimadas.
- Generacion idempotente de propuestas para evitar duplicados durante las pruebas.
- Aceptacion o rechazo del grupo por el conductor (US-21).
- Publicacion de eventos de grupo hacia Booking & Pricing mediante un outbox transaccional.

No debe manejar pagos, liquidaciones, validacion de abordaje, notificaciones en tiempo real ni gestion documental del conductor. Esos flujos pertenecen a otros bounded contexts.

## Identificadores

Los IDs propios del servicio (`tripRequest.id`, `driverAvailability.id` y `matchProposal.id`) son UUID. Las rutas que reciben `tripRequestId` esperan un UUID.

Los campos `passengerId` y `driverId` tambien son UUID: corresponden al `sub` del JWT emitido por Driver Management. La migracion `V3__change_user_ids_to_uuid.sql` convierte los valores numericos existentes al mismo formato determinista usado en V2 (por ejemplo, `25` pasa a `00000000-0000-0000-0000-000000000025`).

## Autenticacion (JWT)

El API Gateway reenvia sin cambios el header `Authorization: Bearer <token>`. El servicio valida el token HS256 emitido por Driver Management con `JWT_SECRET` (el mismo secreto del gateway y de los demas servicios) usando jjwt:

- Usuario: claim `sub` (UUID).
- Rol: claim `role` (`PASSENGER`, `DRIVER` o `ADMIN`).

Las cabeceras `X-User-Id` y `X-User-Role` ya no se usan para identificar al usuario.

Fuera de los perfiles `dev`, `test` y `local` el servicio no arranca sin un `JWT_SECRET` de al menos 32 caracteres, ni con el valor de ejemplo de desarrollo (`JwtSecretValidator`, copiado de Driver Management).

Reglas:

- `POST /api/v1/trip-requests`: requiere rol `PASSENGER`; el pasajero es el `sub` del token.
- `GET /api/v1/trip-requests`: devuelve solo las solicitudes del usuario; `ADMIN` ve todas.
- `GET` y `POST /api/v1/trip-requests/{tripRequestId}/match-proposals`: solo el pasajero duenio de la solicitud o un `ADMIN`. Para cualquier otro usuario responde `404`.
- `POST /api/v1/driver-availabilities`: requiere rol `DRIVER`; el conductor es el `sub` del token.
- `GET /api/v1/driver-availabilities`: el `DRIVER` ve las suyas; el `ADMIN` todas. Otros roles reciben `403`.
- `POST /api/v1/driver-availabilities/{id}/accept` y `/reject`: solo el `DRIVER` duenio. Si no es suya, `404`; si ya no esta `OPEN`, `409`.
- `passengerId` y `driverId` no forman parte de los cuerpos de entrada. Si un cliente los sigue enviando, se ignoran.

Errores (formato `ApiError`):

- `401 Unauthorized`: falta el token Bearer, o es invalido, esta vencido o su `sub` no es un UUID.
- `403 Forbidden`: el claim `role` falta o no corresponde a la operacion.
- `404 Not found`: el recurso no existe o pertenece a otro usuario.
- `409 Conflict`: la disponibilidad ya fue aceptada o rechazada.

## Grupos de conductor (US-21)

Un grupo de ShareWay es la disponibilidad que publica un conductor (`DriverAvailability`). El `groupId` que usa Booking & Pricing es el `id` de la disponibilidad.

- Estados: `OPEN` (al crearla), `ACCEPTED` o `REJECTED`. El matching solo usa disponibilidades `OPEN`.
- Cada propuesta guarda de que disponibilidad salio. `MatchProposalResponse` incluye `groupId`, `availableDate`, `startTime` y `endTime` del grupo. Las propuestas creadas antes de V4 tienen esos campos en `null`.
- Aceptar: la disponibilidad pasa a `ACCEPTED` y sus propuestas `PROPOSED` pasan a `ACCEPTED`.
- Rechazar: la disponibilidad pasa a `REJECTED` y sus propuestas `PROPOSED` pasan a `REJECTED`.

## Eventos a Booking & Pricing (outbox)

Los eventos se guardan en la tabla `outbox` en la misma transaccion que el cambio que los produce. La migracion V5 encola la apertura de las disponibilidades existentes para conservar su `groupId` y capacidad.

Cuando `OUTBOX_RELAY_ENABLED=true`, el proceso programado envia los eventos en orden cada 5 s a:

```text
POST {BOOKING_URL}/api/v1/internal/events
X-Internal-Api-Key: <INTERNAL_API_KEY>
Content-Type: application/json

{"eventId": "<uuid>", "occurredAt": "<ISO-8601>", "type": "<tipo>", ...campos}
```

| Momento | Eventos (en orden) | Campos |
| --- | --- | --- |
| Crear disponibilidad | `TripGroupOpened` | `groupId`, `capacity` (= `availableSeats`) |
| Aceptar | `GroupCompleted`, luego `ProposalAccepted` | `groupId`; `groupId`, `driverId` |
| Rechazar | `GroupRejected` | `groupId`, `driverId` |

Respuesta de Booking:

- `2xx`: el evento queda procesado (`processed_at`).
- Timeout, error de conexion, `5xx`, `401`, `404`, `405`, `408` o `429` (y cualquier `1xx`/`3xx` inesperado): se reintenta con espera exponencial (5 s, 10 s, 20 s... hasta 5 minutos) y el lote se detiene para que ningun evento posterior se adelante. Esto cubre despliegues donde el receptor aun no esta disponible.
- Cualquier otro `4xx`: el evento se marca fallido (`failed_at`), se registra el error en el log y en `last_error`, y se continua con el siguiente.

El `eventId` se fija al guardar el evento. Booking lo usa para descartar duplicados incluso si coinciden dos instancias durante un despliegue. El timeout HTTP es de 90 s para permitir el arranque en frio de Booking.

## Configuracion

| Variable | Uso |
| --- | --- |
| `JWT_SECRET` | Secreto HS256 compartido con el gateway y Driver Management. Obligatorio fuera de `dev`, `test` y `local`. |
| `INTERNAL_API_KEY` | Valor del header `X-Internal-Api-Key` en las llamadas internas. Obligatorio fuera de `dev`, `test` y `local`. |
| `BOOKING_URL` | URL de Booking & Pricing. Por defecto `https://shareway-booking-pricing.onrender.com`. |
| `OUTBOX_RELAY_ENABLED` | Por defecto `false`: conserva los eventos pendientes. En Render, poner `true` cuando Booking tenga disponible `POST /api/v1/internal/events` y ambos servicios usen la misma clave. |
| `SPRING_PROFILES_ACTIVE` | `dev` o `local` usan los valores compartidos de desarrollo de `JWT_SECRET` y `INTERNAL_API_KEY`. |

## Ejecutar localmente

`compose.yaml` arranca el servicio con el perfil `dev`, que usa los valores compartidos de desarrollo de `JWT_SECRET` e `INTERNAL_API_KEY`:

```bash
docker compose up --build
```

Sin Docker:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Health check:

```bash
curl http://localhost:8081/actuator/health
```

Swagger UI:

```text
http://localhost:8081/swagger-ui.html
```

OpenAPI JSON:

```text
http://localhost:8081/v3/api-docs
```

Postman collection:

```text
postman/ShareWay Trip Planning Matching.postman_collection.json
```

## Despliegue en Render con base de datos Filess.io

El repositorio incluye `render.yaml` para crear el Web Service como contenedor Docker en Render. La base de datos no se versiona en el repositorio: se configura con variables de entorno usando la instancia PostgreSQL creada en Filess.io.

### 1. Crear la base de datos en Filess.io

Crear una instancia PostgreSQL y copiar sus datos de conexion:

- Host
- Puerto
- Nombre de base de datos
- Usuario
- Password

Para Spring Boot, la URL debe quedar con formato JDBC:

```text
jdbc:postgresql://<filess-host>:<filess-port>/<filess-database>?currentSchema=<filess-user>
```

Filess no crea un schema `public` usable por defecto. Por eso el servicio debe usar un schema propio, normalmente con el mismo nombre del usuario/base de datos entregado por Filess.

### 2. Configurar el servicio en Render

Crear un Web Service desde este repositorio usando Docker y la rama `main`.

Variables de entorno requeridas:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://<filess-host>:<filess-port>/<filess-database>?currentSchema=<filess-user>
SPRING_DATASOURCE_USERNAME=<filess-user>
SPRING_DATASOURCE_PASSWORD=<filess-password>
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=1
SPRING_FLYWAY_SCHEMAS=<filess-user>
SPRING_FLYWAY_DEFAULT_SCHEMA=<filess-user>
JWT_SECRET=<mismo valor que el API Gateway y Driver Management, minimo 32 caracteres>
INTERNAL_API_KEY=<clave compartida por los servicios de ShareWay>
BOOKING_URL=https://shareway-booking-pricing.onrender.com
```

Sin `JWT_SECRET` o sin `INTERNAL_API_KEY` el servicio no arranca.

Render define automaticamente la variable `PORT`; la aplicacion la usa como puerto principal en despliegue. Localmente conserva `8081` como valor por defecto.

Health check para Render:

```text
/actuator/health
```

### 3. Validar el despliegue

Cuando Render termine el deploy, verificar:

```text
https://<render-service>.onrender.com/actuator/health
https://<render-service>.onrender.com/swagger-ui.html
https://<render-service>.onrender.com/v3/api-docs
```

## Endpoints iniciales

Todas las llamadas llevan un token de Driver Management. Se obtiene con `POST /api/v1/auth/login` (a traves del gateway) y se envia como `Authorization: Bearer <token>`. En los ejemplos, `$DRIVER_TOKEN` y `$PASSENGER_TOKEN` son tokens con rol `DRIVER` y `PASSENGER`.

Crear disponibilidad de conductor (abre un grupo y encola `TripGroupOpened`):

```bash
curl -X POST http://localhost:8081/api/v1/driver-availabilities \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DRIVER_TOKEN" \
  -d '{
    "operationZone": "Chorrillos",
    "availableDate": "2026-10-04",
    "startTime": "07:00:00",
    "endTime": "09:00:00",
    "availableSeats": 3
  }'
```

Listar mis disponibilidades:

```bash
curl http://localhost:8081/api/v1/driver-availabilities \
  -H "Authorization: Bearer $DRIVER_TOKEN"
```

Crear solicitud de viaje:

```bash
curl -X POST http://localhost:8081/api/v1/trip-requests \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $PASSENGER_TOKEN" \
  -d '{
    "originLabel": "UPC Monterrico",
    "destinationLabel": "Chorrillos",
    "operationZone": "Chorrillos",
    "originLat": -12.104800,
    "originLng": -76.963200,
    "destinationLat": -12.172100,
    "destinationLng": -77.016400,
    "travelDate": "2026-10-04",
    "earliestDeparture": "07:30:00",
    "latestDeparture": "08:30:00",
    "seatsRequested": 1
  }'
```

Generar propuestas (cada una incluye su `groupId`):

```bash
curl -X POST http://localhost:8081/api/v1/trip-requests/<UUID_DE_LA_SOLICITUD>/match-proposals \
  -H "Authorization: Bearer $PASSENGER_TOKEN"
```

Listar mis solicitudes de viaje:

```bash
curl http://localhost:8081/api/v1/trip-requests \
  -H "Authorization: Bearer $PASSENGER_TOKEN"
```

Aceptar o rechazar el grupo (US-21):

```bash
curl -X POST http://localhost:8081/api/v1/driver-availabilities/<UUID_DEL_GRUPO>/accept \
  -H "Authorization: Bearer $DRIVER_TOKEN"

curl -X POST http://localhost:8081/api/v1/driver-availabilities/<UUID_DEL_GRUPO>/reject \
  -H "Authorization: Bearer $DRIVER_TOKEN"
```

## Verificacion

```bash
./mvnw test
docker compose config --quiet
docker compose build
```

## Evidencia sugerida para TP1

- Captura de Swagger UI con los endpoints del microservicio.
- Captura de Postman con `201 Created` al registrar disponibilidad.
- Captura de Postman con `201 Created` al registrar solicitud de viaje.
- Captura de Postman con `200 OK` al generar propuestas de matching.
- Captura de Postman con `200 OK` al aceptar o rechazar el grupo (US-21).
- Captura de `GET /actuator/health` con estado `UP`.
