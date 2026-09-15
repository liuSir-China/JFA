package com.jfa.common.model.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Summary {
    @JsonProperty("fault_kinds")
    private List<String> faultKinds = new ArrayList<String>();
    @JsonProperty("fault_kinds_note")
    private String faultKindsNote;
    @JsonProperty("one_line")
    private String oneLine;
    @JsonProperty("overall_confidence")
    private String overallConfidence;
    private Health health = new Health();
    @JsonProperty("fabricated_root_cause")
    private boolean fabricatedRootCause;

    public List<String> getFaultKinds() {
        return faultKinds;
    }

    public void setFaultKinds(List<String> faultKinds) {
        this.faultKinds = faultKinds;
    }

    public String getFaultKindsNote() {
        return faultKindsNote;
    }

    public void setFaultKindsNote(String faultKindsNote) {
        this.faultKindsNote = faultKindsNote;
    }

    public String getOneLine() {
        return oneLine;
    }

    public void setOneLine(String oneLine) {
        this.oneLine = oneLine;
    }

    public String getOverallConfidence() {
        return overallConfidence;
    }

    public void setOverallConfidence(String overallConfidence) {
        this.overallConfidence = overallConfidence;
    }

    public Health getHealth() {
        return health;
    }

    public void setHealth(Health health) {
        this.health = health;
    }

    public boolean isFabricatedRootCause() {
        return fabricatedRootCause;
    }

    public void setFabricatedRootCause(boolean fabricatedRootCause) {
        this.fabricatedRootCause = fabricatedRootCause;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Health {
        @JsonProperty("deadlock_found")
        private Boolean deadlockFound;
        @JsonProperty("heap_oom_evidence_found")
        private Boolean heapOomEvidenceFound;
        @JsonProperty("risk_hints")
        private List<String> riskHints = new ArrayList<String>();

        public Boolean getDeadlockFound() {
            return deadlockFound;
        }

        public void setDeadlockFound(Boolean deadlockFound) {
            this.deadlockFound = deadlockFound;
        }

        public Boolean getHeapOomEvidenceFound() {
            return heapOomEvidenceFound;
        }

        public void setHeapOomEvidenceFound(Boolean heapOomEvidenceFound) {
            this.heapOomEvidenceFound = heapOomEvidenceFound;
        }

        public List<String> getRiskHints() {
            return riskHints;
        }

        public void setRiskHints(List<String> riskHints) {
            this.riskHints = riskHints;
        }
    }
}
