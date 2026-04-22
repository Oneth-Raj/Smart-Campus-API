# Client-Server Architectures - Coursework (2025/26)
## REST API design, development and implementation.

**Module**: Client-Server Architecture
**Module code**: 5COSC022C.2
**Module Lecture**: Cassim Farook

| Student Name | IIT Student ID | UOW Student ID |
|---|---|---|
| Oneth Rajakaruna | 20240870 | [Enter UOW ID] |

## Abstract
The conceptual architectural choices and design principles to be used in the creation of the sensor and room management API that forms part of the Smart Campus are discussed in this report. The project incorporating JAX-RS (Jakarta RESTful Web Services) and embedded server can prove a strong, scalable, and resilient RESTful web service. Other critical implementations examined in this report are the use of thread-safe in-memory data structures in managing state, integration of HATEOAS in resource discovery, deep nesting on Sub-Resource locators, and the use of strict HTTP status codes (409, 422, 403 and 500) through custom exception mapping and API observability filters.

## Acknowledgement
I would like to express my gratitude to my module leader Mr. Cassim Farook and the teaching team for their guidance and support throughout the Client-Server Architectures module. The foundational knowledge and practical insights gained during the lectures and labs were instrumental to the successful completion of this coursework project.

## Abbreviations Table

| Abbreviation | Meaning |
|---|---|
| API | Application Programming Interface |
| HATEOAS | Hypermedia as the Engine of Application State |
| HTTP | Hypertext Transfer Protocol |
| JAX-RS | Jakarta RESTful Web Services |
| JSON | JavaScript Object Notation |
| POJO | Plain Old Java Object |
| REST | Representational State Transfer |
| URI | Uniform Resource Identifier |
| UUID | Universally Unique Identifier |

---

# Smart Campus API - Conceptual Report

## Service Architecture & Setup

### Question
"In your report, explain the default lifecycle of a JAX-RS Resource class. Is a new instance instantiated for every incoming request, or does the runtime treat it as a singleton? Elaborate on how this architectural decision impacts the way you manage and synchronize your in-memory data structures (maps/lists) to prevent data loss or race conditions."

### Answer
JAX-RS resource classes use a request-scoped lifecycle by default, meaning a new instance is created for every incoming HTTP request and destroyed once a response is sent. To prevent data loss and ensure consistency, any shared data structures like maps or lists must be stored outside these transient instances. We achieve this by using static, thread-safe collections like ConcurrentHashMap, which prevents race conditions when multiple requests read and modify the data simultaneously.

### Question
"Why is the provision of 'Hypermedia' (links and navigation within responses) considered a hallmark of advanced RESTful design (HATEOAS)? How does this approach benefit client developers compared to static documentation?"

### Answer
HATEOAS improves API flexibility by providing dynamic navigation links within the response payloads rather than forcing clients to hardcode URIs from static documentation. This decouples the client from the server's routing structure. If endpoints change in the future, the client simply follows the new linked paths provided in the API response, eliminating the need for client-side code updates.

## Room Management

### Question
"When returning a list of rooms, what are the implications of returning only IDs versus returning the full room objects? Consider network bandwidth and client side processing."

### Answer
Returning only IDs drastically reduces the payload size and network bandwidth, but forces the client to make numerous subsequent requests to fetch details for each room, increasing latency. Returning full objects involves a larger initial payload and processing hit, but eliminates the need for secondary requests. We opt for full objects because modern networks easily handle small JSON structures and it significantly speeds up client rendering.

### Question
"Is the DELETE operation idempotent in your implementation? Provide a detailed justification by describing what happens if a client mistakenly sends the exact same DELETE request for a room multiple times."

### Answer
Yes, the DELETE operation is semantically idempotent. If a client mistakenly sends multiple identical DELETE requests for a room, the first request will successfully delete it and return a 204 No Content. Any subsequent identical requests will return a 404 Not Found since the resource is already gone. While the status codes differ, the server's end state remains identically resolved without unintended side effects, strictly adhering to idempotency.

## Sensor Operations & Linking

### Question
"We explicitly use the @Consumes(MediaType.APPLICATION_JSON) annotation on the POST method. Explain the technical consequences if a client attempts to send data in a different format, such as text/plain or application/xml. How does JAX-RS handle this mismatch?"

### Answer
The @Consumes annotation strictly enforces that the endpoint will only process incoming data formatted as JSON. If a client attempts to send XML or plain text, JAX-RS safely rejects the request before it even reaches the internal business logic. It automatically returns a 415 Unsupported Media Type HTTP error, protecting the system from malformed or unsupported parsing attempts.

### Question
"You implemented this filtering using @QueryParam. Contrast this with an alternative design where the type is part of the URL path (e.g., /api/v1/sensors/type/CO2). Why is the query parameter approach generally considered superior for filtering and searching collections?"

### Answer
Query parameters are specifically intended for filtering or searching collections, as they act as optional criteria overlaying an existing resource dataset without altering its hierarchy. Using a path parameter incorrectly implies a statically nested entity. Query parameters provide much more flexibility, allowing clients to combine multiple filters concurrently without breaking the standardized REST routing structure.

## Deep Nesting with Sub-Resources

### Question
"Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path (e.g., sensors/{id}/readings/{rid}) in one massive controller class?"

### Answer
The Sub-Resource Locator pattern allows parent classes to delegate deeply nested endpoints to dedicated child resource classes. This prevents the parent controller from becoming overly complex and bloated, enforcing a clean separation of concerns. It improves code readability, simplifies unit testing, and naturally preserves the contextual ID of the parent resource for the child to use without excessive parameter mapping.

## Advanced Error Handling

### Question
"Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?"

### Answer
HTTP 404 signifies that the requested endpoint URL itself cannot be found. Conversely, a 422 Unprocessable Entity accurately conveys that while the endpoint is correct and the JSON payload syntax is valid, the server cannot process the request due to semantic errors. In our context, this perfectly describes a payload failing a foreign key validation check where a linked room does not exist.

### Question
"From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?"

### Answer
Exposing raw Java stack traces reveals sensitive internal configurations to external consumers. Attackers can parse this data to discover the specific frameworks in use, exact library versions, internal routing maps, and file system paths. This extensive information footprint provides malicious actors with the precise blueprints needed to launch targeted injection attacks or exploit known library vulnerabilities.

### Question
"Why is it advantageous to use JAX-RS filters for cross-cutting concerns like logging, rather than manually inserting Logger.info() statements inside every single resource method?"

### Answer
JAX-RS filters centralize logging operations outside of the core business logic. Instead of repeating logging statements inside every single API method endpoint, a unified filter intercepts all requests and responses automatically. This significantly reduces code duplication, guarantees absolute uniformity across the entire API, and leaves the resource endpoints uncluttered.
