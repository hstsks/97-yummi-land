package kr.yuumi97.core.facility;

import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ⚒ 허브 서쪽 『고양이 대장간』
 * 1) 츄르 장비 내구도 즉시 수리 (사망 내구도 패널티 & 던전 마모 복구)
 * 2) 바닐라 장비 기반 『별빛 세공 (★1 ~ ★7, 장비 파괴 0% 보장!)』
 */
public final class BlacksmithManager implements Listener {

    public enum GearKind { WEAPON, TOOL, ARMOR }

    public static final class ForgeHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static final int SLOT_TARGET = 20;
    private static final int SLOT_SCROLL = 24;
    private static final int SLOT_ACTION = 31;
    private static final int SLOT_REPAIR_HAND = 38;
    private static final int SLOT_REPAIR_ALL = 42;

    // index 1..7 = target star level
    private static final double[] STAR_CHURU_COST = {0, 800, 1400, 2200, 3500, 5500, 8500, 12000};
    private static final int[] STAR_STONE_COST = {0, 1, 1, 2, 2, 3, 4, 5};
    private static final double[] STAR_SUCCESS_RATE = {0, 0.90, 0.80, 0.70, 0.55, 0.40, 0.25, 0.15};

    private static final double[] WEAPON_BONUS_DMG = {0, 1.0, 1.8, 2.6, 3.6, 4.6, 5.6, 7.0};
    private static final double[] TOOL_MINING_EFF = {0, 2.0, 4.0, 6.5, 9.5, 13.0, 17.0, 22.0};
    private static final double[] ARMOR_BONUS_HP = {0, 0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0}; // per piece (4 pieces ★7 = +16 HP = +8 hearts!)
    private static final double[] ARMOR_BONUS_TOUGH = {0, 0.2, 0.4, 0.6, 0.9, 1.2, 1.6, 2.2};

    private final JavaPlugin plugin;
    private final YuumiItems items;
    private final ChuruManager churu;
    private final NamespacedKey starKey;
    private final NamespacedKey modMiningKey;
    private final NamespacedKey modHealthKey;
    private final NamespacedKey modToughKey;

    public BlacksmithManager(JavaPlugin plugin, YuumiItems items, ChuruManager churu) {
        this.plugin = plugin;
        this.items = items;
        this.churu = churu;
        this.starKey = new NamespacedKey(plugin, "starlight_level");
        this.modMiningKey = new NamespacedKey(plugin, "starlight_mining_eff");
        this.modHealthKey = new NamespacedKey(plugin, "starlight_armor_hp");
        this.modToughKey = new NamespacedKey(plugin, "starlight_armor_tough");
    }

