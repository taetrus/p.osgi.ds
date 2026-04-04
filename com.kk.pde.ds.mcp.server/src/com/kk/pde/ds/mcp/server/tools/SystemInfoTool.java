package com.kk.pde.ds.mcp.server.tools;

import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.mcp.api.IMcpTool;

/**
 * MCP tool that returns JVM and OS system information.
 */
@Component(service = IMcpTool.class)
public class SystemInfoTool implements IMcpTool {

	private static final Logger LOG = LoggerFactory.getLogger(SystemInfoTool.class);

	@Activate
	public void activate() {
		LOG.info("SystemInfoTool activated");
	}

	@Override
	public String getName() {
		return "system_info";
	}

	@Override
	public String getDescription() {
		return "Returns JVM and OS system information";
	}

	@Override
	public String getInputSchema() {
		return "{\"type\":\"object\",\"properties\":{},\"required\":[]}";
	}

	@Override
	public String execute(Map<String, String> arguments) {
		Runtime rt = Runtime.getRuntime();
		return "Java: " + System.getProperty("java.version")
			+ ", OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version")
			+ ", Arch: " + System.getProperty("os.arch")
			+ ", Heap: " + (rt.totalMemory() / 1024 / 1024) + "MB"
			+ ", Free: " + (rt.freeMemory() / 1024 / 1024) + "MB";
	}
}
