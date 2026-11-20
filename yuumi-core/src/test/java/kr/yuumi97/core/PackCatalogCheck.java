package kr.yuumi97.core;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.*;

/** Reflection-only catalogue check. Never executes the pack writer. */
public final class PackCatalogCheck {
    public static void run() {
        try {
            Class<?> builder = Class.forName("PackBuilder");
            Method catalog = builder.getDeclaredMethod("buildCatalog"); catalog.setAccessible(true);
            @SuppressWarnings("unchecked") Map<String,Object> entries = (Map<String,Object>) catalog.invoke(null);
            for (String name : List.of("addGameplaySprites", "addCoreSprites")) {
                Method add = builder.getDeclaredMethod(name, Map.class); add.setAccessible(true); add.invoke(null, entries);
            }
            Class<?> spec = Class.forName("PackBuilder$SpriteSpec");
            Method render = builder.getDeclaredMethod("render16", spec); render.setAccessible(true);
            Set<Integer> hashes = new HashSet<>();
            for (String id : List.of("weapon_berserker_greatsword", "weapon_priest_staff", "weapon_arcane_orb_tome", "guidebook")) {
                Object sprite = Objects.requireNonNull(entries.get(id), id);
                String[] pixels = (String[]) spec.getDeclaredMethod("rows").invoke(sprite);
                Map<?,?> colors = (Map<?,?>) spec.getDeclaredMethod("palette").invoke(sprite);
                require(pixels.length == 16, id+" rows");
                for (String row : pixels) {
                    require(row.length() == 16, id+" columns");
                    for (char c : row.toCharArray()) require(c=='.'||c==' '||colors.containsKey(c), id+" palette "+c);
                }
                BufferedImage bitmap = (BufferedImage) render.invoke(null, sprite);
                require(bitmap.getWidth()==16&&bitmap.getHeight()==16, id+" dimensions");
                int[] argb = bitmap.getRGB(0,0,16,16,null,0,16);
                require(Arrays.stream(argb).anyMatch(pixel -> pixel>>>24!=0),id+" empty texture");
                require(hashes.add(Arrays.hashCode(argb)),id+" duplicated fallback");
            }
            System.out.println("PACK_CATALOG_CHECK PASS: four distinct 16x16 sprites; no resource pack or icon written");
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static void require(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
}
