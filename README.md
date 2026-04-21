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

**Answer:**

By default, JAX-RS treats Resource classes as **request-scoped**. The runtime creates a new instance of the resource class for each incoming HTTP request. This design decision has profound implications for application design:

**Request-Scoped Behavior:**
```
Request 1 -> new DiscoveryResource() -> process -> destroy
Request 2 -> new DiscoveryResource() -> process -> destroy
Request 3 -> new DiscoveryResource() -> process -> destroy
```

**Implications:**

1. **Thread Safety of Resource Methods**: Each request thread gets its own resource instance. Instance variables in the resource class do not require synchronization because no two threads access the same instance variable. This is a major architectural advantage.

2. **Shared State Problem**: However, the data stores themselves (ROOMS, SENSORS, READINGS maps) are shared across all request instances. Multiple threads from different requests will simultaneously access these shared data structures.

```java
// InMemoryStore.java
public static final Map<String, Room> ROOMS = new ConcurrentHashMap<>();
```

All requests share this single static map. Without proper synchronization, race conditions emerge:

```
Thread A: Read ROOMS.get("LIB-301")
Thread B: Write ROOMS.put("LIB-301", newRoom)
Thread A: ROOMS.put("LIB-301", oldRoom) // Overwrites Thread B's data!
```

**Our Synchronization Strategy:**

We use thread-safe collection classes:

```java
// ConcurrentHashMap: thread-safe
Map<String, Room> ROOMS = new ConcurrentHashMap<>();

// CopyOnWriteArrayList: thread-safe
List<SensorReading> READINGS = new CopyOnWriteArrayList<>();
```

**ConcurrentHashMap Guarantees:**
- `putIfAbsent(key, value)`: Atomic check-and-insert. If the key already exists, returns the existing value without inserting.
- `get(key)`: Safe read, even while other threads write
- Segment-based locking: Not a single lock for the entire map, but many fine-grained locks (16+ buckets)

**CopyOnWriteArrayList Guarantees:**
- Reads are non-blocking and highly efficient
- Writes create a copy of the entire list (expensive but rare in sensor readings)
- Perfect for scenarios with many reads and few writes

**Example - Atomic Sensor Creation:**
```java
Sensor existing = InMemoryStore.SENSORS.putIfAbsent(newSensor.getId(), newSensor);
if (existing != null) {
    throw new WebApplicationException(...);  // Duplicate ID
}
```

The `putIfAbsent` operation is atomic - no window exists where two threads can both see a key as missing and create duplicates.

**Concurrency Test Scenario:**
```
Thread 1 (Req A): Create sensor "CO2-001"
Thread 2 (Req B): Create sensor "CO2-001" simultaneously

Without ConcurrentHashMap:
- Both threads read: key not found
- Both threads write: last write wins (data loss!)

With ConcurrentHashMap:
- Thread 1's putIfAbsent succeeds, returns null
- Thread 2's putIfAbsent fails, returns Thread 1's sensor object
- Thread 2 throws 409 Conflict (semantically correct)
```

**Design Decision Validation:**
The request-scoped resource design, combined with concurrent data structures, is the standard pattern in enterprise Java applications (Spring, Quarkus, etc.). It provides:
- Excellent multi-request throughput
- Natural thread isolation for business logic
- Clear separation between per-request and shared state

---

## Question 2

**Question from coursework:** "Why is the provision of 'Hypermedia' (links and navigation within responses) considered a hallmark of advanced RESTful design (HATEOAS)? How does this approach benefit client developers compared to static documentation?"

**Answer:**

HATEOAS stands for "Hypermedia As The Engine Of Application State." It represents a fundamental principle in REST architecture that elevates APIs from mere data transfer mechanisms to self-describing systems.

**Traditional Approach (Anti-pattern):**
```
Client developer reads API documentation:
"Rooms endpoint is at /api/v1/rooms"
Client hardcodes: new URL("http://server/api/v1/rooms")
```

