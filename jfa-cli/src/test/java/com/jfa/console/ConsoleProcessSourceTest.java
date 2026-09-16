package com.jfa.console;

import com.jfa.common.model.JavaProcessInfo;
import org.junit.Assert;
import org.junit.Test;

public class ConsoleProcessSourceTest {
    @Test
    public void filtersSelfAndJfaMain() {
        long self = ConsolePidFile.currentPid();
        JavaProcessInfo me = new JavaProcessInfo();
        me.setPid(self);
        me.setMainClassOrJar("org.junit.runner.JUnitCore");
        Assert.assertTrue(ConsoleProcessSource.looksLikeThisConsole(me, self));

        JavaProcessInfo jfa = new JavaProcessInfo();
        jfa.setPid(self + 1);
        jfa.setMainClassOrJar("com.jfa.cli.JfaMain");
        jfa.setJavaCmdSummary("java -jar /opt/jfa/lib/jfa.jar start");
        Assert.assertTrue(ConsoleProcessSource.looksLikeThisConsole(jfa, self));

        JavaProcessInfo app = new JavaProcessInfo();
        app.setPid(self + 2);
        app.setMainClassOrJar("com.example.OrderApp");
        app.setJavaCmdSummary("java -jar order.jar");
        Assert.assertFalse(ConsoleProcessSource.looksLikeThisConsole(app, self));
    }
}
