/**
 * ShinyProxy
 *
 * Copyright (C) 2016-2017 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.services;

import com.spotify.docker.client.LogStream;
import eu.openanalytics.domain.Proxy;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.mail.internet.MimeMessage;
import org.apache.log4j.Logger;
import org.bouncycastle.util.encoders.Hex;
import org.h2.util.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
public class LogService {

	private String supportAddress;
	private String containerPath;
	private ExecutorService executor;
	
	private Logger log = Logger.getLogger(LogService.class);

	private Random rng = new Random();
	
	@Autowired(required=false)
    JavaMailSender mailSender;
	
	@Inject
	DockerService dockerService;
	
	@Inject
	Environment environment;
	
	@PostConstruct
	public void init() {
		containerPath = environment.getProperty("shiny.proxy.support.container-log-path");
		if (containerPath != null) {
			try {
				Files.createDirectories(Paths.get(containerPath));
				executor = Executors.newCachedThreadPool();
				log.info("Container logging enabled. Logs will be saved to " + containerPath);
			} catch (IOException e) {
				log.error("Failed to initialize container logging directory at " + containerPath, e);
			}
		}
		
		supportAddress = environment.getProperty("shiny.proxy.support.mail-to-address");
	}
	
	@PreDestroy
	public void shutdown() {
		if (executor != null) executor.shutdown();
	}

	public boolean isContainerLoggingEnabled() {
		return containerPath != null && executor != null;
	}
	
	public boolean isReportingEnabled() {
		return supportAddress != null && mailSender != null;
	}

	public void attachLogWriter(Proxy proxy, LogStream logStream) {
		if (!isContainerLoggingEnabled()) return;
		executor.submit(() -> {
			try {
				Path[] paths = getLogFilePaths(proxy.getContainerId());
				// Note that this call will block until the container is stopped.
				logStream.attach(new FileOutputStream(paths[0].toFile()), new FileOutputStream(paths[1].toFile()));
			} catch (IOException e) {
				log.error("Failed to attach logging of container " + proxy.getContainerId(), e);
			}
		});
	}
	
	public void attachLogWatcher(Proxy proxy, LogWatch watcher) {
		if (!isContainerLoggingEnabled()) return;
		executor.submit(() -> {
			try {
				byte[] logIdBytes = new byte[20];
				rng.nextBytes(logIdBytes);
				String logId = Hex.toHexString(logIdBytes);
				log.info(String.format("Assigning container %s log file id %s", proxy.getName(), logId));
				// Note that this call will block until the container is stopped.
				IOUtils.copy(watcher.getOutput(), new FileOutputStream(Paths.get(containerPath, logId + ".log").toFile()));
			} catch (IOException e) {
				log.error("Failed to attach logging of container " + proxy.getContainerId(), e);
			}
		});
	}
	
	public void sendSupportMail(IssueForm form) {
		if (!isReportingEnabled()) {
			log.error("Cannot send support mail: no address is configured");
			return;
		}
		
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true);

			// Headers
			
			helper.setFrom(environment.getProperty("shiny.proxy.support.mail-from-address", "issues@shinyproxy.io"));
			helper.addTo(supportAddress);
			helper.setSubject("ShinyProxy Error Report");

			// Body
			
			StringBuilder body = new StringBuilder();
			String lineSep = System.getProperty("line.separator");
			body.append(String.format("This is an error report generated by ShinyProxy%s", lineSep));
			body.append(String.format("User: %s%s", form.userName, lineSep));
			if (form.appName != null) body.append(String.format("App: %s%s", form.appName, lineSep));
			if (form.currentLocation != null) body.append(String.format("Location: %s%s", form.currentLocation, lineSep));
			if (form.customMessage != null) body.append(String.format("Message: %s%s", form.customMessage, lineSep));
			helper.setText(body.toString());

			// Attachments
			
			if (isContainerLoggingEnabled()) {
				Proxy activeProxy = null;
				for (Proxy proxy: dockerService.listProxies()) {
					if (proxy.getUserName().equals(form.getUserName()) && proxy.getAppName().equals(form.getAppName())) {
						activeProxy = proxy;
						break;
					}
				}
				if (activeProxy != null) {
					Path[] filePaths = getLogFilePaths(activeProxy.getContainerId());
					for (Path p: filePaths) {
						if (Files.exists(p)) helper.addAttachment(p.toFile().getName(), p.toFile());
					}
				}
			}
			
			mailSender.send(message);
		} catch (Exception e) {
			throw new RuntimeException("Failed to send email", e);
		}
	}
	
	private Path[] getLogFilePaths(String containerId) {
		return new Path[] {
			Paths.get(containerPath, containerId + "_stdout.log"),
			Paths.get(containerPath, containerId + "_stderr.log")
		};
	}
	
	public static class IssueForm {
		
		private String userName;
		private String appName;
		private String currentLocation;
		private String customMessage;
		
		public String getUserName() {
			return userName;
		}
		public void setUserName(String userName) {
			this.userName = userName;
		}
		public String getAppName() {
			return appName;
		}
		public void setAppName(String appName) {
			this.appName = appName;
		}
		public String getCurrentLocation() {
			return currentLocation;
		}
		public void setCurrentLocation(String currentLocation) {
			this.currentLocation = currentLocation;
		}
		public String getCustomMessage() {
			return customMessage;
		}
		public void setCustomMessage(String customMessage) {
			this.customMessage = customMessage;
		}
	}
}