**HATEOAS Approach:**
```
Client fetches discovery endpoint
Client parses response JSON
Client dynamically constructs requests using links from response
```

**Concrete Benefits:**

1. **Server-Driven Discovery**
   - Clients don't need to know endpoint URLs in advance
   - The API self-documents itself
   - New developers can discover all available endpoints by following links

2. **Resilience to API Changes**
   - Scenario: Server administrator moves the rooms endpoint to `/api/v1/campus/rooms`
   - Static documentation client: Breaks (hardcoded URL)
   - HATEOAS-aware client: Works (follows links from discovery response)

3. **Semantic Clarity**
   - Response `{"rooms": "/api/v1/rooms"}` tells clients: "This is the rooms resource"
   - Better than consulting documentation: "Endpoint 2b on page 4"

4. **Multi-Version Support**
   - API version 1: `"rooms": "/api/v1/rooms"`
   - API version 2: `"rooms": "/api/v2/rooms"`
   - Clients dynamically adapt to the returned URLs

**Real-World Example - GitHub API:**
```json
{
  "repositories_url": "https://api.github.com/user/repos",
  "gists_url": "https://api.github.com/user/gists{/gist_id}",
  "starred_url": "https://api.github.com/user/starred{/owner}{/repo}"
}
```
GitHub's API returns URLs in every response, enabling clients to follow links rather than hardcode paths.

**Implementation in Smart Campus:**
The discovery endpoint returns:
- **name**: API identity (supports multi-tenant scenarios)
- **version**: Current API version
- **contact**: Support information for integration issues
- **resources**: Hypermedia links to available resources

This design enables:
- Client SDK auto-discovery (build URL from response)
- Automated test generation (follow links)
- API monitoring (crawl links to validate)

---

## Question 3

**Question from coursework:** "When returning a list of rooms, what are the implications of returning only IDs versus returning the full room objects? Consider network bandwidth and client side processing."

**Answer:**

| Aspect | IDs Only | Full Objects |
|--------|----------|--------------|
| **Bandwidth** | Minimal | 10x larger |
| **Payload Example** | `[1, 2, 3]` | `[{id:1,name:"...",cap:120,...}]` |
| **Client Round-trips** | N additional requests | 0 additional requests |
| **Caching** | Can cache IDs easily | Can cache objects, faster |
| **Use Case** | Dropdowns, autocomplete | Detail views, dashboards |

**Smart Campus Decision: Full Objects**

We return full room objects because:

1. **Bandwidth Calculation:**
   - A 5-field room object ≈ 200-300 bytes in JSON
   - Typical campus: 50 rooms = 15KB
   - Modern networks: negligible cost (mobile networks: ~10ms delay)

2. **Round-trip Elimination:**
   - Alternative: Return IDs, client fetches each → 50 requests
   - Each request: ~100ms overhead = 5 seconds total
   - Full objects: 1 request = ~100ms
   - Time saving: 4.9 seconds

3. **REST Principle:**
   - A room list endpoint should return rooms, not references
   - Clients consuming list endpoint expect complete data

4. **Practical Example:**
   - UI showing "Rooms" dropdown: Can immediately populate without 50 secondary requests
   - CLI tool listing rooms: Can immediately display details

**Optimization Opportunity (Advanced):**
For extremely large datasets (10,000+ rooms), consider:
- Pagination: `GET /rooms?page=1&size=100` → returns 100 rooms per request
- Sparse fieldsets: `GET /rooms?fields=id,name` → returns only needed fields
- Not implemented here to keep coursework scope manageable

#### POST /api/v1/rooms - Create Room

