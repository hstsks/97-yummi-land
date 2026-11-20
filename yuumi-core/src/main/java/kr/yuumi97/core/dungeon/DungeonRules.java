package kr.yuumi97.core.dungeon;

/** Deterministic rules shared by the live dungeon and the standalone smoke check. */
public final class DungeonRules {
    private DungeonRules() {}
    public static double healthScale(int players) {
        if (players < 1 || players > 8) throw new IllegalArgumentException("party must contain 1..8 players");
        return 1.0 + 0.55 * (players - 1);
    }
    public static int waveSize(int players) { healthScale(players); return 4 + 2 * ((players - 1) / 2); }
    public static boolean expired(long now, long deadline) { return now >= deadline; }
    public static int coins(int base, double multiplier) {
        return Math.max(0, (int) Math.floor(base * Math.max(0, multiplier) + 0.000001));
    }
    public record HealthHit(double remaining, boolean intercept, double nativeHealthBeforeDamage) {}
    public static HealthHit healthHit(double logicalHealth, double nativeHealth, double damage) {
        double remaining = Math.max(0, logicalHealth - damage);
        boolean intercept = remaining > 0 && damage >= nativeHealth;
        double before = remaining <= 0 && damage > 0 ? Math.min(nativeHealth, damage) : nativeHealth;
        return new HealthHit(remaining, intercept, before);
    }
}
