package com.jfa.common.model.report;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Recommendations {
    private List<Recommendation> code = new ArrayList<Recommendation>();
    private List<Recommendation> config = new ArrayList<Recommendation>();
    private List<Recommendation> capacity = new ArrayList<Recommendation>();
    private List<Recommendation> ops = new ArrayList<Recommendation>();

    public List<Recommendation> getCode() {
        return code;
    }

    public void setCode(List<Recommendation> code) {
        this.code = code;
    }

    public List<Recommendation> getConfig() {
        return config;
    }

    public void setConfig(List<Recommendation> config) {
        this.config = config;
    }

    public List<Recommendation> getCapacity() {
        return capacity;
    }

    public void setCapacity(List<Recommendation> capacity) {
        this.capacity = capacity;
    }

    public List<Recommendation> getOps() {
        return ops;
    }

    public void setOps(List<Recommendation> ops) {
        this.ops = ops;
    }

    @JsonIgnore
    public boolean isEmpty() {
        return code.isEmpty() && config.isEmpty() && capacity.isEmpty() && ops.isEmpty();
    }
}
