package kr.yuumi97.core.util;

/** Deterministic economy rules, also exercised without a running Minecraft server. */
public final class V3Rules {
    private V3Rules() {}
    public static final String[] GACHA_IDS = {"hat_yuumi_ears","hat_chef_toque","hat_stock_tophat","plushie_yuumi",
            "furn_cat_tower","furn_kotatsu","furn_neon_sign","starlight_scroll","starlight_stone","churu_golden"};
    public static final int[] GACHA_WEIGHTS = {3,3,3,10,8,6,7,10,48,2};
    public static double money(double value) { return Math.round(value * 100.0) / 100.0; }
    public static double sellTax(double gross) { return money(gross * 0.02); }
    public static boolean canBuy(int held, int quantity, int limit) { return quantity>0 && held>=0 && (long)held+quantity<=limit; }
    public static int weightedIndex(double roll, double[] weights) {
        double total=0;
        for(double w:weights) { if(!Double.isFinite(w)||w<0) throw new IllegalArgumentException("Invalid weight"); total+=w; }
        if(!(total>0) || roll<0 || roll>=1) throw new IllegalArgumentException("Invalid roll");
        double point=roll*total;
        for(int i=0;i<weights.length;i++) {point-=weights[i];if(point<0)return i;}
        return weights.length-1;
    }
    public static double populationScale(int size) { return 1+0.55*(size-1); }
}
