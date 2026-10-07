# ShareWay Trip Planning & Matching Service

Microservicio de ShareWay encargado de planificar solicitudes de viaje y generar propuestas de matching entre pasajeros y conductores disponibles.

## Stack

- Java 21 LTS
- Spring Boot 4.1.1
- Spring Web MVC
- Spring Data JPA
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

No debe manejar pagos, liquidaciones, validacion de abordaje, notificaciones en tiempo real ni gestion documental del conductor. Esos flujos pertenecen a otros bounded contexts.

## Ejecutar localmente

```bash
docker compose up --build
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

## Endpoints iniciales

Crear disponibilidad de conductor:

```bash
curl -X POST http://localhost:8081/api/v1/driver-availabilities \
  -H "Content-Type: application/json" \
  -d '{
    "driverId": 10,
    "operationZone": "Chorrillos",
    "availableDate": "2026-10-04",
    "startTime": "07:00:00",
    "endTime": "09:00:00",
    "availableSeats": 3
  }'
```

Crear solicitud de viaje:

```bash
curl -X POST http://localhost:8081/api/v1/trip-requests \
  -H "Content-Type: application/json" \
  -d '{
    "passengerId": 25,
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

Generar propuestas:

```bash
curl -X POST http://localhost:8081/api/v1/trip-requests/1/match-proposals
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
- Captura de `GET /actuator/health` con estado `UP`.
