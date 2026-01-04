package com.kk.pde.ds.imp;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.kk.pde.ds.api.IGreet;

@Component
public class Greet implements IGreet {

	private static Logger log = LoggerFactory.getLogger(Greet.class);

	@Activate
	public void start() {
		log.info("Greet.start()");
	}

	@Override
	public void greet() {
		log.info("Hello world!");
	}

}
