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

import com.esotericsoftware.yamlbeans.YamlException;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IList;
import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerCertificates;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.LogsParam;
import com.spotify.docker.client.DockerClient.RemoveContainerParam;
import com.spotify.docker.client.LogStream;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.HostConfig.Builder;
import com.spotify.docker.client.messages.PortBinding;
import com.spotify.docker.client.messages.mount.Mount;
import com.spotify.docker.client.messages.swarm.ContainerSpec;
import com.spotify.docker.client.messages.swarm.DnsConfig;
import com.spotify.docker.client.messages.swarm.EndpointSpec;
import com.spotify.docker.client.messages.swarm.NetworkAttachmentConfig;
import com.spotify.docker.client.messages.swarm.Node;
import com.spotify.docker.client.messages.swarm.PortConfig;
import com.spotify.docker.client.messages.swarm.ServiceSpec;
import com.spotify.docker.client.messages.swarm.Task;
import com.spotify.docker.client.messages.swarm.TaskSpec;
import eu.openanalytics.HazelcastConfiguration;
import eu.openanalytics.ShinyProxyException;
import eu.openanalytics.domain.Apple;
import eu.openanalytics.domain.Proxy;
import eu.openanalytics.services.AppService.ShinyApp;
import eu.openanalytics.services.EventService.EventType;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.Cookie;
import io.undertow.servlet.handlers.ServletRequestContext;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.util.WebUtils;

@Service
@Component
public class DockerService {

//	@Autowired
//	private ClientConfig clientConfig;
	/*@Autowired
    private HazelcastInstance hz = Hazelcast.newHazelcastInstance();*/
	@Autowired
	private HazelcastInstance hz ;

	private ConcurrentMap<Integer, Proxy> _launchingProxiesMap = null;
	private ConcurrentMap<Integer, Proxy> launchingProxiesMap (){
		if (null == _launchingProxiesMap){
			_launchingProxiesMap = hz.getMap("launchingProxiesMap");
		}
		return _launchingProxiesMap;
	}

	private ConcurrentMap<Integer, Proxy> _activeProxiesMap = null;
	private ConcurrentMap<Integer, Proxy> activeProxiesMap(){
		if (null == _activeProxiesMap){
			_activeProxiesMap = hz.getMap("activeProxiesMap");
		}

		return _activeProxiesMap;
	}

	private Map<String,String> _activeProxySessionId = null;
	private Map<String,String> activeProxySessionId(){
		if (null == _activeProxySessionId){
			_activeProxySessionId = Collections.synchronizedMap(hz.getMap("activeProxySessionId"));
		}

		return _activeProxySessionId;
	}

	private Set<Integer> _occupiedPorts = null;
	private Set<Integer> occupiedPorts(){
		if (null == _occupiedPorts){
			_occupiedPorts = Collections.synchronizedSet(hz.getSet("occupiedPorts"));
		}

		return _occupiedPorts;
	}

	private IList<Apple> launchingApple;
	private IList<Apple> activeApple;





//	public  DockerService(){
//		System.out.println("============CROT");
//		ClientConfig clientConfig1 = new ClientConfig();
//		System.out.println("in docker service client config");
//		ClientNetworkConfig network = clientConfig1.getNetworkConfig();
//		String hosts = null;
//		try {
//			hosts = new HazelcastConfiguration().hcHosts();
//		} catch (YamlException e) {
//			e.printStackTrace();
//		} catch (FileNotFoundException e) {
//			e.printStackTrace();
//		}
//		String[] split = hosts.split(",");
//		network.addAddress(split).setConnectionTimeout(1000);;
//		clientConfig1.getNetworkConfig().setSmartRouting(true);
//		hz = HazelcastClient.newHazelcastClient(clientConfig1);


//	launchingProxiesMap = hz.getMap("launchingProxiesMap");
//	activeProxiesMap = hz.getMap("activeProxiesMap");
//	launchingApple = hz.getList("launchingApple");
//	activeApple= hz.getList("activeApple");
//	activeProxySessionId = Collections.synchronizedMap(hz.getMap("activeProxySessionId"));
//	occupiedPorts = Collections.synchronizedSet(hz.getSet("occupiedPorts"));
//
//	}
	private List<MappingListener> mappingListeners = Collections.synchronizedList(new ArrayList<>());

	private Logger log = Logger.getLogger(DockerService.class);
	private Random rng = new Random();


	private ExecutorService containerKiller = Executors.newSingleThreadExecutor();

	private boolean swarmMode = false;
	private boolean kubernetes = false;
	Apple a;

	@Inject
	Environment environment;

	@Inject
	AppService appService;

	@Inject
	UserService userService;

	@Inject
	EventService eventService;

	@Inject
	LogService logService;

	@Inject
	DockerClient dockerClient;

	@Inject
	KubernetesClient kubeClient;

	public void addAndRemoveappleApple(){
		a = new Apple();
		a.setName("green");
		launchingApple.add(a);
		a.setId(100);
		activeApple.add(a);
		launchingApple.remove(a);
		//launchingApple.
		for (Apple l : launchingApple){
			System.out.println(l.getName());
			if(l.getName().equalsIgnoreCase("green"))
			{
				launchingApple.remove(l);
			}

		}

		for (Apple l : launchingApple){
			System.out.println(l.getName());


		}
		for (Apple l : activeApple){
			System.out.println(l.getName());


		}

	}