```java
@POST
@Consumes(MediaType.APPLICATION_JSON)
public Response createRoom(Room room, @Context UriInfo uriInfo) {
    if (room == null || isBlank(room.getId()) || isBlank(room.getName())) {
        throw new BadRequestException("Room id and name are required.");
    }

    Room newRoom = new Room(room.getId(), room.getName(), room.getCapacity(), room.getSensorIds());
    Room existing = InMemoryStore.ROOMS.putIfAbsent(newRoom.getId(), newRoom);
    if (existing != null) {
        throw new WebApplicationException(
            Response.status(Response.Status.CONFLICT)
                .entity(Map.of("message", "A room with this id already exists."))
                .type(MediaType.APPLICATION_JSON)
                .build());
    }

    URI location = uriInfo.getAbsolutePathBuilder().path(newRoom.getId()).build();
    return Response.created(location).entity(newRoom).build();
}
```

**Request:**
```bash
POST /api/v1/rooms HTTP/1.1
Content-Type: application/json

{"id":"LIB-301","name":"Library Quiet Study","capacity":120}
```

**Response (201 Created):**
```
HTTP/1.1 201 Created
Location: http://localhost:8080/api/v1/rooms/LIB-301
Content-Type: application/json
Content-Length: 75

{"id":"LIB-301","name":"Library Quiet Study","capacity":120,"sensorIds":[]}
```

**Design Details:**

1. **Validation**: `id` and `name` required (capacity optional, defaults to 0)
2. **Duplicate Prevention**: `putIfAbsent()` atomically checks and inserts
3. **Location Header**: Clients can immediately fetch the created resource at the returned URI
4. **201 Status**: HTTP specification requires 201 Created for successful resource creation

#### GET /api/v1/rooms/{roomId} - Fetch Single Room

```java
@GET
@Path("/{roomId}")
public Room getRoomById(@PathParam("roomId") String roomId) {
    Room room = InMemoryStore.ROOMS.get(roomId);
    if (room == null) {
        throw new ResourceNotFoundException("Room not found: " + roomId);
    }
    return room;
}
```

**Request:**
```
GET /api/v1/rooms/LIB-301 HTTP/1.1
```

**Response (200 OK):**
```json
{
  "id": "LIB-301",
  "name": "Library Quiet Study",
  "capacity": 120,
  "sensorIds": ["CO2-001"]
}
```

**Response (404 Not Found):**
```json
{
  "timestamp": 1234567890,
  "status": 404,
  "error": "Not Found",
  "message": "Room not found: UNKNOWN",
  "path": "/api/v1/rooms/UNKNOWN"
}
```

---

## Question 4

**Question from coursework:** "Is the DELETE operation idempotent in your implementation? Provide a detailed explanation."

**Answer:**

Idempotency is a fundamental REST principle: "calling an API multiple times with identical parameters produces the same result as calling it once."

**Standard Definition:**
```
DELETE /resource/id
First call:  204 No Content (deleted)
Second call: 204 No Content (no longer exists, but operation semantically the same)
```

**Our Implementation - Strictly Speaking: NOT Fully Idempotent**

```
DELETE /rooms/LIB-301
First call:  204 No Content (deleted successfully)
Second call: 404 Not Found (room no longer exists)
```

**Semantic vs. HTTP Idempotency:**

1. **Semantic Idempotency** (Intent-based): ✓ YES
   - Intent: "Delete this room"
   - First call: Deletes it
   - Second call: Also accomplishes the intent (room is deleted)
   - Result is identical

2. **Strict HTTP Idempotency** (Status code-based): ✗ NO
   - Different status codes (204 vs 404)
   - Technically not idempotent by strict definition

**Our Design Choice: Semantically Correct Over Strictly Idempotent**

We chose to return 404 on the second call because:

1. **Semantic Clarity**: 404 tells the client "this resource doesn't exist" (truthful)
2. **Error Detection**: Client can detect "operation already completed" vs "resource never existed"
3. **RESTful Correctness**: Resources should not exist after deletion; returning 404 reflects reality

**Alternative Approach (Strictly Idempotent):**
```java
@DELETE
@Path("/{roomId}")
public Response deleteRoom(@PathParam("roomId") String roomId) {
    Room room = InMemoryStore.ROOMS.get(roomId);
    
    if (room != null && room.getSensorIds() != null && !room.getSensorIds().isEmpty()) {
        throw new RoomNotEmptyException(...);
    }

    InMemoryStore.ROOMS.remove(roomId);  // Safe: no-op if already deleted
    return Response.noContent().build();  // Always return 204
}
```

