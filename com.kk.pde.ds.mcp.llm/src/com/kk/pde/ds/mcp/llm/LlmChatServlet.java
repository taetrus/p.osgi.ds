package com.kk.pde.ds.mcp.llm;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.whiteboard.HttpWhiteboardConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP endpoint at POST /llm/chat that triggers an OpenRouter agent conversation.
 *
 * Request body (JSON):
 *   {"message": "your prompt", "model": "optional-model-override"}
 *
 * Response (JSON):
 *   {"response": "LLM answer after tool calls"}
 *
 * The agent automatically uses all tools registered in the OSGi MCP tool registry.
 */
@Component(
	service = Servlet.class,
	property = {
		HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_PATTERN + "=/llm/chat",
		HttpWhiteboardConstants.HTTP_WHITEBOARD_SERVLET_NAME + "=LlmChatServlet"
	}
)
public class LlmChatServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;
	private static final Logger LOG = LoggerFactory.getLogger(LlmChatServlet.class);

	private OpenRouterAgent agent;

	@Activate
	public void activate() {
		LOG.info("LlmChatServlet activated at /llm/chat");
	}

	@Reference
	public void setAgent(OpenRouterAgent agent) {
		this.agent = agent;
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {

		String body = readBody(req);
		String message = LlmJsonUtil.getString(body, "message");
		String model = LlmJsonUtil.getString(body, "model");

		if (message == null || message.trim().isEmpty()) {
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST,
				"Request body must be JSON with a 'message' field");
			return;
		}

		LOG.info("LLM chat request: {}", message);
		String response = agent.chat(message, model);

		resp.setContentType("application/json");
		resp.setCharacterEncoding("UTF-8");
		PrintWriter writer = resp.getWriter();
		writer.print("{\"response\":\"" + LlmJsonUtil.escape(response) + "\"}");
		writer.flush();
	}

	private String readBody(HttpServletRequest req) throws IOException {
		StringBuilder sb = new StringBuilder();
		String line;
		while ((line = req.getReader().readLine()) != null) {
			sb.append(line);
		}
		return sb.toString();
	}
}
