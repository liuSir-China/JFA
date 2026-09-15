package com.jfa.common.model.report;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimelineEvent {
    private String at;
    private String event;
    private String source;

    public TimelineEvent() {
    }

    public TimelineEvent(String at, String event, String source) {
        this.at = at;
        this.event = event;
        this.source = source;
    }

    public String getAt() {
        return at;
    }

    public void setAt(String at) {
        this.at = at;
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = event;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