	public void addElementToList(){
    //HazelcastInstance hz = Hazelcast.newHazelcastInstance();
    IList<String> list = hz.getList("list");
    list.add("Tokyo");
    list.add("Paris");
    list.add("London");
    list.add("New York");
    System.out.println("Putting finished!");
  }

  public  void getElement(){
    IList<String> list = hz.getList("list");
    for (String s : list) {
      System.out.println(s);
    }
    System.out.println("Reading finished!");
  }

	/*public static class Proxy {

		public String name;
		public String protocol;
		public String host;
		public int port;
		public String containerId;
		public String serviceId;
		public String userName;
		public String appName;
		public Set<String> sessionIds = new HashSet<>();
		public long startupTimestamp;
		public Long lastHeartbeatTimestamp;
		public Pod kubePod;

		public String uptime() {
			long uptimeSec = (System.currentTimeMillis() - startupTimestamp)/1000;
			return String.format("%d:%02d:%02d", uptimeSec/3600, (uptimeSec%3600)/60, uptimeSec%60);
		}

		public Proxy copyInto(Proxy target) {
			target.name = this.name;
			target.protocol = this.protocol;
			target.host = this.host;
			target.port = this.port;
			target.containerId = this.containerId;
			target.serviceId = this.serviceId;
			target.userName = this.userName;
			target.appName = this.appName;
			target.sessionIds = this.sessionIds;
			target.startupTimestamp = this.startupTimestamp;
			return target;
		}
	}*/

	/*public static class Proxy implements Serializable {

		//private HazelcastInstance hz = Hazelcast.newHazelcastInstance();
		private String name;
		private String protocol;
		private String host;
		private int port;
		private String containerId;
		private String serviceId;
		private String userName;
		private String appName;
		// public Set<String> sessionIds = hz.getSet("sessionIds");
		private Set<String> sessionIds = new HashSet<>();
		private long startupTimestamp;
		private Long lastHeartbeatTimestamp;
		private Pod kubePod;

		public String uptime() {
			long uptimeSec = (System.currentTimeMillis() - startupTimestamp) / 1000;
			return String.format("%d:%02d:%02d", uptimeSec / 3600, (uptimeSec % 3600) / 60, uptimeSec % 60);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			Proxy proxy = (Proxy) o;
			return port == proxy.port &&
					Objects.equals(userName, proxy.userName) &&
					Objects.equals(appName, proxy.appName);
		}

		@Override
		public int hashCode() {

			return Objects.hash(port, userName, appName);
		}

		public eu.openanalytics.services.DockerService.Proxy copyInto(eu.openanalytics.services.DockerService.Proxy target) {
			target.name = this.name;
			target.protocol = this.protocol;
			target.host = this.host;
			target.port = this.port;
			target.containerId = this.containerId;
			target.serviceId = this.serviceId;
			target.userName = this.userName;
			target.appName = this.appName;
			// target.sessionIds = this.sessionIds;
			target.startupTimestamp = this.startupTimestamp;
			return target;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getProtocol() {
			return protocol;
		}

		public void setProtocol(String protocol) {
			this.protocol = protocol;
		}

		public String getHost() {
			return host;
		}

		public void setHost(String host) {
			this.host = host;
		}

		public int getPort() {
			return port;
		}

		public void setPort(int port) {
			this.port = port;
		}

		public String getContainerId() {
			return containerId;
		}

		public void setContainerId(String containerId) {
			this.containerId = containerId;
		}

		public String getServiceId() {
			return serviceId;
		}

		public void setServiceId(String serviceId) {
			this.serviceId = serviceId;
		}

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

		public Set<String> getSessionIds() {
			return sessionIds;
		}

		public void setSessionIds(Set<String> sessionIds) {
			this.sessionIds = sessionIds;
		}

		public long getStartupTimestamp() {
			return startupTimestamp;
		}

		public void setStartupTimestamp(long startupTimestamp) {
			this.startupTimestamp = startupTimestamp;
		}

		public Long getLastHeartbeatTimestamp() {
			return lastHeartbeatTimestamp;
		}

		public void setLastHeartbeatTimestamp(Long lastHeartbeatTimestamp) {
			this.lastHeartbeatTimestamp = lastHeartbeatTimestamp;
		}

		public Pod getKubePod() {
			return kubePod;
		}

		public void setKubePod(Pod kubePod) {
			this.kubePod = kubePod;
		}
	}*/

	@PostConstruct
	public void init() {
    hz = Hazelcast.newHazelcastInstance();

		if (kubernetes) {
			log.info("Kubernetes is enabled");
      startCleanUpThread();
			return;
		}

		try {
			swarmMode = (dockerClient.inspectSwarm().id() != null);
		} catch (DockerException | InterruptedException e) {}
		log.info(String.format("Swarm mode is %s", (swarmMode ? "enabled" : "disabled")));

    startCleanUpThread();
	}

	private void startCleanUpThread(){
    System.out.println("Starting clean up thread");
     log.info("Starting clean up thread");
    Thread heartbeatThread = new Thread(new AppCleaner(), "HeartbeatThread");
    heartbeatThread.setDaemon(true);
    heartbeatThread.start();
  }

