package com.jfa.common.model.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReportSection {
    private String type;
    private String status;
    private String note;
    private String confidence;
    private Map<String, Object> qualification = new LinkedHashMap<String, Object>();
    private List<Map<String, Object>> suspects = new ArrayList<Map<String, Object>>();
    private Recommendations recommendations = new Recommendations();
    @JsonProperty("missing_evidence")
    private List<String> missingEvidence = new ArrayList<String>();
    @JsonProperty("next_minimal_actions")
    private List<String> nextMinimalActions = new ArrayList<String>();

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getConfidence() {
        return confidence;
    }

    public void setConfidence(String confidence) {
        this.confidence = confidence;
    }

    public Map<String, Object> getQualification() {
        return qualification;
    }

    public void setQualification(Map<String, Object> qualification) {
        this.qualification = qualification;
    }

    public List<Map<String, Object>> getSuspects() {
        return suspects;
    }

    public void setSuspects(List<Map<String, Object>> suspects) {
        this.suspects = suspects;
    }

    public Recommendations getRecommendations() {
        return recommendations;
    }

    public void setRecommendations(Recommendations recommendations) {
        this.recommendations = recommendations;
    }

    public List<String> getMissingEvidence() {
        return missingEvidence;
    }

    public void setMissingEvidence(List<String> missingEvidence) {
        this.missingEvidence = missingEvidence;
    }

    public List<String> getNextMinimalActions() {
        return nextMinimalActions;
    }

    public void setNextMinimalActions(List<String> nextMinimalActions) {
        this.nextMinimalActions = nextMinimalActions;
    }
}
