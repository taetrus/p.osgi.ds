package com.kk.pde.ds.mcp.server;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.mcp.api.IMcpTool;
import com.kk.pde.ds.mcp.api.IMcpToolRegistry;

/**
 * MCP (Model Context Protocol) server servlet.
 * Handles JSON-RPC 2.0 requests over HTTP at /mcp.
 *
 * Supported methods:
 * - initialize: Protocol handshake
 * - notifications/initialized: Acknowledgement (no response)
 * - tools/list: List available tools with JSON Schema
 * - tools/call: Execute a tool by name
 */
@Component(
	service = Servlet.class,
	property = {
		HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/mcp",
		HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME + "=McpServlet"
	}
)
public class McpServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(McpServlet.class);

	private static final String PROTOCOL_VERSION = "2024-11-05";
	private static final String SERVER_NAME = "osgi-mcp-server";
	private static final String SERVER_VERSION = "1.0.0";

	private IMcpToolRegistry registry;

	@Activate
	public void activate() {
		LOG.info("McpServlet activated at /mcp");
	}

	@Reference
	public void setRegistry(IMcpToolRegistry registry) {
		this.registry = registry;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		String body = readBody(req);
		LOG.debug("MCP request: {}", body);

		String method = JsonUtil.getString(body, "method");
		String id = JsonUtil.getString(body, "id");

		if (method == null) {
			sendJson(resp, buildError(id, -32600, "Invalid request: missing method"));
			return;
		}

		String result;
		if ("initialize".equals(method)) {
			result = handleInitialize(id);
		} else if ("notifications/initialized".equals(method)) {
			// Notification — no response body
			resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
			return;
		} else if ("tools/list".equals(method)) {
			result = handleToolsList(id);
		} else if ("tools/call".equals(method)) {
			result = handleToolsCall(id, body);
		} else {
			result = buildError(id, -32601, "Method not found: " + method);
		}

		LOG.debug("MCP response: {}", result);
		sendJson(resp, result);
	}

	private String handleInitialize(String id) {
		LOG.info("MCP initialize request received");
		StringBuilder sb = new StringBuilder();
		sb.append("{\"jsonrpc\":\"2.0\",\"id\":").append(id).append(",\"result\":{");
		sb.append("\"protocolVersion\":\"").append(PROTOCOL_VERSION).append("\",");
		sb.append("\"capabilities\":{\"tools\":{}},");
		sb.append("\"serverInfo\":{");
		sb.append("\"name\":\"").append(SERVER_NAME).append("\",");
		sb.append("\"version\":\"").append(SERVER_VERSION).append("\"");
		sb.append("}}}");
		return sb.toString();
	}

	private String handleToolsList(String id) {
		List<IMcpTool> tools = registry.getTools();
		LOG.info("MCP tools/list: {} tools available", tools.size());

		StringBuilder sb = new StringBuilder();
		sb.append("{\"jsonrpc\":\"2.0\",\"id\":").append(id).append(",\"result\":{\"tools\":[");

		for (int i = 0; i < tools.size(); i++) {
			IMcpTool tool = tools.get(i);
			if (i > 0) sb.append(",");
			sb.append("{\"name\":\"").append(JsonUtil.escape(tool.getName())).append("\",");
			sb.append("\"description\":\"").append(JsonUtil.escape(tool.getDescription())).append("\",");
			sb.append("\"inputSchema\":").append(tool.getInputSchema());
			sb.append("}");
		}

		sb.append("]}}");
		return sb.toString();
	}

	private String handleToolsCall(String id, String body) {
		String params = JsonUtil.getObject(body, "params");
		String toolName = JsonUtil.getString(params, "name");

		if (toolName == null) {
			return buildError(id, -32602, "Missing tool name in params");
		}

		IMcpTool tool = registry.getTool(toolName);
		if (tool == null) {
			return buildError(id, -32602, "Unknown tool: " + toolName);
		}

		LOG.info("MCP tools/call: executing '{}'", toolName);

		String argsJson = JsonUtil.getObject(params, "arguments");
		Map<String, String> arguments = JsonUtil.parseFlat(argsJson);

		try {
			String resultText = tool.execute(arguments);

			StringBuilder sb = new StringBuilder();
			sb.append("{\"jsonrpc\":\"2.0\",\"id\":").append(id).append(",\"result\":{");
			sb.append("\"content\":[{\"type\":\"text\",\"text\":\"").append(JsonUtil.escape(resultText)).append("\"}],");
			sb.append("\"isError\":false");
			sb.append("}}");
			return sb.toString();
		} catch (Exception e) {
			LOG.error("Tool execution failed: {}", toolName, e);
			StringBuilder sb = new StringBuilder();
			sb.append("{\"jsonrpc\":\"2.0\",\"id\":").append(id).append(",\"result\":{");
			sb.append("\"content\":[{\"type\":\"text\",\"text\":\"Error: ").append(JsonUtil.escape(e.getMessage())).append("\"}],");
			sb.append("\"isError\":true");
			sb.append("}}");
			return sb.toString();
		}
	}

	private String buildError(String id, int code, String message) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\"jsonrpc\":\"2.0\",\"id\":").append(id != null ? id : "null").append(",");
		sb.append("\"error\":{\"code\":").append(code).append(",");
		sb.append("\"message\":\"").append(JsonUtil.escape(message)).append("\"}}");
		return sb.toString();
	}

	private void sendJson(HttpServletResponse resp, String json) throws IOException {
		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		PrintWriter writer = resp.getWriter();
		writer.print(json);
		writer.flush();
	}

	private String readBody(HttpServletRequest req) throws IOException {
		StringBuilder sb = new StringBuilder();
		String line;
		while ((line = req.getReader().readLine()) != null) {
			sb.append(line);
		}
		return sb.toString();
	}
}
