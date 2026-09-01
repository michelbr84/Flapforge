package io.github.michelbr84.flapforge.event;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The synchronous, single-threaded event bus of the presentation layer (D16).
 *
 * <p>Delivery is by exact event type: a listener registered for
 * {@link GameEvent.GatePassed} sees gate events and nothing else. A listener registered for
 * {@link GameEvent} itself sees every event, which is what the debug overlay uses.
 *
 * <p>The bus is confined to one thread — the game loop in production, the test thread in tests.
 * The first {@link #subscribe} or {@link #publish} claims ownership; every later call from another
 * thread fails fast with a message naming both threads, because a listener that touches renderer
 * or mixer state from a foreign thread is a race that would otherwise show up as a rare visual
 * glitch. {@link #adopt()} hands ownership to the current thread when the loop takes over a bus
 * that was built during start-up.
 *
 * <p>Publishing from inside a listener is allowed and ordered: the nested event is queued and
 * drained by the outermost {@link #publish} call, first in, first out, so listeners always observe
 * events in the order they were published and the stack never nests.
 */
public final class EventBus {

    private final Map<Class<?>, CopyOnWriteArrayList<Consumer<?>>> listeners =
            new ConcurrentHashMap<>();
    private final Deque<GameEvent> pending = new ArrayDeque<>();
    private volatile Thread owner;
    private boolean dispatching;

    /** A registration that can be cancelled. */
    @FunctionalInterface
    public interface Subscription {

        /** Removes the listener. Calling it twice is harmless. */
        void cancel();
    }

    /**
     * Registers a listener for one event type.
     *
     * @param type the event class, or {@link GameEvent} to receive everything
     * @param listener the listener
     * @param <T> the event type
     * @return a handle that removes the listener again
     */
    public <T extends GameEvent> Subscription subscribe(Class<T> type,
            Consumer<? super T> listener) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        claim();
        listeners.computeIfAbsent(type, t -> new CopyOnWriteArrayList<>()).add(listener);
        return () -> unsubscribe(type, listener);
    }

    /**
     * Removes a listener.
     *
     * @param type the event class it was registered for
     * @param listener the listener
     * @param <T> the event type
     * @return {@code true} when a registration was removed
     */
    public <T extends GameEvent> boolean unsubscribe(Class<T> type, Consumer<? super T> listener) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        claim();
        CopyOnWriteArrayList<Consumer<?>> registered = listeners.get(type);
        return registered != null && registered.remove(listener);
    }

    /**
     * Delivers an event to its listeners, draining anything they publish in turn.
     *
     * @param event the event
     */
    public void publish(GameEvent event) {
        Objects.requireNonNull(event, "event");
        claim();
        pending.addLast(event);
        if (dispatching) {
            return;
        }
        dispatching = true;
        try {
            GameEvent next;
            while ((next = pending.pollFirst()) != null) {
                dispatch(next);
            }
        } finally {
            pending.clear();
            dispatching = false;
        }
    }

    /**
     * How many listeners are registered for a type.
     *
     * @param type the event class
     * @return the count
     */
    public int subscriberCount(Class<? extends GameEvent> type) {
        CopyOnWriteArrayList<Consumer<?>> registered = listeners.get(type);
        return registered == null ? 0 : registered.size();
    }

    /**
     * Removes every listener (screen teardown, tests).
     */
    public void clear() {
        claim();
        listeners.clear();
        pending.clear();
    }

    /**
     * Makes the current thread the owner. Call it once from the thread that will drive the bus.
     */
    public void adopt() {
        owner = Thread.currentThread();
    }

    /**
     * The thread the bus is confined to.
     *
     * @return the owner, or {@code null} before the first use
     */
    public Thread owner() {
        return owner;
    }

    private void dispatch(GameEvent event) {
        deliver(listeners.get(event.getClass()), event);
        deliver(listeners.get(GameEvent.class), event);
    }

    @SuppressWarnings("unchecked")
    private static void deliver(List<Consumer<?>> registered, GameEvent event) {
        if (registered == null) {
            return;
        }
        for (Consumer<?> listener : registered) {
            ((Consumer<GameEvent>) listener).accept(event);
        }
    }

    private void claim() {
        Thread current = Thread.currentThread();
        Thread holder = owner;
        if (holder == null) {
            owner = current;
            return;
        }
        if (holder != current) {
            throw new IllegalStateException("EventBus is confined to thread '" + holder.getName()
                    + "' but was used from '" + current.getName()
                    + "'; publish presentation events from the loop thread only");
        }
    }
}
