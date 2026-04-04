package com.kk.pde.ds.mcp.server;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.mcp.api.IMcpTool;
import com.kk.pde.ds.mcp.api.IMcpToolRegistry;

/**
 * Dynamic tool registry that collects all IMcpTool services in the OSGi runtime.
 * Tools can come and go at runtime — the registry tracks them automatically.
 */
@Component(service = IMcpToolRegistry.class)
public class McpToolRegistry implements IMcpToolRegistry {

	private static final Logger LOG = LoggerFactory.getLogger(McpToolRegistry.class);

	private final List<IMcpTool> tools = new CopyOnWriteArrayList<IMcpTool>();

	@Activate
	public void activate() {
		LOG.info("McpToolRegistry activated");
	}

	@Reference(cardinality = ReferenceCardinality.MULTIPLE, policy = ReferencePolicy.DYNAMIC)
	public void addTool(IMcpTool tool) {
		tools.add(tool);
		LOG.info("MCP tool registered: {}", tool.getName());
	}

	public void removeTool(IMcpTool tool) {
		tools.remove(tool);
		LOG.info("MCP tool unregistered: {}", tool.getName());
	}

	@Override
	public List<IMcpTool> getTools() {
		return tools;
	}

	@Override
	public IMcpTool getTool(String name) {
		for (IMcpTool tool : tools) {
			if (tool.getName().equals(name)) {
				return tool;
			}
		}
		return null;
	}
}
