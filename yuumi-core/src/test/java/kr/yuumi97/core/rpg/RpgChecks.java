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
        String poisonOwner="player-A";long poisonExpiry=30_000;
        if(RpgRules.replacesEffect(1,400,0,40)){poisonOwner="player-B";poisonExpiry=12_000;}
        check(poisonOwner.equals("player-A")&&poisonExpiry==30_000,"rejected weak poison retains the stronger source and TTL");
        check(!RpgRules.replacesEffect(1,400,0,600),"hidden longer weak poison cannot steal active damage");
        if(RpgRules.replacesEffect(1,400,2,100)){poisonOwner="player-B";poisonExpiry=15_000;}
        check(poisonOwner.equals("player-B")&&poisonExpiry==15_000,"accepted stronger poison changes source and TTL");
        check(RpgRules.replacesEffect(1,40,1,80)&&!RpgRules.replacesEffect(1,80,1,40),"same-strength refresh must extend the active effect");
        System.out.println("RPG invariant checks passed: growth, crafting, SP caps, drop math, cooldowns");
    }
}
