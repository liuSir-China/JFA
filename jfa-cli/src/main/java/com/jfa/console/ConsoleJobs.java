package com.jfa.console;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory async analyze jobs keyed by pid (UI confirm maps to {@code --confirm}).
 */
public final class ConsoleJobs {
    public enum Status {
        IDLE, RUNNING, DONE, ERROR
    }

    public static final class Job {
        public final long pid;
        public volatile Status status;
        public volatile String error;
        public final long startedAtMs;
        public volatile long finishedAtMs;

        Job(long pid) {
            this.pid = pid;
            this.status = Status.RUNNING;
            this.startedAtMs = System.currentTimeMillis();
        }
    }

    public interface AnalyzeRunner {
        void analyze(long pid, boolean confirm) throws Exception;
    }

    private final ConcurrentHashMap<Long, Job> jobs = new ConcurrentHashMap<Long, Job>();
    private final ExecutorService pool;
    private final AnalyzeRunner runner;

    public ConsoleJobs(AnalyzeRunner runner) {
        this.runner = runner;
        this.pool = Executors.newCachedThreadPool(new ThreadFactory() {
            private final AtomicInteger n = new AtomicInteger();

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "jfa-console-analyze-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        });
    }

    public Job get(long pid) {
        return jobs.get(Long.valueOf(pid));
    }

    public boolean isRunning(long pid) {
        Job j = jobs.get(Long.valueOf(pid));
        return j != null && j.status == Status.RUNNING;
    }

    public Status statusOf(long pid) {
        Job j = jobs.get(Long.valueOf(pid));
        return j == null ? Status.IDLE : j.status;
    }

    /**
     * Start analyze for {@code pid}. If already running, returns the existing job.
     */
    public Job start(final long pid, final boolean confirm) {
        Long key = Long.valueOf(pid);
        Job existing = jobs.get(key);
        if (existing != null && existing.status == Status.RUNNING) {
            return existing;
        }
        final Job job = new Job(pid);
        jobs.put(key, job);
        pool.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    runner.analyze(pid, confirm);
                    job.status = Status.DONE;
                } catch (Throwable t) {
                    job.status = Status.ERROR;
                    job.error = t.getMessage() == null ? t.getClass().getName() : t.getMessage();
                } finally {
                    job.finishedAtMs = System.currentTimeMillis();
                }
            }
        });
        return job;
    }

    public void shutdown() {
        pool.shutdownNow();
    }
}
