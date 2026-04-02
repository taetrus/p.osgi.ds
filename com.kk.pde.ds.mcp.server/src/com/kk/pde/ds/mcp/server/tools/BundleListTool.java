package com.kk.pde.ds.mcp.server.tools;

import java.util.Map;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import com.kk.pde.ds.mcp.api.IMcpTool;

@Component(service = IMcpTool.class)
public class BundleListTool implements IMcpTool {

	private BundleContext bundleContext;

	@Activate
	public void activate(BundleContext context) {
		this.bundleContext = context;
	}

	@Override
	public String getName() {
		return "bundle_list";
	}

	@Override
	public String getDescription() {
		return "Lists all installed OSGi bundles with their states";
	}

	@Override
	public String getInputSchema() {
		return "{\"type\":\"object\",\"properties\":{\"filter\":{\"type\":\"string\",\"description\":\"Optional substring to filter bundle symbolic names\"}},\"required\":[]}";
	}

	@Override
	public String execute(Map<String, String> arguments) {
		if (bundleContext == null) {
			return "Error: BundleContext not available";
		}

		String filter = arguments.get("filter");
		Bundle[] bundles = bundleContext.getBundles();
		StringBuilder sb = new StringBuilder();
		int count = 0;

		for (Bundle bundle : bundles) {
			String name = bundle.getSymbolicName();
			if (filter != null && !filter.isEmpty()
					&& (name == null || !name.toLowerCase().contains(filter.toLowerCase()))) {
				continue;
			}
			if (count > 0) sb.append("\n");
			sb.append(String.format("[%3d] %-50s %s %s",
				bundle.getBundleId(),
				name != null ? name : "(no name)",
				bundle.getVersion(),
				getStateString(bundle.getState())));
			count++;
		}

		sb.insert(0, count + " bundles" + (filter != null ? " matching '" + filter + "'" : "") + ":\n");
		return sb.toString();
	}

	private String getStateString(int state) {
		switch (state) {
			case Bundle.UNINSTALLED: return "UNINSTALLED";
			case Bundle.INSTALLED:   return "INSTALLED";
			case Bundle.RESOLVED:    return "RESOLVED";
			case Bundle.STARTING:    return "STARTING";
			case Bundle.STOPPING:    return "STOPPING";
			case Bundle.ACTIVE:      return "ACTIVE";
			default:                 return "UNKNOWN(" + state + ")";
		}
	}
}
