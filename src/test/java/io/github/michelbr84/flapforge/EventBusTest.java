package io.github.michelbr84.flapforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.michelbr84.flapforge.event.EventBus;
import io.github.michelbr84.flapforge.event.GameEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Delivery, unsubscription, re-entrancy and thread confinement of the bus (D16). */
class EventBusTest {

    private final EventBus bus = new EventBus();
    private final List<String> seen = new ArrayList<>();

    private static GameEvent.GatePassed gate(int n) {
        return new GameEvent.GatePassed(n, true);
    }

    @Test
    void eventsGoOnlyToListenersOfTheirType() {
        bus.subscribe(GameEvent.GatePassed.class, e -> seen.add("gate " + e.gatesPassed()));
        bus.subscribe(GameEvent.Flapped.class, e -> seen.add("flap"));

        bus.publish(gate(1));
        bus.publish(new GameEvent.CoinCollected(1, 1));

        assertEquals(List.of("gate 1"), seen, "an unrelated type must not be delivered");
    }

    @Test
    void everyListenerOfATypeIsCalledInSubscriptionOrder() {
        bus.subscribe(GameEvent.GatePassed.class, e -> seen.add("first"));
        bus.subscribe(GameEvent.GatePassed.class, e -> seen.add("second"));
        bus.subscribe(GameEvent.class, e -> seen.add("catch-all"));

        bus.publish(gate(1));

        assertEquals(List.of("first", "second", "catch-all"), seen);
        assertEquals(2, bus.subscriberCount(GameEvent.GatePassed.class));
    }

    @Test
    void unsubscribingStopsDelivery() {
        Consumer<GameEvent.GatePassed> listener = e -> seen.add("gate");
        EventBus.Subscription handle = bus.subscribe(GameEvent.GatePassed.class, listener);

        bus.publish(gate(1));
        handle.cancel();
        bus.publish(gate(2));

        assertEquals(List.of("gate"), seen);
        assertEquals(0, bus.subscriberCount(GameEvent.GatePassed.class));
        assertFalse(bus.unsubscribe(GameEvent.GatePassed.class, listener),
                "cancelling twice is harmless");
    }

    @Test
    void unsubscribeByListenerWorksToo() {
        Consumer<GameEvent.Flapped> listener = e -> seen.add("flap");
        bus.subscribe(GameEvent.Flapped.class, listener);

        assertTrue(bus.unsubscribe(GameEvent.Flapped.class, listener));
        bus.publish(new GameEvent.Flapped(false));

        assertEquals(List.of(), seen);
    }

    @Test
    void reentrantPublishesAreDrainedFirstInFirstOut() {
        bus.subscribe(GameEvent.class, e -> seen.add(label(e)));
        bus.subscribe(GameEvent.GatePassed.class, e -> {
            if (e.gatesPassed() == 1) {
                bus.publish(new GameEvent.CoinCollected(1, 1));
                bus.publish(new GameEvent.Flapped(false));
            }
        });
        bus.subscribe(GameEvent.CoinCollected.class,
                e -> bus.publish(new GameEvent.StreakChanged(3, 1)));

        bus.publish(gate(1));

        assertEquals(List.of("gate", "coin", "flap", "streak"), seen,
                "nested publishes are queued, not delivered depth first");
    }

    @Test
    void aListenerAddedWhileDispatchingDoesNotSeeTheCurrentEvent() {
        bus.subscribe(GameEvent.GatePassed.class, e -> {
            if (e.gatesPassed() == 1) {
                bus.subscribe(GameEvent.GatePassed.class, later -> seen.add("late"));
            }
        });

        bus.publish(gate(1));
        assertEquals(List.of(), seen);
        bus.publish(gate(2));
        assertEquals(List.of("late"), seen);
    }

