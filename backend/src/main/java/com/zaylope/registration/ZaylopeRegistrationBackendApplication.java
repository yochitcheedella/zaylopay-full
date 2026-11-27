package com.zaylope.registration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.SpringApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ZaylopeRegistrationBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ZaylopeRegistrationBackendApplication.class, args);
	}

}
