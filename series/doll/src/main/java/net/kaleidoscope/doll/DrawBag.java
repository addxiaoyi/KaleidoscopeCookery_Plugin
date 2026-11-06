package net.kaleidoscope.doll;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.random.RandomGenerator;

/** A tier exhausts its shuffled pool before it can repeat a doll. */
public final class DrawBag {
    private final List<String> pool;
    private final List<String> remaining = new ArrayList<>();

    public DrawBag(List<String> pool) {
        if (pool.isEmpty() || new HashSet<>(pool).size() != pool.size()) {
            throw new IllegalArgumentException("Doll pool must be nonempty and unique");
        }
        pool.forEach(net.kaleidoscope.series.ItemIds::require);
        this.pool = List.copyOf(pool);
    }

    public String peek(RandomGenerator random) {
        if (remaining.isEmpty()) {
            remaining.addAll(pool);
            for (int i = remaining.size() - 1; i > 0; i--) {
                java.util.Collections.swap(remaining, i, random.nextInt(i + 1));
            }
        }
        return remaining.getFirst();
    }

    public void take(String expected) {
        if (remaining.isEmpty() || !remaining.getFirst().equals(expected)) {
            throw new IllegalStateException("Lottery reservation changed");
        }
        remaining.removeFirst();
    }

    public List<String> snapshot() { return List.copyOf(remaining); }

    public void restore(List<String> saved) {
        if (new HashSet<>(saved).size() != saved.size() || !pool.containsAll(saved)) {
            throw new IllegalArgumentException("Invalid saved doll pool");
        }
        remaining.clear();
        remaining.addAll(saved);
    }
}
