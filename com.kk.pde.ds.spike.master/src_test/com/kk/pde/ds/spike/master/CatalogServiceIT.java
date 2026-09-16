package com.kk.pde.ds.spike.master;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

import com.kk.pde.ds.spike.api.ICatalogService;

/**
 * <h2>Tier 2 for the spike master: did SCR bring the catalog up?</h2>
 *
 * <p>
 * {@code CatalogServiceImplTest} already proves everything about the catalog's
 * <em>behaviour</em>, by calling {@code new CatalogServiceImpl()} and {@code start()} by
 * hand. What it cannot prove is that, in a real framework, Felix SCR reads
 * {@code OSGI-INF/com.kk.pde.ds.spike.master.CatalogServiceImpl.xml}, calls
 * {@code @Activate} itself, and registers the result as {@link ICatalogService} with the
 * {@code service.exported.*} properties the ECF export depends on. That is what this
 * class checks. Read {@code GreetServiceIT} in {@code com.kk.pde.ds.imp} first for how a
 * {@code *IT} class gets to run inside Equinox.
 * </p>
 * <p>
 * The other component in this bundle, {@code MasterApp}, is activated by SCR here too.
 * Its {@code start()} sees the headless JVM (the pom passes
 * {@code -Djava.awt.headless=true}) and returns without opening windows — which is why a
 * {@code mvn verify} on a laptop does not pop up App-1 frames.
 * </p>
 */
public class CatalogServiceIT {

    private final BundleContext context = FrameworkUtil.getBundle(CatalogServiceImpl.class).getBundleContext();

    @Test
    void bundleIsActive() {
        Bundle bundle = FrameworkUtil.getBundle(CatalogServiceImpl.class);

        assertEquals("com.kk.pde.ds.spike.master", bundle.getSymbolicName());
        assertEquals(Bundle.ACTIVE, bundle.getState());
    }

    /**
     * The service exists, was seeded by SCR calling {@code @Activate} (five items), and
     * starts with nothing selected — the same facts the unit test checks, now established
     * by the framework rather than by the test.
     */
    @Test
    void scrActivatesAndRegistersTheCatalog() {
        ServiceReference<ICatalogService> ref = context.getServiceReference(ICatalogService.class);
        assertNotNull(ref, "ICatalogService was not registered — check the component XML");

        ICatalogService catalog = context.getService(ref);
        try {
            assertTrue(catalog instanceof CatalogServiceImpl, "expected CatalogServiceImpl, got " + catalog.getClass());
            assertEquals(5, catalog.listItems().size());
            assertNull(catalog.getSelectedId());
        } finally {
            context.ungetService(ref);
        }
    }

    /**
     * The properties on the registration are what make ECF export the service to the
     * detail JVM. They live in the {@code @Component(property = ...)} annotation and end up
     * in the generated XML; this is the only place they can be read back as the framework
     * sees them.
     */
    @Test
    void catalogIsRegisteredWithTheRemoteExportProperties() {
        ServiceReference<ICatalogService> ref = context.getServiceReference(ICatalogService.class);
        assertNotNull(ref);

        assertEquals("*", ref.getProperty("service.exported.interfaces"));
        assertEquals("ecf.generic.server", ref.getProperty("service.exported.configs"));
        assertEquals("ecftcp://localhost:3289/catalog", ref.getProperty("ecf.exported.containerfactoryargs"));
    }
}
