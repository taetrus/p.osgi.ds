package com.kk.pde.ds.mcp.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP client that connects to the MCP server over HTTP,
 * performs the protocol handshake, lists tools, and calls each one.
 *
 * Communicates purely via HTTP/JSON-RPC — no OSGi service imports from the server.
 */
@Component
public class McpClient {

	private static final Logger LOG = LoggerFactory.getLogger(McpClient.class);
	private static final String DEFAULT_URL = "http://localhost:8080/mcp";

	@Activate
	public void start() {
		final String serverUrl = System.getProperty("mcp.server.url", DEFAULT_URL);
		LOG.info("MCP Client starting, server URL: {}", serverUrl);

		// Run on a daemon thread to avoid blocking SCR activation
		// and to give the HTTP server time to register the servlet
		Thread clientThread = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(3000);
					runProtocol(serverUrl);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					LOG.warn("MCP Client interrupted");
				} catch (Exception e) {
					LOG.error("MCP Client failed", e);
				}
			}
		}, "mcp-client");
		clientThread.setDaemon(true);
		clientThread.start();
	}

	private void runProtocol(String serverUrl) {
		LOG.info("=== MCP Client Protocol Run ===");

		// 1. Initialize
		String initReq = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
			+ "\"params\":{\"protocolVersion\":\"2024-11-05\","
			+ "\"capabilities\":{},"
			+ "\"clientInfo\":{\"name\":\"osgi-mcp-client\",\"version\":\"1.0.0\"}}}";
		String initResp = post(serverUrl, initReq);
		LOG.info("Initialize response: {}", initResp);

		if (initResp == null) {
			LOG.error("Failed to connect to MCP server at {}", serverUrl);
			return;
		}

		// 2. Send initialized notification
		String notifReq = "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}";
		post(serverUrl, notifReq);
		LOG.info("Sent initialized notification");

		// 3. List tools
		String listReq = "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}";
		String listResp = post(serverUrl, listReq);
		LOG.info("Tools list: {}", listResp);

		// 4. Call echo tool
		String echoReq = "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\","
			+ "\"params\":{\"name\":\"echo\",\"arguments\":{\"message\":\"Hello from MCP client!\"}}}";
		String echoResp = post(serverUrl, echoReq);
		LOG.info("Echo result: {}", echoResp);

		// 5. Call greet tool
		String greetReq = "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\","
			+ "\"params\":{\"name\":\"greet\",\"arguments\":{\"name\":\"OSGi\"}}}";
		String greetResp = post(serverUrl, greetReq);
		LOG.info("Greet result: {}", greetResp);

		// 6. Call system_info tool
		String sysReq = "{\"jsonrpc\":\"2.0\",\"id\":5,\"method\":\"tools/call\","
			+ "\"params\":{\"name\":\"system_info\",\"arguments\":{}}}";
		String sysResp = post(serverUrl, sysReq);
		LOG.info("System info result: {}", sysResp);

		LOG.info("=== MCP Client Protocol Run Complete ===");
	}

	private String post(String urlStr, String jsonBody) {
		HttpURLConnection conn = null;
		try {
			URL url = new URL(urlStr);
			conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/json");
			conn.setRequestProperty("Accept", "application/json");
			conn.setDoOutput(true);
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(10000);

			OutputStream os = conn.getOutputStream();
			os.write(jsonBody.getBytes("UTF-8"));
			os.flush();
			os.close();

			int status = conn.getResponseCode();
			if (status == 204) {
				return ""; // No content (notification response)
			}

			BufferedReader reader = new BufferedReader(
				new InputStreamReader(conn.getInputStream(), "UTF-8"));
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = reader.readLine()) != null) {
				sb.append(line);
			}
			reader.close();
			return sb.toString();

		} catch (IOException e) {
			LOG.error("HTTP POST failed: {}", e.getMessage());
			return null;
		} finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}
}
