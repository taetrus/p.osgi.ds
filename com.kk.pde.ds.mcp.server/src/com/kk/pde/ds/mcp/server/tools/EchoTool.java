package com.kk.pde.ds.mcp.server.tools;

import java.util.Map;

import org.osgi.service.component.annotations.Component;

import com.kk.pde.ds.mcp.api.IMcpTool;

/**
 * Simple echo tool — returns whatever message is sent to it.
 * Useful for testing MCP connectivity.
 */
@Component(service = IMcpTool.class)
public class EchoTool implements IMcpTool {

	@Override
	public String getName() {
		return "echo";
	}

	@Override
	public String getDescription() {
		return "Echoes back the provided message";
	}

	@Override
	public String getInputSchema() {
		return "{\"type\":\"object\",\"properties\":{\"message\":{\"type\":\"string\",\"description\":\"Message to echo back\"}},\"required\":[\"message\"]}";
	}

	@Override
	public String execute(Map<String, String> arguments) {
		String message = arguments.get("message");
		return "Echo: " + (message != null ? message : "(empty)");
	}
}
