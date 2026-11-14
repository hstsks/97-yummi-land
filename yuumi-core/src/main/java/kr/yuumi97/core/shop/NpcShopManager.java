package kr.yuumi97.core.shop;

import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.economy.SkillBonusBridge;
import kr.yuumi97.core.economy.SkillBonusBridge.TradeDomain;
import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ⚖ 허브 서쪽 『5대 전문 NPC 상점 거리』
 * - 무인 유저 상점을 없애고 매입가 : 판매가를 약 1 : 3.5 비율로 설정하여 지인 간 자율 직거래를 자연스럽게 유도합니다.
 * - AuraSkills 숙련도 레벨 & 수집 도감 달성률에 비례해 전문 상인 매입가가 최대 +100% 상승합니다!
 */
public final class NpcShopManager implements Listener {

    public enum ShopType {
        FARM("🌾 농산 상인 치즈냥", TradeDomain.FARMING, Material.HAY_BLOCK),
        FISH("🐟 수산 상인 고등어냥", TradeDomain.FISHING, Material.TROPICAL_FISH),
        MINE("⛏ 광물 상인 석탄냥", TradeDomain.MINING, Material.DIAMOND_PICKAXE),
        BUILD("🪵 건축자재 상인 목수냥", TradeDomain.FORAGING, Material.CHERRY_LOG),
        MISC("🎒 만물 잡화상 보부상냥", TradeDomain.COMBAT, Material.BUNDLE);

        public final String title;
        public final TradeDomain domain;
        public final Material badge;

        ShopType(String title, TradeDomain domain, Material badge) {
            this.title = title;
            this.domain = domain;
            this.badge = badge;
        }
    }

    public record ShopItem(Material mat, String customId, String koreanName, double baseSellPrice, double buyPrice) {}

    public static final class ShopHolder implements InventoryHolder {
        public final ShopType type;
        public ShopHolder(ShopType type) { this.type = type; }
        @Override public Inventory getInventory() { return null; }
    }

    private final JavaPlugin plugin;
    private final YuumiItems items;
    private final ChuruManager churu;
    private final SkillBonusBridge skillBridge;
    private final Map<ShopType, List<ShopItem>> catalogs = new EnumMap<>(ShopType.class);

    public NpcShopManager(JavaPlugin plugin, YuumiItems items, ChuruManager churu, SkillBonusBridge skillBridge) {
        this.plugin = plugin;
        this.items = items;
        this.churu = churu;
        this.skillBridge = skillBridge;
        initCatalogs();
    }

