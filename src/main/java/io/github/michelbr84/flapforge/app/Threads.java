package io.github.michelbr84.flapforge.app;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Owner of every thread and executor the application creates (D23). Pure packages receive
 * {@link java.util.concurrent.Executor}s from here by injection and never create threads.
 */
public final class Threads {

    /** Name of the game loop thread. */
    public static final String LOOP_THREAD_NAME = "flapforge-loop";
    /** Name of the save executor thread. */
    public static final String SAVE_THREAD_NAME = "flapforge-save";
    /** Name of the audio mixer thread. */
    public static final String AUDIO_THREAD_NAME = "flapforge-audio";

    private ExecutorService saveExecutor;

    /**
     * Creates the (non-daemon) loop thread. The caller starts it.
     *
     * @param body the loop body
     * @return the unstarted thread
     */
    public Thread loopThread(Runnable body) {
        Thread t = new Thread(body, LOOP_THREAD_NAME);
        t.setDaemon(false);
        return t;
    }

    /**
     * Single daemon-thread executor for save writes with a queue depth of one where the latest
     * submission wins: while one write runs and another waits, a third submission replaces the
     * waiting one.
     *
     * @return the executor (created lazily, shared)
     */
    public synchronized ExecutorService saveExecutor() {
        if (saveExecutor == null) {
            ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(1), daemonFactory(SAVE_THREAD_NAME),
                    new ThreadPoolExecutor.DiscardOldestPolicy());
            executor.allowCoreThreadTimeOut(true);
            saveExecutor = executor;
        }
        return saveExecutor;
    }

    /**
     * Factory for the daemon audio thread.
     *
     * @return the factory
     */
    public ThreadFactory audioThreadFactory() {
        return daemonFactory(AUDIO_THREAD_NAME);
    }

    /**
     * Creates the (daemon) audio thread. The caller starts it.
     *
     * @param body the mixer body
     * @return the unstarted thread
     */
    public Thread audioThread(Runnable body) {
        return audioThreadFactory().newThread(body);
    }

    /**
     * Stops the save executor, waiting up to {@code timeoutMs} for queued writes to finish.
     *
     * @param timeoutMs the bounded wait in milliseconds
     * @return {@code true} when everything terminated in time (or nothing was started)
     */
    public boolean shutdown(long timeoutMs) {
        ExecutorService executor;
        synchronized (this) {
            executor = saveExecutor;
        }
        if (executor == null) {
            return true;
        }
        executor.shutdown();
        try {
            return executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static ThreadFactory daemonFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }
}
