package com.kk.pde.ds.ecf.consumer;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import com.kk.pde.ds.ecf.api.IRemoteGreet;

/**
 * Gogo shell command that invokes the remote {@link IRemoteGreet} on demand.
 *
 * <p>
 * Registered as a Gogo {@code Function} via the {@code osgi.command.scope} /
 * {@code osgi.command.function} service properties, so from the Equinox console
 * you can type:
 * </p>
 *
 * <pre>
 * g! ecf:greet World
 * </pre>
 *
 * <p>
 * and watch the call round-trip to the host JVM. The reference is
 * {@code OPTIONAL} (unlike {@link RemoteGreetConsumer}) so the command stays
 * registered even before/after the remote service is available, and reports a
 * friendly message when it is not.
 * </p>
 */
@Component(property = {
		"osgi.command.scope=ecf",
		"osgi.command.function=greet" }, service = GreetCommand.class)
public class GreetCommand {

	private volatile IRemoteGreet greet;

	@Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL)
	public void bindGreet(IRemoteGreet greet) {
		this.greet = greet;
	}

	public void unbindGreet(IRemoteGreet greet) {
		if (this.greet == greet) {
			this.greet = null;
		}
	}

	/** Gogo command: {@code ecf:greet <name>}. */
	public String greet(String name) {
		IRemoteGreet g = this.greet;
		if (g == null) {
			return "Remote IRemoteGreet is not available — is the host process running?";
		}
		return g.greet(name);
	}
}
