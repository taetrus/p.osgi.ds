package com.kk.pde.ds.fatjar;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Fat JAR launcher that boots an embedded Equinox OSGi framework.
 *
 * IMPORTANT: This class has ZERO OSGi imports. All framework interaction happens
 * via reflection through a URLClassLoader that loads the Equinox JAR. This avoids
 * polluting the application classpath with org.osgi.* packages, which would
 * conflict with the framework's own class loading.
 *
 * Flow:
 *  1. Extract all JARs from bundles/ inside the fat JAR to a temp directory
 *  2. Load the Equinox JAR via URLClassLoader
 *  3. Use ServiceLoader to find FrameworkFactory
 *  4. Boot the framework, install bundles, set start levels, start everything
 *  5. Wait for shutdown
 *
 * Usage: java -jar osgi-fatjar.jar
 */
public class Main {

    /** Start-level map matching the product definition (p2.product). */
    private static final Map<String, StartConfig> BUNDLE_CONFIG = new LinkedHashMap<>();

    static {
        // Start level 1: framework essentials
        cfg("org.apache.felix.configadmin",           1, true);
        cfg("org.apache.felix.metatype",              1, true);

        // Start level 2: core services
        cfg("org.apache.felix.scr",                   2, true);
        cfg("org.osgi.service.component_",            2, true);
        cfg("org.osgi.service.component.annotations", 2, true);
        cfg("org.osgi.util.function",                 2, true);
        cfg("org.osgi.util.promise",                  2, true);
        cfg("org.apache.felix.eventadmin",            2, true);
        cfg("org.osgi.service.event",                 2, true);
        cfg("org.apache.felix.fileinstall",           2, true);
        cfg("org.apache.felix.healthcheck.api",       2, true);
        cfg("org.apache.felix.healthcheck.core",      2, true);

        // Start level 3: shell, console, health check extras
        cfg("org.apache.felix.gogo.runtime",          3, true);
        cfg("org.apache.felix.gogo.command",          3, true);
        cfg("org.apache.felix.gogo.shell",            3, true);
        cfg("org.eclipse.equinox.console",            3, true);
        cfg("org.apache.felix.healthcheck.generalchecks",    3, true);
        cfg("org.apache.felix.healthcheck.webconsoleplugin", 3, true);

        // Start level 4: application bundles + web infrastructure
        cfg("org.apache.felix.http.jetty",            4, true);
        cfg("org.apache.felix.http.servlet-api",      4, true);
        cfg("org.apache.felix.webconsole_",           4, true);
        cfg("org.apache.felix.webconsole.plugins.ds", 4, true);
        cfg("org.apache.felix.inventory",             4, true);
        cfg("com.kk.pde.ds.api",                     4, true);
        cfg("com.kk.pde.ds.imp",                     4, true);
        cfg("com.kk.pde.ds.app",                     4, true);
        cfg("com.kk.pde.ds.rest",                    4, true);
        cfg("com.kk.pde.ds.mcp.api",                 4, true);
        cfg("com.kk.pde.ds.mcp.server",              4, true);
        cfg("com.kk.pde.ds.mcp.client",              4, true);
        cfg("com.kk.pde.ds.mcp.llm",                 4, true);
        cfg("com.kk.pde.ds.chatbot",                 4, true);

        // Start level 4: logging + commons (no auto-start — resolved lazily)
        cfg("ch.qos.logback.classic",                 4, false);
        cfg("ch.qos.logback.core",                    4, false);
        cfg("slf4j.api",                              4, false);
        cfg("org.apache.commons.commons-fileupload",  4, false);
        cfg("org.apache.commons.commons-io",          4, false);
    }

    private static void cfg(String symbolicPrefix, int startLevel, boolean autoStart) {
        BUNDLE_CONFIG.put(symbolicPrefix, new StartConfig(startLevel, autoStart));
    }

