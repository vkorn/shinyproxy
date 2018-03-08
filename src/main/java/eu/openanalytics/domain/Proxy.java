package eu.openanalytics.domain;

import io.fabric8.kubernetes.api.model.Pod;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public  class Proxy implements Serializable {

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

  public Proxy copyInto(Proxy target) {
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
}