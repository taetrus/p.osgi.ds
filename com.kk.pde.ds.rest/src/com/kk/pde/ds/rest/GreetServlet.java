package com.kk.pde.ds.rest;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.api.IGreet;

/**
 * REST-like servlet exposing the IGreet service via HTTP Whiteboard.
 * Registered at /api/greet using OSGi HTTP Whiteboard pattern.
 *
 * Endpoints:
 * - GET /api/greet - Returns JSON greeting
 * - GET /api/greet/{name} - Returns personalized greeting
 * - POST /api/greet - Echoes posted message
 */
@Component(
	service = Servlet.class,
	property = {
		HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/api/greet/*",
		HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME + "=GreetServlet"
	}
)
public class GreetServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(GreetServlet.class);

	private IGreet greetService; 
	
	@Reference
	public void setGreetService(IGreet service)
	{
		greetService = service;
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		String pathInfo = req.getPathInfo();
		String name = null;

		if (pathInfo != null && pathInfo.length() > 1) {
			name = pathInfo.substring(1); // Remove leading slash
		}

		LOG.info("GET /api/greet{} called", name != null ? "/" + name : "");
		greetService.greet();

		String message = name != null
			? "Hello, " + name + "!"
			: "Hello from OSGi HTTP Whiteboard!";

		sendJsonResponse(resp, message);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		LOG.info("POST /api/greet called");
		greetService.greet();

		// Read request body
		StringBuilder sb = new StringBuilder();
		String line;
		while ((line = req.getReader().readLine()) != null) {
			sb.append(line);
		}

		String body = sb.toString();
		String message = "Echo: " + (body.isEmpty() ? "empty" : body);

		sendJsonResponse(resp, message);
	}

	private void sendJsonResponse(HttpServletResponse resp, String message) throws IOException {
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");

		GreetResponse response = new GreetResponse(message);
		String json = toJson(response);

		PrintWriter writer = resp.getWriter();
		writer.print(json);
		writer.flush();
	}

	private String toJson(GreetResponse response) {
		// Simple JSON serialization without external dependencies
		return String.format(
			"{\"message\":\"%s\",\"timestamp\":%d}",
			escapeJson(response.getMessage()),
			response.getTimestamp()
		);
	}

	private String escapeJson(String value) {
		if (value == null) return "";
		return value
			.replace("\\", "\\\\")
			.replace("\"", "\\\"")
			.replace("\n", "\\n")
			.replace("\r", "\\r")
			.replace("\t", "\\t");
	}
}
