package kr.yuumi97.core.facility;

import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * ♨ 허브 동쪽 『사르르 캣닢 온천 · 유미네 특선 식당 · 응급 치유소』
 * 1) 노천 온천탕 15초 입수 시 15분 출격 버프 (생명력 강화 + 재생 + 채굴 성급함)
 * 2) 유미네 특선 식당 (특제 요리 4종 현장 판매 및 조리법 안내)
 * 3) 사망 시 인벤토리/경험치 100% 보존 + 응급 치유소 후송 + 지갑 8% 구조비(은행 예금 면제!) & 장비 내구도 손상
 */
public final class OnsenAndClinicManager implements Listener {

    public static final class DinerHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final JavaPlugin plugin;
    private final YuumiItems items;
    private final ChuruManager churu;
    private final Predicate<Location> isOnsenWater;
    private final Supplier<Location> clinicBedSupplier;

    private final Map<UUID, Integer> soakSeconds = new HashMap<>();
    private final Map<UUID, Double> lastRescueFee = new HashMap<>();

    public OnsenAndClinicManager(JavaPlugin plugin, YuumiItems items, ChuruManager churu,
                                 Predicate<Location> isOnsenWater, Supplier<Location> clinicBedSupplier) {
        this.plugin = plugin;
        this.items = items;
        this.churu = churu;
        this.isOnsenWater = isOnsenWater;
        this.clinicBedSupplier = clinicBedSupplier;
    }

    // ==================== 1. 사르르 캣닢 노천 온천 ====================

