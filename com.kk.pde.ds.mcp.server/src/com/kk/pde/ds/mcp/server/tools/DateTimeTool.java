package com.kk.pde.ds.mcp.server.tools;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.osgi.service.component.annotations.Component;

import com.kk.pde.ds.mcp.api.IMcpTool;

@Component(service = IMcpTool.class)
public class DateTimeTool implements IMcpTool {

	@Override
	public String getName() {
		return "date_time";
	}

	@Override
	public String getDescription() {
		return "Returns current date, time, and timezone. Accepts optional SimpleDateFormat pattern.";
	}

	@Override
	public String getInputSchema() {
		return "{\"type\":\"object\",\"properties\":{\"format\":{\"type\":\"string\",\"description\":\"SimpleDateFormat pattern (default: yyyy-MM-dd HH:mm:ss z)\"}},\"required\":[]}";
	}

	@Override
	public String execute(Map<String, String> arguments) {
		String format = arguments.get("format");
		if (format == null || format.isEmpty()) {
			format = "yyyy-MM-dd HH:mm:ss z";
		}
		try {
			return new SimpleDateFormat(format).format(new Date());
		} catch (IllegalArgumentException e) {
			return "Error: invalid format pattern '" + format + "': " + e.getMessage();
		}
	}
}
