package kr.yuumi97.core.rpg;

/** Pure balance arithmetic shared by production logic and the executable invariant check. */
public final class RpgRules {
    private RpgRules() {}
    public static int xpNeeded(int level,int base,int linear,int quadratic) {
        if(level<1||level>19)throw new IllegalArgumentException("level must be 1..19");
        return Math.max(1,Math.addExact(base,Math.addExact(Math.multiplyExact(linear,level),Math.multiplyExact(quadratic,level*level))));
    }
    public static int materialCost(int amount,double retained) {return Math.max(1,(int)Math.ceil(amount*retained));}
    public static boolean canInvest(int level,int spent,int rank,int cap) {return level>=1&&level<=20&&spent>=0&&spent<level&&rank>=0&&rank<cap;}
    public static long cooldownMillis(double seconds) {return Math.max(0,Math.round(seconds*1000));}
    public static double growthMultiplier(int level,double perLevel){return 1+Math.clamp(level-1,0,19)*perLevel;}
    public static double damageBudget(double base,double forge,int level,double perLevel,boolean primary,double primary15){return (base+forge)*growthMultiplier(level,perLevel)*(primary&&level>=15?1+primary15:1);}
    public static double finisherCooldown(double seconds,int level,int star){return seconds*(level>=20?.90:1)*(star>=7?.85:1);}
    public static double jobChangeCost(boolean firstSelection,int changes,double price){return firstSelection||changes<1?0:price;}
    public static int oreChainCap(int level,int star,int perk){return 4+(int)Math.floor(10*Math.clamp(level-1,0,19)/19.0)+(star>=3?2:0)+Math.clamp(perk,0,5);}
    public static boolean sameInput(long previousTick,long tick){return previousTick==tick;}
    public static int pityFailures(int rank){return Math.max(4,14-2*Math.clamp(rank,0,5));}
    /** A weaker hidden potion must not take ownership of the currently active damage. */
    public static boolean replacesEffect(int oldAmplifier,int oldTicks,int amplifier,int ticks) {
        return ticks>0&&(oldTicks<=0||amplifier>oldAmplifier||amplifier==oldAmplifier&&ticks>oldTicks);
    }
}
