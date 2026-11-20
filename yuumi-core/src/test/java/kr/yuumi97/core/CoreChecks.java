package kr.yuumi97.core;

import kr.yuumi97.core.util.EconomyRules;
import java.util.Arrays;

/** Run with the portable Gradle `coreChecks` task; no test framework or server required. */
public final class CoreChecks {
    public static void main(String[] args) {
        check(!EconomyRules.canBuy(199,2,200),"Holding cap bypass");
        check(EconomyRules.canBuy(150,50,200),"Exact cap purchase");
        check(!EconomyRules.canBuy(Integer.MAX_VALUE,50,200),"Integer overflow");
        check(!EconomyRules.canBuy(0,-1,200),"Negative purchase");
        check(EconomyRules.sellTax(1500)==30,"Exact jackpot tax");
        check(EconomyRules.money(1000-EconomyRules.sellTax(1000))+EconomyRules.sellTax(1000)==1000,"Sale conservation");
        check(Math.abs(EconomyRules.populationScale(8)-4.85)<1e-9,"Eight-player scaling");
        check(EconomyRules.populationScale(1)==1,"Solo scaling");
        double[] weights=Arrays.stream(EconomyRules.GACHA_WEIGHTS).asDoubleStream().toArray();
        int[] bins=new int[weights.length];
        for(int i=0;i<10000;i++) bins[EconomyRules.weightedIndex((i+.5)/10000,weights)]++;
        for(int i=0;i<bins.length;i++)check(bins[i]==EconomyRules.GACHA_WEIGHTS[i]*100,"Gacha weight "+i);
        boolean invalid=false;try{EconomyRules.weightedIndex(.5,new double[]{-1,3});}catch(IllegalArgumentException ex){invalid=true;}
        check(invalid,"Negative gacha weight accepted");
        invalid=false;try{EconomyRules.weightedIndex(.5,new double[]{0,0});}catch(IllegalArgumentException ex){invalid=true;}
        check(invalid,"Empty gacha pool accepted");
        check(kr.yuumi97.core.dungeon.mob.MobType.values().length==18,"eighteen monster definitions");
        check(Arrays.stream(kr.yuumi97.core.dungeon.mob.MobType.values()).map(m->m.id).distinct().count()==18,"unique monster IDs");
        check(Arrays.stream(kr.yuumi97.core.dungeon.mob.MobType.values()).mapToInt(m->m.boss()?3:2).sum()==42,"forty-two rig parts");
        DataPreservationCheck.selfCheck();
        PackCatalogCheck.run();
        kr.yuumi97.core.rpg.RpgChecks.main(args);
        kr.yuumi97.core.rpg.VfxDirectionCheck.run();
        kr.yuumi97.core.rpg.LifeFishingCheck.run();
        kr.yuumi97.core.hub.HubSafetyCheck.main(args);
        kr.yuumi97.core.dungeon.DungeonRulesCheck.main(args);
        kr.yuumi97.core.training.TrainingDamageWindowCheck.run();
        check(Math.abs(kr.yuumi97.core.guide.WayfindingManager.relativeAngle(0,-1,1,0)-90)<1e-9,"north to east is right");
        check(Math.abs(kr.yuumi97.core.guide.WayfindingManager.relativeAngle(0,1,1,0)+90)<1e-9,"south to east is left");
        check(Math.abs(kr.yuumi97.core.guide.WayfindingManager.relativeAngle(1,0,1,0))<1e-9,"destination ahead");
        System.out.println("CORE_DETERMINISTIC_CHECKS PASS (economy, holdings, party scaling, 10000 gacha rolls)");
    }
    private static void check(boolean passed,String message){if(!passed)throw new AssertionError(message);}
}
