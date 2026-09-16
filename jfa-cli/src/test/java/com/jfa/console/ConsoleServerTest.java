package com.jfa.console;

import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;
import com.jfa.common.config.JfaConfig;
import com.jfa.common.model.JavaProcessInfo;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ConsoleServerTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private ConsoleServer server;

    @After
    public void tearDown() {
        if (server != null) {
            server.stop();
            server = null;
        }
    }

    @Test
    public void servesUiAndApiCardsSortedAnalyzedFirst() throws Exception {
        start(null);
        HttpURLConnection redir = open("GET", "/jfa", false);
        Assert.assertEquals(302, redir.getResponseCode());
        Assert.assertTrue(redir.getHeaderField("Location").contains("/jfa/"));
        redir.disconnect();

        String html = body(open("GET", "/jfa/", true));
        Assert.assertTrue(html.contains("已分析"));
        Assert.assertFalse(html.contains("点击"));
        String css = body(open("GET", "/jfa/app.css", true));
        Assert.assertTrue(css.contains("repeat(4"));
        String js = body(open("GET", "/jfa/app.js", true));
        Assert.assertTrue(js.contains("首次分析此项目，是否开始分析"));
        Assert.assertFalse(js.contains("点击"));
        Assert.assertFalse(js.contains("pagination"));

        String list = body(open("GET", "/jfa/api/services", true));
        Assert.assertTrue(list.contains("\"analyzed\" : 1") || list.contains("\"analyzed\":1"));
        Assert.assertTrue(list.contains("\"unanalyzed\" : 1") || list.contains("\"unanalyzed\":1"));
        int blue = list.indexOf("\"pid\" : 100");
        if (blue < 0) {
            blue = list.indexOf("\"pid\":100");
        }
        int gray = list.indexOf("\"pid\" : 200");
        if (gray < 0) {
            gray = list.indexOf("\"pid\":200");
        }
        Assert.assertTrue(blue >= 0 && gray >= 0);
        Assert.assertTrue("analyzed pid must sort first", blue < gray);

        String report = body(open("GET", "/jfa/api/services/100/report", true));
        Assert.assertTrue(report.contains("hello-report"));

        HttpURLConnection missing = open("GET", "/jfa/api/services/200/report", true);
        Assert.assertEquals(404, missing.getResponseCode());
        missing.disconnect();
    }

    @Test
    public void analyzeAsyncThenCardBecomesAnalyzed() throws Exception {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        ConsoleJobs.AnalyzeRunner runner = new ConsoleJobs.AnalyzeRunner() {
            @Override
            public void analyze(long pid, boolean confirm) throws Exception {
                Assert.assertTrue(confirm);
                Assert.assertEquals(200L, pid);
                entered.countDown();
                Assert.assertTrue(release.await(8, TimeUnit.SECONDS));
                writeReport(reportRoot, pid, "done-report");
            }
        };
        start(runner);

        HttpURLConnection post = open("POST", "/jfa/api/services/200/analyze", true);
        Assert.assertEquals(200, post.getResponseCode());
        String started = body(post);
        Assert.assertTrue(started.contains("running"));

        Assert.assertTrue(entered.await(4, TimeUnit.SECONDS));
        String status = body(open("GET", "/jfa/api/services/200/status", true));
        Assert.assertTrue(status.contains("running"));
        String listing = body(open("GET", "/jfa/api/services", true));
        Assert.assertTrue(listing.contains("\"analyzing\" : true") || listing.contains("\"analyzing\":true"));

        release.countDown();
        long deadline = System.currentTimeMillis() + 5000L;
        String done = "";
        while (System.currentTimeMillis() < deadline) {
            done = body(open("GET", "/jfa/api/services/200/status", true));
            if (done.contains("done") && (done.contains("\"analyzed\" : true") || done.contains("\"analyzed\":true"))) {
                break;
            }
            Thread.sleep(50L);
        }
        Assert.assertTrue(done.contains("done"));
        String after = body(open("GET", "/jfa/api/services", true));
        Assert.assertTrue(after.contains("\"analyzed\" : 2") || after.contains("\"analyzed\":2"));
        String report = body(open("GET", "/jfa/api/services/200/report", true));
        Assert.assertTrue(report.contains("done-report"));
    }

    @Test
    public void unknownPidAnalyzeIs404() throws Exception {
        start(null);
        HttpURLConnection post = open("POST", "/jfa/api/services/999/analyze", true);
        Assert.assertEquals(404, post.getResponseCode());
        Assert.assertTrue(body(post).contains("E_PID_NOT_FOUND"));
    }

    private File reportRoot;

    private void start(ConsoleJobs.AnalyzeRunner runner) throws Exception {
        File install = tmp.newFolder("inst");
        File confDir = new File(install, "conf");
        Assert.assertTrue(confDir.mkdirs());
        File props = new File(confDir, "jfa.properties");
        Files.write(props.toPath(),
                ("console.port=0\n# bind\nconsole.bind=127.0.0.1\n"
                        + "retention.days=7\nmin.free.bytes=1\nmin.free.ratio=0\ncover.file=false\n")
                        .getBytes(StandardCharsets.UTF_8));
        JfaConfig cfg = JfaConfig.load(props);
        reportRoot = cfg.getReportfileRoot();
        writeReport(reportRoot, 100L, "hello-report");

        final List<JavaProcessInfo> procs = new ArrayList<JavaProcessInfo>();
        procs.add(proc(100L, "com.blue.App", "app"));
        procs.add(proc(200L, "com.gray.App", "app"));
        ConsoleProcessSource.Source source = new ConsoleProcessSource.Source() {
            @Override
            public List<JavaProcessInfo> discover() {
                return procs;
            }

            @Override
            public JavaProcessInfo requirePid(long pid) {
                for (int i = 0; i < procs.size(); i++) {
                    if (procs.get(i).getPid() == pid) {
                        return procs.get(i);
                    }
                }
                throw new JfaException(ErrorCode.E_PID_NOT_FOUND, "pid 不存在: " + pid);
            }
        };
        if (runner == null) {
            runner = new ConsoleJobs.AnalyzeRunner() {
                @Override
                public void analyze(long pid, boolean confirm) {
                    writeReport(reportRoot, pid, "sync");
                }
            };
        }
        server = ConsoleServer.start(cfg, source, runner);
    }

    private static JavaProcessInfo proc(long pid, String main, String user) {
        JavaProcessInfo info = new JavaProcessInfo();
        info.setPid(pid);
        info.setMainClassOrJar(main);
        info.setUser(user);
        info.setJavaCmdSummary("java -jar " + main);
        return info;
    }

    private static void writeReport(File root, long pid, String text) {
        try {
            File dir = new File(root, "pid_" + pid + "/20260916-120000");
            if (!dir.exists() && !dir.mkdirs()) {
                throw new IllegalStateException("mkdir " + dir);
            }
            Files.write(new File(dir, "diagnose-web.md").toPath(), text.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpURLConnection open(String method, String path, boolean follow) throws Exception {
        URL url = new URL("http://127.0.0.1:" + server.getPort() + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method);
        c.setInstanceFollowRedirects(follow);
        c.setConnectTimeout(3000);
        c.setReadTimeout(5000);
        if ("POST".equals(method)) {
            c.setDoOutput(true);
            c.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));
        }
        return c;
    }

    private static String body(HttpURLConnection c) throws Exception {
        InputStream in;
        try {
            in = c.getInputStream();
        } catch (Exception e) {
            in = c.getErrorStream();
        }
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int n;
        while ((n = in.read(buf)) >= 0) {
            out.write(buf, 0, n);
        }
        in.close();
        c.disconnect();
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
