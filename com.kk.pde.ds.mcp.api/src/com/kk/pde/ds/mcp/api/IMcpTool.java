package com.kk.pde.ds.mcp.api;

import java.util.Map;

/**
 * Contract for an MCP tool that can be registered with the tool registry.
 * Any bundle can provide an IMcpTool service to make it available via MCP.
 */
public interface IMcpTool {

	/** Unique tool name (e.g. "echo", "greet", "system_info"). */
	String getName();

	/** Human-readable description shown in tools/list. */
	String getDescription();

	/**
	 * Returns the JSON Schema for this tool's input parameters as a raw JSON string.
	 * Example: {"type":"object","properties":{"message":{"type":"string"}},"required":["message"]}
	 */
	String getInputSchema();

	/**
	 * Execute the tool with the given arguments.
	 *
	 * @param arguments parsed key-value pairs from the JSON-RPC params.arguments
	 * @return result text to include in the MCP response content
	 */
	String execute(Map<String, String> arguments);
}
