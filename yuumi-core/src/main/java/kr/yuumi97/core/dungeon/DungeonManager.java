package kr.yuumi97.core.dungeon;

import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.util.RewardMailbox;
import kr.yuumi97.core.util.TextUtil;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.command.CommandExecutor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.*;
import java.util.zip.ZipFile;

/** Party-local five-room runs. Bukkit entities, inventories and persistence stay on the server thread. */
public final class DungeonManager implements Listener {
    public static final String WORLD_NAME = "yuumi_dungeon";
    private enum Encounter { OBELISKS, WAVES, THIEF }
    private enum Puzzle { RESONANCE, MEMORY, STATUES }
    private enum Boon {
        LIGHTNING("사르르 연쇄 번개", Material.LIGHTNING_ROD, "주변 적 2명에 번개 피해 4 / 내부 쿨 5초"),
        CRESCENT("파동의 반월검기", Material.IRON_SWORD, "4번째 공격마다 전방 5m 반월 피해 6"),
        PURR("골골송 생명 흡수", Material.GLISTERING_MELON_SLICE, "피해량 4% 회복 / 내부 쿨 0.5초"),
        SPEED("슈우우웅 폭주가속", Material.SUGAR, "이동속도 +15%"),
        FORTRESS("아홉 목숨 성채", Material.SHIELD, "최대 체력 +4 / 받는 피해 -5%"),
        FROST("서리발 발자국", Material.BLUE_ICE, "20% 확률 둔화 II 2초 / 내부 쿨 2초"),
        EXECUTION("처형자의 발톱", Material.NETHERITE_SWORD, "체력 30% 이하 적에게 피해 +20%"),
        GOLD("황금 츄르 사냥꾼", Material.SUNFLOWER, "미궁 클리어 금화 +20%");
        final String title; final Material icon; final String description;
        Boon(String title, Material icon, String description) { this.title = title; this.icon = icon; this.description = description; }
    }
    private static final class Menu implements InventoryHolder {
        final String kind; final UUID run; final List<Boon> options;
        Menu(String kind, UUID run) { this(kind, run, List.of()); }
        Menu(String kind, UUID run, List<Boon> options) { this.kind = kind; this.run = run; this.options = options; }
        @Override public Inventory getInventory() { return null; }
    }
    private static final class Consent {
        final UUID id = UUID.randomUUID(); final PartyManager.Party party; final List<UUID> members; final int tier;
        final Set<UUID> accepted = new HashSet<>(); final long deadline;
        Consent(PartyManager.Party party, int tier, int seconds) { this.party = party; this.members = party.members(); this.tier = tier; this.deadline = System.currentTimeMillis() + seconds * 1000L; }
    }
    private static final class Run {
        final UUID id = UUID.randomUUID(); final UUID leader; final LinkedHashSet<UUID> members; final int tier, slot, originalSize;
        final Map<UUID, GameMode> modes = new HashMap<>(); final Set<UUID> spectators = new HashSet<>();
        final Map<UUID, Long> disconnected = new HashMap<>();
        final Map<UUID, Double> coinMultipliers = new HashMap<>();
        final Set<UUID> entities = new HashSet<>(), enemies = new HashSet<>();
        final Map<UUID, Double> logicalHp = new HashMap<>(), maxHp = new HashMap<>(), obelisks = new HashMap<>();
        final Map<UUID, EnumMap<Boon, Integer>> boons = new HashMap<>(); final Map<UUID, List<Boon>> choices = new HashMap<>();
        final Set<UUID> selected = new HashSet<>(), claimed = new HashSet<>();
        final Map<String, Long> procReady = new HashMap<>(); final Map<UUID, Integer> attacks = new HashMap<>();
        final List<Integer> melody = new ArrayList<>(); final List<UUID> roomDecor = new ArrayList<>();
        final List<BukkitTask> tasks = new ArrayList<>(); final long started = System.currentTimeMillis();
        final Set<ChunkRef> chunks = new HashSet<>();
        int stage = 1, wave = 0, memoryIndex = 0, truth = 0, resonanceSeconds = 0, helperPlate = -1;
        long stageStarted, helperUntil, memoryShownAt; Encounter encounter; Puzzle puzzle;
        boolean awaitingBoon, ended, reward, enraged; UUID boss; BossBar bossBar;
        Run(UUID leader, List<UUID> members, int tier, int slot) {
            this.leader = leader; this.members = new LinkedHashSet<>(members); originalSize = members.size(); this.tier = tier; this.slot = slot;
        }
    }
    private record ChunkRef(int x, int z) {}
    private final JavaPlugin plugin; private final YuumiItems items; private final ChuruManager churu;
    private final FileConfiguration data; private final Runnable save; private final Supplier<Location> hubSpawn;
    private final RewardMailbox mailbox; private final PartyManager parties;
    private final NamespacedKey runKey, kindKey, indexKey, helperKey, speedKey, healthKey;
    private final Map<UUID, Run> runs = new LinkedHashMap<>(), participants = new HashMap<>();
    private final Map<UUID, Consent> consents = new HashMap<>(); private final Set<String> models = new HashSet<>();
    private World world; private BukkitTask ticker; private boolean internalTeleport, bonusDamage, shuttingDown;
    private ToDoubleFunction<Player> coinMultiplier = p -> 1.0;
    private BiConsumer<Player, Integer> completionXp = (p, amount) -> {};

