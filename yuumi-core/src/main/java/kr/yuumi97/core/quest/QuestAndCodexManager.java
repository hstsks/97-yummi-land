package kr.yuumi97.core.quest;

import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public final class QuestAndCodexManager implements Listener, CommandExecutor {

    public enum BoardType { DAILY_QUEST, CODEX }

    public static final class BoardHolder implements InventoryHolder {
        public final BoardType type;
        public BoardHolder(BoardType type) { this.type = type; }
        @Override public Inventory getInventory() { return null; }
    }

    public record QuestSpec(String key, String title, Material mat, String customId, int amount, double churuReward, int pageReward) {}
    public record CodexEntry(String key, String name, Material mat, String customId, int amount, double rewardChuru) {}

    private final JavaPlugin plugin;
    private final YuumiItems items;
    private final ChuruManager churu;
    private final FileConfiguration data;
    private final Runnable saveCallback;

    private final List<QuestSpec> questPool = List.of(
            new QuestSpec("wheat_32", "고양이 빵집 밀 수확", Material.WHEAT, null, 32, 650, 1),
            new QuestSpec("carrot_32", "싱싱한 당근 조달", Material.CARROT, null, 32, 650, 1),
            new QuestSpec("potato_24", "노릇노릇 구운 감자 배달", Material.BAKED_POTATO, null, 24, 750, 1),
            new QuestSpec("salmon_16", "유미가 좋아하는 구운 연어", Material.COOKED_SALMON, null, 16, 850, 1),
            new QuestSpec("cod_16", "바삭바삭 구운 대구 납품", Material.COOKED_COD, null, 16, 800, 1),
            new QuestSpec("skewer_2", "특제 슈우우웅 꼬치구이 조리", Material.COOKED_COD, "zoomies_skewer", 2, 1100, 2),
            new QuestSpec("iron_20", "마을 대장간 철 주괴 납품", Material.IRON_INGOT, null, 20, 900, 1),
            new QuestSpec("gold_16", "카지노 주화용 금 주괴 조달", Material.GOLD_INGOT, null, 16, 950, 1),
            new QuestSpec("amethyst_12", "신비한 자수정 조각 채집", Material.AMETHYST_SHARD, null, 12, 850, 1),
            new QuestSpec("rotten_24", "야생 좀비 소탕 증명", Material.ROTTEN_FLESH, null, 24, 750, 1),
            new QuestSpec("bone_20", "스켈레톤 해골 정화", Material.BONE, null, 20, 800, 1),
            new QuestSpec("spider_16", "던전 거미줄 청소", Material.STRING, null, 16, 750, 1)
    );

    private final List<CodexEntry> codexEntries = List.of(
            // Row 1: Lifestyle & Farming/Fishing (7)
            new CodexEntry("wheat", "황금빛 밀 다발", Material.WHEAT, null, 64, 500),
            new CodexEntry("pumpkin_pie", "달콤한 호박 파이", Material.PUMPKIN_PIE, null, 8, 700),
            new CodexEntry("honey", "야생 벌꿀 병", Material.HONEY_BOTTLE, null, 8, 900),
            new CodexEntry("pufferfish", "위험한 복어 도감 등록", Material.PUFFERFISH, null, 4, 1000),
            new CodexEntry("tropical", "알록달록 열대어", Material.TROPICAL_FISH, null, 4, 1000),
            new CodexEntry("cake", "97랜드 축하 케이크", Material.CAKE, null, 1, 800),
            new CodexEntry("golden_apple", "황금 사과", Material.GOLDEN_APPLE, null, 2, 1500),
            // Row 2: Yuumi Custom Cuisine & Mining (7)
            new CodexEntry("zoomies", "슈우우웅~ 생선 꼬치구이", Material.COOKED_COD, "zoomies_skewer", 2, 1200),
            new CodexEntry("bento", "유미의 꾹꾹이 특선 도시락", Material.COOKED_SALMON, "kneading_bento", 2, 1500),
            new CodexEntry("elixir", "사르르 별빛 캣닢 영약", Material.HONEY_BOTTLE, "catnip_elixir", 2, 1500),
            new CodexEntry("feast_can", "97산 황실 참치 대뱃살 캔", Material.GOLDEN_CARROT, "royal_feast_can", 2, 1800),
            new CodexEntry("emerald", "고양이 눈빛 에메랄드", Material.EMERALD, null, 16, 1400),
            new CodexEntry("diamond", "찬란한 다이아몬드", Material.DIAMOND, null, 8, 2200),
            new CodexEntry("heart_sea", "바다의 심장", Material.HEART_OF_THE_SEA, null, 1, 3500),
            // Row 3: Dungeon, Nether, End & High Relics (7)
            new CodexEntry("ender_pearl", "차원의 엔더 진주", Material.ENDER_PEARL, null, 12, 1600),
            new CodexEntry("blaze_rod", "네더 성채 블레이즈 막대", Material.BLAZE_ROD, null, 12, 2200),
            new CodexEntry("ghast_tear", "가스트의 눈물", Material.GHAST_TEAR, null, 4, 2500),
            new CodexEntry("netherite", "고대 네더라이트 주괴", Material.NETHERITE_INGOT, null, 1, 5000),
            new CodexEntry("echo_shard", "깊은 어둠의 반향 조각", Material.ECHO_SHARD, null, 4, 4000),
            new CodexEntry("shulker_shell", "엔드 셜커 껍데기", Material.SHULKER_SHELL, null, 4, 4500),
            new CodexEntry("nether_star", "찬란한 네더의 별", Material.NETHER_STAR, null, 1, 10000)
    );

    public QuestAndCodexManager(JavaPlugin plugin, YuumiItems items, ChuruManager churu, FileConfiguration data, Runnable saveCallback) {
        this.plugin = plugin;
        this.items = items;
        this.churu = churu;
        this.data = data;
        this.saveCallback = saveCallback;
    }

    public List<QuestSpec> getTodaysQuests() {
        long daySeed = LocalDate.now().toEpochDay() * 9797L;
        List<QuestSpec> copy = new ArrayList<>(questPool);
        Collections.shuffle(copy, new Random(daySeed));
        return copy.subList(0, 4);
    }

    public void openDailyQuestGui(Player player) {
        String today = LocalDate.now().toString();
        String weekId = LocalDate.now().get(IsoFields.WEEK_BASED_YEAR) + "-W" + LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        Inventory inv = Bukkit.createInventory(new BoardHolder(BoardType.DAILY_QUEST), 45,
                TextUtil.mm("<#ff9c6e><bold>📋 마을 회관 일일 · 주간 의뢰 게시판</bold>"));

        ItemStack bg = makeIcon(Material.ORANGE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, bg);

        List<QuestSpec> todays = getTodaysQuests();
        int[] slots = {10, 12, 14, 16};
        int completedCount = 0;

        for (int i = 0; i < todays.size(); i++) {
            QuestSpec q = todays.get(i);
            boolean done = data.getBoolean("quests." + today + "." + player.getUniqueId() + "." + q.key, false);
            if (done) completedCount++;

            ItemStack display = q.customId != null ? buildCustomPreview(q.customId) : new ItemStack(q.mat);
            ItemMeta meta = display.getItemMeta();
            if (done) {
                display = makeIcon(Material.LIME_DYE, "<green><bold>✔ [일일 완료] " + q.title + "</bold>",
                        "<gray>오늘의 의뢰를 성공적으로 납품했습니다!");
            } else {
                meta.displayName(TextUtil.mm("<yellow><bold>📌 [일일] " + q.title + "</bold>"));
                meta.lore(TextUtil.lore(
                        "<gray>필요 물품 수량: <white>" + q.amount + "개",
                        "",
                        "<gold>✦ 보상: " + TextUtil.fmt(q.churuReward) + " 츄르 + <aqua>찢어진 책장 " + q.pageReward + "장</aqua>",
                        "",
                        "<yellow>▶ 인벤토리에 소지 후 클릭하여 즉시 납품!"
                ));
                display.setItemMeta(meta);
            }
            inv.setItem(slots[i], display);
        }

        // Weekly Quest 1 (Slot 29): 128 Crops/Bread -> 2,500 Churu + 2 Starlight Stones
        boolean w1Done = data.getBoolean("weekly." + weekId + "." + player.getUniqueId() + ".bread_48", false);
        inv.setItem(29, w1Done
                ? makeIcon(Material.LIME_DYE, "<green><bold>✔ [주간 완료] 마을 식량 창고 대보급</bold>", "<gray>이번 주 대형 보급을 완료했습니다!")
                : makeIcon(Material.BREAD, "<aqua><bold>🌟 [주간 의뢰] 마을 식량 창고 대보급</bold></aqua>",
                        "<gray>필요 물품: <white>빵(BREAD) 48개</white>",
                        "<gold>✦ 보상: 2,500 츄르 + <aqua>찬란한 별빛 세공석 2개</aqua>!",
                        "", "<yellow>▶ 소지 후 클릭하여 납품"));

        // Weekly Quest 2 (Slot 33): 16 Diamonds -> 3,500 Churu + 1 Starlight Protection Scroll
        boolean w2Done = data.getBoolean("weekly." + weekId + "." + player.getUniqueId() + ".diamond_12", false);
        inv.setItem(33, w2Done
                ? makeIcon(Material.LIME_DYE, "<green><bold>✔ [주간 완료] 대장간 별빛 결정 조달</bold>", "<gray>이번 주 고급 광물 조달을 완료했습니다!")
                : makeIcon(Material.DIAMOND, "<light_purple><bold>🌟 [주간 의뢰] 대장간 별빛 결정 조달</bold></light_purple>",
                        "<gray>필요 물품: <white>다이아몬드 12개</white>",
                        "<gold>✦ 보상: 3,500 츄르 + <light_purple>유미의 세공 보호권 1장</light_purple>!",
                        "", "<yellow>▶ 소지 후 클릭하여 납품"));

        boolean bonusClaimed = data.getBoolean("quests." + today + "." + player.getUniqueId() + ".all_clear", false);
        inv.setItem(31, makeIcon(bonusClaimed ? Material.MINECART : Material.CHEST_MINECART,
                bonusClaimed ? "<green><bold>✔ 오늘의 일일 4종 올클리어 수령 완료!</bold>"
                        : "<gradient:#ffd666:#ff85c0><bold>🎁 일일 의뢰 4종 올클리어 보너스</bold></gradient>",
                "<gray>진행도: <white>" + completedCount + " / 4 완료",
                "<gold>✦ 보너스: 참치맛 츄르(1,000) + 별빛 세공석 1개 + 1티어 소환석 1개!",
                completedCount >= 4 && !bonusClaimed ? "<yellow>▶ 클릭하여 보너스 수령!" : "<dark_gray>(일일 4종 모두 완료 시 클릭 가능)"
        ));

        player.openInventory(inv);
    }

    public void openCodexGui(Player player) {
        int doneTotal = countCompletedCodex(player);
        Inventory inv = Bukkit.createInventory(new BoardHolder(BoardType.CODEX), 45,
                TextUtil.mm("<aqua><bold>📚 97랜드 수집 도감</bold> <gray>(" + doneTotal + " / " + codexEntries.size() + ")"));

        ItemStack bg = makeIcon(Material.CYAN_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, bg);

        int[] gridSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };

        for (int i = 0; i < codexEntries.size(); i++) {
            CodexEntry e = codexEntries.get(i);
            boolean registered = data.getBoolean("codex." + player.getUniqueId() + "." + e.key, false);
            ItemStack item = e.customId != null ? buildCustomPreview(e.customId) : new ItemStack(e.mat);
            ItemMeta m = item.getItemMeta();
            if (registered) {
                m.displayName(TextUtil.mm("<green><bold>✔ [등록됨] " + e.name + "</bold>"));
                m.lore(TextUtil.lore("<gray>이미 도감에 영구 등록된 수집품입니다! 🐾"));
            } else {
                m.displayName(TextUtil.mm("<yellow><bold>❔ [미등록] " + e.name + "</bold>"));
                m.lore(TextUtil.lore(
                        "<gray>등록 필요 수량: <white>" + e.amount + "개",
                        "<gold>✦ 첫 등록 보상: <bold>" + TextUtil.fmt(e.rewardChuru) + " 츄르</bold>",
                        "",
                        "<yellow>▶ 클릭하여 도감에 등록하기"
                ));
            }
            item.setItemMeta(m);
            inv.setItem(gridSlots[i], item);
        }

        boolean masterClaimed = data.getBoolean("codex." + player.getUniqueId() + ".master_reward", false);
        inv.setItem(40, makeIcon(Material.NETHER_STAR,
                masterClaimed ? "<green><bold>★ 도감 마스터 보상 수령 완료! ★</bold>"
                        : "<gradient:#ffe14d:#5cdbd3><bold>👑 97랜드 도감 마스터 최종 보상</bold></gradient>",
                "<gray>수집 달성률: <white>" + doneTotal + " / " + codexEntries.size() + " " + TextUtil.progressBar(doneTotal, codexEntries.size(), 10),
                "<light_purple>✦ 올클리어 보상: 전설의 황금 츄르(10,000) + 『아홉 개의 목숨』 수호 부적!",
                (doneTotal >= codexEntries.size() && !masterClaimed) ? "<yellow>▶ 클릭하여 마스터 보상 수령!" : "<dark_gray>(21종 전원 등록 시 수령 가능)"
        ));

        player.openInventory(inv);
    }

    private int countCompletedCodex(Player player) {
        return getCompletedCodexCount(player.getUniqueId());
    }

    public int getCompletedCodexCount(UUID playerUuid) {
        int c = 0;
        for (CodexEntry e : codexEntries) {
            if (data.getBoolean("codex." + playerUuid + "." + e.key, false)) c++;
        }
        return c;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BoardHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0) return;

        if (holder.type == BoardType.DAILY_QUEST) {
            handleDailyQuestClick(player, slot);
        } else {
            handleCodexClick(player, slot);
        }
    }

    private void handleDailyQuestClick(Player player, int slot) {
        String today = LocalDate.now().toString();
        List<QuestSpec> todays = getTodaysQuests();
        int[] slots = {10, 12, 14, 16};

        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i]) {
                QuestSpec q = todays.get(i);
                String path = "quests." + today + "." + player.getUniqueId() + "." + q.key;
                if (data.getBoolean(path, false)) return;

                if (!consumeMatchingItems(player, q.mat, q.customId, q.amount)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_CAT_STRAY_AMBIENT, 1f, 1f);
                    player.sendMessage(TextUtil.msg("<red>필요한 물품(<white>" + q.title + " - " + q.amount + "개</white>)이 부족합니다!"));
                    return;
                }
                data.set(path, true);
                churu.deposit(player, q.churuReward);
                player.getInventory().addItem(items.tornPage(q.pageReward));
                saveCallback.run();

                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
                player.sendMessage(TextUtil.msg("📋 일일 의뢰 <yellow>[" + q.title + "]</yellow> 완료! " + TextUtil.churu(q.churuReward) + " <gray>+ <aqua>찢어진 책장 " + q.pageReward + "장</aqua> 획득!"));
                openDailyQuestGui(player);
                return;
            }
        }

        if (slot == 29) {
            String weekId = LocalDate.now().get(IsoFields.WEEK_BASED_YEAR) + "-W" + LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            String path = "weekly." + weekId + "." + player.getUniqueId() + ".bread_48";
            if (data.getBoolean(path, false)) return;
            if (!consumeMatchingItems(player, Material.BREAD, null, 48)) {
                player.sendMessage(TextUtil.msg("<red>납품할 빵(48개)이 부족합니다!"));
                return;
            }
            data.set(path, true);
            churu.deposit(player, 2500.0);
            player.getInventory().addItem(items.starlightStone(2));
            saveCallback.run();
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.3f);
            player.sendMessage(TextUtil.msg("🌟 주간 의뢰 완료! " + TextUtil.churu(2500) + " + <aqua>별빛 세공석 2개</aqua> 획득!"));
            openDailyQuestGui(player);
            return;
        }

        if (slot == 33) {
            String weekId = LocalDate.now().get(IsoFields.WEEK_BASED_YEAR) + "-W" + LocalDate.now().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            String path = "weekly." + weekId + "." + player.getUniqueId() + ".diamond_12";
            if (data.getBoolean(path, false)) return;
            if (!consumeMatchingItems(player, Material.DIAMOND, null, 12)) {
                player.sendMessage(TextUtil.msg("<red>납품할 다이아몬드(12개)가 부족합니다!"));
                return;
            }
            data.set(path, true);
            churu.deposit(player, 3500.0);
            player.getInventory().addItem(items.starlightScroll(1));
            saveCallback.run();
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.25f);
            player.sendMessage(TextUtil.msg("🌟 주간 의뢰 완료! " + TextUtil.churu(3500) + " + <light_purple>유미의 세공 보호권 1장</light_purple> 획득!"));
            openDailyQuestGui(player);
            return;
        }

        if (slot == 31) {
            int done = 0;
            for (QuestSpec q : todays) {
                if (data.getBoolean("quests." + today + "." + player.getUniqueId() + "." + q.key, false)) done++;
            }
            String bonusPath = "quests." + today + "." + player.getUniqueId() + ".all_clear";
            if (done >= 4 && !data.getBoolean(bonusPath, false)) {
                data.set(bonusPath, true);
                player.getInventory().addItem(items.churuTuna(1), items.starlightStone(1), items.bossCoreRat(1));
                saveCallback.run();
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                Bukkit.broadcast(TextUtil.msg("🌟 <white>" + player.getName() + "</white>님이 오늘의 일일 의뢰 4종을 올클리어하여 별빛 세공석과 보스 소환석을 획득했습니다!"));
                openDailyQuestGui(player);
            }
        }
    }

    private void handleCodexClick(Player player, int slot) {
        int[] gridSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34
        };
        for (int i = 0; i < gridSlots.length; i++) {
            if (slot == gridSlots[i]) {
                CodexEntry e = codexEntries.get(i);
                String path = "codex." + player.getUniqueId() + "." + e.key;
                if (data.getBoolean(path, false)) return;

                if (!consumeMatchingItems(player, e.mat, e.customId, e.amount)) {
                    player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
                    player.sendMessage(TextUtil.msg("<red>도감 등록에 필요한 아이템(<white>" + e.name + " " + e.amount + "개</white>)이 부족합니다!"));
                    return;
                }
                data.set(path, true);
                churu.deposit(player, e.rewardChuru);
                saveCallback.run();

                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
                player.sendMessage(TextUtil.msg("📚 수집 도감에 <aqua>[" + e.name + "]</aqua> 등록 완료! 보상으로 " + TextUtil.churu(e.rewardChuru) + "<gray>를 받았습니다!"));
                openCodexGui(player);
                return;
            }
        }

        if (slot == 40) {
            int doneTotal = countCompletedCodex(player);
            String masterPath = "codex." + player.getUniqueId() + ".master_reward";
            if (doneTotal >= codexEntries.size() && !data.getBoolean(masterPath, false)) {
                data.set(masterPath, true);
                player.getInventory().addItem(items.churuGolden(1), items.nineLivesCharm());
                saveCallback.run();
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.0f);
                Bukkit.broadcast(TextUtil.msg("👑 <white><bold>" + player.getName() + "</bold></white>님이 97랜드 수집 도감 21종을 모두 완성하여 <gold>도감 마스터</gold>에 등극했습니다!!"));
                openCodexGui(player);
            }
        }
    }

    private boolean consumeMatchingItems(Player player, Material mat, String customId, int needed) {
        int available = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (ItemStack st : contents) {
            if (matches(st, mat, customId)) available += st.getAmount();
        }
        if (available < needed) return false;

        int rem = needed;
        for (ItemStack st : contents) {
            if (rem <= 0) break;
            if (matches(st, mat, customId)) {
                int take = Math.min(st.getAmount(), rem);
                st.setAmount(st.getAmount() - take);
                rem -= take;
            }
        }
        return true;
    }

    private boolean matches(ItemStack st, Material mat, String customId) {
        if (st == null || st.getType().isAir()) return false;
        String foundId = items.getCustomId(st);
        if (customId != null) {
            return customId.equals(foundId);
        }
        return foundId == null && st.getType() == mat;
    }

    private ItemStack buildCustomPreview(String customId) {
        return switch (customId) {
            case "zoomies_skewer" -> items.zoomiesSkewer(1);
            case "kneading_bento" -> items.kneadingBento(1);
            case "catnip_elixir" -> items.catnipElixir(1);
            case "royal_feast_can" -> items.royalFeastCan(1);
            default -> new ItemStack(Material.PAPER);
        };
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
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
        if (command.getName().equalsIgnoreCase("codex")) {
            int done = getCompletedCodexCount(player.getUniqueId());
            player.sendMessage(TextUtil.msg("📚 내 수집 도감 달성도: <aqua><bold>" + done + " / " + codexEntries.size() + "종</bold></aqua> (상점 매입 보너스 +" + (done >= 21 ? 25 : done) + "%) — 도감 등록은 허브 서쪽 <yellow>[마을 회관 박물관]</yellow>을 방문해 주세요!"));
        } else {
            player.sendMessage(TextUtil.msg("📋 일일·주간 납품 의뢰는 허브 도시 서쪽 끝 <yellow>[고양이 마을 회관]</yellow>의 치즈냥 게시판에서 현장 진행하실 수 있습니다!"));
        }
        return true;
    }
}