**Trade-off Analysis:**

| Approach | Pros | Cons |
|----------|------|------|
| Our approach (404) | Semantically correct, error detection | Strictly not idempotent |
| Alternative (204) | Strictly idempotent | Hides errors, less informative |

**Industry Standard**: Most REST APIs follow our approach:
- AWS S3: DELETE non-existent object → 204
- GitHub API: DELETE non-existent repo → 404
- Google Cloud: DELETE non-existent resource → 404

We follow established REST best practices over strict idempotency definitions.

---

## Question 5

**Question from coursework:** "Clearly explain the technical consequences (415 Unsupported Media Type) of content-type mismatches in JAX-RS."

**Answer:**

JAX-RS uses the `@Consumes` annotation to specify expected media types:

```java
@POST
@Consumes(MediaType.APPLICATION_JSON)
public Response createSensor(Sensor sensor) { ... }
```

**Scenario 1: Correct Content-Type**
```bash
POST /api/v1/sensors HTTP/1.1
Content-Type: application/json
{"id":"CO2-001","type":"CO2",...}
```
✓ Jackson deserializes JSON → Sensor POJO
✓ Method executes

**Scenario 2: Wrong Content-Type**
```bash
POST /api/v1/sensors HTTP/1.1
Content-Type: application/xml
<Sensor><id>CO2-001</id>...</Sensor>
```

JAX-RS response:
```
HTTP/1.1 415 Unsupported Media Type
Content-Type: application/json

{"timestamp":1234567890,"status":415,"error":"Unsupported Media Type","message":"...","path":"/api/v1/sensors"}
```

**Technical Consequence:**
- Content-Type mismatch → JAX-RS rejects before method invocation
- No deserialization attempted → No exception inside method
- Prevents malformed data from reaching business logic
- Client must correct Content-Type header and retry

**Correct Content-Type Examples:**
- JSON: `application/json`
- XML: `application/xml`
- Form: `application/x-www-form-urlencoded`

Note: Our implementation handles 415 automatically via the `@Consumes` annotation. The GlobalExceptionMapper catches the underlying exception and returns a clean 415 error response.

#### GET /api/v1/sensors - Retrieve with Optional Filtering

```java
@GET
public List<Sensor> getAllSensors(@QueryParam("type") String type) {
    List<Sensor> all = new ArrayList<>(InMemoryStore.SENSORS.values());
    if (type == null || type.trim().isEmpty()) {
        return all;
    }

    String expectedType = type.trim().toLowerCase(Locale.ROOT);
    return all.stream()
        .filter(sensor -> sensor.getType() != null)
        .filter(sensor -> sensor.getType().toLowerCase(Locale.ROOT).equals(expectedType))
        .collect(Collectors.toList());
}
```

**Request: Unfiltered**
```
GET /api/v1/sensors HTTP/1.1
```

**Response:**
```json
[
  {"id":"CO2-001","type":"CO2",...},
  {"id":"TEMP-001","type":"Temperature",...},
  {"id":"OCC-001","type":"Occupancy",...}
]
```

**Request: Filtered by Type**
```
GET /api/v1/sensors?type=CO2 HTTP/1.1
```

**Response:**
```json
[
  {"id":"CO2-001","type":"CO2",...}
]
```

#### GET /api/v1/sensors/{sensorId} - Fetch Single Sensor

```java
@GET
@Path("/{sensorId}")
public Sensor getSensorById(@PathParam("sensorId") String sensorId) {
    Sensor sensor = InMemoryStore.SENSORS.get(sensorId);
    if (sensor == null) {
        throw new ResourceNotFoundException("Sensor not found: " + sensorId);
    }
    return sensor;
}
```

---

## Question 6

**Question from coursework:** "Insightful contrast between QueryParams and PathParams, justifying why query strings are superior for collection filtering."

**Answer:**

