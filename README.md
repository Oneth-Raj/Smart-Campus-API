# Smart Campus Sensor and Room Management API

A robust, enterprise-grade RESTful API designed to manage and monitor smart campus infrastructure, including dynamic room allocation and comprehensive sensor networks. Built using JAX-RS (Jersey) and utilizing an embedded Grizzly HTTP server, this service provides a reliable, high-performance foundation for facility management.

## System Architecture and Technology Stack

- **Platform:** Java 11
- **Dependency Management:** Maven 3.8+
- **REST Framework:** JAX-RS (Jersey Implementation)
- **HTTP Server:** Embedded Grizzly Container
- **Data Serialization:** Jackson JSON Binding

## Project Architecture Overview

The system source code is structured to separate concerns and ensure maintainability:

- `src/main/java/com/smartcampus/Main.java`: System entry point initializing the embedded Grizzly server.
- `src/main/java/com/smartcampus/config/`: Configuration components including the application registry.
- `src/main/java/com/smartcampus/model/`: Domain structures (Room, Sensor, SensorReading).
- `src/main/java/com/smartcampus/resources/`: JAX-RS endpoint controllers and sub-resource locators.
- `src/main/java/com/smartcampus/exceptions/` & `mappers/`: Business logic exceptions and unified HTTP response mapping.
- `src/main/java/com/smartcampus/filters/`: Cross-cutting concerns such as request and response auditing.
- `src/main/java/com/smartcampus/store/`: Thread-safe, in-memory data structures ensuring safe concurrent execution.

## Build and Executable Instructions

Ensure your environment satisfies the criteria defined in the technology stack specifications before proceeding.

### Compilation

To resolve dependencies and compile the target bytecode, execute:

```bash
mvn clean package
```

### Server Initialization

Deploy the application locally via the Maven execution plugin:

```bash
mvn exec:java
```

Upon successful initialization, the service broadcast will indicate the host address binding:
`Smart Campus API started at http://0.0.0.0:8080/api/v1`

Standard execution can be terminated with `Enter` or via standard interrupt signals (`Ctrl+C`).

## RESTful API Endpoints Specification

### 1. System Discovery
- `GET /api/v1`: Returns application metadata, HATEOAS hyperlinks, and active resource mappings.

### 2. Room Management
- `GET /api/v1/rooms`: Retrieve a complete list of monitored rooms.
- `POST /api/v1/rooms`: Provision a new room within the campus data store.
- `GET /api/v1/rooms/{roomId}`: View explicit metadata for a specific localized room.
- `DELETE /api/v1/rooms/{roomId}`: Safely remove a room allocation (prevented if active sensors remain assigned).

### 3. Sensor Deployment and Control
- `GET /api/v1/sensors`: Retrieve global sensor deployments (supports optional `?type=` parameterized filtering).
- `POST /api/v1/sensors`: Deploy a new sensor and logically link it to an existing room constraint.
- `GET /api/v1/sensors/{sensorId}`: View historical configuration and current state values of a specific sensor unit.

### 4. Telemetry Data Logging (Sub-Resource)
- `GET /api/v1/sensors/{sensorId}/readings/`: Retrieve localized telemetry history for a specific sensor.
- `POST /api/v1/sensors/{sensorId}/readings/`: Append a newly recorded metric and synchronize the upstream `currentValue` parent pointer.

## CLI Testing Procedures

The following standard HTTP requests are provided for local curl execution testing:

**System Metadata Discovery**
```bash
curl -i http://localhost:8080/api/v1
```

**Room Initialization**
```bash
curl -i -X POST http://localhost:8080/api/v1/rooms \
    -H "Content-Type: application/json" \
    -d "{\"id\":\"CS-101\",\"name\":\"Computer Science Lab\",\"capacity\":50}"
```

**Sensor Provisioning (linked to localized Room)**
```bash
curl -i -X POST http://localhost:8080/api/v1/sensors \
    -H "Content-Type: application/json" \
    -d "{\"id\":\"CO2-001\",\"type\":\"CO2\",\"status\":\"ACTIVE\",\"currentValue\":410.5,\"roomId\":\"CS-101\"}"
```

**Metadata Filtering Engine**
```bash
curl -i "http://localhost:8080/api/v1/sensors?type=CO2"
```

**Telemetry Synchronization**
```bash
curl -i -X POST http://localhost:8080/api/v1/sensors/CO2-001/readings/ \
    -H "Content-Type: application/json" \
    -d "{\"value\":425.8}"
```

### Demonstrated Business Logic and Exception Handling Validations

1. Intercepting a constraint violation (attempting deletion with bonded resources):
```bash
curl -i -X DELETE http://localhost:8080/api/v1/rooms/CS-101
```

2. Intercepting unprocessable entities (provisioning a sensor over an absent local context):
```bash
curl -i -X POST http://localhost:8080/api/v1/sensors \
    -H "Content-Type: application/json" \
    -d "{\"id\":\"TEMP-999\",\"type\":\"Temperature\",\"status\":\"ACTIVE\",\"roomId\":\"NO-SUCH-ROOM\"}"
```

3. Intercepting forbidden operations (updating sensors flagged under maintenance states):
```bash
curl -i -X POST http://localhost:8080/api/v1/sensors \
    -H "Content-Type: application/json" \
    -d "{\"id\":\"TEMP-001\",\"type\":\"Temperature\",\"status\":\"MAINTENANCE\",\"roomId\":\"CS-101\"}"

curl -i -X POST http://localhost:8080/api/v1/sensors/TEMP-001/readings/ \
    -H "Content-Type: application/json" \
    -d "{\"value\":20.2}"
```

## Security and Exception Strategies

- **Strict Payload Mapping:** Custom exception mappers intercept common operational failures and emit deterministic JSON, suppressing legacy HTML trace outputs.
- **Fail-Safe Mechanism:** Implementations are safeguarded via `GlobalExceptionMapper` yielding non-descriptive HTTP 500 status blocks for unhandled execution failures.
- **Data Integrity Assurance:** Concurrency-safe architectures utilize `ConcurrentHashMap` mechanisms preventing synchronization failure during scalable load simulations.

Refer to `TECHNICAL_REPORT.md` and `STEP_BY_STEP_GUIDE.md` for extended system design rationale and technical justifications.
