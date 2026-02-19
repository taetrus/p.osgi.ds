package com.kk.pde.ds.mcp.api;

import java.util.List;

/**
 * Registry that collects all available IMcpTool services.
 * The MCP server consults this to handle tools/list and tools/call.
 */
public interface IMcpToolRegistry {

	/** Return all currently registered tools. */
	List<IMcpTool> getTools();

	/** Find a tool by name, or null if not found. */
	IMcpTool getTool(String name);
}