    public static void main(String[] args) throws Exception {
        System.out.println("OSGi Fat JAR Launcher starting...");

        // 1. Extract bundles and configuration from the fat JAR to a temp directory
        Path tempDir = Files.createTempDirectory("osgi-fatjar-");
        Path bundlesDir = extractBundles(tempDir);
        Path configDir = extractConfiguration(tempDir);

        // 1b. Set logback config as a JVM system property BEFORE any bundle loads it.
        //     Logback reads System.getProperty("logback.configurationFile"), not OSGi
        //     framework properties, so this must be set at the JVM level.
        System.setProperty("logback.configurationFile",
            configDir.resolve("logback.xml").toString());

        // 2. Find the Equinox framework JAR
        File equinoxJar = findEquinoxJar(bundlesDir);
        if (equinoxJar == null) {
            System.err.println("ERROR: Could not find org.eclipse.osgi JAR in extracted bundles");
            System.exit(1);
        }
        System.out.println("Framework JAR: " + equinoxJar.getName());

        // 3. Load Equinox via URLClassLoader (isolated from app classpath)
        URLClassLoader fwClassLoader = new URLClassLoader(
            new URL[] { equinoxJar.toURI().toURL() },
            Main.class.getClassLoader()
        );

        // 4. Configure framework properties
        Map<String, String> fwProps = new HashMap<>();
        fwProps.put("osgi.noShutdown", "true");
        fwProps.put("eclipse.ignoreApp", "true");
        fwProps.put("org.osgi.framework.storage", tempDir.resolve("osgi-cache").toString());
        fwProps.put("org.osgi.framework.storage.clean", "onFirstInit");
        fwProps.put("org.osgi.framework.startlevel.beginning", "4");

        // Equinox console: empty string = stdin/stdout, port number = telnet
        // Default to stdin/stdout; override with -Dosgi.console.port=5555 for telnet
        String consolePort = System.getProperty("osgi.console.port", "");
        fwProps.put("osgi.console", consolePort);

        // HTTP / WebConsole
        fwProps.put("org.apache.felix.http.enable", "true");
        fwProps.put("org.osgi.service.http.port", "8080");
        fwProps.put("felix.webconsole.username", "admin");
        fwProps.put("felix.webconsole.password", "admin");

        // FileInstall for health check .cfg files
        fwProps.put("felix.fileinstall.dir", configDir.resolve("configs").toString());
        fwProps.put("felix.fileinstall.noInitialDelay", "true");
        fwProps.put("felix.fileinstall.poll", "5000");
        fwProps.put("felix.fileinstall.log.level", "3");

        // Logback
        fwProps.put("logback.configurationFile", configDir.resolve("logback.xml").toString());

        // Pass through API key / model / base URL
        passThrough(fwProps, "openrouter.api.key");
        passThrough(fwProps, "openrouter.model");
        passThrough(fwProps, "openrouter.base.url");

        // 5. Boot the framework via ServiceLoader + reflection
        //    ServiceLoader.load(FrameworkFactory.class, fwClassLoader)
        Class<?> ffClass = fwClassLoader.loadClass("org.osgi.framework.launch.FrameworkFactory");
        ServiceLoader<?> loader = ServiceLoader.load(ffClass, fwClassLoader);
        Iterator<?> it = loader.iterator();
        if (!it.hasNext()) {
            System.err.println("ERROR: No FrameworkFactory found in " + equinoxJar.getName());
            System.exit(1);
        }
        Object factory = it.next();

        // Framework framework = factory.newFramework(fwProps)
        Method newFramework = factory.getClass().getMethod("newFramework", Map.class);
        Object framework = newFramework.invoke(factory, fwProps);

        // framework.start()
        Method fwStart = framework.getClass().getMethod("start");
        fwStart.invoke(framework);
        System.out.println("OSGi framework started.");

        // BundleContext ctx = framework.getBundleContext()
        Method getBundleContext = framework.getClass().getMethod("getBundleContext");
        Object ctx = getBundleContext.invoke(framework);

        // 6. Install bundles from the extracted temp directory
        File[] jars = bundlesDir.toFile().listFiles((dir, name) -> name.endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            System.err.println("ERROR: No bundles found in " + bundlesDir);
            System.exit(1);
        }
        Arrays.sort(jars, Comparator.comparing(File::getName));

        Method installBundle = ctx.getClass().getMethod("installBundle", String.class);
        Class<?> bslClass = fwClassLoader.loadClass("org.osgi.framework.startlevel.BundleStartLevel");
        Method bslSetStartLevel = bslClass.getMethod("setStartLevel", int.class);
        List<Object> installed = new ArrayList<>();

        for (File jar : jars) {
            // Skip the framework jar — it's already running
            if (jar.getName().startsWith("org.eclipse.osgi_")) {
                continue;
            }
            try {
                String location = jar.toURI().toString();
                Object bundle = installBundle.invoke(ctx, location);
                installed.add(bundle);

                // Set start level via the interface class (not the impl class)
                StartConfig sc = findConfig(jar.getName());
                if (sc != null) {
                    Method adapt = bundle.getClass().getMethod("adapt", Class.class);
                    Object bsl = adapt.invoke(bundle, bslClass);
                    if (bsl != null) {
                        bslSetStartLevel.invoke(bsl, sc.startLevel);
                    }
                }
            } catch (Exception e) {
                System.err.println("WARN: Failed to install " + jar.getName() + ": " + getRootMessage(e));
            }
        }

        System.out.println("Installed " + installed.size() + " bundles.");

        // 7. Start bundles that are configured for auto-start (skip fragments)
        Method getHeaders = installed.get(0).getClass().getMethod("getHeaders");
        Method bundleStart = installed.get(0).getClass().getMethod("start");
        Method getSymbolicName = installed.get(0).getClass().getMethod("getSymbolicName");
        Method getLocation = installed.get(0).getClass().getMethod("getLocation");

        for (Object bundle : installed) {
            String location = (String) getLocation.invoke(bundle);
            String fileName = location.substring(location.lastIndexOf('/') + 1);
            StartConfig sc = findConfig(fileName);
            boolean shouldStart = (sc != null) ? sc.autoStart : true;

            // Skip fragments (they have Fragment-Host header)
            @SuppressWarnings("unchecked")
            java.util.Dictionary<String, String> headers = (java.util.Dictionary<String, String>) getHeaders.invoke(bundle);
            if (headers.get("Fragment-Host") != null) {
                continue;
            }

            if (shouldStart) {
                try {
                    bundleStart.invoke(bundle);
                } catch (Exception e) {
                    String symName = (String) getSymbolicName.invoke(bundle);
                    System.err.println("WARN: Failed to start " + symName + ": " + getRootMessage(e));
                }
            }
        }

        // 8. Set framework start level to 4
        //    FrameworkStartLevel.setStartLevel(int, FrameworkListener...) is varargs,
        //    so the actual signature is (int, FrameworkListener[]). We need to pass
        //    the array type for the method lookup and an empty array for the invocation.
        Class<?> fslClass = fwClassLoader.loadClass("org.osgi.framework.startlevel.FrameworkStartLevel");
        Class<?> flClass = fwClassLoader.loadClass("org.osgi.framework.FrameworkListener");
        Class<?> flArrayClass = java.lang.reflect.Array.newInstance(flClass, 0).getClass();
        Method adaptFw = framework.getClass().getMethod("adapt", Class.class);
        Object fsl = adaptFw.invoke(framework, fslClass);
        Method setStartLevel = fslClass.getMethod("setStartLevel", int.class, flArrayClass);
        Object emptyListeners = java.lang.reflect.Array.newInstance(flClass, 0);
        setStartLevel.invoke(fsl, 4, emptyListeners);

        System.out.println("All bundles started. Framework running.");
        System.out.println("WebConsole: http://localhost:8080/system/console (admin/admin)");
        System.out.println("REST API:   http://localhost:8080/api/greet");
        System.out.println();

        // 9. Shutdown hook
        Method fwStop = framework.getClass().getMethod("stop");
        Method fwWaitForStop = framework.getClass().getMethod("waitForStop", long.class);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                System.out.println("Shutting down OSGi framework...");
                fwStop.invoke(framework);
                fwWaitForStop.invoke(framework, 10000L);
                deleteRecursive(tempDir);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));

        // 10. Wait for framework to stop (blocks forever until shutdown)
        fwWaitForStop.invoke(framework, 0L);
    }

    /** Find the Equinox JAR in the extracted bundles directory. */
    private static File findEquinoxJar(Path bundlesDir) {
        File[] files = bundlesDir.toFile().listFiles(
            (dir, name) -> name.startsWith("org.eclipse.osgi_") && name.endsWith(".jar")
        );
        return (files != null && files.length > 0) ? files[0] : null;
    }

    /**
     * Find the start config for a JAR filename by matching against the symbolic
     * name prefixes in BUNDLE_CONFIG.
     */
    private static StartConfig findConfig(String jarFileName) {
        for (Map.Entry<String, StartConfig> entry : BUNDLE_CONFIG.entrySet()) {
            if (jarFileName.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** Extract all JARs from bundles/ inside the fat JAR to a temp directory. */
    private static Path extractBundles(Path tempDir) throws IOException, URISyntaxException {
        Path bundlesDir = tempDir.resolve("bundles");
        Files.createDirectories(bundlesDir);

        String jarPath = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
        try (JarFile jarFile = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("bundles/") && name.endsWith(".jar") && !entry.isDirectory()) {
                    String fileName = name.substring("bundles/".length());
                    Path target = bundlesDir.resolve(fileName);
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        return bundlesDir;
    }

    /** Extract configuration/ resources from inside the fat JAR. */
    private static Path extractConfiguration(Path tempDir) throws IOException, URISyntaxException {
        Path configDir = tempDir.resolve("configuration");
        Files.createDirectories(configDir);
        Files.createDirectories(configDir.resolve("configs"));

        String jarPath = Main.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
        try (JarFile jarFile = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("configuration/") && !entry.isDirectory()) {
                    Path target = tempDir.resolve(name);
                    Files.createDirectories(target.getParent());
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
        return configDir;
    }

    /** Pass a system property or environment variable into framework properties. */
    private static void passThrough(Map<String, String> fwProps, String key) {
        String val = System.getProperty(key);
        if (val != null && !val.isEmpty()) {
            fwProps.put(key, val);
            return;
        }
        String envKey = key.toUpperCase().replace('.', '_');
        String envVal = System.getenv(envKey);
        if (envVal != null && !envVal.isEmpty()) {
            fwProps.put(key, envVal);
        }
    }

    /** Get the root cause message from a possibly-wrapped exception. */
    private static String getRootMessage(Exception e) {
        Throwable cause = e.getCause();
        return (cause != null) ? cause.getMessage() : e.getMessage();
    }

    /** Recursively delete a directory tree. */
    private static void deleteRecursive(Path path) {
        try {
            Files.walk(path)
                .sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
        } catch (IOException ignored) {}
    }

    /** Configuration for a bundle's start level and auto-start behavior. */
    private static class StartConfig {
        final int startLevel;
        final boolean autoStart;

        StartConfig(int startLevel, boolean autoStart) {
            this.startLevel = startLevel;
            this.autoStart = autoStart;
        }
    }
}
