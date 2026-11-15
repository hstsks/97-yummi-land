package kr.yuumi97.core.rpg;

/** Pure balance arithmetic shared by production logic and the executable invariant check. */
public final class RpgRules {
    private RpgRules() {}
    public static int xpNeeded(int level,int base,int linear,int quadratic) {
        if(level<1||level>19)throw new IllegalArgumentException("level must be 1..19");
        return Math.max(1,Math.addExact(base,Math.addExact(Math.multiplyExact(linear,level),Math.multiplyExact(quadratic,level*level))));
    }
    public static int materialCost(int amount,double retained) {return Math.max(1,(int)Math.ceil(amount*retained));}
    public static boolean canInvest(int level,int spent,int branch) {return level>=1&&level<=20&&spent>=0&&spent<level&&branch>=0&&branch<10;}
    public static double specialtyChance(double base,int efficiency,double feastBonus) {return Math.clamp(base*(1+.03*Math.clamp(efficiency,0,10))*(1+feastBonus),0,1);}
    public static long cooldownMillis(double seconds,int mobility) {return Math.max(1,Math.round(seconds*1000*(1-.025*Math.clamp(mobility,0,10))));}
}