    @Test
    void theBusRefusesAnotherThread() throws InterruptedException {
        bus.subscribe(GameEvent.Flapped.class, e -> seen.add("flap"));
        BlockingQueue<Object> result = new ArrayBlockingQueue<>(1);

        Thread other = new Thread(() -> {
            try {
                bus.publish(new GameEvent.Flapped(false));
                result.add("published");
            } catch (RuntimeException e) {
                result.add(e);
            }
        }, "foreign");
        other.start();
        other.join(2_000L);

        Object outcome = result.poll(2, TimeUnit.SECONDS);
        assertTrue(outcome instanceof IllegalStateException, "expected a guard, got " + outcome);
        assertTrue(((IllegalStateException) outcome).getMessage().contains("foreign"),
                ((IllegalStateException) outcome).getMessage());
        assertEquals(List.of(), seen);
    }

    @Test
    void adoptHandsTheBusToTheCurrentThread() {
        bus.publish(new GameEvent.Flapped(false));
        assertEquals(Thread.currentThread(), bus.owner());
        bus.adopt();
        assertEquals(Thread.currentThread(), bus.owner());
    }

    @Test
    void clearRemovesEveryListener() {
        bus.subscribe(GameEvent.class, e -> seen.add("any"));
        bus.clear();

        bus.publish(gate(1));

        assertEquals(List.of(), seen);
        assertEquals(0, bus.subscriberCount(GameEvent.class));
    }

    @Test
    void everyEventTypeCanBePublished() {
        List<GameEvent> all = List.of(
                new GameEvent.RunStarted("classic", "green_fields", "normal", 42L),
                gate(1),
                new GameEvent.CoinCollected(1, 1),
                new GameEvent.Flapped(true),
                new GameEvent.AbilityActivated("shield", 1),
                new GameEvent.AbilityReady("shield"),
                new GameEvent.ShieldAbsorbed(0),
                new GameEvent.Revived(0),
                new GameEvent.NearMiss(3),
                new GameEvent.StreakChanged(3, 1),
                new GameEvent.SynergyActivated("coin_engine"),
                new GameEvent.Crashed("PIPE", 7),
                new GameEvent.RunEnded(7, 7, 420L, false),
                new GameEvent.ModifierOffered(List.of("tailwind"), 10),
                new GameEvent.ModifierChosen("tailwind", 10),
                new GameEvent.BossWarning("green_boss", 1),
                new GameEvent.BossStarted("green_boss"),
                new GameEvent.BossCleared("green_boss", "green_fields"),
                new GameEvent.RuleShift(List.of("LETHAL_CEILING")),
                new GameEvent.ObjectiveMet("no_shield_1"),
                new GameEvent.CurrencyChanged("coins", 50L, 50L),
                new GameEvent.XpGained(10L, 10L),
                new GameEvent.LevelUp(2),
                new GameEvent.AchievementUnlocked("first_flight"),
                new GameEvent.UnlockGranted("bird:ironbeak"),
                new GameEvent.ChallengeCompleted("no_shield_1", true),
                new GameEvent.DailyRecorded("2026-09-01", 12),
                new GameEvent.SettingsChanged(
                        new io.github.michelbr84.flapforge.persistence.Settings()),
                new GameEvent.LanguageChanged("pt_BR"),
                new GameEvent.ScreenChanged("MainMenuScreen"),
                new GameEvent.SaveFailed("settings.json", "disk full"));
        bus.subscribe(GameEvent.class, e -> seen.add(e.getClass().getSimpleName()));

        all.forEach(bus::publish);

        assertEquals(31, all.size(), "D16 lists 31 events");
        assertEquals(all.size(), seen.size());
        assertEquals(all.size(), seen.stream().distinct().count(), "each type appears once");
    }

    private static String label(GameEvent event) {
        if (event instanceof GameEvent.GatePassed) {
            return "gate";
        }
        if (event instanceof GameEvent.CoinCollected) {
            return "coin";
        }
        if (event instanceof GameEvent.Flapped) {
            return "flap";
        }
        if (event instanceof GameEvent.StreakChanged) {
            return "streak";
        }
        return event.getClass().getSimpleName();
    }
}