    public void startOnsenTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                Location loc = p.getLocation();
                if (isOnsenWater.test(loc) && (loc.getBlock().getType() == Material.WATER || p.isInWater())) {
                    int sec = soakSeconds.getOrDefault(p.getUniqueId(), 0) + 1;
                    soakSeconds.put(p.getUniqueId(), sec);
                    p.getWorld().spawnParticle(Particle.CHERRY_LEAVES, loc.clone().add(0, 1.1, 0), 2, 0.3, 0.2, 0.3, 0.01);

                    if (sec < 15) {
                        p.sendActionBar(TextUtil.mm("<#ff85c0>♨ 사르르 캣닢 온천욕 중... (" + sec + " / 15초)"));
                    } else if (sec == 15 || sec % 30 == 0) {
                        int dur = 20 * 60 * 15; // 15 minutes!
                        p.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, dur, 0, true, true, true));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, dur, 0, true, true, true));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, dur, 0, true, true, true));
                        p.playSound(loc, Sound.ENTITY_CAT_PURR, 1.0f, 1.1f);
                        p.playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.25f);
                        if (sec == 15) {
                            p.sendMessage(TextUtil.msg("<#ff85c0><bold>♨ 『사르르 캣닢 온천욕』 완료!</bold> <white>몸이 노곤하게 풀리며 <green>15분간 출격 버프(체력+성급함+재생)</green>가 부여되었습니다!"));
                        }
                    }
                } else {
                    soakSeconds.remove(p.getUniqueId());
                }
            }
        }, 20L, 20L);
    }

    // ==================== 2. 유미네 특선 식당 ====================

    public void openDinerGui(Player player) {
        Inventory inv = Bukkit.createInventory(new DinerHolder(), 27,
                TextUtil.mm("<#ff9c6e><bold>🍱 유미네 특선 식당</bold></#ff9c6e>"));
        ItemStack bg = icon(Material.ORANGE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, bg);

        inv.setItem(4, icon(Material.CAMPFIRE,
                "<gold><bold>🍳 주방장 뚱냥이의 오늘의 특선</bold></gold>",
                "<gray>직접 재료를 모아 조합대에서 요리해도 좋고,",
                "<gray>바쁜 출격 전에는 이곳에서 따끈한 완제품을 사 드세요!",
                "<white>내 보유 지갑: " + TextUtil.churu(churu.getBalance(player))));

        inv.setItem(10, priceWrap(items.zoomiesSkewer(1), 220));
        inv.setItem(12, priceWrap(items.kneadingBento(1), 420));
        inv.setItem(14, priceWrap(items.catnipElixir(1), 480));
        inv.setItem(16, priceWrap(items.royalFeastCan(1), 650));

        player.openInventory(inv);
    }

    private ItemStack priceWrap(ItemStack food, double price) {
        ItemMeta m = food.getItemMeta();
        var lore = m.lore() != null ? new java.util.ArrayList<>(m.lore()) : new java.util.ArrayList<net.kyori.adventure.text.Component>();
        lore.add(TextUtil.mm(""));
        lore.add(TextUtil.mm("<gold>● 식당 판매가: <bold>" + TextUtil.fmt(price) + " 츄르</bold></gold>"));
        lore.add(TextUtil.mm("<yellow>▶ 클릭하여 1개 주문하기"));
        m.lore(lore);
        food.setItemMeta(m);
        return food;
    }

    @EventHandler
    public void onDinerClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DinerHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack dish = null;
        double cost = 0;
        switch (event.getRawSlot()) {
            case 10 -> { dish = items.zoomiesSkewer(1); cost = 220; }
            case 12 -> { dish = items.kneadingBento(1); cost = 420; }
            case 14 -> { dish = items.catnipElixir(1); cost = 480; }
            case 16 -> { dish = items.royalFeastCan(1); cost = 650; }
        }
        if (dish == null) return;

        if (!churu.withdraw(player, cost)) {
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
            player.sendMessage(TextUtil.msg("<red>식사 값을 지불할 지갑 츄르가 부족합니다! (필요: " + TextUtil.fmt(cost) + " 츄르)"));
            return;
        }
        Map<Integer, ItemStack> left = player.getInventory().addItem(dish);
        for (ItemStack drop : left.values()) player.getWorld().dropItemNaturally(player.getLocation(), drop);
        player.playSound(player.getLocation(), Sound.ENTITY_CAT_EAT, 1f, 1.2f);
        player.sendMessage(TextUtil.msg("🍱 따끈따끈한 특제 요리가 나왔습니다! 맛있게 드세요! (-" + TextUtil.fmt(cost) + " 츄르)"));
        openDinerGui(player);
    }

    // ==================== 3. 응급 치유소 후송 & 장비 증발 방지 패널티 ====================

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        // 100% Protect inventory & XP from lava/void deletion!
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Calculate 8% wallet rescue fee (banked Churu is 100% safe!)
        double wallet = churu.getBalance(player);
        double rate = plugin.getConfig().getDouble("death-clinic.wallet-fee-rate", 0.08);
        double cap = plugin.getConfig().getDouble("death-clinic.wallet-fee-max", 5000.0);
        double fee = Math.min(cap, Math.floor(wallet * rate));
        if (fee > 0) {
            churu.withdraw(player, fee);
        }
        lastRescueFee.put(player.getUniqueId(), fee);

        // Apply 12% durability damage to worn armor & held weapons (never breaking below 3 durability!)
        double durRate = plugin.getConfig().getDouble("death-clinic.durability-damage-rate", 0.12);
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            applySafeDurabilityLoss(armor, durRate);
        }
        applySafeDurabilityLoss(player.getInventory().getItemInMainHand(), durRate);
        applySafeDurabilityLoss(player.getInventory().getItemInOffHand(), durRate);
    }

    private void applySafeDurabilityLoss(ItemStack st, double rate) {
        if (st == null || st.getType().isAir()) return;
        short maxDur = st.getType().getMaxDurability();
        if (maxDur <= 0 || !(st.getItemMeta() instanceof Damageable dmgMeta)) return;
        if (st.getItemMeta().isUnbreakable()) return;

        int penalty = Math.max(1, (int) Math.round(maxDur * rate));
        int maxAllowedDamage = Math.max(0, maxDur - 3); // Leave at least 3 durability so gear NEVER shatters!
        int nextDamage = Math.min(maxAllowedDamage, dmgMeta.getDamage() + penalty);
        if (nextDamage > dmgMeta.getDamage()) {
            dmgMeta.setDamage(nextDamage);
            st.setItemMeta((ItemMeta) dmgMeta);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Location bed = clinicBedSupplier.get();
        if (bed != null && bed.getWorld() != null) {
            event.setRespawnLocation(bed);
        }

        Player player = event.getPlayer();
        Double fee = lastRescueFee.remove(player.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_PURREOW, 1.0f, 1.15f);
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.5, 0), 8, 0.4, 0.3, 0.4, 0.02);
            player.sendMessage(TextUtil.mm(""));
            player.sendMessage(TextUtil.msg("<#ff85c0><bold>🏥 [유미 응급 치유소]</bold></#ff85c0> <white>구조대 냥이들이 소중한 장비를 모두 챙겨 치유소로 후송했습니다!"));
            if (fee != null && fee > 0) {
                player.sendMessage(TextUtil.mm("<gray> · 긴급 구조비 차감: <red>-" + TextUtil.fmt(fee) + " 츄르</red> <dark_gray>(지갑의 8% │ 은행 예금은 100% 보호됨!)"));
            } else {
                player.sendMessage(TextUtil.mm("<gray> · 긴급 구조비: <green>무료 처리됨!</green>"));
            }
            player.sendMessage(TextUtil.mm("<gray> · 충격으로 손상된 장비 내구도는 서쪽 <yellow>[고양이 대장간]</yellow>에서 수리하세요!"));
            player.sendMessage(TextUtil.mm(""));
        }, 10L);
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
