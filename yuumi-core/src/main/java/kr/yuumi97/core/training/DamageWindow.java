package kr.yuumi97.core.training;

import java.util.ArrayDeque;

/** A monotonic, fixed five-second window. Each instance belongs to one player and one dummy. */
public final class DamageWindow {
    public static final long WINDOW_NANOS = 5_000_000_000L;
    private record Hit(long at, double damage) {}
    private final ArrayDeque<Hit> hits = new ArrayDeque<>();
    private double total;
    private double lastDamage;

    public void record(long now, double damage) {
        prune(now);
        if (!Double.isFinite(damage) || damage <= 0) return;
        hits.addLast(new Hit(now, damage));
        total += damage;
        lastDamage = damage;
    }

    public double dps(long now) { prune(now); return total / 5.0; }
    public double total(long now) { prune(now); return total; }
    public double lastDamage() { return lastDamage; }
    public boolean isActive(long now) { prune(now); return !hits.isEmpty(); }

    private void prune(long now) {
        while (!hits.isEmpty() && now - hits.getFirst().at >= WINDOW_NANOS) total -= hits.removeFirst().damage;
        if (hits.isEmpty()) total = 0;
    }
}