    public void startAttributeSyncTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                syncPlayerStarlightStats(p);
            }
        }, 40L, 25L);
    }

    private void syncPlayerStarlightStats(Player p) {
        ItemStack main = p.getInventory().getItemInMainHand();
        int toolStar = classifyGear(main) == GearKind.TOOL ? getStarLevel(main) : 0;
        double miningBonus = TOOL_MINING_EFF[toolStar];

        double hpBonus = 0.0;
        double toughBonus = 0.0;
        for (ItemStack armor : p.getInventory().getArmorContents()) {
            if (classifyGear(armor) == GearKind.ARMOR) {
                int s = getStarLevel(armor);
                hpBonus += ARMOR_BONUS_HP[s];
                toughBonus += ARMOR_BONUS_TOUGH[s];
            }
        }

        applyAttr(p, Attribute.MINING_EFFICIENCY, modMiningKey, miningBonus);
        applyAttr(p, Attribute.MAX_HEALTH, modHealthKey, hpBonus);
        applyAttr(p, Attribute.ARMOR_TOUGHNESS, modToughKey, toughBonus);
    }

    private void applyAttr(Player p, Attribute attr, NamespacedKey key, double value) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(key);
        if (existing != null) {
            if (Math.abs(existing.getAmount() - value) < 0.001) return;
            inst.removeModifier(key);
        }
        if (value > 0.001) {
            inst.addTransientModifier(new AttributeModifier(key, value, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    public int getStarLevel(ItemStack st) {
        if (st == null || st.getType().isAir() || !st.hasItemMeta()) return 0;
        Integer lv = st.getItemMeta().getPersistentDataContainer().get(starKey, PersistentDataType.INTEGER);
        return lv == null ? 0 : Math.max(0, Math.min(7, lv));
    }

    public GearKind classifyGear(ItemStack st) {
        if (st == null || st.getType().isAir()) return null;
        if (items.getCustomId(st) != null) return null; // Custom relics stay pure
        String n = st.getType().name();
        if (n.endsWith("_SWORD") || n.equals("BOW") || n.equals("CROSSBOW") || n.equals("TRIDENT") || n.equals("MACE")) {
            return GearKind.WEAPON;
        }
        if (n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")) {
            return GearKind.TOOL;
        }
        if (n.endsWith("_AXE")) {
            return GearKind.WEAPON; // Axes gain weapon damage AND we also grant half tool efficiency!
        }
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) {
            return GearKind.ARMOR;
        }
        return null;
    }

    // ==================== 대장간 GUI ====================

    public void openBlacksmithGui(Player player) {
        Inventory inv = Bukkit.createInventory(new ForgeHolder(), 45,
                TextUtil.mm("<gold><bold>⚒ 고양이 대장간 (별빛 세공 & 수리)</bold></gold>"));

        ItemStack bg = icon(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, bg);

        inv.setItem(4, icon(Material.ANVIL,
                "<gradient:#ffe14d:#5cdbd3><bold>✦ 강철냥의 별빛 세공 모루 ✦</bold></gradient>",
                "<gray>왼쪽 슬롯에 무기·도구·갑옷을 올리고 <aqua>별빛 세공석</aqua>과",
                "<gold>츄르</gold>로 최대 <light_purple><bold>★7</bold></light_purple>까지 안전하게 강화하세요!",
                "",
                "<green>✔ 장비 파괴(증발) 확률 0%! 인챈트 100% 보존!",
                "<gray>  · ★1~★5 실패 시: 등급 유지 (하락 없음)",
                "<gray>  · ★6~★7 실패 시: 1단계 하락 (오른쪽 슬롯에 <yellow>세공 보호권</yellow> 등록 시 방어!)"
        ));

        inv.setItem(11, icon(Material.ORANGE_STAINED_GLASS_PANE, "<yellow>▼ 세공할 장비 올려놓기 ▼"));
        inv.setItem(SLOT_TARGET, null);

        inv.setItem(15, icon(Material.PINK_STAINED_GLASS_PANE, "<light_purple>▼ 세공 보호권 (선택) ▼"));
        inv.setItem(SLOT_SCROLL, null);

        refreshForgeButton(inv, player);
        refreshRepairButtons(inv, player);

        player.openInventory(inv);
    }

    private void refreshForgeButton(Inventory inv, Player player) {
        ItemStack gear = inv.getItem(SLOT_TARGET);
        GearKind kind = classifyGear(gear);
        if (gear == null || gear.getType().isAir()) {
            inv.setItem(SLOT_ACTION, icon(Material.SMITHING_TABLE,
                    "<yellow><bold>장비를 왼쪽 슬롯에 올려주세요!</bold></yellow>",
                    "<gray>대상: 바닐라 검·도끼·활·석궁·삼지창·곡괭이·삽·괭이·갑옷"));
            return;
        }
        if (kind == null) {
            inv.setItem(SLOT_ACTION, icon(Material.BARRIER,
                    "<red><bold>세공할 수 없는 아이템입니다!</bold></red>",
                    "<gray>일반 무기·도구·방어구만 별빛 세공이 가능합니다."));
            return;
        }
        int cur = getStarLevel(gear);
        if (cur >= 7) {
            inv.setItem(SLOT_ACTION, icon(Material.NETHER_STAR,
                    "<light_purple><bold>★ 최고 단계(★7) 달성 완료! ★</bold></light_purple>",
                    "<gray>더 이상 세공할 수 없는 전설의 종결 장비입니다!"));
            return;
        }

        int next = cur + 1;
        double cost = STAR_CHURU_COST[next];
        int stones = STAR_STONE_COST[next];
        int ratePct = (int) Math.round(STAR_SUCCESS_RATE[next] * 100);
        boolean canDrop = next >= 6;
        ItemStack scrollSlot = inv.getItem(SLOT_SCROLL);
        boolean protectedByScroll = canDrop && items.isCustomItem(scrollSlot, "starlight_scroll");

        inv.setItem(SLOT_ACTION, icon(Material.AMETHYST_CLUSTER,
                "<gradient:#ffd666:#5cdbd3><bold>▶ [클릭] ★" + cur + " → ★" + next + " 별빛 세공 시작!</bold></gradient>",
                "<white>성공 확률: <green><bold>" + ratePct + "%</bold></green>",
                "<white>필요 재화: " + TextUtil.churu(cost) + " + <aqua>별빛 세공석 " + stones + "개</aqua> <gray>(가방 소지)",
                canDrop
                        ? (protectedByScroll ? "<light_purple>✦ 세공 보호권 적용됨: 실패 시에도 등급 100% 유지!"
                                             : "<red>⚠ 실패 시 ★" + (cur - 1) + "로 1단계 하락 가능 (보호권 미등록)")
                        : "<green>✦ 안전 구간: 실패해도 등급 하락 없음!",
                "",
                "<yellow>▶ 클릭하여 별빛 마력 주입!"
        ));
    }

    private void refreshRepairButtons(Inventory inv, Player player) {
        int allDmg = calcTotalRepairDamage(player);
        double allCost = allDmg * 2.0;
        inv.setItem(SLOT_REPAIR_HAND, icon(Material.IRON_INGOT,
                "<aqua><bold>🔧 [클릭] 세공 슬롯에 올린 장비 즉시 수리</bold></aqua>",
                "<gray>모루 경험치 소비 없이 닳은 내구도 1당 <gold>2 츄르</gold>로",
                "<gray>내구도를 100% 새것처럼 복원합니다!"));

        inv.setItem(SLOT_REPAIR_ALL, icon(Material.GOLDEN_APPLE,
                "<gold><bold>🛡 [클릭] 내 착용 갑옷 + 핫바 장비 일괄 수리!</bold></gold>",
                allDmg > 0
                        ? "<white>총 손상 내구도: <red>" + allDmg + "</red> → 수리비: " + TextUtil.churu(allCost)
                        : "<green>현재 착용 및 핫바 장비가 모두 최상 상태입니다!",
                "",
                "<yellow>▶ 클릭하여 한 번에 수리"));
    }

    private int calcTotalRepairDamage(Player p) {
        int sum = 0;
        for (ItemStack armor : p.getInventory().getArmorContents()) sum += getDamage(armor);
        sum += getDamage(p.getInventory().getItemInOffHand());
        for (int i = 0; i < 9; i++) sum += getDamage(p.getInventory().getItem(i));
        return sum;
    }

    private int getDamage(ItemStack st) {
        if (st == null || st.getType().isAir() || !(st.getItemMeta() instanceof Damageable d)) return 0;
        return Math.max(0, d.getDamage());
    }

    private void repairStack(ItemStack st) {
        if (st == null || st.getType().isAir() || !(st.getItemMeta() instanceof Damageable d)) return;
        if (d.getDamage() > 0) {
            d.setDamage(0);
            st.setItemMeta((ItemMeta) d);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ForgeHolder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getInventory();
        int raw = event.getRawSlot();

        if (event.isShiftClick()) {
            event.setCancelled(true);
            if (raw >= 45) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType().isAir()) return;
                if (items.isCustomItem(clicked, "starlight_scroll")) {
                    ItemStack curScroll = top.getItem(SLOT_SCROLL);
                    if (curScroll == null || curScroll.getType().isAir()) {
                        ItemStack one = clicked.clone();
                        one.setAmount(1);
                        top.setItem(SLOT_SCROLL, one);
                        clicked.setAmount(clicked.getAmount() - 1);
                    }
                } else if (classifyGear(clicked) != null) {
                    ItemStack curGear = top.getItem(SLOT_TARGET);
                    if (curGear == null || curGear.getType().isAir()) {
                        top.setItem(SLOT_TARGET, clicked.clone());
                        event.setCurrentItem(null);
                    }
                }
            } else if (raw == SLOT_TARGET || raw == SLOT_SCROLL) {
                ItemStack inSlot = top.getItem(raw);
                if (inSlot != null && !inSlot.getType().isAir()) {
                    Map<Integer, ItemStack> left = player.getInventory().addItem(inSlot);
                    top.setItem(raw, left.isEmpty() ? null : left.values().iterator().next());
                }
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                refreshForgeButton(top, player);
                refreshRepairButtons(top, player);
            });
            return;
        }

        if (raw >= 45) return; // Allow normal clicks in bottom inventory

        if (raw == SLOT_TARGET || raw == SLOT_SCROLL) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                refreshForgeButton(top, player);
                refreshRepairButtons(top, player);
            });
            return;
        }

        event.setCancelled(true);

        if (raw == SLOT_ACTION) {
            attemptEnhance(player, top);
        } else if (raw == SLOT_REPAIR_HAND) {
            ItemStack gear = top.getItem(SLOT_TARGET);
            int dmg = getDamage(gear);
            if (dmg <= 0) {
                player.sendMessage(TextUtil.msg("<yellow>수리할 손상도가 없거나 슬롯이 비어 있습니다!"));
                return;
            }
            double cost = dmg * 2.0;
            if (!churu.withdraw(player, cost)) {
                player.sendMessage(TextUtil.msg("<red>수리비가 부족합니다! (필요: " + TextUtil.fmt(cost) + " 츄르)"));
                return;
            }
            repairStack(gear);
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.9f, 1.25f);
            player.sendMessage(TextUtil.msg("🔧 장비 수리가 완료되었습니다! (-" + TextUtil.fmt(cost) + " 츄르)"));
            refreshRepairButtons(top, player);
        } else if (raw == SLOT_REPAIR_ALL) {
            int dmg = calcTotalRepairDamage(player);
            if (dmg <= 0) {
                player.sendMessage(TextUtil.msg("<green>수리할 손상 장비가 없습니다!"));
                return;
            }
            double cost = dmg * 2.0;
            if (!churu.withdraw(player, cost)) {
                player.sendMessage(TextUtil.msg("<red>일괄 수리비가 부족합니다! (필요: " + TextUtil.fmt(cost) + " 츄르)"));
                return;
            }
            for (ItemStack armor : player.getInventory().getArmorContents()) repairStack(armor);
            repairStack(player.getInventory().getItemInOffHand());
            for (int i = 0; i < 9; i++) repairStack(player.getInventory().getItem(i));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.15f);
            player.sendMessage(TextUtil.msg("🛡 착용 갑옷 및 핫바 장비 일괄 수리 완료! (-" + TextUtil.fmt(cost) + " 츄르)"));
            refreshRepairButtons(top, player);
        }
    }

    private void attemptEnhance(Player player, Inventory top) {
        ItemStack gear = top.getItem(SLOT_TARGET);
        GearKind kind = classifyGear(gear);
        if (gear == null || kind == null) return;
        int cur = getStarLevel(gear);
        if (cur >= 7) return;

        int next = cur + 1;
        double cost = STAR_CHURU_COST[next];
        int stonesNeeded = STAR_STONE_COST[next];

        if (!hasCustomItemCount(player, "starlight_stone", stonesNeeded)) {
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
            player.sendMessage(TextUtil.msg("<red>인벤토리에 <aqua>별빛 세공석</aqua>이 부족합니다! (필요: " + stonesNeeded + "개)"));
            return;
        }
        if (!churu.withdraw(player, cost)) {
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
            player.sendMessage(TextUtil.msg("<red>세공 비용이 부족합니다! (필요: " + TextUtil.fmt(cost) + " 츄르)"));
            return;
        }
        consumeCustomItem(player, "starlight_stone", stonesNeeded);

        ItemStack scrollSlot = top.getItem(SLOT_SCROLL);
        boolean usedScroll = false;
        if (next >= 6 && items.isCustomItem(scrollSlot, "starlight_scroll")) {
            usedScroll = true;
            if (scrollSlot.getAmount() <= 1) top.setItem(SLOT_SCROLL, null);
            else scrollSlot.setAmount(scrollSlot.getAmount() - 1);
        }

        boolean success = ThreadLocalRandom.current().nextDouble() < STAR_SUCCESS_RATE[next];
        if (success) {
            applyStarLore(gear, kind, next);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.25f);
            player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.2, 0), 30, 0.5, 0.6, 0.5, 0.12);
            player.sendMessage(TextUtil.msg("✨ <gold><bold>별빛 세공 성공!!</bold></gold> 장비가 <light_purple><bold>★" + next + "</bold></light_purple> 등급으로 빛납니다!"));
            if (next >= 6) {
                Bukkit.broadcast(TextUtil.msg("🌟 <white><bold>" + player.getName() + "</bold></white>님이 고양이 대장간에서 <gold><bold>[★" + next + " 별빛 세공]</bold></gold>에 성공했습니다!!"));
            }
        } else {
            int after = cur;
            if (next >= 6 && !usedScroll) {
                after = Math.max(0, cur - 1);
                applyStarLore(gear, kind, after);
                player.sendMessage(TextUtil.msg("<red>💥 별빛 마력이 흩어지며 등급이 ★" + after + "로 1단계 하락했습니다... (장비는 온전합니다!)"));
            } else if (usedScroll) {
                player.sendMessage(TextUtil.msg("<yellow>📜 세공에 실패했지만 <light_purple>[유미의 세공 보호권]</light_purple>이 등급 하락을 막아주었습니다! (★" + cur + " 유지)"));
            } else {
                player.sendMessage(TextUtil.msg("<gray>💨 별빛 마력이 스며들지 못했습니다. (★" + cur + " 안전 유지!)"));
            }
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.7f, 0.8f);
        }

        refreshForgeButton(top, player);
        refreshRepairButtons(top, player);
    }

    private void applyStarLore(ItemStack gear, GearKind kind, int star) {
        ItemMeta meta = gear.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(starKey, PersistentDataType.INTEGER, star);

        String starsBar = "★".repeat(star) + "☆".repeat(7 - star);
        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.mm("<gold>✦ 별빛 세공: <bold>" + starsBar + "</bold> <gray>(★" + star + ")"));
        switch (kind) {
            case WEAPON -> {
                lore.add(TextUtil.mm("<green>  + 추가 별빛 공격력: <bold>+" + WEAPON_BONUS_DMG[star] + "</bold>"));
                if (gear.getType().name().endsWith("_AXE")) {
                    lore.add(TextUtil.mm("<aqua>  + 벌목 효율 보너스 적용됨"));
                }
            }
            case TOOL -> lore.add(TextUtil.mm("<aqua>  + 채굴 효율 보너스: <bold>+" + TOOL_MINING_EFF[star] + "</bold>"));
            case ARMOR -> {
                lore.add(TextUtil.mm("<red>  + 최대 체력: <bold>+" + ARMOR_BONUS_HP[star] + " HP</bold>"));
                lore.add(TextUtil.mm("<blue>  + 방어 강도: <bold>+" + ARMOR_BONUS_TOUGH[star] + "</bold>"));
            }
        }
        meta.lore(lore);
        gear.setItemMeta(meta);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof ForgeHolder)) return;
        if (!(event.getPlayer() instanceof Player player)) return;
        Inventory inv = event.getInventory();
        for (int slot : new int[]{SLOT_TARGET, SLOT_SCROLL}) {
            ItemStack st = inv.getItem(slot);
            if (st != null && !st.getType().isAir()) {
                inv.setItem(slot, null);
                Map<Integer, ItemStack> left = player.getInventory().addItem(st);
                for (ItemStack drop : left.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
            }
        }
    }

    // ==================== 별빛 세공 전투·채굴 보너스 & 세공석 자연 드랍 ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onStarlightWeaponDamage(EntityDamageByEntityEvent event) {
        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (classifyGear(weapon) != GearKind.WEAPON) return;
        int star = getStarLevel(weapon);
        if (star <= 0) return;

        event.setDamage(event.getDamage() + WEAPON_BONUS_DMG[star]);
        if (star >= 5) {
            event.getEntity().getWorld().spawnParticle(Particle.END_ROD,
                    event.getEntity().getLocation().add(0, 1.0, 0), 6, 0.25, 0.3, 0.25, 0.05);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMineStarlightDrop(BlockBreakEvent event) {
        String name = event.getBlock().getType().name();
        if (name.endsWith("_ORE") || name.equals("ANCIENT_DEBRIS")) {
            double chance = (name.contains("DIAMOND") || name.contains("EMERALD") || name.equals("ANCIENT_DEBRIS")) ? 0.16 : 0.025;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                Player p = event.getPlayer();
                Map<Integer, ItemStack> left = p.getInventory().addItem(items.starlightStone(1));
                for (ItemStack drop : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);
                p.sendMessage(TextUtil.msg("✦ 광맥 속에서 영롱한 <aqua><bold>[찬란한 별빛 세공석]</bold></aqua>을 발견했습니다!"));
            }
        }
    }

    private boolean hasCustomItemCount(Player player, String id, int needed) {
        int sum = 0;
        for (ItemStack st : player.getInventory().getContents()) {
            if (items.isCustomItem(st, id)) sum += st.getAmount();
        }
        return sum >= needed;
    }

    private void consumeCustomItem(Player player, String id, int needed) {
        int rem = needed;
        for (ItemStack st : player.getInventory().getContents()) {
            if (rem <= 0) break;
            if (items.isCustomItem(st, id)) {
                int take = Math.min(rem, st.getAmount());
                st.setAmount(st.getAmount() - take);
                rem -= take;
            }
        }
    }

    private ItemStack icon(Material mat, String name, String... lore) {
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
