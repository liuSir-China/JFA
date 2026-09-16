package com.jfa.console;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jfa.common.ErrorCode;
import com.jfa.common.JfaException;
import com.jfa.common.config.JfaConfig;
import com.jfa.common.json.JsonSupport;
import com.jfa.common.model.JavaProcessInfo;
import com.jfa.core.io.FileSupport;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JDK built-in {@link HttpServer} web console. Static UI from classpath {@code /web/}
 * is served under {@code /jfa}; JSON APIs under {@code /jfa/api/}.
 */
public final class ConsoleServer {
    public static final String UI_PATH = "/jfa";
    public static final String API_PREFIX = "/jfa/api";
    private static final String WEB_ROOT = "/web/";

    private final JfaConfig config;
    private final ConsoleProcessSource.Source processes;
    private final ConsoleJobs jobs;
    private final ObjectMapper mapper = JsonSupport.mapper();
    private final CountDownLatch stopped = new CountDownLatch(1);
    private HttpServer http;
    private ExecutorService httpPool;
    private volatile boolean running;

    public static ConsoleServer start(JfaConfig config) throws IOException {
        return start(config, ConsoleProcessSource.live(), ConsoleAnalyzeRunner.live(config));
    }

    public static ConsoleServer start(JfaConfig config, ConsoleProcessSource.Source processes,
            ConsoleJobs.AnalyzeRunner analyze) throws IOException {
        ConsoleServer server = new ConsoleServer(config, processes, analyze);
        server.bind();
        return server;
    }

    ConsoleServer(JfaConfig config, ConsoleProcessSource.Source processes, ConsoleJobs.AnalyzeRunner analyze) {
        this.config = config;
        this.processes = processes;
        this.jobs = new ConsoleJobs(analyze);
    }

