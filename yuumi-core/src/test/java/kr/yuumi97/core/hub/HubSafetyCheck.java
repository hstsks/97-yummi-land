package kr.yuumi97.core.hub;

import kr.yuumi97.core.plot.PlotManager;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Arrays;
import java.util.UUID;

/** Standalone check: run main with compiled core classes and the Paper compile classpath. */
public final class HubSafetyCheck {
    public static void main(String[] args) {
        int cases = 0;
        for (int width = 1; width <= 7; width++) {
            for (int depth = 2; depth <= 11; depth++) {
                for (StructureRotation rotation : StructureRotation.values()) {
                    double angle = switch (rotation) {
                        case NONE -> 0;
                        case CLOCKWISE_90 -> Math.PI / 2;
                        case CLOCKWISE_180 -> Math.PI;
                        case COUNTERCLOCKWISE_90 -> -Math.PI / 2;
                    };
                    int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
                    int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
                    for (int x : new int[]{0, width - 1}) {
                        for (int z : new int[]{0, depth - 1}) {
                            int rx = (int) Math.round(x * Math.cos(angle) - z * Math.sin(angle));
                            int rz = (int) Math.round(x * Math.sin(angle) + z * Math.cos(angle));
                            minX = Math.min(minX, rx);
                            maxX = Math.max(maxX, rx);
                            minZ = Math.min(minZ, rz);
                            maxZ = Math.max(maxZ, rz);
                        }
                    }
                    check(Arrays.equals(HubWorldManager.rotatedFootprint(width, depth, rotation),
                            new int[]{minX, maxX, minZ, maxZ}), "Rotated NBT footprint: " + rotation);
                    cases++;
                }
            }
        }
        var data = new YamlConfiguration();
        var plots = new PlotManager(null, null, data, () -> {}, () -> null);
        for (var plot : plots.getAllPlots().values()) {
            check(!plots.isOwnedColumn(plot.centerX(), plot.centerZ()), "Unowned plot is not an owned exclusion");
            data.set("plots." + plot.id() + ".owner", UUID.randomUUID().toString());
            check(plots.isOwnedColumn(plot.minX(), plot.minZ()), "Owned minimum corner");
            check(plots.isOwnedColumn(plot.maxX(), plot.maxZ()), "Owned maximum corner");
        }
        check(!plots.isOwnedColumn(0, 70), "Public residential road");
        check(!plots.isOwnedColumn(-25, 61), "One block outside the owned boundary");
        int[] callbacks = {0};
        plots.setBeforePlotReset(plot -> {
            check(plots.getOwner(plot.id()) != null, "Owner must exist when furniture is recovered");
            callbacks[0]++;
        });
        plots.resetPlotLawn(plots.getAllPlots().get("S-1"));
        check(callbacks[0] == 1, "Exactly one pre-reset recovery callback");
        System.out.println("HUB_CHECK_PASS: " + cases + " rotated bounds, twelve owned plot borders, pre-reset owner callback");
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }
}