	@PreDestroy
	public void shutdown() {
		containerKiller.shutdown();
		List<Proxy> proxiesToRelease = new ArrayList<>();
		ConcurrentMap<Integer, Proxy> proxies = activeProxiesMap();
		synchronized (proxies) {
			proxies.keySet().stream().forEach(s->proxiesToRelease.add(proxies.get(s)));
			//proxiesToRelease.addAll(activeProxies);
		}
		for (Proxy proxy: proxiesToRelease) releaseProxy(proxy, false);
	}

	@Bean KubernetesClient getKubeClient() {
		kubernetes = "true".equals(environment.getProperty("shiny.proxy.docker.kubernetes"));
		if (!kubernetes) return null;
		ConfigBuilder configBuilder = new ConfigBuilder();
		String masterUrl = environment.getProperty("shiny.proxy.docker.kubernetes-url");
		if (masterUrl != null) {
			configBuilder.withMasterUrl(masterUrl);
		}
		return new DefaultKubernetesClient(configBuilder.build());
	}

	@Bean
	@DependsOn("getKubeClient") // for kubernetes boolean
	public DockerClient getDockerClient() {
		if (kubernetes) return null;
		try {
			DefaultDockerClient.Builder builder = DefaultDockerClient.fromEnv();
			String confCertPath = environment.getProperty("shiny.proxy.docker.cert-path");
			if (confCertPath != null) {
				builder.dockerCertificates(DockerCertificates.builder().dockerCertPath(Paths.get(confCertPath)).build().orNull());
			}
			String confUrl = environment.getProperty("shiny.proxy.docker.url");
			if (confUrl != null) {
				builder.uri(confUrl);
			}
			return builder.build();
		} catch (DockerCertificateException e) {
			throw new ShinyProxyException("Failed to initialize docker client", e);
		}
	}

	public List<Proxy> listProxies() {
		ConcurrentMap<Integer, Proxy> proxies = activeProxiesMap();
		synchronized (proxies) {
			return proxies.keySet().stream().map(p -> proxies.get(p).copyInto(new Proxy())).collect(Collectors.toList());
		}
	}

	public String getMapping(HttpServletRequest request, String userName, String appName, boolean startNew) {
		waitForLaunchingProxy(userName, appName);
		Proxy proxy = findProxy(userName, appName);
		if (proxy == null && startNew) {
			// The user has no proxy yet.
			proxy = startProxy(userName, appName);
		}
		if (proxy == null) {
			return null;
		} else {
			//proxy.sessionIds.add(getCurrentSessionId(request));
			activeProxySessionId().put(proxy.getName(),getCurrentSessionId(request));
			return proxy.getName();
		}
	}

	public boolean sessionOwnsProxy(HttpServerExchange exchange) {
		String sessionId = getCurrentSessionId(exchange);
		if (sessionId == null) return false;
		String proxyName = exchange.getRelativePath();
		ConcurrentMap<Integer, Proxy> proxies = activeProxiesMap();
		synchronized (proxies) {
		//	for (Proxy p: activeProxies) {
				//if (p.sessionIds.contains(sessionId) && proxyName.startsWith("/" + p.name))
			Set<Integer> keySet = proxies.keySet();
			for(Integer key : keySet) {
				if (activeProxySessionId().get(proxies.get(key).getName()).equalsIgnoreCase(sessionId) && proxyName
						.startsWith("/" + proxies.get(key).getName())) {
					return true;
				}
			}


	}
		proxies = launchingProxiesMap();
		synchronized (proxies) {
		//	for (Proxy p: launchingProxies) {

			Set<Integer> integers = proxies.keySet();
			for(Integer i : integers){
			//	if (p.sessionIds.contains(sessionId) && proxyName.startsWith("/" + p.name)) {
				if (activeProxySessionId().get(proxies.get(i).getName()).equalsIgnoreCase(sessionId) && proxyName.startsWith("/" + proxies.get(i).getName())){
					return true;
				}
			}
		}
		return false;
	}

	public List<Proxy> releaseProxies(String userName) {
		List<Proxy> proxiesToRelease = new ArrayList<>();
		ConcurrentMap<Integer, Proxy> proxies = activeProxiesMap();
		synchronized (proxies) {
			/*for (Proxy proxy: activeProxies) {
				if (userName.equals(proxy.getUserName())) proxiesToRelease.add(proxy);
			}*/
			proxies.keySet().stream().forEach(k -> {
				if (userName.equalsIgnoreCase(proxies.get(k).getUserName())) {
					proxiesToRelease.add(proxies.get(k));
				}
			});
		}
		for (Proxy proxy: proxiesToRelease) {
			releaseProxy(proxy, true);
		}
		return proxiesToRelease;
	}

	public void releaseProxy(String userName, String appName) {
		Proxy proxy = findProxy(userName, appName);
		if (proxy != null) {
			releaseProxy(proxy, true);
		}
	}

	private String getCurrentSessionId(HttpServerExchange exchange) {
		if (exchange == null && ServletRequestContext.current() != null) {
			exchange = ServletRequestContext.current().getExchange();
		}
		if (exchange == null) return null;
		Cookie sessionCookie = exchange.getRequestCookies().get("JSESSIONID");
		if (sessionCookie == null) return null;
		return sessionCookie.getValue();
	}

