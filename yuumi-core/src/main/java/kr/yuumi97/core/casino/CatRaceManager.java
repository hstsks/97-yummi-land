package kr.yuumi97.core.casino;

import kr.yuumi97.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 🏇 황금 냥냥 카지노 1층 중앙 아트리움 『실시간 고양이 달리기 경주 (경묘 / Live Cat Derby)』
 * - 지인들이 트랙 난간(1층 & 2층 VIP 발코니)에 모여 카지노 칩으로 베팅하고,
 *   실제 5마리 고양이 엔티티가 돌발 예능 이벤트(캣닢 부스터, 식빵 굽기, 막판 역전)와 함께 질주합니다!
 */
public final class CatRaceManager implements Listener {

    public enum RaceState { IDLE, BETTING_OPEN, RACING, CELEBRATING }

    public record RacerSpec(
            int laneIndex, // 0..4
            String name,
            Cat.Type catType,
            Material iconMat,
            double odds,
            int laneZ,
            String desc
    ) {}

    public static final class RaceBetHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record BetEntry(int laneIndex, int chips) {}

    public static final double START_X = 54.5;
    public static final double FINISH_X = 71.5;
    public static final double TRACK_Y = 65.0;

    private static final List<RacerSpec> RACERS = List.of(
            new RacerSpec(0, "<gold>[1번] 식빵왕 치즈냥</gold>", Cat.Type.RED, Material.ORANGE_WOOL, 2.2, -26, "꾸준한 보폭으로 달리는 믿음직한 정배 에이스!"),
            new RacerSpec(1, "<white>[2번] 신사 턱시도냥</white>", Cat.Type.BLACK, Material.WHITE_WOOL, 3.0, -24, "초중반 밸런스가 뛰어난 안정형 스프린터!"),
            new RacerSpec(2, "<aqua>[3번] 질풍 샴냥</aqua>", Cat.Type.SIAMESE, Material.LIGHT_BLUE_WOOL, 4.2, -22, "캣닢 냄새를 맡으면 폭발 가속하는 승부사!"),
            new RacerSpec(3, "<#ff85c0>[4번] 귀족 페르시안</#ff85c0>", Cat.Type.PERSIAN, Material.PINK_WOOL, 6.5, -20, "도도하게 걷다가 중반 이후 스퍼트하는 역배마!"),
            new RacerSpec(4, "<light_purple>[5번] 초각성 복권냥</light_purple>", Cat.Type.ALL_BLACK, Material.PURPLE_WOOL, 12.0, -18, "평소엔 식빵만 굽지만 가끔 12배 광속 역전을 터뜨립니다!")
    );

    private final JavaPlugin plugin;
    private final CasinoManager casino;
    private final Supplier<World> hubWorldSupplier;
    private final NamespacedKey raceCatKey;

    private RaceState state = RaceState.IDLE;
    private int countdownSec = 0;
    private int idleAutoTimerSec = 0;
    private final Map<UUID, BetEntry> currentBets = new HashMap<>();
    private final List<Cat> activeCats = new ArrayList<>();
    private final double[] progressX = new double[5];

    public CatRaceManager(JavaPlugin plugin, CasinoManager casino, Supplier<World> hubWorldSupplier) {
        this.plugin = plugin;
        this.casino = casino;
        this.hubWorldSupplier = hubWorldSupplier;
        this.raceCatKey = new NamespacedKey(plugin, "derby_racer_cat");
    }

    public void startScheduler() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            World hub = hubWorldSupplier.get();
            if (hub == null) return;
            List<Player> spectators = getCasinoPlayers(hub);