| Aspect | Path Parameters | Query Parameters |
|--------|---|---|
| **Syntax** | `/rooms/{roomId}` | `/rooms?type=CO2&status=ACTIVE` |
| **Purpose** | Identify single resource | Modify collection behavior |
| **Semantics** | "Get THIS room" | "Get rooms WITH these criteria" |
| **Caching** | Each path = separate cache entry | Query affects cache key |
| **Optional** | Required | Optional |
| **Multiple Values** | Awkward (`/values/a/b/c`) | Natural (`?colors=red&colors=blue`) |
| **HTTP Idempotency** | GET always safe | GET always safe |

**Design Decision:**

**Incorrect Design (Using Path Parameters):**
```
GET /api/v1/sensors/type/CO2
GET /api/v1/sensors/status/ACTIVE
GET /api/v1/sensors/type/CO2/status/ACTIVE
```

Problems:
- Explosive URL combinations (3 filters = 8 paths)
- Unclear semantics (is "type/CO2" a path segment or filter?)
- Difficult to extend (adding 4th filter creates 16 paths)

**Correct Design (Using Query Parameters):**
```
GET /api/v1/sensors?type=CO2
GET /api/v1/sensors?status=ACTIVE
GET /api/v1/sensors?type=CO2&status=ACTIVE
GET /api/v1/sensors?type=CO2&status=ACTIVE&roomId=LIB-301
```

Advantages:
- Single URL pattern for all filters
- Clear semantics (query string modifies collection)
- Extensible (add new filters without new paths)
- Follows HTTP conventions
- Caching-aware

**REST Architectural Principle:**
Path parameters identify resources. Query parameters filter/modify collections.
- `/rooms/LIB-301` - Identify room with ID LIB-301
- `/rooms?capacity>100` - Filter rooms by capacity
- `/sensors/CO2-001/readings?startDate=2025-01-01` - Filter sensor readings

**Implementation in Smart Campus:**
```java
@GET
public List<Sensor> getAllSensors(@QueryParam("type") String type) {
    // type is optional; if null, return all
}
```

This design scales to multiple filters:
```java
@GET
public List<Sensor> getAllSensors(
    @QueryParam("type") String type,
    @QueryParam("status") String status,
    @QueryParam("roomId") String roomId) {
    
    return sensors.stream()
        .filter(s -> type == null || s.getType().equals(type))
        .filter(s -> status == null || s.getStatus().equals(status))
        .filter(s -> roomId == null || s.getRoomId().equals(roomId))
        .collect(Collectors.toList());
}
```

---

## Question 7

**Question from coursework:** "Detailed discussion on managing complexity and delegation in large APIs."

**Answer:**

The sub-resource locator pattern elegantly manages API complexity through delegation:

**Complexity Management Benefits:**

1. **Separation of Concerns**
   ```
   SensorResource: Manages /sensors endpoint
   SensorReadingResource: Manages /sensors/{id}/readings endpoint
   
   Each class has single responsibility (SRP)
   ```

2. **Reduced Cognitive Load**
   - SensorResource doesn't contain reading logic
   - Developers looking at SensorResource see ~100 lines, not 200+
   - Reading-related code isolated in its own class

3. **Independent Scaling**
   - SensorReadingResource can grow with new endpoints
   - GET /readings/{readingId} - fetch single reading
   - DELETE /readings/{readingId} - delete reading
   - Without touching SensorResource

4. **Testability**
   ```java
   // Unit test SensorReadingResource in isolation
   SensorReadingResource resource = new SensorReadingResource("CO2-001");
   List<SensorReading> readings = resource.getReadings();
   
   // No need to instantiate SensorResource
   ```

5. **Maintainability**
   - Bug in reading logic? Modify SensorReadingResource only
   - Bug in sensor logic? Modify SensorResource only
   - Reduced risk of side effects

**Architectural Pattern Comparison:**

