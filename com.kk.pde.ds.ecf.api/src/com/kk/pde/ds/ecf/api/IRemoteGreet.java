package com.kk.pde.ds.ecf.api;

/**
 * Remote-friendly greeting contract.
 *
 * <p>
 * This interface is the shared contract between the ECF Remote Services
 * <em>host</em> (which implements and exports it as a remote OSGi service) and
 * the <em>consumer</em> (which imports it and injects it via DS
 * {@code @Reference}). It is deliberately kept separate from the local
 * {@code com.kk.pde.ds.api.IGreet} so the two demos never compete for the same
 * {@code @Reference} injection.
 * </p>
 *
 * <p>
 * The single {@code String} argument and {@code String} return value exercise
 * ECF's argument/return marshalling across the network boundary.
 * </p>
 */
public interface IRemoteGreet {

	/**
	 * Greets the given name.
	 *
	 * @param name the name to greet
	 * @return a greeting produced by the (possibly remote) provider
	 */
	String greet(String name);
}
