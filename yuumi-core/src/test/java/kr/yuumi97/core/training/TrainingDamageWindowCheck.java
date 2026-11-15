package kr.yuumi97.core.training;

/** Deterministic fixed-window and isolation checks, without a server or wall-clock sleeps. */
public final class TrainingDamageWindowCheck {
    private static void equal(double actual,double expected,String label){if(Math.abs(actual-expected)>1e-9)throw new AssertionError(label+": "+actual+" != "+expected);}
    public static void run(){
        long start=10_000_000_000L;
        DamageWindow first=new DamageWindow(),otherDummy=new DamageWindow(),otherPlayer=new DamageWindow();
        first.record(start,20);equal(first.dps(start),4,"fixed five-second denominator");
        first.record(start+1_000_000_000L,30);equal(first.dps(start+1_000_000_000L),10,"two valid hits");
        otherDummy.record(start,100);otherPlayer.record(start,200);
        equal(first.dps(start+2_000_000_000L),10,"another target must not affect this window");
        equal(otherDummy.dps(start),20,"second target window");equal(otherPlayer.dps(start),40,"second player window");
        first.record(start+2_000_000_000L,Double.NaN);first.record(start+2_000_000_000L,-2);first.record(start+2_000_000_000L,Double.POSITIVE_INFINITY);
        equal(first.total(start+2_000_000_000L),50,"reject invalid damage");
        equal(first.dps(start+5_000_000_000L),6,"discard exactly five-second-old hit");
        equal(first.lastDamage(),30,"show the last valid hit");equal(first.dps(start+6_000_000_000L),0,"expire all hits");
        if(first.isActive(start+6_000_000_000L))throw new AssertionError("expired practice session");
    }
    public static void main(String[] args){run();System.out.println("Training damage window checks passed");}
}