    private void bind() throws IOException {
        String bind = config.getConsoleBind();
        int port = config.getConsolePort();
        InetSocketAddress addr;
        try {
            if (bind == null || bind.trim().isEmpty() || "0.0.0.0".equals(bind.trim())) {
                addr = new InetSocketAddress(port);
            } else {
                addr = new InetSocketAddress(bind.trim(), port);
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("无效 console.bind/console.port: " + bind + ":" + port, e);
        }
        http = HttpServer.create(addr, 0);
        http.createContext(API_PREFIX, new ApiHandler());
        http.createContext(UI_PATH, new StaticHandler());
        http.createContext("/", new RootHandler());
        httpPool = Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "jfa-console-http-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
        http.setExecutor(httpPool);
        http.start();
        running = true;
    }

    public int getPort() {
        if (http == null) {
            return config.getConsolePort();
        }
        InetSocketAddress addr = http.getAddress();
        return addr == null ? config.getConsolePort() : addr.getPort();
    }

    public String getBind() {
        return config.getConsoleBind();
    }

    public String browseUrl() {
        String host = config.getConsoleBind();
        if (host == null || host.trim().isEmpty() || "0.0.0.0".equals(host) || "*".equals(host)) {
            host = guessReachableHost();
        }
        return "http://" + host + ":" + getPort() + UI_PATH;
    }

    public static String guessReachableHost() {
        try {
            InetAddress local = InetAddress.getLocalHost();
            if (local != null && !local.isLoopbackAddress()) {
                String ip = local.getHostAddress();
                if (ip != null && !ip.isEmpty() && !"127.0.0.1".equals(ip)) {
                    return ip;
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        return "127.0.0.1";
    }

    public void join() throws InterruptedException {
        stopped.await();
    }

    public synchronized void stop() {
        if (!running && http == null) {
            stopped.countDown();
            return;
        }
        running = false;
        if (http != null) {
            http.stop(0);
            http = null;
        }
        jobs.shutdown();
        if (httpPool != null) {
            httpPool.shutdownNow();
            httpPool = null;
        }
        stopped.countDown();
    }

    JfaConfig config() {
        return config;
    }

    private final class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = pathOf(ex);
            if ("/".equals(path)) {
                redirect(ex, UI_PATH + "/");
                return;
            }
            sendJson(ex, 404, errorBody(ErrorCode.E_USAGE, "Not found"));
        }
    }

    private final class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                sendJson(ex, 405, errorBody(ErrorCode.E_USAGE, "Method not allowed"));
                return;
            }
            String path = pathOf(ex);
            if (UI_PATH.equals(path) && !path.endsWith("/")) {
                redirect(ex, UI_PATH + "/");
                return;
            }
            String rel = path.substring(UI_PATH.length());
            if (rel.startsWith("/")) {
                rel = rel.substring(1);
            }
            if (rel.isEmpty()) {
                rel = "index.html";
            }
            if (rel.contains("..") || rel.indexOf('\\') >= 0 || rel.indexOf(':') >= 0) {
                sendJson(ex, 404, errorBody(ErrorCode.E_USAGE, "Not found"));
                return;
            }
            if (!rel.matches("[A-Za-z0-9._-]+")) {
                sendJson(ex, 404, errorBody(ErrorCode.E_USAGE, "Not found"));
                return;
            }
            byte[] body = readClasspath(WEB_ROOT + rel);
            if (body == null) {
                sendJson(ex, 404, errorBody(ErrorCode.E_USAGE, "Not found"));
                return;
            }
            sendBytes(ex, 200, mime(rel), body, !"HEAD".equalsIgnoreCase(method));
        }
    }

    private final class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            drain(ex);
            try {
                routeApi(ex);
            } catch (JfaException e) {
                sendJson(ex, statusFor(e.getErrorCode()), errorBody(e.getErrorCode(), e.getMessage()));
            } catch (Exception e) {
                String msg = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
                sendJson(ex, 500, errorBody(ErrorCode.E_INTERNAL, msg));
            }
        }
    }

    private void routeApi(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = pathOf(ex);
        if (path.endsWith("/") && path.length() > 1) {
            path = path.substring(0, path.length() - 1);
        }
        if ((API_PREFIX + "/services").equals(path) && "GET".equalsIgnoreCase(method)) {
            sendJson(ex, 200, listServices());
            return;
        }
        String prefix = API_PREFIX + "/services/";
        if (path.startsWith(prefix)) {
            String rest = path.substring(prefix.length());
            int slash = rest.indexOf('/');
            String pidToken = slash < 0 ? rest : rest.substring(0, slash);
            String tail = slash < 0 ? "" : rest.substring(slash + 1);
            long pid;
            try {
                pid = Long.parseLong(pidToken);
            } catch (NumberFormatException e) {
                sendJson(ex, 400, errorBody(ErrorCode.E_USAGE, "pid 必须是数字"));
                return;
            }
            if ("report".equals(tail) && "GET".equalsIgnoreCase(method)) {
                Map<String, Object> report = latestReport(pid);
                if (report == null) {
                    sendJson(ex, 404, errorBody(ErrorCode.E_USAGE, "尚无分析报告: pid " + pid));
                    return;
                }
                sendJson(ex, 200, report);
                return;
            }
            if ("analyze".equals(tail) && "POST".equalsIgnoreCase(method)) {
                sendJson(ex, 200, startAnalyze(pid));
                return;
            }
            if ("status".equals(tail) && "GET".equalsIgnoreCase(method)) {
                sendJson(ex, 200, status(pid));
                return;
            }
        }
        sendJson(ex, 404, errorBody(ErrorCode.E_USAGE, "Not found"));
    }

    private Map<String, Object> listServices() {
        List<JavaProcessInfo> found = processes.discover();
        File root = config.getReportfileRoot();
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        int analyzed = 0;
        int unanalyzed = 0;
        for (int i = 0; i < found.size(); i++) {
            JavaProcessInfo info = found.get(i);
            boolean has = ReportCatalog.hasReport(root, info.getPid());
            boolean analyzing = jobs.isRunning(info.getPid());
            if (has) {
                analyzed++;
            } else {
                unanalyzed++;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("pid", Long.valueOf(info.getPid()));
            row.put("user", info.getUser());
            row.put("main", displayMain(info));
            row.put("cmd", info.getJavaCmdSummary() == null ? "" : info.getJavaCmdSummary());
            row.put("analyzed", Boolean.valueOf(has));
            row.put("analyzing", Boolean.valueOf(analyzing));
            rows.add(row);
        }
        Collections.sort(rows, new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                boolean aa = Boolean.TRUE.equals(a.get("analyzed"));
                boolean ba = Boolean.TRUE.equals(b.get("analyzed"));
                if (aa != ba) {
                    return aa ? -1 : 1;
                }
                long pa = ((Number) a.get("pid")).longValue();
                long pb = ((Number) b.get("pid")).longValue();
                return Long.compare(pa, pb);
            }
        });
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("analyzed", Integer.valueOf(analyzed));
        out.put("unanalyzed", Integer.valueOf(unanalyzed));
        out.put("services", rows);
        return out;
    }

    private Map<String, Object> latestReport(long pid) {
        File file = ReportCatalog.latestTextFile(config.getReportfileRoot(), pid);
        if (file == null) {
            return null;
        }
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("pid", Long.valueOf(pid));
        out.put("path", file.getAbsolutePath());
        out.put("text", FileSupport.readUtf8(file));
        return out;
    }

    private Map<String, Object> startAnalyze(long pid) {
        processes.requirePid(pid);
        ConsoleJobs.Job job = jobs.start(pid, true);
        return jobView(pid, job);
    }

    private Map<String, Object> status(long pid) {
        ConsoleJobs.Job job = jobs.get(pid);
        return jobView(pid, job);
    }

    private Map<String, Object> jobView(long pid, ConsoleJobs.Job job) {
        boolean has = ReportCatalog.hasReport(config.getReportfileRoot(), pid);
        ConsoleJobs.Status st = job == null ? ConsoleJobs.Status.IDLE : job.status;
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("pid", Long.valueOf(pid));
        out.put("status", st.name().toLowerCase());
        out.put("analyzing", Boolean.valueOf(st == ConsoleJobs.Status.RUNNING));
        out.put("analyzed", Boolean.valueOf(has));
        if (job != null && job.error != null) {
            out.put("error", job.error);
        }
        return out;
    }

    static String displayMain(JavaProcessInfo info) {
        String m = info.getMainClassOrJar();
        if (m == null || m.trim().isEmpty()) {
            return "pid " + info.getPid();
        }
        int slash = Math.max(m.lastIndexOf('/'), m.lastIndexOf('\\'));
        return slash >= 0 ? m.substring(slash + 1) : m;
    }

    private static String pathOf(HttpExchange ex) {
        URI uri = ex.getRequestURI();
        String raw = uri.getRawPath();
        if (raw == null) {
            raw = uri.getPath();
        }
        if (raw == null || raw.isEmpty()) {
            return "/";
        }
        try {
            return URLDecoder.decode(raw, "UTF-8");
        } catch (Exception e) {
            return raw;
        }
    }

    private static int statusFor(ErrorCode code) {
        if (code == ErrorCode.E_PID_NOT_FOUND || code == ErrorCode.E_SERVICE_NOT_FOUND) {
            return 404;
        }
        if (code == ErrorCode.E_USAGE) {
            return 400;
        }
        if (code == ErrorCode.E_PERM_DISCOVERY || code == ErrorCode.E_PERM_ATTACH) {
            return 403;
        }
        return 500;
    }

    private Map<String, String> errorBody(ErrorCode code, String message) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        m.put("error_code", code.code());
        m.put("message", message);
        return m;
    }

    private void sendJson(HttpExchange ex, int code, Object body) throws IOException {
        byte[] raw;
        try {
            raw = mapper.writeValueAsBytes(body);
        } catch (Exception e) {
            raw = "{\"error_code\":\"E_INTERNAL\",\"message\":\"json\"}".getBytes(StandardCharsets.UTF_8);
            code = 500;
        }
        sendBytes(ex, code, "application/json; charset=UTF-8", raw, true);
    }

    private static void redirect(HttpExchange ex, String location) throws IOException {
        drain(ex);
        Headers h = ex.getResponseHeaders();
        h.set("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    private static void sendBytes(HttpExchange ex, int code, String contentType, byte[] body, boolean writeBody)
            throws IOException {
        drain(ex);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", contentType);
        h.set("Cache-Control", "no-store");
        h.set("X-Content-Type-Options", "nosniff");
        int len = body == null ? 0 : body.length;
        ex.sendResponseHeaders(code, writeBody ? len : -1);
        if (writeBody && len > 0) {
            OutputStream os = ex.getResponseBody();
            try {
                os.write(body);
            } finally {
                os.close();
            }
        } else {
            ex.close();
        }
    }

    private static void drain(HttpExchange ex) {
        InputStream in = ex.getRequestBody();
        if (in == null) {
            return;
        }
        byte[] buf = new byte[512];
        try {
            while (in.read(buf) >= 0) {
                // discard
            }
        } catch (IOException ignored) {
            // ignore
        }
    }

    private static byte[] readClasspath(String name) {
        InputStream in = ConsoleServer.class.getResourceAsStream(name);
        if (in == null) {
            return null;
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) >= 0) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    private static String mime(String name) {
        String lower = name.toLowerCase();
        if (lower.endsWith(".html") || lower.endsWith(".htm")) {
            return "text/html; charset=UTF-8";
        }
        if (lower.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (lower.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".ico")) {
            return "image/x-icon";
        }
        return "application/octet-stream";
    }
}
