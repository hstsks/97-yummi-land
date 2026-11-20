package kr.yuumi97.core.hub;

import kr.yuumi97.core.plot.PlotManager;
import kr.yuumi97.core.plot.PlotManager.PlotSpec;
import kr.yuumi97.core.util.TextUtil;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.Lootable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockVector;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.InputStream;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.zip.ZipFile;

/**
 * 🏰 유미의 97랜드 전용 평화 허브 도시 월드 매니저 (yuumi_hub)
 *
 * 십자형 5대 구역 + 큐레이션 오픈소스 .nbt 건축물 스탬핑 + 2층 황금 냥냥 카지노 홀 +
 * 4대 차원 대포탈 성소 + 노천 온천/응급 치유소 + 남쪽 벚꽃 대교 너머 12필지 분양 단지를 총괄합니다.
 */
public final class HubWorldManager implements Listener {

    public static final String HUB_WORLD_NAME = "yuumi_hub";
    public static final int GROUND_Y = 64;

    private final JavaPlugin plugin;
    private final FileConfiguration data;
    private final Runnable saveCallback;
    private final BiConsumer<Player, String> npcActionRouter;
    private PlotManager plotManager;

    private final NamespacedKey npcRoleKey;
    private final NamespacedKey hubDecorKey;
    private final NamespacedKey propModelKey;
    private final NamespacedKey propHologramKey;
    private final NamespacedKey statueBlocksKey;

    private World hubWorld;
    private boolean rouletteSpinning;
    private boolean rebuilding;
    private final HubLayoutBuilder layout;
    private Runnable trainingDummyReconciler = () -> {};
    private BiConsumer<Player,Boolean> guidebookInteraction = (player,receive) -> {};
    private final Map<UUID, Map<String, Integer>> npcClickTicks = new HashMap<>();

    private static final List<String> CURATED_STRUCTURES = List.of(
            "jp_gazebo_1", "jp_park_1", "jp_park_2", "jp_planter", "jp_lamp", "jp_gate_1",
            "jp_small_farm_1", "jp_shepherd_1", "jp_fisher_1", "jp_weaponsmith_1", "jp_armorer_1",
            "jp_cartographer_1", "jp_leatherworker_1", "jp_medium_house_1", "jp_small_house_1",
            "jp_small_house_2", "jp_temple", "oriental_pagoda", "harbor_ship", "lighthouse_house");

    public HubWorldManager(JavaPlugin plugin, FileConfiguration data, Runnable saveCallback,
                           BiConsumer<Player, String> npcActionRouter) {
        this.plugin = plugin;
        this.data = data;
        this.saveCallback = saveCallback;
        this.npcActionRouter = npcActionRouter;
        this.npcRoleKey = new NamespacedKey(plugin, "hub_npc_role");
        this.hubDecorKey = new NamespacedKey(plugin, "hub_decor_tag");
        this.propModelKey = new NamespacedKey(plugin, "hub_3d_prop_id");
        this.propHologramKey = new NamespacedKey(plugin, "hub_prop_hologram");
        this.statueBlocksKey = new NamespacedKey(plugin, "hub_statue_replaced_blocks");
        this.layout = new HubLayoutBuilder(this, plugin);
    }

    public void setPlotManager(PlotManager plotManager) {
        this.plotManager = plotManager;
    }

    public void setTrainingDummyReconciler(Runnable callback) {
        trainingDummyReconciler = callback == null ? () -> {} : callback;
    }

    public void setGuidebookInteraction(BiConsumer<Player,Boolean> callback) { guidebookInteraction=callback; }
    public String layoutDescription() { return layout.description(); }

    // ==================== 1. 전용 평화 허브 월드 생성기 (yuumi_hub) ====================

    public World initHubWorld() {
        World existing = Bukkit.getWorld(HUB_WORLD_NAME);
        if (existing != null) {
            this.hubWorld = existing;
            applyPeacefulRules(existing);
            return existing;
        }

        WorldCreator creator = new WorldCreator(HUB_WORLD_NAME)
                .generator(new HubChunkGenerator())
                .generateStructures(false);
        this.hubWorld = Bukkit.createWorld(creator);
        if (this.hubWorld != null) {
            applyPeacefulRules(this.hubWorld);
            plugin.getLogger().info("[HubWorldManager] 전용 평화 허브 도시 월드(yuumi_hub) 로드 완료!");
        }
        return this.hubWorld;
    }

    private void applyPeacefulRules(World w) {
        w.setDifficulty(Difficulty.NORMAL); // Allows Raid Boss entities in Northern Arena while natural mob spawning is disabled!
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        w.setGameRule(GameRule.FALL_DAMAGE, false);
        w.setGameRule(GameRule.KEEP_INVENTORY, true);
        w.setTime(4500L);
        w.setStorm(false);
        w.setThundering(false);
        w.setSpawnLocation(0, GROUND_Y + 1, 12);
    }

    public World getHubWorld() {
        if (hubWorld == null) {
            hubWorld = Bukkit.getWorld(HUB_WORLD_NAME);
        }
        return hubWorld;
    }

    public Location getHubSpawn() {
        World w = getHubWorld();
        if (w == null) w = Bukkit.getWorlds().get(0);
        return new Location(w, 0.5, GROUND_Y + 1.0, 12.5, 180f, 0f);
    }

    public Location getArenaCenter() {
        World w = getHubWorld();
        if (w == null) w = Bukkit.getWorlds().get(0);
        return new Location(w, 0.5, GROUND_Y + 1.0, -212.5, 0f, 0f);
    }

    public Location getClinicRespawnLocation() {
        World w = getHubWorld();
        if (w == null) w = Bukkit.getWorlds().get(0);
        return new Location(w, 65.5, GROUND_Y + 1.2, 8.5, 180f, 0f);
    }

    public Location getDungeonGateLocation() {
        return new Location(getHubWorld(), 18.5, 66.0, -55.5, 0f, 0f);
    }

    public Location getClassMasterLocation() {
        return new Location(getHubWorld(), -14.5, 65.0, -10.5, 0f, 0f);
    }

    public Location getUtilityHallLocation() { return new Location(getHubWorld(),-21.5,65,-18.5,180,0); }
    public Location getGuidebookKioskLocation() { return new Location(getHubWorld(),8.5,66,8.5,0,0); }
    public Location getHolyWaterLocation() { return new Location(getHubWorld(),-21.5,65,-31.5); }
    public Location getTrashLocation() { return new Location(getHubWorld(),-10.5,65,-24.5); }

    public Location getStockBrokerLocation() {
        return new Location(getHubWorld(), 52.5, 72.0, -33.0, 0f, 0f);
    }

    public Location getHallOfFameLocation() {
        return new Location(getHubWorld(), 14.5, 65, -27.5, 0f, 0f);
    }

    public Location getGachaLocation() {
        return new Location(getHubWorld(), 22.5, 65, -27.5, 0f, 0f);
    }

    public Location getHallBoardLocation(int category) {
        if (category < 0 || category > 2) throw new IllegalArgumentException("Hall category must be 0..2");
        return new Location(getHubWorld(), 10.5 + category * 4, 69.5, -29.0);
    }

    public boolean isOnsenWater(Location loc) {
        World w = getHubWorld();
        if (loc == null || w == null || !w.equals(loc.getWorld())) return false;
        int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
        return x >= 38 && x <= 52 && z >= 11 && z <= 25 && y >= 63 && y <= 67;
    }

    public String describeDistrict(Location loc) {
        World w = getHubWorld();
        if (loc == null || loc.getWorld() == null) return "미지의 공간";
        if (w == null || !w.equals(loc.getWorld())) {
            return switch (loc.getWorld().getEnvironment()) {
                case NETHER -> "🔥 네더 원정지";
                case THE_END -> "🌌 엔드 원정지";
                default -> "🌲 오버월드 야생";
            };
        }
        int x = loc.getBlockX(), z = loc.getBlockZ();
        if (z <= -180) return "⚔ 레이드 결투장";
        if (z <= -42) return "🌌 차원 대포탈 성소";
        if (z >= 52) {
            if (plotManager != null) {
                PlotSpec ps = plotManager.getPlotAt(loc);
                if (ps != null) return "🏡 " + ps.id() + "호 부지";
            }
            return "🏡 남쪽 분양 주거단지";
        }
        if (x >= 41 && x <= 85 && z >= -39 && z <= -7) {
            return loc.getBlockY() >= 70 ? "🎰 카지노 2F VIP" : "🎰 황금 냥냥 카지노";
        }
        if (isOnsenWater(loc)) return "♨ 사르르 캣닢 온천";
        if (x >= 35) return "🏮 동쪽 위락·온천가";
        if (x <= -95) return "🏛 서쪽 마을 회관";
        if (x <= -74) return "🏦 은행 · 대장간 거리";
        if (x <= -30) return "⚖ 전문 상점 거리";
        return "🏰 중앙 고양이 분수 광장";
    }

    /**
     * Custom Cherry-Blossom Island ChunkGenerator for yuumi_hub.
     */
    public static final class HubChunkGenerator extends ChunkGenerator {
        @Override
        public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
            int baseX = chunkX << 4;
            int baseZ = chunkZ << 4;

            for (int lx = 0; lx < 16; lx++) {
                int wx = baseX + lx;
                for (int lz = 0; lz < 16; lz++) {
                    int wz = baseZ + lz;
                    chunkData.setBlock(lx, 0, lz, Material.BEDROCK);

                    // Northern Raid Colosseum Islet around (0, -212)
                    double distArena = Math.hypot(wx, wz + 212);
                    if (distArena <= 28.5) {
                        for (int y = 1; y <= 60; y++) chunkData.setBlock(lx, y, lz, Material.DEEPSLATE);
                        for (int y = 61; y <= 63; y++) chunkData.setBlock(lx, y, lz, Material.COBBLED_DEEPSLATE);
                        chunkData.setBlock(lx, GROUND_Y, lz, distArena <= 24.5 ? Material.POLISHED_DEEPSLATE : Material.TUFF);
                        continue;
                    }

                    // Mainland Cat Hub Island: X in [-142..142], Z in [-96..218] with soft rounded corners
                    double nx = Math.max(0.0, Math.abs(wx) - 122.0) / 22.0;
                    double nz = wz < -76 ? (-76.0 - wz) / 22.0 : (wz > 198 ? (wz - 198.0) / 22.0 : 0.0);
                    double edgeMetric = Math.hypot(nx, nz);

                    if (edgeMetric <= 1.0) {
                        for (int y = 1; y <= 59; y++) chunkData.setBlock(lx, y, lz, Material.STONE);
                        for (int y = 60; y <= 63; y++) chunkData.setBlock(lx, y, lz, Material.DIRT);
                        chunkData.setBlock(lx, GROUND_Y, lz, edgeMetric > 0.86 ? Material.SAND : Material.GRASS_BLOCK);
                    } else {
                        // Calm turquoise surrounding sea
                        for (int y = 1; y <= 58; y++) chunkData.setBlock(lx, y, lz, Material.STONE);
                        chunkData.setBlock(lx, 59, lz, Material.PRISMARINE);
                        chunkData.setBlock(lx, 60, lz, Material.SAND);
                        for (int y = 61; y <= 63; y++) chunkData.setBlock(lx, y, lz, Material.WATER);
                    }
                }
            }
        }

