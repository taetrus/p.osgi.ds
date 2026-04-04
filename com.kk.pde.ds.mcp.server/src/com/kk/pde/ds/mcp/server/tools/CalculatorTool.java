package com.kk.pde.ds.mcp.server.tools;

import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.mcp.api.IMcpTool;

@Component(service = IMcpTool.class)
public class CalculatorTool implements IMcpTool {

	private static final Logger LOG = LoggerFactory.getLogger(CalculatorTool.class);

	@Activate
	public void activate() {
		LOG.info("CalculatorTool activated");
	}

	@Override
	public String getName() {
		return "calculator";
	}

	@Override
	public String getDescription() {
		return "Evaluates simple math: add, subtract, multiply, divide two numbers";
	}

	@Override
	public String getInputSchema() {
		return "{\"type\":\"object\",\"properties\":{" +
			"\"a\":{\"type\":\"string\",\"description\":\"First number\"}," +
			"\"b\":{\"type\":\"string\",\"description\":\"Second number\"}," +
			"\"operation\":{\"type\":\"string\",\"description\":\"One of: add, subtract, multiply, divide\"}" +
			"},\"required\":[\"a\",\"b\",\"operation\"]}";
	}

	@Override
	public String execute(Map<String, String> arguments) {
		String aStr = arguments.get("a");
		String bStr = arguments.get("b");
		String op = arguments.get("operation");

		if (aStr == null || bStr == null || op == null) {
			return "Error: a, b, and operation are all required";
		}

		double a, b;
		try {
			a = Double.parseDouble(aStr);
			b = Double.parseDouble(bStr);
		} catch (NumberFormatException e) {
			return "Error: invalid number: " + e.getMessage();
		}

		double result;
		switch (op.toLowerCase()) {
			case "add":
				result = a + b;
				break;
			case "subtract":
				result = a - b;
				break;
			case "multiply":
				result = a * b;
				break;
			case "divide":
				if (b == 0) {
					return "Error: division by zero";
				}
				result = a / b;
				break;
			default:
				return "Error: unknown operation '" + op + "'. Use: add, subtract, multiply, divide";
		}

		// Format nicely: show integer if whole number
		if (result == Math.floor(result) && !Double.isInfinite(result)) {
			return String.valueOf((long) result);
		}
		return String.valueOf(result);
	}
}
