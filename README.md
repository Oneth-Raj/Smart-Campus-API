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

---

# Conceptual Report

This report contains the answers to the 10 questions specified in the coursework guidelines.

## Question 1

**Question from coursework:** "In your report, explain the default lifecycle of a JAX-RS Resource class. Is a new instance instantiated for every incoming request, or does the runtime treat it as a singleton? Elaborate on how this architectural decision impacts the way you manage and synchronize your in-memory data structures (maps/lists) to prevent data loss or race conditions."

JAX-RS resource classes use a request-scoped lifecycle by default, meaning a new instance is created for every incoming HTTP request and destroyed once a response is sent. To prevent data loss and ensure consistency, any shared data structures like maps or lists must be stored outside these transient instances. We achieve this by using static, thread-safe collections like ConcurrentHashMap, which prevents race conditions when multiple requests read and modify the data simultaneously.

---

## Question 2

**Question from coursework:** "Why is the provision of 'Hypermedia' (links and navigation within responses) considered a hallmark of advanced RESTful design (HATEOAS)? How does this approach benefit client developers compared to static documentation?"

HATEOAS improves API flexibility by providing dynamic navigation links within the response payloads rather than forcing clients to hardcode URIs from static documentation. This decouples the client from the server's routing structure. If endpoints change in the future, the client simply follows the new linked paths provided in the API response, eliminating the need for client-side code updates.

---

## Question 3

**Question from coursework:** "When returning a list of rooms, what are the implications of returning only IDs versus returning the full room objects? Consider network bandwidth and client side processing."

Returning only IDs drastically reduces the payload size and network bandwidth, but forces the client to make numerous subsequent requests to fetch details for each room, increasing latency. Returning full objects involves a larger initial payload and processing hit, but eliminates the need for secondary requests. We opt for full objects because modern networks easily handle small JSON structures and it significantly speeds up client rendering.

---

## Question 4

**Question from coursework:** "Is the DELETE operation idempotent in your implementation? Provide a detailed justification by describing what happens if a client mistakenly sends the exact same DELETE request for a room multiple times."

Yes, the DELETE operation is semantically idempotent. If a client mistakenly sends multiple identical DELETE requests for a room, the first request will successfully delete it and return a 204 No Content. Any subsequent identical requests will return a 404 Not Found since the resource is already gone. While the status codes differ, the server's end state remains identically resolved without unintended side effects, strictly adhering to idempotency.

---

## Question 5

**Question from coursework:** "We explicitly use the @Consumes(MediaType.APPLICATION_JSON) annotation on the POST method. Explain the technical consequences if a client attempts to send data in a different format, such as text/plain or application/xml. How does JAX-RS handle this mismatch?"

The @Consumes annotation strictly enforces that the endpoint will only process incoming data formatted as JSON. If a client attempts to send XML or plain text, JAX-RS safely rejects the request before it even reaches the internal business logic. It automatically returns a 415 Unsupported Media Type HTTP error, protecting the system from malformed or unsupported parsing attempts.

---

## Question 6

**Question from coursework:** "You implemented this filtering using @QueryParam. Contrast this with an alternative design where the type is part of the URL path (e.g., /api/v1/sensors/type/CO2). Why is the query parameter approach generally considered superior for filtering and searching collections?"

Query parameters are specifically intended for filtering or searching collections, as they act as optional criteria overlaying an existing resource dataset without altering its hierarchy. Using a path parameter incorrectly implies a statically nested entity. Query parameters provide much more flexibility, allowing clients to combine multiple filters concurrently without breaking the standardized REST routing structure.

---

## Question 7

**Question from coursework:** "Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path (e.g., sensors/{id}/readings/{rid}) in one massive controller class?"

The Sub-Resource Locator pattern allows parent classes to delegate deeply nested endpoints to dedicated child resource classes. This prevents the parent controller from becoming overly complex and bloated, enforcing a clean separation of concerns. It improves code readability, simplifies unit testing, and naturally preserves the contextual ID of the parent resource for the child to use without excessive parameter mapping.

---

## Question 8

**Question from coursework:** "Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?"

HTTP 404 signifies that the requested endpoint URL itself cannot be found. Conversely, a 422 Unprocessable Entity accurately conveys that while the endpoint is correct and the JSON payload syntax is valid, the server cannot process the request due to semantic errors. In our context, this perfectly describes a payload failing a foreign key validation check where a linked room does not exist.

---

## Question 9

**Question from coursework:** "From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?"

Exposing raw Java stack traces reveals sensitive internal configurations to external consumers. Attackers can parse this data to discover the specific frameworks in use, exact library versions, internal routing maps, and file system paths. This extensive information footprint provides malicious actors with the precise blueprints needed to launch targeted injection attacks or exploit known library vulnerabilities.

---

## Question 10

**Question from coursework:** "Why is it advantageous to use JAX-RS filters for cross-cutting concerns like logging, rather than manually inserting Logger.info() statements inside every single resource method?"

JAX-RS filters centralize logging operations outside of the core business logic. Instead of repeating logging statements inside every single API method endpoint, a unified filter intercepts all requests and responses automatically. This significantly reduces code duplication, guarantees absolute uniformity across the entire API, and leaves the resource endpoints uncluttered.

