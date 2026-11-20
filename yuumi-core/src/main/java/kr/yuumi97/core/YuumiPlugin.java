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
import kr.yuumi97.core.hub.HubUtilityManager;
import kr.yuumi97.core.guide.GuidebookManager;
import kr.yuumi97.core.guide.HubTravelManager;
import kr.yuumi97.core.guide.WayfindingManager;
import kr.yuumi97.core.training.TrainingDummyManager;
import kr.yuumi97.core.items.RelicAndFoodListener;
import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.pack.ResourcePackServer;
import kr.yuumi97.core.plot.PlotManager;
import kr.yuumi97.core.portal.PortalAndRecallManager;
import kr.yuumi97.core.quest.QuestAndCodexManager;
import kr.yuumi97.core.shop.NpcShopManager;
import kr.yuumi97.core.shop.NpcShopManager.ShopType;
import kr.yuumi97.core.trade.DirectTradeManager;
import kr.yuumi97.core.util.TextUtil;
import kr.yuumi97.core.util.RewardMailbox;
import kr.yuumi97.core.dungeon.DungeonManager;
import kr.yuumi97.core.rpg.RpgJobManager;
import kr.yuumi97.core.stock.ChuruStockManager;
import kr.yuumi97.core.housing.GachaAndFurnitureManager;
import kr.yuumi97.core.social.HallOfFameManager;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

public final class YuumiPlugin extends JavaPlugin implements Listener, CommandExecutor {

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
    private RewardMailbox rewardMailbox;
    private DungeonManager dungeonManager;
    private RpgJobManager rpgManager;
    private ChuruStockManager stockManager;
    private GachaAndFurnitureManager furnitureManager;
    private HallOfFameManager hallManager;
    private TrainingDummyManager trainingManager;
    private WayfindingManager wayfindingManager;
    private HubTravelManager travelManager;
    private GuidebookManager guidebookManager;
    private HubUtilityManager utilityManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        loadDataFile();
        normalizeInitializationKeys();

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
        items.setModelAvailability(hubManager::hasBundledModel);

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