	private String getCurrentSessionId(HttpServletRequest request) {
		if (request == null) {
			return getCurrentSessionId((HttpServerExchange) null);
		}
		javax.servlet.http.Cookie sessionCookie = WebUtils.getCookie(request, "JSESSIONID");
		if (sessionCookie == null) return null;
		return sessionCookie.getValue();
	}

	private void releaseProxy(Proxy proxy, boolean async) {
		//activeProxies.remove(proxy);
		activeProxiesMap().remove(proxy.hashCode());

		Runnable releaser = () -> {
			try {
				if (kubernetes) {
					kubeClient.pods().delete(proxy.getKubePod());
				} else if (swarmMode) {
					dockerClient.removeService(proxy.getServiceId());
				} else {
					ShinyApp app = appService.getApp(proxy.getAppName());
					if (app != null && app.getDockerNetworkConnections() != null) {
						for (String networkConnection: app.getDockerNetworkConnections()) {
							dockerClient.disconnectFromNetwork(proxy.getContainerId(), networkConnection);
						}
					}
					dockerClient.removeContainer(proxy.getContainerId(), RemoveContainerParam.forceKill());
				}
				releasePort(proxy.getPort());
				log.info(String.format("Proxy released [user: %s] [app: %s] [port: %d]", proxy.getUserName(), proxy.getAppName(), proxy.getPort()));
				eventService.post(EventType.AppStop.toString(), proxy.getUserName(), proxy.getAppName());
			} catch (Exception e){
				log.error("Failed to release proxy " + proxy.getName(), e);
			}
		};
		if (async) containerKiller.submit(releaser);
		else releaser.run();

		synchronized (mappingListeners) {
			for (MappingListener listener: mappingListeners) {
				listener.mappingRemoved(proxy.getName());
			}
		}
	}

