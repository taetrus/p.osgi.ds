# Declarative Services

This document tells you how the greet demo uses OSGi Declarative Services (DS).
It shows each component, its code, its XML descriptor, and the sequence in which Felix SCR connects the components.

## Contents

- [What DS does](#what-ds-does)
- [Run the greet demo](#run-the-greet-demo)
- [The XML descriptor controls SCR](#the-xml-descriptor-controls-scr)
- [DS annotations in the code](#ds-annotations-in-the-code)
- [The components](#the-components)
  - [1. IGreet interface (API)](#1-igreet-interface-api)
  - [2. Greet implementation (provider)](#2-greet-implementation-provider)
  - [3. App (consumer)](#3-app-consumer)
  - [4. GreetServlet (HTTP Whiteboard)](#4-greetservlet-http-whiteboard)
  - [5. GreetHealthCheck](#5-greethealthcheck)
- [Service binding flow](#service-binding-flow)
- [Related documents](#related-documents)

## What DS does

OSGi Declarative Services gives dependency injection for OSGi services.
A DS component declares the services that it provides and the services that it needs.
The Service Component Runtime (SCR) reads these declarations.
In this project, the SCR is Felix SCR.
SCR creates each component when all its mandatory references have a service.
Then SCR gives the services to the component and calls its activate method.

The greet demo is the base of all other features.
`IGreet` (API bundle) goes to `Greet` (implementation bundle), and `Greet` goes to `App` (consumer bundle).
Felix SCR injects the services into `App` through the `@Reference` bind methods.

## Run the greet demo

1. Start the product:

   ```bash
   # macOS
   ./distribution/scripts/run.sh

   # Windows
   distribution\scripts\run.bat
   ```

2. Look at the console. `App` activates, SCR injects `Greet`, and "Hello world!" appears.
   Then `App` opens the Clock window and the Chat window.
3. Open http://localhost:8080/system/console/components in a browser. Use the user name `admin` and the password `admin`.
4. Find each DS component on this page, and look at the live state of its references.

For more data about the run scripts, see [running.md](running.md).
For the WebConsole, see [webconsole-and-health-checks.md](webconsole-and-health-checks.md).

## The XML descriptor controls SCR

Felix SCR reads the component XML files in `OSGI-INF/`.
The `Service-Component` header in `META-INF/MANIFEST.MF` lists these files.
In this project, a person writes each XML file by hand.
The build does not make the XML files from the annotations.
Thus the XML file is the source of truth, and the annotations are documentation only.

The bundles import `org.osgi.service.component.annotations` with `resolution:=optional`.
At runtime, nothing reads the annotations.

**Proof.** `App.stop()` has `@Deactivate`.
Before commit `7470d2c`, `OSGI-INF/com.kk.pde.ds.app.App.xml` had no `deactivate="stop"` attribute.
SCR looked for a method with the name `deactivate()`, and did not call `stop()`.
When you stopped the app bundle, the Clock window and the Chat window stayed open, and the clock timer continued to run.
Commit `7470d2c` added `deactivate="stop"` to the XML file.
The annotation did not change.

The XML file must name each lifecycle method that does not have the default name:

- If the XML has no `activate` attribute, SCR calls a method with the name `activate`.
- If the XML has no `deactivate` attribute, SCR calls a method with the name `deactivate`.
- Each other method name must be in the XML, for example `activate="start"`.

`GreetServlet` and `GreetHealthCheck` show the default rule.
Each has an `activate()` method, and their XML files have no `activate` attribute.

When you add or change a component, do these steps:

1. Change the Java class.
2. Change the XML file in `OSGI-INF/`. Make it agree with the class. Include the service, each reference, and each lifecycle method name.
3. Make sure that the `Service-Component` header in `META-INF/MANIFEST.MF` includes the XML file.

The full rule set is in [architecture.md, "How SCR reads the wiring"](architecture.md#how-scr-reads-the-wiring).

## DS annotations in the code

The Java classes use these annotations.
They tell the reader the intent of the code.
The XML file must give SCR the same data (see the section above).

| Annotation | Purpose |
|------------|---------|
| `@Component` | Declares a class as a DS component |
| `@Reference` | Injects a service dependency |
| `@Activate` | Marks the lifecycle method that SCR calls when it activates the component |
| `@Deactivate` | Marks the lifecycle method that SCR calls when it deactivates the component |

## The components

The snippets below come from the current sources.
Some snippets do not show imports, Javadoc, or other lines. A note tells you which snippets are shorter than the source.

### 1. IGreet interface (API)

**File:** `com.kk.pde.ds.api/src/com/kk/pde/ds/api/IGreet.java`

```java
package com.kk.pde.ds.api;

public interface IGreet {
    void greet();
}
```

### 2. Greet implementation (provider)

**File:** `com.kk.pde.ds.imp/src/com/kk/pde/ds/imp/Greet.java` (no imports)

```java
@Component
public class Greet implements IGreet {

    private static final Logger log = LoggerFactory.getLogger(Greet.class);

    @Activate
    public void start() {
        log.info("Greet.start()");
    }

    @Override
    public void greet() {
        log.info(greeting());
    }

    /**
     * The greeting text. Extracted as a testable seam so tests can assert on the
     * message without capturing log output.
     */
    public String greeting() {
        return "Hello world!";
    }
}
```

**DS descriptor:** `com.kk.pde.ds.imp/OSGI-INF/com.kk.pde.ds.imp.Greet.xml`

```xml
<scr:component xmlns:scr="http://www.osgi.org/xmlns/scr/v1.1.0" activate="start" name="com.kk.pde.ds.imp.Greet">
   <service>
      <provide interface="com.kk.pde.ds.api.IGreet"/>
   </service>
   <implementation class="com.kk.pde.ds.imp.Greet"/>
</scr:component>
```

- The descriptor registers `Greet` as a service for `com.kk.pde.ds.api.IGreet`.
- `activate="start"` tells SCR to call `start()`.
- The component provides a service and has no `immediate` attribute. Thus it is a delayed component.
  SCR registers the `IGreet` service first. SCR creates and activates `Greet` when a consumer gets the service for the first time.

### 3. App (consumer)

**File:** `com.kk.pde.ds.app/src/com/kk/pde/ds/app/App.java` (shorter than the source: no imports, no constructor, no `ClockPanel` and `ClockFrame` classes)

```java
@Component
public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    private IGreet greet;
    private ChatService chatService;

    // Held so @Deactivate can dispose them; set/read across the EDT and the SCR
    // deactivation thread, hence volatile.
    private volatile ClockFrame clockFrame;
    private volatile ChatFrame chatFrame;

    @Reference
    public void setChatService(ChatService chatService) {
        log.info("App.setChatService()");
        this.chatService = chatService;
    }

    @Reference
    public void setApi(IGreet greet) {
        log.info("App.setApi()");
        this.greet = greet;
    }

    @Activate
    public void start() {
        log.info("App.start()");

        greet.greet();

        // Launch the UI — apply dark theme BEFORE creating any components
        SwingUtilities.invokeLater(() -> {
            DarkTheme.apply();
            clockFrame = new ClockFrame();
            clockFrame.setVisible(true);
            chatFrame = new ChatFrame(chatService);
            chatFrame.setVisible(true);
        });
    }

    @Deactivate
    public void stop() {
        log.info("App.stop()");
        final ClockFrame clock = clockFrame;
        final ChatFrame chat = chatFrame;
        clockFrame = null;
        chatFrame = null;
        SwingUtilities.invokeLater(() -> {
            if (clock != null) {
                clock.dispose();
            }
            if (chat != null) {
                chat.dispose();
            }
        });
    }
}
```

**DS descriptor:** `com.kk.pde.ds.app/OSGI-INF/com.kk.pde.ds.app.App.xml`

```xml
<scr:component xmlns:scr="http://www.osgi.org/xmlns/scr/v1.1.0" activate="start" deactivate="stop" name="com.kk.pde.ds.app.App">
   <reference bind="setApi" interface="com.kk.pde.ds.api.IGreet" name="Api"/>
   <reference bind="setChatService" interface="com.kk.pde.ds.chatbot.ChatService" name="ChatService"/>
   <implementation class="com.kk.pde.ds.app.App"/>
</scr:component>
```

- The descriptor references `IGreet` through the `setApi` bind method.
- The descriptor references `ChatService` through the `setChatService` bind method.
- `activate="start"` and `deactivate="stop"` name the lifecycle methods.
- When you stop the app bundle, SCR calls `stop()`. `stop()` disposes the Clock window and the Chat window on the Swing event dispatch thread (EDT).
  `ClockFrame.dispose()` also stops the 1-second clock timer.
- `App` provides no service. Thus SCR activates it immediately when both references have a service.

### 4. GreetServlet (HTTP Whiteboard)

**File:** `com.kk.pde.ds.rest/src/com/kk/pde/ds/rest/GreetServlet.java` (shorter than the source)

```java
@Component(
    service = Servlet.class,
    property = {
        HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/api/greet/*",
        HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME + "=GreetServlet"
    }
)
public class GreetServlet extends HttpServlet {

    private IGreet greetService;

    @Activate
    public void activate() {
        LOG.info("GreetServlet activated at /api/greet");
    }

    @Reference
    public void setGreetService(IGreet service) {
        greetService = service;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) { ... }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) { ... }
}
```

**DS descriptor:** `com.kk.pde.ds.rest/OSGI-INF/com.kk.pde.ds.rest.GreetServlet.xml`

```xml
<scr:component xmlns:scr="http://www.osgi.org/xmlns/scr/v1.1.0" name="com.kk.pde.ds.rest.GreetServlet">
   <property name="osgi.http.whiteboard.servlet.pattern" value="/api/greet/*"/>
   <property name="osgi.http.whiteboard.servlet.name" value="GreetServlet"/>
   <reference bind="setGreetService" interface="com.kk.pde.ds.api.IGreet" name="GreetService"/>
   <service>
      <provide interface="javax.servlet.Servlet"/>
   </service>
   <implementation class="com.kk.pde.ds.rest.GreetServlet"/>
</scr:component>
```

The servlet endpoints and the whiteboard registration are in [rest-api.md](rest-api.md).

### 5. GreetHealthCheck

**File:** `com.kk.pde.ds.imp/src/com/kk/pde/ds/imp/GreetHealthCheck.java` (no imports, no Javadoc, no debug log line)

```java
@Component(
    service = HealthCheck.class,
    property = {
        HealthCheck.NAME + "=Greet Service Health Check",
        HealthCheck.TAGS + "=application,greet",
        HealthCheck.MBEAN_NAME + "=greetServiceHealthCheck"
    }
)
public class GreetHealthCheck implements HealthCheck {

    private static final Logger log = LoggerFactory.getLogger(GreetHealthCheck.class);

    @Reference
    private IGreet greetService;

    @Activate
    public void activate() {
        log.info("GreetHealthCheck activated");
    }

    @Override
    public Result execute() {
        FormattingResultLog resultLog = new FormattingResultLog();
        try {
            // greetService is a mandatory (1..1) static reference — SCR guarantees it is
            // non-null while this component is active, so no null check is needed here.
            greetService.greet();
            resultLog.info("IGreet service is available and operational");
            resultLog.info("Service implementation: {}", greetService.getClass().getName());
        } catch (Exception e) {
            log.error("Greet Service Health Check failed", e);
            resultLog.critical("IGreet service invocation failed: {}", e.getMessage());
        }
        return new Result(resultLog);
    }
}
```

**DS descriptor:** `com.kk.pde.ds.imp/OSGI-INF/com.kk.pde.ds.imp.GreetHealthCheck.xml`

```xml
<scr:component xmlns:scr="http://www.osgi.org/xmlns/scr/v1.3.0" name="com.kk.pde.ds.imp.GreetHealthCheck">
   <property name="hc.name" value="Greet Service Health Check"/>
   <property name="hc.tags" value="application,greet"/>
   <property name="hc.mbean.name" value="greetServiceHealthCheck"/>
   <implementation class="com.kk.pde.ds.imp.GreetHealthCheck"/>
   <service>
      <provide interface="org.apache.felix.hc.api.HealthCheck"/>
   </service>
   <reference cardinality="1..1" field="greetService" interface="com.kk.pde.ds.api.IGreet" name="greetService"/>
</scr:component>
```

- This descriptor uses the DS 1.3 namespace, because field injection (`field="greetService"`) starts in DS 1.3.
  The other descriptors use DS 1.1 and bind methods.
- The reference has no `policy` attribute. Thus the policy is static.
- The health check behavior is in [webconsole-and-health-checks.md](webconsole-and-health-checks.md#custom-health-check-greethealthcheck).

## Service binding flow

The diagram shows the order of events for the greet components.
Steps 2, 3 and 4 do not depend on each other. SCR does not guarantee their order.

```
┌─────────────────────────────────────────────────────────────────────┐
│                        OSGi Framework Startup                        │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│  1. Greet component is satisfied (it has no references)              │
│     - SCR registers the IGreet service                               │
│     - Greet is delayed: SCR activates it, and calls start(),         │
│       when the first consumer gets the service                       │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│  2. App component references satisfied (IGreet AND ChatService)      │
│     - setApi(IGreet) called with Greet instance                      │
│     - setChatService(ChatService) called                             │
│     - activate method start() called                                 │
│     - Calls greet.greet() → "Hello world!"                           │
│     - Opens the Clock and Chat windows on the EDT                    │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│  3. GreetServlet references satisfied                                │
│     - setGreetService(IGreet) called                                 │
│     - Registered at /api/greet/* via HTTP Whiteboard                 │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│  4. GreetHealthCheck references satisfied                            │
│     - IGreet injected into the greetService field                    │
│     - Available for health check execution                           │
└─────────────────────────────────────────────────────────────────────┘
```

`ChatService` has its own references. Thus `App` waits for the chatbot and LLM components too.
For the full wiring of all components, see [architecture.md](architecture.md).

## Related documents

- [architecture.md](architecture.md): the wiring diagrams of all components, and the rules for the XML descriptors.
- [rest-api.md](rest-api.md): the REST endpoints that `GreetServlet` gives.
- [webconsole-and-health-checks.md](webconsole-and-health-checks.md): the WebConsole and the health checks.
- [running.md](running.md): how to start the products.
- [build.md](build.md): how to build the products.
- [testing.md](testing.md): the tests for `Greet` and `GreetHealthCheck`.