        @Override
        public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
            return new BiomeProvider() {
                @Override
                public Biome getBiome(WorldInfo info, int x, int y, int z) {
                    return Biome.CHERRY_GROVE;
                }
                @Override
                public List<Biome> getBiomes(WorldInfo info) {
                    return List.of(Biome.CHERRY_GROVE);
                }
            };
        }
    }

    // ==================== 2. 도시 전체 자동 건설 및 오픈소스 .nbt 스탬핑 ====================

    public void ensureCityBuilt() {
        World hub = getHubWorld();
        if (hub == null) return;

        if (!layout.sourceFingerprint().equals(data.getString("hub.blueprint_hash", ""))) {
            buildEntireHubCity(hub);
        } else {
            refreshCustom3DProps();
        }
        plugin.getLogger().info("[HubWorldManager] CORE_HUB_READY");
    }

    public void buildEntireHubCity(World hub) {
        if (rebuilding) return;
        rebuilding = true;
        try {
            layout.beginBuild();
            data.set("hub.blueprint_hash", null);
            saveCallback.run();
            plugin.getLogger().info("[HubWorldManager] Rebuilding public city; owned plot columns remain protected.");
            buildPublicCity(hub, true);
            data.set("hub.blueprint_hash", layout.appliedFingerprint());
            data.set("hub_v2", null);
            data.set("hub_v3", null);
            data.set("hub_v3_1", null);
            saveCallback.run();
            plugin.getLogger().info("[HubWorldManager] Public city blueprint applied; guild hall, kiosk and facilities ready.");
        } finally {
            rebuilding = false;
        }
    }

    private void buildPublicCity(World hub, boolean includeSouth) {
        if (hub == null) throw new IllegalStateException("Hub world is not loaded");
        validateCuratedStructures();
        // Persistent NPCs can be in unloaded chunks when an administrator rebuilds.
        loadPublicChunks(hub);
        clearTaggedHubEntities(hub);
        layout.clearOldTrees(hub);

        // 1) Central Cat Fountain Plaza & 4 Cardinal Boulevards
        buildCentralPlazaAndBoulevards(hub);

        // 2) North: Threshold Altar & 4-Gate Portal Sanctuary + Raid Colosseum
        buildNorthPortalSanctuary(hub);
        buildRaidColosseum(hub);

        // 3) West: 5 Specialized Merchant Carts, Churu Bank, Blacksmith & Lv.30 Library, Town Hall
        buildWestMarketAndCivicDistrict(hub);

        // 4) East: 2F Golden Paws Casino Hall (+ Live Cat Derby Track) + Onsen/Diner/Clinic + Tavern & Fishing Pier
        buildGoldenPawsCasinoHall(hub);
        buildEastOnsenTavernAndPier(hub);

        // 5) South: Scenic Canal, Cherry Arch Bridge & 12-Plot Residential District
        if (includeSouth) buildSouthBridgeAndPlotDistrict(hub);

        // Fill public spaces without crossing owned plot columns.
        buildPlazaDistrict(hub);
        buildMarketDistrict(hub);
        buildSanctuaryDistrict(hub);
        buildCasinoDistrict(hub);
        buildHarborDistrict(hub);
        layout.buildPlaza(hub);

        // 6) Spawn all Interactive Cat NPCs & Floating Holograms
        spawnAllHubNpcs(hub);

        // 7) Auto-mount any 3D JSON Props authored by Codex in Yuumi97Pack.zip
        refreshCustom3DProps();
    }

    private void loadPublicChunks(World hub) {
        for (int cx = -8; cx <= 9; cx++) {
            for (int cz = -5; cz <= 3; cz++) hub.getChunkAt(cx, cz).load();
        }
        for (int cx = -2; cx <= 1; cx++) {
            for (int cz = -15; cz <= -12; cz++) hub.getChunkAt(cx, cz).load();
        }
    }

    private void validateCuratedStructures() {
        for (String id : CURATED_STRUCTURES) {
            try (InputStream in = plugin.getResource("structures/v3/" + id + ".nbt")) {
                if (in == null) throw new IllegalStateException("Missing required hub structure: " + id);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Cannot read hub structure: " + id, e);
            }
        }
    }

    private void buildPlazaDistrict(World hub) {
        // Old podium/booth fragments extended outside the circular plaza; remove the complete legacy footprint.
        for (int x = 9; x <= 24; x++) for (int z = -23; z <= -9; z++) clearColumn(hub, x, 65, 80, z);
        stampCuratedCentered(hub, "jp_gazebo_1", -14, 64, -14, StructureRotation.NONE);
        // A shallow reflecting pond beside the school gazebo, outside the main crossroad.
        for (int x = -24; x <= -21; x++) {
            for (int z = -10; z <= -6; z++) {
                publicBlock(hub, x, 63, z, Material.PRISMARINE_BRICKS);
                publicBlock(hub, x, 64, z,
                        x == -24 || x == -21 || z == -10 || z == -6 ? Material.MOSSY_STONE_BRICKS : Material.WATER);
            }
        }
        clearNpcPad(getClassMasterLocation(), 64);

        stampCuratedCentered(hub, "jp_planter", -23, 65, 14, StructureRotation.NONE);
        buildCafeParasol(hub, -16, 13, Material.PINK_WOOL);
        buildCafeParasol(hub, -11, 20, Material.LIGHT_BLUE_WOOL);
        for (int z : new int[]{11, 15, 18, 22}) {
            publicBlock(hub, -19, 65, z, Material.CHERRY_STAIRS);
            publicBlock(hub, -8, 65, z, Material.CHERRY_STAIRS);
        }
        spawnHolo(hub, new Location(hub, -14.5, 70.4, 17.5),
                "<#ff85c0><bold>☕ 사르르 직거래 카페</bold></#ff85c0>\n<white>집사를 Shift+우클릭하여 안전 거래</white>");

        stampCuratedCentered(hub, "jp_park_1", 15, 64, 14, StructureRotation.NONE);
        // Decorative cats are persistent town residents, separate from the interactive NPCs.
        for (int[] point : new int[][]{{12, 12}, {18, 18}, {21, 11}}) {
            Location loc = new Location(hub, point[0] + 0.5, 65, point[1] + 0.5);
            clearNpcPad(loc, 64);
            Cat cat = hub.spawn(loc, Cat.class);
            cat.setCatType(Cat.Type.CALICO);
            cat.setAI(false);
            cat.setSitting(true);
            cat.setInvulnerable(true);
            cat.setPersistent(true);
            cat.setRemoveWhenFarAway(false);
            cat.getPersistentDataContainer().set(hubDecorKey, PersistentDataType.BYTE, (byte) 1);
        }
    }

    private void buildCafeParasol(World hub, int cx, int cz, Material wool) {
        publicBlock(hub, cx, 65, cz, Material.CHERRY_FENCE);
        publicBlock(hub, cx, 66, cz, Material.CHERRY_PRESSURE_PLATE);
        publicBlock(hub, cx, 67, cz, Material.CHERRY_FENCE);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 3) publicBlock(hub, cx + dx, 68, cz + dz, wool);
            }
        }
        publicBlock(hub, cx, 69, cz, Material.LANTERN);
    }

    private void buildMarketDistrict(World hub) {
        // Remove all five former carts before stamping larger stores; the avenue remains open.
        pavePlazaRect(hub, -74, -30, -21, -6, Material.SMOOTH_STONE, Material.CHERRY_PLANKS);
        pavePlazaRect(hub, -69, -31, 6, 21, Material.SMOOTH_STONE, Material.CHERRY_PLANKS);
        stampCuratedCentered(hub, "jp_shepherd_1", -38, 64, -14, StructureRotation.NONE);
        stampCuratedCentered(hub, "jp_fisher_1", -53, 64, -14, StructureRotation.NONE);
        stampCuratedCentered(hub, "jp_weaponsmith_1", -68, 64, -14, StructureRotation.NONE);
        stampCuratedCentered(hub, "jp_cartographer_1", -44, 64, 13, StructureRotation.CLOCKWISE_180);
        stampCuratedCentered(hub, "jp_leatherworker_1", -61, 64, 14, StructureRotation.CLOCKWISE_180);
        for (int x : new int[]{-38, -53, -68}) buildShopAwning(hub, x, -7, Material.PINK_WOOL);
        for (int x : new int[]{-44, -61}) buildShopAwning(hub, x, 7, Material.LIGHT_BLUE_WOOL);

        stampCuratedCentered(hub, "jp_medium_house_1", -44, 64, -30, StructureRotation.NONE);
        stampCuratedCentered(hub, "jp_small_house_1", -59, 64, -29, StructureRotation.NONE);
        stampCuratedCentered(hub, "jp_small_house_2", -72, 64, -29, StructureRotation.NONE);
        stampCuratedCentered(hub, "jp_armorer_1", -73, 64, 29, StructureRotation.CLOCKWISE_180);
        stampCuratedCentered(hub, "jp_park_2", -56, 64, 28, StructureRotation.NONE);
        stampCuratedCentered(hub, "jp_small_farm_1", -43, 64, 28, StructureRotation.NONE);
        for (int x : new int[]{-33, -49, -65, -90}) {
            stampCuratedCentered(hub, "jp_lamp", x, 65, -5, StructureRotation.NONE);
            stampCuratedCentered(hub, "jp_lamp", x, 65, 5, StructureRotation.NONE);
        }
        // Suspended garlands have five blocks of clear headroom over both sidewalks.
        for (int x = -72; x <= -30; x++) {
            publicBlock(hub, x, 71, -4, Material.CHAIN);
            publicBlock(hub, x, 71, 4, Material.CHAIN);
            if ((x + 72) % 5 == 0) {
                publicBlock(hub, x, 70, -4, Material.LANTERN);
                publicBlock(hub, x, 70, 4, Material.SOUL_LANTERN);
            }
        }
        for (int x : new int[]{-74, -30}) {
            for (int z : new int[]{-4, 4}) {
                for (int y = 65; y <= 71; y++) publicBlock(hub, x, y, z, Material.CHERRY_FENCE);
            }
        }
    }

    private void buildShopAwning(World hub, int cx, int z, Material wool) {
        for (int x = cx - 3; x <= cx + 3; x++) {
            publicBlock(hub, x, 68, z, x % 2 == 0 ? wool : Material.WHITE_WOOL);
        }
        for (int x : new int[]{cx - 3, cx + 3}) {
            for (int y = 65; y <= 67; y++) publicBlock(hub, x, y, z, Material.CHERRY_FENCE);
        }
        publicBlock(hub, cx - 2, 65, z, Material.BARREL);
        publicBlock(hub, cx + 2, 65, z, Material.CHEST);
    }

    private void buildSanctuaryDistrict(World hub) {
        // The large pagoda is offset two blocks west so its 27-block footprint clears the portal terrace.
        stampCuratedCentered(hub, "oriental_pagoda", -48, 64, -66, StructureRotation.NONE);
        stampCuratedCentered(hub, "jp_temple", 46, 64, -66, StructureRotation.NONE);
        stampCuratedCentered(hub, "jp_gate_1", -48, 65, -49, StructureRotation.CLOCKWISE_90);
        for (int x = -48; x <= -34; x++) {
            for (int z = -52; z <= -50; z++) publicBlock(hub, x, 64, z, Material.QUARTZ_BRICKS);
        }
        for (int x = 34; x <= 46; x++) {
            for (int z = -55; z <= -53; z++) publicBlock(hub, x, 64, z, Material.QUARTZ_BRICKS);
        }
        for (int x = 44; x <= 48; x++) {
            for (int z = -61; z <= -53; z++) publicBlock(hub, x, 64, z, Material.QUARTZ_BRICKS);
        }
        // A separate assembly arch is before the dimension gates, never in their trigger volumes.
        for (int x = 14; x <= 22; x++) {
            for (int z = -60; z <= -53; z++) publicBlock(hub, x, 65, z, Material.POLISHED_DEEPSLATE);
        }
        for (int y = 66; y <= 71; y++) {
            publicBlock(hub, 15, y, -59, Material.DEEPSLATE_TILE_WALL);
            publicBlock(hub, 21, y, -59, Material.DEEPSLATE_TILE_WALL);
        }
        for (int x = 15; x <= 21; x++) publicBlock(hub, x, 72, -59, Material.CRYING_OBSIDIAN);
        publicBlock(hub, 18, 73, -59, Material.PEARLESCENT_FROGLIGHT);
        clearNpcPad(getDungeonGateLocation(), 65);
        spawnHolo(hub, new Location(hub, 18.5, 74.8, -59),
                "<light_purple><bold>🗝 문턱의 미궁 집결 아치</bold></light_purple>\n<white>1~8인 파티 · 세 가지 차원의 시련</white>");
    }

    private void buildCasinoDistrict(World hub) {
        // The 2F stock lounge is on the north balcony, outside the racing atrium and slot rows.
        for (int x = 46; x <= 58; x++) {
            for (int z = -35; z <= -29; z++) {
                publicBlock(hub, x, 70, z, (x + z) % 2 == 0 ? Material.BLUE_WOOL : Material.BLACK_CONCRETE);
                clearColumn(hub, x, 71, 74, z);
            }
        }
        for (int x = 49; x <= 55; x++) publicBlock(hub, x, 71, -32, Material.BLUE_CARPET);
        spawnHolo(hub, new Location(hub, 52.5, 78.0, -34.5),
                "<green><bold>📈 츄르 증권 거래소</bold></green>\n<white>4대 예능 종목 · 10분 시세 갱신</white>");
        layout.buildCasino(hub);
        clearNpcPad(getStockBrokerLocation(), 71);
    }

    private void buildHarborDistrict(World hub) {
        for (int x = 119; x <= 146; x++) {
            for (int z = 12; z <= 38; z++) {
                clearColumn(hub, x, 64, 88, z);
                publicBlock(hub, x, 60, z, Material.PRISMARINE);
                for (int y = 61; y <= 63; y++) publicBlock(hub, x, y, z, Material.WATER);
            }
        }
        stampCuratedCentered(hub, "harbor_ship", 129, 63, 27, StructureRotation.NONE);
        stampCuratedCentered(hub, "lighthouse_house", 114, 64, 42, StructureRotation.NONE);
        for (int x = 112; x <= 116; x++) {
            for (int z = 32; z <= 35; z++) publicBlock(hub, x, 64, z, Material.SPRUCE_PLANKS);
        }
        // A gangplank reaches the small sailing vessel without moving the original fishing pier.
        for (int x = 118; x <= 126; x++) {
            for (int z = 25; z <= 26; z++) publicBlock(hub, x, 64, z, Material.SPRUCE_SLAB);
        }
        buildLanternPost(hub, 117, 65, 17);
        buildLanternPost(hub, 117, 65, 33);
        spawnHolo(hub, new Location(hub, 123.5, 68.5, 20.5),
                "<aqua><bold>⛵ 별빛 항구와 고양이 등대</bold></aqua>\n<white>낚시 부두 · 정박 범선 · 해안 산책로</white>");
    }

    void publicBlock(World world, int x, int y, int z, Material material) {
        if (isOwnedColumn(world, x, z)) return;
        Block block = world.getBlockAt(x, y, z);
        var blockData = material.createBlockData();
        if (blockData instanceof Leaves leaves) leaves.setPersistent(true);
        block.setBlockData(blockData, false);
    }

    private void clearNpcPad(Location location, int floorY) {
        if (location.getWorld() == null) return;
        int x = location.getBlockX(), z = location.getBlockZ();
        Material floor = location.getWorld().getBlockAt(x, floorY, z).getType();
        if (floor != Material.GOLD_BLOCK && floor != Material.CHISELED_QUARTZ_BLOCK) {
            publicBlock(location.getWorld(), x, floorY, z, Material.QUARTZ_BRICKS);
        }
        clearColumn(location.getWorld(), x, floorY + 1, floorY + 3, z);
    }

    void stampCuratedCentered(World world, String id, int cx, int y, int cz, StructureRotation rotation) {
        String file = "v3/" + id + ".nbt";
        try (InputStream in = plugin.getResource("structures/" + file)) {
            if (in == null) throw new IllegalStateException("Missing required structure: " + file);
            BlockVector size = Bukkit.getStructureManager().loadStructure(in).getSize();
            int[] offsets = rotatedFootprint(size.getBlockX(), size.getBlockZ(), rotation);
            int minX = cx - (offsets[1] - offsets[0] + 1) / 2;
            int minZ = cz - (offsets[3] - offsets[2] + 1) / 2;
            int maxX = minX + offsets[1] - offsets[0], maxZ = minZ + offsets[3] - offsets[2];
            if (intersectsOwnedColumns(world, minX, maxX, minZ, maxZ)) {
                throw new IllegalStateException("Public structure overlaps an owned plot: " + file);
            }
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) clearColumn(world, x, Math.max(y + 1, 65), y + size.getBlockY(), z);
            }
            if (!stampStructure(world, file, minX - offsets[0], y, minZ - offsets[2], rotation)) {
                throw new IllegalStateException("Failed required hub structure: " + file);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot load structure " + file, e);
        }
    }

    // ==================== 구역 1: 중앙 고양이 분수 광장 & 십자 대로 ====================

    private void buildCentralPlazaAndBoulevards(World hub) {
        // Circular Central Plaza (radius 26 around (0, 64, 0))
        for (int dx = -27; dx <= 27; dx++) {
            for (int dz = -27; dz <= 27; dz++) {
                double dist = Math.hypot(dx, dz);
                if (dist > 26.5) continue;

                clearColumn(hub, dx, GROUND_Y + 1, GROUND_Y + 20, dz);
                Material floor;
                if (dist <= 5.5) {
                    floor = dist <= 4.2 ? Material.PRISMARINE_BRICKS : Material.CHISELED_QUARTZ_BLOCK;
                } else if (Math.abs(dist - 14.0) < 1.1 || Math.abs(dist - 24.5) < 1.2) {
                    floor = Material.CHERRY_PLANKS;
                } else if ((Math.abs(dx) + Math.abs(dz)) % 7 == 0) {
                    floor = Material.SMOOTH_QUARTZ;
                } else {
                    floor = Material.QUARTZ_BRICKS;
                }
                hub.getBlockAt(dx, GROUND_Y, dz).setType(floor, false);

                if ((Math.abs(dx) == 10 && Math.abs(dz) == 10) || (Math.abs(dx) == 18 && Math.abs(dz) == 0) || (Math.abs(dx) == 0 && Math.abs(dz) == 18)) {
                    hub.getBlockAt(dx, GROUND_Y, dz).setType(Material.SEA_LANTERN, false);
                }
            }
        }

        // Central Golden Cat & Threshold Fountain (0, 65..71, 0)
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                double d = Math.hypot(dx, dz);
                if (d <= 4.3 && d >= 3.2) {
                    hub.getBlockAt(dx, GROUND_Y + 1, dz).setType(Material.QUARTZ_BRICKS, false);
                } else if (d < 3.2) {
                    hub.getBlockAt(dx, GROUND_Y + 1, dz).setType(Material.WATER, false);
                }
            }
        }
        for (int y = GROUND_Y + 1; y <= GROUND_Y + 4; y++) {
            hub.getBlockAt(0, y, 0).setType(Material.QUARTZ_PILLAR, false);
        }
        hub.getBlockAt(0, GROUND_Y + 5, 0).setType(Material.GOLD_BLOCK, false);
        hub.getBlockAt(-1, GROUND_Y + 6, 0).setType(Material.PINK_CONCRETE, false); // Left cat ear
        hub.getBlockAt(1, GROUND_Y + 6, 0).setType(Material.PINK_CONCRETE, false);  // Right cat ear
        hub.getBlockAt(0, GROUND_Y + 6, 0).setType(Material.SEA_LANTERN, false);
        hub.getBlockAt(0, GROUND_Y + 2, 1).setType(Material.BELL, false);

        spawnHolo(hub, new Location(hub, 0.5, 77.0, 0.5),
                "<gradient:#ff85c0:#ffd666><bold>🐱 유미의 97랜드 중앙 광장</bold></gradient>\n"
                + "<white>북: 차원 대포탈 성소 │ 남: 12필지 분양 주거단지</white>\n"
                + "<yellow>서: 상점·은행·대장간·회관 │ 동: 2F 카지노·온천·선술집</yellow>");

        // West Boulevard (X = -27 .. -108, Z in [-5..5])
        paveBoulevardX(hub, -108, -27, -5, 5);
        // East Boulevard (X = +27 .. +112, Z in [-5..5])
        paveBoulevardX(hub, 27, 112, -5, 5);

        // Stamp street lamps & diagonal plaza cherry trees
        int[][] lampCoords = {{-16, -16}, {16, -16}, {-16, 16}, {16, 16}, {-38, 0}, {-70, 0}, {-96, 0}, {36, 0}, {63, 0}, {90, 0}};
        for (int[] lc : lampCoords) {
            if (lc[1] == 0) {
                buildLanternPost(hub, lc[0], GROUND_Y + 1, -5);
                buildLanternPost(hub, lc[0], GROUND_Y + 1, 5);
            } else {
                stampStructure(hub, "street_lamp.nbt", lc[0] - 1, GROUND_Y + 1, lc[1] - 1, StructureRotation.NONE);
            }
        }
    }

    private void paveBoulevardX(World hub, int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                clearColumn(hub, x, GROUND_Y + 1, GROUND_Y + 14, z);
                boolean border = (z == minZ || z == maxZ);
                boolean centerStripe = (z == 0 && Math.abs(x) % 4 != 0);
                Material m = border ? Material.CHERRY_PLANKS : (centerStripe ? Material.SMOOTH_QUARTZ : Material.QUARTZ_BRICKS);
                if (z == 0 && Math.abs(x) % 8 == 0) m = Material.SEA_LANTERN;
                hub.getBlockAt(x, GROUND_Y, z).setType(m, false);
            }
        }
    }

    // ==================== 구역 2: 북쪽 문턱의 서 제단 & 4대 대포탈 성소 + 결투장 ====================

    private void buildNorthPortalSanctuary(World hub) {
        // Remove the old off-centre torii remnant before the centred welcome route is stamped.
        for (int x = -11; x <= -7; x++) for (int z = -37; z <= -33; z++) clearColumn(hub, x, 65, 73, z);
        // Avenue & Grand Stairs from Z = -27 to Z = -45
        for (int z = -45; z <= -27; z++) {
            for (int x = -6; x <= 6; x++) {
                clearColumn(hub, x, GROUND_Y + 1, GROUND_Y + 16, z);
                hub.getBlockAt(x, GROUND_Y, z).setType(Math.abs(x) == 6 ? Material.CHERRY_PLANKS : Material.QUARTZ_BRICKS, false);
                if (z <= -43) {
                    hub.getBlockAt(x, GROUND_Y + 1, z).setType(z == -43 ? Material.QUARTZ_SLAB : Material.QUARTZ_BRICKS, false);
                }
            }
        }
        // Decorative Torii Gate at North Sanctuary approach
        stampStructure(hub, "torii_gate.nbt", 5, GROUND_Y + 1, -37, StructureRotation.CLOCKWISE_90);

        // Elevated Sanctuary Terrace: X in [-34..34], Z in [-76..-46], Floor Top Y = 65 (walkable Y = 66)
        for (int x = -34; x <= 34; x++) {
            for (int z = -76; z <= -46; z++) {
                clearColumn(hub, x, GROUND_Y + 1, GROUND_Y + 22, z);
                boolean edge = (Math.abs(x) == 34 || z == -76 || (z == -46 && Math.abs(x) > 6));
                hub.getBlockAt(x, GROUND_Y, z).setType(Material.STONE_BRICKS, false);
                Material top = edge ? Material.CHISELED_QUARTZ_BLOCK
                        : ((Math.abs(x) + Math.abs(z)) % 9 == 0 ? Material.SEA_LANTERN : Material.QUARTZ_BRICKS);
                hub.getBlockAt(x, GROUND_Y + 1, z).setType(top, false);
                if (edge && z < -46) {
                    hub.getBlockAt(x, GROUND_Y + 2, z).setType(Material.QUARTZ_SLAB, false);
                }
            }
        }

        // Threshold Altar Dais at (0, 66, -55)
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                hub.getBlockAt(dx, GROUND_Y + 1, -55 + dz).setType(Material.CRYING_OBSIDIAN, false);
            }
        }
        hub.getBlockAt(0, GROUND_Y + 2, -55).setType(Material.ENCHANTING_TABLE, false);
        hub.getBlockAt(-2, GROUND_Y + 2, -56).setType(Material.CHISELED_BOOKSHELF, false);
        hub.getBlockAt(2, GROUND_Y + 2, -56).setType(Material.CHISELED_BOOKSHELF, false);
        hub.getBlockAt(-2, GROUND_Y + 3, -56).setType(Material.AMETHYST_CLUSTER, false);
        hub.getBlockAt(2, GROUND_Y + 3, -56).setType(Material.AMETHYST_CLUSTER, false);

        // 4 Monumental Portal Frames along Z = -70 (Exact contract with PortalAndRecallManager!)
        // Gate 1: Overworld (X = -24, Z = -70)
        buildGrandGateFrame(hub, -24, -70, Material.MOSSY_STONE_BRICKS, Material.CHERRY_WOOD, Material.VERDANT_FROGLIGHT,
                "<green><bold>🌲 오버월드 야생 원정문</bold></green>\n<white>상시 점화 │ 통과 시 야생 대륙 출격</white>");
        // Gate 2: Nether (X = -8, Z = -70)
        buildGrandGateFrame(hub, -8, -70, Material.BLACKSTONE, Material.CRYING_OBSIDIAN, Material.SHROOMLIGHT,
                "<gold><bold>🔥 네더 차원 대포탈</bold></gold>\n<yellow>문턱의 서 [제2장] 달성 시 자동 점화!</yellow>");
        // Gate 3: The End (X = +8, Z = -70)
        buildGrandGateFrame(hub, 8, -70, Material.END_STONE_BRICKS, Material.PURPUR_PILLAR, Material.PEARLESCENT_FROGLIGHT,
                "<light_purple><bold>🌌 엔드 차원 대포탈</bold></light_purple>\n<yellow>문턱의 서 [제3장] 달성 시 자동 점화!</yellow>");
        // Gate 4: Raid Colosseum (X = +24, Z = -70)
        buildGrandGateFrame(hub, 24, -70, Material.POLISHED_DEEPSLATE, Material.RED_NETHER_BRICKS, Material.OCHRE_FROGLIGHT,
                "<red><bold>⚔ 월드보스 레이드 결투장</bold></red>\n<white>상시 점화 │ 1~3티어 협동 보스 토벌</white>");
    }

    private void buildGrandGateFrame(World hub, int cx, int cz, Material pillarMat, Material trimMat, Material lightMat, String holoText) {
        // Pillars at cx-2, cx+2 (Y = 66..70), Arch top at Y = 70..71, Curtain at cx-1..cx+1, Y = 66..69
        for (int y = 66; y <= 70; y++) {
            hub.getBlockAt(cx - 2, y, cz).setType(pillarMat, false);
            hub.getBlockAt(cx + 2, y, cz).setType(pillarMat, false);
            hub.getBlockAt(cx - 3, y, cz).setType(trimMat, false);
            hub.getBlockAt(cx + 3, y, cz).setType(trimMat, false);
        }
        for (int dx = -3; dx <= 3; dx++) {
            hub.getBlockAt(cx + dx, 70, cz).setType(pillarMat, false);
            if (Math.abs(dx) <= 2) {
                hub.getBlockAt(cx + dx, 71, cz).setType(trimMat, false);
            }
        }
        hub.getBlockAt(cx, 71, cz).setType(lightMat, false);
        spawnHolo(hub, new Location(hub, cx + 0.5, 72.6, cz + 0.5), holoText);
    }

    private void buildRaidColosseum(World hub) {
        int cx = 0, cz = -212, radius = 22;
        for (int dx = -radius - 2; dx <= radius + 2; dx++) {
            for (int dz = -radius - 2; dz <= radius + 2; dz++) {
                double dist = Math.hypot(dx, dz);
                if (dist > radius + 1.5) continue;
                int x = cx + dx, z = cz + dz;
                clearColumn(hub, x, GROUND_Y + 1, GROUND_Y + 24, z);

                if (dist <= radius - 1.5) {
                    Material m = (Math.abs(dist - 10.0) < 0.9 || Math.abs(dx) == Math.abs(dz))
                            ? Material.QUARTZ_BRICKS : Material.POLISHED_DEEPSLATE;
                    if (Math.abs(dx) % 7 == 0 && Math.abs(dz) % 7 == 0) m = Material.SEA_LANTERN;
                    hub.getBlockAt(x, GROUND_Y, z).setType(m, false);
                } else {
                    hub.getBlockAt(x, GROUND_Y, z).setType(Material.DEEPSLATE_BRICKS, false);
                    hub.getBlockAt(x, GROUND_Y + 1, z).setType(Material.DEEPSLATE_BRICKS, false);
                    hub.getBlockAt(x, GROUND_Y + 2, z).setType(Material.IRON_BARS, false);
                    hub.getBlockAt(x, GROUND_Y + 3, z).setType(Material.IRON_BARS, false);
                    if ((dx + dz) % 5 == 0) {
                        hub.getBlockAt(x, GROUND_Y + 4, z).setType(Material.DEEPSLATE_TILES, false);
                        hub.getBlockAt(x, GROUND_Y + 5, z).setType(Material.SOUL_LANTERN, false);
                    }
                }
            }
        }

        // Return Portal Gate on South Rim of Arena (X = -1..+1, Y = 65..68, Z = -193 -> LIME_STAINED_GLASS)
        for (int y = 65; y <= 69; y++) {
            hub.getBlockAt(-2, y, -193).setType(Material.QUARTZ_PILLAR, false);
            hub.getBlockAt(2, y, -193).setType(Material.QUARTZ_PILLAR, false);
        }
        for (int dx = -2; dx <= 2; dx++) {
            hub.getBlockAt(dx, 69, -193).setType(Material.CHISELED_QUARTZ_BLOCK, false);
        }
        for (int dx = -1; dx <= 1; dx++) {
            hub.getBlockAt(dx, GROUND_Y, -193).setType(Material.SEA_LANTERN, false);
            for (int y = 65; y <= 68; y++) {
                hub.getBlockAt(dx, y, -193).setType(Material.LIME_STAINED_GLASS, false);
            }
        }
        spawnHolo(hub, new Location(hub, 0.5, 70.4, -193.0),
                "<green><bold>🌌 성소 복귀 포탈</bold></green>\n<gray>걸어 들어가면 허브 성소로 복귀합니다</gray>");
    }

    // ==================== 구역 3: 서쪽 전문 상점가 · 츄르 은행 · 대장간/도서관 · 마을 회관 ====================

    private void buildWestMarketAndCivicDistrict(World hub) {
        // Paved Market Sidewalks flanking West Boulevard (X = -68 .. -32, Z in [-14..14])
        pavePlazaRect(hub, -68, -32, -14, -6, Material.SMOOTH_STONE, Material.STONE_BRICKS);
        pavePlazaRect(hub, -68, -32, 6, 14, Material.SMOOTH_STONE, Material.STONE_BRICKS);

        // 5 Open-Source .nbt Merchant Carts (5x4x5 each)
        stampStructure(hub, "cart_farm.nbt", -40, GROUND_Y + 1, -12, StructureRotation.NONE);
        stampStructure(hub, "cart_fish.nbt", -52, GROUND_Y + 1, -12, StructureRotation.NONE);
        stampStructure(hub, "cart_mine.nbt", -64, GROUND_Y + 1, -12, StructureRotation.NONE);
        stampStructure(hub, "cart_build.nbt", -46, GROUND_Y + 1, 8, StructureRotation.NONE);
        stampStructure(hub, "cart_misc.nbt", -60, GROUND_Y + 1, 8, StructureRotation.NONE);

        // Churu Bank & Personal Vault (North side X = -89..-74, Z = -19..-6)
        pavePlazaRect(hub, -89, -74, -19, -6, Material.SMOOTH_QUARTZ, Material.GOLD_BLOCK);
        stampStructure(hub, "bank.nbt", -86, GROUND_Y, -17, StructureRotation.NONE);
        hub.getBlockAt(-87, GROUND_Y + 1, -8).setType(Material.ENDER_CHEST, false);
        hub.getBlockAt(-76, GROUND_Y + 1, -8).setType(Material.ENDER_CHEST, false);
        spawnHolo(hub, new Location(hub, -81.5, GROUND_Y + 4.8, -7.5),
                "<gold><bold>🏦 츄르 중앙 은행 & 개인 금고</bold></gold>\n<white>사망 구조비 100% 면제 예금 · 수표 발행 · 4탭 창고</white>");

        // Cat Blacksmith & Lv.30 Enchanting Library (South side X = -104..-75, Z = 6..23)
        pavePlazaRect(hub, -104, -75, 6, 23, Material.POLISHED_ANDESITE, Material.STONE_BRICKS);
        stampStructure(hub, "blacksmith.nbt", -88, GROUND_Y, 10, StructureRotation.NONE);
        stampStructure(hub, "library.nbt", -102, GROUND_Y, 11, StructureRotation.NONE);

        // Outdoor Public Smithing & Repair Anvil Station
        hub.getBlockAt(-85, GROUND_Y + 1, 7).setType(Material.ANVIL, false);
        hub.getBlockAt(-84, GROUND_Y + 1, 7).setType(Material.SMITHING_TABLE, false);
        hub.getBlockAt(-80, GROUND_Y + 1, 7).setType(Material.GRINDSTONE, false);
        hub.getBlockAt(-79, GROUND_Y + 1, 7).setType(Material.CRAFTING_TABLE, false);

        // Guaranteed Full-Power Lv.30 Enchanting Gazebo at (-96, 65, 8)
        buildLv30EnchantingAltar(hub, -96, GROUND_Y + 1, 8);

        // West Terminus: Town Hall (Quests, Codex Museum & Plot Registry)
        pavePlazaRect(hub, -125, -104, -10, 10, Material.QUARTZ_BRICKS, Material.CHERRY_PLANKS);
        stampStructure(hub, "town_hall.nbt", -123, GROUND_Y, -6, StructureRotation.NONE);
        spawnHolo(hub, new Location(hub, -108.5, GROUND_Y + 5.2, 0.5),
                "<gradient:#ff85c0:#ffd666><bold>🏛 고양이 마을 회관</bold></gradient>\n<white>일일·주간 의뢰 │ 수집 도감 박물관 │ 토지 등기소</white>");
    }

    private void buildLv30EnchantingAltar(World hub, int cx, int y, int cz) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                hub.getBlockAt(cx + dx, y - 1, cz + dz).setType(Material.CHISELED_QUARTZ_BLOCK, false);
                hub.getBlockAt(cx + dx, y, cz + dz).setType(Material.AIR, false);
                hub.getBlockAt(cx + dx, y + 1, cz + dz).setType(Material.AIR, false);
            }
        }
        // 16 bookshelves around the 5x5 perimeter leaving South entrance (dz = -2, dx = 0) open!
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    if (dz == -2 && Math.abs(dx) <= 1) continue; // Open entrance facing boulevard
                    hub.getBlockAt(cx + dx, y, cz + dz).setType(Material.BOOKSHELF, false);
                    hub.getBlockAt(cx + dx, y + 1, cz + dz).setType(Material.BOOKSHELF, false);
                }
            }
        }
        hub.getBlockAt(cx, y, cz).setType(Material.ENCHANTING_TABLE, false);
        spawnHolo(hub, new Location(hub, cx + 0.5, y + 2.8, cz + 0.5),
                "<light_purple><bold>✨ 30레벨 공공 마법부여 제단</bold></light_purple>\n<gray>누구나 자유롭게 30레벨 인챈트 가능!</gray>");
    }

    // ==================== 구역 4A: 동쪽 2층 랜드마크 『황금 냥냥 카지노 홀』 ====================

    private void buildGoldenPawsCasinoHall(World hub) {
        int minX = 42, maxX = 84, minZ = -38, maxZ = -8;

        // Clear & build 1F Casino Floor (Y = 64)
        for (int x = minX - 2; x <= maxX + 2; x++) {
            for (int z = minZ - 2; z <= maxZ + 2; z++) {
                clearColumn(hub, x, GROUND_Y + 1, GROUND_Y + 20, z);
                if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                    Material floor = ((x + z) % 2 == 0) ? Material.RED_WOOL : Material.CRIMSON_PLANKS;
                    if (x == minX || x == maxX || z == minZ || z == maxZ) floor = Material.GOLD_BLOCK;
                    else if (x % 6 == 0 && z % 6 == 0) floor = Material.SEA_LANTERN;
                    hub.getBlockAt(x, GROUND_Y, z).setType(floor, false);
                } else {
                    hub.getBlockAt(x, GROUND_Y, z).setType(Material.SMOOTH_QUARTZ, false);
                }
            }
        }

        // Exterior Walls (Y = 65..75) & Roof Cornice (Y = 76)
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                boolean onWall = (x == minX || x == maxX || z == minZ || z == maxZ);
                if (onWall) {
                    boolean pillar = (x == minX || x == maxX) && (z == minZ || z == maxZ)
                            || (x % 7 == 0) || (z % 6 == 0);
                    for (int y = 65; y <= 75; y++) {
                        if (y == 70 || y == 75) {
                            hub.getBlockAt(x, y, z).setType(Material.GOLD_BLOCK, false);
                        } else if (pillar) {
                            hub.getBlockAt(x, y, z).setType(Material.QUARTZ_PILLAR, false);
                        } else if (y >= 67 && y <= 69 || y >= 72 && y <= 74) {
                            hub.getBlockAt(x, y, z).setType(Material.BLACK_STAINED_GLASS, false);
                        } else {
                            hub.getBlockAt(x, y, z).setType(Material.SMOOTH_QUARTZ, false);
                        }
                    }
                    hub.getBlockAt(x, 76, z).setType(Material.OCHRE_FROGLIGHT, false);
                } else {
                    // Skylight ceiling at Y = 76
                    Material roof = (x >= 53 && x <= 73 && z >= -27 && z <= -17)
                            ? Material.TINTED_GLASS
                            : ((x % 5 == 0 && z % 5 == 0) ? Material.OCHRE_FROGLIGHT : Material.BLACK_CONCRETE);
                    hub.getBlockAt(x, 76, z).setType(roof, false);
                }
            }
        }

        // Grand South Entrance Arch (X = 61..65, Z = -8, Y = 65..69) + Giant Golden Cat Ears Marquee
        for (int x = 61; x <= 65; x++) {
            for (int y = 65; y <= 69; y++) {
                hub.getBlockAt(x, y, maxZ).setType(Material.AIR, false);
            }
        }
        // West Back-Alley Door to Welfare Soup Kitchen (X = 42, Z = -13..-12, Y = 65..67)
        for (int z = -36; z <= -34; z++) {
            for (int y = 65; y <= 67; y++) hub.getBlockAt(minX, y, z).setType(Material.AIR, false);
        }
        // Golden Cat Ear Sign above Entrance (X = 59..67, Z = -8, Y = 77..79)
        for (int x = 59; x <= 67; x++) hub.getBlockAt(x, 77, maxZ).setType(Material.GOLD_BLOCK, false);
        hub.getBlockAt(60, 78, maxZ).setType(Material.GOLD_BLOCK, false);
        hub.getBlockAt(60, 79, maxZ).setType(Material.OCHRE_FROGLIGHT, false);
        hub.getBlockAt(66, 78, maxZ).setType(Material.GOLD_BLOCK, false);
        hub.getBlockAt(66, 79, maxZ).setType(Material.OCHRE_FROGLIGHT, false);
        spawnHolo(hub, new Location(hub, 63.5, 84.5, -3.2),
                "<gradient:#ffe14d:#ff4d79><bold>🎰 GOLDEN PAWS CASINO (2F)</bold></gradient>\n"
                + "<white>1F: 칩 환전소 · 일반 슬롯 · 블랙잭 · 룰렛 · <gold>실시간 경묘 트랙</gold></white>\n"
                + "<light_purple>2F VIP: 하이롤러 슬롯(잭팟 2배!) & VIP 블랙잭 발코니</light_purple>");

        // Cashier Counter at (61..65, 65, -12)
        for (int x = 61; x <= 65; x++) {
            hub.getBlockAt(x, 65, -12).setType(Material.QUARTZ_SLAB, false);
            hub.getBlockAt(x, 66, -12).setType(Material.AIR, false);
        }
        hub.getBlockAt(63, 65, -12).setType(Material.GOLD_BLOCK, false);

        // 1F Central Atrium: Live 5-Lane Cat Derby Track!
        // Lanes Z = -26, -24, -22, -20, -18 from X = 54 to X = 72
        Material[] laneTurfs = {
                Material.ORANGE_CONCRETE_POWDER,
                Material.WHITE_CONCRETE_POWDER,
                Material.LIGHT_BLUE_CONCRETE_POWDER,
                Material.PINK_CONCRETE_POWDER,
                Material.PURPLE_CONCRETE_POWDER
        };
        int[] laneZs = {-26, -24, -22, -20, -18};
        for (int x = 53; x <= 73; x++) {
            for (int z = -27; z <= -17; z++) {
                boolean rail = (x == 53 || x == 73 || z == -27 || z == -17);
                if (rail) {
                    hub.getBlockAt(x, 64, z).setType(Material.CHISELED_QUARTZ_BLOCK, false);
                    hub.getBlockAt(x, 65, z).setType(Material.OAK_FENCE, false);
                } else if (x >= 71) {
                    // Checkered Finish Line!
                    hub.getBlockAt(x, 64, z).setType(((x + z) % 2 == 0) ? Material.QUARTZ_BLOCK : Material.COAL_BLOCK, false);
                } else {
                    hub.getBlockAt(x, 64, z).setType(Material.SMOOTH_QUARTZ, false);
                }
            }
        }
        for (int i = 0; i < laneZs.length; i++) {
            int lz = laneZs[i];
            for (int x = 54; x <= 70; x++) {
                hub.getBlockAt(x, 64, lz).setType(laneTurfs[i], false);
            }
        }
        spawnHolo(hub, new Location(hub, 63.0, 67.6, -22.0),
                "<gradient:#ffe14d:#ff85c0><bold>🏇 제97랜드 실시간 고양이 달리기 경주장 (경묘)</bold></gradient>\n"
                + "<white>남서쪽 <yellow>[경묘 캐스터냥]</yellow>에게 칩을 베팅하고 난간에서 응원하세요!</white>");

        // 2F VIP Mezzanine Floor (Y = 70, walkable Y = 71) with Open Atrium (X = 52..74, Z = -28..-16)
        for (int x = minX + 1; x <= maxX - 1; x++) {
            for (int z = minZ + 1; z <= maxZ - 1; z++) {
                boolean inAtrium = (x >= 52 && x <= 74 && z >= -28 && z <= -16);
                boolean inStairwell = (x >= 47 && x <= 50 && z >= -15 && z <= -9);
                if (inAtrium || inStairwell) {
                    if (inAtrium && (x == 52 || x == 74 || z == -28 || z == -16)) {
                        hub.getBlockAt(x, 70, z).setType(Material.GOLD_BLOCK, false);
                        hub.getBlockAt(x, 71, z).setType(Material.YELLOW_STAINED_GLASS_PANE, false);
                    }
                    continue;
                }
                hub.getBlockAt(x, 70, z).setType(((x + z) % 2 == 0) ? Material.PURPLE_WOOL : Material.BLACK_CONCRETE, false);
            }
        }

        // Grand Staircase from 1F (Z = -10, Y = 65) up to 2F VIP (Z = -15, Y = 70) along X = 48..49
        for (int step = 0; step < 6; step++) {
            int sz = -10 - step;
            int sy = 65 + step;
            for (int sx = 48; sx <= 49; sx++) {
                for (int fillY = 65; fillY < sy; fillY++) {
                    hub.getBlockAt(sx, fillY, sz).setType(Material.QUARTZ_BRICKS, false);
                }
                Block stairBlock = hub.getBlockAt(sx, sy, sz);
                stairBlock.setType(Material.QUARTZ_STAIRS, false);
                if (stairBlock.getBlockData() instanceof Stairs stairs) {
                    stairs.setFacing(BlockFace.NORTH);
                    stairBlock.setBlockData(stairs, false);
                }
            }
        }
        spawnHolo(hub, new Location(hub, 49.0, 67.2, -9.5), "<light_purple><bold>⬆ 2F VIP 하이롤러 라운지 계단</bold></light_purple>");

        // 1F Physical Slot Machines along West Wall (X = 44, Z = -16, -20, -24, -28)
        int[] slotZs1F = {-16, -20, -24, -28};
        for (int sz : slotZs1F) {
            buildSlotMachineBooth(hub, 44, 65, sz, false);
        }

        // 2F VIP Physical Slot Machines along 2F West Wall (X = 44, Y = 71, Z = -18, -22, -26)
        int[] slotZs2F = {-18, -22, -26};
        for (int sz : slotZs2F) {
            buildSlotMachineBooth(hub, 44, 71, sz, true);
        }

        // 1F East Floor: Blackjack Table (79, 65, -16), Roulette Wheel (79, 65, -23), 1:1 Dice Duel (79, 65, -30)
        buildBlackjackTable(hub, 79, 65, -16);
        buildRouletteTable(hub, 79, 65, -23);
        buildDiceDuelTable(hub, 79, 65, -30);

        // 2F East VIP Lounge: VIP Blackjack Table (79, 71, -22)
        buildBlackjackTable(hub, 79, 71, -22);

        // West Back-Alley Welfare Soup Kitchen (40, 65, -13)
        hub.getBlockAt(39, 65, -36).setType(Material.CAMPFIRE, false);
        hub.getBlockAt(40, 65, -36).setType(Material.CAULDRON, false);
    }

    private void buildSlotMachineBooth(World hub, int x, int baseY, int z, boolean vip) {
        hub.getBlockAt(x, baseY, z).setType(vip ? Material.DIAMOND_BLOCK : Material.GOLD_BLOCK, false);
        hub.getBlockAt(x, baseY + 1, z).setType(Material.NOTE_BLOCK, false); // Right-click triggers CasinoManager!
        hub.getBlockAt(x, baseY + 2, z).setType(vip ? Material.PEARLESCENT_FROGLIGHT : Material.OCHRE_FROGLIGHT, false);
        Block seat = hub.getBlockAt(x + 2, baseY, z);
        seat.setType(Material.CRIMSON_STAIRS, false);
        if (seat.getBlockData() instanceof Stairs st) {
            st.setFacing(BlockFace.EAST);
            seat.setBlockData(st, false);
        }
        spawnHolo(hub, new Location(hub, x + 0.5, baseY + 3.1, z + 0.5),
                vip ? "<light_purple><bold>🎰 VIP 하이롤러 슬롯</bold></light_purple>\n<yellow>[노트블록 우클릭]</yellow>"
                    : "<gold><bold>🎰 3릴 잭팟 슬롯머신</bold></gold>\n<yellow>[노트블록 우클릭]</yellow>");
    }

    private void buildBlackjackTable(World hub, int cx, int baseY, int cz) {
        for (int dz = -1; dz <= 1; dz++) {
            hub.getBlockAt(cx, baseY - 1, cz + dz).setType(Material.EMERALD_BLOCK, false);
            hub.getBlockAt(cx, baseY, cz + dz).setType(Material.GREEN_CARPET, false);
            hub.getBlockAt(cx - 1, baseY, cz + dz).setType(Material.DARK_OAK_STAIRS, false);
        }
        spawnHolo(hub, new Location(hub, cx + 0.5, baseY + 2.5, cz + 0.5),
                (baseY >= 70 ? "<gold><bold>🃏 2F VIP 블랙잭 테이블</bold></gold>" : "<aqua><bold>🃏 21 블랙잭 테이블</bold></aqua>")
                + "\n<yellow>[테이블/딜러 우클릭]</yellow>");
    }

    private void buildRouletteTable(World hub, int cx, int baseY, int cz) {
        hub.getBlockAt(cx, baseY, cz).setType(Material.LODESTONE, false);
        hub.getBlockAt(cx, baseY, cz - 1).setType(Material.REDSTONE_LAMP, false);
        hub.getBlockAt(cx, baseY, cz + 1).setType(Material.REDSTONE_LAMP, false);
        spawnHolo(hub, new Location(hub, cx + 0.5, baseY + 2.5, cz + 0.5),
                "<green><bold>🎡 사르르 캣닢 룰렛 휠</bold></green>\n<yellow>[룰렛 휠/딜러 우클릭]</yellow>");
    }

    private void buildDiceDuelTable(World hub, int cx, int baseY, int cz) {
        hub.getBlockAt(cx, baseY, cz).setType(Material.TARGET, false);
        hub.getBlockAt(cx, baseY, cz - 1).setType(Material.DARK_OAK_STAIRS, false);
        hub.getBlockAt(cx, baseY, cz + 1).setType(Material.DARK_OAK_STAIRS, false);
        spawnHolo(hub, new Location(hub, cx + 0.5, baseY + 2.4, cz + 0.5),
                "<gold><bold>🎲 1:1 주사위 데스매치 테이블</bold></gold>\n<white>/도박신청 <닉네임> <칩수량></white>");
    }

    // ==================== 구역 4B: 동쪽 남측 노천 온천 · 식당/응급 치유소 · 선술집 · 낚시터 ====================

    private void buildEastOnsenTavernAndPier(World hub) {
        // 1) Purring Catnip Outdoor Onsen (X = 38..52, Z = 11..25)
        for (int x = 38; x <= 52; x++) {
            for (int z = 11; z <= 25; z++) {
                clearColumn(hub, x, GROUND_Y + 1, GROUND_Y + 15, z);
                boolean rim = (x <= 39 || x >= 51 || z <= 12 || z >= 24);
                if (rim) {
                    hub.getBlockAt(x, GROUND_Y, z).setType(((x + z) % 3 == 0) ? Material.MOSSY_COBBLESTONE : Material.SMOOTH_BASALT, false);
                    if (z > 12 && (x == 38 || x == 52 || z == 25)) {
                        hub.getBlockAt(x, GROUND_Y + 1, z).setType(Material.CHERRY_FENCE, false);
                    }
                } else {
                    // Warm steaming hot-spring bath!
                    if ((x + z) % 4 == 0) {
                        hub.getBlockAt(x, GROUND_Y - 2, z).setType(Material.CAMPFIRE, false);
                    }
                    hub.getBlockAt(x, GROUND_Y - 1, z).setType((x == 45 && z == 18) ? Material.SEA_LANTERN : Material.SMOOTH_BASALT, false);
                    hub.getBlockAt(x, GROUND_Y, z).setType(Material.WATER, false);
                }
            }
        }
        spawnHolo(hub, new Location(hub, 45.5, GROUND_Y + 3.4, 18.5),
                "<#ff85c0><bold>♨ 사르르 캣닢 노천 온천</bold></#ff85c0>\n<white>따끈한 탕에 15초간 몸을 담그면 15분 출격 버프 부여!</white>");

        // 2) Yuumi's Diner & Emergency Clinic (X = 55..69, Z = 7..23)
        pavePlazaRect(hub, 55, 69, 6, 23, Material.SMOOTH_QUARTZ, Material.CHERRY_PLANKS);
        stampStructure(hub, "diner_clinic.nbt", 56, GROUND_Y, 11, StructureRotation.NONE);
        // Recovery Bed Pavilion at (66, 65, 9..10)
        placeRecoveryBed(hub, 66, GROUND_Y + 1, 9);
        spawnHolo(hub, new Location(hub, 66.5, GROUND_Y + 3.5, 9.5),
                "<green><bold>🏥 유미 응급 치유소</bold></green>\n<gray>원정 사망 시 장비 100% 보존 후 이곳으로 후송됩니다</gray>");

        // 3) Cherry Blossom Tavern (BreweryX) at (72, 64, 10)
        pavePlazaRect(hub, 71, 91, 6, 31, Material.SPRUCE_PLANKS, Material.STONE_BRICKS);
        stampStructure(hub, "cherry_tavern.nbt", 72, GROUND_Y, 10, StructureRotation.NONE);
        // Outdoor BreweryX Cauldron & Barrel Station
        hub.getBlockAt(74, GROUND_Y, 8).setType(Material.CAMPFIRE, false);
        hub.getBlockAt(74, GROUND_Y + 1, 8).setType(Material.WATER_CAULDRON, false);
        hub.getBlockAt(73, GROUND_Y + 1, 8).setType(Material.BARREL, false);

        // 4) Hub Fishing Hut & Crystal Pond (X = 94..118, Z = 8..34)
        stampStructure(hub, "fishing_hut.nbt", 95, GROUND_Y, 8, StructureRotation.NONE);
        for (int x = 96; x <= 118; x++) {
            for (int z = 18; z <= 34; z++) {
                clearColumn(hub, x, GROUND_Y + 1, GROUND_Y + 14, z);
                boolean shore = (x == 96 || x == 118 || z == 18 || z == 34);
                if (shore) {
                    hub.getBlockAt(x, GROUND_Y, z).setType(Material.MOSSY_STONE_BRICKS, false);
                } else {
                    hub.getBlockAt(x, GROUND_Y - 3, z).setType(((x + z) % 7 == 0) ? Material.SEA_LANTERN : Material.SAND, false);
                    for (int y = GROUND_Y - 2; y <= GROUND_Y; y++) {
                        hub.getBlockAt(x, y, z).setType(Material.WATER, false);
                    }
                }
            }
        }
        // Wooden Pier over Pond (X = 105..108, Z = 17..23)
        for (int x = 105; x <= 108; x++) {
            for (int z = 17; z <= 23; z++) {
                hub.getBlockAt(x, GROUND_Y + 1, z).setType(Material.SPRUCE_SLAB, false);
            }
        }
        spawnHolo(hub, new Location(hub, 107.0, GROUND_Y + 3.4, 23.5),
                "<aqua><bold>🎣 97랜드 허브 낚시 부두</bold></aqua>\n<white>지인과 수다 떨며 낚은 물고기를 서쪽 수산 상인에게 감별 판매!</white>");
    }

    private void placeRecoveryBed(World hub, int x, int y, int zFoot) {
        Block foot = hub.getBlockAt(x, y, zFoot);
        Block head = hub.getBlockAt(x, y, zFoot + 1);
        foot.setType(Material.PINK_BED, false);
        head.setType(Material.PINK_BED, false);
        if (foot.getBlockData() instanceof Bed bf && head.getBlockData() instanceof Bed bh) {
            bf.setPart(Bed.Part.FOOT);
            bf.setFacing(BlockFace.SOUTH);
            bh.setPart(Bed.Part.HEAD);
            bh.setFacing(BlockFace.SOUTH);
            foot.setBlockData(bf, false);
            head.setBlockData(bh, false);
        }
    }

    // ==================== 구역 5: 남쪽 벚꽃 운하 대교 & 12필지 분양 주거단지 ====================

    private void buildSouthBridgeAndPlotDistrict(World hub) {
        // Approach road from South Plaza edge (Z = 27..34)
        for (int z = 27; z <= 34; z++) {
            for (int x = -5; x <= 5; x++) {
                clearColumn(hub, x, GROUND_Y + 1, GROUND_Y + 14, z);
                hub.getBlockAt(x, GROUND_Y, z).setType(Math.abs(x) == 5 ? Material.CHERRY_PLANKS : Material.QUARTZ_BRICKS, false);
            }
        }

        // Scenic Blossom Canal across X = -70..70, Z = 35..47
        for (int x = -70; x <= 70; x++) {
            for (int z = 35; z <= 47; z++) {
                clearColumn(hub, x, GROUND_Y + 1, GROUND_Y + 14, z);
                if (z == 35 || z == 47) {
                    hub.getBlockAt(x, GROUND_Y, z).setType(Material.CHISELED_QUARTZ_BLOCK, false);
                } else {
                    hub.getBlockAt(x, GROUND_Y - 3, z).setType((Math.abs(x) % 9 == 0 && z == 41) ? Material.SEA_LANTERN : Material.PRISMARINE, false);
                    for (int y = GROUND_Y - 2; y <= GROUND_Y - 1; y++) {
                        hub.getBlockAt(x, y, z).setType(Material.WATER, false);
                    }
                    hub.getBlockAt(x, GROUND_Y, z).setType(Material.AIR, false);
                }
            }
        }

        // Arched Cherry & Quartz Bridge spanning Z = 34..48, X = -5..5
        for (int z = 34; z <= 48; z++) {
            int archLift = (z >= 38 && z <= 44) ? 2 : ((z >= 36 && z <= 46) ? 1 : 0);
            int deckY = GROUND_Y + archLift;
            for (int x = -5; x <= 5; x++) {
                hub.getBlockAt(x, deckY, z).setType(Math.abs(x) == 5 ? Material.CHERRY_WOOD : Material.QUARTZ_BRICKS, false);
                if (Math.abs(x) == 5) {
                    hub.getBlockAt(x, deckY + 1, z).setType(Material.CHERRY_FENCE, false);
                    if (z % 4 == 0) hub.getBlockAt(x, deckY + 2, z).setType(Material.LANTERN, false);
                }
            }
        }
        spawnHolo(hub, new Location(hub, 0.5, GROUND_Y + 5.2, 41.5),
                "<#ff85c0><bold>🌸 사르르 벚꽃 대교</bold></#ff85c0>\n<white>▼ 남쪽: 고양이 주거·농장 12필지 분양 단지</white>");

        // Main Residential Boulevard (Z = 49..204, X = -6..6) + Cross Street (Z = 55..59, X = -48..48)
        for (int z = 49; z <= 204; z++) {
            for (int x = -6; x <= 6; x++) {
                clearColumn(hub, x, GROUND_Y + 1, GROUND_Y + 14, z);
                Material road = Math.abs(x) == 6 ? Material.CHERRY_PLANKS : (x == 0 && z % 8 == 0 ? Material.SEA_LANTERN : Material.QUARTZ_BRICKS);
                hub.getBlockAt(x, GROUND_Y, z).setType(road, false);
            }
            if (z % 22 == 0) {
                buildLanternPost(hub, -6, GROUND_Y + 1, z);
                buildLanternPost(hub, 6, GROUND_Y + 1, z);
            }
        }
        for (int x = -48; x <= 48; x++) {
            for (int z = 54; z <= 58; z++) {
                if (Math.abs(x) <= 6) continue;
                clearColumn(hub, x, GROUND_Y + 1, GROUND_Y + 14, z);
                hub.getBlockAt(x, GROUND_Y, z).setType((z == 54 || z == 58) ? Material.CHERRY_PLANKS : Material.QUARTZ_BRICKS, false);
            }
        }

        publicBlock(hub,3,65,58,Material.CHERRY_SIGN);
        Block board=hub.getBlockAt(3,65,58);
        if(board.getBlockData() instanceof org.bukkit.block.data.type.Sign facing){facing.setRotation(BlockFace.WEST);board.setBlockData(facing,false);}
        if(board.getState() instanceof org.bukkit.block.Sign sign){
            var front=sign.getSide(org.bukkit.block.sign.Side.FRONT);
            front.line(0,TextUtil.mm("<gold>🏡 12필지 분양 단지"));front.line(1,TextUtil.mm("<black>소형6 · 중형4 · 대형2"));
            front.line(2,TextUtil.mm("<black>현장 표지판 / 마을 회관"));front.line(3,TextUtil.mm("<dark_green>/집 · /분양"));
            sign.setWaxed(true);sign.update(true,false);
        }

        // Format all 12 Residential Plots with crisp borders & fresh grass lawns
        if (plotManager != null) {
            for (PlotSpec spec : plotManager.getAllPlots().values()) {
                for (int x = spec.minX() - 1; x <= spec.maxX() + 1; x++) {
                    for (int z = spec.minZ() - 1; z <= spec.maxZ() + 1; z++) {
                        if (isOwnedColumn(hub, x, z)) continue;
                        clearColumn(hub, x, GROUND_Y + 1, GROUND_Y + 18, z);
                        boolean border = (x == spec.minX() - 1 || x == spec.maxX() + 1 || z == spec.minZ() - 1 || z == spec.maxZ() + 1);
                        if (border) {
                            hub.getBlockAt(x, GROUND_Y, z).setType(Material.CHISELED_QUARTZ_BLOCK, false);
                            hub.getBlockAt(x, GROUND_Y + 1, z).setType(Material.SMOOTH_QUARTZ_SLAB, false);
                        } else {
                            hub.getBlockAt(x, GROUND_Y, z).setType(Material.GRASS_BLOCK, false);
                        }
                    }
                }
            }
            plotManager.refreshAllPlotSigns();
        }
    }

    /**
     * Stamps a free Cozy Starter Cottage (.nbt) when a player buys a plot with the starter house option!
     */
    public void buildStarterCottageOnPlot(World hub, PlotSpec spec) {
        String nbt = (Math.abs(spec.centerX() + spec.centerZ()) % 2 == 0) ? "starter_cottage_1.nbt" : "starter_cottage_2.nbt";
        int ox = spec.centerX() - 3;
        int oz = spec.centerZ() - 3;
        stampStructure(hub, nbt, ox, GROUND_Y, oz, StructureRotation.NONE, true);

        // Neat garden stepping-stone walkway toward road sign
        int stepDir = spec.signX() < spec.minX() ? -1 : 1;
        int startX = stepDir < 0 ? spec.minX() : spec.maxX();
        for (int x = Math.min(startX, spec.centerX()); x <= Math.max(startX, spec.centerX()); x++) {
            Block b = hub.getBlockAt(x, GROUND_Y, spec.signZ());
            if (b.getType() == Material.GRASS_BLOCK) {
                b.setType(Material.COBBLESTONE, false);
            }
        }
    }

    // ==================== 3. 오픈소스 .nbt 구조물 로더 & 무결성 정화기 ====================

    public boolean stampStructure(World world, String fileName, int x, int y, int z, StructureRotation rotation) {
        return stampStructure(world, fileName, x, y, z, rotation, false);
    }

    private boolean stampStructure(World world, String fileName, int x, int y, int z,
                                   StructureRotation rotation, boolean allowOwned) {
        try (InputStream in = plugin.getResource("structures/" + fileName)) {
            if (in == null) {
                plugin.getLogger().warning("[HubWorldManager] Missing bundled structure resource: " + fileName);
                return false;
            }
            Structure structure = Bukkit.getStructureManager().loadStructure(in);
            BlockVector size = structure.getSize();
            int[] bounds = rotatedFootprint(size.getBlockX(), size.getBlockZ(), rotation);
            int minX = x + bounds[0], maxX = x + bounds[1];
            int minZ = z + bounds[2], maxZ = z + bounds[3];
            if (!allowOwned && intersectsOwnedColumns(world, minX, maxX, minZ, maxZ)) {
                plugin.getLogger().warning("[HubWorldManager] Refused public structure over an owned plot: " + fileName);
                return false;
            }
            Location origin = new Location(world, x, y, z);
            structure.place(origin, false, rotation, Mirror.NONE, 0, 1.0f, new Random(97L));
            sanitizePlacedRegion(world, minX, maxX, y, y + size.getBlockY() - 1, minZ, maxZ,
                    fileName.endsWith("harbor_ship.nbt"));
            return true;
        } catch (Exception e) {
            plugin.getLogger().warning("[HubWorldManager] Failed to stamp " + fileName + ": " + e.getMessage());
            return false;
        }
    }

    /** Exact offsets for Bukkit's rotation around the placement origin, inclusive. */
    public static int[] rotatedFootprint(int width, int depth, StructureRotation rotation) {
        if (width < 1 || depth < 1) throw new IllegalArgumentException("Structure dimensions must be positive");
        return switch (rotation) {
            case NONE -> new int[]{0, width - 1, 0, depth - 1};
            case CLOCKWISE_90 -> new int[]{1 - depth, 0, 0, width - 1};
            case CLOCKWISE_180 -> new int[]{1 - width, 0, 1 - depth, 0};
            case COUNTERCLOCKWISE_90 -> new int[]{0, depth - 1, 1 - width, 0};
        };
    }

    boolean isOwnedColumn(World world, int x, int z) {
        return world != null && HUB_WORLD_NAME.equals(world.getName())
                && plotManager != null && plotManager.isOwnedColumn(x, z);
    }

    boolean intersectsOwnedColumns(World world, int minX, int maxX, int minZ, int maxZ) {
        if (!HUB_WORLD_NAME.equals(world.getName()) || plotManager == null) return false;
        for (PlotSpec plot : plotManager.getAllPlots().values()) {
            if (plotManager.getOwner(plot.id()) != null && minX <= plot.maxX() && maxX >= plot.minX()
                    && minZ <= plot.maxZ() && maxZ >= plot.minZ()) return true;
        }
        return false;
    }

    private void sanitizePlacedRegion(World world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                                      boolean marine) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    Block b = world.getBlockAt(x, y, z);
                    Material type = b.getType();
                    if (type == Material.JIGSAW || type == Material.STRUCTURE_BLOCK || type == Material.COMMAND_BLOCK
                            || type == Material.CHAIN_COMMAND_BLOCK || type == Material.REPEATING_COMMAND_BLOCK) {
                        b.setType(marine && y <= 63 ? Material.WATER : (y <= GROUND_Y ? Material.STONE_BRICKS : Material.AIR), false);
                    } else if (type == Material.STRUCTURE_VOID || type == Material.BARRIER
                            || type == Material.SPAWNER || type == Material.TRIAL_SPAWNER) {
                        b.setType(Material.AIR, false);
                    } else if (b.getBlockData() instanceof Leaves leaves) {
                        leaves.setPersistent(true);
                        b.setBlockData(leaves, false);
                    } else if (b.getState() instanceof Container container) {
                        if (container instanceof Lootable lootable && lootable.getLootTable() != null) {
                            lootable.setLootTable(null);
                            container.update(true, false);
                        }
                        container.getInventory().clear();
                    }
                }
            }
        }
    }

    // ==================== 4. 허브 전용 NPC 고양이 스폰 & 상호작용 ====================

    private void spawnAllHubNpcs(World hub) {
        spawnCatNpc(hub,new Location(hub,-30.5,65,-36.5),Cat.Type.BLACK,
                "<gold><bold>⚒ 공방 대장 보조냥</bold></gold>","<white>별빛 세공 · 수리 · 룬 · 지오드</white>","workshop_smith");
        spawnCatNpc(hub,new Location(hub,-12.5,65,-36.5),Cat.Type.RAGDOLL,
                "<aqua><bold>🔐 금고지기 보조냥</bold></aqua>","<white>4탭 개인 안전 금고</white>","workshop_vault");
        spawnCatNpc(hub,new Location(hub,-12.5,65,-28.5),Cat.Type.CALICO,
                "<green><bold>🪚 목공·만찬 공방 보조냥</bold></green>","<white>공공 제작 · 전문직 효율 우대</white>","workshop_craft");
        Interaction kiosk=hub.spawn(getGuidebookKioskLocation(),Interaction.class);
        kiosk.setInteractionWidth(1.4f);kiosk.setInteractionHeight(3.4f);kiosk.setResponsive(true);kiosk.setPersistent(true);
        kiosk.getPersistentDataContainer().set(npcRoleKey,PersistentDataType.STRING,"guidebook_kiosk");
        kiosk.getPersistentDataContainer().set(hubDecorKey,PersistentDataType.BYTE,(byte)1);
        spawnHolo(hub,new Location(hub,8.5,73.3,8.5),"<gold><bold>📖 만능 가이드북 안내소</bold></gold>\n<white>우클릭: 가이드 열람 · Shift+우클릭: 책 무료 수령</white>");
        spawnCatNpc(hub, getClassMasterLocation(), Cat.Type.SIAMESE,
                "<gold><bold>🌟 성향 전직 교관 마스터냥</bold></gold>",
                "<yellow>[우클릭] 전투 6 · 생활 6 성향 · 초월 각성 · 특전 · 책갈피함</yellow>", "class_master");
        spawnCatNpc(hub, getDungeonGateLocation(), Cat.Type.ALL_BLACK,
                "<light_purple><bold>🗝 미궁 수문장 유령냥</bold></light_purple>",
                "<yellow>[우클릭] 파티 미궁 입장 · 고대 금화 교환</yellow>", "dungeon_gate");
        spawnCatNpc(hub, getStockBrokerLocation(), Cat.Type.PERSIAN,
                "<green><bold>📈 츄르 증권 중개묘</bold></green>",
                "<yellow>[우클릭] 실시간 시세 · 매수 · 매도</yellow>", "stock_broker");
        spawnCatNpc(hub, getHallOfFameLocation(), Cat.Type.WHITE,
                "<gold><bold>🏆 명예의 전당 기록묘</bold></gold>",
                "<yellow>[우클릭] Top 3 순위 · 획득 칭호</yellow>", "hall_of_fame");
        spawnCatNpc(hub, getGachaLocation(), Cat.Type.CALICO,
                "<#ff85c0><bold>🎁 황금 캡슐 가챠냥</bold></#ff85c0>",
                "<yellow>[우클릭] 모자 · 가구 · 세공 재료 뽑기</yellow>", "gacha");
        // North Sanctuary & Raid Arena
        spawnCatNpc(hub, new Location(hub, 0.5, 66.0, -53.5, 0f, 0f), Cat.Type.SIAMESE,
                "<light_purple><bold>📖 차원 수호묘 유미</bold></light_purple>", "<yellow>[우클릭] 문턱의 서 제단 납품</yellow>", "altar");
        spawnCatNpc(hub, new Location(hub, 0.5, 65.0, -199.5, 180f, 0f), Cat.Type.ALL_BLACK,
                "<red><bold>⚔ 결투장 심판냥</bold></red>", "<yellow>[우클릭] 월드보스 레이드 소환</yellow>", "raid");

        // West Boulevard: 5 Specialized Merchants
        spawnCatNpc(hub, new Location(hub, -37.5, 65.0, -6.5, 0f, 0f), Cat.Type.RED,
                "<gold><bold>🌾 농산 상인 치즈냥</bold></gold>", "<yellow>[우클릭] 농작물 매입 · 종자 상점</yellow>", "shop_farm");
        spawnCatNpc(hub, new Location(hub, -52.5, 65.0, -6.5, 0f, 0f), Cat.Type.TABBY,
                "<aqua><bold>🐟 수산 상인 고등어냥</bold></aqua>", "<yellow>[우클릭] 바닐라·특수 물고기 감정 매입</yellow>", "shop_fish");
        spawnCatNpc(hub, new Location(hub, -67.5, 65.0, -6.5, 0f, 0f), Cat.Type.BRITISH_SHORTHAIR,
                "<gray><bold>⛏ 광물 상인 석탄냥</bold></gray>", "<yellow>[우클릭] 광석 매입 · 광부 보급</yellow>", "shop_mine");
        spawnCatNpc(hub, new Location(hub, -43.5, 65.0, 6.5, 180f, 0f), Cat.Type.CALICO,
                "<#ff9c6e><bold>🪵 건축자재 상인 목수냥</bold></#ff9c6e>", "<yellow>[우클릭] 목재 · 장식 블록 상점</yellow>", "shop_build");
        spawnCatNpc(hub, new Location(hub, -60.5, 65.0, 6.5, 180f, 0f), Cat.Type.JELLIE,
                "<green><bold>🎒 만물 잡화상 보부상냥</bold></green>", "<yellow>[우클릭] 전리품 매입 · 귀환 책갈피</yellow>", "shop_misc");

        // West Bank & Vault
        spawnCatNpc(hub, new Location(hub, -83.5, 65.0, -7.0, 0f, 0f), Cat.Type.PERSIAN,
                "<gold><bold>🏦 은행장 금빛냥</bold></gold>", "<yellow>[우클릭] 안전 예금 · 츄르 수표 발행</yellow>", "bank_teller");
        spawnCatNpc(hub, new Location(hub, -79.5, 65.0, -7.0, 0f, 0f), Cat.Type.RAGDOLL,
                "<aqua><bold>🔐 금고지기 은빛냥</bold></aqua>", "<yellow>[우클릭] 개인 확장형 금고 열기</yellow>", "vault_keeper");

        // West Blacksmith
        spawnCatNpc(hub, new Location(hub, -82.5, 65.0, 6.8, 180f, 0f), Cat.Type.BLACK,
                "<#ff9c6e><bold>⚒ 대장장이 망치냥</bold></#ff9c6e>", "<yellow>[우클릭] 장비 수리 · ★1~★7 별빛 세공</yellow>", "blacksmith");

        // West Terminus: Town Hall 3 Officials
        spawnCatNpc(hub, new Location(hub, -106.5, 65.0, -3.5, -90f, 0f), Cat.Type.WHITE,
                "<#ff9c6e><bold>📋 의뢰 담당 비서냥</bold></#ff9c6e>", "<yellow>[우클릭] 일일 · 주간 납품 의뢰</yellow>", "quest");
        spawnCatNpc(hub, new Location(hub, -106.5, 65.0, 0.5, -90f, 0f), Cat.Type.SIAMESE,
                "<aqua><bold>📚 도감 박물관장 박사냥</bold></aqua>", "<yellow>[우클릭] 21종 수집 도감 등록</yellow>", "codex");
        spawnCatNpc(hub, new Location(hub, -106.5, 65.0, 4.5, -90f, 0f), Cat.Type.CALICO,
                "<#ff85c0><bold>🏛 토지 등기소장 도장냥</bold></#ff85c0>", "<yellow>[우클릭] 12필지 분양 현황 & 등기</yellow>", "plot_registry");

        // East Golden Paws Casino Hall
        spawnCatNpc(hub, new Location(hub, 63.5, 66.0, -12.0, 0f, 0f), Cat.Type.PERSIAN,
                "<gold><bold>💱 카지노 환전소 캐셔냥</bold></gold>", "<yellow>[우클릭] 츄르 ⇄ 실물 카지노 칩 환전</yellow>", "casino_cashier");
        spawnCatNpc(hub, new Location(hub, 52.5, 66.0, -15.0, 0f, 0f), Cat.Type.RED,
                "<gradient:#ffe14d:#ff85c0><bold>🏇 경묘 중계 캐스터냥</bold></gradient>", "<yellow>[우클릭] 실시간 5레인 고양이 경주 베팅!</yellow>", "cat_race");
        spawnCatNpc(hub, new Location(hub, 80.5, 66.0, -16.0, 90f, 0f), Cat.Type.BLACK,
                "<aqua><bold>🃏 1F 블랙잭 딜러냥</bold></aqua>", "<yellow>[우클릭] 21 블랙잭 승부</yellow>", "casino_blackjack");
        spawnCatNpc(hub, new Location(hub, 80.5, 66.0, -21.0, 90f, 0f), Cat.Type.TABBY,
                "<green><bold>🎡 캣닢 룰렛 딜러냥</bold></green>", "<yellow>[우클릭] 14배 황금 유미 룰렛</yellow>", "casino_roulette");
        spawnCatNpc(hub, new Location(hub, 80.5, 72.0, -22.0, 90f, 0f), Cat.Type.ALL_BLACK,
                "<light_purple><bold>💎 2F VIP 블랙잭 딜러냥</bold></light_purple>", "<yellow>[우클릭] 하이롤러 블랙잭 (2,000~10,000칩)</yellow>", "casino_blackjack_vip");
        spawnCatNpc(hub, new Location(hub, 40.5, 65.0, -35.0, 90f, 0f), Cat.Type.RAGDOLL,
                "<#ff9c6e><bold>🥣 무료 급식소 이모냥</bold></#ff9c6e>", "<yellow>[우클릭] 파산 집사 긴급 생계비 배급</yellow>", "casino_welfare");

        // East Diner, Clinic & Tavern
        spawnCatNpc(hub, new Location(hub, 57.5, 65.0, 8.5, 180f, 0f), Cat.Type.RED,
                "<#ff9c6e><bold>🍱 특선 식당 주방장 뚱냥</bold></#ff9c6e>", "<yellow>[우클릭] 유미 특선 버프 도시락 구매</yellow>", "diner");
        spawnCatNpc(hub, new Location(hub, 67.5, 65.0, 8.5, 180f, 0f), Cat.Type.WHITE,
                "<green><bold>💉 응급 치유사 간호냥</bold></green>", "<yellow>[우클릭] 체력·허기 즉시 무료 회복</yellow>", "clinic");
        spawnCatNpc(hub, new Location(hub, 76.5, 65.0, 8.5, 180f, 0f), Cat.Type.CALICO,
                "<gold><bold>🍺 벚꽃 선술집 마스터냥</bold></gold>", "<yellow>[우클릭] 수제 양조주(BreweryX) 비법 안내</yellow>", "tavern");
    }

    private void spawnCatNpc(World world, Location loc, Cat.Type type, String title, String sub, String role) {
        if (isOwnedColumn(world, loc.getBlockX(), loc.getBlockZ())) return;
        clearNpcPad(loc, loc.getBlockY() - 1);
        world.getChunkAt(loc).load();
        Cat cat = (Cat) world.spawnEntity(loc, EntityType.CAT);
        cat.setCatType(type);
        cat.setAdult();
        cat.setSitting(true);
        cat.setAI(false);
        var scale = cat.getAttribute(Attribute.SCALE);
        if (scale != null) scale.setBaseValue(Set.of("casino_cashier", "cat_race", "casino_blackjack",
                "casino_roulette", "casino_blackjack_vip", "stock_broker").contains(role) ? 1.35 : 1.28);
        cat.setInvulnerable(true);
        cat.setSilent(false);
        cat.setRemoveWhenFarAway(false);
        cat.setPersistent(true);
        cat.getPersistentDataContainer().set(npcRoleKey, PersistentDataType.STRING, role);
        cat.getPersistentDataContainer().set(hubDecorKey, PersistentDataType.BYTE, (byte) 1);
        Interaction interaction = world.spawn(loc, Interaction.class);
        interaction.setInteractionWidth(1.35f);
        interaction.setInteractionHeight(2.0f);
        interaction.setResponsive(true);
        interaction.setPersistent(true);
        interaction.getPersistentDataContainer().set(npcRoleKey, PersistentDataType.STRING, role);
        interaction.getPersistentDataContainer().set(hubDecorKey, PersistentDataType.BYTE, (byte) 1);
        spawnHolo(world, loc.clone().add(0, 1.65, 0), title + "\n" + sub);
    }

    void spawnHolo(World world, Location loc, String mmText) {
        if (isOwnedColumn(world, loc.getBlockX(), loc.getBlockZ())) return;
        world.getChunkAt(loc).load();
        TextDisplay td = (TextDisplay) world.spawnEntity(loc, EntityType.TEXT_DISPLAY);
        td.text(TextUtil.mm(mmText));
        td.setBillboard(Display.Billboard.CENTER);
        td.setAlignment(TextDisplay.TextAlignment.CENTER);
        td.setShadowed(true);
        td.setBackgroundColor(Color.fromARGB(120, 18, 14, 28));
        td.setPersistent(true);
        td.getPersistentDataContainer().set(hubDecorKey, PersistentDataType.BYTE, (byte) 1);
    }

    /**
     * Auto-detects custom 3D JSON models placed by Codex inside Yuumi97Pack.zip and mounts ItemDisplay props!
     */
    public void refreshCustom3DProps() {
        World hub = getHubWorld();
        if (hub == null) return;
        if (!rebuilding && !layout.sourceFingerprint().equals(data.getString("hub.blueprint_hash", ""))) {
            buildEntireHubCity(hub);
            return;
        }
        loadPublicChunks(hub);

        // Load every mount chunk before finding old persistent displays. Otherwise
        // a refresh can create duplicates of displays in previously unloaded chunks.
        for (int[] point : new int[][]{{0, 0}, {-1, 0}, {44, -16}, {44, -20},
                {44, -24}, {44, -28}, {44, -18}, {44, -22}, {44, -26},
                {79, -23}, {72, -22}, {21, -15}, {52, -35}, {24, -57}, {12, -59}, {59, 10}}) {
            hub.getChunkAt(point[0] >> 4, point[1] >> 4).load();
        }

        // Remove previous 3D prop displays before refreshing
        for (Entity e : hub.getEntitiesByClass(ItemDisplay.class)) {
            if (!isOwnedColumn(hub, e.getLocation().getBlockX(), e.getLocation().getBlockZ())
                    && e.getPersistentDataContainer().has(propModelKey, PersistentDataType.STRING)) {
                e.remove();
            }
        }

        Set<String> bundledModels = scanBundledPackModels();
        boolean statuePresent = bundledModels.contains("prop_yuumi_statue");
        reconcileStatueBlocks(hub, statuePresent);
        adjustPropHologram(hub, "fountain", new Location(hub, 0.5, 77.0, 0.5),
                77.0, statuePresent, "중앙 광장");
        boolean slotsPresent = bundledModels.contains("prop_slot_cabinet");
        for (int z : new int[]{-16, -20, -24, -28}) {
            adjustPropHologram(hub, "slot_1f_" + z, new Location(hub, 44.5, 68.1, z + 0.5),
                    69.0, slotsPresent, "슬롯");
        }
        for (int z : new int[]{-18, -22, -26}) {
            adjustPropHologram(hub, "slot_2f_" + z, new Location(hub, 44.5, 74.1, z + 0.5),
                    75.0, slotsPresent, "슬롯");
        }

        if (bundledModels.contains("prop_yuumi_statue")) {
            mount3DProp(hub, new Location(hub, 0.5, GROUND_Y + 6.6, 0.5, 0f, 0f), "prop_yuumi_statue", 2.0f);
        }
        if (bundledModels.contains("prop_slot_cabinet")) {
            for (int sz : new int[]{-16, -20, -24, -28}) {
                mount3DProp(hub, new Location(hub, 44.5, 66.5, sz + 0.5, -90f, 0f), "prop_slot_cabinet", 1.35f);
            }
            for (int sz : new int[]{-18, -22, -26}) {
                mount3DProp(hub, new Location(hub, 44.5, 72.5, sz + 0.5, -90f, 0f), "prop_slot_cabinet", 1.35f);
            }
        }
        if (bundledModels.contains("prop_roulette_wheel")) {
            mount3DProp(hub, new Location(hub, 79.5, 66.15, -22.5, 90f, 0f), "prop_roulette_wheel", 1.25f);
        }
        if (bundledModels.contains("prop_derby_trophy")) {
            mount3DProp(hub, new Location(hub, 72.5, 66.2, -22.0, 90f, 0f), "prop_derby_trophy", 1.5f);
        }
        mountFacilityProp(hub, bundledModels, "prop_gacha_machine", 22, 65, -31,
                new Location(hub, 22.5, 65.7, -30.5), Material.GOLD_BLOCK, 1.4f);
        mountFacilityProp(hub, bundledModels, "prop_stock_board", 52, 74, -35,
                new Location(hub, 52.5, 75.0, -34.7), Material.BLACK_CONCRETE, 1.5f);
        mountFacilityProp(hub, bundledModels, "prop_dungeon_obelisk", 24, 66, -57,
                new Location(hub, 24.5, 67.2, -56.5), Material.CHISELED_DEEPSLATE, 1.25f);
        mountFacilityProp(hub, bundledModels, "prop_boon_altar", 12, 66, -59,
                new Location(hub, 12.5, 66.9, -58.5), Material.ENCHANTING_TABLE, 1.2f);
        mountFacilityProp(hub, bundledModels, "prop_feast_cauldron", 59, 65, 10,
                new Location(hub, 59.5, 65.8, 10.5), Material.CAULDRON, 1.0f);
        layout.mountLandmarks(hub, bundledModels);
        trainingDummyReconciler.run();
    }

    private void mountFacilityProp(World world, Set<String> models, String id, int x, int y, int z,
                                      Location displayLocation, Material fallback, float scale) {
        if (isOwnedColumn(world, x, z)) return;
        Block block = world.getBlockAt(x, y, z);
        if (models.contains(id)) {
            if (block.getType() == fallback) block.setType(Material.AIR, false);
            if (id.equals("prop_gacha_machine") || id.equals("prop_stock_board")) {
                tagPublicProp(spawnAnchoredProp(displayLocation, id, scale, 8f, 0f), id);
            } else mount3DProp(world, displayLocation, id, scale);
        } else if (block.getType().isAir()) {
            block.setType(fallback, false);
        }
    }

    public boolean hasBundledModel(String modelId) {
        return scanBundledPackModels().contains(modelId);
    }

    /** Caller owns the returned display; public refresh deliberately does not tag or delete it. */
    public ItemDisplay spawnModelProp(Location location, String modelId, float scale) {
        if (location == null || location.getWorld() == null || !hasBundledModel(modelId)) return null;
        return createModelDisplay(location.getWorld(), location, modelId, scale);
    }

    /** Align a model's [8,anchorY,8] to world space. Logical yaw follows the cat/NPC facing convention. */
    public ItemDisplay spawnAnchoredProp(Location anchor, String modelId, float scale, float anchorY, float logicalYaw) {
        if (anchor == null || anchor.getWorld() == null || !hasBundledModel(modelId)) return null;
        Location entityLocation = anchor.clone().add(0, (8f - anchorY) * scale / 16f, 0);
        entityLocation.setYaw(logicalYaw);
        ItemDisplay display = createModelDisplay(entityLocation.getWorld(), entityLocation, modelId, scale);
        display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f((float) Math.PI, 0, 1, 0),
                new Vector3f(scale, scale, scale), new AxisAngle4f(0, 0, 1, 0)));
        Vector3f facing = new Vector3f(0, 0, -1).rotate(display.getTransformation().getLeftRotation())
                .rotateY((float) -Math.toRadians(logicalYaw));
        Vector3f outward = new Vector3f((float) -Math.sin(Math.toRadians(logicalYaw)), 0,
                (float) Math.cos(Math.toRadians(logicalYaw)));
        if (facing.distanceSquared(outward) > .00001f) throw new IllegalStateException("Landmark facing transform mismatch: " + modelId);
        return display;
    }

    void tagPublicProp(ItemDisplay display, String id) {
        if (display != null) display.getPersistentDataContainer().set(propModelKey, PersistentDataType.STRING, id);
    }

    public void animateRoulette() {
        if (rouletteSpinning || getHubWorld() == null) return;
        ItemDisplay wheel = getHubWorld().getEntitiesByClass(ItemDisplay.class).stream()
                .filter(e -> "prop_roulette_wheel".equals(e.getPersistentDataContainer().get(propModelKey, PersistentDataType.STRING)))
                .findFirst().orElse(null);
        if (wheel == null) return;
        rouletteSpinning = true;
        float originalYaw = wheel.getLocation().getYaw();
        new BukkitRunnable() {
            int ticks;
            @Override public void run() {
                if (!wheel.isValid() || ticks >= 30) {
                    if (wheel.isValid()) wheel.setRotation(originalYaw, 0);
                    rouletteSpinning = false;
                    cancel();
                    return;
                }
                wheel.setRotation(originalYaw + (++ticks * 24f), 0);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void reconcileStatueBlocks(World hub, boolean customStatuePresent) {
        int[][] positions = {{0, 69, 0}, {-1, 70, 0}, {1, 70, 0}, {0, 70, 0}};
        Material[] original = {Material.GOLD_BLOCK, Material.PINK_CONCRETE,
                Material.PINK_CONCRETE, Material.SEA_LANTERN};
        int replaced = hub.getPersistentDataContainer().getOrDefault(statueBlocksKey, PersistentDataType.INTEGER, 0);
        for (int i = 0; i < positions.length; i++) {
            int[] p = positions[i];
            Block block = hub.getBlockAt(p[0], p[1], p[2]);
            if (customStatuePresent && block.getType() == original[i]) {
                block.setType(Material.AIR, false);
                replaced |= 1 << i;
            } else if (!customStatuePresent && (replaced & (1 << i)) != 0) {
                // Restore only blocks this feature removed, never overwrite later edits.
                if (block.getType().isAir()) block.setType(original[i], false);
                replaced &= ~(1 << i);
            }
        }
        if (replaced == 0) hub.getPersistentDataContainer().remove(statueBlocksKey);
        else hub.getPersistentDataContainer().set(statueBlocksKey, PersistentDataType.INTEGER, replaced);
    }

    private void adjustPropHologram(World hub, String id, Location original, double customY,
                                   boolean customPresent, String titleFragment) {
        for (TextDisplay text : hub.getEntitiesByClass(TextDisplay.class)) {
            String ownedId = text.getPersistentDataContainer().get(propHologramKey, PersistentDataType.STRING);
            boolean legacyMatch = ownedId == null
                    && text.getPersistentDataContainer().has(hubDecorKey, PersistentDataType.BYTE)
                    && text.getLocation().distanceSquared(original) < 0.01
                    && PlainTextComponentSerializer.plainText().serialize(text.text()).contains(titleFragment);
            if (!id.equals(ownedId) && !legacyMatch) continue;
            text.getPersistentDataContainer().set(propHologramKey, PersistentDataType.STRING, id);
            Location target = original.clone();
            if (customPresent) target.setY(customY);
            if (text.getLocation().distanceSquared(target) > 0.0001) text.teleport(target);
        }
    }

    private void mount3DProp(World hub, Location loc, String modelId, float scale) {
        if (isOwnedColumn(hub, loc.getBlockX(), loc.getBlockZ())) return;
        ItemDisplay display = createModelDisplay(hub, loc, modelId, scale);
        display.getPersistentDataContainer().set(propModelKey, PersistentDataType.STRING, modelId);
    }

    private ItemDisplay createModelDisplay(World hub, Location loc, String modelId, float scale) {
        hub.getChunkAt(loc).load();
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey("yuumi97", modelId));
            stack.setItemMeta(meta);
        }
        ItemDisplay display = (ItemDisplay) hub.spawnEntity(loc, EntityType.ITEM_DISPLAY);
        display.setItemStack(stack);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
        display.setBillboard(Display.Billboard.FIXED);
        display.setTransformation(new Transformation(
                new Vector3f(0, 0, 0),
                new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f(0, 0, 1, 0)
        ));
        display.setPersistent(true);
        return display;
    }

    public Set<String> scanBundledPackModels() {
        Set<String> found = new HashSet<>();
        File zipFile = new File(plugin.getDataFolder().getParentFile().getParentFile(), "resourcepack/Yuumi97Pack.zip");
        if (!zipFile.exists()) return found;
        String prefix = "assets/yuumi97/items/";
        try (ZipFile zf = new ZipFile(zipFile)) {
            zf.stream().forEach(entry -> {
                String name = entry.getName();
                if (name.startsWith(prefix) && name.endsWith(".json")) {
                    found.add(name.substring(prefix.length(), name.length() - 5));
                }
            });
        } catch (Exception ignored) {
        }
        return found;
    }

    // ==================== 5. 허브 도시 보호 & NPC 클릭 이벤트 ====================

    @EventHandler
    public void onRightClickHubNpc(PlayerInteractEntityEvent event) {
        routeNpcClick(event);
    }

    @EventHandler
    public void onPreciseRightClickHubNpc(PlayerInteractAtEntityEvent event) {
        routeNpcClick(event);
    }

    private void routeNpcClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Entity clicked = event.getRightClicked();
        String role = clicked.getPersistentDataContainer().get(npcRoleKey, PersistentDataType.STRING);
        if (role == null) return;

        event.setCancelled(true);
        Player p = event.getPlayer();
        UUID playerId = p.getUniqueId();
        int tick = Bukkit.getCurrentTick();
        Map<String, Integer> roleTicks = npcClickTicks.computeIfAbsent(playerId, ignored -> new HashMap<>());
        Integer previous = roleTicks.put(role, tick);
        if (previous != null && previous == tick) return;
        if(role.equals("guidebook_kiosk")){guidebookInteraction.accept(p,p.isSneaking());return;}
        p.playSound(clicked.getLocation(), Sound.ENTITY_CAT_PURREOW, 1.0f, 1.15f);
        npcActionRouter.accept(p, role);
    }

    @EventHandler
    public void onNpcPlayerQuit(PlayerQuitEvent event) {
        npcClickTicks.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        World hub = getHubWorld();
        if (hub == null || !hub.equals(event.getBlock().getWorld())) return;
        Player p = event.getPlayer();
        if (plotManager != null && plotManager.canBuildInHub(p, event.getBlock().getLocation())) return;

        event.setCancelled(true);
        p.sendActionBar(TextUtil.mm("<red>🐾 고양이 허브 공공 구역 및 타인의 분양지는 훼손할 수 없습니다!</red>"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        World hub = getHubWorld();
        if (hub == null || !hub.equals(event.getBlock().getWorld())) return;
        Player p = event.getPlayer();
        if (plotManager != null && plotManager.canBuildInHub(p, event.getBlock().getLocation())) return;

        event.setCancelled(true);
        p.sendActionBar(TextUtil.mm("<red>🐾 건축은 남쪽 벚꽃 대교 너머 [내 분양 부지] 안에서만 가능합니다!</red>"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        World hub = getHubWorld();
        if (hub == null || !hub.equals(event.getBlock().getWorld())) return;
        if (plotManager != null && plotManager.canBuildInHub(event.getPlayer(), event.getBlock().getLocation())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        World hub = getHubWorld();
        if (hub == null || !hub.equals(event.getBlock().getWorld())) return;
        if (plotManager != null && plotManager.canBuildInHub(event.getPlayer(), event.getBlock().getLocation())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHubInteract(PlayerInteractEvent event) {
        Block b = event.getClickedBlock();
        if (b == null) return;
        World hub = getHubWorld();
        if (hub == null || !hub.equals(b.getWorld())) return;
        if(event.getAction()==Action.RIGHT_CLICK_BLOCK&&b.getX()==8&&b.getY()==66&&b.getZ()==8) {
            event.setCancelled(true);guidebookInteraction.accept(event.getPlayer(),event.getPlayer().isSneaking());return;
        }

        // Trampling farmland protection
        if (event.getAction() == Action.PHYSICAL && b.getType() == Material.FARMLAND) {
            if (plotManager == null || !plotManager.canBuildInHub(event.getPlayer(), b.getLocation())) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Material m = b.getType();

        if(b.getX()>=-36&&b.getX()<=-8&&b.getZ()>=-44&&b.getZ()<=-20
                &&(m==Material.FURNACE||m==Material.BLAST_FURNACE||m==Material.SMOKER))return;

        // Public Hub utility blocks everyone is welcome to use!
        if (m == Material.ENDER_CHEST || m == Material.ENCHANTING_TABLE || m == Material.CRAFTING_TABLE
                || m == Material.ANVIL || m == Material.CHIPPED_ANVIL || m == Material.DAMAGED_ANVIL
                || m == Material.GRINDSTONE || m == Material.SMITHING_TABLE || m == Material.LOOM
                || m == Material.CARTOGRAPHY_TABLE || m == Material.STONECUTTER || m == Material.BELL
                || m == Material.CAULDRON || m == Material.WATER_CAULDRON) {
            // Keep anvil infinitely undamaged in Hub!
            if (m == Material.CHIPPED_ANVIL || m == Material.DAMAGED_ANVIL) {
                b.setType(Material.ANVIL, false);
            }
            return;
        }

        // BreweryX aging barrels in Cherry Tavern (X >= 70, Z >= 6) are open to players
        if (m == Material.BARREL && b.getX() >= 70 && b.getZ() >= 6 && b.getZ() <= 32) {
            return;
        }

        // Protect private plot chests/barrels/furnaces/hoppers/shulkers from outsiders!
        if (b.getState() instanceof Container) {
            if (plotManager == null || !plotManager.canBuildInHub(event.getPlayer(), b.getLocation())) {
                event.setCancelled(true);
                event.getPlayer().sendActionBar(TextUtil.mm("<red>🔒 보호된 보관함입니다!</red>"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        World hub = getHubWorld();
        if (hub != null && hub.equals(event.getLocation().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        World hub = getHubWorld();
        if (hub == null || !hub.equals(event.getEntity().getWorld())) return;
        if (event.getRemover() instanceof Player p && plotManager != null && plotManager.canBuildInHub(p, event.getEntity().getLocation())) {
            return;
        }
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity victim = event.getEntity();
        if (victim.getPersistentDataContainer().has(new NamespacedKey(plugin, "yuumi_training_dummy"), PersistentDataType.BYTE)) return;
        if (victim.getPersistentDataContainer().has(npcRoleKey, PersistentDataType.STRING)
                || victim.getPersistentDataContainer().has(hubDecorKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            return;
        }

        World hub = getHubWorld();
        if (hub == null || !hub.equals(victim.getWorld())) return;

        if (victim instanceof Player p) {
            // Peaceful invulnerability everywhere in yuumi_hub EXCEPT inside the Northern Raid Colosseum (Z <= -180)!
            if (p.getLocation().getBlockZ() > -180) {
                event.setCancelled(true);
                return;
            }
            // Inside Raid Colosseum: prevent friendly-fire PvP between players!
            if (event instanceof EntityDamageByEntityEvent edbe) {
                Entity damager = edbe.getDamager();
                if (damager instanceof Player || (damager instanceof Projectile proj && proj.getShooter() instanceof Player)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHungerInHub(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        World hub = getHubWorld();
        if (hub != null && hub.equals(p.getWorld()) && p.getLocation().getBlockZ() > -180) {
            if (event.getFoodLevel() < p.getFoodLevel()) {
                event.setCancelled(true);
            }
        }
    }

    // ==================== 건축 유틸리티 ====================

    private void pavePlazaRect(World hub, int minX, int maxX, int minZ, int maxZ, Material fill, Material border) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (isOwnedColumn(hub, x, z)) continue;
                clearColumn(hub, x, GROUND_Y + 1, GROUND_Y + 16, z);
                boolean edge = (x == minX || x == maxX || z == minZ || z == maxZ);
                hub.getBlockAt(x, GROUND_Y, z).setType(edge ? border : fill, false);
            }
        }
    }

    void clearColumn(World hub, int x, int minY, int maxY, int z) {
        if (isOwnedColumn(hub, x, z)) return;
        for (int y = minY; y <= maxY; y++) {
            Block b = hub.getBlockAt(x, y, z);
            if (!b.getType().isAir()) {
                b.setType(Material.AIR, false);
            }
        }
    }

    private void buildLanternPost(World hub, int x, int baseY, int z) {
        if (isOwnedColumn(hub, x, z)) return;
        hub.getBlockAt(x, baseY, z).setType(Material.CHERRY_FENCE, false);
        hub.getBlockAt(x, baseY + 1, z).setType(Material.CHERRY_FENCE, false);
        hub.getBlockAt(x, baseY + 2, z).setType(Material.CHERRY_FENCE, false);
        hub.getBlockAt(x, baseY + 3, z).setType(Material.LANTERN, false);
    }

    private void clearTaggedHubEntities(World hub) {
        for (Entity e : hub.getEntities()) {
            if (isOwnedColumn(hub, e.getLocation().getBlockX(), e.getLocation().getBlockZ())) continue;
            if (e.getPersistentDataContainer().has(npcRoleKey, PersistentDataType.STRING)
                    || e.getPersistentDataContainer().has(hubDecorKey, PersistentDataType.BYTE)
                    || e.getPersistentDataContainer().has(propModelKey, PersistentDataType.STRING)) {
                e.remove();
            }
        }
    }
}
