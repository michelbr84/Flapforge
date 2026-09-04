package io.github.michelbr84.flapforge.app;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
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
    /** Name of the short-lived boot warm-up thread. */
    public static final String BOOT_THREAD_NAME = "flapforge-boot";

    private ExecutorService saveExecutor;
    private final List<Thread> bootThreads = new ArrayList<>();

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
     * Single daemon-thread executor for save writes: every submitted task runs, in order.
     *
     * <p>It used to keep a queue of one with {@code DiscardOldestPolicy}, which collapsed a burst
     * of saves into the newest state — but a discarded task never runs, so its submitter is never
     * told, and the bookkeeping that {@code SaveManager.flush} and
     * {@code SaveManager.pendingWrites} rest on leaked one slot per discard. Coalescing belongs to
     * the component that knows what the write means, so {@code SaveManager} does it (one task in
     * flight, the newest state replacing the queued one) and this executor simply runs what it is
     * given.
     *
     * @return the executor (created lazily, shared)
     */
    public synchronized ExecutorService saveExecutor() {
        if (saveExecutor == null) {
            ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(), daemonFactory(SAVE_THREAD_NAME));
            executor.allowCoreThreadTimeOut(true);
            saveExecutor = executor;
        }
        return saveExecutor;
    }

    /**
     * Executor for the boot warm-up (D19, M2): each task gets a fresh daemon thread that ends
     * with the task, so nothing lingers after the splash screen and the loop thread is never
     * blocked by a warm-up.
     *
     * @return the executor
     */
    public Executor bootExecutor() {
        ThreadFactory factory = daemonFactory(BOOT_THREAD_NAME);
        return body -> {
            Thread thread = factory.newThread(body);
            synchronized (this) {
                bootThreads.removeIf(t -> !t.isAlive());
                bootThreads.add(thread);
            }
            thread.start();
        };
    }

    /**
     * Waits, for at most {@code timeoutMs}, for the boot warm-up to finish.
     *
     * <p>A quit within the first few hundred milliseconds can catch the warm-up while it is still
     * opening the audio device. Letting the shutdown run past it would close an
     * {@link io.github.michelbr84.flapforge.audio.AudioManager} that is about to be handed a
     * freshly opened line, which would leak that line for the life of the JVM — so the loop waits
     * here before closing the audio, and {@code SmokeWindowTest} asserts that no
     * {@code flapforge-} thread outlives the quit path.
     *
     * @param timeoutMs the bounded wait in milliseconds
     * @return {@code true} when no warm-up thread is running any more
     */
    public boolean awaitBootIdle(long timeoutMs) {
        List<Thread> pending;
        synchronized (this) {
            pending = new ArrayList<>(bootThreads);
        }
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMs));
        boolean idle = true;
        for (Thread thread : pending) {
            long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remainingMs <= 0) {
                idle &= !thread.isAlive();
                continue;
            }
            try {
                thread.join(remainingMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            idle &= !thread.isAlive();
        }
        return idle;
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
     * <p>The answer is worth acting on: the thread is a daemon, so a drain that did not finish in
     * time dies with the JVM and takes the last write with it. Callers report that rather than
     * losing it silently.
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
