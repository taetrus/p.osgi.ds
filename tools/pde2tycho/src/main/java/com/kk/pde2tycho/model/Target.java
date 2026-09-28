package com.kk.pde2tycho.model;

import java.util.List;

/**
 * The active target platform. {@code locations} are Maven/InstallableUnit location
 * elements copied verbatim as XML text.
 */
public record Target(String source, List<String> locations, List<TargetJar> resolved, List<TargetJar> vendor) {
}
