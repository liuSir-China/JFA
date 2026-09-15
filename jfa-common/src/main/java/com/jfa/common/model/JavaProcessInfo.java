package com.jfa.common.model;

public class JavaProcessInfo {
    private long pid;
    private String user;
    private String mainClassOrJar;
    private String javaCmdSummary;
    private String javaHome;
    private String commandLine;
    private String cwd;

    public long getPid() {
        return pid;
    }

    public void setPid(long pid) {
        this.pid = pid;
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getMainClassOrJar() {
        return mainClassOrJar;
    }

    public void setMainClassOrJar(String mainClassOrJar) {
        this.mainClassOrJar = mainClassOrJar;
    }

    public String getJavaCmdSummary() {
        return javaCmdSummary;
    }

    public void setJavaCmdSummary(String javaCmdSummary) {
        this.javaCmdSummary = javaCmdSummary;
    }

    public String getJavaHome() {
        return javaHome;
    }

    public void setJavaHome(String javaHome) {
        this.javaHome = javaHome;
    }

    public String getCommandLine() {
        return commandLine;
    }

    public void setCommandLine(String commandLine) {
        this.commandLine = commandLine;
    }

    public String getCwd() {
        return cwd;
    }

    public void setCwd(String cwd) {
        this.cwd = cwd;
    }
}