**Monolithic Approach (Anti-pattern):**
```java
@Path("/sensors")
public class MonolithicResource {
    @GET
    public List<Sensor> getAllSensors() { ... }
    
    @POST
    public Response createSensor(Sensor sensor) { ... }
    
    @GET
    @Path("/{sensorId}")
    public Sensor getSensorById(String sensorId) { ... }
    
    @GET
    @Path("/{sensorId}/readings")
    public List<SensorReading> getReadings(String sensorId) { ... }
    
    @POST
    @Path("/{sensorId}/readings")
    public Response addReading(String sensorId, SensorReading reading) { ... }
    
    @GET
    @Path("/{sensorId}/readings/{readingId}")
    public SensorReading getReading(String sensorId, String readingId) { ... }
    
    // Class grows to 500+ lines, becomes unmaintainable
}
```

**Delegated Approach (Our Design):**
```java
@Path("/sensors")
public class SensorResource {
    @GET
    public List<Sensor> getAllSensors() { ... }
    
    @POST
    public Response createSensor(Sensor sensor) { ... }
    
    @GET
    @Path("/{sensorId}")
    public Sensor getSensorById(String sensorId) { ... }
    
    @Path("/{sensorId}/readings")
    public SensorReadingResource sensorReadings(String sensorId) { ... }
}

@Produces(MediaType.APPLICATION_JSON)
public class SensorReadingResource {
    @GET
    @Path("/")
    public List<SensorReading> getReadings() { ... }
    
    @POST
    @Path("/")
    public Response addReading(SensorReading reading) { ... }
    
    @GET
    @Path("/{readingId}")
    public SensorReading getReading(String readingId) { ... }
}
```

Benefits:
- Each class ~50-100 lines (digestible)
- Clear hierarchy: Sensors contain Readings
- Matches domain model structure
- Scales to 3+ levels (Campus > Building > Room > Sensor > Reading)

---

## Question 8

**Question from coursework:** "How did you ensure parent-child consistency?"

**Answer:**

