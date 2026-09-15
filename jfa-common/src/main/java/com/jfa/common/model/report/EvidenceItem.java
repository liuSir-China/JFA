package com.jfa.common.model.report;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvidenceItem {
    private String id;
    private String type;
    private String path;
    private boolean usable;
    private String notes;

    public EvidenceItem() {
    }

    public EvidenceItem(String id, String type, String path, boolean usable, String notes) {
        this.id = id;
        this.type = type;
        this.path = path;
        this.usable = usable;
        this.notes = notes;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public boolean isUsable() {
        return usable;
    }

    public void setUsable(boolean usable) {
        this.usable = usable;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
