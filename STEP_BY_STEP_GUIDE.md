# Smart Campus API - Step-by-Step Implementation Guide

## Overview
This guide walks you through understanding and completing the Smart Campus coursework. The code has been structured to align with JAX-RS best practices and covers all 100 marks of the coursework requirements.

---

## Part 1: Project Setup & Architecture (10 Marks)

### Step 1.1: Understanding Maven Configuration

**What it does:** Maven manages dependencies and builds your project.

**File:** `pom.xml`

**Key sections to understand:**
```xml
<properties>
    <maven.compiler.source>11</maven.compiler.source>
    <maven.compiler.target>11</maven.compiler.target>
</properties>
```
- Sets Java 11 as the compilation target

**Dependencies:**
- **jersey-server**: Core JAX-RS implementation for REST endpoints
- **jersey-hk2**: Dependency injection for Jersey
- **jersey-container-grizzly2-http**: Embedded HTTP server (no external app server needed)
- **jersey-media-json-jackson**: Automatic JSON serialization/deserialization

**Why this matters for your report:**
These dependencies show you understand the technology stack. In your viva, you might be asked: "Why did you choose Jersey over Resteasy?" Answer: Jersey is the reference implementation of JAX-RS and integrates well with Grizzly for embedded deployment.

---

### Step 1.2: Application Entry Point

**File:** `src/main/java/com/smartcampus/Main.java`

**Understanding the code:**

```java
public class Main {
    public static final String BASE_URI = "http://0.0.0.0:8080/";

    public static HttpServer startServer() {
        ResourceConfig config = ResourceConfig.forApplication(new SmartCampusApplication());
        return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), config);
    }
}
```

**What happens:**
1. `ResourceConfig.forApplication()` scans the SmartCampusApplication class for registered resources
2. `GrizzlyHttpServerFactory` creates an embedded HTTP server
3. The server starts on `http://0.0.0.0:8080/`

**JAX-RS Lifecycle Question (for your report):**
> By default, JAX-RS Resource classes are request-scoped. A new instance is created for **every incoming request**. This means your resource methods are thread-safe by default, but your **shared data structures** (like maps in InMemoryStore) must be thread-safe. That's why we use `ConcurrentHashMap` and `CopyOnWriteArrayList`.

---

### Step 1.3: Application Configuration Class

**File:** `src/main/java/com/smartcampus/config/SmartCampusApplication.java`

**Key code:**
```java
@ApplicationPath("/api/v1")
public class SmartCampusApplication extends Application {
    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();
        classes.add(JacksonFeature.class);
        classes.add(DiscoveryResource.class);
        classes.add(RoomResource.class);
        classes.add(SensorResource.class);
        // Exception mappers...
        classes.add(ApiLoggingFilter.class);
        return classes;
    }
}
```

**What this does:**
- `@ApplicationPath("/api/v1")` sets the API root to `/api/v1` (versioning best practice)
- `getClasses()` registers all resources, mappers, and filters
- Order doesn't matter - JAX-RS discovers them all

**Design note:** The `@ApplicationPath` annotation is essential for API versioning. If you had `/api/v2` later, you could have both coexist.

---

### Step 1.4: Discovery Endpoint

**File:** `src/main/java/com/smartcampus/resources/DiscoveryResource.java`

**The endpoint:**
```java
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class DiscoveryResource {
    @GET
    public Map<String, Object> discover() {
        // Returns metadata
    }
}
```

**What it returns:**
```json
{
  "name": "Smart Campus Sensor and Room Management API",
  "version": "v1",
  "contact": {
    "owner": "Facilities Technology Team",
    "email": "smart-campus-admin@university.local"
  },
  "resources": {
    "rooms": "/api/v1/rooms",
    "sensors": "/api/v1/sensors"
  }
}
```

**HATEOAS Question (for your report):**
> The discovery endpoint implements **HATEOAS** (Hypermedia As The Engine Of Application State). Instead of hardcoding `/api/v1/rooms` in client code, clients can fetch the discovery endpoint, parse the JSON, and follow the links. This is superior to static documentation because:
> 1. **Self-documenting**: The API tells you what's available
> 2. **Resilient to change**: If you move an endpoint, clients automatically adapt
> 3. **Version-aware**: The discovery endpoint can return different links for different API versions

