package com.kk.pde.ds.app;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.api.IGreet;

@Component
public class App {

	private static Logger log = LoggerFactory.getLogger(App.class);

	private IGreet greet;
	private String mcpJson;

	public App() {
		log.info("App.App()");
	}

	@Reference
	public void setApi(IGreet greet) {
		log.info("App.setApi()");
		this.greet = greet;
	}

	@Activate
	public void start() {
		log.info("App.start()");

		mcpJson = McpSettings.load(log);

		greet.greet();
	}
}
