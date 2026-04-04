package com.kk.pde.ds.mcp.server.tools;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.mcp.api.IMcpTool;

@Component(service = IMcpTool.class)
public class DateTimeTool implements IMcpTool {

	private static final Logger LOG = LoggerFactory.getLogger(DateTimeTool.class);

	@Activate
	public void activate() {
		LOG.info("DateTimeTool activated");
	}

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
