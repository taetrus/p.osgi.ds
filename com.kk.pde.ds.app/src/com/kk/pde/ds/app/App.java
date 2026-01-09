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
	private RestApiExample restApi;

	public App() {
		log.info("App.App()");
	}

	@Reference
	public void setApi(IGreet greet) {
		log.info("App.setApi()");
		this.greet = greet;
	}

	@Reference
	public void setRestApi(RestApiExample restApi) {
		log.info("App.setRestApi()");
		this.restApi = restApi;
	}

	@Activate
	public void start() {
		log.info("App.start()");

		greet.greet();
		logRestApiExample();
	}

	private void logRestApiExample() {
		String endpoint = "https://api.example.com/v1/status";
		log.info("REST usage example: restApi.fetchJson(\"{}\")", endpoint);
		log.debug("REST API service injected: {}", restApi != null);
	}
}
