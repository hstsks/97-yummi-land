package kr.yuumi97.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public final class TextUtil {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final DecimalFormat NUM_FMT = new DecimalFormat("#,###");

    public static final String PREFIX = "<gradient:#ff85c0:#ffd666><bold>[97랜드]</bold></gradient> <gray>";

    private TextUtil() {}

    public static Component mm(String miniMessage) {
        return MM.deserialize(miniMessage).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static Component msg(String body) {
        return mm(PREFIX + body);
    }

    public static List<Component> lore(String... lines) {
        List<Component> result = new ArrayList<>(lines.length);
        for (String line : lines) {
            result.add(mm(line));
        }
        return result;
    }

    public static String fmt(double amount) {
        return NUM_FMT.format(Math.round(amount));
    }

    public static String churu(double amount) {
        return "<gold><bold>" + fmt(amount) + " 츄르</bold></gold>";
    }

    public static String progressBar(int current, int max, int bars) {
        if (max <= 0) max = 1;
        double ratio = Math.max(0.0, Math.min(1.0, (double) current / max));
        int filled = (int) Math.round(ratio * bars);
        StringBuilder sb = new StringBuilder("<dark_gray>[</dark_gray>");
        for (int i = 0; i < bars; i++) {
            if (i < filled) {
                sb.append("<green>■</green>");
            } else {
                sb.append("<gray>□</gray>");
            }
        }
        int pct = (int) Math.round(ratio * 100.0);
        sb.append("<dark_gray>]</dark_gray> <yellow>").append(pct).append("%</yellow>");
        return sb.toString();
    }

    public static String toLegacy(Component component) {
        return LEGACY.serialize(component);
    }
}
