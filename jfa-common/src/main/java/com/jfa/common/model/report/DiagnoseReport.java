package com.jfa.common.model.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.jfa.common.JfaConstants;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiagnoseReport {
    @JsonProperty("report_schema_version")
    private String reportSchemaVersion = JfaConstants.REPORT_SCHEMA_VERSION;
    private String product = JfaConstants.PRODUCT;
    @JsonProperty("generated_at")
    private String generatedAt;
    @JsonProperty("analysis_mode")
    private String analysisMode;
    @JsonProperty("report_mode")
    private String reportMode;
    private TargetInfo target = new TargetInfo();
    private Summary summary = new Summary();
    private List<TimelineEvent> timeline = new ArrayList<TimelineEvent>();
    private List<EvidenceItem> evidence = new ArrayList<EvidenceItem>();
    private List<ReportSection> sections = new ArrayList<ReportSection>();

    public String getReportSchemaVersion() {
        return reportSchemaVersion;
    }

    public void setReportSchemaVersion(String reportSchemaVersion) {
        this.reportSchemaVersion = reportSchemaVersion;
    }

    public String getProduct() {
        return product;
    }

    public void setProduct(String product) {
        this.product = product;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }

    public String getAnalysisMode() {
        return analysisMode;
    }

    public void setAnalysisMode(String analysisMode) {
        this.analysisMode = analysisMode;
    }

    public String getReportMode() {
        return reportMode;
    }

    public void setReportMode(String reportMode) {
        this.reportMode = reportMode;
    }

    public TargetInfo getTarget() {
        return target;
    }

    public void setTarget(TargetInfo target) {
        this.target = target;
    }

    public Summary getSummary() {
        return summary;
    }

    public void setSummary(Summary summary) {
        this.summary = summary;
    }

    public List<TimelineEvent> getTimeline() {
        return timeline;
    }

    public void setTimeline(List<TimelineEvent> timeline) {
        this.timeline = timeline;
    }

    public List<EvidenceItem> getEvidence() {
        return evidence;
    }

    public void setEvidence(List<EvidenceItem> evidence) {
        this.evidence = evidence;
    }

    public List<ReportSection> getSections() {
        return sections;
    }

    public void setSections(List<ReportSection> sections) {
        this.sections = sections;
    }

    public ReportSection sectionOfType(String type) {
        if (sections == null) {
            return null;
        }
        for (ReportSection s : sections) {
            if (type.equals(s.getType())) {
                return s;
            }
        }
        return null;
    }
}
