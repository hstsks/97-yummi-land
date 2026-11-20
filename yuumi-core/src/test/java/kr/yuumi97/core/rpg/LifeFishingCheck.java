package kr.yuumi97.core.rpg;

/** Deterministic size-distribution checks without a server or EMF dependency. */
public final class LifeFishingCheck {
    public static void main(String[] args){run();}
    public static void run(){
        for(int tenth=0;tenth<=1000;tenth++){
            float before=tenth/10f,normal=FishingRarityBridge.boostLength(before,0,100,1),bait=FishingRarityBridge.boostLength(before,0,100,1.35),gold=FishingRarityBridge.boostLength(before,0,100,1.75);
            check(normal==before,"Unbuffed fish retain native size");check(bait>=before&&gold>=bait&&gold<=100,"Bait and gold weights only increase sizes within species bounds");
        }
        check(FishingRarityBridge.boostLength(-1,-1,-1,2)==-1,"Lengthless fish are preserved");
        check(FishingRarityBridge.boostLength(30,30,30,2)==30,"Fixed-size range is preserved");
        check(FishingRarityBridge.boostLength(30,0,100,Double.NaN)==30,"Invalid config cannot poison native size");
        check(FishingRarityBridge.boostLength(101,0,100,2)==101,"An already out-of-range authored catch is not rewritten");
        check(FishingRarityBridge.boostLength(50,0,100,1.75)>FishingRarityBridge.boostLength(50,0,100,1.35),"Golden field gives a stronger big-fish distribution than bait");
        System.out.println("LIFE_FISHING_CHECKS PASS: 1001 native size percentiles, monotonic bonuses, bounds and authored exceptions");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
