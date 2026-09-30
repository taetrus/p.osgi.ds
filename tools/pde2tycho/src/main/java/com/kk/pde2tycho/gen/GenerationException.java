package com.kk.pde2tycho.gen;

import java.util.List;

/** The inventory cannot produce a working build; {@link #errors()} lists every reason. */
public final class GenerationException extends Exception {

	private static final long serialVersionUID = 1L;

	private final List<String> errors;

	public GenerationException(List<String> errors) {
		super(String.join("; ", errors));
		this.errors = List.copyOf(errors);
	}

	public List<String> errors() {
		return errors;
	}
}