	private Proxy startProxy(String userName, String appName) {

		ShinyApp app = appService.getApp(appName);
		if (app == null) {
			throw new ShinyProxyException("Cannot start container: unknown application: " + appName);
		}

		if (findProxy(userName, appName) != null) {
			throw new ShinyProxyException("Cannot start container: user " + userName + " already has a running proxy");
		}

		boolean internalNetworking = "true".equals(environment.getProperty("shiny.proxy.docker.internal-networking"));
		boolean generateName = kubernetes || swarmMode ||
			"true".equals(environment.getProperty("shiny.proxy.docker.generate-name", String.valueOf(internalNetworking)));

		Proxy proxy = new Proxy();
		proxy.setPingErrors(0);
		proxy.setUserName(userName);
		proxy.setAppName(appName);
		if (internalNetworking) {
			proxy.setPort(app.getPort());
		} else {
			proxy.setPort(getFreePort());
		}
		//launchingProxies.add(proxy);
		int key = proxy.hashCode();
		launchingProxiesMap().putIfAbsent(key,proxy);

		String kubeNamespace = Optional.ofNullable(app.getKubernetesNamespace()).orElse("default");

		try {
			String containerProtocolDefault = "http";
			String hostURLString = environment.getProperty("shiny.proxy.docker.url");
			URL hostURL = null;
			if (hostURLString != null) {
				hostURL = new URL(hostURLString);
				containerProtocolDefault = hostURL.getProtocol();
			}
			proxy.setProtocol(environment.getProperty("shiny.proxy.docker.container-protocol", containerProtocolDefault));

			if (generateName) {
				byte[] nameBytes = new byte[20];
				rng.nextBytes(nameBytes);
				proxy.setName(Hex.encodeHexString(nameBytes));
			}

			if (kubernetes) {
				String[] dockerVolumeStrs = Optional.ofNullable(app.getDockerVolumes()).orElse(new String[] {});
				Volume[] volumes = new Volume[dockerVolumeStrs.length];
				VolumeMount[] volumeMounts = new VolumeMount[dockerVolumeStrs.length];
				for (int i = 0; i < dockerVolumeStrs.length; i++) {
					String[] dockerVolume = dockerVolumeStrs[i].split(":");
					String hostSource = dockerVolume[0];
					String containerDest = dockerVolume[1];
					String name = "shinyproxy-volume-" + i;
					volumes[i] = new VolumeBuilder()
							.withNewHostPath(hostSource)
							.withName(name)
							.build();
					volumeMounts[i] = new VolumeMountBuilder()
							.withMountPath(containerDest)
							.withName(name)
							.build();
				}

				String[] dockerNetworkConnections = app.getDockerNetworkConnections();
				if (dockerNetworkConnections != null && dockerNetworkConnections.length > 0) {
					log.warn(String.format("Docker networks specified for app %s, but Kubernetes does not have that concept", app.getName()));
				}

				ContainerPortBuilder containerPortBuilder = new ContainerPortBuilder().withContainerPort(app.getPort());
				if (!internalNetworking) {
					containerPortBuilder.withHostPort(proxy.getPort());
				}

				List<EnvVar> envVars = new ArrayList<>();
				for (String envString : buildEnv(userName, app)) {
					int idx = envString.indexOf('=');
					if (idx == -1) {
						log.warn("Invalid environment variable: " + envString);
					}
					envVars.add(new EnvVar(envString.substring(0, idx), envString.substring(idx + 1), null));
				}

				String k8sMemoryRequest = Optional.ofNullable(app.getKubernetesMemoryRequest()).orElse("600M");
				String k8sMemoryLimit = Optional.ofNullable(app.getKubernetesMemoryLimit()).orElse("1G");
				String k8sCpuRequest = Optional.ofNullable(app.getKubernetesCpuRequest()).orElse("500m");
				String k8sCpuLimit = Optional.ofNullable(app.getKubernetesCpuLimit()).orElse("1");

				ContainerBuilder containerBuilder = new ContainerBuilder()
						.withImage(app.getDockerImage())
						.withName("shiny-container")
						.withPorts(containerPortBuilder.build())
						.withNewResources()
							.addToLimits("cpu", new Quantity(k8sCpuLimit))
							.addToLimits("memory", new Quantity(k8sMemoryLimit))
							.addToRequests("cpu", new Quantity(k8sCpuRequest))
							.addToRequests("memory", new Quantity(k8sMemoryRequest))
						.endResources()
						.withEnv(envVars);

				String imagePullPolicy = environment.getProperty("shiny.proxy.docker.kubernetes-image-pull-policy", app.getKubernetesImagePullPolicy());
				if (imagePullPolicy != null) {
					containerBuilder.withImagePullPolicy(imagePullPolicy);
				}
				if (app.getDockerCmd() != null) {
					containerBuilder.withCommand(app.getDockerCmd());
				}

				String[] imagePullSecrets = environment.getProperty("shiny.proxy.docker.kubernetes-image-pull-secrets", String[].class);
				if (imagePullSecrets == null) {
					String imagePullSecret = environment.getProperty("shiny.proxy.docker.kubernetes-image-pull-secret");
					if (imagePullSecret != null) {
						imagePullSecrets = new String[] {imagePullSecret};
					} else {
						imagePullSecrets = new String[0];
					}
				}

				Pod pod = kubeClient.pods().inNamespace(kubeNamespace).createNew()
						.withApiVersion("v1")
						.withKind("Pod")
						.withNewMetadata()
						.withName(proxy.getName())
						.endMetadata()
						.withNewSpec()
						.withContainers(Collections.singletonList(containerBuilder.build()))
						.withVolumes(Arrays.asList(volumes))
						.withImagePullSecrets(Arrays.asList(imagePullSecrets).stream()
								.map(LocalObjectReference::new).collect(Collectors.toList()))

						.endSpec()
						.done();

				try {
					 pod = kubeClient.resource(pod).waitUntilReady(
							Integer.parseInt(environment.getProperty("shiny.proxy.container-wait-time", "20000")),
							TimeUnit.MILLISECONDS);
					proxy.setKubePod(pod);
					log.info("pod created successfully   " + proxy.getName());
				}
				catch (Exception e){
					try {
						kubeClient.pods().delete(pod);
					}catch(Exception e1)
					{
						log.error("failed to delete pod  " + proxy.getName());
					}
					throw e;
				}
				if (internalNetworking) {
					proxy.setHost(pod.getStatus().getPodIP());
				} else {
					proxy.setHost(pod.getStatus().getHostIP());
				}
			} else if (swarmMode) {
				Mount[] mounts = getBindVolumes(app).stream()
						.map(b -> b.split(":"))
						.map(fromTo -> Mount.builder().source(fromTo[0]).target(fromTo[1]).type("bind").build())
						.toArray(i -> new Mount[i]);

				ContainerSpec containerSpec = ContainerSpec.builder()
						.image(app.getDockerImage())
						.command(app.getDockerCmd())
						.env(buildEnv(userName, app))
						.dnsConfig(DnsConfig.builder().nameServers(app.getDockerDns()).build())
						.mounts(mounts)
						.build();

				NetworkAttachmentConfig[] networks = Arrays
						.stream(Optional.ofNullable(app.getDockerNetworkConnections()).orElse(new String[0]))
						.map(n -> NetworkAttachmentConfig.builder().target(n).build())
						.toArray(i -> new NetworkAttachmentConfig[i]);

				ServiceSpec.Builder serviceSpecBuilder = ServiceSpec.builder()
						.networks(networks)
						.name(proxy.getName())
						.taskTemplate(TaskSpec.builder()
								.containerSpec(containerSpec)
								.build());
				if (!internalNetworking) {
					serviceSpecBuilder.endpointSpec(EndpointSpec.builder()
							.ports(PortConfig.builder().publishedPort(proxy.getPort()).targetPort(app.getPort()).build())
							.build());
				}

				proxy.setServiceId(dockerClient.createService(serviceSpecBuilder.build()).id());

				boolean containerFound = retry(i -> {
					try {
						Task serviceTask = dockerClient
							.listTasks(Task.Criteria.builder().serviceName(proxy.getName()).build())
							.stream().findAny().orElseThrow(() -> new IllegalStateException("Swarm service has no tasks"));
						proxy.setContainerId(serviceTask.status().containerStatus().containerId());
						proxy.setHost(serviceTask.nodeId());
					} catch (Exception e) {
						throw new RuntimeException("Failed to inspect swarm service tasks");
					}
					return (proxy.getContainerId() != null);
				}, 10, 2000);
				if (!containerFound) throw new IllegalStateException("Swarm container did not start in time");

				if (internalNetworking) {
					proxy.setHost(proxy.getName());
				} else {
					Node node = dockerClient.listNodes().stream()
							.filter(n -> n.id().equals(proxy.getHost())).findAny()
							.orElseThrow(() -> new IllegalStateException(String.format("Swarm node not found [id: %s]", proxy.getHost())));
					proxy.setHost(node.description().hostname());
				}

				log.info(String.format("Container running in swarm [service: %s] [node: %s]", proxy.getName(), proxy.getHost()));
			} else {
				Builder hostConfigBuilder = HostConfig.builder();

				Optional.ofNullable(memoryToBytes(app.getDockerMemory())).ifPresent(l -> hostConfigBuilder.memory(l));
				Optional.ofNullable(app.getDockerNetwork()).ifPresent(n -> hostConfigBuilder.networkMode(app.getDockerNetwork()));

				List<PortBinding> portBindings;
				if (internalNetworking) {
					portBindings = Collections.emptyList();
				} else {
					portBindings = Collections.singletonList(PortBinding.of("0.0.0.0", proxy.getPort()));
				}
				hostConfigBuilder
						.portBindings(Collections.singletonMap(app.getPort().toString(), portBindings))
						.dns(app.getDockerDns())
						.binds(getBindVolumes(app));

				ContainerConfig containerConfig = ContainerConfig.builder()
					    .hostConfig(hostConfigBuilder.build())
					    .image(app.getDockerImage())
					    .exposedPorts(app.getPort().toString())
					    .cmd(app.getDockerCmd())
					    .env(buildEnv(userName, app))
					    .build();

				ContainerCreation container = dockerClient.createContainer(containerConfig);
				if (app.getDockerNetworkConnections() != null) {
					for (String networkConnection: app.getDockerNetworkConnections()) {
						dockerClient.connectToNetwork(container.id(), networkConnection);
					}
				}
				if (proxy.getName() != null) {
					dockerClient.renameContainer(container.id(), proxy.getName());
				}
				dockerClient.startContainer(container.id());

				ContainerInfo info = dockerClient.inspectContainer(container.id());
				if (proxy.getName() == null) {
					proxy.setName(info.name().substring(1));
				}
				if (internalNetworking) {
					proxy.setHost(proxy.getName());
				} else {
					proxy.setHost(hostURL.getHost());
				}
				proxy.setContainerId(container.id());
			}

			proxy.setStartupTimestamp( System.currentTimeMillis());
		} catch (Exception e) {
			if (!internalNetworking) {
				releasePort(proxy.getPort());
			}
		//	launchingProxies.remove(proxy);
		//	removeLaunchingProxy(proxy);
			launchingProxiesMap().remove(proxy.hashCode());
			throw new ShinyProxyException("Failed to start container: " + e.getMessage(), e);
		}


		if (!testProxy(proxy)) {
			releaseProxy(proxy, true);
		//	launchingProxies.remove(proxy);
		//	removeLaunchingProxy(proxy);
			launchingProxiesMap().remove(proxy.hashCode());
			throw new ShinyProxyException("Container did not respond in time");
		}

		try {
			URI target = new URI(String.format("%s://%s:%d", proxy.getProtocol(), proxy.getHost(), proxy.getPort()));
			synchronized (mappingListeners) {
				for (MappingListener listener: mappingListeners) {
					listener.mappingAdded(proxy.getName(), target);
				}
			}
		} catch (URISyntaxException ignore) {}

		if (logService.isContainerLoggingEnabled()) {
			try {
				if (kubernetes) {
					LogWatch watcher = kubeClient.pods().inNamespace(kubeNamespace).withName(proxy.getName()).watchLog();
					logService.attachLogWatcher(proxy, watcher);
				} else {
					LogStream logStream;
					logStream = dockerClient.logs(proxy.getContainerId(), LogsParam.follow(), LogsParam.stdout(), LogsParam.stderr());
					logService.attachLogWriter(proxy, logStream);
				}
			} catch (DockerException e) {
				log.error("Failed to attach to container log " + proxy.getContainerId(), e);
			} catch (InterruptedException e) {
				log.error("Interrupted while attaching to container log " + proxy.getContainerId(), e);
			}
		}

	//	activeProxies.add(proxy);
		activeProxiesMap().put(proxy.hashCode(),proxy);
		//launchingProxies.remove(proxy);
		//launchingProxies.remove(0);
		//launchingProxies.getPartitionKey()
		//launchingProxies.removeIf((Proxy p) ->  p.getUserName().equalsIgnoreCase("jack"));
		launchingProxiesMap().remove(proxy.hashCode());
		//launchingProxiesMap.remove(proxy.hashCode(),proxy);
		//removeLaunchingProxy(proxy);
		log.info(String.format("Proxy activated [user: %s] [app: %s] [port: %d]", userName, appName, proxy.getPort()));
		eventService.post(EventType.AppStart.toString(), userName, appName);

		return proxy;
	}