    private void initCatalogs() {
        // 1. 농산 상인 (매입 중심 + 종자/비료 판매, 약 1:3.5 차등)
        catalogs.put(ShopType.FARM, List.of(
                new ShopItem(Material.WHEAT, null, "밀", 4.5, 16.0),
                new ShopItem(Material.CARROT, null, "당근", 4.0, 15.0),
                new ShopItem(Material.POTATO, null, "감자", 4.0, 15.0),
                new ShopItem(Material.BEETROOT, null, "비트", 4.5, 16.0),
                new ShopItem(Material.SUGAR_CANE, null, "사탕수수", 3.5, 14.0),
                new ShopItem(Material.PUMPKIN, null, "호박", 7.0, 26.0),
                new ShopItem(Material.MELON_SLICE, null, "수박 조각", 2.0, 8.0),
                new ShopItem(Material.SWEET_BERRIES, null, "달콤한 열매", 3.5, 14.0),
                new ShopItem(Material.GLOW_BERRIES, null, "발광 열매", 6.0, 22.0),
                new ShopItem(Material.COCOA_BEANS, null, "코코아 콩", 5.0, 18.0),
                new ShopItem(Material.EGG, null, "달걀", 6.0, 22.0),
                new ShopItem(Material.HONEY_BOTTLE, null, "꿀이 든 병", 22.0, 80.0),
                new ShopItem(Material.BONE_MEAL, null, "비료용 뼛가루", 0.0, 12.0),
                new ShopItem(Material.NAME_TAG, null, "가축 이름표", 0.0, 450.0)
        ));

        // 2. 수산 상인 (바닐라 어종 + EvenMoreFish 특별 감정 매입 지원!)
        catalogs.put(ShopType.FISH, List.of(
                new ShopItem(Material.COD, null, "생대구", 12.0, 42.0),
                new ShopItem(Material.SALMON, null, "생연어", 15.0, 52.0),
                new ShopItem(Material.COOKED_COD, null, "구운 대구", 15.0, 52.0),
                new ShopItem(Material.COOKED_SALMON, null, "구운 연어", 18.0, 62.0),
                new ShopItem(Material.TROPICAL_FISH, null, "열대어", 32.0, 110.0),
                new ShopItem(Material.PUFFERFISH, null, "복어", 38.0, 130.0),
                new ShopItem(Material.KELP, null, "켈프", 2.5, 10.0),
                new ShopItem(Material.INK_SAC, null, "먹물 주머니", 10.0, 35.0),
                new ShopItem(Material.GLOW_INK_SAC, null, "발광 먹물 주머니", 24.0, 85.0),
                new ShopItem(Material.PRISMARINE_SHARD, null, "프리즈머린 조각", 18.0, 65.0),
                new ShopItem(Material.NAUTILUS_SHELL, null, "앵무조개 껍데기", 120.0, 450.0),
                new ShopItem(Material.FISHING_ROD, null, "튼튼한 낚싯대", 0.0, 120.0)
        ));

        // 3. 광물 상인 (광부 주력 소득원 & 세공 재료 직거래 핵심 품목!)
        catalogs.put(ShopType.MINE, List.of(
                new ShopItem(Material.COAL, null, "석탄", 6.0, 22.0),
                new ShopItem(Material.COPPER_INGOT, null, "구리 주괴", 8.0, 30.0),
                new ShopItem(Material.IRON_INGOT, null, "철 주괴", 18.0, 65.0),
                new ShopItem(Material.GOLD_INGOT, null, "금 주괴", 26.0, 92.0),
                new ShopItem(Material.REDSTONE, null, "레드스톤 가루", 5.0, 18.0),
                new ShopItem(Material.LAPIS_LAZULI, null, "청금석", 7.0, 26.0),
                new ShopItem(Material.QUARTZ, null, "네더 석영", 10.0, 36.0),
                new ShopItem(Material.AMETHYST_SHARD, null, "자수정 조각", 16.0, 58.0),
                new ShopItem(Material.EMERALD, null, "에메랄드", 65.0, 230.0),
                new ShopItem(Material.DIAMOND, null, "다이아몬드", 180.0, 650.0),
                new ShopItem(Material.NETHERITE_SCRAP, null, "네더라이트 파편", 650.0, 2400.0),
                new ShopItem(Material.TORCH, null, "광부용 횃불", 0.0, 6.0)
        ));

        // 4. 건축자재 상인 (남쪽 분양 단지 집짓기 블록 상시 공급!)
        catalogs.put(ShopType.BUILD, List.of(
                new ShopItem(Material.OAK_LOG, null, "참나무 원목", 6.0, 22.0),
                new ShopItem(Material.SPRUCE_LOG, null, "가문비나무 원목", 6.0, 22.0),
                new ShopItem(Material.BIRCH_LOG, null, "자작나무 원목", 6.0, 22.0),
                new ShopItem(Material.CHERRY_LOG, null, "벚나무 원목", 7.0, 25.0),
                new ShopItem(Material.DARK_OAK_LOG, null, "짙은 참나무 원목", 6.5, 24.0),
                new ShopItem(Material.STONE_BRICKS, null, "석재 벽돌", 2.0, 8.0),
                new ShopItem(Material.CALCITE, null, "방해석 (화이트 외벽)", 3.0, 12.0),
                new ShopItem(Material.QUARTZ_BLOCK, null, "석영 블록", 12.0, 45.0),
                new ShopItem(Material.BRICKS, null, "붉은 벽돌", 5.0, 20.0),
                new ShopItem(Material.GLASS, null, "투명 유리", 2.0, 9.0),
                new ShopItem(Material.LANTERN, null, "감성 랜턴", 0.0, 35.0),
                new ShopItem(Material.SEA_LANTERN, null, "바다 랜턴", 0.0, 55.0),
                new ShopItem(Material.CHERRY_SAPLING, null, "벚나무 묘목", 0.0, 45.0),
                new ShopItem(Material.PINK_PETALS, null, "분홍 꽃잎 장식", 0.0, 18.0)
        ));

        // 5. 만물 잡화상 (수렵 전리품 매입 + 귀환 책갈피 & 별빛 세공 재료 판매!)
        catalogs.put(ShopType.MISC, List.of(
                new ShopItem(Material.ROTTEN_FLESH, null, "썩은 살점", 4.0, 15.0),
                new ShopItem(Material.BONE, null, "뼈다귀", 6.0, 22.0),
                new ShopItem(Material.STRING, null, "거미줄 실", 6.0, 22.0),
                new ShopItem(Material.GUNPOWDER, null, "화약", 11.0, 40.0),
                new ShopItem(Material.SLIME_BALL, null, "슬라임볼", 18.0, 65.0),
                new ShopItem(Material.ENDER_PEARL, null, "엔더 진주", 32.0, 120.0),
                new ShopItem(Material.BLAZE_ROD, null, "블레이즈 막대", 42.0, 155.0),
                new ShopItem(Material.LEATHER, null, "가죽", 12.0, 45.0),
                new ShopItem(Material.PAPER, "recall_bookmark", "문턱의 서: 고속 귀환 책갈피", 0.0, 220.0),
                new ShopItem(Material.AMETHYST_SHARD, "starlight_stone", "찬란한 별빛 세공석", 180.0, 750.0),
                new ShopItem(Material.PAPER, "starlight_scroll", "유미의 발도장 세공 보호권", 0.0, 4500.0)
        ));
    }

    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25
    };

    public void openShop(Player player, ShopType type) {
        Inventory inv = Bukkit.createInventory(new ShopHolder(type), 45,
                TextUtil.mm("<gold><bold>" + type.title + "</bold></gold>"));

        ItemStack bg = makeIcon(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, bg);

        int skillLv = skillBridge.getDomainSkillLevel(player, type.domain);
        int skillPct = (int) Math.round(skillBridge.getSkillBonusRate(player, type.domain) * 100);
        int codexPct = (int) Math.round(skillBridge.getCodexBonusRate(player) * 100);
        double mult = skillBridge.getTotalSellMultiplier(player, type.domain);

        // Slot 4: Appraisal & P2P Trade Info Banner
        inv.setItem(4, makeIcon(type.badge,
                "<gradient:#ffd666:#ff85c0><bold>" + type.title + " 감정서</bold></gradient>",
                "<white>내 보유 지갑: " + TextUtil.churu(churu.getBalance(player)),
                "",
                "<aqua>✦ 내 " + type.domain.koreanLabel + " (Lv." + skillLv + "): <green>+" + skillPct + "% 매입 우대</green>",
                "<light_purple>✦ 도감 박물관 우대: <green>+" + codexPct + "% 매입 우대</green>",
                "<gold>▶ 최종 NPC 매입 배율: <bold>x" + String.format("%.2f", mult) + "</bold></gold>",
                "",
                "<gray>💡 상점 구매가는 매입가보다 높게 책정되어 있습니다.",
                "<yellow>   광장에서 지인과 [Shift+우클릭] 자율 직거래하면 서로 대이득!"
        ));

        List<ShopItem> list = catalogs.getOrDefault(type, List.of());
        for (int i = 0; i < list.size() && i < ITEM_SLOTS.length; i++) {
            ShopItem si = list.get(i);
            ItemStack disp = buildDisplay(si);
            ItemMeta meta = disp.getItemMeta();
            List<String> lore = new ArrayList<>();
            if (si.baseSellPrice > 0) {
                double boosted = Math.round(si.baseSellPrice * mult * 10.0) / 10.0;
                lore.add("<green>● 상인 매입가 (1개): <gold><bold>" + fmtDec(boosted) + " 츄르</bold></gold> <dark_gray>(기본 " + fmtDec(si.baseSellPrice) + ")");
            } else {
                lore.add("<dark_gray>● 매입 불가 품목");
            }
            if (si.buyPrice > 0) {
                lore.add("<aqua>● 상인 판매가 (1개): <white><bold>" + TextUtil.fmt(si.buyPrice) + " 츄르</bold></white>");
            } else {
                lore.add("<dark_gray>● 상점 미판매 (유저 채집/직거래 품목)");
            }
            lore.add("");
            if (si.buyPrice > 0) {
                lore.add("<yellow>▶ 좌클릭: 1개 구매 │ 쉬프트+좌클릭: 16개 구매");
            }
            if (si.baseSellPrice > 0) {
                lore.add("<gold>▶ 우클릭: 1개 판매 │ 쉬프트+우클릭: 인벤 전체 판매");
            }
            meta.displayName(TextUtil.mm("<white><bold>" + si.koreanName + "</bold></white>"));
            meta.lore(TextUtil.lore(lore.toArray(new String[0])));
            disp.setItemMeta(meta);
            inv.setItem(ITEM_SLOTS[i], disp);
        }

        // Special Slot 40 for Fish Merchant: Appraise & Sell EvenMoreFish custom catches!
        if (type == ShopType.FISH) {
            inv.setItem(40, makeIcon(Material.HEART_OF_THE_SEA,
                    "<aqua><bold>[클릭] 특대·희귀 낚시 물고기(EvenMoreFish) 일괄 감정 판매!</bold></aqua>",
                    "<gray>인벤토리(핫바 제외 9~35번 칸)에 있는 커스텀 등급 물고기를",
                    "<gray>길이·희귀도 및 내 낚시 숙련도 보너스를 적용해 고가로 매입합니다!",
                    "",
                    "<yellow>▶ 클릭하여 가방 속 낚시 물고기 일괄 정산"
            ));
        }

        player.openInventory(inv);
    }

    private ItemStack buildDisplay(ShopItem si) {
        if (si.customId == null) return new ItemStack(si.mat);
        return switch (si.customId) {
            case "recall_bookmark" -> items.recallBookmark(1);
            case "starlight_stone" -> items.starlightStone(1);
            case "starlight_scroll" -> items.starlightScroll(1);
            default -> new ItemStack(si.mat);
        };
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ShopHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 45) return;

        if (holder.type == ShopType.FISH && slot == 40) {
            sellEvenMoreFishCatches(player);
            openShop(player, holder.type);
            return;
        }

        List<ShopItem> list = catalogs.getOrDefault(holder.type, List.of());
        int idx = -1;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) {
                idx = i;
                break;
            }
        }
        if (idx < 0 || idx >= list.size()) return;

        ShopItem si = list.get(idx);
        ClickType click = event.getClick();
        double mult = skillBridge.getTotalSellMultiplier(player, holder.type.domain);

        // BUY (Left Click)
        if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
            if (si.buyPrice <= 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
                player.sendMessage(TextUtil.msg("<red>이 품목은 상점에서 판매하지 않습니다. 지인과 직거래해 보세요!"));
                return;
            }
            int qty = click == ClickType.SHIFT_LEFT ? 16 : 1;
            if ("starlight_scroll".equals(si.customId)) qty = 1;
            double totalCost = si.buyPrice * qty;
            if (!churu.withdraw(player, totalCost)) {
                player.playSound(player.getLocation(), Sound.ENTITY_CAT_STRAY_AMBIENT, 1f, 0.9f);
                player.sendMessage(TextUtil.msg("<red>지갑에 츄르가 부족합니다! (필요: " + TextUtil.fmt(totalCost) + " 츄르)"));
                return;
            }
            ItemStack purchased = buildDisplay(si);
            purchased.setAmount(qty);
            Map<Integer, ItemStack> left = player.getInventory().addItem(purchased);
            for (ItemStack drop : left.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.3f);
            player.sendMessage(TextUtil.msg("<white>" + si.koreanName + " " + qty + "개</white> 구매 완료! (-" + TextUtil.fmt(totalCost) + " 츄르)"));
            openShop(player, holder.type);
            return;
        }

        // SELL (Right Click)
        if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
            if (si.baseSellPrice <= 0) {
                player.sendMessage(TextUtil.msg("<red>상인이 매입하지 않는 품목입니다."));
                return;
            }
            boolean sellAll = click == ClickType.SHIFT_RIGHT;
            int sold = 0;
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack st = contents[i];
                if (!matchesShopItem(st, si)) continue;
                int take = sellAll ? st.getAmount() : 1;
                st.setAmount(st.getAmount() - take);
                sold += take;
                if (!sellAll) break;
            }
            if (sold <= 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
                player.sendMessage(TextUtil.msg("<red>인벤토리에 판매할 <white>" + si.koreanName + "</white> 아이템이 없습니다!"));
                return;
            }
            double unitEarned = si.baseSellPrice * mult;
            double totalEarned = Math.round(unitEarned * sold);
            churu.deposit(player, totalEarned);
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_PURREOW, 0.9f, 1.25f);
            player.sendMessage(TextUtil.msg("<white>" + si.koreanName + " " + sold + "개</white> 납품 정산 완료! +" + TextUtil.churu(totalEarned)
                    + " <gray>(숙련·도감 보너스 x" + String.format("%.2f", mult) + ")"));
            openShop(player, holder.type);
        }
    }

    private void sellEvenMoreFishCatches(Player player) {
        double mult = skillBridge.getTotalSellMultiplier(player, TradeDomain.FISHING);
        int count = 0;
        double baseSum = 0.0;

        // Scan upper inventory (slots 9..35) so hotbar food/favorite fish stay safe!
        for (int slot = 9; slot < 36; slot++) {
            ItemStack st = player.getInventory().getItem(slot);
            if (st == null || st.getType().isAir() || items.getCustomId(st) != null) continue;
            if (!st.hasItemMeta()) continue;
            PersistentDataContainer pdc = st.getItemMeta().getPersistentDataContainer();
            boolean isEmf = false;
            float length = 25.0f;
            String rarity = "";
            for (NamespacedKey key : pdc.getKeys()) {
                if (key.getNamespace().equalsIgnoreCase("evenmorefish")) {
                    isEmf = true;
                    if (key.getKey().contains("length")) {
                        Float f = pdc.get(key, PersistentDataType.FLOAT);
                        if (f != null) length = f;
                    } else if (key.getKey().contains("rarity")) {
                        String r = pdc.get(key, PersistentDataType.STRING);
                        if (r != null) rarity = r.toLowerCase();
                    }
                }
            }
            if (!isEmf) continue;

            double rarityBase = 24.0;
            if (rarity.contains("legend") || rarity.contains("myth") || rarity.contains("전설")) rarityBase = 220.0;
            else if (rarity.contains("epic") || rarity.contains("영웅") || rarity.contains("희귀")) rarityBase = 95.0;
            else if (rarity.contains("rare") || rarity.contains("상급")) rarityBase = 52.0;

            double fishVal = (rarityBase + Math.max(0.0, length * 0.45)) * st.getAmount();
            count += st.getAmount();
            baseSum += fishVal;
            player.getInventory().setItem(slot, null);
        }

        if (count <= 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
            player.sendMessage(TextUtil.msg("<red>가방(윗줄 9~35번 칸)에 감정할 특수 낚시 물고기(EvenMoreFish)가 없습니다!"));
            return;
        }
        double total = Math.round(baseSum * mult);
        churu.deposit(player, total);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.35f);
        player.sendMessage(TextUtil.msg("🐟 특수 낚시 물고기 <aqua>" + count + "마리</aqua> 특별 감정 완료! +" + TextUtil.churu(total) + " 지급!"));
    }

    private boolean matchesShopItem(ItemStack st, ShopItem si) {
        if (st == null || st.getType().isAir()) return false;
        String cid = items.getCustomId(st);
        if (si.customId != null) {
            return si.customId.equals(cid);
        }
        if (cid != null) return false;
        // Do not accidentally sell enchanted gear or EvenMoreFish custom fish as plain vanilla fish!
        if (st.hasItemMeta() && (!st.getItemMeta().getEnchants().isEmpty() || !st.getItemMeta().getPersistentDataContainer().getKeys().isEmpty())) {
            return false;
        }
        return st.getType() == si.mat;
    }

    private String fmtDec(double v) {
        if (Math.abs(v - Math.rint(v)) < 0.05) return TextUtil.fmt(v);
        return String.format("%,.1f", v);
    }

    private ItemStack makeIcon(Material mat, String name, String... lore) {
        ItemStack st = new ItemStack(mat);
        ItemMeta m = st.getItemMeta();
        if (m != null) {
            m.displayName(TextUtil.mm(name));
            if (lore.length > 0) m.lore(TextUtil.lore(lore));
            st.setItemMeta(m);
        }
        return st;
    }
}
