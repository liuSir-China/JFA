package com.jfa.console;

import com.jfa.common.AnalysisMode;
import com.jfa.common.OutputFormat;
import com.jfa.common.config.JfaConfig;
import com.jfa.core.diagnose.DiagnoseOrchestrator;
import com.jfa.core.diagnose.DiagnoseRequest;
import com.jfa.core.diagnose.DiagnoseResult;

/**
 * Live {@code jfa-analyze} equivalent: {@code diagnose --pid} with UI confirm = {@code --confirm}.
 */
public final class ConsoleAnalyzeRunner {
    private ConsoleAnalyzeRunner() {
    }

    public static ConsoleJobs.AnalyzeRunner live(final JfaConfig config) {
        return new ConsoleJobs.AnalyzeRunner() {
            @Override
            public void analyze(long pid, boolean confirm) {
                DiagnoseRequest req = new DiagnoseRequest();
                req.setConfig(config);
                req.setPid(Long.valueOf(pid));
                req.setMode(AnalysisMode.AUTO);
                req.setConfirm(confirm);
                req.setLiveCollect(true);
                req.setFormat(OutputFormat.BOTH);
                req.setQuiet(true);
                DiagnoseResult result = new DiagnoseOrchestrator().run(req);
                if (result.getExitCode() != 0) {
                    throw new IllegalStateException("analyze 退出码 " + result.getExitCode());
                }
            }
        };
    }
}