	/*private void removeLaunchingProxy(Proxy proxy){
		*//*int cnt=0;
		for(Proxy p : launchingProxies){
			System.out.println(p.getUserName());
			if(p.getUserName().equalsIgnoreCase(proxy.getUserName()) && p.getAppName().equalsIgnoreCase(proxy.getAppName()) && p.getPort()==proxy.getPort()){
				launchingProxies.re
				launchingProxies.remove(cnt);
			}
			cnt++;
		}*//*

		*//*Iterator<Proxy> iterator = launchingProxies.iterator();
		while ( iterator.hasNext() ) {
			Proxy price = iterator.next();
			//analyze
			launchingProxies.remove(price);
		}*//*

	}*/

	private Proxy findProxy(String userName, String appName) {
		ConcurrentMap<Integer, Proxy> proxies = activeProxiesMap();
		synchronized (proxies) {
			/*for (Proxy proxy: activeProxies) {
				if (userName.equals(proxy.getUserName()) && appName.equals(proxy.getAppName()))
					return proxy;
			}*/
			Set<Integer> keySet = proxies.keySet();
			for(Integer key : keySet) {
				if (userName.equals(proxies.get(key).getUserName()) && appName
						.equals(proxies.get(key).getAppName()))
					return proxies.get(key);
			}
		}
		return null;
	}

