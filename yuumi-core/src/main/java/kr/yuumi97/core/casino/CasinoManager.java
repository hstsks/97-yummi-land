package kr.yuumi97.core.casino;

import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.util.TextUtil;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 🎰 황금 냥냥 카지노 v2.0 (2층 단독 리얼 카지노 홀 시설)
 * - 원격 명령어 입장을 폐지하고 카지노 입구 [환전소] 실물 칩 환전 + 1F/2F 물리 기기·테이블 착석 체험으로 전면 개편!
 */
public final class CasinoManager implements Listener, CommandExecutor {

    public enum GuiType { CASHIER, SLOTS_STD, SLOTS_VIP, BLACKJACK, ROULETTE }

    public static final class CasinoHolder implements InventoryHolder {
        public final GuiType type;
        public CasinoHolder(GuiType type) { this.type = type; }
        @Override public Inventory getInventory() { return null; }
    }

    private record Card(String rank, String suit, int value) {
        String display() { return suit + rank; }
    }

    private static final class BlackjackSession {
        int betChips;
        boolean vip;
        List<Card> deck = new ArrayList<>();
        List<Card> playerHand = new ArrayList<>();
        List<Card> dealerHand = new ArrayList<>();
        boolean finished = false;
    }

    private record DuelChallenge(UUID challenger, UUID target, int chips, long expiresAt) {}

    // Casino building bounding box inside yuumi_hub: X in [42..84], Z in [-38..-8]
    public static final int CASINO_MIN_X = 41, CASINO_MAX_X = 85;
    public static final int CASINO_MIN_Z = -39, CASINO_MAX_Z = -7;

    private final JavaPlugin plugin;
    private final YuumiItems items;
    private final ChuruManager churu;
    private final FileConfiguration data;
    private final Runnable saveCallback;
    private Supplier<World> hubWorldSupplier = () -> null;
    private Runnable rouletteAnimation = () -> {};

    public void setRouletteAnimation(Runnable animation) { this.rouletteAnimation = animation; }

    private final Set<UUID> spinningPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BlackjackSession> blackjackSessions = new HashMap<>();
    private final Map<UUID, DuelChallenge> pendingDuelsByTarget = new HashMap<>();

    public CasinoManager(JavaPlugin plugin, YuumiItems items, ChuruManager churu, FileConfiguration data, Runnable saveCallback) {
        this.plugin = plugin;
        this.items = items;
        this.churu = churu;
        this.data = data;
        this.saveCallback = saveCallback;
        if (!data.contains("casino.jackpot")) {
            data.set("casino.jackpot", plugin.getConfig().getDouble("casino.initial-jackpot", 15000.0));
            saveCallback.run();
        }
    }

    public void setHubWorldSupplier(Supplier<World> hubWorldSupplier) {
        this.hubWorldSupplier = hubWorldSupplier;
    }

    public boolean isInsideCasinoHall(Location loc) {
        World hub = hubWorldSupplier.get();
        if (loc == null || hub == null || !hub.equals(loc.getWorld())) return false;
        int x = loc.getBlockX(), z = loc.getBlockZ();
        return x >= CASINO_MIN_X && x <= CASINO_MAX_X && z >= CASINO_MIN_Z && z <= CASINO_MAX_Z;
    }

    public double getJackpot() {
        double min = plugin.getConfig().getDouble("casino.initial-jackpot", 15000.0);
        return Math.max(min, data.getDouble("casino.jackpot", min));
    }

    public void addToJackpot(double lostBet) {
        double rake = plugin.getConfig().getDouble("casino.jackpot-rake-rate", 0.15);
        data.set("casino.jackpot", getJackpot() + Math.max(1.0, Math.round(lostBet * rake)));
        saveCallback.run();
    }

    /** Exact transfer, unlike the rake applied to lost casino bets. */
    public void depositJackpot(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return;
        data.set("casino.jackpot", getJackpot() + amount);
        saveCallback.run();
    }

    private double claimAndResetJackpot() {
        double won = getJackpot();
        data.set("casino.jackpot", plugin.getConfig().getDouble("casino.initial-jackpot", 15000.0));
        saveCallback.run();
        return won;
    }

    // ==================== 실물 카지노 칩 지갑 엔진 ====================

    public int countPlayerChips(Player player) {
        int sum = 0;
        for (ItemStack st : player.getInventory().getStorageContents()) {
            sum += chipValueOf(st);
        }
        sum += chipValueOf(player.getInventory().getItemInOffHand());
        return sum;
    }

    private int chipValueOf(ItemStack st) {
        if (st == null || st.getType().isAir()) return 0;
        String id = items.getCustomId(st);
        if ("chip_100".equals(id)) return 100 * st.getAmount();
        if ("chip_1000".equals(id)) return 1000 * st.getAmount();
        if ("chip_10000".equals(id)) return 10000 * st.getAmount();
        return 0;
    }

    public boolean spendChips(Player player, int neededChips) {
        if (neededChips <= 0) return true;
        int total = countPlayerChips(player);
        if (total < neededChips) return false;

        // Clear all chips and return exact optimal change!
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (chipValueOf(contents[i]) > 0) player.getInventory().setItem(i, null);
        }
        if (chipValueOf(player.getInventory().getItemInOffHand()) > 0) {
            player.getInventory().setItemInOffHand(null);
        }

