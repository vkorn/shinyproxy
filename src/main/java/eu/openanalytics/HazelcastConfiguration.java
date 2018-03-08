package eu.openanalytics;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.core.HazelcastInstance;
import java.io.IOException;
import javax.inject.Inject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

//import com.hazelcast.web.WebFilter;

@Configuration
public class HazelcastConfiguration {

  @Inject
  Environment environment;

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
  @Bean
  public HazelcastInstance getHazelcastClientInstance() throws IOException {
    ClientConfig clientConfig = new ClientConfig();
    ClientNetworkConfig network = clientConfig.getNetworkConfig();
    network.addAddress(environment.getProperty("shiny.proxy.hazelcast.server"));
   // network.addAddress("192.168.0.210:5701");
   /* network.setPort(5701).setPortCount(20);
    network.setPortAutoIncrement(true);*/
   /* JoinConfig join = network.();
    join.getMulticastConfig().setEnabled(true);
    join.getTcpIpConfig()
        *//*.addMember("machine1")*//*
        .addMember("192.168.0.210").setEnabled(false);*/
    //   clientConfig = new XmlClientConfigBuilder("hazelcast-client-dev.xml").build();
    HazelcastInstance hazelcastInstance=  HazelcastClient.newHazelcastClient(clientConfig);
    return hazelcastInstance;
  }

  @Bean
  public ClientConfig getHazelcastClientConfig() throws IOException {
    ClientConfig clientConfig = new ClientConfig();
    ClientNetworkConfig network = clientConfig.getNetworkConfig();
    network.addAddress(environment.getProperty("shiny.proxy.hazelcast.server")).setConnectionTimeout(1000);;

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