	private void waitForLaunchingProxy(String userName, String appName) {
		int totalWaitMs = Integer.parseInt(environment.getProperty("shiny.proxy.container-wait-time", "20000"));
		int waitMs = Math.min(2000, totalWaitMs);
		int maxTries = totalWaitMs / waitMs;

		boolean mayProceed = retry(i -> {
			ConcurrentMap<Integer, Proxy> proxies = launchingProxiesMap();
			synchronized (proxies) {
			//	for (Proxy proxy: launchingProxies) {
				Set<Integer> keys = proxies.keySet();
				for (Integer o : keys){
				if (userName.equals(proxies.get(o).getUserName()) && appName.equals(proxies.get(o).getAppName())) {
						return false;
					}
				}
			}
			return true;
		}, maxTries, waitMs);

		if (!mayProceed) throw new ShinyProxyException("Cannot proceed: waiting for proxy to launch");
	}

	private boolean testProxy(Proxy proxy) {
		int totalWaitMs = Integer.parseInt(environment.getProperty("shiny.proxy.container-wait-time", "20000"));
		int waitMs = Math.min(2000, totalWaitMs);
		int maxTries = totalWaitMs / waitMs;
		int timeoutMs = Integer.parseInt(environment.getProperty("shiny.proxy.container-wait-timeout", "5000"));

		return retry(i -> {
			String urlString = String.format("%s://%s:%d", proxy.getProtocol(), proxy.getHost(), proxy.getPort());
			try {
				URL testURL = new URL(urlString);
				HttpURLConnection connection = ((HttpURLConnection) testURL.openConnection());
				connection.setConnectTimeout(timeoutMs);
				int responseCode = connection.getResponseCode();
				if (responseCode == 200) return true;
			} catch (Exception e) {
				if (i > 1) log.warn(String.format("Container unresponsive, trying again (%d/%d): %s", i, maxTries, urlString));
			}
			return false;
		}, maxTries, waitMs);
	}

	private List<String> buildEnv(String userName, ShinyApp app) throws IOException {
		List<String> env = new ArrayList<>();
		env.add(String.format("SHINYPROXY_USERNAME=%s", userName));

		String[] groups = userService.getGroups(userService.getCurrentAuth());
		env.add(String.format("SHINYPROXY_USERGROUPS=%s", Arrays.stream(groups).collect(Collectors.joining(","))));

		String envFile = app.getDockerEnvFile();
		if (envFile != null && Files.isRegularFile(Paths.get(envFile))) {
			Properties envProps = new Properties();
			envProps.load(new FileInputStream(envFile));
			for (Object key: envProps.keySet()) {
				env.add(String.format("%s=%s", key, envProps.get(key)));
			}
		}

		for (Map.Entry<String, String> entry : app.getDockerEnv().entrySet()) {
			env.add(String.format("%s=%s", entry.getKey(), entry.getValue()));
		}

		return env;
	}

	private List<String> getBindVolumes(ShinyApp app) {
		List<String> volumes = new ArrayList<>();

		if (app.getDockerVolumes() != null) {
			for (String vol: app.getDockerVolumes()) {
				volumes.add(vol);
			}
		}

		return volumes;
	}

	private int getFreePort() {
		int startPort = Integer.valueOf(environment.getProperty("shiny.proxy.docker.port-range-start"));
		int maxPort = Integer.valueOf(environment.getProperty("shiny.proxy.docker.port-range-max", "-1"));
		int nextPort = startPort;
		Set<Integer> ports = occupiedPorts();
		while (ports.contains(nextPort)) nextPort++;
		if (maxPort > 0 && nextPort > maxPort) {
			throw new ShinyProxyException("Cannot start container: all allocated ports are currently in use."
					+ " Please try again later or contact an administrator.");
		}
		ports.add(nextPort);
		return nextPort;
	}

	private void releasePort(int port) {
		occupiedPorts().remove(port);
	}

	private boolean retry(IntPredicate job, int tries, int waitTime) {
		boolean retVal = false;
		for (int currentTry = 1; currentTry <= tries; currentTry++) {
			if (job.test(currentTry)) {
				retVal = true;
				break;
			}
			try { Thread.sleep(waitTime); } catch (InterruptedException ignore) {}
		}
		return retVal;
	}

