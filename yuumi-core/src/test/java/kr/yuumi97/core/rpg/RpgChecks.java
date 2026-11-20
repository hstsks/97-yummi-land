package kr.yuumi97.core.rpg;

public final class RpgChecks {
    public static void main(String[] args) {
        int total=0;for(int level=1;level<20;level++){total+=RpgRules.xpNeeded(level,80,40,10);if(level==9)check(total==5370,"Lv10 curve");}
        check(total==33820,"Lv20 curve");
        check(CombatClass.values().length==6&&LifeProfession.values().length==6,"six independent domains");
        check(RpgRules.materialCost(20,.55)==11&&RpgRules.materialCost(1,.55)==1,"recipe savings round up");
        check(!RpgRules.canInvest(20,20,0,4)&&!RpgRules.canInvest(20,10,4,4),"combat SP and rank caps");
        check(!RpgRules.canInvest(20,19,5,5)&&RpgRules.canInvest(1,0,0,4),"life rank cap and first SP");
        check(RpgRules.jobChangeCost(true,9,5000)==0&&RpgRules.jobChangeCost(false,0,5000)==0&&RpgRules.jobChangeCost(false,1,5000)==5000,"slot first selection and first change free");
        near(RpgRules.growthMultiplier(1,.025),1,"Lv1 growth");near(RpgRules.growthMultiplier(20,.025),1.475,"Lv20 growth");
        double cast=RpgRules.damageBudget(32,7,20,.025,false,.10),sum=0;
        for(int i=0;i<25;i++)sum+=cast/25;
        near(sum,(32+7)*1.475,"forge applies once to a multihit cast");
        near(RpgRules.damageBudget(12,7,15,.025,true,.10),19*1.35*1.10,"Lv15 primary milestone");
        near(RpgRules.finisherCooldown(30,20,7),22.95,"Lv20 and star7 cooldown multiply");
        check(RpgRules.oreChainCap(1,0,0)==4&&RpgRules.oreChainCap(20,0,0)==14&&RpgRules.oreChainCap(20,3,5)==21,"chain progression and breakthroughs");
        check(RpgRules.pityFailures(1)==12&&RpgRules.pityFailures(5)==4,"pity rank boundaries");
        check(RpgRules.sameInput(42,42)&&!RpgRules.sameInput(42,43),"same-tick event deduplication");
        check(RpgRules.cooldownMillis(.35)==350,"shortbow interval");
        check(!RpgRules.replacesEffect(1,400,0,600)&&RpgRules.replacesEffect(1,400,2,100),"damage source ownership follows active potion");
        System.out.println("RPG_CHECKS PASS: dual roster, growth, SP, prices, cast budget, milestones, cooldown, pity and input guards");
    }
    private static void near(double a,double b,String why){check(Math.abs(a-b)<1e-9,why+": "+a+" != "+b);}
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
}
