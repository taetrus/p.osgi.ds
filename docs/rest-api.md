# REST API

This document tells you how the `com.kk.pde.ds.rest` bundle gives the `IGreet` service on HTTP.
It lists the endpoints, the limits and errors, and the HTTP Whiteboard registration.

> **Security:** The HTTP surface on port 8080 has no authentication. Use it on localhost only. See [../SECURITY.md](../SECURITY.md).

## Contents

- [Overview](#overview)
- [Base configuration](#base-configuration)
- [Try the endpoints](#try-the-endpoints)
- [Endpoints](#endpoints)
- [Limits and errors](#limits-and-errors)
- [HTTP Whiteboard registration](#http-whiteboard-registration)
- [Related documents](#related-documents)

## Overview

The REST API uses the **OSGi HTTP Whiteboard** pattern. It does not use JAX-RS.
The servlet `GreetServlet` is a DS component that provides the `javax.servlet.Servlet` service.
Felix HTTP Jetty finds the service and its whiteboard properties, and serves the servlet.
There is no `web.xml` and no servlet container configuration.

## Base configuration

| Setting | Value |
|---------|-------|
| **Base URL** | `http://localhost:8080/api/greet` |
| **Servlet pattern** | `/api/greet/*` |
| **HTTP port** | 8080 (`org.osgi.service.http.port=8080` in `distribution/configuration/config.ini`) |

## Try the endpoints

1. Start the product. See [running.md](running.md), or [declarative-services.md](declarative-services.md#run-the-greet-demo).
2. Send these requests:

   ```bash
   # macOS (Terminal)
   curl http://localhost:8080/api/greet
   curl http://localhost:8080/api/greet/World
   curl -X POST http://localhost:8080/api/greet -d '{"message":"Test"}'

   # Windows (cmd.exe — note the escaped quotes on POST)
   curl http://localhost:8080/api/greet
   curl http://localhost:8080/api/greet/World
   curl -X POST http://localhost:8080/api/greet -d "{\"message\":\"Test\"}"
   ```

Each request also calls `IGreet.greet()`. Thus "Hello world!" appears in the console log for each request.

## Endpoints

| Method | URL | Description |
|--------|-----|-------------|
| GET | `/api/greet` | Returns a JSON greeting |
| GET | `/api/greet/{name}` | Returns a personalized greeting |
| POST | `/api/greet` | Echoes the posted body |

Each successful response has the content type `application/json` with UTF-8.
The body has two fields: `message`, and `timestamp` in milliseconds since the epoch.
The servlet writes the JSON by hand, with no JSON library.

### GET /api/greet

Returns a JSON greeting message.

```bash
curl http://localhost:8080/api/greet
```

**Response:**

```json
{"message":"Hello from OSGi HTTP Whiteboard!","timestamp":1737734567890}
```

### GET /api/greet/{name}

Returns a personalized greeting.

```bash
curl http://localhost:8080/api/greet/World
```

**Response:**

```json
{"message":"Hello, World!","timestamp":1737734567890}
```

The name is all of the path after `/api/greet/`.
For example, `/api/greet/a/b` returns `"Hello, a/b!"`.
A path of `/api/greet/` with no name returns the default greeting.

### POST /api/greet

Echoes the posted body. The servlet does not parse the body as JSON.

```bash
curl -X POST http://localhost:8080/api/greet -d '{"message":"Test"}'
```

**Response:**

```json
{"message":"Echo: {\"message\":\"Test\"}","timestamp":1737734567890}
```

- If the body is empty, the message is `"Echo: empty"`.
- The servlet keeps the line breaks in the body. The JSON output escapes them as `\n` and `\r`.

## Limits and errors

- The POST body has a limit of 64 KB. A larger body gets HTTP `413` ("Request too large").
  The servlet counts characters, not bytes. The limit is 65,536 characters (`MAX_BODY_BYTES = 64 * 1024`).
  The `413` response comes from `sendError()`. Thus its body is the error page of the container, not JSON.
- If the `IGreet` call throws a `RuntimeException`, the servlet returns HTTP `500` with a JSON body: `{"error":"<message>"}`.
  This rule applies to GET and to POST.
  If the response is already committed, the servlet only logs the error.

The limit stops a very large POST body from using all the heap of the shared Jetty server.

## HTTP Whiteboard registration

**File:** `com.kk.pde.ds.rest/src/com/kk/pde/ds/rest/GreetServlet.java`

```java
@Component(
    service = Servlet.class,
    property = {
        HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/api/greet/*",
        HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME + "=GreetServlet"
    }
)
public class GreetServlet extends HttpServlet { ... }
```

SCR does not read these annotations. It reads the hand-written descriptor
`com.kk.pde.ds.rest/OSGI-INF/com.kk.pde.ds.rest.GreetServlet.xml`.
The descriptor has the same two properties:

```xml
<property name="osgi.http.whiteboard.servlet.pattern" value="/api/greet/*"/>
<property name="osgi.http.whiteboard.servlet.name" value="GreetServlet"/>
```

If you change the pattern, change it in the XML file. A change to the annotation only has no effect.
See [declarative-services.md](declarative-services.md#the-xml-descriptor-controls-scr).

The servlet has a static reference to `IGreet` through the `setGreetService` bind method.
If no `IGreet` service is available, SCR does not register the servlet. Then no servlet serves `/api/greet`.

## Related documents

- [declarative-services.md](declarative-services.md): the DS components, with `GreetServlet`.
- [webconsole-and-health-checks.md](webconsole-and-health-checks.md): the "REST API Check" health check.
- [architecture.md](architecture.md): the wiring of all components.
- [running.md](running.md): how to start the product.
- [build.md](build.md): how to build the product.
- [../SECURITY.md](../SECURITY.md): the known exposures of the HTTP surface.