	private Long memoryToBytes(String memory) {
		if (memory == null || memory.isEmpty()) return null;
		Matcher matcher = Pattern.compile("(\\d+)([bkmg]?)").matcher(memory.toLowerCase());
		if (!matcher.matches()) throw new IllegalArgumentException("Invalid memory argument: " + memory);
		long mem = Long.parseLong(matcher.group(1));
		String unit = matcher.group(2);
		switch (unit) {
		case "k":
			mem *= 1024;
			break;
		case "m":
			mem *= 1024*1024;
			break;
		case "g":
			mem *= 1024*1024*1024;
			break;
		default:
		}
		return mem;
	}

	public void addMappingListener(MappingListener listener) {
		mappingListeners.add(listener);
	}

	public void removeMappingListener(MappingListener listener) {
		mappingListeners.remove(listener);
	}

	public static interface MappingListener {
		public void mappingAdded(String mapping, URI target);
		public void mappingRemoved(String mapping);
	}

	public void heartbeatReceived(String user, String app) {
		Proxy proxy = findProxy(user, app);
		if (proxy != null) {
			proxy.setLastHeartbeatTimestamp(System.currentTimeMillis());
			activeProxiesMap().put(proxy.hashCode(),proxy);
		}
	}

	private class AppCleaner implements Runnable {
		@Override
		public void run() {
			long cleanupInterval = 2 * Long.parseLong(environment.getProperty("shiny.proxy.heartbeat-rate", "10000"));
			long heartbeatTimeout = Long.parseLong(environment.getProperty("shiny.proxy.heartbeat-timeout", "60000"));
			long pingInterval = Long.parseLong(environment.getProperty("shiny.proxy.ping-interval", "30000"));
			long pingErrorThreshold = Integer.parseInt(environment.getProperty("shiny.proxy.ping-error-threshold", "3"));

			while (true) {
				try {

					List<Proxy> proxiesToRemove = new ArrayList<>();
					long currentTimestamp = System.currentTimeMillis();
					ConcurrentMap<Integer, Proxy> proxiesMap = activeProxiesMap();
					synchronized (proxiesMap) {
						Set<Integer> keySet = proxiesMap.keySet();
						for(Integer key : keySet){
							Proxy proxy = proxiesMap.get(key);
						//for (Proxy proxy: activeProxies) {
							Long lastHeartbeat = proxy.getLastHeartbeatTimestamp();
							if (lastHeartbeat == null) lastHeartbeat = proxy.getStartupTimestamp();
							long proxySilence = currentTimestamp - lastHeartbeat;
							log.info("In AppCleaner ... containerId " + proxy.getContainerId() + "name " +proxy.getName() + "proxySilence  " + proxySilence + "heartbeatTimeout = " +heartbeatTimeout);
							System.out.println("In AppCleaner ... containerId " + proxy.getContainerId() + "name " +proxy.getName() + "proxySilence " + proxySilence + "heartbeatTimeout = " +heartbeatTimeout);
								if (proxySilence > heartbeatTimeout) {
								log.info("In AppCleaner ... containerId " + proxy.getContainerId() + "name " +proxy.getName() + "proxySilence " + proxySilence + " heartbeatTimeout " + heartbeatTimeout);
								log.info(String.format("Releasing inactive proxy [user: %s] [app: %s] [silence: %dms]", proxy.getUserName(), proxy.getAppName(), proxySilence));
								proxiesToRemove.add(proxy);
							} else {
									Long lastPing = proxy.getLastPingTimestamp();
									if (lastPing == null) lastPing = proxy.getStartupTimestamp();
									if (currentTimestamp - lastPing > pingInterval) {
										log.info("Pinging " + proxy.getContainerId() + " name " + proxy.getName() + " diff " + (currentTimestamp - lastPing) + " of " + pingInterval + " error " + proxy.getPingErrors());
										proxy.setLastPingTimestamp(currentTimestamp);
										String urlString = String.format("%s://%s:%d", proxy.getProtocol(), proxy.getHost(), proxy.getPort());
										try {
											URL testURL = new URL(urlString);
											HttpURLConnection connection = ((HttpURLConnection) testURL.openConnection());
											connection.setConnectTimeout(5000);
											int responseCode = connection.getResponseCode();
											if (responseCode == 200) {
												log.info("Ping ok");
												proxy.setPingErrors(0);
											} else {
												log.info("Ping error");
												proxy.setPingErrors(proxy.getPingErrors() + 1);
											}
										} catch (Exception e) {
											log.info("Ping error");
											proxy.setPingErrors(proxy.getPingErrors() + 1);
										}

										if (proxy.getPingErrors() >= pingErrorThreshold) {
											log.info("Removing container " + proxy.getContainerId() + " because of the failed ping");
											proxiesToRemove.add(proxy);
										}
									}
								}
							proxiesMap.put(proxy.hashCode(),proxy);
						}
					}
					for (Proxy proxy: proxiesToRemove) {
						releaseProxy(proxy, true);
						log.info(String.format("Releasing inactive proxy [user: %s] [app: %s]", proxy.getUserName(), proxy.getAppName()));
					}
				} catch (Throwable t) {
					log.error("Error in HeartbeatThread", t);
				}
				try {
					Thread.sleep(cleanupInterval);
				} catch (InterruptedException e) {}
			}
		}
	}
}
