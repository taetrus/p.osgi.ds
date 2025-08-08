package com.kk.pde.ds.imp;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;

import com.kk.pde.ds.api.IGreet;

@Component
public class Greet implements IGreet {

	@Activate
	public void start() {
		System.out.println("Greet.start()");
	}

	@Override
	public void greet() {
		System.out.println("Hello world!");
	}

}