        // Domain systems share the same economy and durable reward ledger.
        this.rewardMailbox = new RewardMailbox(this, items, dataConfig, this::saveDataFile);
        this.dungeonManager = new DungeonManager(this, items, churuManager, dataConfig, this::saveDataFile, hubManager::getHubSpawn, rewardMailbox);
        this.rpgManager = new RpgJobManager(this, items, churuManager, dataConfig, this::saveDataFile, plotManager);
        rpgManager.setMailbox(rewardMailbox);
        rpgManager.setModelAvailability(hubManager::hasBundledModel);
        this.stockManager = new ChuruStockManager(this, churuManager, casinoManager, dataConfig, this::saveDataFile, hubManager::getStockBrokerLocation);
        this.furnitureManager = new GachaAndFurnitureManager(this, items, casinoManager, plotManager, hubManager, dataConfig, this::saveDataFile, rewardMailbox);
        this.hallManager = new HallOfFameManager(this, churuManager, dataConfig, this::saveDataFile, hubManager::getHubWorld);
        hallManager.setBoardLocation(hubManager::getHallBoardLocation);
        this.trainingManager = new TrainingDummyManager(this, hubManager);
        rpgManager.setTrainingDummyHooks(trainingManager::isDummy, trainingManager::approvedDamage);
        rpgManager.setPracticingPredicate(trainingManager::isPracticing);
        hubManager.setTrainingDummyReconciler(trainingManager::reconcile);
        rpgManager.setPartyMembers(dungeonManager::partyMembers);
        rpgManager.setHostilePredicate(dungeonManager::isDungeonMob);
        rpgManager.setDungeonHooks(dungeonManager::isParticipant, dungeonManager::canAffect, dungeonManager::reviveSpectator);
        rpgManager.setDungeonCombatHooks(dungeonManager::damagePoise,
                entity -> dungeonManager.isBoss(entity) || raidManager.isRaidBoss(entity),
                event -> { double accepted = dungeonManager.acceptedDamage(event); return accepted >= 0 ? accepted : raidManager.acceptedDamage(event); });
        dungeonManager.setCoinMultiplier(rpgManager::getCoinMultiplier);
        dungeonManager.setCompletionXp(rpgManager::awardCombatXp);
        dungeonManager.setBoonDamage((player, target, amount) -> rpgManager.damage().raw(player, target, amount,
                kr.yuumi97.core.rpg.CombatDamageService.DamageKind.RUNE));
        questManager.setCompletionXp(rpgManager::awardQuestXp);
        raidManager.setRewardMailbox(rewardMailbox);
        onsenManager.setDungeonParticipantPredicate(dungeonManager::handlesClinic);
        portalManager.setLeaveCallback(dungeonManager::onExternalLeave);
        blacksmithManager.setRpgManager(rpgManager);
        blacksmithManager.setSuccessListener((player, star) -> { hallManager.recordSmith(player, star); stockManager.onSmithSuccess(); });
        shopManager.setRpgManager(rpgManager);
        onsenManager.setRpgManager(rpgManager);
        plotManager.setBeforePlotReset(furnitureManager::recoverPlot);
        casinoManager.setRouletteAnimation(furnitureManager::animateRoulette);
        hallManager.setMythicRefresh(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                var combat = rpgManager.getCombat(player);
                if (combat != null && rpgManager.combatLevel(player) == 20 && !rpgManager.combatAwakening(player).isEmpty())
                    hallManager.unlock(player.getUniqueId(), "mythic_" + combat.name(), "<light_purple>[신화 " + combat.display + "]</light_purple>");
                var life = rpgManager.getLife(player);
                if (life != null && rpgManager.lifeLevel(player) == 20 && !rpgManager.lifeSpec(player).isEmpty())
                    hallManager.unlock(player.getUniqueId(), "mythic_" + life.name(), "<light_purple>[신화 " + life.display + "]</light_purple>");
            }
        });
        wayfindingManager = new WayfindingManager(this, hubManager);
        travelManager = new HubTravelManager(this, hubManager, plotManager, wayfindingManager);
        travelManager.setBeforeTravel(portalManager::cancelRecall);
        guidebookManager = new GuidebookManager(this, items, travelManager, portalManager::startChanneledRecall, rewardMailbox::deliver);
        utilityManager = new HubUtilityManager(this, hubManager, items, (player, action) -> {
            switch (action) {
                case "forge" -> blacksmithManager.openBlacksmithGui(player);
                case "runes" -> rpgManager.getWorkshop().openSmith(player);
                case "furniture" -> rpgManager.getWorkshop().openFurniture(player);
                case "feast" -> rpgManager.getWorkshop().openFeast(player);
                case "vault" -> bankManager.openPersonalVault(player, 1);
            }
        }, rewardMailbox::give);
        wayfindingManager.start();
        rpgManager.start();
        // Register after RPG's HIGHEST damage handlers to measure the completed calculation.
        trainingManager.start();
        dungeonManager.start();
        stockManager.start();
        furnitureManager.start();
        hallManager.start();

        RelicAndFoodListener relicListener = new RelicAndFoodListener(
                this, items, churuManager,
                portalManager::startChanneledRecall
        );
        relicListener.startFriendshipAuraTask();
        relicListener.setBookmarkPouch(rpgManager.getPouch());

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
        pm.registerEvents(rewardMailbox, this);
        pm.registerEvents(guidebookManager, this);
        pm.registerEvents(travelManager, this);
        pm.registerEvents(utilityManager, this);

        // Facility commands share one physical landing/wayfinding catalogue.
        bindCmd("yuumi", this);
        bindCmd("churu", churuManager);
        bindCmd("altar", this);
        bindCmd("casino", this);
        bindCmd("duelbet", casinoManager);
        bindCmd("quest", this);
        bindCmd("codex", this);
        bindCmd("land", this);
        bindCmd("raid", this);
        bindCmd("party", dungeonManager.getPartyManager());
        bindCmd("titles", hallManager);
        bindCmd("job", this);
        for (String name : java.util.List.of("guide", "home", "workshop", "way", "shops", "bank", "smith", "dungeon", "townhall", "onsen", "arena")) bindCmd(name, this);
        bindCmd("stock", this);

        // Post-boot world & Vault hook
        Bukkit.getScheduler().runTaskLater(this, () -> {
            churuManager.hookVault();
            hubManager.ensureCityBuilt();
            portalManager.igniteUnlockedPortals();
            plotManager.refreshAllPlotSigns();
            hallManager.refresh();
            stockManager.refreshBoard();
        }, 40L);

        // Live Broadcast-Style Actionbar HUD every 2.5 seconds
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            int phase = altarManager.getCurrentPhase();
            double jackpot = casinoManager.getJackpot();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (trainingManager.isPracticing(p) || rpgManager.holdingSignature(p)) continue;
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
        getLogger().info("  🐱 [유미의 97랜드] YuumiLandCore 활성화 완료!");
        getLogger().info("  전용 평화 허브(yuumi_hub) · 12필지 분양 · 2F 카지노/경묘 준비됨");
        getLogger().info("==========================================================");
    }

    @Override
    public void onDisable() {
        if (wayfindingManager != null) wayfindingManager.stop();
        if (utilityManager != null) utilityManager.closeAll();
        if (trainingManager != null) trainingManager.stop();
        if (dungeonManager != null) dungeonManager.cleanup();
        if (rpgManager != null) rpgManager.stop();
        if (stockManager != null) stockManager.stop();
        if (furnitureManager != null) furnitureManager.stop();
        if (hallManager != null) hallManager.stop();
        if (catRaceManager != null) catRaceManager.cleanup();
        if (raidManager != null) raidManager.cleanup();
        if (packServer != null) packServer.stop();
        saveDataFile();
    }

    private void handleHubNpcInteract(Player player, String role) {
        switch (role) {
            case "workshop_smith", "workshop_vault", "workshop_craft" -> utilityManager.openNpc(player, role);
            case "class_master" -> rpgManager.openClassGui(player);
            case "dungeon_gate" -> dungeonManager.openGateGui(player);
            case "stock_broker" -> stockManager.openBroker(player);
            case "gacha" -> furnitureManager.openGacha(player);
            case "hall_of_fame" -> hallManager.openHall(player);
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
            java.nio.file.Path target = dataFile.toPath();
            java.nio.file.Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            java.nio.file.Files.writeString(temp, dataConfig.saveToString(), java.nio.charset.StandardCharsets.UTF_8);
            try {
                java.nio.file.Files.move(temp, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                java.nio.file.Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    private void normalizeInitializationKeys() {
        var players = dataConfig.getConfigurationSection("players");
        if (players == null) return;
        for (String id : players.getKeys(false)) {
            String root = "players." + id;
            if (dataConfig.getBoolean(root + ".starter_kit_v2")) dataConfig.set(root + ".initialized", true);
            dataConfig.set(root + ".starter_kit_v2", null);
        }
        saveDataFile();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (churuManager.initializePlayer(player)) {
            rewardMailbox.enqueueOnce(player.getUniqueId(), "starter-bookmarks", items.recallBookmark(3));
            rewardMailbox.enqueueOnce(player.getUniqueId(), "starter-churu", items.churuSalmon(5));
            rewardMailbox.enqueueOnce(player.getUniqueId(), "starter-food", items.zoomiesSkewer(3));
            rewardMailbox.deliver(player);
            player.teleport(hubManager.getHubSpawn());
            Bukkit.broadcast(TextUtil.msg("🐱 <white><bold>" + player.getName() + "</bold></white>님이 유미의 97랜드에 도착했습니다!"));
        }
        guidebookManager.normalize(player);
        player.sendMessage(TextUtil.msg("<gold>유미의 97랜드</gold>에 오신 것을 환영합니다! <yellow>/가이드</yellow> · 허브 시설 즉시 워프 · <aqua>가이드북 Shift+우클릭</aqua> 귀환"));
        player.sendMessage(TextUtil.mm("<gray>전투·생활 직업을 함께 성장시키세요. 성물 조작: <white>좌클릭 / 우클릭 / F</white> · 지인 직거래: Shift+우클릭"));
    }

    public void openMainMenu(Player player) { guidebookManager.open(player); }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH)
    public void onReservedMaterialUse(org.bukkit.event.player.PlayerInteractEvent event) {
        String id=items.getCustomId(event.getItem());
        if(id==null || !java.util.Set.of("ancient_cat_coin","respec_potion","labyrinth_key","golden_catnip").contains(id))return;
        if(event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_AIR && event.getAction()!=org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)return;
        event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
        if(id.equals("respec_potion") && event.getHand()==org.bukkit.inventory.EquipmentSlot.HAND)
            event.getPlayer().sendActionBar(TextUtil.mm("<yellow>망각 물약은 성향 교관에게 직업을 변경할 때 사용됩니다."));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equals("verify-core") && (!(sender instanceof Player) || sender.isOp())) {
            try {
                if (!dungeonManager.validateReadiness().isEmpty()) throw new IllegalStateException(dungeonManager.diagnosticSummary());
                sender.sendMessage(kr.yuumi97.core.util.CoreRuntimeChecks.run(this,items,rpgManager,hubManager,dataConfig,dungeonManager));
                sender.sendMessage(dungeonManager.diagnosticSummary());
            }
            catch (Exception failure) { getLogger().log(java.util.logging.Level.SEVERE,"CORE_RUNTIME_CHECKS FAILED",failure); sender.sendMessage("CORE_RUNTIME_CHECKS FAILED: "+failure.getMessage()); }
            return true;
        }
        if (!(sender instanceof Player p)) return true;
        if (args.length > 0 && args[0].equals("보상")) { rewardMailbox.deliver(p); return true; }
        if (command.getName().equals("guide")) { guidebookManager.open(p); return true; }
        if (travelManager.handleCommand(p, command.getName())) return true;

        if (args.length == 0) {
            openMainMenu(p);
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("책") || sub.equals("book")) {
            guidebookManager.give(p);
            return true;
        }

        if (sub.equals("setup-hub") && p.isOp()) {
            World hub = hubManager.initHubWorld();
            hubManager.buildEntireHubCity(hub);
            hallManager.refresh();
            stockManager.refreshBoard();
            portalManager.igniteUnlockedPortals();
            plotManager.refreshAllPlotSigns();
            p.teleport(hubManager.getHubSpawn());
            p.sendMessage(TextUtil.msg("<green>[관리자] 공공 허브·대공방·광장·카지노·수련장이 재구축되었습니다!"));
            return true;
        }

        if (sub.equals("props") && p.isOp()) {
            hubManager.refreshCustom3DProps();
            p.sendMessage(TextUtil.msg("<green>[관리자] 리소스팩 내 커스텀 3D 오브젝트(ItemDisplay) 스캔 및 배치를 갱신했습니다!"));
            return true;
        }

        if (sub.equals("give") && p.isOp() && args.length >= 2) {
            String key = args[1].toLowerCase();
            ItemStack gift = YuumiItems.CUSTOM_IDS.contains(key) ? items.create(key) : switch (key) {
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
            rewardMailbox.give(p, gift);
            p.sendMessage(TextUtil.msg("[관리자] 커스텀 아이템 지급 완료: <yellow>" + key + "</yellow>"));
            return true;
        }

        openMainMenu(p);
        return true;
    }

    public RpgJobManager rpg() { return rpgManager; }
    public DungeonManager dungeon() { return dungeonManager; }
    public HubWorldManager hub() { return hubManager; }
    public HubTravelManager travel() { return travelManager; }
    public GuidebookManager guidebook() { return guidebookManager; }
    public FileConfiguration progression() { return dataConfig; }
    public YuumiItems items() { return items; }
    public PlotManager plots() { return plotManager; }
    public BlacksmithManager forge() { return blacksmithManager; }

}