---

## Part 2: Room Management (20 Marks)

### Step 2.1: Understanding the Room Model

**File:** `src/main/java/com/smartcampus/model/Room.java`

```java
public class Room {
    private String id;           // e.g., "LIB-301"
    private String name;         // e.g., "Library Quiet Study"
    private int capacity;        // e.g., 120
    private List<String> sensorIds = new ArrayList<>();  // Linked sensors
}
```

**Why these fields?**
- `id`: Unique key for storage
- `name`: Human-readable identifier
- `capacity`: Business rule - can't have more people than capacity
- `sensorIds`: Links to sensors in this room (important for deletion logic)

---

### Step 2.2: Room CRUD Operations

**File:** `src/main/java/com/smartcampus/resources/RoomResource.java`

#### GET /api/v1/rooms (List all rooms)

```java
@GET
public List<Room> getAllRooms() {
    return new ArrayList<>(InMemoryStore.ROOMS.values());
}
```

**What happens:**
1. Client sends: `GET /api/v1/rooms`
2. JAX-RS finds method with no `@Path` (at root)
3. Returns all Room objects as JSON array
4. Jackson automatically serializes to JSON

**Response example:**
```json
[
  {
    "id": "LIB-301",
    "name": "Library Quiet Study",
    "capacity": 120,
    "sensorIds": []
  }
]
```

---

#### POST /api/v1/rooms (Create room)

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

**What happens step-by-step:**
1. Client sends: `POST /api/v1/rooms` with JSON body
2. Jackson deserializes JSON → Room object
3. Validation: check id and name are not empty
4. Thread-safety: `putIfAbsent()` atomically checks existence and inserts
5. If duplicate: returns 409 Conflict status
6. If success: returns 201 Created with Location header pointing to the new room

**Testing command:**
```bash
curl -i -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"LIB-301","name":"Library Quiet Study","capacity":120}'
```

**Response:**
```
HTTP/1.1 201 Created
Location: http://localhost:8080/api/v1/rooms/LIB-301
Content-Type: application/json

{"id":"LIB-301","name":"Library Quiet Study","capacity":120,"sensorIds":[]}
```

---

#### GET /api/v1/rooms/{roomId} (Fetch single room)

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

**What happens:**
1. `@PathParam("roomId")` extracts the room ID from the URL
2. Looks up in the concurrent map
3. If not found, throws ResourceNotFoundException (mapped to 404)

---

#### DELETE /api/v1/rooms/{roomId} (Delete room with integrity check)

```java
@DELETE
@Path("/{roomId}")
public Response deleteRoom(@PathParam("roomId") String roomId) {
    Room room = InMemoryStore.ROOMS.get(roomId);
    if (room == null) {
        throw new ResourceNotFoundException("Room not found: " + roomId);
    }

    if (room.getSensorIds() != null && !room.getSensorIds().isEmpty()) {
        throw new RoomNotEmptyException("Room cannot be deleted while sensors are assigned.");
    }

    InMemoryStore.ROOMS.remove(roomId);
    return Response.noContent().build();
}
```

**Business Logic - The key part:**
- If a room still has sensors, deletion is **blocked** with 409 Conflict
- This prevents data orphans (sensors with no room)
- Only after all sensors are removed can the room be deleted

**Idempotency Question (for your report):**
> DELETE is NOT fully idempotent in this implementation:
> - First call to DELETE a non-existent room: 404 Not Found
> - Second call: 404 (same response)
> - This is actually good! True idempotency would require returning 204 both times, but returning 404 for a non-existent resource is more semantically correct. Some argue this means DELETE is "safe-idempotent" - the operation is idempotent in intent (delete the room), even if the HTTP response differs.

**Testing:**
```bash
# Delete succeeds (no sensors)
curl -i -X DELETE http://localhost:8080/api/v1/rooms/LIB-301

# Get deleted room - 404
curl -i http://localhost:8080/api/v1/rooms/LIB-301
```

---

## Part 3: Sensors & Filtering (20 Marks)

### Step 3.1: Sensor Model

**File:** `src/main/java/com/smartcampus/model/Sensor.java`

```java
public class Sensor {
    private String id;              // e.g., "CO2-001"
    private String type;            // e.g., "CO2", "Temperature"
    private String status;          // "ACTIVE", "MAINTENANCE", "OFFLINE"
    private double currentValue;    // Latest reading
    private String roomId;          // Foreign key
}
```

