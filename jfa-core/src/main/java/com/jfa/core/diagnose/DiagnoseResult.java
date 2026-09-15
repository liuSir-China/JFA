package com.jfa.core.diagnose;

import com.jfa.common.model.report.DiagnoseReport;

import java.io.File;

public class DiagnoseResult {
    private DiagnoseReport report;
    private File textFile;
    private File jsonFile;
    private String text;
    private String json;
    private int exitCode;

    public DiagnoseReport getReport() {
        return report;
    }

    public void setReport(DiagnoseReport report) {
        this.report = report;
    }

    public File getTextFile() {
        return textFile;
    }

    public void setTextFile(File textFile) {
        this.textFile = textFile;
    }

    public File getJsonFile() {
        return jsonFile;
    }

    public void setJsonFile(File jsonFile) {
        this.jsonFile = jsonFile;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getJson() {
        return json;
    }

    public void setJson(String json) {
        this.json = json;
    }

    public int getExitCode() {
        return exitCode;
    }

    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }
}
