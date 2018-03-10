package eu.openanalytics;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.web.SessionListener;
import com.hazelcast.web.spring.SpringAwareWebFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import javax.servlet.DispatcherType;
import java.util.Properties;

@Configuration
public class HazelcastConfiguration {

    @Autowired
    Environment environment;

    HazelcastInstance server;

    @Bean
    public HazelcastInstance hazelcastInstance() {
        ClientConfig clientConfig = new ClientConfig();
        ClientNetworkConfig network = clientConfig.getNetworkConfig();
        String[] addresses;
        if ("true".equals(environment.getProperty("shiny.proxy.ha"))) {
            String hosts = environment.getProperty("shiny.proxy.hazelcast.server");
            addresses = hosts.split(",");
        } else {
            Config config = new Config();
            config.setInstanceName("hcdev1");
            server = Hazelcast.newHazelcastInstance(config);
            addresses = new String[]{"localhost:5701"};
        }
        network.addAddress(addresses).setConnectionTimeout(1000);
        clientConfig.getNetworkConfig().setSmartRouting(true);
        clientConfig.setInstanceName("hcdev1");
        return HazelcastClient.newHazelcastClient(clientConfig);
    }

    @Bean
    public FilterRegistrationBean hazelcastFilter(HazelcastInstance hazelcastInstance) {
        Properties properties = new Properties();
        properties.put("instance-name", hazelcastInstance.getName());
        properties.put("sticky-session", "false");
        properties.put("use-client", "true");
        properties.put("cookie-max-age", environment.getProperty("server.session.cookie.max-age", "1800"));

        FilterRegistrationBean registration = new FilterRegistrationBean(new SpringAwareWebFilter(properties));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.INCLUDE);
        return registration;
    }

    @Bean
    public ServletListenerRegistrationBean<SessionListener> hazelcastSessionListener() {
        return new ServletListenerRegistrationBean<SessionListener>(new SessionListener());
    }
}