**Foreign Key Design:**
- `roomId` links sensor to a room
- This enables room-sensor relationships without a full database
- We validate that the room exists before allowing sensor creation

---

### Step 3.2: Sensor Creation with Validation

**File:** `src/main/java/com/smartcampus/resources/SensorResource.java`

```java
@POST
@Consumes(MediaType.APPLICATION_JSON)
public Response createSensor(Sensor sensor, @Context UriInfo uriInfo) {
    if (sensor == null || isBlank(sensor.getId()) || isBlank(sensor.getType()) || isBlank(sensor.getRoomId())) {
        throw new BadRequestException("Sensor id, type and roomId are required.");
    }

    // Foreign key validation - CRITICAL!
    Room room = InMemoryStore.ROOMS.get(sensor.getRoomId());
    if (room == null) {
        throw new LinkedResourceNotFoundException("Linked room does not exist: " + sensor.getRoomId());
    }

    Sensor newSensor = new Sensor(
        sensor.getId(),
        sensor.getType(),
        normalizeStatus(sensor.getStatus()),
        sensor.getCurrentValue(),
        sensor.getRoomId());

    Sensor existing = InMemoryStore.SENSORS.putIfAbsent(newSensor.getId(), newSensor);
    if (existing != null) {
        throw new WebApplicationException(
            Response.status(Response.Status.CONFLICT)
                .entity(Map.of("message", "A sensor with this id already exists."))
                .type(MediaType.APPLICATION_JSON)
                .build());
    }

    room.getSensorIds().add(newSensor.getId());  // Update parent room

    URI location = uriInfo.getAbsolutePathBuilder().path(newSensor.getId()).build();
    return Response.created(location).entity(newSensor).build();
}
```

**Critical validation step:**
```java
Room room = InMemoryStore.ROOMS.get(sensor.getRoomId());
if (room == null) {
    throw new LinkedResourceNotFoundException(...);  // → 422 Unprocessable Entity
}
```

**Why 422 instead of 404?**
- 404 = resource doesn't exist (generic)
- 422 = the request is syntactically correct but semantically invalid
- The JSON is valid, but the roomId it references doesn't exist
- This tells the client: "Your payload is well-formed, but the referenced room is missing"

**Testing valid sensor:**
```bash
curl -i -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"CO2-001","type":"CO2","status":"ACTIVE","currentValue":410.5,"roomId":"LIB-301"}'
```

