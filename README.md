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

---

# Smart Campus API - Conceptual Report

## Service Architecture & Setup

### Question
"In your report, explain the default lifecycle of a JAX-RS Resource class. Is a new instance instantiated for every incoming request, or does the runtime treat it as a singleton? Elaborate on how this architectural decision impacts the way you manage and synchronize your in-memory data structures (maps/lists) to prevent data loss or race conditions."

### Answer
By default, new JAX-RS resource classes will have a request-scoped lifecycle creating a new instance each time a new HTTP request is received and destroying it each time a response is given. Any shared data which consists of maps within lists should be stored outside these transient instances to avoid the loss of data and to maintain consistency. We do this through the application of static thread-safe collections such as ConcurrentHashMap, which avoids race conditions in cases where multiple requests are accessing the data and make concurrent changes.

### Question
"Why is the provision of 'Hypermedia' (links and navigation within responses) considered a hallmark of advanced RESTful design (HATEOAS)? How does this approach benefit client developers compared to static documentation?"

### Answer
HATEOAS enhances flexibility in APIs by giving dynamic navigation links in the response payloads instead of creating hard-coded URIs in the static documentation. This also decouples the server routing architecture with a client. In the event of any end-point changes in the future, the client just adheres to the new interposed paths as specified in the response of the API, and avoids having to update the client-side code to do so.

## Room Management

### Question
"When returning a list of rooms, what are the implications of returning only IDs versus returning the full room objects? Consider network bandwidth and client-side processing."

### Answer
Sending back IDs alone will radically cut down on the payload size and bandwidth required across the network but will require the client to transfer many subsequent requests to obtain the information about each room, which will increase latency. Full object returning will incur high initial payload and processing overhead but will do away with re-request. We use full objects since currently, the networks can handle small JSON objects without any issue, and it considerably accelerates the client rendering.

### Question
"Is the DELETE operation idempotent in your implementation? Provide a detailed justification by describing what happens if a client mistakenly sends the exact same DELETE request for a room multiple times."

### Answer
The DELETE operation is indeed semantically idempotent. With a misguided client sending more than one identical DELETE request on a room, the initial one will successfully delete it and respond with a 204 No Content. The next equal requests, will respond with 404 Not Found as the resource has already disappeared. The status codes vary but the overall resolution of the server is the same, with no unwanted side effect and is strictly idempotent.

## Sensor Operations & Linking

### Question
"We explicitly use the @Consumes(MediaType.APPLICATION_JSON) annotation on the POST method. Explain the technical consequences if a client attempts to send data in a different format, such as text/plain or application/xml. How does JAX-RS handle this mismatch?"

### Answer
The @Consumes annotation strictly enforces that the endpoint will just accept incoming data in the form of a JSON. In case a client tries to send XML or plain text, JAX-RS is safe enough to reject the request even before the internal business logic receives it. It is configured to automatically reply with a 415 Unsupported Media Type HTTP error and safeguard the system against improper or invalid parsing attempts.

### Question
"You implemented this filtering using @QueryParam. Contrast this with an alternative design where the type is part of the URL path (e.g., /api/v1/sensors/type/CO2). Why is the query parameter approach generally considered superior for filtering and searching collections?"

### Answer
The query parameters are designed more specifically to be used to filter or search collections because the query parameters serve as optional criteria on top of an existing resource dataset without any hierarchy changes. The wrong use of a path parameter suggests a statically nested entity. The query parameters are far more flexible and can be used with multiple three or more filters at the same time without disrupting the standardized structure of the REST routing.

## Deep Nesting with Sub-Resources

### Question
"Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path (e.g., sensors/{id}/readings/{rid}) in one massive controller class?"

### Answer
Sub-Resource Locator pattern enables the parent classes to delegate endpoints deeply nested with a specific child resource classes. This helps to keep the parent controller small and slim as it does not become cumbersome with multiple concerns. It enhances better code readability, eases unit testing, and of course maintains contextual ID of the parent resource to be used by the child without over use of parameter mapping.

## Advanced Error Handling

### Question
"Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?"

### Answer
An HTTP 404 error is an error that is given when the URL specifying the endpoint of a request cannot be located. To clients, on the other hand, a 422 Unprocessable Entity is suitable because the endpoint is correct and the syntax of the JSON content is valid but the server is unable to act because there are semantic errors in the request. This exactly explains a failed step of an integrity test on a payload in which a linked room is nonexistent in our case.

### Question
"From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?"

### Answer
The disclosure of raw Java stack traces exposes highly sensitive internal settings to the outward consumers. This information can be decomposed by attackers to learn the precise frameworks of use, specific versions of libraries, internal routing tables and even pathnames to files. Such a rich information trail gives malicious faction detailed blueprint to achieve targeted injection attacks or capitalize on known library vulnerabilities.

### Question
"Why is it advantageous to use JAX-RS filters for cross-cutting concerns like logging, rather than manually inserting Logger.info() statements inside every single resource method?"

### Answer
JAX-RS filters are used to centralize logging activities not in the business logics. Rather than relying on repetition of logging statements within each and every one of the API method endpoints, a single filter is used to intercept all the requests and responses. This greatly minimizes code redundancy, ensures a hundred percent consistency throughout the API and leaves the resource endpoints unpolluted.
