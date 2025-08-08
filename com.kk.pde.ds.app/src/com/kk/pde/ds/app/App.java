package com.kk.pde.ds.app;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import com.kk.pde.ds.api.IGreet;

@Component
public class App {

	private IGreet greet;

	public App() {
		System.out.println("App.App()");
	}

	@Reference
	public void setApi(IGreet greet) {
		System.out.println("App.setApi()");
		this.greet = greet;
	}

	@Activate
	public void start() {
		System.out.println("App.start()");

		greet.greet();
	}
}
