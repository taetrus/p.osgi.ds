package com.kk.pde2tycho.model;

/**
 * A jar from a Directory/Profile target location. {@code gav} ("g:a:v") is set when
 * Maven Central has the identical file; otherwise the jar is vendored and
 * {@code reason} says why.
 */
public record TargetJar(String jar, String sha1, String bsn, String version, String gav, String reason) {
}
