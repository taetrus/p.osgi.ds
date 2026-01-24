package com.kk.pde.ds.rest;

/**
 * JSON DTO for greeting responses.
 */
public class GreetResponse {

	private String message;
	private long timestamp;

	public GreetResponse() {
		this.timestamp = System.currentTimeMillis();
	}

	public GreetResponse(String message) {
		this();
		this.message = message;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
}
