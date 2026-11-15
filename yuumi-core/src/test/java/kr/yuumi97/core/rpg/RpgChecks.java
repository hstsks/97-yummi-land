package kr.yuumi97.core.rpg;

/** Run with portable javac/java; no server or test framework required. */
public final class RpgChecks {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        int total=0;for(int level=1;level<20;level++){int xp=RpgRules.xpNeeded(level,80,40,10);check(xp>0,"positive XP");total+=xp;if(level==9)check(total==5370,"Lv10 curve");}
        check(total==33820,"Lv20 curve");check(RpgRules.materialCost(20,.55)==11,"45% carpenter discount");
        check(RpgRules.materialCost(10,.60)==6,"40% chef discount");check(RpgRules.materialCost(1,.55)==1,"nonzero ingredient");
        check(!RpgRules.canInvest(20,20,0),"20 SP cap");check(!RpgRules.canInvest(20,10,10),"branch cap");check(RpgRules.canInvest(1,0,0),"first level gets SP");
        check(Math.abs(RpgRules.specialtyChance(.14,10,0)-.182)<1e-9,"efficiency is relative, preserving job advantage");
        check(RpgRules.cooldownMillis(8,10)==6000,"10 mobility 25% cooldown reduction");
        System.out.println("RPG invariant checks passed: growth, crafting, SP caps, drop math, cooldowns");
    }
}
