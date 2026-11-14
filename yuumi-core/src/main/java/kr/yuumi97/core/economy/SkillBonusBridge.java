package kr.yuumi97.core.economy;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.function.Function;

/**
 * 🐾 AuraSkills 숙련도 & 97랜드 수집 도감 연계 감정 보너스 브릿지
 * 전문 분야를 파고들거나 도감을 채울수록 NPC 상점 납품(매입) 단가가 상승합니다!
 */
public final class SkillBonusBridge {

    public enum TradeDomain {
        FARMING("농사 숙련"),
        FISHING("낚시 숙련"),
        MINING("채광 숙련"),
        FORAGING("벌목·채집 숙련"),
        COMBAT("전투·수렵 숙련");

        public final String koreanLabel;
        TradeDomain(String koreanLabel) { this.koreanLabel = koreanLabel; }
    }

    private final JavaPlugin plugin;
    private final Function<UUID, Integer> codexCountProvider;

    public SkillBonusBridge(JavaPlugin plugin, Function<UUID, Integer> codexCountProvider) {
        this.plugin = plugin;
        this.codexCountProvider = codexCountProvider;
    }

    public int getDomainSkillLevel(Player player, TradeDomain domain) {
        if (Bukkit.getPluginManager().getPlugin("AuraSkills") == null) return 0;
        try {
            AuraSkillsApi api = AuraSkillsApi.get();
            SkillsUser user = api.getUser(player.getUniqueId());
            if (user == null || !user.isLoaded()) return 0;
            return switch (domain) {
                case FARMING -> user.getSkillLevel(Skills.FARMING);
                case FISHING -> user.getSkillLevel(Skills.FISHING);
                case MINING -> user.getSkillLevel(Skills.MINING);
                case FORAGING -> Math.max(user.getSkillLevel(Skills.FORAGING), user.getSkillLevel(Skills.EXCAVATION));
                case COMBAT -> Math.max(user.getSkillLevel(Skills.FIGHTING), user.getSkillLevel(Skills.ARCHERY));
            };
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public double getSkillBonusRate(Player player, TradeDomain domain) {
        int lv = Math.max(0, getDomainSkillLevel(player, domain));
        double perLv = plugin.getConfig().getDouble("economy.skill-sell-bonus-per-level", 0.025);
        double maxBonus = plugin.getConfig().getDouble("economy.skill-sell-bonus-max", 0.75);
        return Math.min(maxBonus, lv * perLv);
    }

    public double getCodexBonusRate(Player player) {
        int count = codexCountProvider.apply(player.getUniqueId());
        if (count >= 21) return 0.25; // 마스터 우대 +25%
        return Math.min(0.21, count * 0.01);
    }

    public double getTotalSellMultiplier(Player player, TradeDomain domain) {
        return 1.0 + getSkillBonusRate(player, domain) + getCodexBonusRate(player);
    }
}
