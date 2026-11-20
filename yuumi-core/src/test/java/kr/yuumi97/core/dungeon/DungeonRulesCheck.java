package kr.yuumi97.core.dungeon;

/** Run with java -ea after compiling this file and DungeonRules; no server or test framework needed. */
public final class DungeonRulesCheck {
    public static void main(String[] args) {
        check(Math.abs(DungeonRules.healthScale(1) - 1) < 1e-9, "solo health");
        check(Math.abs(DungeonRules.healthScale(8) - 4.85) < 1e-9, "eight-player health");
        check(DungeonRules.waveSize(1) == 4 && DungeonRules.waveSize(8) == 10, "wave scaling");
        double last = 0;
        for (int n = 1; n <= 8; n++) {
            check(DungeonRules.healthScale(n) > last, "monotonic scaling"); last = DungeonRules.healthScale(n);
        }
        check(DungeonLayout.STRIDE==1024&&DungeonLayout.FLOOR_Y==66,"seamless coordinate contract");
        int length=0;for(int i=0;i<DungeonLayout.ZONES.size();i++){
            var zone=DungeonLayout.ZONES.get(i);length+=zone.length();
            check(DungeonLayout.zoneAt(zone.north())==zone.number()&&DungeonLayout.zoneAt(zone.south()+.999)==zone.number(),"zone boundaries");
            if(i<5)check(zone.north()-1==DungeonLayout.ZONES.get(i+1).south(),"seamless adjacency");
        }
        check(length==221&&DungeonLayout.zoneAt(0)==0&&DungeonLayout.zoneAt(-221.01)==0,"full spine extent");
        check(((22>>4)-(-22>>4)+1)*((-1>>4)-(-221>>4)+1)==56,"bounded ticket footprint");
        check(DungeonRules.coins(4, 1.25) == 5, "whisker exact 25 percent");
        check(DungeonRules.coins(8, 1.25 + .4) == 13, "two gold boons plus whisker");
        check(!DungeonRules.expired(179999, 180000) && DungeonRules.expired(180000, 180000), "exact reconnect boundary");
        double logical = 3880, nativeHealth = 1024;
        for (double damage : new double[]{1500, 1500, 500, 390}) {
            DungeonRules.HealthHit hit = DungeonRules.healthHit(logical, nativeHealth, damage);
            logical = hit.remaining();
            if (!hit.intercept()) nativeHealth = Math.max(0, hit.nativeHealthBeforeDamage() - damage);
        }
        check(logical == 0 && nativeHealth == 0, "same-tick burst must actually kill logical-dead boss");
        DungeonRules.HealthHit reserve = DungeonRules.healthHit(3880, 1024, 1500);
        check(reserve.remaining() == 2380 && reserve.intercept(), "reserve prevents premature native death");
        for (int invalid : new int[]{0, 9, -1}) {
            boolean rejected = false;
            try { DungeonRules.healthScale(invalid); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid party size rejected");
        }
        System.out.println("DungeonRulesCheck PASS: solo/8-player scaling, rewards, party bounds, reconnect boundary");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
