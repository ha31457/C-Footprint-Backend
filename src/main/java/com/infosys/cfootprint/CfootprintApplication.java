package com.infosys.cfootprint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CfootprintApplication {

	public static void main(String[] args) {
		// Load .env file programmatically into System properties
		java.io.File envFile = new java.io.File(".env");
		if (envFile.exists()) {
			try {
				java.nio.file.Files.lines(envFile.toPath())
					.map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#"))
					.forEach(line -> {
						int index = line.indexOf("=");
						if (index > 0) {
							String key = line.substring(0, index).trim();
							String value = line.substring(index + 1).trim();
							// Strip outer quotes if present
							if (value.startsWith("\"") && value.endsWith("\"")) {
								value = value.substring(1, value.length() - 1);
							}
							if (value.startsWith("'") && value.endsWith("'")) {
								value = value.substring(1, value.length() - 1);
							}
							System.setProperty(key, value);
						}
					});
			} catch (Exception e) {
				System.err.println("Failed to load .env file: " + e.getMessage());
			}
		}

		SpringApplication.run(CfootprintApplication.class, args);
	}

}
