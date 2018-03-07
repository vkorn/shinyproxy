package eu.openanalytics;

import static java.util.Collections.singletonList;

import com.hazelcast.config.Config;
import com.hazelcast.config.JoinConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HazelcastConfiguration {
  @Bean
  public Config config() {
   /* return new Config().addMapConfig(
        new MapConfig()
            .setName("accepted-messages")
            .setEvictionPolicy(EvictionPolicy.LRU)
            .setTimeToLiveSeconds(2400))
        .setProperty("hazelcast.logging.type","slf4j"); // Set up any non-default config here*/

    Config config = new Config();

    JoinConfig joinConfig = config.getNetworkConfig().getJoin();

    joinConfig.getMulticastConfig().setEnabled(false);
    joinConfig.getTcpIpConfig().setEnabled(true).setMembers(singletonList("localhost"));

    return config;
  }
  /*@Bean
  public WebFilter webFilter(HazelcastInstance hazelcastInstance) {

    Properties properties = new Properties();
    properties.put("instance-name", hazelcastInstance.getName());
    properties.put("sticky-session", "false");

    return new WebFilter(properties);
  }*/

}
