package com.kk.pde.ds.ecf.host;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.ecf.api.IRemoteGreet;

/**
 * Host-side implementation of {@link IRemoteGreet}, exported as an OSGi
 * <em>Remote Service</em> via ECF's Generic provider.
 *
 * <p>
 * The {@code property} entries below are the standard OSGi Remote Services
 * "export" hints. ECF's Remote Service Admin (RSA) topology manager scans every
 * registered service for these properties:
 * </p>
 * <ul>
 * <li>{@code service.exported.interfaces=*} — export all interfaces this service
 * is registered under (here, {@link IRemoteGreet}).</li>
 * <li>{@code service.exported.configs=ecf.generic.server} — use ECF's built-in
 * Generic provider as the distribution transport.</li>
 * <li>{@code ecf.exported.containerfactoryargs=ecftcp://localhost:3288/server} —
 * bind the Generic server socket to a fixed, well-known endpoint so the consumer
 * process can find it via a static EDEF file (no discovery daemon needed).</li>
 * </ul>
 *
 * <p>
 * Note there is <strong>no ECF code here</strong> — exporting is purely
 * declarative. RSA does all the work in response to the service properties. The
 * checked-in {@code OSGI-INF/*.xml} component descriptor carries the same
 * properties for the Tycho/runtime build.
 * </p>
 */
@Component(property = {
		"service.exported.interfaces=*",
		"service.exported.configs=ecf.generic.server",
		"ecf.exported.containerfactoryargs=ecftcp://localhost:3288/server" })
public class RemoteGreetImpl implements IRemoteGreet {

	private static final Logger log = LoggerFactory.getLogger(RemoteGreetImpl.class);

	@Activate
	public void start() {
		log.info("RemoteGreetImpl activated — exporting IRemoteGreet via ecf.generic.server on ecftcp://localhost:3288/server");
	}

	@Override
	public String greet(String name) {
		log.info("RemoteGreetImpl.greet(\"{}\") invoked (remote call received)", name);
		return "Hello, " + name + "! (served remotely by host)";
	}
}
