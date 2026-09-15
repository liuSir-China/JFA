package com.jfa.common.model.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TargetInfo {
    @JsonProperty("service_id")
    private String serviceId;
    private Long pid;
    @JsonProperty("main_class_or_jar")
    private String mainClassOrJar;
    private String host;

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public Long getPid() {
        return pid;
    }

    public void setPid(Long pid) {
        this.pid = pid;
    }

    public String getMainClassOrJar() {
        return mainClassOrJar;
    }

    public void setMainClassOrJar(String mainClassOrJar) {
        this.mainClassOrJar = mainClassOrJar;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
}
