/**
 * ShinyProxy
 * <p>
 * Copyright (C) 2016-2017 Open Analytics
 * <p>
 * ===========================================================================
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 * <p>
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.domain;

import io.fabric8.kubernetes.api.model.Pod;

import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Proxy implements Serializable {

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
    private Long lastPingTimestamp;
    private int pingErrors;

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
        target.pingErrors = this.pingErrors;
        target.lastPingTimestamp = this.lastPingTimestamp;
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

    public Long getLastPingTimestamp() {
        return lastPingTimestamp;
    }

    public void setLastPingTimestamp(Long lastPingTimestamp) {
        this.lastPingTimestamp = lastPingTimestamp;
    }

    public int getPingErrors() {
        return pingErrors;
    }

    public void setPingErrors(int pingErrors) {
        this.pingErrors = pingErrors;
    }

    public Pod getKubePod() {
        return kubePod;
    }

    public void setKubePod(Pod kubePod) {
        this.kubePod = kubePod;
    }

    private void ping(boolean isSuccess) {
        this.lastPingTimestamp = System.currentTimeMillis();
        if (isSuccess) {
            this.pingErrors = 0;
        } else {
            this.pingErrors += 1;
        }
    }

    public void Ping() {
        String urlString = String.format("%s://%s:%d", this.protocol, this.host, this.port);
        try {
            URL testURL = new URL(urlString);
            HttpURLConnection connection = ((HttpURLConnection) testURL.openConnection());
            connection.setConnectTimeout(5000);
            int responseCode = connection.getResponseCode();
            if (responseCode == 200) {
                this.ping(true);
            } else {
                this.ping(false);
            }
        } catch (Exception e) {
            this.ping(false);
        }
    }
}