        int change = total - neededChips;
        if (change > 0) {
            giveChips(player, change);
        }
        return true;
    }

    public void giveChips(Player player, int amount) {
        if (amount <= 0) return;
        int rem = amount;
        int c10k = rem / 10000; rem %= 10000;
        int c1k = rem / 1000;   rem %= 1000;
        int c100 = rem / 100;   rem %= 100;

        if (c10k > 0) addStack(player, items.chip10000(1), c10k);
        if (c1k > 0)  addStack(player, items.chip1000(1), c1k);
        if (c100 > 0) addStack(player, items.chip100(1), c100);
        // Any fractional remainder (<100, e.g. from 1.2x or 2.2x odds) goes straight to Wallet Churu!
        if (rem > 0) {
            churu.deposit(player, rem);
        }
    }

    private void addStack(Player p, ItemStack proto, int totalCount) {
        int rem = totalCount;
        while (rem > 0) {
            int batch = Math.min(64, rem);
            ItemStack copy = proto.clone();
            copy.setAmount(batch);
            Map<Integer, ItemStack> left = p.getInventory().addItem(copy);
            for (ItemStack drop : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), drop);
            }
            rem -= batch;
        }
    }

    // ==================== 물리 블록 우클릭 기기 연동 ====================

    @EventHandler
    public void onClickPhysicalCasinoMachine(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Block b = event.getClickedBlock();
        if (!isInsideCasinoHall(b.getLocation())) return;

        Player p = event.getPlayer();
        Material type = b.getType();
        int y = b.getY();
        boolean isVipFloor = y >= 70;

        if (type == Material.NOTE_BLOCK || type == Material.LEVER) {
            event.setCancelled(true);
            openSlotsGui(p, isVipFloor);
        } else if (type == Material.GREEN_CARPET || type == Material.DARK_OAK_STAIRS) {
            // Only trigger table if adjacent to emerald/gold dealer table block
            if (hasNearbyBlock(b, Material.EMERALD_BLOCK)) {
                event.setCancelled(true);
                openBlackjackSetup(p, isVipFloor);
            }
        } else if (type == Material.EMERALD_BLOCK) {
            event.setCancelled(true);
            openBlackjackSetup(p, isVipFloor);
        } else if (type == Material.LODESTONE || type == Material.REDSTONE_LAMP) {
            event.setCancelled(true);
            openRouletteGui(p);
        } else if (type == Material.TARGET) {
            event.setCancelled(true);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.3f);
            p.sendMessage(TextUtil.msg("🎲 <gold><bold>[1:1 주사위 데스매치 테이블]</bold></gold> 맞은편 지인에게 <yellow>/도박신청 <닉네임> <칩수량></yellow>을 입력해 단판 승부를 걸어보세요!"));
        }
    }

    private boolean hasNearbyBlock(Block center, Material target) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (center.getRelative(dx, 0, dz).getType() == target || center.getRelative(dx, -1, dz).getType() == target) {
                    return true;
                }
            }
        }
        return false;
    }

    // ==================== 0. 입구 칩 환전소 (Cashier Cage) ====================

    public void openCashierGui(Player player) {
        Inventory inv = Bukkit.createInventory(new CasinoHolder(GuiType.CASHIER), 45,
                TextUtil.mm("<gold><bold>💱 황금 냥냥 카지노 칩 환전소</bold></gold>"));

        ItemStack bg = icon(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, bg);

        int myChips = countPlayerChips(player);
        inv.setItem(4, items.slotIcon("slot_seven", "<gradient:#ffe14d:#ff4d79><bold>★ 카지노 환전 창구 (수수료 0%) ★</bold></gradient>"));
        ItemMeta hm = inv.getItem(4).getItemMeta();
        hm.lore(TextUtil.lore(
                "<white>내 보유 지갑: " + TextUtil.churu(churu.getBalance(player)),
                "<aqua>내 보유 칩 합계: <bold>" + TextUtil.fmt(myChips) + " 칩</bold></aqua>",
                "<yellow>현재 서버 누적 잭팟: <bold>" + TextUtil.fmt(getJackpot()) + " 츄르</bold></yellow>",
                "",
                "<gray>카지노의 모든 슬롯·블랙잭·룰렛·고양이 경주는",
                "<gray>이곳에서 환전한 실물 <white>[카지노 칩]</white>으로 플레이합니다!"
        ));
        inv.getItem(4).setItemMeta(hm);

        // Buy Chips
        inv.setItem(19, wrapChipBuy(items.chip100(1), 100));
        inv.setItem(21, wrapChipBuy(items.chip1000(1), 1000));
        inv.setItem(23, wrapChipBuy(items.chip10000(1), 10000));

        // Cash out all chips -> Wallet Churu!
        inv.setItem(25, icon(Material.GOLD_BLOCK,
                "<green><bold>💰 [정산] 보유 카지노 칩 전액 츄르로 환전!</bold></green>",
                "<gray>인벤토리의 모든 카지노 칩을 1:1 비율로",
                "<gray>지갑 츄르로 즉시 정산합니다!",
                "",
                "<white>정산 예정 금액: " + TextUtil.churu(myChips),
                "<yellow>▶ 클릭하여 전액 츄르로 바꾸기"));

        // Floor Guide
        inv.setItem(40, icon(Material.BOOK,
                "<aqua><bold>🗺 카지노 홀 시설 안내</bold></aqua>",
                "<white>· 1층 서측 벽면: <yellow>일반 슬롯머신 부스 (100~1,000칩)</yellow>",
                "<white>· 1층 중앙 트랙: <gold><bold>실시간 5레인 고양이 달리기 경주 (경묘!)</bold></gold>",
                "<white>· 1층 동측 홀: <green>블랙잭 테이블 · 룰렛 휠 · 1:1 주사위석</green>",
                "<white>· 2층 VIP 라운지: <light_purple>하이롤러 슬롯(잭팟 우대!) & VIP 블랙잭</light_purple>",
                "<white>· 서쪽 뒷문 밖: <#ff9c6e>파산 길냥이 무료 급식소</#ff9c6e>"));

        player.openInventory(inv);
    }

    private ItemStack wrapChipBuy(ItemStack chip, int unit) {
        ItemMeta m = chip.getItemMeta();
        m.lore(TextUtil.lore(
                "<gray>지갑 츄르로 카지노 실물 칩을 구매합니다.",
                "",
                "<yellow>▶ 좌클릭: 1개 구매 (" + TextUtil.fmt(unit) + " 츄르)",
                "<yellow>▶ 쉬프트+클릭: 10개 구매 (" + TextUtil.fmt(unit * 10L) + " 츄르)"
        ));
        chip.setItemMeta(m);
        return chip;
    }

    // ==================== 1. 슬롯머신 (1F 일반 / 2F VIP) ====================

    public void openSlotsGui(Player player, boolean vip) {
        GuiType gt = vip ? GuiType.SLOTS_VIP : GuiType.SLOTS_STD;
        String title = vip ? "<gradient:#ffe14d:#ff2457><bold>👑 2F VIP 황금 슬롯머신</bold></gradient>"
                           : "<#ff85c0><bold>🎰 1F 유미의 슬롯머신</bold></#ff85c0>";
        Inventory inv = Bukkit.createInventory(new CasinoHolder(gt), 45,
                TextUtil.mm(title + " <gray>(잭팟: " + TextUtil.fmt(getJackpot()) + ")"));

        ItemStack bg = icon(vip ? Material.PURPLE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, bg);

        ItemStack frame = icon(Material.GOLD_BLOCK, "<gold>▼ 행운의 당첨 라인 (보유: " + TextUtil.fmt(countPlayerChips(player)) + "칩) ▼");
        inv.setItem(3, frame); inv.setItem(4, frame); inv.setItem(5, frame);
        inv.setItem(21, icon(Material.GOLD_BLOCK, "<gold>▲ 행운의 당첨 라인 ▲"));
        inv.setItem(22, icon(Material.GOLD_BLOCK, "<gold>▲ 행운의 당첨 라인 ▲"));
        inv.setItem(23, icon(Material.GOLD_BLOCK, "<gold>▲ 행운의 당첨 라인 ▲"));

        inv.setItem(12, items.slotIcon("slot_paw", "<#ff85c0><bold>🐾 핑크 발바닥</bold>"));
        inv.setItem(13, items.slotIcon("slot_yuumi", "<aqua><bold>🐱 마법고양이 유미</bold>"));
        inv.setItem(14, items.slotIcon("slot_seven", "<gold><bold>7️⃣ 럭키 세븐</bold>"));

        if (!vip) {
            inv.setItem(29, makeSlotBetBtn(100, false, "가볍게 100칩 스핀!"));
            inv.setItem(30, makeSlotBetBtn(300, false, "기분 좋은 300칩 스핀!"));
            inv.setItem(32, makeSlotBetBtn(500, false, "짜릿한 500칩 스핀!"));
            inv.setItem(33, makeSlotBetBtn(1000, false, "1층 최고 한도 1,000칩 스핀!"));
        } else {
            inv.setItem(29, makeSlotBetBtn(2000, true, "VIP 2,000칩 스핀 (777 확률 우대!)"));
            inv.setItem(31, makeSlotBetBtn(5000, true, "VIP 5,000칩 스핀 (777 확률 우대!)"));
            inv.setItem(33, makeSlotBetBtn(10000, true, "★ 로얄 10,000칩 스핀 (인생 역전!)"));
        }

        player.openInventory(inv);
    }

    private ItemStack makeSlotBetBtn(int chips, boolean vip, String desc) {
        ItemStack chip = vip ? items.chip10000(1) : (chips >= 1000 ? items.chip1000(1) : items.chip100(1));
        ItemMeta m = chip.getItemMeta();
        m.displayName(TextUtil.mm("<gold><bold>▶ " + TextUtil.fmt(chips) + " 칩 스핀 돌리기!</bold></gold>"));
        m.lore(TextUtil.lore("<gray>" + desc, "", "<yellow>클릭 시 실물 칩을 투입해 릴을 회전합니다!"));
        chip.setItemMeta(m);
        return chip;
    }

    private record ReelSymbol(String key, String modelId, Material fallbackMat, String label, double mult3, int stdWeight, int vipWeight) {}

    private static final List<ReelSymbol> REEL_POOL = List.of(
            new ReelSymbol("FISH", null, Material.COD, "<aqua><bold>🐟 싱싱한 생선</bold>", 2.5, 36, 32),
            new ReelSymbol("PAW", "slot_paw", Material.PAPER, "<#ff85c0><bold>🐾 핑크 발바닥</bold>", 4.0, 28, 27),
            new ReelSymbol("BOOK", "book_of_thresholds", Material.PAPER, "<light_purple><bold>📖 문턱의 서</bold>", 6.5, 18, 19),
            new ReelSymbol("YUUMI", "slot_yuumi", Material.PAPER, "<yellow><bold>🐱 마법고양이 유미</bold>", 12.0, 12, 13),
            new ReelSymbol("SEVEN", "slot_seven", Material.PAPER, "<gradient:#ffe14d:#ff2457><bold>7️⃣ 럭키 777</bold></gradient>", 25.0, 6, 9)
    );

    private ReelSymbol rollSymbol(boolean vip) {
        int total = 0;
        for (ReelSymbol s : REEL_POOL) total += (vip ? s.vipWeight : s.stdWeight);
        int r = ThreadLocalRandom.current().nextInt(total);
        int acc = 0;
        for (ReelSymbol s : REEL_POOL) {
            acc += (vip ? s.vipWeight : s.stdWeight);
            if (r < acc) return s;
        }
        return REEL_POOL.get(0);
    }

    private ItemStack renderSymbol(ReelSymbol s) {
        if (s.modelId != null) return items.slotIcon(s.modelId, s.label);
        return icon(s.fallbackMat, s.label);
    }

    private void startSlotSpin(Player player, Inventory inv, int betChips, boolean vip) {
        if (spinningPlayers.contains(player.getUniqueId())) return;
        if (!spendChips(player, betChips)) {
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
            player.sendMessage(TextUtil.msg("<red>보유한 카지노 칩이 부족합니다! 입구 <yellow>[환전소]</yellow>에서 칩을 환전해 오세요."));
            return;
        }

        spinningPlayers.add(player.getUniqueId());
        ReelSymbol f1 = rollSymbol(vip);
        ReelSymbol f2 = rollSymbol(vip);
        ReelSymbol f3 = rollSymbol(vip);

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                tick++;
                if (!player.isOnline()) {
                    spinningPlayers.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                if (tick <= 8) inv.setItem(12, renderSymbol(tick == 8 ? f1 : rollSymbol(vip)));
                if (tick <= 13) inv.setItem(13, renderSymbol(tick == 13 ? f2 : rollSymbol(vip)));
                if (tick <= 18) inv.setItem(14, renderSymbol(tick == 18 ? f3 : rollSymbol(vip)));

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1.0f + (tick * 0.04f));
                if (tick == 8 || tick == 13 || tick == 18) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.3f);
                }
                if (tick >= 18) {
                    spinningPlayers.remove(player.getUniqueId());
                    cancel();
                    resolveSlotOutcome(player, inv, betChips, f1, f2, f3);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void resolveSlotOutcome(Player player, Inventory inv, int betChips, ReelSymbol s1, ReelSymbol s2, ReelSymbol s3) {
        if (s1.key.equals(s2.key) && s2.key.equals(s3.key)) {
            if (s1.key.equals("SEVEN")) {
                double jackpotBonus = claimAndResetJackpot();
                int chipWin = (int) Math.round(betChips * s1.mult3);
                giveChips(player, chipWin);
                churu.deposit(player, jackpotBonus);
                player.getInventory().addItem(items.jackpotCrown());

                Bukkit.broadcast(TextUtil.mm(""));
                Bukkit.broadcast(TextUtil.mm("<gold>====================================================="));
                Bukkit.broadcast(TextUtil.mm("  🎰 <gradient:#ffe14d:#ff2457><bold>777 황금 냥냥 대잭팟 당첨!!</bold></gradient> 🎰"));
                Bukkit.broadcast(TextUtil.mm("  <white><bold>" + player.getName() + "</bold></white>님이 <gold>" + TextUtil.fmt(chipWin) + "칩</gold> + 누적 잭팟 " + TextUtil.churu(jackpotBonus) + " 독식!!"));
                Bukkit.broadcast(TextUtil.mm("<gold>====================================================="));
                Bukkit.broadcast(TextUtil.mm(""));

                Title t = Title.title(TextUtil.mm("<gold><bold>★ 777 JACKPOT!! ★</bold></gold>"),
                        TextUtil.mm("<yellow>+" + TextUtil.fmt(chipWin + jackpotBonus) + " 대박 당첨!</yellow>"),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofSeconds(1)));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(t);
                    p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
                }
            } else {
                int win = (int) Math.round(betChips * s1.mult3);
                giveChips(player, win);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
                player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.2, 0), 25, 0.5, 0.5, 0.5, 0.2);
                player.sendMessage(TextUtil.msg("🎰 <gold><bold>트리플 매치 당첨!!</bold></gold> (" + s1.label + "<gold>) → <bold>+" + TextUtil.fmt(win) + " 칩</bold> 획득!"));
                if (win >= 10000) {
                    Bukkit.broadcast(TextUtil.msg("🎰 카지노 홀에서 <white>" + player.getName() + "</white>님이 트리플 당첨으로 <gold>" + TextUtil.fmt(win) + " 칩</gold>을 쓸어 담았습니다!"));
                }
            }
        } else if (s1.key.equals(s2.key) || s2.key.equals(s3.key) || s1.key.equals(s3.key)) {
            int win = (int) Math.round(betChips * 1.2);
            giveChips(player, win);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.3f);
            player.sendMessage(TextUtil.msg("🎰 2개 심볼 일치! <green>+" + TextUtil.fmt(win) + " 칩</green> (1.2배 환급)"));
        } else {
            addToJackpot(betChips);
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 0.7f, 0.85f);
            player.sendMessage(TextUtil.msg("<gray>아쉽게 빗나갔습니다... (일부 금액이 서버 누적 잭팟에 적립됨: " + TextUtil.fmt(getJackpot()) + ")"));
        }
        ItemStack frame = icon(Material.GOLD_BLOCK, "<gold>▼ 행운의 당첨 라인 (보유: " + TextUtil.fmt(countPlayerChips(player)) + "칩) ▼");
        inv.setItem(3, frame); inv.setItem(4, frame); inv.setItem(5, frame);
    }

    // ==================== 2. 21 블랙잭 테이블 ====================

    public void openBlackjackSetup(Player player, boolean vip) {
        BlackjackSession existing = blackjackSessions.get(player.getUniqueId());
        if (existing != null && !existing.finished) {
            renderBlackjackTable(player, existing);
            return;
        }

        Inventory inv = Bukkit.createInventory(new CasinoHolder(GuiType.BLACKJACK), 45,
                TextUtil.mm((vip ? "<gold><bold>🃏 2F VIP 블랙잭 테이블" : "<aqua><bold>🃏 1F 블랙잭 테이블") + "</bold>"));
        ItemStack felt = icon(Material.GREEN_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, felt);

        inv.setItem(13, items.slotIcon("casino_chip", "<gold><bold>턱시도 딜러냥과 21 카드 승부!</bold>"));
        ItemMeta m = inv.getItem(13).getItemMeta();
        m.lore(TextUtil.lore(
                "<white>내 보유 칩: <aqua>" + TextUtil.fmt(countPlayerChips(player)) + " 칩</aqua>",
                "<gray>일반 승리 2.0배 │ 첫 패 블랙잭(21) 2.5배!",
                "", "<yellow>아래 칩을 클릭해 패를 받으세요!"
        ));
        inv.getItem(13).setItemMeta(m);

        if (!vip) {
            inv.setItem(29, makeBjBet(200));
            inv.setItem(31, makeBjBet(500));
            inv.setItem(33, makeBjBet(1000));
        } else {
            inv.setItem(29, makeBjBet(2000));
            inv.setItem(31, makeBjBet(5000));
            inv.setItem(33, makeBjBet(10000));
        }
        player.openInventory(inv);
    }

    private ItemStack makeBjBet(int chips) {
        ItemStack c = chips >= 2000 ? items.chip10000(1) : (chips >= 1000 ? items.chip1000(1) : items.chip100(1));
        ItemMeta m = c.getItemMeta();
        m.displayName(TextUtil.mm("<gold><bold>▶ " + TextUtil.fmt(chips) + " 칩 베팅 & 딜 시작</bold></gold>"));
        c.setItemMeta(m);
        return c;
    }

    private void startBlackjackHand(Player player, int betChips, boolean vip) {
        if (!spendChips(player, betChips)) {
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
            player.sendMessage(TextUtil.msg("<red>보유 카지노 칩이 부족합니다! 입구 환전소를 이용해 주세요."));
            return;
        }
        BlackjackSession s = new BlackjackSession();
        s.betChips = betChips;
        s.vip = vip;
        String[] suits = {"♠", "♥", "♦", "♣"};
        String[] ranks = {"A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K"};
        int[] vals =     {11,  2,   3,   4,   5,   6,   7,   8,   9,   10,   10,  10,  10};
        for (String su : suits) {
            for (int i = 0; i < ranks.length; i++) s.deck.add(new Card(ranks[i], su, vals[i]));
        }
        Collections.shuffle(s.deck, ThreadLocalRandom.current());

        s.playerHand.add(s.deck.remove(0));
        s.dealerHand.add(s.deck.remove(0));
        s.playerHand.add(s.deck.remove(0));
        s.dealerHand.add(s.deck.remove(0));
        blackjackSessions.put(player.getUniqueId(), s);

        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.3f);
        if (handTotal(s.playerHand) == 21) {
            finishBlackjack(player, s);
        } else {
            renderBlackjackTable(player, s);
        }
    }

    private int handTotal(List<Card> hand) {
        int sum = 0, aces = 0;
        for (Card c : hand) {
            sum += c.value;
            if ("A".equals(c.rank)) aces++;
        }
        while (sum > 21 && aces > 0) {
            sum -= 10;
            aces--;
        }
        return sum;
    }

    private void renderBlackjackTable(Player player, BlackjackSession s) {
        Inventory inv = Bukkit.createInventory(new CasinoHolder(GuiType.BLACKJACK), 45,
                TextUtil.mm("<aqua><bold>🃏 블랙잭 진행중 (베팅: " + TextUtil.fmt(s.betChips) + "칩)</bold></aqua>"));
        ItemStack felt = icon(Material.GREEN_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, felt);

        // Dealer cards (row 1: 2..6)
        for (int i = 0; i < s.dealerHand.size() && i < 6; i++) {
            if (!s.finished && i == 1) {
                inv.setItem(2 + i, icon(Material.BOOK, "<dark_purple><bold>🂠 딜러 히든 카드</bold></dark_purple>"));
            } else {
                Card c = s.dealerHand.get(i);
                inv.setItem(2 + i, icon(Material.PAPER, "<red><bold>딜러 카드: " + c.display() + "</bold></red>"));
            }
        }
        inv.setItem(0, icon(Material.BLACK_WOOL, "<gold><bold>턱시도 딜러냥</bold></gold>",
                s.finished ? "<white>합계: <yellow>" + handTotal(s.dealerHand) : "<gray>오픈 카드: " + s.dealerHand.get(0).display()));

        // Player cards (row 3: 20..25)
        for (int i = 0; i < s.playerHand.size() && i < 6; i++) {
            Card c = s.playerHand.get(i);
            inv.setItem(20 + i, icon(Material.MAP, "<aqua><bold>내 카드: " + c.display() + "</bold></aqua>"));
        }
        inv.setItem(18, icon(Material.LIME_WOOL, "<green><bold>" + player.getName() + "님의 패</bold></green>",
                "<white>현재 합계: <gold><bold>" + handTotal(s.playerHand) + "</bold></gold>"));

        if (!s.finished) {
            inv.setItem(38, icon(Material.EMERALD_BLOCK, "<green><bold>▶ [HIT] 카드 한 장 더 받기</bold></green>"));
            inv.setItem(40, icon(Material.GOLD_BLOCK, "<yellow><bold>✋ [STAND] 현재 패로 승부하기</bold></yellow>"));
            if (s.playerHand.size() == 2) {
                inv.setItem(42, icon(Material.DIAMOND_BLOCK, "<light_purple><bold>⚡ [DOUBLE DOWN] 베팅 2배 & 딱 1장 받기!</bold></light_purple>"));
            }
        } else {
            inv.setItem(40, icon(Material.SUNFLOWER, "<gold><bold>▶ 새 라운드 시작하기</bold></gold>"));
        }
        player.openInventory(inv);
    }

    private void finishBlackjack(Player player, BlackjackSession s) {
        s.finished = true;
        blackjackSessions.remove(player.getUniqueId());
        int pScore = handTotal(s.playerHand);

        if (pScore > 21) {
            addToJackpot(s.betChips);
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_HISS, 0.8f, 1.0f);
            player.sendMessage(TextUtil.msg("💥 <red><bold>버스트(Bust, " + pScore + ")!</bold></red> 21을 초과하여 칩을 잃었습니다."));
            renderBlackjackTable(player, s);
            return;
        }

        while (handTotal(s.dealerHand) < 17) {
            s.dealerHand.add(s.deck.remove(0));
        }
        int dScore = handTotal(s.dealerHand);
        boolean naturalBj = (pScore == 21 && s.playerHand.size() == 2);

        if (naturalBj && !(dScore == 21 && s.dealerHand.size() == 2)) {
            int win = (int) Math.round(s.betChips * 2.5);
            giveChips(player, win);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.3f);
            player.sendMessage(TextUtil.msg("🃏 <gold><bold>BLACKJACK (21)!!</bold></gold> 2.5배 배당 <aqua>+" + TextUtil.fmt(win) + " 칩</aqua> 획득!"));
        } else if (dScore > 21 || pScore > dScore) {
            int win = s.betChips * 2;
            giveChips(player, win);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.3f);
            player.sendMessage(TextUtil.msg("🃏 <green><bold>블랙잭 승리!</bold></green> (나 " + pScore + " vs 딜러 " + dScore + ") → <aqua>+" + TextUtil.fmt(win) + " 칩</aqua>!"));
        } else if (pScore == dScore) {
            giveChips(player, s.betChips);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.1f);
            player.sendMessage(TextUtil.msg("🃏 <yellow>푸시(무승부, " + pScore + ")!</yellow> 베팅한 칩이 그대로 반환되었습니다."));
        } else {
            addToJackpot(s.betChips);
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 0.8f, 0.85f);
            player.sendMessage(TextUtil.msg("🃏 <red>딜러 승리</red> (나 " + pScore + " vs 딜러 " + dScore + ")... 다음 판을 노려보세요!"));
        }
        renderBlackjackTable(player, s);
    }

    // ==================== 3. 사르르 캣닢 배당 룰렛 ====================

    public void openRouletteGui(Player player) {
        Inventory inv = Bukkit.createInventory(new CasinoHolder(GuiType.ROULETTE), 36,
                TextUtil.mm("<green><bold>🎡 사르르 캣닢 룰렛 휠</bold></green>"));
        ItemStack bg = icon(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 36; i++) inv.setItem(i, bg);

        inv.setItem(4, icon(Material.COMPASS,
                "<gold><bold>🎡 운명의 캣닢 룰렛 (보유: " + TextUtil.fmt(countPlayerChips(player)) + "칩)</bold></gold>",
                "<gray>좌클릭: <yellow>500 칩</yellow> 베팅 │ 쉬프트+클릭: <gold>2,000 칩</gold> 베팅!"));

        inv.setItem(20, icon(Material.RED_CONCRETE, "<red><bold>🔴 코랄 레드 (2.0배)</bold></red>",
                "<gray>확률: 46.7% (7 / 15칸)", "", "<yellow>▶ 좌클릭: 500칩 │ 쉬프트: 2,000칩"));
        inv.setItem(22, items.slotIcon("slot_yuumi", "<gradient:#ffe14d:#5cdbd3><bold>🟢 ★ 황금 유미 칸 (14.0배!)</bold></gradient>"));
        ItemMeta ym = inv.getItem(22).getItemMeta();
        ym.lore(TextUtil.lore("<gray>확률: 6.6% (1 / 15칸)", "", "<yellow>▶ 좌클릭: 500칩 │ 쉬프트: 2,000칩"));
        inv.getItem(22).setItemMeta(ym);
        inv.setItem(24, icon(Material.BLACK_CONCRETE, "<dark_gray><bold>⚫ 미드나잇 블랙 (2.0배)</bold></dark_gray>",
                "<gray>확률: 46.7% (7 / 15칸)", "", "<yellow>▶ 좌클릭: 500칩 │ 쉬프트: 2,000칩"));

        player.openInventory(inv);
    }

    private void spinRoulette(Player player, String pickColor, int betChips) {
        if (!spendChips(player, betChips)) {
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
            player.sendMessage(TextUtil.msg("<red>보유 카지노 칩이 부족합니다! 입구 환전소를 이용해 주세요."));
            return;
        }
        rouletteAnimation.run();
        int pocket = ThreadLocalRandom.current().nextInt(15); // 0 = YUUMI, 1..7 = RED, 8..14 = BLACK
        String result = pocket == 0 ? "YUUMI" : (pocket <= 7 ? "RED" : "BLACK");
        String korResult = switch (result) {
            case "YUUMI" -> "<gold><bold>★ 황금 유미 칸 ★</bold></gold>";
            case "RED" -> "<red><bold>🔴 코랄 레드</bold></red>";
            default -> "<dark_gray><bold>⚫ 미드나잇 블랙</bold></dark_gray>";
        };

        if (result.equals(pickColor)) {
            int mult = "YUUMI".equals(result) ? 14 : 2;
            int win = betChips * mult;
            giveChips(player, win);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.3f);
            player.sendMessage(TextUtil.msg("🎡 룰렛 결과: " + korResult + " 적중!! <aqua><bold>+" + TextUtil.fmt(win) + " 칩</bold></aqua> (" + mult + "배)!"));
            if (mult == 14) {
                Bukkit.broadcast(TextUtil.msg("🎡 카지노 룰렛에서 <white><bold>" + player.getName() + "</bold></white>님이 <gold><bold>[14배 황금 유미 칸]</bold></gold>을 적중시켜 " + TextUtil.fmt(win) + " 칩을 터뜨렸습니다!!"));
            }
        } else {
            addToJackpot(betChips);
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 0.8f, 0.85f);
            player.sendMessage(TextUtil.msg("🎡 룰렛 결과: " + korResult + " <gray>... 아쉽게 빗나갔습니다!"));
        }
        openRouletteGui(player);
    }

    // ==================== 4. 파산 길냥이 무료 급식소 ====================

    public void claimWelfareSoup(Player player) {
        double totalWealth = churu.getBalance(player) + churu.getBankBalance(player) + countPlayerChips(player);
        double threshold = plugin.getConfig().getDouble("casino.welfare-threshold", 100.0);
        if (totalWealth >= threshold) {
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_HISS, 0.8f, 1.1f);
            player.sendMessage(TextUtil.msg("<red>지갑·은행 예금·카지노 칩 합산 자산이 " + TextUtil.fmt(threshold) + " 이상인 부자 집사는 무료 급식소를 이용할 수 없습니다! 😾"));
            return;
        }
        String today = LocalDate.now().toString();
        String key = "welfare." + today + "." + player.getUniqueId();
        if (data.getBoolean(key, false)) {
            player.sendMessage(TextUtil.msg("<yellow>오늘의 길냥이 무료 급식은 이미 배급받으셨습니다! 내일 다시 오세요..."));
            return;
        }
        data.set(key, true);
        saveCallback.run();
        double grant = plugin.getConfig().getDouble("casino.welfare-grant", 300.0);
        churu.deposit(player, grant);
        player.getInventory().addItem(new ItemStack(Material.COOKED_COD, 3));
        player.playSound(player.getLocation(), Sound.ENTITY_CAT_BEG_FOR_FOOD, 1.0f, 1.0f);
        Bukkit.broadcast(TextUtil.msg("🥣 <yellow>[긴급 속보]</yellow> 카지노 뒷골목 무료 급식소에서 알거지가 된 <white><bold>" + player.getName() + "</bold></white>님이 눈물의 구운 대구와 생계비 " + TextUtil.churu(grant) + "를 배급받았습니다 ㅠㅠ"));
    }

    // ==================== GUI 클릭 라우팅 ====================

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof CasinoHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int raw = event.getRawSlot();
        if (raw < 0) return;

        switch (holder.type) {
            case CASHIER -> {
                int mult = event.isShiftClick() ? 10 : 1;
                if (raw == 19) buyChip(player, 100, mult);
                else if (raw == 21) buyChip(player, 1000, mult);
                else if (raw == 23) buyChip(player, 10000, event.isShiftClick() ? 5 : 1);
                else if (raw == 25) {
                    int allChips = countPlayerChips(player);
                    if (allChips <= 0) {
                        player.sendMessage(TextUtil.msg("<yellow>환전할 카지노 칩이 인벤토리에 없습니다!"));
                        return;
                    }
                    spendChips(player, allChips);
                    churu.deposit(player, allChips);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.3f);
                    player.sendMessage(TextUtil.msg("💰 보유 칩 전액을 정산하여 지갑에 " + TextUtil.churu(allChips) + "<gray>가 입금되었습니다!"));
                    openCashierGui(player);
                }
            }
            case SLOTS_STD -> {
                if (raw == 29) startSlotSpin(player, event.getInventory(), 100, false);
                else if (raw == 30) startSlotSpin(player, event.getInventory(), 300, false);
                else if (raw == 32) startSlotSpin(player, event.getInventory(), 500, false);
                else if (raw == 33) startSlotSpin(player, event.getInventory(), 1000, false);
            }
            case SLOTS_VIP -> {
                if (raw == 29) startSlotSpin(player, event.getInventory(), 2000, true);
                else if (raw == 31) startSlotSpin(player, event.getInventory(), 5000, true);
                else if (raw == 33) startSlotSpin(player, event.getInventory(), 10000, true);
            }
            case BLACKJACK -> {
                BlackjackSession s = blackjackSessions.get(player.getUniqueId());
                if (s == null || s.finished) {
                    boolean isVip = player.getLocation().getBlockY() >= 70;
                    if (!isVip) {
                        if (raw == 29) startBlackjackHand(player, 200, false);
                        else if (raw == 31) startBlackjackHand(player, 500, false);
                        else if (raw == 33) startBlackjackHand(player, 1000, false);
                    } else {
                        if (raw == 29) startBlackjackHand(player, 2000, true);
                        else if (raw == 31) startBlackjackHand(player, 5000, true);
                        else if (raw == 33) startBlackjackHand(player, 10000, true);
                    }
                    if (raw == 40) openBlackjackSetup(player, isVip);
                } else {
                    if (raw == 38) {
                        s.playerHand.add(s.deck.remove(0));
                        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.3f);
                        if (handTotal(s.playerHand) >= 21) finishBlackjack(player, s);
                        else renderBlackjackTable(player, s);
                    } else if (raw == 40) {
                        finishBlackjack(player, s);
                    } else if (raw == 42 && s.playerHand.size() == 2) {
                        if (!spendChips(player, s.betChips)) {
                            player.sendMessage(TextUtil.msg("<red>더블다운을 위한 추가 카지노 칩(" + TextUtil.fmt(s.betChips) + "칩)이 부족합니다!"));
                            return;
                        }
                        s.betChips *= 2;
                        s.playerHand.add(s.deck.remove(0));
                        finishBlackjack(player, s);
                    }
                }
            }
            case ROULETTE -> {
                int bet = event.isShiftClick() ? 2000 : 500;
                if (raw == 20) spinRoulette(player, "RED", bet);
                else if (raw == 22) spinRoulette(player, "YUUMI", bet);
                else if (raw == 24) spinRoulette(player, "BLACK", bet);
            }
        }
    }

    private void buyChip(Player player, int unit, int qty) {
        double cost = (double) unit * qty;
        if (!churu.withdraw(player, cost)) {
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
            player.sendMessage(TextUtil.msg("<red>지갑 츄르가 부족합니다! (필요: " + TextUtil.fmt(cost) + " 츄르)"));
            return;
        }
        giveChips(player, unit * qty);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.35f);
        player.sendMessage(TextUtil.msg("💱 카지노 실물 칩 <aqua><bold>" + TextUtil.fmt((long) unit * qty) + " 칩</bold></aqua> 환전 완료!"));
        openCashierGui(player);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        // Active blackjack sessions remain resumable at the table!
    }

    // ==================== 명령어 처리 (현장 전용 안내 & 카지노 홀 내 1:1 주사위) ====================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (command.getName().equalsIgnoreCase("casino")) {
            World hub = hubWorldSupplier.get();
            if (hub != null && player.getWorld().equals(hub)) {
                player.setCompassTarget(new Location(hub, 63.5, 65.0, -8.5));
            }
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.3f);
            player.sendMessage(TextUtil.msg("🎰 <gold><bold>[황금 냥냥 카지노]</bold></gold>는 허브 도시 <yellow>동쪽 2층 랜드마크 건물(X=+63, Z=-8)</yellow>에 직접 방문하여 입구 환전소와 슬롯·블랙잭·경묘 트랙을 이용하실 수 있습니다!"));
            return true;
        }

        // /도박신청 & /도박수락 (Must be inside Casino Hall!)
        if (!isInsideCasinoHall(player.getLocation())) {
            player.sendMessage(TextUtil.msg("<red>🎲 1:1 주사위 데스매치는 허브 동쪽 <yellow>[황금 냥냥 카지노 홀 내부]</yellow>에서만 진행할 수 있습니다!"));
            return true;
        }

        if (label.equalsIgnoreCase("도박수락") || (args.length >= 1 && args[0].equalsIgnoreCase("수락"))) {
            DuelChallenge dc = pendingDuelsByTarget.remove(player.getUniqueId());
            if (dc == null || dc.expiresAt < System.currentTimeMillis()) {
                player.sendMessage(TextUtil.msg("<red>수락할 유효한 주사위 대결 신청이 없습니다!"));
                return true;
            }
            Player ch = Bukkit.getPlayer(dc.challenger);
            if (ch == null || !isInsideCasinoHall(ch.getLocation())) {
                player.sendMessage(TextUtil.msg("<red>상대방이 카지노 홀을 벗어났습니다."));
                return true;
            }
            if (!spendChips(ch, dc.chips)) {
                player.sendMessage(TextUtil.msg("<red>신청자의 보유 칩이 부족해 대결이 취소되었습니다."));
                return true;
            }
            if (!spendChips(player, dc.chips)) {
                giveChips(ch, dc.chips);
                player.sendMessage(TextUtil.msg("<red>내 보유 카지노 칩(" + TextUtil.fmt(dc.chips) + "칩)이 부족합니다!"));
                return true;
            }

            int r1 = ThreadLocalRandom.current().nextInt(1, 101);
            int r2 = ThreadLocalRandom.current().nextInt(1, 101);
            while (r1 == r2) r2 = ThreadLocalRandom.current().nextInt(1, 101);

            Player winner = r1 > r2 ? ch : player;
            int pot = dc.chips * 2;
            giveChips(winner, pot);

            for (Player spec : player.getWorld().getPlayers()) {
                if (isInsideCasinoHall(spec.getLocation())) {
                    spec.playSound(spec.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.9f, 1.3f);
                    spec.sendMessage(TextUtil.msg("🎲 <gold>[카지노 주사위 데스매치]</gold> <white>" + ch.getName() + "(" + r1 + ")</white> vs <white>"
                            + player.getName() + "(" + r2 + ")</white> → <aqua><bold>" + winner.getName() + "</bold></aqua>님이 <gold>" + TextUtil.fmt(pot) + " 칩</gold> 독식!!"));
                }
            }
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(TextUtil.msg("사용법 (카지노 홀 내부): <yellow>/도박신청 <닉네임> <베팅칩수량></yellow>"));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(player) || !isInsideCasinoHall(target.getLocation())) {
            player.sendMessage(TextUtil.msg("<red>카지노 홀 내부에 함께 있는 지인의 닉네임을 입력해주세요!"));
            return true;
        }
        int chips;
        try {
            chips = (Integer.parseInt(args[1]) / 100) * 100;
        } catch (NumberFormatException e) {
            player.sendMessage(TextUtil.msg("<red>100칩 단위의 숫자를 입력해주세요."));
            return true;
        }
        if (chips < 100 || countPlayerChips(player) < chips) {
            player.sendMessage(TextUtil.msg("<red>최소 100칩 이상, 본인이 보유한 카지노 칩 이내로 신청해주세요!"));
            return true;
        }
        pendingDuelsByTarget.put(target.getUniqueId(), new DuelChallenge(player.getUniqueId(), target.getUniqueId(), chips, System.currentTimeMillis() + 30_000L));
        player.sendMessage(TextUtil.msg("🎲 <white>" + target.getName() + "</white>님에게 <gold>" + TextUtil.fmt(chips) + " 칩</gold> 주사위 승부를 신청했습니다!"));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.4f);
        target.sendMessage(TextUtil.msg("🎲 <yellow><bold>" + player.getName() + "</bold></yellow>님이 <gold>" + TextUtil.fmt(chips) + " 칩</gold> 주사위 데스매치를 신청했습니다! 수락하려면 <aqua>/도박수락</aqua> 입력!"));
        return true;
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
