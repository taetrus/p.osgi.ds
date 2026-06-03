package com.kk.pde.ds.spike.api;

import java.io.Serializable;

/**
 * Immutable, serializable catalog record shared between the master app (which
 * owns the data) and the detail app (which displays it) across JVMs.
 *
 * <p>
 * It is a {@link Serializable} value object — never a live domain object — because
 * it is marshalled over ECF's Generic transport. The explicit
 * {@code serialVersionUID} is part of the cross-app contract: independently
 * deployed apps must agree on it.
 * </p>
 */
public final class CatalogItem implements Serializable {

	private static final long serialVersionUID = 1L;

	private final String id;
	private final String name;
	private final String description;
	private final int quantity;

	public CatalogItem(String id, String name, String description, int quantity) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.quantity = quantity;
	}

	public String getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public int getQuantity() {
		return quantity;
	}

	@Override
	public String toString() {
		return name;
	}
}
