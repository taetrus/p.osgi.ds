package com.kk.pde2tycho.resolve;

import java.io.IOException;
import java.util.Optional;

/** Finds the Maven coordinates of a file by its SHA-1. */
public interface ArtifactResolver {

	/** Never finds anything: used for --offline and after Central proves unreachable. */
	ArtifactResolver OFFLINE = sha1 -> Optional.empty();

	/** "groupId:artifactId:version" of the artifact whose file has this SHA-1, if known. */
	Optional<String> findBySha1(String sha1) throws IOException;
}
