package eu.openanalytics;

import com.esotericsoftware.yamlbeans.YamlException;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

//import com.hazelcast.web.WebFilter;

@Configuration
@EnableAutoConfiguration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
public class HazelcastConfiguration {

  @Inject
  Environment environment;

  public String hcHosts() throws YamlException, FileNotFoundException {


   /* ClassLoader classLoader = getClass().getClassLoader();
    File file = new File(classLoader.getResource(System.getProperty("config.file","/home/kaiser/application.yaml")).getFile());*/
///Users/kpsoi/IdeaProjects/shinyproxykubernetes/src/main/java/eu/openanalytics/HazelcastConfiguration.java
    YamlReader reader = new YamlReader(new FileReader(System.getProperty("config.file","/home/kaiser/application.yaml")));
    Object object = reader.read();
    Map map = (Map)object;
    Map shiny = (Map)map.get("shiny");
    Map proxy = (Map)shiny.get("proxy");
    Map hazelcast = (Map)proxy.get("hazelcast");
    String server = (String)hazelcast.get("server");

    return server;
  }
 /* @Bean
  public HazelcastInstance hazelcastInstance() {
    return Hazelcast.newHazelcastInstance();
  }*/
  /*@Bean
  public Config config() {
   *//* return new Config().addMapConfig(
        new MapConfig()
            .setName("accepted-messages")
            .setEvictionPolicy(EvictionPolicy.LRU)
            .setTimeToLiveSeconds(2400))
        .setProperty("hazelcast.logging.type","slf4j"); // Set up any non-default config here*//*

    Config config = new Config();

    JoinConfig joinConfig = config.getNetworkConfig().getJoin();

    joinConfig.getMulticastConfig().setEnabled(false);

    joinConfig.getTcpIpConfig().setEnabled(true).setMembers(singletonList("localhost"));

    return config;
  }*/
//shiny:
//  proxy:
/*  @Bean
  public HazelcastInstance getHazelcastClientInstance() throws IOException {
    ClientConfig clientConfig = new ClientConfig();
    ClientNetworkConfig network = clientConfig.getNetworkConfig();
    String hosts = environment.getProperty("shiny.proxy.hazelcast.server");
    String[] split = hosts.split(",");
    network.addAddress(split);
    HazelcastInstance hazelcastInstance=  HazelcastClient.newHazelcastClient(clientConfig);
    return hazelcastInstance;
  }*/

  @Bean
  public ClientConfig getHazelcastClientConfig() throws IOException {
    ClientConfig clientConfig = new ClientConfig();
    System.out.println("in client config");
    ClientNetworkConfig network = clientConfig.getNetworkConfig();
    String hosts = hcHosts();/*environment.getProperty("shiny.proxy.hazelcast.server");*/
    String[] split = hosts.split(",");
    network.addAddress(split).setConnectionTimeout(1000);;
    clientConfig.getNetworkConfig().setSmartRouting(true);
    return clientConfig;
  }

  /*@Bean
  public Config config() {
   *//* return new Config().addMapConfig(
        new MapConfig()
            .setName("accepted-messages")
            .setEvictionPolicy(EvictionPolicy.LRU)
            .setTimeToLiveSeconds(2400))
        .setProperty("hazelcast.logging.type","slf4j"); // Set up any non-default config here*//*

    Config config = new Config();
    NetworkConfig network = config.getNetworkConfig();
    network.setPort(5701).setPortCount(20);
    network.setPortAutoIncrement(true);
    JoinConfig join = network.getJoin();
    join.getMulticastConfig().setEnabled(true);
    join.getTcpIpConfig()
        *//*.addMember("machine1")*//*
        .addMember("192.168.0.210").setEnabled(false);
    return config;
  }
*/
 /* @Bean
  public FilterRegistrationBean hazelcastFilter() {
    FilterRegistrationBean registration = new FilterRegistrationBean(new SpringAwareWebFilter());

    registration.addUrlPatterns("/*");
    registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.INCLUDE);

    // Configure init parameters as appropriate:
    // registration.addInitParameter("foo", "bar");

    return registration;
  }

  @Bean
  public ServletListenerRegistrationBean<SessionListener> hazelcastSessionListener() {
    return new ServletListenerRegistrationBean<SessionListener>(new SessionListener());
  }

  @Bean
  public WebFilter webFilter(HazelcastInstance hazelcastInstance) {

    Properties properties = new Properties();
    properties.put("instance-name", hazelcastInstance.getName());
    properties.put("sticky-session", "false");
    properties.put("debug", "false");
    properties.put("map-name","shinySessions");
    //properties.put("cookie-name","hccok");

   // WebFilter sp = new SpringAwareWebFilter();

    return new WebFilter(properties);
  }*/

}
