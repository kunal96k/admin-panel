package com.tts.sms;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * TechnoKraft Student Management System
 * Main Application Entry Point
 *
 * @author TechnoKraft Services LLP
 * @version 1.0.0
 */
@Slf4j
@SpringBootApplication
public class SmsApplication {

	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(SmsApplication.class);
		Environment env = app.run(args).getEnvironment();

		logApplicationStartup(env);
	}

	/**
	 * Log application startup information
	 */
	private static void logApplicationStartup(Environment env) {
		String protocol = env.getProperty("server.ssl.enabled", "false").equals("true")
				? "https" : "http";
		String serverPort = env.getProperty("server.port");
		String contextPath = env.getProperty("server.servlet.context-path", "/");
		String hostAddress = "localhost";

		try {
			hostAddress = InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			log.warn("Unable to determine host address", e);
		}

		log.info("\n----------------------------------------------------------\n" +
						"Application '{}' is running!\n" +
						"Profile(s): {}\n" +
						"Access URLs:\n" +
						"  Local:      {}://localhost:{}{}\n" +
						"  External:   {}://{}:{}{}\n" +
						"----------------------------------------------------------",
				env.getProperty("spring.application.name"),
				env.getActiveProfiles().length == 0 ? "default" : String.join(", ", env.getActiveProfiles()),
				protocol, serverPort, contextPath,
				protocol, hostAddress, serverPort, contextPath
		);
	}
}