package kr.yuumi97.core;

import kr.yuumi97.core.util.V3Rules;
import java.util.Arrays;

/** Run with the portable Gradle `v3Checks` task; no test framework or server required. */
public final class V3Checks {
    public static void main(String[] args) {
        check(!V3Rules.canBuy(199,2,200),"Holding cap bypass");
        check(V3Rules.canBuy(150,50,200),"Exact cap purchase");
        check(!V3Rules.canBuy(Integer.MAX_VALUE,50,200),"Integer overflow");
        check(!V3Rules.canBuy(0,-1,200),"Negative purchase");
        check(V3Rules.sellTax(1500)==30,"Exact jackpot tax");
        check(V3Rules.money(1000-V3Rules.sellTax(1000))+V3Rules.sellTax(1000)==1000,"Sale conservation");
        check(Math.abs(V3Rules.populationScale(8)-4.85)<1e-9,"Eight-player scaling");
        check(V3Rules.populationScale(1)==1,"Solo scaling");
        double[] weights=Arrays.stream(V3Rules.GACHA_WEIGHTS).asDoubleStream().toArray();
        int[] bins=new int[weights.length];
        for(int i=0;i<10000;i++) bins[V3Rules.weightedIndex((i+.5)/10000,weights)]++;
        for(int i=0;i<bins.length;i++)check(bins[i]==V3Rules.GACHA_WEIGHTS[i]*100,"Gacha weight "+i);
        boolean invalid=false;try{V3Rules.weightedIndex(.5,new double[]{-1,3});}catch(IllegalArgumentException ex){invalid=true;}
        check(invalid,"Negative gacha weight accepted");
        invalid=false;try{V3Rules.weightedIndex(.5,new double[]{0,0});}catch(IllegalArgumentException ex){invalid=true;}
        check(invalid,"Empty gacha pool accepted");
        kr.yuumi97.core.rpg.RpgChecks.main(args);
        kr.yuumi97.core.hub.HubSafetyCheck.main(args);
        kr.yuumi97.core.dungeon.DungeonRulesCheck.main(args);
        kr.yuumi97.core.training.TrainingDamageWindowCheck.run();
        System.out.println("V3 deterministic checks PASS (economy, holdings, party scaling, 10000 gacha rolls)");
    }
    private static void check(boolean passed,String message){if(!passed)throw new AssertionError(message);}
}