            if (state == RaceState.IDLE) {
                if (!spectators.isEmpty()) {
                    idleAutoTimerSec++;
                    // Auto-open race every 150 seconds when players are hanging out in the casino!
                    if (idleAutoTimerSec >= 150) {
                        idleAutoTimerSec = 0;
                        openBettingWindow(hub, false);
                    }
                } else {
                    idleAutoTimerSec = 0;
                }
            } else if (state == RaceState.BETTING_OPEN) {
                countdownSec--;
                if (countdownSec == 15 || countdownSec == 5) {
                    broadcastToCasino(hub, "🏇 <gold><bold>[경묘 안내]</bold></gold> 제97회 실시간 고양이 경주 출발 <yellow>" + countdownSec + "초 전</yellow>! 트랙 앞 캐스터냥에게 베팅하세요!");
                    playSoundCasino(hub, Sound.BLOCK_NOTE_BLOCK_PLING, 1.3f);
                }
                if (countdownSec <= 0) {
                    launchLiveRace(hub);
                }
            }
        }, 40L, 20L);
    }

    public void cleanup() {
        for (Cat c : activeCats) {
            if (c != null && c.isValid()) c.remove();
        }
        activeCats.clear();
        World hub = hubWorldSupplier.get();
        if (hub != null) {
            for (Entity e : hub.getEntitiesByClass(Cat.class)) {
                if (e.getPersistentDataContainer().has(raceCatKey, PersistentDataType.BYTE)) {
                    e.remove();
                }
            }
        }
    }

    private void openBettingWindow(World hub, boolean triggeredByBet) {
        if (state != RaceState.IDLE) return;
        state = RaceState.BETTING_OPEN;
        countdownSec = 25;
        spawnLineupAtGate(hub);
        broadcastToCasino(hub, "🏇 <gradient:#ffe14d:#ff85c0><bold>[실시간 고양이 경주 오픈!]</bold></gradient> <white>25초 뒤 1층 중앙 트랙에서 경주가 시작됩니다! 지금 베팅하세요!");
        playSoundCasino(hub, Sound.ENTITY_CAT_PURREOW, 1.2f);
    }

    private void spawnLineupAtGate(World hub) {
        cleanup();
        for (RacerSpec spec : RACERS) {
            progressX[spec.laneIndex] = START_X;
            Location startLoc = new Location(hub, START_X, TRACK_Y, spec.laneZ + 0.5, -90f, 0f);
            Cat cat = (Cat) hub.spawnEntity(startLoc, EntityType.CAT);
            cat.setCatType(spec.catType);
            cat.setAdult();
            cat.setAI(false);
            cat.setInvulnerable(true);
            cat.setCollidable(false);
            cat.setSilent(false);
            cat.setSitting(false);
            cat.customName(TextUtil.mm(spec.name));
            cat.setCustomNameVisible(true);
            cat.getPersistentDataContainer().set(raceCatKey, PersistentDataType.BYTE, (byte) 1);
            activeCats.add(cat);
        }
    }

    private void launchLiveRace(World hub) {
        state = RaceState.RACING;
        if (activeCats.size() != 5) spawnLineupAtGate(hub);

        broadcastToCasino(hub, "🔫 <gold><bold>탕!! 경주묘 5마리가 일제히 게이트를 박차고 출발합니다!!</bold></gold>");
        playSoundCasino(hub, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.2f);

        new BukkitRunnable() {
            int step = 0;
            @Override
            public void run() {
                step++;
                if (activeCats.size() < 5) {
                    cancel();
                    state = RaceState.IDLE;
                    return;
                }

                int leaderIdx = 0;
                int winnerIdx = -1;

                // Base speeds tuned approximately inversely to odds, plus hilarious random events!
                double[] baseStep = {0.26, 0.245, 0.23, 0.21, 0.175};

                // Random comedy event every 10 steps (~2 seconds)
                int eventLane = (step % 10 == 3) ? ThreadLocalRandom.current().nextInt(5) : -1;
                int eventKind = ThreadLocalRandom.current().nextInt(3); // 0=catnip turbo, 1=loafing pause, 2=zoomies!

                for (int i = 0; i < 5; i++) {
                    double delta = baseStep[i] * (0.65 + ThreadLocalRandom.current().nextDouble() * 0.78);

                    if (i == eventLane) {
                        RacerSpec rs = RACERS.get(i);
                        if (i == 4 && ThreadLocalRandom.current().nextDouble() < 0.42) {
                            // Lane 5 Miracle Turbo!
                            delta += 1.25;
                            broadcastToCasino(hub, "🚀 " + rs.name + "<white>이(가) 갑자기 눈빛이 변하며 <light_purple><bold>초음속 슈우우웅 부스터</bold></light_purple>를 켭니다!!");
                            hub.spawnParticle(Particle.SOUL_FIRE_FLAME, activeCats.get(i).getLocation().add(0, 0.4, 0), 16, 0.2, 0.2, 0.2, 0.05);
                        } else if (eventKind == 0) {
                            delta += 0.65;
                            broadcastToCasino(hub, "💨 " + rs.name + "<white>이(가) 캣닢 향기를 맡고 맹렬하게 치고 나갑니다!!");
                            hub.spawnParticle(Particle.HAPPY_VILLAGER, activeCats.get(i).getLocation().add(0, 0.5, 0), 12, 0.25, 0.25, 0.25, 0.05);
                        } else if (eventKind == 1) {
                            delta = 0.02;
                            broadcastToCasino(hub, "🙀 " + rs.name + "<gray>이(가) 트랙 한복판에서 갑자기 그루밍을 하며 식빵을 굽습니다!!");
                            hub.playSound(activeCats.get(i).getLocation(), Sound.ENTITY_CAT_PURR, 1f, 1.0f);
                        } else {
                            delta += 0.48;
                        }
                    }

                    progressX[i] = Math.min(FINISH_X, progressX[i] + delta);
                    Cat cat = activeCats.get(i);
                    if (cat != null && cat.isValid()) {
                        Location nextLoc = new Location(hub, progressX[i], TRACK_Y, RACERS.get(i).laneZ + 0.5, -90f, 0f);
                        cat.teleport(nextLoc);
                        hub.spawnParticle(Particle.CLOUD, nextLoc.clone().add(-0.3, 0.1, 0), 1, 0.05, 0.02, 0.05, 0.01);
                    }

                    if (progressX[i] > progressX[leaderIdx]) leaderIdx = i;
                    if (progressX[i] >= FINISH_X && winnerIdx == -1) {
                        winnerIdx = i;
                    }
                }

                // Live ActionBar broadcast to spectators
                int pct = (int) Math.min(100, Math.round(((progressX[leaderIdx] - START_X) / (FINISH_X - START_X)) * 100));
                String barMsg = "🏇 <gold>경묘 생중계</gold> │ 선두: " + RACERS.get(leaderIdx).name + " <yellow>(" + pct + "%)</yellow>";
                for (Player p : getCasinoPlayers(hub)) {
                    p.sendActionBar(TextUtil.mm(barMsg));
                }

                if (winnerIdx != -1 || step > 110) {
                    if (winnerIdx == -1) winnerIdx = leaderIdx;
                    cancel();
                    finishRace(hub, winnerIdx);
                }
            }
        }.runTaskTimer(plugin, 10L, 4L);
    }

    private void finishRace(World hub, int winnerIdx) {
        state = RaceState.CELEBRATING;
        RacerSpec winner = RACERS.get(winnerIdx);

        // Firework on winning cat!
        Cat winCat = activeCats.get(winnerIdx);
        if (winCat != null && winCat.isValid()) {
            Firework fw = (Firework) hub.spawnEntity(winCat.getLocation().add(0, 1.2, 0), EntityType.FIREWORK_ROCKET);
            FireworkMeta fwm = fw.getFireworkMeta();
            fwm.setPower(0);
            fwm.addEffect(FireworkEffect.builder()
                    .with(FireworkEffect.Type.BALL_LARGE)
                    .withColor(Color.YELLOW, Color.FUCHSIA)
                    .withFade(Color.AQUA)
                    .withFlicker()
                    .build());
            fw.setFireworkMeta(fwm);
        }

        broadcastToCasino(hub, "");
        broadcastToCasino(hub, "🏆 <gradient:#ffe14d:#ff85c0><bold>결승선 통과!! 우승묘: " + winner.name + " (배당 x" + winner.odds + ")!!</bold></gradient>");

        // Settle all bets!
        for (Map.Entry<UUID, BetEntry> entry : currentBets.entrySet()) {
            Player bettor = Bukkit.getPlayer(entry.getKey());
            BetEntry bet = entry.getValue();
            if (bet.laneIndex == winnerIdx) {
                int payout = (int) Math.round(bet.chips * winner.odds);
                if (bettor != null && bettor.isOnline()) {
                    casino.giveChips(bettor, payout);
                    bettor.playSound(bettor.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.25f);
                    bettor.sendMessage(TextUtil.msg("🎉 <gold><bold>경묘 적중 대박!!</bold></gold> " + winner.name + " 우승으로 <aqua><bold>+" + TextUtil.fmt(payout) + " 칩</bold></aqua> 배당 지급!"));
                }
            } else {
                casino.addToJackpot(bet.chips);
                if (bettor != null && bettor.isOnline()) {
                    bettor.playSound(bettor.getLocation(), Sound.ENTITY_CAT_AMBIENT, 0.8f, 0.85f);
                    bettor.sendMessage(TextUtil.msg("<gray>응원하던 고양이가 아쉽게 우승을 놓쳤습니다... (잭팟 적립)"));
                }
            }
        }
        currentBets.clear();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            cleanup();
            state = RaceState.IDLE;
            idleAutoTimerSec = 0;
        }, 100L);
    }

    // ==================== 베팅 키오스크 GUI ====================

    public void openBettingGui(Player player) {
        if (state == RaceState.RACING || state == RaceState.CELEBRATING) {
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_PURREOW, 1f, 1.2f);
            player.sendMessage(TextUtil.msg("<yellow>🏇 현재 트랙에서 경주가 한창 진행 중입니다! 난간에서 응원해 주세요!!"));
            return;
        }

        Inventory inv = Bukkit.createInventory(new RaceBetHolder(), 36,
                TextUtil.mm("<gold><bold>🏇 실시간 고양이 경주 (경묘) 베팅창</bold></gold>"));
        ItemStack bg = icon(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 36; i++) inv.setItem(i, bg);

        BetEntry myBet = currentBets.get(player.getUniqueId());
        inv.setItem(4, icon(Material.GOLDEN_CARROT,
                "<gradient:#ffe14d:#ff85c0><bold>🏇 97랜드 중앙 경묘 베팅 데스크</bold></gradient>",
                "<white>내 보유 칩: <aqua>" + TextUtil.fmt(casino.countPlayerChips(player)) + " 칩</aqua>",
                myBet != null
                        ? "<green>✦ 내 현재 베팅: " + RACERS.get(myBet.laneIndex).name + " <gold>(" + TextUtil.fmt(myBet.chips) + "칩)</gold>"
                        : "<gray>✦ 아직 이번 회차에 베팅하지 않았습니다.",
                "",
                "<yellow>좌클릭: 500 칩 베팅 │ 쉬프트+좌클릭: 2,000 칩 베팅",
                "<aqua>우클릭: 5,000 칩 하이롤러 베팅!"));

        int[] slots = {19, 20, 21, 22, 23};
        for (int i = 0; i < RACERS.size(); i++) {
            RacerSpec r = RACERS.get(i);
            inv.setItem(slots[i], icon(r.iconMat,
                    r.name + " <yellow><bold>(x" + r.odds + "배)</bold></yellow>",
                    "<gray>" + r.desc,
                    "",
                    "<yellow>▶ 좌클릭: 500칩 │ 쉬프트: 2,000칩 │ 우클릭: 5,000칩"));
        }

        player.openInventory(inv);
    }

    @EventHandler
    public void onBetClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RaceBetHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (state == RaceState.RACING || state == RaceState.CELEBRATING) {
            player.closeInventory();
            player.sendMessage(TextUtil.msg("<red>이미 레이스가 출발하여 베팅이 마감되었습니다!"));
            return;
        }

        int[] slots = {19, 20, 21, 22, 23};
        int chosenLane = -1;
        for (int i = 0; i < slots.length; i++) {
            if (event.getRawSlot() == slots[i]) {
                chosenLane = i;
                break;
            }
        }
        if (chosenLane == -1) return;

        if (currentBets.containsKey(player.getUniqueId())) {
            player.sendMessage(TextUtil.msg("<yellow>이번 회차에는 이미 베팅을 완료하셨습니다! 출발을 기다려주세요."));
            return;
        }

        int betChips = event.isRightClick() ? 5000 : (event.isShiftClick() ? 2000 : 500);
        if (!casino.spendChips(player, betChips)) {
            player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
            player.sendMessage(TextUtil.msg("<red>보유 카지노 칩이 부족합니다! 입구 <yellow>[환전소]</yellow>에서 칩을 환전해 오세요."));
            return;
        }

        currentBets.put(player.getUniqueId(), new BetEntry(chosenLane, betChips));
        RacerSpec rs = RACERS.get(chosenLane);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.35f);

        World hub = hubWorldSupplier.get();
        if (hub != null) {
            if (state == RaceState.IDLE) {
                openBettingWindow(hub, true);
            }
            broadcastToCasino(hub, "🎫 <white>" + player.getName() + "</white>님이 " + rs.name + "<white>에게 <gold>" + TextUtil.fmt(betChips) + " 칩</gold>을 베팅했습니다!! (출발 " + countdownSec + "초 전)");
        }
        openBettingGui(player);
    }

    private List<Player> getCasinoPlayers(World hub) {
        List<Player> list = new ArrayList<>();
        for (Player p : hub.getPlayers()) {
            if (casino.isInsideCasinoHall(p.getLocation())) list.add(p);
        }
        return list;
    }

    private void broadcastToCasino(World hub, String mmText) {
        for (Player p : getCasinoPlayers(hub)) {
            p.sendMessage(mmText.isEmpty() ? TextUtil.mm("") : TextUtil.msg(mmText));
        }
    }

    private void playSoundCasino(World hub, Sound sound, float pitch) {
        for (Player p : getCasinoPlayers(hub)) {
            p.playSound(p.getLocation(), sound, 0.9f, pitch);
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
