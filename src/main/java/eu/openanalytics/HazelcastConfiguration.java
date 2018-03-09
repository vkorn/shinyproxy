package eu.openanalytics;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.web.SessionListener;
import com.hazelcast.web.spring.SpringAwareWebFilter;
import java.util.Properties;
import javax.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
@Configuration
@ConditionalOnExpression(ShinyProxyApplication.USE_HAZELCAST)

public class HazelcastConfiguration {

  @Autowired
  Environment environment;

  @Bean
  public HazelcastInstance hazelcastInstance() {
    ClientConfig clientConfig = new ClientConfig();
    System.out.println("in client config");
    ClientNetworkConfig network = clientConfig.getNetworkConfig();
    String hosts = environment.getProperty("shiny.proxy.hazelcast.server");
    String[] split = hosts.split(",");
    network.addAddress(split).setConnectionTimeout(1000);;
    clientConfig.getNetworkConfig().setSmartRouting(true);
    clientConfig.setInstanceName("hcdev1");
    return HazelcastClient.newHazelcastClient(clientConfig);
  }

  /*@Bean
  public WebFilter springAwareWebFilter(HazelcastInstance hazelcastInstance) {
    *//*ClientConfig clientConfig = new ClientConfig();
    System.out.println("in client config");
    ClientNetworkConfig network = clientConfig.getNetworkConfig();
    String hosts = environment.getProperty("shiny.proxy.hazelcast.server");
    String[] split = hosts.split(",");
    network.addAddress(split).setConnectionTimeout(1000);;
    clientConfig.getNetworkConfig().setSmartRouting(true);*//*


    Properties properties = new Properties();
    properties.put("instance-name", hazelcastInstance.getName());
    properties.put("sticky-session", "false");
    properties.put("use-client","true");

    WebFilter webFilter = new WebFilter(properties);

    return webFilter;
  }*/

  @Bean

  public FilterRegistrationBean hazelcastFilter(HazelcastInstance hazelcastInstance) {
    Properties properties = new Properties();
    properties.put("instance-name", hazelcastInstance.getName());
    properties.put("sticky-session", "false");
    properties.put("use-client","true");

    FilterRegistrationBean registration = new FilterRegistrationBean(new SpringAwareWebFilter(properties));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
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
}
