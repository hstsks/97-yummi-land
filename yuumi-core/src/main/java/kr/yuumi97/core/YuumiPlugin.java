package kr.yuumi97.core;

import kr.yuumi97.core.altar.ThresholdAltarManager;
import kr.yuumi97.core.boss.RaidBossManager;
import kr.yuumi97.core.casino.CasinoManager;
import kr.yuumi97.core.casino.CatRaceManager;
import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.economy.SkillBonusBridge;
import kr.yuumi97.core.facility.BankAndVaultManager;
import kr.yuumi97.core.facility.BlacksmithManager;
import kr.yuumi97.core.facility.OnsenAndClinicManager;
import kr.yuumi97.core.hub.HubWorldManager;
import kr.yuumi97.core.items.RelicAndFoodListener;
import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.pack.ResourcePackServer;
import kr.yuumi97.core.plot.PlotManager;
import kr.yuumi97.core.plot.PlotManager.PlotSpec;
import kr.yuumi97.core.portal.PortalAndRecallManager;
import kr.yuumi97.core.quest.QuestAndCodexManager;
import kr.yuumi97.core.shop.NpcShopManager;
import kr.yuumi97.core.shop.NpcShopManager.ShopType;
import kr.yuumi97.core.trade.DirectTradeManager;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class YuumiPlugin extends JavaPlugin implements Listener, CommandExecutor {

    public static final class GuidebookHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private File dataFile;
    private FileConfiguration dataConfig;

    private YuumiItems items;
    private ChuruManager churuManager;
    private ResourcePackServer packServer;
    private HubWorldManager hubManager;
    private PlotManager plotManager;
    private ThresholdAltarManager altarManager;
    private PortalAndRecallManager portalManager;
    private QuestAndCodexManager questManager;
    private SkillBonusBridge skillBridge;
    private NpcShopManager shopManager;
    private DirectTradeManager tradeManager;
    private BankAndVaultManager bankManager;
    private BlacksmithManager blacksmithManager;
    private OnsenAndClinicManager onsenManager;
    private CasinoManager casinoManager;
    private CatRaceManager catRaceManager;
    private RaidBossManager raidManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadDataFile();

        // 1. Custom Items & Crafting Recipes
        this.items = new YuumiItems(this);
        this.items.registerRecipes();

        // 2. Economy & Embedded ResourcePack HTTP Server
        this.churuManager = new ChuruManager(this, items, dataConfig, this::saveDataFile);
        this.packServer = new ResourcePackServer(this);
        this.packServer.start();

        // 3. Dedicated Peaceful Hub City World (yuumi_hub) & NPC Action Router
        this.hubManager = new HubWorldManager(this, dataConfig, this::saveDataFile, this::handleHubNpcInteract);
        this.hubManager.initHubWorld();

        // 4. South 12-Plot Real Estate District
        this.plotManager = new PlotManager(this, churuManager, dataConfig, this::saveDataFile, hubManager::getHubWorld);
        this.plotManager.setStarterCottageBuilder(hubManager::buildStarterCottageOnPlot);
        this.hubManager.setPlotManager(plotManager);

        // 5. Threshold Altar, 4-Gate Portal Sanctuary & Channeled/Bookmark Recall
        this.altarManager = new ThresholdAltarManager(this, items, churuManager, dataConfig, this::saveDataFile);
        this.portalManager = new PortalAndRecallManager(
                this, items, altarManager, plotManager,
                hubManager::getHubWorld, hubManager::getHubSpawn, hubManager::getArenaCenter
        );
        this.altarManager.setOnPhaseUnlockedCallback(portalManager::igniteUnlockedPortals);

        // 6. Quests, Codex, AuraSkills Bonus Bridge, 5 NPC Shops & Safe 1:1 Direct Trade
        this.questManager = new QuestAndCodexManager(this, items, churuManager, dataConfig, this::saveDataFile);
        this.skillBridge = new SkillBonusBridge(this, questManager::getCompletedCodexCount);
        this.shopManager = new NpcShopManager(this, items, churuManager, skillBridge);
        this.tradeManager = new DirectTradeManager(this, churuManager);

        // 7. Hub Convenience Facilities (Bank/Vault, Starlight Blacksmith, Onsen/Diner/Clinic)
        this.bankManager = new BankAndVaultManager(this, churuManager, dataConfig, this::saveDataFile);
        this.blacksmithManager = new BlacksmithManager(this, items, churuManager);
        this.blacksmithManager.startAttributeSyncTask();

        this.onsenManager = new OnsenAndClinicManager(
                this, items, churuManager,
                hubManager::isOnsenWater, hubManager::getClinicRespawnLocation
        );
        this.onsenManager.startOnsenTask();

        // 8. 2F Golden Paws Casino Hall & Live 5-Lane Cat Derby
        this.casinoManager = new CasinoManager(this, items, churuManager, dataConfig, this::saveDataFile);
        this.casinoManager.setHubWorldSupplier(hubManager::getHubWorld);
        this.catRaceManager = new CatRaceManager(this, casinoManager, hubManager::getHubWorld);
        this.catRaceManager.startScheduler();

        // 9. Multi-Phase Raid Colosseum & Yuumi Relics
        this.raidManager = new RaidBossManager(this, items, churuManager, altarManager, hubManager::getArenaCenter);

        RelicAndFoodListener relicListener = new RelicAndFoodListener(
                this, items, churuManager, this::openMainMenu,
                portalManager::startChanneledRecall
        );
        relicListener.startFriendshipAuraTask();

        // Register all event listeners
        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(this, this);
        pm.registerEvents(packServer, this);
        pm.registerEvents(hubManager, this);
        pm.registerEvents(plotManager, this);
        pm.registerEvents(altarManager, this);
        pm.registerEvents(portalManager, this);
        pm.registerEvents(questManager, this);
        pm.registerEvents(shopManager, this);
        pm.registerEvents(tradeManager, this);
        pm.registerEvents(bankManager, this);
        pm.registerEvents(blacksmithManager, this);
        pm.registerEvents(onsenManager, this);
        pm.registerEvents(casinoManager, this);
        pm.registerEvents(catRaceManager, this);
        pm.registerEvents(raidManager, this);
        pm.registerEvents(relicListener, this);

        // Bind commands (All facility commands act as Wayfinding Guides per Physical Visit Philosophy!)
        bindCmd("yuumi", this);
        bindCmd("churu", churuManager);
        bindCmd("altar", altarManager);
        bindCmd("casino", casinoManager);
        bindCmd("duelbet", casinoManager);
        bindCmd("quest", questManager);
        bindCmd("codex", questManager);
        bindCmd("land", plotManager);
        bindCmd("raid", raidManager);

        // Post-boot world & Vault hook
        Bukkit.getScheduler().runTaskLater(this, () -> {
            churuManager.hookVault();
            hubManager.ensureCityBuilt();
            portalManager.igniteUnlockedPortals();
            plotManager.refreshAllPlotSigns();
        }, 40L);

        // Live Broadcast-Style Actionbar HUD every 2.5 seconds
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            int phase = altarManager.getCurrentPhase();
            double jackpot = casinoManager.getJackpot();
            for (Player p : Bukkit.getOnlinePlayers()) {
                double bal = churuManager.getBalance(p);
                String zone = hubManager.describeDistrict(p.getLocation());
                p.sendActionBar(TextUtil.mm(
                        "<#ff85c0>" + zone + "</#ff85c0> "
                        + "<dark_gray>│</dark_gray> <gold><bold>" + TextUtil.fmt(bal) + " 츄르</bold></gold> "
                        + "<dark_gray>│</dark_gray> <aqua>📖 제" + phase + "장</aqua> "
                        + "<dark_gray>│</dark_gray> <yellow>🎰 잭팟 " + TextUtil.fmt(jackpot) + "</yellow>"
                ));
            }
        }, 60L, 50L);

        getLogger().info("==========================================================");
        getLogger().info("  🐱 [유미의 97랜드 v2.0] YuumiLandCore 활성화 완료!");
        getLogger().info("  전용 평화 허브(yuumi_hub) · 12필지 분양 · 2F 카지노/경묘 준비됨");
        getLogger().info("==========================================================");
    }

    @Override
    public void onDisable() {
        if (catRaceManager != null) catRaceManager.cleanup();
        if (raidManager != null) raidManager.cleanup();
        if (packServer != null) packServer.stop();
        saveDataFile();
    }

    private void handleHubNpcInteract(Player player, String role) {
        switch (role) {
            case "altar" -> altarManager.openAltarGui(player);
            case "raid" -> raidManager.openRaidGui(player);
            case "shop_farm" -> shopManager.openShop(player, ShopType.FARM);
            case "shop_fish" -> shopManager.openShop(player, ShopType.FISH);
            case "shop_mine" -> shopManager.openShop(player, ShopType.MINE);
            case "shop_build" -> shopManager.openShop(player, ShopType.BUILD);
            case "shop_misc" -> shopManager.openShop(player, ShopType.MISC);
            case "bank_teller" -> bankManager.openBankCounter(player);
            case "vault_keeper" -> bankManager.openPersonalVault(player, 1);
            case "blacksmith" -> blacksmithManager.openBlacksmithGui(player);
            case "quest" -> questManager.openDailyQuestGui(player);
            case "codex" -> questManager.openCodexGui(player);
            case "plot_registry" -> plotManager.openRegistryGui(player);
            case "casino_cashier" -> casinoManager.openCashierGui(player);
            case "cat_race" -> catRaceManager.openBettingGui(player);
            case "casino_blackjack" -> casinoManager.openBlackjackSetup(player, false);
            case "casino_blackjack_vip" -> casinoManager.openBlackjackSetup(player, true);
            case "casino_roulette" -> casinoManager.openRouletteGui(player);
            case "casino_welfare" -> casinoManager.claimWelfareSoup(player);
            case "diner" -> onsenManager.openDinerGui(player);
            case "clinic" -> {
                AttributeInstance maxHp = player.getAttribute(Attribute.MAX_HEALTH);
                player.setHealth(maxHp != null ? maxHp.getValue() : 20.0);
                player.setFoodLevel(20);
                player.setSaturation(20f);
                player.setFireTicks(0);
                player.playSound(player.getLocation(), Sound.ENTITY_CAT_PURR, 1f, 1.2f);
                player.sendMessage(TextUtil.msg("💉 <green><bold>[응급 치유소]</bold></green> 간호냥이 정성껏 꾹꾹이 치료를 해 주어 체력과 허기가 완전히 회복되었습니다!"));
            }
            case "tavern" -> {
                player.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 1f, 1.15f);
                player.sendMessage(TextUtil.msg("🍺 <gold><bold>[벚꽃 선술집 양조 비법]</bold></gold> 테라스의 <yellow>물 가마솥(모닥불 위)</yellow>에 밀·사과·달콤한 열매·발광 열매 등을 넣고 시계로 발효 시간을 맞춘 뒤 유리병으로 떠서 <yellow>오크통(Barrel)</yellow>에 숙성해 보세요! 지인들과 취기 예능을 즐길 수 있습니다!"));
            }
        }
    }

    private void bindCmd(String name, CommandExecutor exec) {
        if (getCommand(name) != null) {
            getCommand(name).setExecutor(exec);
        }
    }

    private void loadDataFile() {
        this.dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            getDataFolder().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                getLogger().warning("Could not create data.yml: " + e.getMessage());
            }
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public synchronized void saveDataFile() {
        if (dataConfig == null || dataFile == null) return;
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    // ==================== 첫 접속 환영 키트 & 허브 광장 스폰 ====================

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        churuManager.ensureStarterBalance(p);

        String starterKey = "players." + p.getUniqueId() + ".starter_kit_v2";
        if (!dataConfig.getBoolean(starterKey, false)) {
            dataConfig.set(starterKey, true);
            saveDataFile();
            p.getInventory().addItem(
                    items.bookOfThresholds(),
                    items.recallBookmark(3),
                    items.churuSalmon(5),
                    items.zoomiesSkewer(3)
            );
            p.teleport(hubManager.getHubSpawn());
            Bukkit.broadcast(TextUtil.msg("🐱 <white><bold>" + p.getName() + "</bold></white>님이 <gradient:#ff85c0:#ffd666><bold>유미의 97랜드</bold></gradient> 고양이 허브 도시에 첫 발을 내디뎠습니다! 환영해주세요!!"));
        }

        p.sendMessage(TextUtil.mm(""));
        p.sendMessage(TextUtil.msg("<gradient:#ff85c0:#ffd666><bold>유미의 97랜드 v2.0</bold></gradient><gray>에 오신 것을 환영합니다! (<yellow>/메뉴</yellow> 길찾기 수첩 │ <aqua>문턱의 서 Shift+우클릭</aqua> 허브 귀환)"));
        p.sendMessage(TextUtil.mm("<gray> · 현재 진행: <aqua>" + altarManager.getPhaseTitle() + "</aqua> │ 카지노 누적 잭팟: " + TextUtil.churu(casinoManager.getJackpot())));
        p.sendMessage(TextUtil.mm("<gray> · 지인 직거래: 광장에서 상대를 <yellow>[Shift + 우클릭]</yellow>하여 안전 교환창을 여세요!"));
        p.sendMessage(TextUtil.mm(""));
    }

    // ==================== 차원 안내 수첩 & 나침반 길찾기 (/메뉴) ====================

    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new GuidebookHolder(), 45,
                TextUtil.mm("<gradient:#ff85c0:#ffd666><bold>📖 유미의 97랜드 차원 안내 수첩</bold></gradient>"));

        ItemStack bg = makeIcon(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, bg);

        double wallet = churuManager.getBalance(player);
        double bank = churuManager.getBankBalance(player);
        int chips = casinoManager.countPlayerChips(player);
        PlotSpec myPlot = plotManager.getOwnedPlot(player.getUniqueId());
        int codexCount = questManager.getCompletedCodexCount(player.getUniqueId());

        inv.setItem(4, makeIcon(Material.NETHER_STAR,
                "<gold><bold>🐾 집사 프로필 (" + player.getName() + ")</bold></gold>",
                "<white>지갑 보유액: " + TextUtil.churu(wallet),
                "<aqua>은행 안전예금: <bold>" + TextUtil.fmt(bank) + " 츄르</bold> <gray>(사망 차감 면제!)</gray>",
                "<yellow>보유 카지노 칩: <bold>" + TextUtil.fmt(chips) + " 칩</bold>",
                "<#ff85c0>내 분양 부지: <white>" + (myPlot != null ? myPlot.id() + "호 (" + myPlot.tier().display + ")" : "미보유 (남쪽 단지 분양 가능)"),
                "<green>수집 도감 등록: <white>" + codexCount + " / 21종 <gray>(상점 매입가 +" + Math.round(skillBridge.getCodexBonusRate(player) * 100) + "%)</gray>",
                "",
                "<gray>🤝 지인 안전 직거래: 상대 캐릭터 <yellow>[Shift + 우클릭]</yellow>"
        ));

        // Row 3: Hub Districts Wayfinding Compass Buttons (Physical Visit Philosophy!)
        inv.setItem(19, makeIcon(Material.ENCHANTED_BOOK, "<light_purple><bold>🌌 [북쪽] 문턱의 서 제단 & 4대 대포탈 성소</bold></light_purple>",
                "<gray>위치: 중앙 광장 북쪽 계단 위 (X=0, Z=-55)",
                "<white>현재 차원: <aqua>" + altarManager.getPhaseTitle(),
                "<gray>야생·네더·엔드·레이드 결투장 대포탈이 모인 성소입니다.",
                "", "<yellow>▶ 클릭 시 해당 시설로 나침반 방향 설정"));

        inv.setItem(20, makeIcon(Material.EMERALD, "<gold><bold>⚖ [서쪽] 5대 전문 NPC 상점 거리</bold></gold>",
                "<gray>위치: 서쪽 대로변 (X=-37 ~ -62, Z=0)",
                "<gray>농산·수산·광물·건축·만물 상인냥이 상주합니다.",
                "<green>AuraSkills 레벨 & 도감 달성률만큼 매입 단가 상승!",
                "", "<yellow>▶ 클릭 시 나침반 방향 설정"));

        inv.setItem(21, makeIcon(Material.RAW_GOLD_BLOCK, "<yellow><bold>🏦 [서쪽] 츄르 중앙 은행 & 대장간/마법도서관</bold></yellow>",
                "<gray>위치: 서쪽 중반부 (X=-83, Z=-8 / Z=+8)",
                "<gray>· 은행: 사망 구조비 100% 면제 예금, 수표 발행, 4탭 개인 금고",
                "<gray>· 대장간: 장비 수리, 파괴 0% ★1~★7 별빛 세공, 30Lv 인챈트",
                "", "<yellow>▶ 클릭 시 나침반 방향 설정"));

        inv.setItem(22, makeIcon(Material.WRITABLE_BOOK, "<#ff9c6e><bold>🏛 [서쪽 끝] 고양이 마을 회관</bold></#ff9c6e>",
                "<gray>위치: 서쪽 대로 종점 (X=-108, Z=0)",
                "<gray>일일·주간 납품 의뢰, 21종 도감 박물관, 토지 등기소 통합 청사!",
                "", "<yellow>▶ 클릭 시 나침반 방향 설정"));

        inv.setItem(23, items.slotIcon("slot_seven", "<gradient:#ffe14d:#ff4d79><bold>🎰 [동쪽] 2층 황금 냥냥 카지노 & 경묘장</bold></gradient>"));
        ItemMeta cm = inv.getItem(23).getItemMeta();
        cm.lore(TextUtil.lore(
                "<gray>위치: 동쪽 대로 북측 랜드마크 (X=+63, Z=-8)",
                "<white>현재 누적 잭팟: " + TextUtil.churu(casinoManager.getJackpot()),
                "<gray>1F 환전소·슬롯·블랙잭·룰렛·<gold>실시간 5레인 고양이 경주</gold>!",
                "<light_purple>2F VIP 하이롤러 발코니 & 뒷골목 무료 급식소",
                "", "<yellow>▶ 클릭 시 나침반 방향 설정"));
        inv.getItem(23).setItemMeta(cm);

        inv.setItem(24, makeIcon(Material.CAMPFIRE, "<#ff85c0><bold>♨ [동쪽] 노천 온천 · 식당 · 선술집 · 낚시터</bold></#ff85c0>",
                "<gray>위치: 동쪽 대로 남측 (X=+45 ~ +107, Z=+15)",
                "<gray>15초 입수 시 15분 버프 온천, 특선 도시락 식당, 수제 양조 선술집!",
                "", "<yellow>▶ 클릭 시 나침반 방향 설정"));

        inv.setItem(25, makeIcon(Material.CHERRY_SAPLING, "<#ff85c0><bold>🏡 [남쪽] 벚꽃 대교 & 12필지 분양 주거단지</bold></#ff85c0>",
                "<gray>위치: 남쪽 벚꽃 대교 너머 (X=0, Z=+60 ~ +200)",
                "<gray>소형(15×15) · 중형(23×23) · 대형 별장(33×33) 매물 현장 표지판 확인!",
                "", "<yellow>▶ 클릭 시 나침반 방향 설정"));

        // Row 5: Channeled Recall & Guidebook Recovery
        inv.setItem(40, makeIcon(Material.FEATHER, "<aqua><bold>✨ 3초 정신집중 허브 귀환 발동</bold></aqua>",
                "<gray>야생·네더·엔드 원정 중 제자리에 3초간 가만히 서서",
                "<gray>고양이 허브 도시 중앙 광장으로 무료 귀환합니다! (쿨타임 60초)",
                "<gray>※ 1초 고속 집 귀환은 <white>[귀환 책갈피]</white> 우클릭!",
                "", "<yellow>▶ 클릭하여 귀환 정신집중 시작"));

        player.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof GuidebookHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;

        int slot = event.getRawSlot();
        if (slot == 40) {
            p.closeInventory();
            portalManager.startChanneledRecall(p, false);
            return;
        }

        World hub = hubManager.getHubWorld();
        Location target = switch (slot) {
            case 19 -> new Location(hub, 0.5, 66.0, -54.5);
            case 20 -> new Location(hub, -48.5, 65.0, 0.5);
            case 21 -> new Location(hub, -83.5, 65.0, 0.5);
            case 22 -> new Location(hub, -106.5, 65.0, 0.5);
            case 23 -> new Location(hub, 63.5, 65.0, -8.5);
            case 24 -> new Location(hub, 56.5, 65.0, 12.5);
            case 25 -> new Location(hub, 0.5, 65.0, 62.5);
            default -> null;
        };

        if (target != null) {
            p.closeInventory();
            if (hub != null && hub.equals(p.getWorld())) {
                p.setCompassTarget(target);
                int dist = (int) Math.round(p.getLocation().distance(target));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.35f);
                p.sendMessage(TextUtil.msg("🧭 목표 시설 방향(<yellow>X=" + target.getBlockX() + ", Z=" + target.getBlockZ()
                        + "</yellow>, 거리 약 <aqua>" + dist + "m</aqua>)으로 길찾기 안내가 설정되었습니다!"));
            } else {
                p.playSound(p.getLocation(), Sound.ENTITY_CAT_PURREOW, 1f, 1.15f);
                p.sendMessage(TextUtil.msg("🌌 현재 원정 월드에 계십니다! 먼저 <aqua>[문턱의 서 Shift+우클릭]</aqua> 또는 <yellow>[귀환 책갈피]</yellow>로 고양이 허브 도시에 복귀해 주세요!"));
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (args.length == 0) {
            openMainMenu(p);
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("책") || sub.equals("book")) {
            p.getInventory().addItem(items.bookOfThresholds());
            p.sendMessage(TextUtil.msg("📖 <aqua>유미의 문턱의 서</aqua>를 지급받았습니다!"));
            return true;
        }

        if (sub.equals("setup-hub") && p.isOp()) {
            World hub = hubManager.initHubWorld();
            hubManager.buildEntireHubCity(hub);
            portalManager.igniteUnlockedPortals();
            plotManager.refreshAllPlotSigns();
            p.teleport(hubManager.getHubSpawn());
            p.sendMessage(TextUtil.msg("<green>[관리자] 전용 평화 허브 도시(yuumi_hub) 5대 구역과 16종 .nbt 건축물 스탬핑이 재구축되었습니다!"));
            return true;
        }

        if (sub.equals("props") && p.isOp()) {
            hubManager.refreshCustom3DProps();
            p.sendMessage(TextUtil.msg("<green>[관리자] 리소스팩 내 커스텀 3D 오브젝트(ItemDisplay) 스캔 및 배치를 갱신했습니다!"));
            return true;
        }

        if (sub.equals("give") && p.isOp() && args.length >= 2) {
            String key = args[1].toLowerCase();
            ItemStack gift = switch (key) {
                case "churu_salmon" -> items.churuSalmon(5);
                case "churu_tuna" -> items.churuTuna(5);
                case "churu_golden" -> items.churuGolden(1);
                case "chip_100" -> items.chip100(10);
                case "chip_1000" -> items.chip1000(5);
                case "chip_10000" -> items.chip10000(1);
                case "bookmark", "recall_bookmark" -> items.recallBookmark(5);
                case "starlight_stone" -> items.starlightStone(10);
                case "starlight_scroll" -> items.starlightScroll(3);
                case "torn_page" -> items.tornPage(10);
                case "bell" -> items.youAndMeBell();
                case "wand" -> items.prowlingWand();
                case "tome" -> items.finalChapterTome();
                case "charm" -> items.nineLivesCharm();
                case "core1" -> items.bossCoreRat(1);
                case "core2" -> items.bossCoreHound(1);
                case "core3" -> items.bossCoreVoid(1);
                case "hat_yuumi_ears" -> items.yuumiEarsHat();
                case "plushie_yuumi" -> items.yuumiPlushie();
                default -> {
                    // Support minting any custom 3D model item authored by Codex in Yuumi97Pack.zip!
                    ItemStack custom = new ItemStack(Material.CARVED_PUMPKIN);
                    ItemMeta m = custom.getItemMeta();
                    if (m != null) {
                        m.displayName(TextUtil.mm("<light_purple><bold>✨ 커스텀 에셋: " + key + "</bold></light_purple>"));
                        m.setItemModel(new NamespacedKey("yuumi97", key));
                        custom.setItemMeta(m);
                    }
                    yield custom;
                }
            };
            p.getInventory().addItem(gift);
            p.sendMessage(TextUtil.msg("[관리자] 커스텀 아이템 지급 완료: <yellow>" + key + "</yellow>"));
            return true;
        }

        openMainMenu(p);
        return true;
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
