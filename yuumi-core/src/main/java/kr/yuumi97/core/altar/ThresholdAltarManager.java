package kr.yuumi97.core.altar;

import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.util.TextUtil;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class ThresholdAltarManager implements Listener, CommandExecutor {

    public static final class AltarGuiHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() { return null; }
    }

    private final JavaPlugin plugin;
    private final YuumiItems items;
    private final ChuruManager churu;
    private final FileConfiguration data;
    private final Runnable saveCallback;
    private Runnable onPhaseUnlockedCallback = () -> {};
    private final Map<Material, Integer> supplyValues = new EnumMap<>(Material.class);

    public ThresholdAltarManager(JavaPlugin plugin, YuumiItems items, ChuruManager churu, FileConfiguration data, Runnable saveCallback) {
        this.plugin = plugin;
        this.items = items;
        this.churu = churu;
        this.data = data;
        this.saveCallback = saveCallback;
        initSupplyTable();
    }

    public void setOnPhaseUnlockedCallback(Runnable cb) {
        this.onPhaseUnlockedCallback = cb != null ? cb : () -> {};
    }

    private void initSupplyTable() {
        // Type A Lifestyle supplies
        supplyValues.put(Material.WHEAT, 1);
        supplyValues.put(Material.CARROT, 1);
        supplyValues.put(Material.POTATO, 1);
        supplyValues.put(Material.PUMPKIN, 2);
        supplyValues.put(Material.MELON_SLICE, 1);
        supplyValues.put(Material.COOKED_COD, 3);
        supplyValues.put(Material.COOKED_SALMON, 3);
        supplyValues.put(Material.HONEY_BOTTLE, 5);
        supplyValues.put(Material.GOLDEN_CARROT, 6);
        // Type B Mining & Dungeon supplies
        supplyValues.put(Material.IRON_INGOT, 3);
        supplyValues.put(Material.GOLD_INGOT, 4);
        supplyValues.put(Material.AMETHYST_SHARD, 4);
        supplyValues.put(Material.DIAMOND, 15);
        supplyValues.put(Material.EMERALD, 8);
        supplyValues.put(Material.BLAZE_ROD, 10);
        supplyValues.put(Material.QUARTZ, 2);
        supplyValues.put(Material.ENDER_PEARL, 6);
    }

    public int getCurrentPhase() {
        return Math.max(1, data.getInt("altar.phase", 1));
    }

    public String getPhaseTitle() {
        return switch (getCurrentPhase()) {
            case 1 -> "제1장: 고양이 마을의 개척";
            case 2 -> "제2장: 불타는 네더 원정기";
            default -> "제3장: 공허 너머의 대단원";
        };
    }

    private int getPagesDonated() { return data.getInt("altar.pages", 0); }
    private int getChuruDonated() { return data.getInt("altar.churu", 0); }
    private int getSuppliesDonated() { return data.getInt("altar.supplies", 0); }

    private int reqPages(int targetPhase) {
        String path = targetPhase == 2 ? "threshold-altar.phase2-nether.pages-required" : "threshold-altar.phase3-end.pages-required";
        return plugin.getConfig().getInt(path, targetPhase == 2 ? 30 : 60);
    }

    private int reqChuru(int targetPhase) {
        String path = targetPhase == 2 ? "threshold-altar.phase2-nether.churu-required" : "threshold-altar.phase3-end.churu-required";
        return plugin.getConfig().getInt(path, targetPhase == 2 ? 25000 : 80000);
    }

    private int reqSupplies(int targetPhase) {
        String path = targetPhase == 2 ? "threshold-altar.phase2-nether.supply-points-required" : "threshold-altar.phase3-end.supply-points-required";
        return plugin.getConfig().getInt(path, targetPhase == 2 ? 500 : 1200);
    }

    private void addContribution(Player player, int score) {
        String path = "altar.contributors." + player.getUniqueId();
        data.set(path, data.getInt(path, 0) + score);
    }

    public void openAltarGui(Player player) {
        Inventory inv = Bukkit.createInventory(new AltarGuiHolder(), 45,
                TextUtil.mm("<dark_purple><bold>📖 문턱의 서 제단</bold> <gray>(" + getPhaseTitle() + ")"));

        ItemStack pane = makeIcon(Material.PURPLE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, pane);

        int phase = getCurrentPhase();
        if (phase < 3) {
            int nextPhase = phase + 1;
            String unlockName = nextPhase == 2 ? "<red><bold>네더 차원문 & 2티어 보스 해금</bold></red>" : "<light_purple><bold>엔드 차원문 & 최종 보스 해금</bold></light_purple>";
            int pCur = getPagesDonated(), pMax = reqPages(nextPhase);
            int cCur = getChuruDonated(), cMax = reqChuru(nextPhase);
            int sCur = getSuppliesDonated(), sMax = reqSupplies(nextPhase);

            ItemStack status = items.bookOfThresholds();
            ItemMeta sm = status.getItemMeta();
            sm.displayName(TextUtil.mm("<gold><bold>✦ 현재 진행: " + getPhaseTitle() + " ✦</bold></gold>"));
            sm.lore(TextUtil.lore(
                    "<gray>8인의 지인이 힘을 합쳐 마력을 채우면 다음 장이 열립니다!",
                    "",
                    "<white>목표 보상: " + unlockName,
                    "",
                    "<aqua>1. 찢어진 책장: <white>" + pCur + " / " + pMax + "장 " + TextUtil.progressBar(pCur, pMax, 10),
                    "<gold>2. 츄르 기금: <white>" + TextUtil.fmt(cCur) + " / " + TextUtil.fmt(cMax) + " " + TextUtil.progressBar(cCur, cMax, 10),
                    "<green>3. 마을 보급품: <white>" + sCur + " / " + sMax + "점 " + TextUtil.progressBar(sCur, sMax, 10)
            ));
            status.setItemMeta(sm);
            inv.setItem(13, status);

            // Button 1: Donate Torn Pages
            inv.setItem(20, makeIcon(Material.PAPER, "<aqua><bold>[클릭] 찢어진 마법 책장 납품하기</bold>",
                    "<gray>인벤토리의 <aqua>찢어진 마법 책장</aqua>을 모두 제단에 바칩니다.",
                    "",
                    "<green>✦ 납품 감사 보상: 장당 <gold>250 츄르</gold> 지급!",
                    "<yellow>▶ 클릭하여 납품"));

            // Button 2: Donate Churu (1,000 / Shift: 5,000)
            inv.setItem(22, makeIcon(Material.GOLD_INGOT, "<gold><bold>[클릭] 마을 발전 츄르 기금 후원</bold>",
                    "<gray>문턱의 서 복원을 위해 보유 츄르를 후원합니다.",
                    "",
                    "<yellow>▶ 좌클릭: <gold>1,000 츄르</gold> 후원",
                    "<yellow>▶ 쉬프트+클릭: <gold>5,000 츄르</gold> 후원"));

            // Button 3: Donate Lifestyle & Dungeon Supplies
            inv.setItem(24, makeIcon(Material.CHEST, "<green><bold>[클릭] 생활·탐험 보급 물자 일괄 납품</bold>",
                    "<gray>인벤토리에 있는 보급 자원을 제단에 납품합니다.",
                    "<gray>(밀/당근/감자/구운생선/철/금/자수정/다이아/블레이즈막대 등)",
                    "<dark_gray>※ 핫바(1~9번 칸)에 든 아이템은 실수 방지를 위해 제외됩니다!",
                    "",
                    "<green>✦ 보급 점수 1점당 <gold>15 츄르</gold> 환급!",
                    "<yellow>▶ 클릭하여 가방(윗줄) 보급품 납품"));
        } else {
            inv.setItem(13, makeIcon(Material.END_CRYSTAL, "<light_purple><bold>★ 제3장 대단원 복원 완료! ★</bold>",
                    "<gray>유미의 문턱의 서가 모든 차원의 문을 열었습니다!",
                    "<green>· 오버월드 / 네더 / 엔드 모든 차원 개방됨",
                    "<green>· 1~3티어 모든 월드보스 소환 가능 (<white>/레이드</white>)"));
        }

        // Leaderboard at slot 40
        List<String> rankLines = new ArrayList<>();
        rankLines.add("<gray>생활 납품과 원정 후원으로 마을을 키운 주역들!");
        rankLines.add("");
        ConfigurationSection sec = data.getConfigurationSection("altar.contributors");
        if (sec == null || sec.getKeys(false).isEmpty()) {
            rankLines.add("<dark_gray>아직 첫 납품자가 없습니다. 이름을 남겨보세요!");
        } else {
            List<Map.Entry<String, Integer>> list = new ArrayList<>();
            for (String uuidStr : sec.getKeys(false)) {
                list.add(Map.entry(uuidStr, sec.getInt(uuidStr, 0)));
            }
            list.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed());
            int rank = 1;
            for (Map.Entry<String, Integer> entry : list) {
                if (rank > 8) break;
                OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(entry.getKey()));
                String name = op.getName() != null ? op.getName() : "모험가";
                rankLines.add("<yellow>" + rank + "위. <white>" + name + " <gray>- <aqua>" + TextUtil.fmt(entry.getValue()) + " 기여점수");
                rank++;
            }
        }
        inv.setItem(40, makeIcon(Material.BEACON, "<gradient:#ffd666:#ff85c0><bold>🏆 97랜드 번영 기여도 명예의 전당</bold></gradient>",
                rankLines.toArray(new String[0])));

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AltarGuiHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int phase = getCurrentPhase();
        if (phase >= 3) return;
        int nextPhase = phase + 1;
        int slot = event.getRawSlot();

        if (slot == 20) {
            // Donate torn pages
            int needed = Math.max(0, reqPages(nextPhase) - getPagesDonated());
            if (needed <= 0) {
                player.sendMessage(TextUtil.msg("<green>찢어진 책장 목표치는 이미 100% 달성되었습니다!"));
                return;
            }
            int donated = 0;
            ItemStack[] contents = player.getInventory().getContents();
            for (int i = 0; i < contents.length && donated < needed; i++) {
                ItemStack st = contents[i];
                if (items.isCustomItem(st, "torn_page")) {
                    int take = Math.min(st.getAmount(), needed - donated);
                    st.setAmount(st.getAmount() - take);
                    donated += take;
                }
            }
            if (donated <= 0) {
                player.playSound(player.getLocation(), Sound.ENTITY_CAT_STRAY_AMBIENT, 1f, 0.9f);
                player.sendMessage(TextUtil.msg("<red>인벤토리에 <aqua>찢어진 마법 책장</aqua>이 없습니다! (농사/낚시/채광/사냥 시 획득)"));
                return;
            }
            data.set("altar.pages", getPagesDonated() + donated);
            addContribution(player, donated * 25);
            double reward = donated * 250.0;
            churu.deposit(player, reward);
            saveCallback.run();
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.3f);
            Bukkit.broadcast(TextUtil.msg("<white>" + player.getName() + "</white>님이 문턱의 서 제단에 <aqua>찢어진 마법 책장 " + donated + "장</aqua>을 바쳤습니다! 🐾"));
            player.sendMessage(TextUtil.msg("감사 보상으로 " + TextUtil.churu(reward) + "<gray>가 지급되었습니다!"));
            checkPhaseAdvance();
            openAltarGui(player);
        } else if (slot == 22) {
            int needed = Math.max(0, reqChuru(nextPhase) - getChuruDonated());
            if (needed <= 0) {
                player.sendMessage(TextUtil.msg("<green>츄르 기금 목표치는 이미 100% 달성되었습니다!"));
                return;
            }
            int want = event.isShiftClick() ? 5000 : 1000;
            int actual = Math.min(want, needed);
            if (!churu.withdraw(player, actual)) {
                player.sendMessage(TextUtil.msg("<red>보유 츄르가 부족합니다!"));
                return;
            }
            data.set("altar.churu", getChuruDonated() + actual);
            addContribution(player, actual / 20);
            saveCallback.run();
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_PURREOW, 1f, 1.25f);
            Bukkit.broadcast(TextUtil.msg("<white>" + player.getName() + "</white>님이 문턱의 서 복원 기금으로 " + TextUtil.churu(actual) + "<gray>를 쾌척했습니다!"));
            checkPhaseAdvance();
            openAltarGui(player);
        } else if (slot == 24) {
            int needed = Math.max(0, reqSupplies(nextPhase) - getSuppliesDonated());
            if (needed <= 0) {
                player.sendMessage(TextUtil.msg("<green>보급 물자 목표치는 이미 100% 달성되었습니다!"));
                return;
            }
            int gainedPts = 0;
            // Only scan upper storage inventory (slots 9..35) to protect hotbar items!
            for (int i = 9; i < 36 && gainedPts < needed; i++) {
                ItemStack st = player.getInventory().getItem(i);
                if (st == null || st.getType().isAir() || items.getCustomId(st) != null) continue;
                Integer ptPerItem = supplyValues.get(st.getType());
                if (ptPerItem == null) continue;
                int maxItemsNeeded = (int) Math.ceil((double) (needed - gainedPts) / ptPerItem);
                int take = Math.min(st.getAmount(), maxItemsNeeded);
                st.setAmount(st.getAmount() - take);
                gainedPts += take * ptPerItem;
            }
            if (gainedPts <= 0) {
                player.sendMessage(TextUtil.msg("<red>납품 가능한 보급 물자가 가방(윗줄 인벤토리 9~35번 칸)에 없습니다! (핫바 제외)"));
                return;
            }
            int cappedPts = Math.min(needed, gainedPts);
            data.set("altar.supplies", getSuppliesDonated() + cappedPts);
            addContribution(player, cappedPts);
            double payback = cappedPts * 15.0;
            churu.deposit(player, payback);
            saveCallback.run();
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_CELEBRATE, 1f, 1.2f);
            player.sendMessage(TextUtil.msg("보급 물자 <green>" + cappedPts + "점</green> 납품 완료! 수고비로 " + TextUtil.churu(payback) + "<gray>를 받았습니다!"));
            checkPhaseAdvance();
            openAltarGui(player);
        }
    }

    private void checkPhaseAdvance() {
        int phase = getCurrentPhase();
        if (phase >= 3) return;
        int nextPhase = phase + 1;
        if (getPagesDonated() >= reqPages(nextPhase)
                && getChuruDonated() >= reqChuru(nextPhase)
                && getSuppliesDonated() >= reqSupplies(nextPhase)) {

            data.set("altar.phase", nextPhase);
            data.set("altar.pages", 0);
            data.set("altar.churu", 0);
            data.set("altar.supplies", 0);
            saveCallback.run();
            onPhaseUnlockedCallback.run();

            String mainTitle = nextPhase == 2
                    ? "<gradient:#ff4d4f:#ffd666><bold>🔥 제2장: 네더 차원문 개방! 🔥</bold></gradient>"
                    : "<gradient:#b37feb:#5cdbd3><bold>🌌 제3장: 엔드 차원문 개방! 🌌</bold></gradient>";
            String subTitle = nextPhase == 2
                    ? "<white>유미의 문턱의 서가 복원되어 네더와 2티어 보스가 해금되었습니다!"
                    : "<white>모든 차원이 열렸습니다! 엔드 대륙과 최종 보스에 도전하세요!";

            Bukkit.broadcast(TextUtil.mm(""));
            Bukkit.broadcast(TextUtil.mm("<gold>====================================================="));
            Bukkit.broadcast(TextUtil.mm("  " + mainTitle));
            Bukkit.broadcast(TextUtil.mm("  " + subTitle));
            Bukkit.broadcast(TextUtil.mm("<gold>====================================================="));
            Bukkit.broadcast(TextUtil.mm(""));

            Title titleObj = Title.title(TextUtil.mm(mainTitle), TextUtil.mm(subTitle),
                    Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofSeconds(1)));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(titleObj);
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                p.playSound(p.getLocation(), Sound.ENTITY_CAT_PURREOW, 1.0f, 1.1f);
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1.2, 0), 40, 0.6, 0.8, 0.6, 0.2);
            }
        }
    }

    // ==================== 차원 잠금 (자율 페이즈 게이트) ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        Player player = event.getPlayer();
        PlayerTeleportEvent.TeleportCause cause = event.getCause();

        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL && getCurrentPhase() < 2) {
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
            player.sendMessage(TextUtil.msg("<red>아직 문턱의 서 제2장이 복원되지 않아 네더 차원문이 봉인되어 있습니다! (<yellow>/제단</yellow> 확인)"));
        } else if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL && getCurrentPhase() < 3) {
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
            player.sendMessage(TextUtil.msg("<light_purple>아직 문턱의 서 제3장이 복원되지 않아 엔드 차원문이 봉인되어 있습니다! (<yellow>/제단</yellow> 확인)"));
        }
    }

    // ==================== 야생 활동 시 '찢어진 마법 책장' 자연 드랍 ====================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            // 18% chance when fishing!
            if (ThreadLocalRandom.current().nextDouble() < 0.18) {
                giveTornPageDrop(event.getPlayer(), "낚시 도중 물병 속에서");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        Material type = b.getType();
        if (b.getBlockData() instanceof Ageable ageable && ageable.getAge() == ageable.getMaximumAge()) {
            if (ThreadLocalRandom.current().nextDouble() < 0.045) {
                giveTornPageDrop(event.getPlayer(), "농작물 수확 중");
            }
            return;
        }
        String name = type.name();
        if (name.endsWith("_ORE") || type == Material.ANCIENT_DEBRIS) {
            double chance = (name.contains("DIAMOND") || name.contains("EMERALD") || type == Material.ANCIENT_DEBRIS) ? 0.20 : 0.05;
            if (ThreadLocalRandom.current().nextDouble() < chance) {
                giveTornPageDrop(event.getPlayer(), "광맥 채굴 중");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobKill(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster mob)) return;
        Player killer = mob.getKiller();
        if (killer == null) return;
        double chance = mob.getWorld().getEnvironment() == World.Environment.NETHER ? 0.10 : 0.06;
        if (ThreadLocalRandom.current().nextDouble() < chance) {
            giveTornPageDrop(killer, "몬스터 토벌 전리품으로");
        }
    }

    private void giveTornPageDrop(Player player, String sourceDesc) {
        Map<Integer, ItemStack> left = player.getInventory().addItem(items.tornPage(1));
        for (ItemStack drop : left.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), drop);
        }
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.9f, 1.4f);
        player.sendMessage(TextUtil.msg("<aqua>✨ " + sourceDesc + " <bold>[문턱의 서: 찢어진 마법 책장]</bold>을 발견했습니다! (<white>/제단</white>)"));
    }

    private ItemStack makeIcon(Material mat, String name, String... lore) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.mm(name));
            if (lore.length > 0) meta.lore(TextUtil.lore(lore));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (args.length >= 2 && args[0].equalsIgnoreCase("setphase") && player.isOp()) {
            int p = Math.max(1, Math.min(3, Integer.parseInt(args[1])));
            data.set("altar.phase", p);
            saveCallback.run();
            onPhaseUnlockedCallback.run();
            player.sendMessage(TextUtil.msg("[관리자] 문턱의 서 페이즈가 " + p + "장으로 설정되었으며 성소 대포탈 상태가 갱신되었습니다."));
            return true;
        }
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
        player.sendMessage(TextUtil.msg("📖 현재 진행: <aqua><bold>" + getPhaseTitle() + "</bold></aqua> — 책장 및 보급품 납품은 허브 북쪽 <yellow>[문턱의 서 대포탈 성소 중앙 유미 제단]</yellow>에서 현장 진행하실 수 있습니다!"));
        return true;
    }
}
