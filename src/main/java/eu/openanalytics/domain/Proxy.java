package eu.openanalytics.domain;

import io.fabric8.kubernetes.api.model.Pod;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public  class Proxy implements Serializable {

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
}