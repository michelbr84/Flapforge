package io.github.michelbr84.flapforge.support;

import java.util.concurrent.Executor;

/** {@link Executor} that runs every task inline on the calling thread. */
public final class DirectExecutor implements Executor {

    private int executed;

    @Override
    public void execute(Runnable command) {
        executed++;
        command.run();
    }

    /**
     * Number of tasks executed so far.
     *
     * @return the count
     */
    public int executed() {
        return executed;
    }
}
