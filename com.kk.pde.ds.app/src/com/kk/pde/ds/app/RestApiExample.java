package com.kk.pde.ds.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
		service = RestApiExample.class,
		property = {
				JaxrsWhiteboardConstants.JAX_RS_RESOURCE + "=true"
		})
@Path("/rest/example")
public class RestApiExample {

	private static final Logger log = LoggerFactory.getLogger(RestApiExample.class);

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String example() {
		String endpoint = "https://api.example.com/v1/status";
		try {
			return fetchJson(endpoint);
		} catch (IOException exception) {
			log.warn("Failed to fetch REST example payload from {}", endpoint, exception);
			return "{\"error\":\"Unable to fetch REST example payload\"}";
		}
	}

	public String fetchJson(String endpointUrl) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(endpointUrl).openConnection();
		connection.setRequestMethod("GET");
		connection.setRequestProperty("Accept", "application/json");
		connection.setConnectTimeout(5000);
		connection.setReadTimeout(5000);

		try {
			int status = connection.getResponseCode();
			InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
			return stream == null ? "" : readResponse(stream);
		} finally {
			connection.disconnect();
		}
	}

	private String readResponse(InputStream stream) throws IOException {
		try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
				BufferedReader buffered = new BufferedReader(reader)) {
			StringBuilder response = new StringBuilder();
			String line;
			while ((line = buffered.readLine()) != null) {
				response.append(line);
			}
			String body = response.toString();
			log.debug("REST response payload size: {} bytes", body.length());
			return body;
		}
	}
}