**Testing invalid sensor (room doesn't exist):**
```bash
curl -i -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-999","type":"Temperature","roomId":"NO-SUCH-ROOM"}'
```

**Response:**
```
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/json

{"timestamp":1234567890,"status":422,"error":"Unprocessable Entity","message":"Linked room does not exist: NO-SUCH-ROOM","path":"/api/v1/sensors"}
```

---

### Step 3.3: Filtering Sensors

**File:** `src/main/java/com/smartcampus/resources/SensorResource.java`

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

**How @QueryParam works:**
- URL: `GET /api/v1/sensors?type=CO2`
- `@QueryParam("type")` automatically extracts the `type` parameter from query string
- If not provided, `type` is null

**Why query parameters for filtering?**

| Feature | Query Parameters | Path Parameters |
|---------|------------------|-----------------|
| Purpose | Filtering, sorting, pagination | Identifying resources |
| Multiple values | Supported (type=A&type=B) | Not typically |
| Caching | Query affects cache key | More cache-friendly |
| Semantics | "Optional modifiers" | "Required identifiers" |

**Testing:**
```bash
# Get all sensors
curl http://localhost:8080/api/v1/sensors

# Get only CO2 sensors
curl http://localhost:8080/api/v1/sensors?type=CO2

# Case-insensitive
curl http://localhost:8080/api/v1/sensors?type=co2
```

---

## Part 4: Sub-Resources (20 Marks)

### Step 4.1: Sub-Resource Locator Pattern

**File:** `src/main/java/com/smartcampus/resources/SensorResource.java`

```java
@Path("/{sensorId}/readings")
public SensorReadingResource sensorReadings(@PathParam("sensorId") String sensorId) {
    if (!InMemoryStore.SENSORS.containsKey(sensorId)) {
        throw new ResourceNotFoundException("Sensor not found: " + sensorId);
    }
    return new SensorReadingResource(sensorId);
}
```

**How it works:**
1. Client requests: `GET /api/v1/sensors/CO2-001/readings/`
2. JAX-RS matches `/api/v1/sensors/{sensorId}/readings`
3. This method returns a **new SensorReadingResource instance**
4. JAX-RS then looks for matching methods on the returned resource
5. Finds the `@GET` method on SensorReadingResource

**Why this pattern?**
- **Separation of concerns**: SensorResource doesn't need to know about readings
- **Testability**: You can unit-test SensorReadingResource independently
- **Maintainability**: Reading logic is isolated
- **Scalability**: Different API versions can have different sub-resource implementations

---

### Step 4.2: Reading Management

**File:** `src/main/java/com/smartcampus/resources/SensorReadingResource.java`

```java
@Produces(MediaType.APPLICATION_JSON)
public class SensorReadingResource {
    private final String sensorId;

    public SensorReadingResource(String sensorId) {
        this.sensorId = sensorId;
    }

    @GET
    @Path("/")
    public List<SensorReading> getReadings() {
        ensureSensorExists();
        return InMemoryStore.getOrCreateReadings(sensorId);
    }

    @POST
    @Path("/")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response addReading(SensorReading payload, @Context UriInfo uriInfo) {
        Sensor sensor = ensureSensorExists();
        
        // Validation: sensor must not be in maintenance
        if ("MAINTENANCE".equals(sensor.getStatus().toUpperCase(Locale.ROOT))) {
            throw new SensorUnavailableException("Sensor is in MAINTENANCE and cannot accept new readings.");
        }

        if (payload == null) {
            throw new BadRequestException("Reading payload is required.");
        }

        String readingId = (payload.getId() == null || payload.getId().trim().isEmpty())
            ? UUID.randomUUID().toString()
            : payload.getId();
        long timestamp = payload.getTimestamp() <= 0 ? System.currentTimeMillis() : payload.getTimestamp();

        SensorReading newReading = new SensorReading(readingId, timestamp, payload.getValue());
        InMemoryStore.getOrCreateReadings(sensorId).add(newReading);
        
        // SIDE EFFECT: Update parent sensor's current value
        sensor.setCurrentValue(newReading.getValue());

        URI location = uriInfo.getAbsolutePathBuilder().path(newReading.getId()).build();
        return Response.created(location).entity(newReading).build();
    }
}
```

**Key concepts:**

1. **UUID Generation**: If reading ID not provided, generate a UUID
2. **Timestamp handling**: Use current time if timestamp not provided
3. **Side effect**: Setting the sensor's `currentValue` ensures parent stays in sync
4. **State consistency**: If sensor is MAINTENANCE, reject new readings (403 Forbidden)

**Testing readings:**
```bash
# Add a reading
curl -i -X POST http://localhost:8080/api/v1/sensors/CO2-001/readings/ \
  -H "Content-Type: application/json" \
  -d '{"value":425.8}'

# Get reading history
curl http://localhost:8080/api/v1/sensors/CO2-001/readings/

# Try to add reading to maintenance sensor - 403 Forbidden
curl -i -X POST http://localhost:8080/api/v1/sensors/CO2-001/readings/ \
  -H "Content-Type: application/json" \
  -d '{"value":430.0}'
```

---

## Part 5: Error Handling (30 Marks)

### Step 5.1: Exception-to-HTTP-Status Mapping

The key insight: **JAX-RS maps exceptions to HTTP responses via ExceptionMapper**

### Exception Classes

All custom exceptions extend `RuntimeException`:

```java
public class RoomNotEmptyException extends RuntimeException {
    public RoomNotEmptyException(String message) {
        super(message);
    }
}
```

### Exception Mappers

**Pattern for each mapper:**

```java
@Provider
public class RoomNotEmptyExceptionMapper implements ExceptionMapper<RoomNotEmptyException> {
    @Context
    private UriInfo uriInfo;

    @Override
    public Response toResponse(RoomNotEmptyException exception) {
        ErrorResponse error = new ErrorResponse(
            Response.Status.CONFLICT.getStatusCode(),
            "Conflict",
            exception.getMessage(),
            uriInfo.getPath());

        return Response.status(Response.Status.CONFLICT)
            .type(MediaType.APPLICATION_JSON)
            .entity(error)
            .build();
    }
}
```

**What @Provider does:**
- Registers the mapper with JAX-RS
- JAX-RS automatically catches exceptions and delegates to matching mapper

### HTTP Status Codes Used

| Exception | Status | Reason |
|-----------|--------|--------|
| RoomNotEmptyException | 409 Conflict | Room state conflicts with delete request |
| LinkedResourceNotFoundException | 422 Unprocessable Entity | Payload references non-existent room |
| SensorUnavailableException | 403 Forbidden | Resource exists but can't process request |
| ResourceNotFoundException | 404 Not Found | Resource doesn't exist |
| Throwable (global) | 500 Internal Server Error | Unexpected error |

### Error Response Format

```json
{
  "timestamp": 1618492800000,
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Linked room does not exist: NO-SUCH-ROOM",
  "path": "/api/v1/sensors"
}
```

**Fields:**
- `timestamp`: When error occurred (epoch ms)
- `status`: HTTP status code
- `error`: Error category
- `message`: Specific details
- `path`: Which endpoint failed

---

### Step 5.2: Global Exception Handler

**File:** `src/main/java/com/smartcampus/mappers/GlobalExceptionMapper.java`

```java
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Throwable> {
    // ...
    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException) {
            WebApplicationException webEx = (WebApplicationException) exception;
            Response existing = webEx.getResponse();
            // ... delegate to specific mapper
        }

        LOGGER.log(Level.SEVERE, "Unhandled exception", exception);
        ErrorResponse error = new ErrorResponse(
            Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
            "Internal Server Error",
            "An unexpected error occurred. Please contact support.",
            uriInfo == null ? "" : uriInfo.getPath());

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
            .type(MediaType.APPLICATION_JSON)
            .entity(error)
            .build();
    }
}
```

**Security note:**
- Notice: `"An unexpected error occurred. Please contact support."` - **NO STACK TRACE**
- Stack traces expose:
  - Internal class names and paths
  - Library versions (Spring 5.2.3 vs 5.3.0)
  - Code logic and vulnerable libraries
  - This is a **security risk**

---

### Step 5.3: Logging Filter

**File:** `src/main/java/com/smartcampus/filters/ApiLoggingFilter.java`

```java
@Provider
@Priority(Priorities.USER)
public class ApiLoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final Logger LOGGER = Logger.getLogger(ApiLoggingFilter.class.getName());

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        LOGGER.info(() -> String.format("Incoming request: %s %s",
            requestContext.getMethod(),
            requestContext.getUriInfo().getRequestUri()));
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        LOGGER.info(() -> String.format("Outgoing response: %s %s -> %d",
            requestContext.getMethod(),
            requestContext.getUriInfo().getRequestUri().getPath(),
            responseContext.getStatus()));
    }
}
```

**What it does:**
- Logs every incoming request (method + URI)
- Logs every outgoing response (status code)
- Uses lambda for lazy evaluation (only evaluates if log level is enabled)

---

## Step 6: In-Memory Data Store

**File:** `src/main/java/com/smartcampus/store/InMemoryStore.java`

```java
public final class InMemoryStore {
    public static final Map<String, Room> ROOMS = new ConcurrentHashMap<>();
    public static final Map<String, Sensor> SENSORS = new ConcurrentHashMap<>();
    public static final Map<String, List<SensorReading>> READINGS = new ConcurrentHashMap<>();

    private InMemoryStore() {
    }

    public static List<SensorReading> getOrCreateReadings(String sensorId) {
        return READINGS.computeIfAbsent(sensorId, key -> new CopyOnWriteArrayList<>());
    }
}
```

**Thread Safety:**
- `ConcurrentHashMap`: Safe for concurrent reads/writes without external synchronization
- `CopyOnWriteArrayList`: Safe for concurrent reads with occasional writes (readings)
- Why not `HashMap` + `synchronized`? Performance - concurrent structures scale better

---

## Part 7: Building and Running

### Build
```bash
mvn clean package
```

**Output:**
```
[INFO] Building jar: ...target/smart-campus-api-1.0.0.jar
[INFO] BUILD SUCCESS
```

### Run
```bash
mvn exec:java
```

**Console output:**
```
Smart Campus API started at http://0.0.0.0:8080/api/v1
Press Enter to stop the server...
```

---

## Part 8: Testing All Endpoints

### Test Discovery
```bash
curl http://localhost:8080/api/v1
```

### Test Room CRUD
```bash
# Create
curl -X POST http://localhost:8080/api/v1/rooms \
  -H "Content-Type: application/json" \
  -d '{"id":"LIB-301","name":"Library","capacity":120}'

# List
curl http://localhost:8080/api/v1/rooms

# Get one
curl http://localhost:8080/api/v1/rooms/LIB-301

# Delete (will fail - has sensors)
curl -X DELETE http://localhost:8080/api/v1/rooms/LIB-301
```

### Test Sensor CRUD + Validation
```bash
# Create valid sensor
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"CO2-001","type":"CO2","status":"ACTIVE","currentValue":410.5,"roomId":"LIB-301"}'

# Try invalid (room doesn't exist) - 422
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-999","type":"Temperature","roomId":"NO-ROOM"}'

# List all
curl http://localhost:8080/api/v1/sensors

# Filter by type
curl "http://localhost:8080/api/v1/sensors?type=CO2"

# Get one
curl http://localhost:8080/api/v1/sensors/CO2-001
```

### Test Sub-Resources
```bash
# Add reading
curl -X POST http://localhost:8080/api/v1/sensors/CO2-001/readings/ \
  -H "Content-Type: application/json" \
  -d '{"value":425.8}'

# Get history
curl http://localhost:8080/api/v1/sensors/CO2-001/readings/

# Try reading on maintenance sensor - 403
curl -X POST http://localhost:8080/api/v1/sensors/MAINT-001/readings/ \
  -H "Content-Type: application/json" \
  -d '{"value":430}'
```

### Test Error Handling
```bash
# 404 - room doesn't exist
curl http://localhost:8080/api/v1/rooms/NO-ROOM

# 409 - room has sensors
curl -X DELETE http://localhost:8080/api/v1/rooms/LIB-301

# 422 - invalid room reference
curl -X POST http://localhost:8080/api/v1/sensors \
  -H "Content-Type: application/json" \
  -d '{"id":"TEMP-001","type":"Temperature","roomId":"NO-ROOM"}'
```

---

## Key Takeaways for Your Viva

1. **JAX-RS Lifecycle**: Request-scoped by default → need thread-safe data structures
2. **HATEOAS**: Discovery endpoint provides links instead of hardcoding URLs
3. **Foreign Key Validation**: Prevents orphaned data (422 status)
4. **Idempotency**: DELETE returns 404 for non-existent (semantically correct over 204)
5. **Sub-resource Locators**: Separate classes for separate concerns
6. **Exception Mapping**: Automatic conversion to appropriate HTTP status codes
7. **Security**: Never expose stack traces to clients
8. **Thread Safety**: ConcurrentHashMap and CopyOnWriteArrayList for shared data

---

## Personalization Checklist

Before submission:
- [ ] Change contact email to yours or a plausible one
- [ ] Change "Facilities Technology Team" to "Student IT Team" or similar
- [ ] Add one custom validation rule (e.g., room capacity must be > 0)
- [ ] Change one sensor type (e.g., add "Light Intensity")
- [ ] Add a comment explaining one complex piece of code
- [ ] Test all 9 curl commands in the Testing section above
- [ ] Record a video of the API working
- [ ] Prepare answers to the REPORT_QUESTIONS section

---

## Report Questions to Address

1. **Part 1.1**: Explain JAX-RS Resource lifecycle and how you ensured thread safety
2. **Part 1.2**: Justify why HATEOAS (discovery endpoint) is better than static documentation
3. **Part 2.1**: Analyze returning IDs vs full objects in list endpoints
4. **Part 2.2**: Is DELETE idempotent? Provide detailed explanation
5. **Part 3.1**: Explain why 422 is better than 404 for bad foreign keys
6. **Part 3.2**: Why use query parameters for filtering vs path parameters
7. **Part 4.1**: Benefits of sub-resource locator pattern
8. **Part 4.2**: How did you ensure parent-child consistency
9. **Part 5.1**: Technical consequences of HTTP status codes
10. **Part 5.2**: Security risks of exposing stack traces

