package com.jfa.common.model.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Recommendation {
    private String id;
    private String what;
    private String why;
    @JsonProperty("how_to_verify")
    private String howToVerify;
    @JsonProperty("related_suspects")
    private List<String> relatedSuspects = new ArrayList<String>();
    private String confidence;
    private String assumption;

    public Recommendation() {
    }

    public Recommendation(String id, String what, String why, String howToVerify) {
        this.id = id;
        this.what = what;
        this.why = why;
        this.howToVerify = howToVerify;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getWhat() {
        return what;
    }

    public void setWhat(String what) {
        this.what = what;
    }

    public String getWhy() {
        return why;
    }

    public void setWhy(String why) {
        this.why = why;
    }

    public String getHowToVerify() {
        return howToVerify;
    }

    public void setHowToVerify(String howToVerify) {
        this.howToVerify = howToVerify;
    }

    public List<String> getRelatedSuspects() {
        return relatedSuspects;
    }

    public void setRelatedSuspects(List<String> relatedSuspects) {
        this.relatedSuspects = relatedSuspects;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public String getAssumption() {
        return assumption;
    }

    public void setAssumption(String assumption) {
        this.assumption = assumption;
    }
}