    public DungeonManager(JavaPlugin plugin, YuumiItems items, ChuruManager churu, FileConfiguration data,
                          Runnable save, Supplier<Location> hubSpawn, RewardMailbox mailbox) {
        this.plugin = plugin; this.items = items; this.churu = churu; this.data = data;
        this.save = save; this.hubSpawn = hubSpawn; this.mailbox = mailbox;
        runKey = new NamespacedKey(plugin, "dungeon_run"); kindKey = new NamespacedKey(plugin, "dungeon_kind");
        indexKey = new NamespacedKey(plugin, "dungeon_index"); helperKey = new NamespacedKey(plugin, "dungeon_solo_bell");
        speedKey = new NamespacedKey(plugin, "dungeon_boon_speed"); healthKey = new NamespacedKey(plugin, "dungeon_boon_health");
        parties = new PartyManager(id -> participants.containsKey(id) || consents.containsKey(id), this::gateLocation);
    }
    public PartyManager getPartyManager() { return parties; }
    public void setCoinMultiplier(ToDoubleFunction<Player> multiplier) { coinMultiplier = multiplier; }
    public void setCompletionXp(BiConsumer<Player, Integer> callback) { completionXp = callback; }
    public int getClearCount(UUID id) { return data.getInt("dungeon.stats." + id + ".clears", 0); }
    public boolean isParticipant(UUID id) { return participants.containsKey(id); }
    public boolean isParticipant(Player player) { return isParticipant(player.getUniqueId()); }
    public boolean handlesClinic(Player player) { return isParticipant(player) || data.contains("dungeon.recovery." + player.getUniqueId()); }
    public Collection<Player> partyMembers(Player player) {
        Run run = participants.get(player.getUniqueId());
        if (run == null) return parties.onlineMembers(player);
        return online(run, false);
    }
    public boolean isDungeonMob(Entity entity) { return entity.getPersistentDataContainer().has(runKey, PersistentDataType.STRING) && "enemy".equals(kind(entity)); }
    public boolean canAffect(Player caster, Entity target) {
        Run run = participants.get(caster.getUniqueId());
        if (run == null) return !WORLD_NAME.equals(target.getWorld().getName());
        if (run.ended || run.spectators.contains(caster.getUniqueId())) return false;
        if (target instanceof Player other) return participants.get(other.getUniqueId()) == run && !run.spectators.contains(other.getUniqueId());
        return run.id.toString().equals(target.getPersistentDataContainer().get(runKey, PersistentDataType.STRING)) && "enemy".equals(kind(target));
    }
    public static final class VoidChunkGenerator extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
    }
    public void start() {
        world = Bukkit.createWorld(new WorldCreator(WORLD_NAME).generator(new VoidChunkGenerator()).generateStructures(false));
        if (world == null) throw new IllegalStateException("Cannot create yuumi_dungeon");
        world.setGameRule(GameRule.KEEP_INVENTORY, true); world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false); world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false); world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setTime(18000); world.setDifficulty(Difficulty.NORMAL); world.setPVP(false);
        for (Entity entity : world.getEntities()) if (!(entity instanceof Player)) entity.remove();
        scanModels(); recoverInterruptedRuns();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
        for (Player player : Bukkit.getOnlinePlayers()) recover(player);
        plugin.getLogger().info("[DungeonManager] yuumi_dungeon loaded; party instances and restart recovery ready.");
    }
    private void scanModels() {
        java.io.File pack = new java.io.File(plugin.getServer().getWorldContainer(), "resourcepack/Yuumi97Pack.zip");
        if (!pack.isFile()) pack = new java.io.File(plugin.getDataFolder(), "Yuumi97Pack.zip");
        if (!pack.isFile()) return;
        try (ZipFile zip = new ZipFile(pack)) {
            zip.stream().map(java.util.zip.ZipEntry::getName).filter(n -> n.startsWith("assets/yuumi97/items/") && n.endsWith(".json"))
                    .forEach(n -> models.add(n.substring(n.lastIndexOf('/') + 1, n.length() - 5)));
        } catch (java.io.IOException ex) { plugin.getLogger().warning("[DungeonManager] Pack scan: " + ex.getMessage()); }
    }
    private double number(String suffix, double fallback) { return plugin.getConfig().getDouble("dungeon." + suffix, fallback); }
    private int integer(String suffix, int fallback) { return Math.max(1, plugin.getConfig().getInt("dungeon." + suffix, fallback)); }
    private double boon(String type, String field, double fallback) { return number("boons." + type + "." + field, fallback); }
    private String path(Run run) { return "dungeon.runs." + run.id; }
    private Location gateLocation() { Location spawn = hubSpawn.get(); return spawn == null ? null : new Location(spawn.getWorld(), 18.5, 66, -55.5); }
    private Location center(Run run) { return new Location(world, run.slot * 800 + .5, 65, (run.stage - 1) * 48 + .5); }
    private List<Player> online(Run run, boolean living) {
        List<Player> result = new ArrayList<>();
        for (UUID id : run.members) { Player player = Bukkit.getPlayer(id); if (player != null && player.isOnline() && (!living || !run.spectators.contains(id)) && participants.get(id) == run) result.add(player); }
        return result;
    }
    private void tell(Run run, String message) { for (Player player : online(run, false)) player.sendMessage(TextUtil.msg(message)); }
    private void teleport(Player player, Location location) {
        internalTeleport = true;
        try { if (!player.teleport(location)) throw new IllegalStateException("Teleport refused for " + player.getName()); }
        finally { internalTeleport = false; }
    }
    private ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material); ItemMeta meta = item.getItemMeta(); meta.displayName(TextUtil.mm(name)); meta.lore(TextUtil.lore(lore)); item.setItemMeta(meta); return item;
    }
    private Inventory menu(Player player, Menu holder, int size, String name) {
        Inventory inv = Bukkit.createInventory(holder, size, TextUtil.mm(name));
        ItemStack glass = icon(Material.BLACK_STAINED_GLASS_PANE, " "); for (int i = 0; i < size; i++) inv.setItem(i, glass);
        return inv;
    }
    public void openGateGui(Player player) {
        Consent consent = consents.get(player.getUniqueId());
        if (consent != null) { openConsent(player, consent); return; }
        if (isParticipant(player)) { player.sendMessage(TextUtil.msg("<yellow>이미 미궁에 참여 중입니다.")); return; }
        Inventory inv = menu(player, new Menu("gate", null), 45, "<aqua><bold>🗝 문턱의 미궁 집결 관문");
        for (int tier = 1; tier <= 3; tier++) inv.setItem(18 + tier * 2, icon(tier == 1 ? Material.BOOKSHELF : tier == 2 ? Material.MAGMA_BLOCK : Material.CRYING_OBSIDIAN,
                "<gold>제" + tier + "미궁 · " + new String[]{"별빛 서고", "홍련의 회랑", "공허의 심연"}[tier - 1],
                "<gray>1~8인 · 5스테이지 · 제한 " + integer("run-seconds", 1800) / 60 + "분",
                tier == 1 ? "<green>입장 무료" : "<yellow>파티장 열쇠 1개 (제단 해금과 무관)", "<gray>전원 관문 12m 이내 집결 및 입장 동의 필요"));
        inv.setItem(40, icon(Material.SUNFLOWER, "<yellow>고대 고양이 금화 교환소", "<gray>책갈피 유물 · 망각 물약 · 입장 열쇠"));
        player.openInventory(inv);
    }
    private void openShop(Player player) {
        Inventory inv = menu(player, new Menu("shop", null), 36, "<gold><bold>미궁 수문장 교환소");
        String[] ids = {"charm_crimson_claw", "charm_starlight_aegis", "charm_golden_whisker", "charm_vampiric_purr", "you_and_me_bell", "nine_lives_charm", "respec_potion"};
        for (int i = 0; i < ids.length; i++) {
            ItemStack item = items.create(ids[i]); ItemMeta meta = item.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(TextUtil.mm("<yellow>금화 " + shopCost(ids[i]) + "개로 교환")); meta.lore(lore); item.setItemMeta(meta); inv.setItem(10 + i, item);
        }
        inv.setItem(28, icon(Material.TRIAL_KEY, "<aqua>미궁 입장 열쇠", "<yellow>" + TextUtil.fmt(number("key-price", 3000)) + " 츄르"));
        player.openInventory(inv);
    }
    private int shopCost(String id) { return integer("shop-coins." + id, id.equals("respec_potion") ? 12 : id.equals("nine_lives_charm") ? 8 : 24); }
    private boolean atGate(Player player) {
        Location gate = gateLocation(); return gate != null && player.getWorld().equals(gate.getWorld()) && player.getLocation().distanceSquared(gate) <= 144 && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR;
    }
    private void requestConsent(Player leader, int tier) {
        if (!atGate(leader)) { parties.guide(leader); return; }
        PartyManager.Party party = parties.ensureSolo(leader);
        if (!party.leader().equals(leader.getUniqueId())) { leader.sendMessage(TextUtil.msg("<red>파티장만 입장을 요청할 수 있습니다.")); return; }
        for (UUID id : party.members()) {
            Player member = Bukkit.getPlayer(id);
            if (member == null || !atGate(member) || isParticipant(id) || consents.containsKey(id)) { leader.sendMessage(TextUtil.msg("<red>파티원 전원이 온라인 상태로 관문 12m 안에 모여야 합니다.")); return; }
        }
        if (tier > 1 && count(leader, "labyrinth_key") < 1) { leader.sendMessage(TextUtil.msg("<red>파티장에게 미궁 열쇠 1개가 필요합니다.")); return; }
        Consent consent = new Consent(party, tier, integer("consent-seconds", 30));
        for (UUID id : consent.members) { consents.put(id, consent); Player member = Bukkit.getPlayer(id); if (member != null) openConsent(member, consent); }
    }
    private void openConsent(Player player, Consent consent) {
        Inventory inv = menu(player, new Menu("consent", consent.id), 27, "<aqua><bold>미궁 입장 동의 (" + integer("consent-seconds", 30) + "초)");
        inv.setItem(11, icon(Material.LIME_CONCRETE, "<green>제" + consent.tier + "미궁 입장에 동의", "<gray>전원 동의하면 입장합니다.", "<gray>실패/중도포기 시 열쇠는 반환되지 않습니다."));
        inv.setItem(15, icon(Material.RED_CONCRETE, "<red>입장 취소")); player.openInventory(inv);
    }
    private void cancelConsent(Consent consent, String reason) {
        for (UUID id : consent.members) { consents.remove(id, consent); Player player = Bukkit.getPlayer(id); if (player != null) { if (player.getOpenInventory().getTopInventory().getHolder() instanceof Menu holder && "consent".equals(holder.kind)) player.closeInventory(); player.sendMessage(TextUtil.msg(reason)); } }
    }
    private void enter(Consent consent) {
        Player leader = Bukkit.getPlayer(consent.party.leader());
        if (leader == null || !consent.members.equals(consent.party.members())) { cancelConsent(consent, "<red>파티 구성이 바뀌어 취소되었습니다."); return; }
        for (UUID id : consent.members) { Player player = Bukkit.getPlayer(id); if (player == null || !atGate(player) || isParticipant(id)) { cancelConsent(consent, "<red>모든 파티원이 관문 가까이에 있어야 합니다."); return; } }
        if (consent.tier > 1 && count(leader, "labyrinth_key") < 1) { cancelConsent(consent, "<red>파티장의 열쇠가 없어 입장을 취소했습니다."); return; }
        int slot = 0; Set<Integer> occupied = new HashSet<>(); for (Run active : runs.values()) occupied.add(active.slot); while (occupied.contains(slot)) slot++;
        Run run = new Run(leader.getUniqueId(), consent.members, consent.tier, slot);
        try {
            buildRooms(run);
            data.set(path(run) + ".status", "PREPARING"); data.set(path(run) + ".leader", run.leader.toString()); data.set(path(run) + ".key-consumed", false);
            for (UUID id : run.members) {
                Player player = Bukkit.getPlayer(id); run.modes.put(id, player.getGameMode());
                run.coinMultipliers.put(id, coinMultiplier.applyAsDouble(player));
                data.set("dungeon.recovery." + id + ".gamemode", player.getGameMode().name()); data.set("dungeon.recovery." + id + ".run", run.id.toString());
            }
            save.run();
            if (run.tier > 1) { if (!consume(leader, "labyrinth_key", 1)) throw new IllegalStateException("Entry key disappeared"); data.set(path(run) + ".key-consumed", true); save.run(); }
            for (UUID id : consent.members) consents.remove(id, consent);
            runs.put(run.id, run);
            for (UUID id : run.members) { Player player = Bukkit.getPlayer(id); participants.put(id, run); player.closeInventory(); player.setGameMode(GameMode.ADVENTURE); teleport(player, center(run)); }
            data.set(path(run) + ".status", "RUNNING"); save.run(); beginStage(run, 1);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("[DungeonManager] Entry rolled back: " + ex.getMessage());
            cancelConsent(consent, "<red>미궁 입장 준비에 실패하여 비용을 돌려드립니다."); endRun(run, "시스템 입장 실패", true);
        }
    }
    private int count(Player player, String id) { int count = 0; for (ItemStack item : player.getInventory().getContents()) if (items.isCustomItem(item, id)) count += item.getAmount(); return count; }
    private boolean consume(Player player, String id, int amount) {
        if (count(player, id) < amount) return false;
        for (ItemStack item : player.getInventory().getContents()) if (items.isCustomItem(item, id)) { int take = Math.min(amount, item.getAmount()); item.setAmount(item.getAmount() - take); amount -= take; if (amount == 0) break; }
        return true;
    }
    private void buildRooms(Run run) {
        Material floor = switch (run.tier) { case 2 -> Material.POLISHED_BLACKSTONE_BRICKS; case 3 -> Material.END_STONE_BRICKS; default -> Material.POLISHED_DEEPSLATE; };
        Material trim = switch (run.tier) { case 2 -> Material.NETHER_BRICKS; case 3 -> Material.PURPUR_BLOCK; default -> Material.CHISELED_BOOKSHELF; };
        Material light = run.tier == 2 ? Material.SHROOMLIGHT : run.tier == 3 ? Material.PEARLESCENT_FROGLIGHT : Material.SEA_LANTERN;
        int cx = run.slot * 800;
        for (int stage = 0; stage < 5; stage++) {
            int cz = stage * 48;
            // Keep the complete run resident during the reconnect grace; an unloaded mob must never count as a kill.
            for (int chunkX = Math.floorDiv(cx - 15, 16); chunkX <= Math.floorDiv(cx + 15, 16); chunkX++)
                for (int chunkZ = Math.floorDiv(cz - 15, 16); chunkZ <= Math.floorDiv(cz + 15, 16); chunkZ++) {
                    world.addPluginChunkTicket(chunkX, chunkZ, plugin); run.chunks.add(new ChunkRef(chunkX, chunkZ));
                }
            for (int x = -15; x <= 15; x++) for (int z = -15; z <= 15; z++) {
                world.getBlockAt(cx + x, 63, cz + z).setType(Material.BARRIER, false);
                world.getBlockAt(cx + x, 64, cz + z).setType((x % 7 == 0 && z % 7 == 0) ? light : floor, false);
                for (int y = 65; y <= 79; y++) {
                    boolean edge = Math.abs(x) == 15 || Math.abs(z) == 15;
                    world.getBlockAt(cx + x, y, cz + z).setType(edge ? ((x + z + y) % 5 == 0 ? trim : floor) : Material.AIR, false);
                }
                world.getBlockAt(cx + x, 80, cz + z).setType((Math.abs(x) <= 1 || Math.abs(z) <= 1) ? light : floor, false);
            }
            // Four genuine opaque pillars are both architecture and the boss's line-of-sight cover.
            for (int x : new int[]{-6, 6}) for (int z : new int[]{-5, 5}) {
                for (int y = 65; y <= 77; y++) world.getBlockAt(cx + x, y, cz + z).setType(y == 70 ? light : trim, false);
                for (int arch = -3; arch <= 3; arch++) world.getBlockAt(cx + x + arch, 77 + (Math.abs(arch) == 3 ? -1 : 0), cz + z).setType(trim, false);
            }
            if (run.tier == 1) for (int x = -12; x <= 12; x += 6) for (int y = 65; y <= 68; y++) world.getBlockAt(cx + x, y, cz + 14).setType(Material.BOOKSHELF, false);
        }
    }
    private <T extends Entity> T tag(T entity, Run run, String kind) {
        entity.getPersistentDataContainer().set(runKey, PersistentDataType.STRING, run.id.toString());
        entity.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind);
        entity.setPersistent(false); run.entities.add(entity.getUniqueId()); return entity;
    }
    private String kind(Entity entity) { return entity.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING); }
    private Run entityRun(Entity entity) {
        String id = entity.getPersistentDataContainer().get(runKey, PersistentDataType.STRING);
        if (id == null) return null;
        try { return runs.get(UUID.fromString(id)); } catch (IllegalArgumentException ignored) { return null; }
    }
    private void text(Run run, Location at, String value) {
        TextDisplay text = tag(world.spawn(at, TextDisplay.class), run, "decor"); text.text(TextUtil.mm(value));
        text.setBillboard(Display.Billboard.CENTER); text.setSeeThrough(false); text.setLineWidth(220); run.roomDecor.add(text.getUniqueId());
    }
    private void prop(Run run, String model, Location at, Material fallback, String interaction, int index) {
        if (models.contains(model)) {
            ItemDisplay display = tag(world.spawn(at, ItemDisplay.class), run, "decor");
            ItemStack item = new ItemStack(Material.PAPER); ItemMeta meta = item.getItemMeta(); meta.setItemModel(new NamespacedKey("yuumi97", model)); item.setItemMeta(meta); display.setItemStack(item);
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.5f), new AxisAngle4f())); run.roomDecor.add(display.getUniqueId());
        } else {
            BlockDisplay display = tag(world.spawn(at.clone().add(-.5, 0, -.5), BlockDisplay.class), run, "decor"); display.setBlock(fallback.createBlockData()); run.roomDecor.add(display.getUniqueId());
        }
        if (interaction != null) {
            Interaction hitbox = tag(world.spawn(at, Interaction.class), run, interaction); hitbox.setInteractionWidth(1.8f); hitbox.setInteractionHeight(2.2f); hitbox.setResponsive(true);
            hitbox.getPersistentDataContainer().set(indexKey, PersistentDataType.INTEGER, index); run.roomDecor.add(hitbox.getUniqueId());
            if (interaction.equals("obelisk")) run.obelisks.put(hitbox.getUniqueId(), number("obelisk-health", 60) * run.tier);
        }
    }
    private void setAttr(LivingEntity entity, Attribute attr, double value) { AttributeInstance instance = entity.getAttribute(attr); if (instance != null) instance.setBaseValue(value); }
    private double tierValue(String field, int tier, double a, double b, double c) { return number("tiers." + tier + "." + field, tier == 1 ? a : tier == 2 ? b : c); }
    private LivingEntity enemy(Run run, EntityType type, Location at, double hp, double damage, String name) {
        LivingEntity entity = tag((LivingEntity) world.spawnEntity(at, type), run, "enemy");
        entity.customName(TextUtil.mm(name)); entity.setCustomNameVisible(true); entity.setRemoveWhenFarAway(false);
        double total = hp * DungeonRules.healthScale(run.originalSize);
        run.logicalHp.put(entity.getUniqueId(), total); run.maxHp.put(entity.getUniqueId(), total); run.enemies.add(entity.getUniqueId());
        // The shipped Spigot cap is 1024 HP. Logical reserve preserves scaling without editing server configuration.
        setAttr(entity, Attribute.MAX_HEALTH, Math.min(1024, total)); entity.setHealth(Math.min(1024, total)); setAttr(entity, Attribute.ATTACK_DAMAGE, damage);
        setAttr(entity, Attribute.MOVEMENT_SPEED, .27); entity.setCanPickupItems(false);
        if (entity instanceof Zombie zombie) { zombie.setAdult(); zombie.setShouldBurnInDay(false); }
        if (entity.getEquipment() != null) { entity.getEquipment().setItemInMainHandDropChance(0); entity.getEquipment().setHelmetDropChance(0); }
        Player target = online(run, true).stream().findFirst().orElse(null); if (entity instanceof Mob mob && target != null) mob.setTarget(target);
        return entity;
    }
    private void spawnWave(Run run, boolean skeletonsOnly) {
        int count = skeletonsOnly ? 2 + (run.originalSize - 1) / 3 : DungeonRules.waveSize(run.originalSize);
        Location origin = center(run);
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / count;
            EntityType type = skeletonsOnly || i % 2 == 0 ? EntityType.SKELETON : run.tier == 2 ? EntityType.WITHER_SKELETON : EntityType.HUSK;
            enemy(run, type, origin.clone().add(Math.cos(angle) * 10, 0, Math.sin(angle) * 10),
                    tierValue("mob-health", run.tier, 24, 36, 50), tierValue("mob-damage", run.tier, 4, 6, 8), "<gray>미궁의 파수꾼");
        }
    }
    private void removeEntities(Run run, Collection<UUID> ids) {
        for (UUID id : new ArrayList<>(ids)) { Entity entity = Bukkit.getEntity(id); if (entity != null) entity.remove(); run.entities.remove(id); run.enemies.remove(id); run.logicalHp.remove(id); run.maxHp.remove(id); }
        ids.clear();
    }
    private void clearRoom(Run run) {
        removeEntities(run, run.enemies); removeEntities(run, run.roomDecor); run.obelisks.clear();
        for (BukkitTask task : run.tasks) task.cancel(); run.tasks.clear();
        if (run.bossBar != null) { for (Player player : online(run, false)) player.hideBossBar(run.bossBar); run.bossBar = null; }
    }
    private void beginStage(Run run, int stage) {
        if (run.ended) return;
        clearRoom(run); run.stage = stage; run.stageStarted = System.currentTimeMillis(); run.wave = 0; run.awaitingBoon = false;
        run.selected.clear(); run.choices.clear(); run.memoryIndex = 0; run.resonanceSeconds = 0; run.helperPlate = -1;
        for (Player player : online(run, false)) { revive(run, player); teleport(player, center(run).clone().add((ThreadLocalRandom.current().nextDouble() - .5) * 3, 0, 10)); }
        text(run, center(run).add(0, 7, 0), "<aqua><bold>제" + run.tier + "미궁 │ Stage " + stage + " / 5");
        tell(run, "<aqua>문턱의 미궁 <bold>" + stage + " / 5</bold>");
        if (stage == 1) {
            run.encounter = Encounter.values()[ThreadLocalRandom.current().nextInt(3)];
            switch (run.encounter) {
                case OBELISKS -> {
                    prop(run, "prop_dungeon_obelisk", center(run).add(-9, 0, -7), Material.AMETHYST_BLOCK, "obelisk", 0);
                    prop(run, "prop_dungeon_obelisk", center(run).add(9, 0, -7), Material.AMETHYST_BLOCK, "obelisk", 1);
                    prop(run, "prop_dungeon_obelisk", center(run).add(0, 0, 8), Material.AMETHYST_BLOCK, "obelisk", 2);
                    spawnWave(run, false); tell(run, "<light_purple>봉인 수정탑 3기를 먼저 파괴하세요! 봉인 중에는 적이 피해를 받지 않습니다.");
                }
                case WAVES -> { run.wave = 1; spawnWave(run, false); tell(run, "<yellow>원거리·근접 포위 돌파전! 2개 웨이브를 격파하세요."); }
                case THIEF -> spawnThief(run);
            }
        } else if (stage == 2) beginBoon(run);
        else if (stage == 3) beginPuzzle(run);
        else if (stage == 4) {
            enemy(run, run.tier == 1 ? EntityType.IRON_GOLEM : EntityType.WITHER_SKELETON, center(run).add(0, 0, -7),
                    tierValue("elite-health", run.tier, 140, 240, 360), tierValue("elite-damage", run.tier, 7, 10, 13), "<red><bold>엘리트 미궁 수문장");
            tell(run, "<red>엘리트 수문장을 격파하면 모닥불 회복과 두 번째 축복이 열립니다.");
        } else spawnBoss(run);
    }
    private void spawnThief(Run run) {
        run.stageStarted = System.currentTimeMillis();
        LivingEntity thief = enemy(run, EntityType.OCELOT, center(run), 65 * run.tier, 0, "<gold><bold>황금 보물 도둑냥");
        thief.setAI(false); tell(run, "<gold>45초 안에 황금 보물 도둑냥을 잡으세요! 실패하면 방을 재도전합니다.");
    }
    private void beginBoon(Run run) {
        run.awaitingBoon = true; run.selected.clear(); run.choices.clear();
        prop(run, "prop_boon_altar", center(run), Material.ENCHANTING_TABLE, "boon", 0);
        for (UUID id : run.members) if (participants.get(id) == run) {
            List<Boon> options = new ArrayList<>(List.of(Boon.values())); Collections.shuffle(options); run.choices.put(id, List.copyOf(options.subList(0, 3)));
            Player player = Bukkit.getPlayer(id); if (player != null) openBoon(player, run);
        }
    }
    private void openBoon(Player player, Run run) {
        if (!run.awaitingBoon || run.selected.contains(player.getUniqueId())) return;
        List<Boon> options = run.choices.get(player.getUniqueId()); if (options == null) return;
        Inventory inv = menu(player, new Menu("boon", run.id, options), 27, "<light_purple><bold>축복 제단 · 3택 1");
        for (int i = 0; i < 3; i++) { Boon boon = options.get(i); inv.setItem(10 + i * 3, icon(boon.icon, "<gold>" + boon.title, "<gray>" + boon.description, "<yellow>현재 " + stacks(run, player, boon) + "중첩 · 클릭하여 선택")); }
        player.openInventory(inv);
    }
    private void beginPuzzle(Run run) {
        run.puzzle = Puzzle.values()[ThreadLocalRandom.current().nextInt(3)];
        if (run.puzzle == Puzzle.RESONANCE) {
            // The required subset can grow when disconnected members return; all four pads must exist already.
            for (int i = 0; i < 4; i++) { Location plate = plate(run, i); world.getBlockAt(plate.clone().add(0, -1, 0)).setType(Material.GOLD_BLOCK, false); world.getBlockAt(plate).setType(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, false); }
            giveSoloBell(run);
            tell(run, "<yellow>공명 발판을 동시에 3초 동안 밟으세요. 생존자가 혼자이면 영혼 분신 방울을 우클릭하세요.");
        } else if (run.puzzle == Puzzle.MEMORY) {
            for (int i = 0; i < 4; i++) {
                prop(run, "", crystal(run, i), new Material[]{Material.REDSTONE_BLOCK, Material.LAPIS_BLOCK, Material.EMERALD_BLOCK, Material.GOLD_BLOCK}[i], "crystal", i);
                run.melody.add(ThreadLocalRandom.current().nextInt(4));
            }
            run.memoryShownAt = System.currentTimeMillis(); tell(run, "<aqua>4색 수정의 음계를 기억하고, 같은 순서로 수정을 우클릭하세요.");
        } else {
            run.truth = ThreadLocalRandom.current().nextInt(4);
            for (int i = 0; i < 4; i++) {
                prop(run, "", center(run).add(-9 + i * 6, 0, -4), Material.CHISELED_STONE_BRICKS, "statue", i);
                text(run, center(run).add(-9 + i * 6, 3, -4), "<yellow>" + (i + 1) + "번 석상");
            }
            String clues = "<gold>진실한 석상은 " + ((run.truth + 1) % 2 == 0 ? "짝수" : "홀수") + " 번호입니다.\n" + (run.truth < 2 ? "앞쪽 두 번호(1, 2) 중 하나입니다." : "뒤쪽 두 번호(3, 4) 중 하나입니다.");
            text(run, center(run).add(0, 5, 7), clues); tell(run, clues + " <gray>정답 석상을 우클릭하세요.");
        }
    }
    private Location plate(Run run, int i) { return center(run).add(i % 2 == 0 ? -8 : 8, 0, i < 2 ? -8 : 8); }
    private Location crystal(Run run, int i) { return center(run).add(i % 2 == 0 ? -8 : 8, 0, i < 2 ? -7 : 7); }
    private void giveSoloBell(Run run) {
        List<Player> living = online(run, true); if (living.size() != 1) return;
        Player solo = living.getFirst();
        for (ItemStack item : solo.getInventory().getContents()) if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(helperKey, PersistentDataType.STRING)) return;
        ItemStack bell = icon(Material.BELL, "<aqua>영혼 분신 방울", "<gray>혼자일 때 보조 공명 발판을 20초간 활성화합니다.", "<gray>미궁을 떠나면 사라집니다.");
        ItemMeta meta = bell.getItemMeta(); meta.getPersistentDataContainer().set(helperKey, PersistentDataType.STRING, run.id.toString()); bell.setItemMeta(meta);
        if (solo.getInventory().firstEmpty() >= 0) solo.getInventory().addItem(bell);
        else tell(run, "<yellow>인벤토리가 가득 찼습니다. 발판 위에서 빈손 우클릭으로도 분신을 부를 수 있습니다.");
    }
    private void puzzleMistake(Run run) {
        tell(run, "<red>공명이 어긋났습니다! 작은 해골 무리가 나타납니다. 다시 도전할 수 있습니다.");
        if (run.enemies.size() < 6) spawnWave(run, true);
        run.memoryIndex = 0; run.memoryShownAt = System.currentTimeMillis();
    }
    private void spawnBoss(Run run) {
        LivingEntity boss = enemy(run, run.tier == 1 ? EntityType.HUSK : EntityType.WITHER_SKELETON, center(run).add(0, 0, -9),
                tierValue("boss-health", run.tier, 300, 500, 800), tierValue("boss-damage", run.tier, 9, 13, 17), "<dark_purple><bold>문턱의 심연 지배자");
        boss.addScoreboardTag("yuumi_dungeon_boss");
        setAttr(boss, Attribute.SCALE, 1.8); setAttr(boss, Attribute.KNOCKBACK_RESISTANCE, .85); run.boss = boss.getUniqueId();
        run.bossBar = BossBar.bossBar(TextUtil.mm("<light_purple>문턱의 심연 지배자"), 1, BossBar.Color.PURPLE, BossBar.Overlay.NOTCHED_10);
        for (Player player : online(run, false)) player.showBossBar(run.bossBar);
        tell(run, "<red>최종 보스전! 장판을 피하고, 전멸기에는 석주 뒤로 숨으세요!");
    }
    private void later(Run run, long ticks, Runnable action) {
        run.tasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (run.ended) return;
            try { action.run(); } catch (RuntimeException ex) {
                plugin.getLogger().warning("[DungeonManager] Run aborted: " + ex.getMessage()); endRun(run, "시스템 오류", true);
            }
        }, ticks));
    }
    private void tick() {
        long now = System.currentTimeMillis();
        for (Consent consent : new HashSet<>(consents.values())) if (now >= consent.deadline) cancelConsent(consent, "<yellow>입장 동의 시간이 지나 취소되었습니다.");
        for (Run run : new ArrayList<>(runs.values())) {
            try { tickRun(run, now); } catch (RuntimeException ex) { plugin.getLogger().warning("[DungeonManager] Run tick failed: " + ex.getMessage()); endRun(run, "시스템 오류", true); }
        }
    }
    private void tickRun(Run run, long now) {
        if (run.ended) return;
        for (Map.Entry<UUID, Long> lost : new ArrayList<>(run.disconnected.entrySet())) if (now >= lost.getValue()) {
            run.disconnected.remove(lost.getKey()); participants.remove(lost.getKey(), run);
            tell(run, "<yellow>재접속 유예 3분이 지나 파티원 1명이 미궁에서 이탈했습니다.");
        }
        if (run.members.stream().noneMatch(id -> participants.get(id) == run)) { endRun(run, run.reward ? "미궁 완료" : "참가자 전원 이탈", false); return; }
        if (run.reward) {
            if (now - run.stageStarted > integer("reward-minutes", 5) * 60_000L) { endRun(run, "보상 수령 시간 종료", false); return; }
            for (Player player : online(run, false)) if (player.getLocation().distanceSquared(center(run).add(0, 0, 11)) < 2.5) { claimLoot(run.id, player.getUniqueId()); leaveMember(run, player, "미궁에서 무사히 복귀했습니다."); }
            return;
        }
        if (now - run.started >= integer("run-seconds", 1800) * 1000L) { endRun(run, "미궁 제한 시간 종료", false); return; }
        List<Player> living = online(run, true);
        boolean waitingLiving = run.disconnected.keySet().stream().anyMatch(id -> !run.spectators.contains(id));
        if (living.isEmpty() && !waitingLiving) { endRun(run, "원정대 전멸", false); return; }
        for (UUID id : new ArrayList<>(run.enemies)) {
            Entity entity = Bukkit.getEntity(id);
            if (entity == null || entity.isDead()) { run.enemies.remove(id); continue; }
            if (entity instanceof Mob mob && !(entity instanceof Ocelot)) {
                if (!living.isEmpty() && (mob.getTarget() == null || mob.getTarget().isDead() || !(mob.getTarget() instanceof Player target) || participants.get(target.getUniqueId()) != run || run.spectators.contains(target.getUniqueId()))) mob.setTarget(living.get(ThreadLocalRandom.current().nextInt(living.size())));
                if (entity.getLocation().distanceSquared(center(run)) > 360) entity.teleport(center(run).add(0, 0, -8));
            }
        }
        if (run.awaitingBoon) {
            for (Player player : online(run, false)) if (!run.selected.contains(player.getUniqueId()) && !(player.getOpenInventory().getTopInventory().getHolder() instanceof Menu holder && holder.kind.equals("boon"))) openBoon(player, run);
            boolean all = run.members.stream().filter(id -> participants.get(id) == run).allMatch(run.selected::contains);
            if (all) { run.awaitingBoon = false; beginStage(run, run.stage == 2 ? 3 : 5); }
            return;
        }
        long elapsed = (now - run.stageStarted) / 1000;
        if (run.stage == 1) {
            if (run.encounter == Encounter.THIEF && !run.enemies.isEmpty()) {
                LivingEntity thief = (LivingEntity) Bukkit.getEntity(run.enemies.iterator().next());
                if (thief != null) {
                    double angle = elapsed * .9; Location next = center(run).add(Math.cos(angle) * 10, 0, Math.sin(angle) * 10); thief.teleport(next);
                    world.spawnParticle(Particle.TOTEM_OF_UNDYING, next.clone().add(0, .8, 0), 8, .25, .25, .25);
                }
                for (Player player : living) player.sendActionBar(TextUtil.mm("<gold>도둑냥 추격 " + Math.max(0, 45 - elapsed) + "초"));
                if (elapsed >= 45) { removeEntities(run, run.enemies); spawnThief(run); }
            } else if (run.enemies.isEmpty() && run.obelisks.isEmpty()) {
                if (run.encounter == Encounter.WAVES && run.wave == 1) { run.wave = 2; spawnWave(run, false); tell(run, "<red>두 번째 포위 웨이브!"); }
                else beginStage(run, 2);
            }
        } else if (run.stage == 3) {
            if (run.puzzle == Puzzle.RESONANCE) tickResonance(run, now);
            else if (run.puzzle == Puzzle.MEMORY) {
                int note = (int) ((now - run.memoryShownAt) / 1000) - 1;
                if (note >= 0 && note < 4) {
                    int color = run.melody.get(note); Location at = crystal(run, color).add(0, 1.5, 0);
                    world.spawnParticle(Particle.END_ROD, at, 30, .5, .5, .5); world.playSound(at, Sound.BLOCK_NOTE_BLOCK_CHIME, 1.5f, .7f + color * .25f);
                    tell(run, "<aqua>음계 " + (note + 1) + ": " + new String[]{"빨강", "파랑", "초록", "노랑"}[color]);
                }
            }
        } else if (run.stage == 4 && run.enemies.isEmpty()) {
            for (Player player : online(run, false)) { revive(run, player); heal(player, maxHealth(player)); player.setFoodLevel(20); }
            prop(run, "", center(run).add(0, 0, 6), Material.SOUL_CAMPFIRE, null, 0);
            tell(run, "<green>회복 모닥불이 원정대를 완전히 회복했습니다. 두 번째 축복을 고르세요."); beginBoon(run);
        } else if (run.stage == 5) tickBoss(run, elapsed);
    }
    private void tickResonance(Run run, long now) {
        List<Player> living = online(run, true); int needed = DungeonRules.plateCount(living.size());
        if (living.size() == 1) giveSoloBell(run);
        boolean all = true;
        for (int i = 0; i < needed; i++) {
            Location point = plate(run, i); boolean occupied = i == run.helperPlate && now < run.helperUntil;
            for (Player player : living) if (player.getLocation().distanceSquared(point) < 2.1) occupied = true;
            if (!occupied) all = false;
            world.spawnParticle(occupied ? Particle.HAPPY_VILLAGER : Particle.END_ROD, point.clone().add(0, .2, 0), 4, .4, .05, .4);
        }
        run.resonanceSeconds = all ? run.resonanceSeconds + 1 : 0;
        if (run.resonanceSeconds >= 3) { tell(run, "<green>모든 공명이 일치했습니다!"); beginStage(run, 4); }
    }
    private void tickBoss(Run run, long elapsed) {
        Entity found = run.boss == null ? null : Bukkit.getEntity(run.boss); if (!(found instanceof LivingEntity boss) || boss.isDead()) return;
        double hp = run.logicalHp.getOrDefault(run.boss, boss.getHealth()), max = run.maxHp.getOrDefault(run.boss, maxHealth(boss));
        run.bossBar.progress((float) Math.max(0, Math.min(1, hp / max)));
        if (!run.enraged && hp <= max * number("boss.enrage-threshold", .25)) {
            run.enraged = true; setAttr(boss, Attribute.ATTACK_DAMAGE, tierValue("boss-damage", run.tier, 9, 13, 17) * (1 + number("boss.enrage-damage", .3))); setAttr(boss, Attribute.MOVEMENT_SPEED, .27 * (1 + number("boss.enrage-speed", .2)));
            tell(run, "<red><bold>심연의 지배자가 광폭화합니다!</bold>");
        }
        int interval = (int) Math.max(1, tierValue("blast-interval-seconds", run.tier, 8, 7, 6));
        if (elapsed > 0 && elapsed % interval == 0) {
            List<Player> living = online(run, true); if (living.isEmpty()) return;
            Location target = living.get(ThreadLocalRandom.current().nextInt(living.size())).getLocation().clone();
            double radius = number("boss.blast-radius", 3.5); long warning = (long) (20 * number("boss.blast-warning-seconds", 1.5));
            warnCircle(target, radius, Color.RED); later(run, Math.max(1, warning / 2), () -> warnCircle(target, radius, Color.ORANGE));
            later(run, Math.max(1, warning), () -> {
                world.spawnParticle(Particle.EXPLOSION, target.clone().add(0, .4, 0), 12, 2, .2, 2);
                world.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, .8f, 1);
                for (Player player : online(run, true)) if (player.getLocation().distanceSquared(target) <= radius * radius) player.damage(tierValue("blast-damage", run.tier, 8, 12, 16) * (run.enraged ? 1 + number("boss.enrage-damage", .3) : 1), boss);
            });
        }
        int firstBeam = integer("boss.beam-first-seconds", 20), beamInterval = integer("boss.beam-interval-seconds", 30);
        if (elapsed >= firstBeam && (elapsed - firstBeam) % beamInterval == 0) {
            tell(run, "<red><bold>석주 뒤로 숨으세요! 3초 후 심연 파동!</bold>");
            for (Player player : online(run, true)) player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, .7f, 1);
            later(run, integer("boss.beam-warning-seconds", 3) * 20L, () -> {
                if (boss.isDead()) return;
                Location eye = boss.getEyeLocation();
                for (Player player : online(run, true)) {
                    Vector ray = player.getEyeLocation().toVector().subtract(eye.toVector());
                    RayTraceResult hit = world.rayTraceBlocks(eye, ray, ray.length(), FluidCollisionMode.NEVER, true);
                    if (hit == null || hit.getHitBlock() == null) { player.damage(maxHealth(player) * number("boss.beam-health-multiplier", 10), boss); player.sendMessage(TextUtil.msg("<red>석주에 숨지 못해 심연 파동에 휘말렸습니다!")); }
                    else player.sendMessage(TextUtil.msg("<green>석주가 심연 파동을 막았습니다!"));
                }
                world.playSound(boss.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1, .7f);
            });
        }
    }
    private void warnCircle(Location center, double radius, Color color) {
        Particle.DustOptions options = new Particle.DustOptions(color, 1.5f);
        for (int i = 0; i < 40; i++) { double a = i * Math.PI * 2 / 40; world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * radius, .1, Math.sin(a) * radius), 1, 0, 0, 0, 0, options); }
    }
    private int stacks(Run run, Player player, Boon boon) { EnumMap<Boon, Integer> owned = run.boons.get(player.getUniqueId()); return owned == null ? 0 : owned.getOrDefault(boon, 0); }
    private double maxHealth(LivingEntity entity) { AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH); return attribute == null ? 20 : attribute.getValue(); }
    private void heal(Player player, double amount) { if (!player.isDead()) player.setHealth(Math.min(maxHealth(player), player.getHealth() + amount)); }
    private void boonAttributes(Run run, Player player) {
        removeModifier(player, Attribute.MOVEMENT_SPEED, speedKey); removeModifier(player, Attribute.MAX_HEALTH, healthKey);
        int speed = stacks(run, player, Boon.SPEED), fortress = stacks(run, player, Boon.FORTRESS);
        AttributeInstance attr = player.getAttribute(Attribute.MOVEMENT_SPEED); if (attr != null && speed > 0) attr.addTransientModifier(new AttributeModifier(speedKey, boon("speed", "amount", .15) * speed, AttributeModifier.Operation.ADD_SCALAR));
        attr = player.getAttribute(Attribute.MAX_HEALTH); if (attr != null && fortress > 0) attr.addTransientModifier(new AttributeModifier(healthKey, boon("fortress", "health", 4) * fortress, AttributeModifier.Operation.ADD_NUMBER));
    }
    private void removeModifier(Player player, Attribute attr, NamespacedKey key) {
        AttributeInstance instance = player.getAttribute(attr); if (instance == null) return;
        for (AttributeModifier modifier : new ArrayList<>(instance.getModifiers())) if (modifier.getKey().equals(key)) instance.removeModifier(modifier);
        if (attr == Attribute.MAX_HEALTH && !player.isDead() && player.getHealth() > instance.getValue()) player.setHealth(instance.getValue());
    }
    private Player attacker(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        return null;
    }
    private boolean proc(Run run, Player player, String type, long cooldown) {
        String key = player.getUniqueId() + ":" + type; long now = System.currentTimeMillis();
        if (now < run.procReady.getOrDefault(key, 0L)) return false;
        run.procReady.put(key, now + cooldown); return true;
    }
    private void extraDamage(Player player, LivingEntity target, double damage) {
        boolean previous = bonusDamage; bonusDamage = true;
        try { target.damage(damage, player); } finally { bonusDamage = previous; }
    }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageIsolation(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity(); Entity source = event.getDamager();
        if (!WORLD_NAME.equals(victim.getWorld().getName())) return;
        Player player = attacker(source);
        if ("obelisk".equals(kind(victim))) {
            event.setCancelled(true); Run run = entityRun(victim); if (player != null && run != null && participants.get(player.getUniqueId()) == run && !run.spectators.contains(player.getUniqueId())) {
                AttributeInstance attack = player.getAttribute(Attribute.ATTACK_DAMAGE);
                hitObelisk(run, victim.getUniqueId(), Math.max(4, attack == null ? event.getDamage() : attack.getValue()));
            } return;
        }
        Run run = victim instanceof Player p ? participants.get(p.getUniqueId()) : entityRun(victim);
        Entity effectiveSource = source instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter ? shooter : source;
        Run sourceRun = player == null ? entityRun(effectiveSource) : participants.get(player.getUniqueId());
        if (run == null || sourceRun != run || (victim instanceof Player && player != null)) { event.setCancelled(true); return; }
        if (victim instanceof Player p && run.spectators.contains(p.getUniqueId())) { event.setCancelled(true); return; }
        if (player != null && (run.spectators.contains(player.getUniqueId()) || !"enemy".equals(kind(victim)))) event.setCancelled(true);
        if (run.stage == 1 && run.encounter == Encounter.OBELISKS && !run.obelisks.isEmpty() && isDungeonMob(victim)) event.setCancelled(true);
        if (source instanceof AbstractArrow && player == null && victim instanceof Player) event.setDamage(tierValue("mob-damage", run.tier, 4, 6, 8));
    }
    private void applyBoons(EntityDamageByEntityEvent event) {
        Player player = attacker(event.getDamager()); if (player == null || bonusDamage || !(event.getEntity() instanceof LivingEntity target)) return;
        Run run = participants.get(player.getUniqueId()); if (run == null || !canAffect(player, target) || target instanceof Player) return;
        int execution = stacks(run, player, Boon.EXECUTION);
        if (execution > 0 && run.logicalHp.getOrDefault(target.getUniqueId(), target.getHealth()) <= run.maxHp.getOrDefault(target.getUniqueId(), maxHealth(target)) * boon("execution", "threshold", .3)) event.setDamage(event.getDamage() * (1 + boon("execution", "damage", .2) * execution));
        double damage = event.getFinalDamage();
        int purr = stacks(run, player, Boon.PURR); if (purr > 0 && proc(run, player, "purr", (long) (1000 * boon("purr", "cooldown-seconds", .5)))) heal(player, damage * boon("purr", "leech", .04) * purr);
        int frost = stacks(run, player, Boon.FROST); if (frost > 0 && ThreadLocalRandom.current().nextDouble() < boon("frost", "chance", .2) * frost && proc(run, player, "frost", (long) (1000 * boon("frost", "cooldown-seconds", 2)))) target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, (int) (20 * boon("frost", "duration-seconds", 2)), 1));
        int lightning = stacks(run, player, Boon.LIGHTNING);
        if (lightning > 0 && proc(run, player, "lightning", (long) (1000 * boon("lightning", "cooldown-seconds", 5)))) {
            int remaining = (int) boon("lightning", "targets", 2) * lightning;
            for (Entity nearby : target.getNearbyEntities(5, 4, 5)) if (nearby instanceof LivingEntity other && canAffect(player, other) && !(other instanceof Player)) {
                extraDamage(player, other, boon("lightning", "damage", 4) * lightning); world.spawnParticle(Particle.ELECTRIC_SPARK, other.getLocation().add(0, 1, 0), 15, .3, .7, .3); if (--remaining <= 0) break;
            }
        }
        int crescent = stacks(run, player, Boon.CRESCENT);
        if (crescent > 0 && run.attacks.merge(player.getUniqueId(), 1, Integer::sum) % Math.max(1, (int) boon("crescent", "every-hits", 4)) == 0) {
            Vector forward = player.getLocation().getDirection().setY(0).normalize();
            for (Entity nearby : player.getNearbyEntities(5, 3, 5)) if (nearby instanceof LivingEntity other && canAffect(player, other) && !(other instanceof Player)) {
                Vector delta = other.getLocation().toVector().subtract(player.getLocation().toVector()).setY(0);
                if (delta.lengthSquared() <= 25 && (delta.lengthSquared() < .01 || delta.normalize().dot(forward) > .3)) extraDamage(player, other, boon("crescent", "damage", 6) * crescent);
            }
            world.spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(forward.multiply(2)).add(0, 1, 0), 3, 1, .3, 1);
        }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFortress(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return; Run run = participants.get(player.getUniqueId());
        if (run != null && run.awaitingBoon) { event.setCancelled(true); return; }
        if (run != null) event.setDamage(event.getDamage() * Math.max(0, 1 - boon("fortress", "damage-reduction", .05) * stacks(run, player, Boon.FORTRESS)));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onReserveHealth(EntityDamageEvent event) {
        Run run = entityRun(event.getEntity()); if (run == null || !isDungeonMob(event.getEntity()) || !(event.getEntity() instanceof LivingEntity entity)) return;
        if (event instanceof EntityDamageByEntityEvent attack) applyBoons(attack);
        if (entity.isDead()) return;
        UUID id = entity.getUniqueId();
        DungeonRules.HealthHit hit = DungeonRules.healthHit(run.logicalHp.getOrDefault(id, entity.getHealth()), entity.getHealth(), event.getFinalDamage());
        double remaining = hit.remaining(); run.logicalHp.put(id, remaining);
        if (remaining > 0) {
            if (hit.intercept()) event.setCancelled(true);
            later(run, 1, () -> { if (entity.isValid() && !entity.isDead()) entity.setHealth(Math.min(maxHealth(entity), run.logicalHp.getOrDefault(id, remaining))); });
        } else if (hit.nativeHealthBeforeDamage() < entity.getHealth()) entity.setHealth(hit.nativeHealthBeforeDamage());
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnemyDeath(EntityDeathEvent event) {
        Run run = entityRun(event.getEntity()); if (run == null) return;
        event.getDrops().clear(); event.setDroppedExp(0); UUID id = event.getEntity().getUniqueId();
        run.enemies.remove(id); run.logicalHp.remove(id); run.maxHp.remove(id);
        if (id.equals(run.boss) && !run.ended && !run.reward) complete(run);
    }
    @EventHandler public void onMenu(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu holder)) return;
        event.setCancelled(true); if (!(event.getWhoClicked() instanceof Player player) || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        int slot = event.getRawSlot();
        switch (holder.kind) {
            case "gate" -> {
                if (!atGate(player)) { player.closeInventory(); parties.guide(player); return; }
                if (slot == 20 || slot == 22 || slot == 24) requestConsent(player, (slot - 18) / 2);
                else if (slot == 40) openShop(player);
            }
            case "shop" -> {
                if (!atGate(player)) { player.closeInventory(); return; }
                if (slot == 28) {
                    double price = Math.max(0, number("key-price", 3000));
                    if (churu.withdraw(player, price)) { mailbox.give(player, items.labyrinthKey(1)); player.sendMessage(TextUtil.msg("<green>미궁 입장 열쇠를 구매했습니다.")); }
                    else player.sendMessage(TextUtil.msg("<red>츄르가 부족합니다."));
                } else if (slot >= 10 && slot <= 16) {
                    String[] ids = {"charm_crimson_claw", "charm_starlight_aegis", "charm_golden_whisker", "charm_vampiric_purr", "you_and_me_bell", "nine_lives_charm", "respec_potion"};
                    String id = ids[slot - 10]; if (consume(player, "ancient_cat_coin", shopCost(id))) { mailbox.give(player, items.create(id)); player.sendMessage(TextUtil.msg("<green>금화 교환을 완료했습니다.")); }
                    else player.sendMessage(TextUtil.msg("<red>고대 고양이 금화가 부족합니다."));
                }
            }
            case "consent" -> {
                Consent consent = consents.get(player.getUniqueId()); if (consent == null || !consent.id.equals(holder.run)) return;
                if (slot == 15) cancelConsent(consent, "<yellow>파티원이 입장을 취소했습니다.");
                else if (slot == 11) {
                    if (System.currentTimeMillis() >= consent.deadline) { cancelConsent(consent, "<red>동의 시간이 지났습니다."); return; }
                    consent.accepted.add(player.getUniqueId()); player.closeInventory(); player.sendMessage(TextUtil.msg("<green>입장에 동의했습니다. 전원 동의를 기다립니다."));
                    if (consent.accepted.containsAll(consent.members)) enter(consent);
                }
            }
            case "boon" -> {
                Run run = participants.get(player.getUniqueId()); if (run == null || !run.id.equals(holder.run) || !run.awaitingBoon || run.selected.contains(player.getUniqueId())) return;
                int index = slot == 10 ? 0 : slot == 13 ? 1 : slot == 16 ? 2 : -1;
                if (index >= 0) {
                    Boon boon = holder.options.get(index); run.boons.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(Boon.class)).merge(boon, 1, Integer::sum);
                    run.selected.add(player.getUniqueId()); boonAttributes(run, player); player.closeInventory();
                    player.sendMessage(TextUtil.msg("<light_purple>" + boon.title + " 축복을 얻었습니다! (" + stacks(run, player, boon) + "중첩)"));
                }
            }
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent event) { if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Run run = entityRun(event.getRightClicked()); if (run == null) return;
        event.setCancelled(true); Player player = event.getPlayer();
        if (participants.get(player.getUniqueId()) != run || run.spectators.contains(player.getUniqueId())) return;
        String kind = kind(event.getRightClicked());
        if ("obelisk".equals(kind)) hitObelisk(run, event.getRightClicked().getUniqueId(), 8);
        else if ("boon".equals(kind)) openBoon(player, run);
        else if ("portal".equals(kind) && run.reward) { claimLoot(run.id, player.getUniqueId()); leaveMember(run, player, "미궁에서 무사히 복귀했습니다."); }
        else if ("chest".equals(kind) && run.reward) claimLoot(run.id, player.getUniqueId());
        else if (run.stage == 3) {
            int index = event.getRightClicked().getPersistentDataContainer().getOrDefault(indexKey, PersistentDataType.INTEGER, -1);
            if ("crystal".equals(kind) && run.puzzle == Puzzle.MEMORY && System.currentTimeMillis() - run.memoryShownAt >= 5000) {
                world.playSound(crystal(run, Math.max(0, index)), Sound.BLOCK_NOTE_BLOCK_CHIME, 1, .7f + index * .25f);
                if (index != run.melody.get(run.memoryIndex)) puzzleMistake(run);
                else if (++run.memoryIndex == 4) { tell(run, "<green>기억의 수정 음계가 완성되었습니다!"); beginStage(run, 4); }
            } else if ("statue".equals(kind) && run.puzzle == Puzzle.STATUES) {
                if (index == run.truth) { tell(run, "<green>진실의 석상을 찾았습니다!"); beginStage(run, 4); } else puzzleMistake(run);
            }
        }
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer(); Run run = participants.get(player.getUniqueId()); if (run == null || run.spectators.contains(player.getUniqueId())) return;
        Action action = event.getAction(); boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (right && run.stage == 3 && run.puzzle == Puzzle.RESONANCE && online(run, true).size() == 1) {
            ItemStack held = event.getItem(); boolean bell = held != null && held.hasItemMeta() && run.id.toString().equals(held.getItemMeta().getPersistentDataContainer().get(helperKey, PersistentDataType.STRING));
            if (bell || (held == null || held.getType().isAir())) {
                event.setCancelled(true); int current = player.getLocation().distanceSquared(plate(run, 0)) < player.getLocation().distanceSquared(plate(run, 1)) ? 0 : 1;
                run.helperPlate = current == 0 ? 1 : 0; run.helperUntil = System.currentTimeMillis() + 20_000;
                player.playSound(player.getLocation(), Sound.BLOCK_BELL_USE, .7f, 1.5f); player.sendMessage(TextUtil.msg("<aqua>영혼 분신이 반대편 발판을 20초 동안 누릅니다.")); return;
            }
        }
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            RayTraceResult hit = world.rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 5, .5, entity -> "obelisk".equals(kind(entity)) && entityRun(entity) == run);
            if (hit != null && hit.getHitEntity() != null) { event.setCancelled(true); hitObelisk(run, hit.getHitEntity().getUniqueId(), 8); }
        }
    }
    private void hitObelisk(Run run, UUID id, double damage) {
        if (!run.obelisks.containsKey(id)) return;
        double hp = run.obelisks.get(id) - Math.max(0, damage); Entity entity = Bukkit.getEntity(id);
        if (entity != null) { world.spawnParticle(Particle.CRIT, entity.getLocation().add(0, 1, 0), 12, .5, .5, .5); world.playSound(entity.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, .8f, 1.5f); }
        if (hp > 0) { run.obelisks.put(id, hp); return; }
        run.obelisks.remove(id); if (entity != null) {
            Location point = entity.getLocation();
            for (UUID displayId : new ArrayList<>(run.roomDecor)) { Entity display = Bukkit.getEntity(displayId); if (display instanceof Display && display.getLocation().distanceSquared(point) < 2) { display.remove(); run.roomDecor.remove(displayId); run.entities.remove(displayId); } }
            entity.remove(); run.roomDecor.remove(id); run.entities.remove(id);
        }
        tell(run, "<light_purple>봉인 수정탑 파괴! 남은 수정탑: " + run.obelisks.size());
        if (run.obelisks.isEmpty()) tell(run, "<green>봉인이 해제되었습니다. 파수꾼들을 격파하세요!");
    }
    public void damageObelisk(Player player, double damage) { damageObelisks(player, damage); }
    public void damageObelisks(Player player, double damage) {
        Run run = participants.get(player.getUniqueId()); if (run == null || run.spectators.contains(player.getUniqueId())) return;
        for (UUID id : new ArrayList<>(run.obelisks.keySet())) { Entity entity = Bukkit.getEntity(id); if (entity != null && entity.getLocation().distanceSquared(player.getLocation()) <= 49) hitObelisk(run, id, damage * 3); }
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity(); Run run = participants.get(player.getUniqueId()); if (run == null) return;
        event.setKeepInventory(true); event.setKeepLevel(true); event.getDrops().clear(); event.setDroppedExp(0);
        run.spectators.add(player.getUniqueId()); player.sendMessage(TextUtil.msg("<gray>미궁 관전 상태입니다. 다음 방 클리어 또는 대신관의 기적으로 부활합니다. 구조비는 무료입니다."));
        later(run, 2, () -> { if (player.isOnline() && player.isDead()) player.spigot().respawn(); });
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer(); Run run = participants.get(player.getUniqueId());
        if (run != null) {
            event.setRespawnLocation(center(run).add(0, 3, 11));
            later(run, 1, () -> { if (player.isOnline() && !player.isDead() && participants.get(player.getUniqueId()) == run) {
                player.setGameMode(run.spectators.contains(player.getUniqueId()) ? GameMode.SPECTATOR : GameMode.ADVENTURE);
                if (run.bossBar != null) player.showBossBar(run.bossBar);
            } });
        } else if (data.contains("dungeon.recovery." + player.getUniqueId())) {
            Location spawn = hubSpawn.get(); if (spawn != null) event.setRespawnLocation(spawn);
            Bukkit.getScheduler().runTaskLater(plugin, () -> recover(player), 1);
        }
    }
    private void revive(Run run, Player player) {
        if (!run.spectators.remove(player.getUniqueId())) return;
        if (player.isDead()) { player.spigot().respawn(); }
        player.setGameMode(GameMode.ADVENTURE); heal(player, maxHealth(player)); player.setFireTicks(0); player.setFoodLevel(20);
        player.sendMessage(TextUtil.msg("<green>아홉 목숨의 축복으로 미궁에 부활했습니다!"));
    }
    public void reviveOnePartyMember(Player caster) { reviveSpectator(caster, 12); }
    public boolean reviveSpectator(Player caster, double radius) {
        Run run = participants.get(caster.getUniqueId()); if (run == null || run.spectators.contains(caster.getUniqueId())) return false;
        for (Player player : online(run, false)) if (run.spectators.contains(player.getUniqueId()) && player.getLocation().distanceSquared(caster.getLocation()) <= radius * radius) {
            revive(run, player); teleport(player, caster.getLocation()); return true;
        }
        return false;
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer(); Consent consent = consents.get(player.getUniqueId()); if (consent != null) cancelConsent(consent, "<yellow>파티원이 접속을 종료하여 입장을 취소했습니다.");
        Run run = participants.get(player.getUniqueId());
        if (run != null) { run.coinMultipliers.put(player.getUniqueId(), coinMultiplier.applyAsDouble(player)); run.disconnected.put(player.getUniqueId(), System.currentTimeMillis() + integer("disconnect-seconds", 180) * 1000L); tell(run, "<yellow>파티원 연결이 끊겼습니다. 재접속 유예 " + integer("disconnect-seconds", 180) + "초."); }
        parties.transferDisconnectedLeader(player.getUniqueId());
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer(); Run run = participants.get(player.getUniqueId());
        if (run != null && run.disconnected.containsKey(player.getUniqueId()) && System.currentTimeMillis() < run.disconnected.get(player.getUniqueId())) {
            run.disconnected.remove(player.getUniqueId());
            later(run, 2, () -> { player.setGameMode(run.spectators.contains(player.getUniqueId()) ? GameMode.SPECTATOR : GameMode.ADVENTURE); teleport(player, center(run).add(0, 0, 10)); boonAttributes(run, player); if (run.bossBar != null) player.showBossBar(run.bossBar); if (run.awaitingBoon) openBoon(player, run); });
        } else {
            if (run != null) { run.disconnected.remove(player.getUniqueId()); participants.remove(player.getUniqueId(), run); }
            Bukkit.getScheduler().runTaskLater(plugin, () -> recover(player), 2);
        }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Run run = participants.get(event.getPlayer().getUniqueId()); if (run == null || event.getTo() == null || internalTeleport || event instanceof PlayerTeleportEvent) return;
        Location target = event.getTo(), origin = center(run);
        if (!target.getWorld().equals(world) || Math.abs(target.getX() - origin.getX()) > 14 || Math.abs(target.getZ() - origin.getZ()) > 14 || target.getY() < 64 || target.getY() > 79) {
            event.setTo(event.getFrom());
            if (event.getFrom().getY() < 63) teleport(event.getPlayer(), origin.clone().add(0, 0, 10));
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (internalTeleport || shuttingDown || event.getTo() == null) return;
        Player player = event.getPlayer(); Run run = participants.get(player.getUniqueId());
        if (run == null) { if (WORLD_NAME.equals(event.getTo().getWorld().getName())) { event.setCancelled(true); parties.guide(player); } return; }
        if (!event.getTo().getWorld().equals(world)) return;
        Location origin = center(run); if (Math.abs(event.getTo().getX() - origin.getX()) > 14 || Math.abs(event.getTo().getZ() - origin.getZ()) > 14 || event.getTo().getY() < 64 || event.getTo().getY() > 79) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSuccessfulExternalTeleport(PlayerTeleportEvent event) {
        if (internalTeleport || shuttingDown || event.getTo() == null || event.getTo().getWorld().equals(world) || !isParticipant(event.getPlayer())) return;
        Bukkit.getScheduler().runTask(plugin, () -> { if (!event.getPlayer().getWorld().equals(world)) onExternalLeave(event.getPlayer()); });
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        Run run = entityRun(event.getEntity()); if (run == null || event.getTarget() == null) return;
        if (!(event.getTarget() instanceof Player player) || participants.get(player.getUniqueId()) != run || run.spectators.contains(player.getUniqueId())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { if (WORLD_NAME.equals(event.getBlock().getWorld().getName())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { if (WORLD_NAME.equals(event.getBlock().getWorld().getName())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent event) { if (WORLD_NAME.equals(event.getPlayer().getWorld().getName())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) { if (WORLD_NAME.equals(event.getLocation().getWorld().getName())) event.blockList().clear(); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) { if (WORLD_NAME.equals(event.getBlock().getWorld().getName())) event.blockList().clear(); }
    private void complete(Run run) {
        if (run.reward || run.ended) return;
        run.reward = true; run.stageStarted = System.currentTimeMillis();
        for (BukkitTask task : run.tasks) task.cancel(); run.tasks.clear();
        if (run.bossBar != null) for (Player player : online(run, false)) player.hideBossBar(run.bossBar);
        int chestIndex = 0;
        for (UUID id : run.members) if (participants.get(id) == run) {
            Player player = Bukkit.getPlayer(id);
            double multiplier = 1 + boon("gold", "amount", .2) * run.boons.getOrDefault(id, new EnumMap<>(Boon.class)).getOrDefault(Boon.GOLD, 0);
            multiplier += Math.max(0, (player != null ? coinMultiplier.applyAsDouble(player) : run.coinMultipliers.getOrDefault(id, 1.0)) - 1);
            int coins = DungeonRules.coins((int) tierValue("coins", run.tier, 4, 8, 12), multiplier);
            List<ItemStack> loot = new ArrayList<>(); loot.add(items.ancientCatCoin(Math.max(1, coins))); loot.add(items.starlightStone((int) tierValue("stones", run.tier, 1, 2, 3)));
            if (ThreadLocalRandom.current().nextDouble() < tierValue("capsule-chance", run.tier, .10, .15, .20)) loot.add(items.gachaCapsule(1));
            if (ThreadLocalRandom.current().nextDouble() < tierValue("charm-chance", run.tier, .02, .04, .08)) loot.add(items.create(new String[]{"charm_crimson_claw", "charm_starlight_aegis", "charm_golden_whisker", "charm_vampiric_purr"}[ThreadLocalRandom.current().nextInt(4)]));
            for (int i = 0; i < loot.size(); i++) data.set(path(run) + ".loot." + id + "." + i, loot.get(i));
            data.set("dungeon.stats." + id + ".clears", getClearCount(id) + 1);
            data.set("dungeon.completion-xp." + id, data.getInt("dungeon.completion-xp." + id, 0) + integer("completion-xp-per-tier", 250) * run.tier);
            double x = -10 + (chestIndex % 4) * 6, z = 7 + (chestIndex / 4) * 3;
            prop(run, "", center(run).add(x, 0, z), Material.CHEST, "chest", chestIndex++);
            text(run, center(run).add(x, 2.5, z), "<gold>개인 보상 상자\n<white>" + (Bukkit.getOfflinePlayer(id).getName() == null ? "원정대원" : Bukkit.getOfflinePlayer(id).getName()));
        }
        data.set(path(run) + ".status", "COMPLETE"); save.run();
        for (Player player : online(run, false)) {
            revive(run, player); heal(player, maxHealth(player));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1.2f); grantPendingXp(player);
        }
        prop(run, "", center(run).add(0, 0, 11), Material.LIME_STAINED_GLASS, "portal", 0);
        text(run, center(run).add(0, 3, 11), "<green><bold>허브 복귀 포탈");
        tell(run, "<gold><bold>문턱의 미궁 정복!</bold> 개인 보상 상자를 우클릭하고 초록 복귀 포탈로 돌아가세요. 미수령 보상은 보관됩니다.");
    }
    private void grantPendingXp(Player player) {
        String key = "dungeon.completion-xp." + player.getUniqueId(); int xp = data.getInt(key, 0);
        if (xp > 0) { data.set(key, null); completionXp.accept(player, xp); save.run(); }
    }
    private void claimLoot(UUID runId, UUID owner) {
        String key = "dungeon.runs." + runId + ".loot." + owner; ConfigurationSection section = data.getConfigurationSection(key);
        if (section == null) return;
        boolean any = false;
        for (String itemId : new ArrayList<>(section.getKeys(false))) {
            ItemStack item = section.getItemStack(itemId);
            if (item != null) { mailbox.enqueueOnce(owner, "dungeon-" + runId + "-" + itemId, item); any = true; }
            data.set(key + "." + itemId, null);
        }
        data.set(key, null); save.run();
        Player player = Bukkit.getPlayer(owner);
        if (player != null) { mailbox.deliver(player); grantPendingXp(player); if (any) player.sendMessage(TextUtil.msg("<gold>미궁 개인 보상을 정산했습니다! 가방에 들어가지 않은 보상은 보관됩니다.")); }
    }
    private void refundKey(UUID runId) {
        String key = "dungeon.runs." + runId;
        if (!data.getBoolean(key + ".key-consumed", false)) return;
        String owner = data.getString(key + ".leader");
        if (owner != null) {
            mailbox.enqueueOnce(UUID.fromString(owner), "dungeon-" + runId + "-key-refund", items.labyrinthKey(1));
            data.set(key + ".key-refunded", true); save.run();
        }
    }
    private void endRun(Run run, String reason, boolean systemFailure) {
        if (run.ended) return; run.ended = true;
        tell(run, "<yellow>" + reason + (systemFailure && run.tier > 1 && !run.reward ? " · 사용한 열쇠는 보관함으로 반환됩니다." : ""));
        if (!run.reward) { data.set(path(run) + ".status", systemFailure ? "INTERRUPTED" : "FAILED"); save.run(); if (systemFailure) refundKey(run.id); }
        for (UUID id : run.members) {
            if (run.reward) claimLoot(run.id, id);
            Player player = Bukkit.getPlayer(id);
            if (player != null && participants.get(id) == run) leaveMember(run, player, null);
            else participants.remove(id, run);
        }
        clearRoom(run); removeEntities(run, run.entities);
        for (ChunkRef chunk : run.chunks) world.removePluginChunkTicket(chunk.x, chunk.z, plugin);
        run.chunks.clear(); runs.remove(run.id); save.run();
    }
    private void stripTemporaryItems(Player player) {
        for (ItemStack item : player.getInventory().getContents()) if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(helperKey, PersistentDataType.STRING)) item.setAmount(0);
    }
    private void restorePlayer(Player player, boolean sendHome) {
        String key = "dungeon.recovery." + player.getUniqueId(); GameMode mode = GameMode.SURVIVAL;
        try { mode = GameMode.valueOf(data.getString(key + ".gamemode", "SURVIVAL")); } catch (IllegalArgumentException ignored) { }
        if (mode == GameMode.SPECTATOR) mode = GameMode.SURVIVAL;
        removeModifier(player, Attribute.MOVEMENT_SPEED, speedKey); removeModifier(player, Attribute.MAX_HEALTH, healthKey);
        stripTemporaryItems(player);
        if (player.isDead()) {
            if (!shuttingDown && plugin.isEnabled()) player.spigot().respawn();
            // During plugin disable, retain recovery until next login instead of scheduling a respawn task.
            return;
        }
        player.setGameMode(mode); player.setFireTicks(0);
        if (sendHome && hubSpawn.get() != null) teleport(player, hubSpawn.get());
        data.set(key, null); save.run(); mailbox.deliver(player); grantPendingXp(player);
    }
    private void leaveMember(Run run, Player player, String message) {
        if (run.reward) claimLoot(run.id, player.getUniqueId());
        if (run.bossBar != null) player.hideBossBar(run.bossBar);
        participants.remove(player.getUniqueId(), run); run.disconnected.remove(player.getUniqueId()); run.spectators.remove(player.getUniqueId());
        restorePlayer(player, true); if (message != null) player.sendMessage(TextUtil.msg("<green>" + message));
    }
    public void onExternalLeave(Player player) {
        Run run = participants.get(player.getUniqueId()); if (run == null) return;
        if (run.reward) claimLoot(run.id, player.getUniqueId());
        if (run.bossBar != null) player.hideBossBar(run.bossBar);
        participants.remove(player.getUniqueId(), run); run.disconnected.remove(player.getUniqueId()); run.spectators.remove(player.getUniqueId());
        restorePlayer(player, false); tell(run, "<yellow>" + player.getName() + "님이 미궁에서 이탈했습니다.");
    }
    private void recoverInterruptedRuns() {
        ConfigurationSection section = data.getConfigurationSection("dungeon.runs"); if (section == null) return;
        for (String id : new ArrayList<>(section.getKeys(false))) {
            UUID runId; try { runId = UUID.fromString(id); } catch (IllegalArgumentException ignored) { continue; }
            String key = "dungeon.runs." + id; String status = data.getString(key + ".status", "");
            if (status.equals("PREPARING") || status.equals("RUNNING") || status.equals("INTERRUPTED")) { data.set(key + ".status", "INTERRUPTED"); refundKey(runId); }
            ConfigurationSection loot = data.getConfigurationSection(key + ".loot");
            if (loot != null) for (String owner : new ArrayList<>(loot.getKeys(false))) { try { claimLoot(runId, UUID.fromString(owner)); } catch (IllegalArgumentException ignored) { } }
        }
        save.run();
    }
    private void recover(Player player) {
        if (!player.isOnline() || participants.containsKey(player.getUniqueId())) return;
        if (data.contains("dungeon.recovery." + player.getUniqueId()) || WORLD_NAME.equals(player.getWorld().getName())) {
            restorePlayer(player, true); player.sendMessage(TextUtil.msg("<yellow>중단된 미궁 원정을 정리하고 허브로 안전하게 복귀했습니다."));
        }
        grantPendingXp(player);
    }
    public List<String> validateReadiness() {
        List<String> errors = new ArrayList<>();
        if (world == null || !WORLD_NAME.equals(world.getName())) errors.add("dungeon world unavailable");
        else {
            if (!Boolean.TRUE.equals(world.getGameRuleValue(GameRule.KEEP_INVENTORY))) errors.add("keepInventory not enabled");
            if (!Boolean.FALSE.equals(world.getGameRuleValue(GameRule.DO_MOB_SPAWNING))) errors.add("natural spawning not disabled");
            if (!Boolean.FALSE.equals(world.getGameRuleValue(GameRule.MOB_GRIEFING))) errors.add("mobGriefing not disabled");
            if (world.getPVP()) errors.add("dungeon PvP enabled");
        }
        Set<Integer> slots = new HashSet<>();
        for (Run run : runs.values()) {
            if (!slots.add(run.slot)) errors.add("duplicate occupied slot");
            if (run.originalSize < 1 || run.originalSize > 8 || run.stage < 1 || run.stage > 5) errors.add("invalid run state");
            Location origin = center(run);
            if (world.getBlockAt(origin.clone().add(0, -1, 0)).getType().isAir()) errors.add("room floor missing");
            for (UUID id : run.members) if (participants.get(id) == run && !data.contains("dungeon.recovery." + id)) errors.add("participant missing recovery record");
        }
        return errors;
    }
    public String diagnosticSummary() { return "world=" + (world == null ? "missing" : world.getName()) + ", activeRuns=" + runs.size() + ", participants=" + participants.size() + ", consentGroups=" + new HashSet<>(consents.values()).size() + ", readinessErrors=" + validateReadiness(); }
    public void cleanup() {
        shuttingDown = true;
        for (Consent consent : new HashSet<>(consents.values())) cancelConsent(consent, "<yellow>서버 종료로 입장 대기를 취소합니다.");
        for (Run run : new ArrayList<>(runs.values())) endRun(run, "서버 종료로 미궁 원정이 중단되었습니다", true);
        if (ticker != null) ticker.cancel();
    }
}