Parent-child consistency (parent Sensor's `currentValue` always reflects the latest reading) is maintained through:

1. **Atomic Side Effect:**
   ```java
   SensorReading newReading = new SensorReading(readingId, timestamp, payload.getValue());
   InMemoryStore.getOrCreateReadings(sensorId).add(newReading);  // Add to history
   sensor.setCurrentValue(newReading.getValue());                // Update parent
   ```

2. **No Intermediate State:**
   ```
   // What we do (consistent):
   Add reading → Update parent (both happen or neither)
   
   // What we avoid (inconsistent):
   Add reading → (failure) → Parent not updated
   ```

3. **Request-Scoped Resource:**
   - Each POST request gets new SensorReadingResource instance
   - The sensor reference is obtained once
   - All modifications happen on same object
   - JVM memory guarantees atomicity for simple assignment

4. **No Concurrent Modification:**
   ```java
   // This is safe because:
   // 1. We read sensor once: Sensor sensor = ensureSensorExists()
   // 2. Add reading: InMemoryStore.getOrCreateReadings(sensorId).add(newReading)
   // 3. Update sensor: sensor.setCurrentValue(newReading.getValue())
   // All within same method, no interleaving
   ```

**Consistency Scenario:**

```
Thread A: POST reading value=410.5 for CO2-001
Thread B: POST reading value=412.0 for CO2-001
Thread C: GET CO2-001

Scenario 1 (Our approach - Consistent):
T1: Thread A reads Sensor, sees currentValue=400
T2: Thread A adds reading to history
T3: Thread A sets currentValue=410.5
T4: Thread B reads Sensor, sees currentValue=410.5
T5: Thread B adds reading to history
T6: Thread B sets currentValue=412.0
T7: Thread C reads Sensor, sees currentValue=412.0 (matches latest reading)
    → CONSISTENT

Scenario 2 (Without side effect - Inconsistent):
T1: Thread A adds reading value=410.5
T2: Thread B adds reading value=412.0
T3: Thread C reads Sensor, sees currentValue=400 (hasn't been updated)
    → INCONSISTENT (parent doesn't reflect latest child)
```

**Thread Safety of Side Effect:**
```java
sensor.setCurrentValue(newReading.getValue());
```

This single assignment is atomic at the JVM level. Even with concurrent threads:
```
Thread A: sensor.currentValue = 410.5 ← atomic write
Thread B: sensor.currentValue = 412.0 ← atomic write
Thread C: read sensor.currentValue    ← atomic read

Result: Thread C always sees the most recent write (either 410.5 or 412.0)
```

**Constraint: Maintenance Mode Validation**

```java
if ("MAINTENANCE".equals(sensor.getStatus().toUpperCase(Locale.ROOT))) {
    throw new SensorUnavailableException(...);
}
```

Ensures readings can only be added to ACTIVE or OFFLINE sensors, not MAINTENANCE.

---

## Question 9

**Question from coursework:** "Analysis of cybersecurity risks, detailing how stack traces expose internal paths, library versions, and logic flaws."

**Answer:**

Stack trace exposure is a critical security vulnerability:

**What Attackers Learn from Stack Traces:**

1. **Library Versions:**
   ```
   at com.fasterxml.jackson.core.JsonFactory.createParser(JsonFactory.java:531)
                                       ↓
   Attacker: "App uses Jackson 2.10.0 from 2020-01-01"
   Check CVE database for known vulnerabilities in 2.10.0
   ```

2. **Internal Architecture:**
   ```
   at com.smartcampus.store.InMemoryStore.getOrCreateReadings(InMemoryStore.java:42)
   at com.smartcampus.resources.SensorReadingResource.addReading(SensorReadingResource.java:65)
                                ↓
   Attacker: "Sensors use in-memory store, not database"
   Attacker: "Can design attacks targeting memory (DOS via huge readings list)"
   ```

3. **Class Hierarchy:**
   ```
   Caused by: com.smartcampus.exceptions.ResourceNotFoundException: Sensor not found
   at com.smartcampus.resources.SensorResource.getSensorById(SensorResource.java:52)
                      ↓
   Attacker: "Class is named 'SensorResource', package is 'com.smartcampus.resources'"
   Attacker: Can predict other class names: RoomResource, DiscoveryResource
   ```

4. **Possible Logic Flaws:**
   ```
   Exception in thread "pool-1-thread-3" java.lang.NullPointerException
   at com.smartcampus.mappers.ErrorResponse.<init>(ErrorResponse.java:25)
                                                     ↓
   Attacker: "Constructor at line 25 throws NPE"
   Attacker: "View source code on GitHub to see line 25"
   Attacker: "Find the bug developers haven't fixed yet"
   ```

**Our Implementation - Secure:**

```java
ErrorResponse error = new ErrorResponse(
    Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
    "Internal Server Error",
    "An unexpected error occurred. Please contact support.",  // Generic message
    uriInfo.getPath());
```

Benefits:
- Logs actual exception server-side: `LOGGER.log(Level.SEVERE, "Unhandled exception", exception)`
- Developers can debug from logs
- Clients see generic, non-informative response
- Stack trace never reaches clients

**Real-World Attack Example:**

Attacker scans your API and sees:
```json
{
  "message": "java.sql.SQLException: ORA-00904: invalid column name SENR_ID",
  "stackTrace": ["oracle.jdbc...", "com.smartcampus.store..."]
}
```

Attacker deduces:
- Database is Oracle (licensing: expensive to target)
- Table has column issues (might exploit SQL injection)
- Version info leaks across multiple responses

**Best Practice - Our Approach:**
```json
{
  "message": "An unexpected error occurred. Please contact support.",
  "path": "/api/v1/sensors/CO2-001/readings",
  "requestId": "abc-123-def"  // For support correlation
}
```

Support team can correlate requestId with server logs:
```
[SEVERE] RequestId: abc-123-def | java.sql.SQLException: ORA-00904: invalid column name SENR_ID
at oracle.jdbc.driver.DatabaseError.throwSqlException(DatabaseError.java:123)
...
```

---

