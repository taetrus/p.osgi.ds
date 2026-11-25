package com.kk.pde.ds.webconsole.terminal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleContext;

/**
 * <h2>Tier 1: the plugin loads and works in an application with no Gogo</h2>
 *
 * <p>
 * The bundle imports {@code org.apache.felix.service.command} (Gogo) as optional. Without Gogo,
 * each class that names a Gogo type in a method signature fails in
 * {@code Class.getDeclaredMethods()} with {@code NoClassDefFoundError}. SCR and
 * {@code HttpServlet.doOptions} call that method on the plugin class. Thus
 * {@link TerminalPlugin} must not name a Gogo type in a field or a method signature.
 * </p>
 * <p>
 * The test cannot remove Gogo from the test class path. Instead, it loads the classes of this
 * package through a child-first class loader. That loader throws
 * {@code ClassNotFoundException} for each class in {@code org.apache.felix.service.command}. All
 * other classes (servlet API, WebConsole, OSGi, Mockito) come from the normal class path. Then the
 * test does what SCR and the WebConsole do: it lists the declared methods, calls the public
 * no-argument constructor, activates the plugin with a mock {@link BundleContext}, renders the
 * page and sends a command. With no command processor, the page is read-only
 * ({@code data-shell="false"}) and {@code exec} answers 503.
 * </p>
 */
class NoGogoTest {

    private static final String PACKAGE = "com.kk.pde.ds.webconsole.terminal.";
    private static final String GOGO = "org.apache.felix.service.command.";

    /** Defines the classes of this package itself. Refuses Gogo. Delegates the rest. */
    private static final class NoGogoLoader extends ClassLoader {
        NoGogoLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith(GOGO)) {
                throw new ClassNotFoundException(name);
            }
            if (!name.startsWith(PACKAGE) || name.startsWith(NoGogoTest.class.getName())) {
                return super.loadClass(name, resolve);
            }
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                byte[] bytes = bytes(name);
                c = defineClass(name, bytes, 0, bytes.length);
            }
            if (resolve) {
                resolveClass(c);
            }
            return c;
        }

        private byte[] bytes(String name) throws ClassNotFoundException {
            InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class");
            if (in == null) {
                throw new ClassNotFoundException(name);
            }
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                for (int n; (n = in.read(chunk)) > 0;) {
                    out.write(chunk, 0, n);
                }
                in.close();
                return out.toByteArray();
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    @Test
    void pluginLoadsWithoutGogo() throws Exception {
        ClassLoader loader = new NoGogoLoader(NoGogoTest.class.getClassLoader());
        Class<?> pluginClass = Class.forName(TerminalPlugin.class.getName(), true, loader);
        assertTrue(pluginClass.getClassLoader() == loader, "the plugin class must come from the test loader");

        // SCR and HttpServlet.doOptions list the declared methods.
        pluginClass.getDeclaredMethods();

        Object plugin = pluginClass.getConstructor().newInstance();
        BundleContext context = mock(BundleContext.class);
        pluginClass.getMethod("activate", BundleContext.class).invoke(plugin, context);

        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        StringWriter page = new StringWriter();
        when(res.getWriter()).thenReturn(new PrintWriter(page));
        Method render = pluginClass.getDeclaredMethod("renderContent", HttpServletRequest.class,
                HttpServletResponse.class);
        render.setAccessible(true);
        render.invoke(plugin, req, res);
        assertTrue(page.toString().contains("id=\"terminal\""), page.toString());

        String html = page.toString();
        assertTrue(html.contains("data-shell=\"false\""), "no processor: the page is read-only");
        int start = html.indexOf("data-token=\"") + "data-token=\"".length();
        String token = html.substring(start, html.indexOf('"', start));
        HttpServletRequest post = mock(HttpServletRequest.class);
        HttpServletResponse postRes = mock(HttpServletResponse.class);
        when(post.getPathInfo()).thenReturn("/terminal/exec");
        when(post.getHeader("X-Terminal-Token")).thenReturn(token);
        when(post.getInputStream()).thenReturn(new Body("ss"));
        Method doPost = pluginClass.getDeclaredMethod("doPost", HttpServletRequest.class, HttpServletResponse.class);
        doPost.setAccessible(true);
        doPost.invoke(plugin, post, postRes);
        verify(postRes).sendError(503);

        pluginClass.getMethod("deactivate").invoke(plugin);
    }

    /** A request body. */
    private static final class Body extends ServletInputStream {
        private final ByteArrayInputStream in;

        Body(String text) {
            in = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public int read() {
            return in.read();
        }

        @Override
        public boolean isFinished() {
            return in.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
        }
    }
}
