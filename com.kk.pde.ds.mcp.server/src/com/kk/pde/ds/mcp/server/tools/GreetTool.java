package com.kk.pde.ds.mcp.server.tools;

import java.util.Map;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.kk.pde.ds.api.IGreet;
import com.kk.pde.ds.mcp.api.IMcpTool;

/**
 * MCP tool that invokes the OSGi IGreet service.
 * Demonstrates cross-bundle service integration through MCP.
 */
@Component(service = IMcpTool.class)
public class GreetTool implements IMcpTool {

	private IGreet greetService;

	@Reference
	public void setGreetService(IGreet service) {
		this.greetService = service;
	}

	@Override
	public String getName() {
		return "greet";
	}

	@Override
	public String getDescription() {
		return "Invokes the OSGi IGreet service and returns a greeting";
	}

	@Override
	public String getInputSchema() {
		return "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"Name to greet\"}},\"required\":[]}";
	}

	@Override
	public String execute(Map<String, String> arguments) {
		greetService.greet();
		String name = arguments.get("name");
		if (name != null && !name.isEmpty()) {
			return "Hello, " + name + "!";
		}
		return "Hello from OSGi MCP!";
	}
}
