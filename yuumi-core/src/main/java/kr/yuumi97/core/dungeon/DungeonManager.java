package kr.yuumi97.core.dungeon;

import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.util.RewardMailbox;
import kr.yuumi97.core.util.TextUtil;
import kr.yuumi97.core.dungeon.mob.MobType;
import kr.yuumi97.core.dungeon.mob.MonsterRig;
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

/** Six connected zones, fixed party ownership and durable rewards. Server-thread only. */
public final class DungeonManager implements Listener {
    public static final String WORLD_NAME = "yuumi_dungeon";
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
        final Map<UUID,MobState> mobs=new LinkedHashMap<>();final Map<Integer,DungeonGate> gates=new HashMap<>();
        final Set<Integer> activated=new HashSet<>(),cleared=new HashSet<>();final Set<UUID> sideOwners=new HashSet<>();
        final Map<UUID,Long> fallReady=new HashMap<>(),inputReady=new HashMap<>();
        final Map<Integer,BlockDisplay> mirrors=new HashMap<>();
        final Map<Integer,UUID> chestOwners=new HashMap<>();
        final int[] puzzleValues=new int[4],puzzleTargets=new int[4];final Set<Integer> valves=new HashSet<>();
        final Set<UUID> lateDecor=new HashSet<>(),bridgeHit=new HashSet<>();long bridgeStarted;
        final List<BukkitTask> tasks = new ArrayList<>(); final long started = System.currentTimeMillis();
        final Set<ChunkRef> chunks = new HashSet<>();
        int stage=1,wave,memoryIndex,attempt;long stageStarted,memoryShownAt,melodyNext;boolean obeliskEncounter,checkpoint,retried,awaitingBoon,ended,reward,enraged;
        UUID boss;BossBar bossBar;
        Run(UUID leader, List<UUID> members, int tier, int slot) {
            this.leader = leader; this.members = new LinkedHashSet<>(members); originalSize = members.size(); this.tier = tier; this.slot = slot;
        }
    }
    private static final class MobState {
        final LivingEntity entity;final MobType type;final int zone;final Location home;final MonsterRig rig;
        final double damage,maxPoise;double poise;long nextAttack,staggerUntil,poiseImmune,windUntil,phaseUntil,nextSignature,signatureEnd;
        int phase=1,pattern,signature,heat;Location aim;Vector direction;UUID shieldTarget;boolean dead;
        MobState(LivingEntity entity,MobType type,int zone,Location home,MonsterRig rig,double damage,double poise){this.entity=entity;this.type=type;this.zone=zone;this.home=home.clone();this.rig=rig;this.damage=damage;this.poise=poise;this.maxPoise=poise;}
    }
    private record ChunkRef(int x, int z) {}
    private final JavaPlugin plugin; private final YuumiItems items; private final ChuruManager churu;
    private final FileConfiguration data; private final Runnable save; private final Supplier<Location> hubSpawn;
    private final RewardMailbox mailbox; private final PartyManager parties;
    private final NamespacedKey runKey, kindKey, indexKey, speedKey, healthKey;
    private final Map<UUID, Run> runs = new LinkedHashMap<>(), participants = new HashMap<>();
    private final Map<UUID, Consent> consents = new HashMap<>(); private final Set<String> models = new HashSet<>();
    private World world; private BukkitTask ticker; private boolean internalTeleport, bonusDamage, shuttingDown;private int scriptedAttackDepth;private long ticks;
    private DungeonLayout layout;private MonsterRig.Catalog rigCatalog;
    private final Map<EntityDamageEvent,Double> accepted=new WeakHashMap<>();
    private ToDoubleFunction<Player> coinMultiplier = p -> 1.0;
    private BiConsumer<Player, Integer> completionXp = (p, amount) -> {};
    @FunctionalInterface public interface BoonDamage {void hit(Player player,LivingEntity target,double amount);}
    private BoonDamage boonDamage=(player,target,amount)->target.damage(amount,player);

    public DungeonManager(JavaPlugin plugin, YuumiItems items, ChuruManager churu, FileConfiguration data,
                          Runnable save, Supplier<Location> hubSpawn, RewardMailbox mailbox) {
        this.plugin = plugin; this.items = items; this.churu = churu; this.data = data;
        this.save = save; this.hubSpawn = hubSpawn; this.mailbox = mailbox;
        runKey = new NamespacedKey(plugin, "dungeon_run"); kindKey = new NamespacedKey(plugin, "dungeon_kind");
        indexKey = new NamespacedKey(plugin, "dungeon_index");
        speedKey = new NamespacedKey(plugin, "dungeon_boon_speed"); healthKey = new NamespacedKey(plugin, "dungeon_boon_health");
        parties = new PartyManager(id -> participants.containsKey(id) || consents.containsKey(id), this::gateLocation);
    }
    public PartyManager getPartyManager() { return parties; }
    public void setCoinMultiplier(ToDoubleFunction<Player> multiplier) { coinMultiplier = multiplier; }
    public void setCompletionXp(BiConsumer<Player, Integer> callback) { completionXp = callback; }
    public void setBoonDamage(BoonDamage callback){boonDamage=Objects.requireNonNull(callback);}
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
    public boolean isBoss(Entity entity){Run run=entityRun(entity);MobState mob=run==null?null:run.mobs.get(entity.getUniqueId());return mob!=null&&mob.type.boss();}
    public double acceptedDamage(EntityDamageEvent event){return accepted.getOrDefault(event,-1.0);}
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
        layout=new DungeonLayout(plugin);layout.load();rigCatalog=new MonsterRig.Catalog(plugin);scanModels(); recoverInterruptedRuns();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2, 2);
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
    private Location center(Run run) {return at(run,0,67,switch(run.stage){case 1->-12;case 2->-47;case 3->-87;case 4->-124;case 5->-160;default->-199;});}
    private Location at(Run run,double x,double y,double z){return new Location(world,run.slot*DungeonLayout.STRIDE+x+.5,y,z+.5,180,0);}
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
                "<gold>제" + tier + "미궁 · " + new String[]{"별빛 천문 대서고", "홍련의 거신 용광로 성채", "공허의 수정 대성당"}[tier - 1],
                "<gray>1~8인 · 심리스6구역 · 제한 " + integer("run-seconds", 1800) / 60 + "분",
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
            for (UUID id : run.members) { Player player = Bukkit.getPlayer(id); participants.put(id, run); player.closeInventory(); player.setGameMode(GameMode.ADVENTURE); teleport(player,DungeonLayout.spawn(world,run.slot)); }
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
        int cx=run.slot*DungeonLayout.STRIDE;
        for(int x=Math.floorDiv(cx-22,16);x<=Math.floorDiv(cx+22,16);x++)for(int z=-14;z<=-1;z++){
            world.addPluginChunkTicket(x,z,plugin);run.chunks.add(new ChunkRef(x,z));
        }
        layout.place(world,run.slot,run.tier);
        for(int zone=1;zone<=5;zone++)run.gates.put(zone,new DungeonGate(world,cx,DungeonLayout.ZONES.get(zone-1).north(),e->tag(e,run,"gate")));
    }

    private <T extends Entity> T tag(T entity, Run run, String kind) {
        entity.getPersistentDataContainer().set(runKey, PersistentDataType.STRING, run.id.toString());
        entity.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind);
        entity.setPersistent(false); run.entities.add(entity.getUniqueId());if(run.stage>=5&&!kind.equals("gate"))run.lateDecor.add(entity.getUniqueId());return entity;
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
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
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
    private LivingEntity enemy(Run run,MobType type,Location location,int zone,boolean twin) {
        LivingEntity entity=tag((LivingEntity)world.spawnEntity(location,EntityType.HUSK),run,"enemy");
        entity.setInvisible(true);entity.setSilent(true);entity.setPersistent(false);entity.setRemoveWhenFarAway(false);entity.setCanPickupItems(false);
        entity.setFireTicks(0);entity.setVisualFire(false);if(entity instanceof Zombie zombie){zombie.setAdult();zombie.setShouldBurnInDay(false);}
        if(entity.getEquipment()!=null){entity.getEquipment().clear();entity.getEquipment().setItemInMainHandDropChance(0);entity.getEquipment().setHelmetDropChance(0);}
        double health=type.health();if(type.role==3)health=tierValue("elite-health",run.tier,140,240,360);
        if(type.role==5)health=tierValue("boss-health",run.tier,300,500,800);
        if(type.role<3&&type!=MobType.CHOIR){double base=tierValue("mob-health",run.tier,24,36,50);health=type.role==1?Math.round(base*1.25):type.role==2?Math.round(base*.85):base;}
        if(twin)health/=2;
        health=Math.max(1,number("mobs."+type.id+".health",health));
        double total=health*DungeonRules.healthScale(run.originalSize),damage=type.damage();
        if(type.role==5)damage=tierValue("boss-damage",run.tier,9,13,17);else if(type.role==3)damage=tierValue("elite-damage",run.tier,7,10,13);
        else if(type.role<3)damage=tierValue("mob-damage",run.tier,4,6,8)*(type==MobType.CHOIR?.75:1);
        damage=Math.max(0,number("mobs."+type.id+".damage",damage));
        run.logicalHp.put(entity.getUniqueId(),total);run.maxHp.put(entity.getUniqueId(),total);run.enemies.add(entity.getUniqueId());
        setAttr(entity,Attribute.MAX_HEALTH,Math.min(1024,total));entity.setHealth(Math.min(1024,total));setAttr(entity,Attribute.ATTACK_DAMAGE,0);
        setAttr(entity,Attribute.MOVEMENT_SPEED,type==MobType.HOUND?.32:.24);setAttr(entity,Attribute.KNOCKBACK_RESISTANCE,type.boss()?.9:type.role==3?.6:.15);
        setAttr(entity,Attribute.SCALE,type.boss()?1.45:type.role==3?1.2:1);entity.setAI(!type.ranged());
        if(type.boss())entity.addScoreboardTag("yuumi_dungeon_boss");
        MonsterRig rig=new MonsterRig(entity,type,rigCatalog,e->tag(e,run,"rig"));
        double poise=Math.max(1,number("mobs."+type.id+".poise",type.poise())*(twin?.5:1)*(1+number("poise.player-scaling",.30)*(run.originalSize-1)));
        MobState mob=new MobState(entity,type,zone,location,rig,damage,poise);mob.nextAttack=ticks+30;
        run.mobs.put(entity.getUniqueId(),mob);return entity;
    }
    private void spawnWave(Run run){
        int count=DungeonRules.waveSize(run.originalSize),ground=(count+1)/2,upper=count-ground;
        for(int i=0;i<ground;i++)enemy(run,MobType.of(run.tier,run.tier==3?0:i%2),at(run,-6+(i%4)*4,67,-43-(i/4)*5),2,false);
        for(int i=0;i<upper;i++)enemy(run,MobType.of(run.tier,2),at(run,i%2==0?-12:12,75,-56+(i/2)*3),2,false);
    }

    private void removeEntities(Run run, Collection<UUID> ids) {
        for (UUID id : new ArrayList<>(ids)) { Entity entity = Bukkit.getEntity(id); if (entity != null) entity.remove(); run.entities.remove(id); run.enemies.remove(id); run.logicalHp.remove(id); run.maxHp.remove(id); }
        ids.clear();
    }
    private void clearRoom(Run run) {
        for(MobState mob:run.mobs.values())mob.rig.remove();run.mobs.clear();
        removeEntities(run,run.enemies);removeEntities(run,run.roomDecor);run.obelisks.clear();
        for(BukkitTask task:run.tasks)task.cancel();run.tasks.clear();run.gates.values().forEach(DungeonGate::remove);run.gates.clear();
        if(run.bossBar!=null){for(Player p:online(run,false))p.hideBossBar(run.bossBar);run.bossBar=null;}
    }
    private void beginStage(Run run,int zone){
        if(run.ended||!run.activated.add(zone))return;run.stage=zone;run.stageStarted=System.currentTimeMillis();
        tell(run,"<aqua><bold>제"+run.tier+"장 · "+new String[]{"","전초 로비","복층 전투 전당","기믹 대교","중보스 성소","고대 공간 퍼즐","대보스 대성당"}[zone]);
        if(zone==1){
            prop(run,"",at(run,0,67,-23),Material.LODESTONE,"ready",0);text(run,at(run,0,70,-23),"<gold>파티장 우클릭: 준비 완료 · 제1성문 개방");
            prop(run,"",at(run,-10,67,-90),Material.CHEST,"side",0);
        }else if(zone==2){
            run.obeliskEncounter=false;run.wave=1;
            if(run.obeliskEncounter){for(int i=0;i<3;i++)prop(run,"prop_dungeon_obelisk",at(run,-8+i*8,67,-57),Material.AMETHYST_BLOCK,"obelisk",i);tell(run,"<light_purple>봉인 수정탑3기를 부수고 전당의 적을 처치하세요.");}
            else tell(run,"<yellow>1층 근접·2층 원거리 전당: 두 웨이브를 격파하세요.");
            spawnWave(run);
        }else if(zone==3){
            run.bridgeStarted=ticks;
            if(run.tier==3)for(int z:new int[]{-79,-90,-101})for(int x=-1;x<=1;x++)for(int dz=-1;dz<=1;dz++)world.getBlockAt(run.slot*DungeonLayout.STRIDE+x,67,z+dz).setType(Material.PURPUR_SLAB,false);
            int elites=1+(run.originalSize-1)/4;
            for(int i=0;i<elites;i++){
                enemy(run,MobType.of(run.tier,3),at(run,i==0?-1:2,67,-88-i*8),3,false);
                if(run.tier==3)enemy(run,MobType.CHOIR,at(run,-2+i*4,67,-95),3,false);
            }
            for(int i=0;i<2;i++)enemy(run,MobType.of(run.tier,0),at(run,i==0?-2:2,67,-78),3,false);
            tell(run,"<yellow>다리의 진홍 전조를 피하세요. 옆길의 보물은 원정당1회이며 최종 승리 시 정산됩니다.");
        }else if(zone==4){
            enemy(run,MobType.of(run.tier,4),at(run,run.tier==3?-4:0,67,-124),4,run.tier==3);
            if(run.tier==3)enemy(run,MobType.ARCHONS,at(run,4,67,-124),4,true);
        }else if(zone==5)beginPuzzle(run);
        else{
            run.gates.get(5).close();LivingEntity boss=enemy(run,MobType.of(run.tier,5),at(run,0,67,-209),6,false);run.boss=boss.getUniqueId();
            run.bossBar=BossBar.bossBar(TextUtil.mm("<gold>"+MobType.of(run.tier,5).title),1,BossBar.Color.PURPLE,BossBar.Overlay.NOTCHED_10);
            for(Player p:online(run,false))p.showBossBar(run.bossBar);
            if(run.tier==2)for(int i=0;i<3;i++)prop(run,"",valvePoint(run,i),Material.WAXED_COPPER_BLOCK,"cooling",i);
            if(run.tier==3)for(int i=0;i<4;i++)prop(run,"",lanternPoint(run,i),Material.SEA_LANTERN,"lantern",i);
        }
    }
    private void clearZone(Run run,int zone){
        if(!run.cleared.add(zone))return;
        for(Player player:online(run,false))if(run.spectators.contains(player.getUniqueId())){revive(run,player);teleport(player,safePoint(run,zone));}
        tell(run,"<green>제"+zone+"구역 클리어 · 성문이 올라갑니다!");
        if(zone<5)run.gates.get(zone).open(ticks);else run.gates.get(5).open(ticks);
    }
    private int enemiesIn(Run run,int zone){return (int)run.mobs.values().stream().filter(m->m.zone==zone&&!m.dead).count();}
    private void beginBoon(Run run){
        run.awaitingBoon=true;run.selected.clear();run.choices.clear();
        for(UUID id:run.members)if(participants.get(id)==run){List<Boon> options=new ArrayList<>(List.of(Boon.values()));Collections.shuffle(options);run.choices.put(id,List.copyOf(options.subList(0,3)));}
        prop(run,"prop_dungeon_boon_altar",at(run,0,68,-140),Material.ENCHANTING_TABLE,"boon",0);
        for(Player p:online(run,false))openBoon(p,run);
    }
    private void openBoon(Player player,Run run){
        if(!run.awaitingBoon||run.selected.contains(player.getUniqueId()))return;List<Boon> choices=run.choices.get(player.getUniqueId());if(choices==null)return;
        Inventory inv=menu(player,new Menu("boon",run.id,choices),27,"<light_purple>별빛 화톳불 · 축복3택1");
        for(int i=0;i<3;i++){Boon boon=choices.get(i);inv.setItem(10+i*3,icon(boon.icon,"<light_purple>"+boon.title,"<gray>"+boon.description));}player.openInventory(inv);
    }
    private Location puzzlePoint(Run run,int i){return at(run,i<2?-9:9,67,i%2==0?-167:-153);}
    private Location valvePoint(Run run,int i){return at(run,new int[]{-8,8,0}[i],67,new int[]{-194,-194,-211}[i]);}
    private Location lanternPoint(Run run,int i){return at(run,i<2?-8:8,67,i%2==0?-207:-189);}
    private void beginPuzzle(Run run){
        run.memoryIndex=0;run.memoryShownAt=0;
        for(int i=0;i<4;i++){
            run.puzzleTargets[i]=i;run.puzzleValues[i]=(i+1+ThreadLocalRandom.current().nextInt(3))%4;
            if(run.tier==2)run.puzzleValues[i]=new int[]{0,20,80,100}[ThreadLocalRandom.current().nextInt(4)];
            if(run.tier!=2||i<3)prop(run,"",puzzlePoint(run,i),run.tier==1?Material.GOLD_BLOCK:run.tier==2?Material.COPPER_BLOCK:new Material[]{Material.RED_STAINED_GLASS,Material.BLUE_STAINED_GLASS,Material.LIME_STAINED_GLASS,Material.YELLOW_STAINED_GLASS}[i],"puzzle",i);
            if(run.tier==1){BlockDisplay mirror=tag(world.spawn(puzzlePoint(run,i).add(0,1,0),BlockDisplay.class),run,"decor");mirror.setBlock(Material.LIGHT_BLUE_STAINED_GLASS.createBlockData());run.roomDecor.add(mirror.getUniqueId());run.mirrors.put(i,mirror);rotateMirror(run,i);}
        }
        if(run.tier==3){if(run.melody.isEmpty()){run.melody.addAll(List.of(0,1,2,3));Collections.shuffle(run.melody);}prop(run,"",at(run,0,67,-160),Material.NOTE_BLOCK,"replay",0);replayMelody(run);}
        tell(run,run.tier==1?"<aqua>거울4개를 회전시켜 중앙 수정에 빛을 모으세요. 녹색 빛은 정렬 완료입니다.":run.tier==2?"<gold>밸브3개: 클릭마다 압력+20, 녹색40~60에서 자동 고정됩니다.":"<light_purple>4색 공허 파이프를 들려준 순서대로 연주하세요. 중앙 장치로 언제든 다시 듣습니다.");
    }
    private void replayMelody(Run run){run.memoryShownAt=System.currentTimeMillis();run.melodyNext=ticks;}
    private void rotateMirror(Run run,int index){BlockDisplay display=run.mirrors.get(index);if(display==null)return;org.joml.Quaternionf rotation=new org.joml.Quaternionf().rotateY((float)Math.toRadians(run.puzzleValues[index]*90));display.setInterpolationDelay(0);display.setInterpolationDuration(5);display.setTransformation(new Transformation(rotation.transform(new Vector3f(-.75f,0,-.08f)),rotation,new Vector3f(1.5f,1.5f,.16f),new org.joml.Quaternionf()));}
    private void tintProp(Run run,Location point,Material material){for(UUID id:run.roomDecor){Entity entity=Bukkit.getEntity(id);if(entity instanceof BlockDisplay block&&entity.getLocation().distanceSquared(point)<1)block.setBlock(material.createBlockData());}}
    private void later(Run run,long delay,Runnable action){int attempt=run.attempt;
        run.tasks.add(Bukkit.getScheduler().runTaskLater(plugin,()->{if(run.ended||run.attempt!=attempt)return;try{action.run();}catch(RuntimeException ex){plugin.getLogger().warning("[Dungeon] delayed task: "+ex.getMessage());endRun(run,"시스템 오류",true);}},delay));
    }
    private void tick(){
        ticks+=2;long now=System.currentTimeMillis();
        for(Consent consent:new HashSet<>(consents.values()))if(now>=consent.deadline)cancelConsent(consent,"<yellow>입장 동의 시간이 지나 취소되었습니다.");
        for(Run run:new ArrayList<>(runs.values()))try{tickRun(run,now);}catch(RuntimeException ex){plugin.getLogger().warning("[Dungeon] Run failed: "+ex.getMessage());endRun(run,"시스템 오류",true);}
    }
    private void tickRun(Run run,long now){
        if(run.ended)return;run.gates.values().forEach(g->g.tick(ticks));
        for(Player player:new ArrayList<>(online(run,false)))if(!player.getWorld().equals(world)&&!run.disconnected.containsKey(player.getUniqueId()))onExternalLeave(player);
        for(var lost:new ArrayList<>(run.disconnected.entrySet()))if(now>=lost.getValue()){run.disconnected.remove(lost.getKey());participants.remove(lost.getKey(),run);tell(run,"<yellow>재접속 유예가 끝나 원정대원1명이 이탈했습니다.");}
        if(run.members.stream().noneMatch(id->participants.get(id)==run)){endRun(run,run.reward?"미궁 완료":"참가자 전원 이탈",false);return;}
        if(run.reward){
            if(now-run.stageStarted>integer("reward-minutes",5)*60_000L){endRun(run,"보상 수령 시간 종료",false);return;}
            for(Player p:online(run,false))if(p.getLocation().distanceSquared(at(run,0,67,-216))<3){claimLoot(run.id,p.getUniqueId());leaveMember(run,p,"미궁에서 무사히 복귀했습니다.");}return;
        }
        if(now-run.started>=integer("run-seconds",1800)*1000L){endRun(run,"미궁 제한 시간 종료",false);return;}
        List<Player> living=online(run,true);boolean waiting=run.disconnected.keySet().stream().anyMatch(id->!run.spectators.contains(id));
        if(living.isEmpty()&&!waiting){if(run.checkpoint&&!run.retried)retryCheckpoint(run);else endRun(run,"원정대 전멸",false);return;}
        if(run.awaitingBoon){
            for(Player p:online(run,false))if(!run.selected.contains(p.getUniqueId())&&!(p.getOpenInventory().getTopInventory().getHolder() instanceof Menu holder&&"boon".equals(holder.kind)))openBoon(p,run);
            if(run.members.stream().filter(id->participants.get(id)==run).allMatch(run.selected::contains)){run.awaitingBoon=false;clearZone(run,4);}return;
        }
        for(Player p:living){int zone=DungeonLayout.zoneAt(p.getLocation().getZ());if(zone>run.stage&&zone<=5&&run.gates.get(zone-1).isOpen())beginStage(run,zone);}
        if(run.cleared.contains(5)&&!run.activated.contains(6)&&!living.isEmpty()){
            long inside=living.stream().filter(p->p.getLocation().getZ()<=-180).count();
            if(inside==living.size()&&!waiting){beginStage(run,6);for(Player p:online(run,false))if(run.spectators.contains(p.getUniqueId()))teleport(p,at(run,0,70,-183));}
            else if(ticks%20==0&&inside>0)tell(run,"<yellow>대성당 집결 "+inside+"/"+living.size()+(waiting?" · 재접속 대기":""));
        }
        for(MobState mob:new ArrayList<>(run.mobs.values()))if(mob.dead){mob.rig.tick(ticks,null,0,1,0,1);}else{
            if(!mob.entity.isValid())throw new IllegalStateException("live mob disappeared: "+mob.type.id);
            tickMob(run,mob,living);
        }
        if(run.stage==2&&!run.cleared.contains(2)&&enemiesIn(run,2)==0&&run.obelisks.isEmpty()){
            if(!run.obeliskEncounter&&run.wave==1){run.wave=2;later(run,60,()->spawnWave(run));run.wave=3;}
            else if(run.wave!=3)clearZone(run,2);
            else if(run.mobs.values().stream().filter(m->m.zone==2).count()>DungeonRules.waveSize(run.originalSize))clearZone(run,2);
        }
        if(run.stage==3&&!run.cleared.contains(3)){
            bridgeHazard(run,living);if(enemiesIn(run,3)==0)clearZone(run,3);
        }
        if(run.stage==4&&!run.checkpoint&&enemiesIn(run,4)==0){
            run.checkpoint=true;for(Player p:online(run,false)){revive(run,p);heal(p,maxHealth(p));p.setFoodLevel(20);p.setSaturation(20);p.setFireTicks(0);}
            prop(run,"",at(run,0,68,-138),Material.SOUL_CAMPFIRE,null,0);tell(run,"<green>별빛 화톳불 점화! 전원 회복 · 원정당1회 전멸 재도전 해금.");beginBoon(run);
        }
        if(run.stage==5&&!run.cleared.contains(5))tickPuzzle(run);
        if(run.boss!=null&&run.bossBar!=null){MobState boss=run.mobs.get(run.boss);if(boss!=null&&!boss.dead){double hp=run.logicalHp.getOrDefault(run.boss,0.0),max=run.maxHp.getOrDefault(run.boss,1.0);run.bossBar.progress((float)Math.clamp(hp/max,0,1));}}
    }
    private void retryCheckpoint(Run run){
        run.retried=true;run.attempt++;run.stage=4;run.stageStarted=System.currentTimeMillis();run.boss=null;run.enraged=false;run.valves.clear();run.memoryIndex=0;
        for(BukkitTask task:run.tasks)task.cancel();run.tasks.clear();
        for(MobState mob:new ArrayList<>(run.mobs.values()))if(mob.zone>=5){mob.rig.remove();mob.entity.remove();run.mobs.remove(mob.entity.getUniqueId());run.enemies.remove(mob.entity.getUniqueId());run.logicalHp.remove(mob.entity.getUniqueId());run.maxHp.remove(mob.entity.getUniqueId());}
        removeEntities(run,run.lateDecor);run.roomDecor.removeIf(id->!run.entities.contains(id));run.activated.remove(5);run.activated.remove(6);run.cleared.remove(5);run.cleared.remove(6);run.gates.get(5).close();
        if(run.bossBar!=null){for(Player p:online(run,false))p.hideBossBar(run.bossBar);run.bossBar=null;}
        run.spectators.clear();for(Player p:online(run,false)){if(p.isDead())p.spigot().respawn();p.setGameMode(GameMode.ADVENTURE);heal(p,maxHealth(p));p.setFoodLevel(20);p.setSaturation(20);p.setFireTicks(0);for(PotionEffectType negative:List.of(PotionEffectType.POISON,PotionEffectType.WITHER,PotionEffectType.SLOWNESS,PotionEffectType.WEAKNESS,PotionEffectType.BLINDNESS,PotionEffectType.DARKNESS,PotionEffectType.HUNGER,PotionEffectType.NAUSEA,PotionEffectType.MINING_FATIGUE))p.removePotionEffect(negative);teleport(p,DungeonLayout.checkpoint(world,run.slot));}
        tell(run,"<gold>화톳불의 마지막 기적! 남은 시간·축복·사이드 보물은 유지됩니다. 퍼즐과 대보스에 다시 도전하세요.");
    }
    private void tickPuzzle(Run run){
        if(run.tier==1){boolean all=true;for(int i=0;i<4;i++){boolean correct=run.puzzleValues[i]==run.puzzleTargets[i];all&=correct;Location from=puzzlePoint(run,i).add(0,1,0);Vector direction=(correct?at(run,0,68,-160):from.clone().add(Math.cos(run.puzzleValues[i]*Math.PI/2)*4,0,Math.sin(run.puzzleValues[i]*Math.PI/2)*4)).toVector().subtract(from.toVector());drawLine(from,direction,correct?Color.AQUA:Color.PURPLE);}
            if(all){if(run.memoryShownAt==0)run.memoryShownAt=System.currentTimeMillis();if(System.currentTimeMillis()-run.memoryShownAt>=1000)clearZone(run,5);}else run.memoryShownAt=0;
        }else if(run.tier==2){if(run.puzzleValues[0]>=40&&run.puzzleValues[0]<=60&&run.puzzleValues[1]>=40&&run.puzzleValues[1]<=60&&run.puzzleValues[2]>=40&&run.puzzleValues[2]<=60)clearZone(run,5);}
        else if(ticks==run.melodyNext||ticks==run.melodyNext+14||ticks==run.melodyNext+28||ticks==run.melodyNext+42){int i=(int)((ticks-run.melodyNext)/14);int note=run.melody.get(i);Location point=puzzlePoint(run,note);world.spawnParticle(Particle.END_ROD,point.clone().add(0,1,0),25,.4,.4,.4);world.playSound(point,Sound.BLOCK_NOTE_BLOCK_CHIME,2,.7f+note*.25f);tell(run,"<aqua>공허 화음 "+(i+1)+": "+new String[]{"빨강●","파랑◆","초록▲","노랑★"}[note]);}
    }
    private void bridgeHazard(Run run,List<Player> living){
        int cycle=run.tier==2?100:120;long phase=(ticks-run.bridgeStarted)%cycle;
        if(phase==0)run.bridgeHit.clear();Color red=Color.fromRGB(255,42,42);
        if(run.tier==1&&phase<=20){
            double angle=Math.sin((ticks-run.bridgeStarted)*.035)*1.1;Vector direction=new Vector(Math.sin(angle),0,Math.cos(angle));Location pivot=at(run,0,67,-88);
            drawLine(pivot.clone().subtract(direction.clone().multiply(12)),direction.clone().multiply(24),red);
            world.spawnParticle(Particle.ENCHANT,pivot.clone().add(0,5,0),6,.4,.4,.4);
            if(phase==20){for(Player player:living)if(DungeonLayout.zoneAt(player.getLocation().getZ())==3){Vector offset=player.getLocation().toVector().subtract(pivot.toVector()).setY(0);double along=offset.dot(direction);if(Math.abs(along)<=12&&offset.subtract(direction.clone().multiply(along)).lengthSquared()<1)hurt(player,tierValue("mob-damage",run.tier,4,6,8),null);}world.playSound(pivot,Sound.ENTITY_GUARDIAN_ATTACK,1,1.5f);}
        }else if(run.tier==2&&phase==0){
            int vent=(int)(((ticks-run.bridgeStarted)/cycle)%3);Location target=at(run,0,67,-78-vent*12);warnCircle(target,3.5,red);
            later(run,20,()->{if(run.cleared.contains(3))return;for(Player p:online(run,true))if(DungeonLayout.zoneAt(p.getLocation().getZ())==3&&Math.abs(p.getLocation().getZ()-target.getZ())<=3.5)hurt(p,tierValue("mob-damage",run.tier,4,6,8),null);world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,target,50,3,.5,1);world.playSound(target,Sound.BLOCK_FIRE_EXTINGUISH,1,.6f);});
        }else if(run.tier==3){
            if(phase==0){drawLine(at(run,-3,67,-70),new Vector(6,0,0),red);tell(run,"<light_purple>공허 파동! 도약석에 오르거나 파동을 뛰어넘으세요.");}
            if(phase>=20&&phase<=104){double z=-70-(phase-20)*.5;Location front=at(run,-3,67,z);drawLine(front,new Vector(6,0,0),red);
                for(Player p:living)if(DungeonLayout.zoneAt(p.getLocation().getZ())==3&&Math.abs(p.getLocation().getZ()-(z+.5))<1.4&&p.getLocation().getY()<67.45&&run.bridgeHit.add(p.getUniqueId()))hurt(p,tierValue("mob-damage",run.tier,4,6,8),null);
            }
        }
    }
    private Player nearest(MobState mob,List<Player> players){return players.stream().filter(p->p.getWorld().equals(mob.entity.getWorld())&&DungeonLayout.zoneAt(p.getLocation().getZ())==mob.zone).min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(mob.entity.getLocation()))).orElse(null);}
    private void tickMob(Run run,MobState mob,List<Player> living){
        Player target=nearest(mob,living);double hp=run.logicalHp.getOrDefault(mob.entity.getUniqueId(),0.0),max=run.maxHp.getOrDefault(mob.entity.getUniqueId(),1.0);
        if(mob.type.role==5&&!run.enraged&&hp<=max*number("boss.enrage-threshold",.25)){run.enraged=true;setAttr(mob.entity,Attribute.MOVEMENT_SPEED,.24*(1+number("boss.enrage-speed",.2)));tell(run,"<red><bold>"+mob.type.title+" 광폭화!");}
        if(mob.entity.getLocation().getY()<66||DungeonLayout.zoneAt(mob.entity.getLocation().getZ())!=mob.zone)mob.entity.teleport(mob.home);
        mob.entity.setFireTicks(0);mob.entity.setVisualFire(false);
        if(target!=null&&mob.windUntil==0&&mob.signature==0){Location look=mob.entity.getLocation();look.setDirection(target.getLocation().toVector().subtract(look.toVector()));mob.entity.setRotation(look.getYaw(),0);}
        mob.rig.tick(ticks,target,hp,max,mob.poise,mob.maxPoise);
        if(target==null){if(mob.signature>0)mob.signatureEnd+=2;if(mob.windUntil>0)mob.windUntil+=2;if(mob.phaseUntil>ticks)mob.phaseUntil+=2;mob.nextAttack+=2;mob.nextSignature+=2;return;}
        if(run.awaitingBoon||ticks<mob.phaseUntil)return;
        if(ticks<mob.staggerUntil){mob.entity.setVelocity(new Vector());return;}
        if(mob.staggerUntil>0){mob.staggerUntil=0;mob.poise=mob.maxPoise;mob.entity.setAI(!mob.type.ranged());}
        if(mob.type==MobType.CHOIR){
            MobState ally=run.mobs.values().stream().filter(m->!m.dead&&m.zone==mob.zone&&m.type.role==3&&m.entity.getLocation().distanceSquared(mob.entity.getLocation())<=18*18).findFirst().orElse(null);
            mob.shieldTarget=ally==null?null:ally.entity.getUniqueId();if(ally!=null)drawLine(mob.entity.getEyeLocation(),ally.entity.getLocation().add(0,1,0).toVector().subtract(mob.entity.getEyeLocation().toVector()),Color.PURPLE);
        }
        if(mob.type.role==5&&mob.phase==2){if(mob.signature>0){tickSignature(run,mob,living);return;}if(ticks>=mob.nextSignature){beginSignature(run,mob);return;}}
        if(mob.windUntil>0){telegraph(mob);if(ticks>=mob.windUntil){strike(run,mob);mob.windUntil=0;mob.entity.setAI(!mob.type.ranged());mob.rig.pose(MonsterRig.Pose.STRIKE,ticks,6);later(run,6,()->{if(!mob.dead)mob.rig.pose(MonsterRig.Pose.RECOVER,ticks,12);});mob.nextAttack=ticks+(mob.type.boss()?70:mob.type.role==3?80:mob.type.ranged()?70:45);}return;}
        if(!mob.type.ranged()&&mob.entity instanceof Mob entity){entity.setTarget(target);if(mob.entity.getLocation().distanceSquared(mob.home)>35*35)mob.entity.teleport(mob.home);}
        double distance=target.getLocation().distanceSquared(mob.entity.getLocation());double range=mob.type.ranged()?25:mob.type.boss()?18:mob.type==MobType.SENTINEL||mob.type==MobType.HOUND?7:mob.type.role==3?5:3.8;
        if(ticks<mob.nextAttack||distance>range*range)return;
        mob.pattern++;mob.aim=target.getLocation().clone();mob.direction=mob.aim.toVector().subtract(mob.entity.getLocation().toVector()).setY(0);if(mob.direction.lengthSquared()<.01)mob.direction=new Vector(0,0,1);else mob.direction.normalize();
        if(mob.type==MobType.STALKER){Location behind=target.getLocation().clone().subtract(target.getLocation().getDirection().setY(0).normalize().multiply(2));if(safeWalk(run,behind)){mob.entity.teleport(behind);mob.direction=mob.aim.toVector().subtract(behind.toVector()).setY(0).normalize();Location face=behind.clone().setDirection(mob.direction);mob.entity.setRotation(face.getYaw(),0);}}
        int wind=mob.type.role==3||mob.type.boss()?24:mob.type==MobType.MIMIC||mob.type==MobType.IMP?12:18;
        mob.windUntil=ticks+wind;mob.entity.setAI(false);mob.rig.pose(MonsterRig.Pose.WIND_UP,ticks,wind);world.playSound(mob.entity.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.8f,.7f);telegraph(mob);
    }
    private void telegraph(MobState mob){
        Color red=Color.fromRGB(255,42,42);Location origin=mob.entity.getLocation().add(0,.15,0);
        if(mob.type==MobType.LIBRARIAN||mob.type==MobType.BOMBARDIER||mob.type==MobType.CHOIR||mob.type==MobType.IMP||mob.type==MobType.ASTRO&&mob.pattern%2==1||mob.type==MobType.NOVARION&&mob.pattern%2==0)warnCircle(mob.aim,3,red);
        else if(mob.type==MobType.ASTRO){for(double angle:new double[]{-.65,0,.65})drawLine(origin,mob.direction.clone().rotateAroundY(angle).multiply(20),red);}
        else if(mob.type==MobType.OCULUS){drawLine(mob.entity.getEyeLocation(),mob.aim.clone().add(0,1,0).toVector().subtract(mob.entity.getEyeLocation().toVector()),red);warnCircle(mob.aim,1.25,red);}
        else if(mob.type==MobType.SENTINEL||mob.type==MobType.HOUND)drawLine(origin,mob.direction.clone().multiply(8),red);
        else if(mob.type==MobType.GOLEM||mob.type==MobType.VULCANTUS&&mob.pattern%2==0||mob.type==MobType.IGNION)warnCircle(origin,5,red);
        else{for(double angle=-.9;angle<=.9;angle+=.15){Vector ray=mob.direction.clone().rotateAroundY(angle).multiply(mob.type.boss()?8:4);world.spawnParticle(Particle.DUST,origin.clone().add(ray),1,0,0,0,0,new Particle.DustOptions(red,1.3f));}}
    }
    private void strike(Run run,MobState mob){
        if(mob.dead||ticks<mob.staggerUntil)return;double damage=mob.damage*(run.enraged&&mob.type.role==5?1+number("boss.enrage-damage",.3):1);
        Location origin=mob.entity.getLocation();
        for(Player p:online(run,true))if(DungeonLayout.zoneAt(p.getLocation().getZ())==mob.zone){
            Vector delta=p.getLocation().toVector().subtract(origin.toVector());Vector flat=delta.clone().setY(0);double distance=flat.length();boolean hit;
            if(mob.type==MobType.LIBRARIAN||mob.type==MobType.BOMBARDIER||mob.type==MobType.CHOIR||mob.type==MobType.IMP||mob.type==MobType.ASTRO&&mob.pattern%2==1||mob.type==MobType.NOVARION&&mob.pattern%2==0)hit=p.getLocation().distanceSquared(mob.aim)<=9;
            else if(mob.type==MobType.ASTRO){hit=false;for(double angle:new double[]{-.65,0,.65}){Vector ray=mob.direction.clone().rotateAroundY(angle);double along=flat.dot(ray);if(along>=0&&along<=20&&flat.clone().subtract(ray.multiply(along)).lengthSquared()<1.8)hit=true;}}
            else if(mob.type==MobType.OCULUS){Vector beam=mob.aim.clone().add(0,1,0).toVector().subtract(mob.entity.getEyeLocation().toVector()),offset=p.getEyeLocation().toVector().subtract(mob.entity.getEyeLocation().toVector());double length=beam.length();if(length<.01)hit=false;else{beam.normalize();double along=offset.dot(beam);hit=along>=0&&along<=length+1&&offset.subtract(beam.multiply(along)).lengthSquared()<2;}}
            else if(mob.type==MobType.SENTINEL||mob.type==MobType.HOUND){double along=flat.dot(mob.direction);hit=along>=0&&along<=8&&flat.clone().subtract(mob.direction.clone().multiply(along)).lengthSquared()<2;}
            else if(mob.type==MobType.GOLEM||mob.type==MobType.VULCANTUS&&mob.pattern%2==0||mob.type==MobType.IGNION)hit=distance<=5;
            else hit=distance<=(mob.type.boss()?8:4)&&(distance<.05||flat.normalize().dot(mob.direction)>.55);
            if(hit&&(mob.type.ranged()||Math.abs(delta.getY())<5)&&mob.entity.hasLineOfSight(p)){hurt(p,damage,mob.entity);
                if(mob.type==MobType.LIBRARIAN)p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,60,1));
                if(mob.type==MobType.IMP||mob.type==MobType.VULCANTUS)p.setFireTicks(60);
                if(mob.type==MobType.JUGGERNAUT)p.setVelocity(origin.toVector().subtract(p.getLocation().toVector()).normalize().multiply(.7));
                if(mob.type==MobType.ECLIPSIA)p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,60,0));
            }
        }
        if(mob.type==MobType.HOUND){Location end=origin.clone().add(mob.direction.clone().multiply(5));if(safeWalk(run,end))mob.entity.teleport(end);}
        if(mob.type==MobType.ARCHONS&&mob.pattern%2==0){for(Player p:online(run,true))if(p.getLocation().distanceSquared(mob.aim)<9)hurt(p,damage,mob.entity);}
        if(mob.type==MobType.TEMPLAR){Location ring=origin.clone();warnCircle(ring,6,Color.fromRGB(255,42,42));later(run,20,()->{if(mob.dead)return;for(Player p:online(run,true)){double d=p.getLocation().distanceSquared(ring);if(d>=9&&d<=36)hurt(p,mob.damage,mob.entity);}});}
        world.playSound(origin,Sound.ENTITY_PLAYER_ATTACK_SWEEP,.8f,.6f);world.spawnParticle(Particle.SWEEP_ATTACK,origin.clone().add(mob.direction.clone().multiply(2)).add(0,1,0),4,1,.2,1);
    }
    private void beginSignature(Run run,MobState mob){
        mob.signature=run.tier;mob.signatureEnd=ticks+(long)(20*(run.tier==1?number("boss.novarion.warning-seconds",3):run.tier==2?number("boss.ignion.cooling-window-seconds",6):number("boss.eclipsia.darkness-seconds",8)));mob.windUntil=0;mob.entity.setAI(false);mob.rig.pose(MonsterRig.Pose.WIND_UP,ticks,24);run.valves.clear();mob.heat=100;
        tell(run,run.tier==1?"<red><bold>초신성! 3초 안에 불투명 석주 뒤로 숨으세요!":run.tier==2?"<red><bold>용광로 과열! 6초 안에 서로 다른 냉각 밸브2개를 여세요!":"<red><bold>개기일식! 빛나는 두 등불의 안전지대로 이동하세요!");
        world.playSound(mob.entity.getLocation(),Sound.ENTITY_WARDEN_SONIC_CHARGE,1,.7f);
        if(run.tier==3){for(Player p:online(run,true))p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,160,0));for(int i=0;i<4;i++)tintProp(run,lanternPoint(run,i),litLantern(mob,i)?Material.SEA_LANTERN:Material.POLISHED_BLACKSTONE);}
    }
    private boolean litLantern(MobState mob,int index){int parity=mob.pattern%2;return index==parity||index==3-parity;}
    private void tickSignature(Run run,MobState mob,List<Player> living){
        if(mob.signatureEnd-ticks==24)mob.rig.pose(MonsterRig.Pose.WIND_UP,ticks,24);
        if(mob.signature==1&&ticks%10==0){warnCircle(mob.entity.getLocation(),18,Color.fromRGB(255,42,42));world.spawnParticle(Particle.ELECTRIC_SPARK,mob.entity.getLocation().add(0,2,0),30,1,1,1);}
        if(mob.signature==2){for(int i=0;i<3;i++)world.spawnParticle(run.valves.contains(i)?Particle.HAPPY_VILLAGER:Particle.FLAME,valvePoint(run,i).add(0,1,0),4,.3,.3,.3);}
        if(mob.signature==3){
            int parity=mob.pattern%2;for(int i=0;i<4;i++)if(litLantern(mob,i)){Location l=lanternPoint(run,i);warnCircle(l,3.5,Color.WHITE);world.spawnParticle(Particle.END_ROD,l.clone().add(0,2,0),3,.1,1,.1);}
            long left=mob.signatureEnd-ticks;if(left<=120&&left>0&&left%20==0)for(Player p:living){boolean safe=false;for(int i=0;i<4;i++)if(litLantern(mob,i)&&p.getLocation().distanceSquared(lanternPoint(run,i))<=3.5*3.5)safe=true;if(!safe)hurt(p,maxHealth(p)*number("boss.eclipsia.pulse-health-ratio",.15),null);}
        }
        if(ticks<mob.signatureEnd)return;
        if(mob.signature==1){for(Player p:living){Location eye=mob.entity.getEyeLocation();
            if(!opaqueCover(eye,p.getEyeLocation()))hurt(p,maxHealth(p)*number("boss.novarion.uncovered-health-ratio",1.25),null);else p.sendMessage(TextUtil.msg("<green>석주가 초신성을 막았습니다!"));}}
        if(mob.signature==2){if(mob.heat>number("boss.ignion.safe-heat",30)){for(Player p:living)hurt(p,mob.damage*number("boss.ignion.overheat-damage-multiplier",1.5),mob.entity);for(int i=1;i<=6;i++)later(run,i*20L,()->{if(mob.dead)return;for(Player p:online(run,true))if(p.getLocation().distanceSquared(at(run,0,67,-199))<=36)hurt(p,2,null);});}
            else{mob.staggerUntil=ticks+60;tell(run,"<aqua>냉각 성공! 거신이3초간 무력화됩니다.");}}
        if(mob.signature==3){for(Player p:online(run,false))p.removePotionEffect(PotionEffectType.DARKNESS);for(int i=0;i<4;i++)tintProp(run,lanternPoint(run,i),Material.POLISHED_BLACKSTONE);}
        mob.signature=0;mob.entity.setAI(!mob.type.ranged());mob.nextSignature=ticks+(long)(20*number("boss."+(run.tier==1?"novarion":run.tier==2?"ignion":"eclipsia")+".interval-seconds",run.tier==3?22:24));mob.nextAttack=ticks+20;mob.pattern++;
    }
    private void hurt(Player player,double amount,LivingEntity source){scriptedAttackDepth++;try{if(!player.isDead()){if(source==null)player.damage(amount);else player.damage(amount,source);}}finally{scriptedAttackDepth--;}}
    private boolean opaqueCover(Location from,Location to){Vector delta=to.toVector().subtract(from.toVector());double length=delta.length();if(length<.3)return false;Vector unit=delta.normalize();for(double d=.25;d<length-.2;d+=.2)if(from.clone().add(unit.clone().multiply(d)).getBlock().getType().isOccluding())return true;return false;}
    private boolean safeWalk(Run run,Location point){return DungeonLayout.contains(run.slot,point)&&point.getBlock().isPassable()&&point.clone().add(0,1,0).getBlock().isPassable()&&!point.clone().add(0,-1,0).getBlock().isPassable();}
    private void drawLine(Location from,Vector direction,Color color){double length=direction.length();if(length<.01)return;Vector step=direction.clone().normalize().multiply(.6);Particle.DustOptions dust=new Particle.DustOptions(color,1.2f);for(double d=0;d<length;d+=.6)world.spawnParticle(Particle.DUST,from.clone().add(step.clone().multiply(d/.6)),1,0,0,0,0,dust);}
    private void warnCircle(Location center,double radius,Color color){Particle.DustOptions options=new Particle.DustOptions(color,1.3f);for(int i=0;i<32;i++){double a=i*Math.PI/16;world.spawnParticle(Particle.DUST,center.clone().add(Math.cos(a)*radius,.15,Math.sin(a)*radius),1,0,0,0,0,options);}}
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
        try { boonDamage.hit(player,target,damage); } finally { bonusDamage = previous; }
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
        if (run.stage == 2 && run.obeliskEncounter && !run.obelisks.isEmpty() && isDungeonMob(victim)) event.setCancelled(true);
        if(player==null&&sourceRun==run&&scriptedAttackDepth==0)event.setCancelled(true);
    }
    private void applyBoons(EntityDamageByEntityEvent event) {
        Player player = attacker(event.getDamager()); if (player == null || bonusDamage || !(event.getEntity() instanceof LivingEntity target)) return;
        Run run = participants.get(player.getUniqueId()); if (run == null || !canAffect(player, target) || target instanceof Player) return;
        int execution = stacks(run, player, Boon.EXECUTION);
        if (execution > 0 && run.logicalHp.getOrDefault(target.getUniqueId(), target.getHealth()) <= run.maxHp.getOrDefault(target.getUniqueId(), maxHealth(target)) * boon("execution", "threshold", .3)) event.setDamage(event.getDamage() * (1 + boon("execution", "damage", .2) * execution));
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
        Run run=entityRun(event.getEntity());if(run==null||!isDungeonMob(event.getEntity())||!(event.getEntity() instanceof LivingEntity entity))return;
        MobState mob=run.mobs.get(entity.getUniqueId());if(mob==null)return;
        if(mob.dead||ticks<mob.phaseUntil){accepted.put(event,0.0);event.setCancelled(true);return;}
        if(EnumSet.of(EntityDamageEvent.DamageCause.FIRE,EntityDamageEvent.DamageCause.FIRE_TICK,EntityDamageEvent.DamageCause.LAVA,EntityDamageEvent.DamageCause.HOT_FLOOR).contains(event.getCause())){accepted.put(event,0.0);event.setCancelled(true);entity.setFireTicks(0);return;}
        if(event instanceof EntityDamageByEntityEvent attack)applyBoons(attack);
        if(entity.isDead()||mob.dead||ticks<mob.phaseUntil||run.logicalHp.getOrDefault(entity.getUniqueId(),0.0)<=0){accepted.put(event,0.0);event.setCancelled(true);return;}
        if(mob.type==MobType.SENTINEL&&ticks>=mob.staggerUntil&&event instanceof EntityDamageByEntityEvent attack){Player source=attacker(attack.getDamager());if(source!=null){Vector facing=source.getLocation().toVector().subtract(entity.getLocation().toVector()).setY(0);if(facing.lengthSquared()>.01&&facing.normalize().dot(entity.getLocation().getDirection().setY(0))>.55)event.setDamage(event.getDamage()*.45);}}
        if(run.mobs.values().stream().anyMatch(m->!m.dead&&m.type==MobType.CHOIR&&ticks>=m.staggerUntil&&entity.getUniqueId().equals(m.shieldTarget)))event.setDamage(event.getDamage()*.30);
        if(ticks<mob.staggerUntil)event.setDamage(event.getDamage()*1.15);
        UUID id=entity.getUniqueId();double hp=run.logicalHp.getOrDefault(id,entity.getHealth()),max=run.maxHp.getOrDefault(id,hp);
        if(hp<=0){accepted.put(event,0.0);event.setCancelled(true);return;}
        double damage=Math.max(0,Math.min(hp,event.getFinalDamage())),threshold=max*number("boss.phase-two-threshold",.5);boolean transition=mob.type.role==5&&mob.phase==1&&hp-damage<=threshold;
        if(transition){damage=Math.max(0,hp-threshold);mob.phase=2;mob.phaseUntil=ticks+(long)(20*number("boss.phase-transition-seconds",1.2));mob.nextSignature=mob.phaseUntil;mob.windUntil=0;mob.entity.setAI(false);mob.rig.pose(MonsterRig.Pose.RECOVER,ticks,24);tell(run,"<red><bold>"+mob.type.title+" · 제2페이즈!");}
        accepted.put(event,damage);DungeonRules.HealthHit hit=DungeonRules.healthHit(hp,entity.getHealth(),damage);run.logicalHp.put(id,hit.remaining());
        Player attacker=event instanceof EntityDamageByEntityEvent attack?attacker(attack.getDamager()):null;if(attacker!=null&&damage>0){damagePoise(attacker,entity,damage);int purr=stacks(run,attacker,Boon.PURR);if(!bonusDamage&&purr>0&&proc(run,attacker,"purr",(long)(1000*boon("purr","cooldown-seconds",.5))))heal(attacker,damage*boon("purr","leech",.04)*purr);}
        if(transition||hit.intercept())event.setCancelled(true);
        if(hit.remaining()>0)later(run,1,()->{if(entity.isValid()&&!entity.isDead())entity.setHealth(Math.min(maxHealth(entity),run.logicalHp.getOrDefault(id,hit.remaining())));});
        else if(hit.nativeHealthBeforeDamage()<entity.getHealth())entity.setHealth(hit.nativeHealthBeforeDamage());
        if(damage>0&&mob.windUntil==0&&ticks>=mob.staggerUntil&&!transition)mob.rig.pose(MonsterRig.Pose.HIT_FLASH,ticks,4);
    }
    public void damagePoise(Player player,LivingEntity target,double damage){
        Run run=participants.get(player.getUniqueId());if(run==null||!canAffect(player,target)||!Double.isFinite(damage)||damage<=0)return;MobState mob=run.mobs.get(target.getUniqueId());
        if(mob!=null&&mob.staggerUntil>0&&ticks>=mob.staggerUntil){mob.staggerUntil=0;mob.poise=mob.maxPoise;mob.entity.setAI(!mob.type.ranged());}
        if(mob==null||mob.dead||ticks<mob.poiseImmune||ticks<mob.phaseUntil)return;mob.poise=Math.max(0,mob.poise-damage);if(mob.poise>0)return;
        int duration=mob.type.boss()?24:36;mob.staggerUntil=ticks+duration;mob.poiseImmune=mob.staggerUntil+120;mob.windUntil=0;mob.nextAttack=mob.staggerUntil+10;mob.shieldTarget=null;
        mob.rig.pose(MonsterRig.Pose.RECOVER,ticks,duration);target.setAI(false);target.setVelocity(new Vector());world.playSound(target.getLocation(),Sound.BLOCK_ANVIL_LAND,.5f,1.5f);world.spawnParticle(Particle.CRIT,target.getLocation().add(0,1,0),25,.5,.5,.5);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEnemyDeath(EntityDeathEvent event) {
        Run run=entityRun(event.getEntity());if(run==null)return;MobState mob=run.mobs.get(event.getEntity().getUniqueId());if(mob==null)return;
        event.getDrops().clear();event.setDroppedExp(0);mob.dead=true;mob.rig.pose(MonsterRig.Pose.DEATH,ticks,20);
        UUID id=event.getEntity().getUniqueId();run.enemies.remove(id);run.logicalHp.remove(id);run.maxHp.remove(id);later(run,20,()->{mob.rig.remove();run.entities.remove(id);});
        if(mob.type.role==5&&!run.ended&&!run.reward)complete(run);
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
    @EventHandler(priority=EventPriority.HIGH)
    public void onEntityInteract(PlayerInteractEntityEvent event){
        if(event.getHand()!=EquipmentSlot.HAND)return;Run run=entityRun(event.getRightClicked());if(run==null)return;event.setCancelled(true);Player player=event.getPlayer();
        if(participants.get(player.getUniqueId())!=run||run.spectators.contains(player.getUniqueId()))return;
        String kind=kind(event.getRightClicked());int index=event.getRightClicked().getPersistentDataContainer().getOrDefault(indexKey,PersistentDataType.INTEGER,-1);
        if("ready".equals(kind)&&parties.getParty(player.getUniqueId())!=null&&parties.getParty(player.getUniqueId()).leader().equals(player.getUniqueId()))clearZone(run,1);
        else if("obelisk".equals(kind))hitObelisk(run,event.getRightClicked().getUniqueId(),8);
        else if("boon".equals(kind))openBoon(player,run);
        else if("side".equals(kind))sideChest(run,player);
        else if("portal".equals(kind)&&run.reward){claimLoot(run.id,player.getUniqueId());leaveMember(run,player,"미궁에서 무사히 복귀했습니다.");}
        else if("chest".equals(kind)&&run.reward){if(player.getUniqueId().equals(run.chestOwners.get(index)))claimLoot(run.id,player.getUniqueId());else player.sendMessage(TextUtil.msg("<yellow>다른 원정대원의 개인 상자입니다. 본인 상자나 귀환 포탈에서 정산하세요."));}
        else if("puzzle".equals(kind)&&run.stage==5&&!run.cleared.contains(5))puzzleInput(run,player,index);
        else if("replay".equals(kind)&&run.stage==5&&!run.cleared.contains(5)){run.memoryIndex=0;replayMelody(run);}
        else if("cooling".equals(kind)&&run.boss!=null){MobState boss=run.mobs.get(run.boss);if(boss!=null&&boss.signature==2&&run.valves.add(index)){boss.heat=Math.max(0,boss.heat-(int)number("boss.ignion.valve-cooling",40));player.sendMessage(TextUtil.msg("<aqua>냉각 밸브 개방 · 열 "+boss.heat+"/100"));}}
    }
    private void sideChest(Run run,Player player){
        if(!run.activated.contains(3)){player.sendMessage(TextUtil.msg("<yellow>대교를 개방한 후 보물을 조사할 수 있습니다."));return;}
        if(run.sideOwners.add(player.getUniqueId())){data.set(path(run)+".side."+player.getUniqueId(),true);save.run();player.sendMessage(TextUtil.msg("<gold>숨겨진 유물 발견! 최종 클리어 시 추가 금화"+run.tier+"개를 정산합니다."));}
        else player.sendMessage(TextUtil.msg("<gray>이미 조사한 원정 보물입니다."));
    }
    private void puzzleInput(Run run,Player player,int index){
        if(index<0||index>3||run.tier==2&&index==3)return;long now=System.currentTimeMillis();if(now<run.inputReady.getOrDefault(player.getUniqueId(),0L))return;run.inputReady.put(player.getUniqueId(),now+250);
        if(run.tier==1){run.puzzleValues[index]=(run.puzzleValues[index]+1)%4;rotateMirror(run,index);player.sendMessage(TextUtil.msg("<aqua>거울 "+(index+1)+": "+(run.puzzleValues[index]==run.puzzleTargets[index]?"중앙 정렬 완료":"회전 "+(run.puzzleValues[index]*90)+"°")));}
        else if(run.tier==2){int pressure=run.puzzleValues[index];if(pressure>=40&&pressure<=60)return;pressure=(pressure+20)%120;run.puzzleValues[index]=pressure;if(pressure>=40&&pressure<=60)tintProp(run,puzzlePoint(run,index),Material.LIME_CONCRETE);player.sendMessage(TextUtil.msg("<gold>밸브 "+(index+1)+": "+pressure+"/100"+(pressure>=40&&pressure<=60?" · 녹색 고정!":" · 녹색40~60")));world.playSound(puzzlePoint(run,index),Sound.BLOCK_COPPER_BULB_TURN_ON,1,1);}
        else{
            if(now-run.memoryShownAt<3000)return;world.playSound(puzzlePoint(run,index),Sound.BLOCK_NOTE_BLOCK_CHIME,1,.7f+index*.25f);
            if(index!=run.melody.get(run.memoryIndex)){run.memoryIndex=0;replayMelody(run);tell(run,"<yellow>화음이 어긋났습니다. 다시 들려드립니다.");}
            else if(++run.memoryIndex==4)clearZone(run,5);
        }
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event){
        if(event.getHand()!=EquipmentSlot.HAND)return;Player player=event.getPlayer();Run run=participants.get(player.getUniqueId());if(run==null||run.spectators.contains(player.getUniqueId()))return;
        if(event.getClickedBlock()!=null&&event.getAction()==Action.RIGHT_CLICK_BLOCK){Block block=event.getClickedBlock();int rx=block.getX()-run.slot*DungeonLayout.STRIDE;
            if(block.getType()==Material.CHEST&&Math.abs(rx+11)<=1&&Math.abs(block.getZ()+90)<=1){event.setCancelled(true);sideChest(run,player);return;}}
        if(event.getAction()==Action.LEFT_CLICK_AIR||event.getAction()==Action.LEFT_CLICK_BLOCK){RayTraceResult hit=world.rayTraceEntities(player.getEyeLocation(),player.getEyeLocation().getDirection(),5,.5,e->"obelisk".equals(kind(e))&&entityRun(e)==run);if(hit!=null&&hit.getHitEntity()!=null){event.setCancelled(true);hitObelisk(run,hit.getHitEntity().getUniqueId(),8);}}
    }
    private void hitObelisk(Run run,UUID id,double damage){
        if(!run.obelisks.containsKey(id))return;double hp=run.obelisks.get(id)-Math.max(0,damage);Entity entity=Bukkit.getEntity(id);
        if(entity!=null){world.spawnParticle(Particle.CRIT,entity.getLocation().add(0,1,0),12,.5,.5,.5);world.playSound(entity.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_HIT,.8f,1.5f);}
        if(hp>0){run.obelisks.put(id,hp);return;}run.obelisks.remove(id);
        if(entity!=null){Location point=entity.getLocation();for(UUID displayId:new ArrayList<>(run.roomDecor)){Entity display=Bukkit.getEntity(displayId);if(display instanceof Display&&display.getLocation().distanceSquared(point)<2){display.remove();run.roomDecor.remove(displayId);run.entities.remove(displayId);}}entity.remove();run.roomDecor.remove(id);run.entities.remove(id);}
        tell(run,"<light_purple>봉인 수정탑 파괴! 남은 수정탑: "+run.obelisks.size());
    }
    public void damageObelisk(Player player,double damage){damageObelisks(player,damage);}
    public void damageObelisks(Player player,double damage){Run run=participants.get(player.getUniqueId());if(run==null||run.spectators.contains(player.getUniqueId()))return;for(UUID id:new ArrayList<>(run.obelisks.keySet())){Entity entity=Bukkit.getEntity(id);if(entity!=null&&entity.getLocation().distanceSquared(player.getLocation())<=49)hitObelisk(run,id,damage*3);}}
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity(); Run run = participants.get(player.getUniqueId()); if (run == null) return;
        event.setKeepInventory(true); event.setKeepLevel(true); event.getDrops().clear(); event.setDroppedExp(0);
        run.spectators.add(player.getUniqueId()); player.sendMessage(TextUtil.msg("<gray>미궁 관전 상태입니다. 화톳불 또는 대신관의 기적으로 부활합니다. 구조비는 무료입니다."));
        later(run, 2, () -> { if (player.isOnline() && player.isDead()) player.spigot().respawn(); });
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer(); Run run = participants.get(player.getUniqueId());
        if (run != null) {
            event.setRespawnLocation(safePoint(run,run.stage).add(0,2,0));
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
            later(run, 2, () -> { player.setGameMode(run.spectators.contains(player.getUniqueId()) ? GameMode.SPECTATOR : GameMode.ADVENTURE); teleport(player,safePoint(run,run.stage));run.disconnected.remove(player.getUniqueId());player.setFallDistance(0);player.removePotionEffect(PotionEffectType.DARKNESS); boonAttributes(run, player); if (run.bossBar != null) player.showBossBar(run.bossBar); if (run.awaitingBoon) openBoon(player, run); });
        } else {
            if (run != null) { run.disconnected.remove(player.getUniqueId()); participants.remove(player.getUniqueId(), run); }
            Bukkit.getScheduler().runTaskLater(plugin, () -> recover(player), 2);
        }
    }
    private Location safePoint(Run run,int zone){if(zone==4&&run.checkpoint)return DungeonLayout.checkpoint(world,run.slot);return at(run,0,67,DungeonLayout.ZONES.get(Math.clamp(zone,1,6)-1).south()-4);}
    private boolean crossesClosedGate(Run run,Location from,Location to){for(DungeonGate gate:run.gates.values())if(!gate.isOpen()&&(from.getZ()-(gate.z+.5))*(to.getZ()-(gate.z+.5))<0)return true;return false;}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onMove(PlayerMoveEvent event){
        Player player=event.getPlayer();Run run=participants.get(player.getUniqueId());if(run==null||event.getTo()==null||internalTeleport||event instanceof PlayerTeleportEvent)return;Location to=event.getTo();
        if(!to.getWorld().equals(world)||!DungeonLayout.contains(run.slot,to)||crossesClosedGate(run,event.getFrom(),to)){event.setTo(event.getFrom());return;}
        if(to.getY()<66&&!run.spectators.contains(player.getUniqueId())&&ticks>=run.fallReady.getOrDefault(player.getUniqueId(),0L)){
            run.fallReady.put(player.getUniqueId(),ticks+20);int zone=DungeonLayout.zoneAt(to.getZ());event.setTo(safePoint(run,zone==0?run.stage:zone));hurt(player,maxHealth(player)*.20,null);player.setFallDistance(0);player.setNoDamageTicks(20);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onTeleport(PlayerTeleportEvent event){
        if(internalTeleport||shuttingDown||event.getTo()==null)return;Player player=event.getPlayer();Run run=participants.get(player.getUniqueId());
        if(run==null){if(WORLD_NAME.equals(event.getTo().getWorld().getName())){event.setCancelled(true);parties.guide(player);}return;}
        if(!event.getTo().getWorld().equals(world))return;
        if(!DungeonLayout.contains(run.slot,event.getTo())||crossesClosedGate(run,event.getFrom(),event.getTo()))event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSuccessfulExternalTeleport(PlayerTeleportEvent event) {
        if (internalTeleport || shuttingDown || event.getTo() == null || event.getTo().getWorld().equals(world) || !isParticipant(event.getPlayer())) return;
        Bukkit.getScheduler().runTask(plugin, () -> { if (!event.getPlayer().getWorld().equals(world)) onExternalLeave(event.getPlayer()); });
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        Run run = entityRun(event.getEntity()); if (run == null || event.getTarget() == null) return;
        MobState mob=run.mobs.get(event.getEntity().getUniqueId());
        if (!(event.getTarget() instanceof Player player) || participants.get(player.getUniqueId()) != run || run.spectators.contains(player.getUniqueId())||mob!=null&&DungeonLayout.zoneAt(player.getLocation().getZ())!=mob.zone) event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onCombust(EntityCombustEvent event){if(isDungeonMob(event.getEntity())){event.setCancelled(true);event.getEntity().setFireTicks(0);}}
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
        for(MobState mob:run.mobs.values()){mob.signature=0;if(mob.dead)later(run,20,mob.rig::remove);}
        for(Player player:online(run,false))player.removePotionEffect(PotionEffectType.DARKNESS);
        if (run.bossBar != null) for (Player player : online(run, false)) player.hideBossBar(run.bossBar);
        int chestIndex = 0;
        for (UUID id : run.members) if (participants.get(id) == run) {
            Player player = Bukkit.getPlayer(id);
            double multiplier = 1 + boon("gold", "amount", .2) * run.boons.getOrDefault(id, new EnumMap<>(Boon.class)).getOrDefault(Boon.GOLD, 0);
            multiplier += Math.max(0, (player != null ? coinMultiplier.applyAsDouble(player) : run.coinMultipliers.getOrDefault(id, 1.0)) - 1);
            int coins = DungeonRules.coins((int) tierValue("coins", run.tier, 4, 8, 12), multiplier)+(run.sideOwners.contains(id)?run.tier:0);
            List<ItemStack> loot = new ArrayList<>(); loot.add(items.ancientCatCoin(Math.max(1, coins))); loot.add(items.starlightStone((int) tierValue("stones", run.tier, 1, 2, 3)));
            if (ThreadLocalRandom.current().nextDouble() < tierValue("capsule-chance", run.tier, .10, .15, .20)) loot.add(items.gachaCapsule(1));
            if (ThreadLocalRandom.current().nextDouble() < tierValue("charm-chance", run.tier, .02, .04, .08)) loot.add(items.create(new String[]{"charm_crimson_claw", "charm_starlight_aegis", "charm_golden_whisker", "charm_vampiric_purr"}[ThreadLocalRandom.current().nextInt(4)]));
            for (int i = 0; i < loot.size(); i++) data.set(path(run) + ".loot." + id + "." + i, loot.get(i));
            data.set("dungeon.stats." + id + ".clears", getClearCount(id) + 1);
            data.set("dungeon.completion-xp." + id, data.getInt("dungeon.completion-xp." + id, 0) + integer("completion-xp-per-tier", 250) * run.tier);
            double x = -9 + (chestIndex % 4) * 6, z = -215 + (chestIndex / 4) * 3;
            run.chestOwners.put(chestIndex,id);prop(run, "", at(run,x,67,z), Material.CHEST, "chest", chestIndex++);
            text(run, at(run,x,69.5,z), "<gold>개인 보상 상자\n<white>" + (Bukkit.getOfflinePlayer(id).getName() == null ? "원정대원" : Bukkit.getOfflinePlayer(id).getName()));
        }
        data.set(path(run) + ".status", "COMPLETE"); save.run();
        for (Player player : online(run, false)) {
            revive(run, player); heal(player, maxHealth(player));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1.2f); grantPendingXp(player);
        }
        prop(run, "", at(run,0,67,-216), Material.LIME_STAINED_GLASS, "portal", 0);
        text(run, at(run,0,70,-216), "<green><bold>허브 복귀 포탈");
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
    private void restorePlayer(Player player, boolean sendHome) {
        String key = "dungeon.recovery." + player.getUniqueId(); GameMode mode = GameMode.SURVIVAL;
        try { mode = GameMode.valueOf(data.getString(key + ".gamemode", "SURVIVAL")); } catch (IllegalArgumentException ignored) { }
        if (mode == GameMode.SPECTATOR) mode = GameMode.SURVIVAL;
        removeModifier(player, Attribute.MOVEMENT_SPEED, speedKey); removeModifier(player, Attribute.MAX_HEALTH, healthKey);
        player.removePotionEffect(PotionEffectType.DARKNESS);
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
            if (run.originalSize < 1 || run.originalSize > 8 || run.stage < 1 || run.stage > 6) errors.add("invalid run state");
            Location origin = DungeonLayout.spawn(world,run.slot);
            if(world.getBlockAt(origin.clone().add(0,-1,0)).getType().isAir())errors.add("spine floor missing");
            for (UUID id : run.members) if (participants.get(id) == run && !data.contains("dungeon.recovery." + id)) errors.add("participant missing recovery record");
        }
        return errors;
    }
    public void verifyCore(){
        List<String> errors=validateReadiness();if(!errors.isEmpty())throw new IllegalStateException(errors.toString());
        if(MobType.values().length!=18||DungeonLayout.ZONES.size()!=6)throw new IllegalStateException("incomplete dungeon catalog");
        for(int i=0;i<5;i++)if(DungeonLayout.ZONES.get(i).north()-1!=DungeonLayout.ZONES.get(i+1).south())throw new IllegalStateException("spine seam mismatch");
    }
    public Map<String,Object> snapshot(UUID player){
        Run run=participants.get(player);if(run==null)return Map.of("active",false);
        Map<String,Object> result=new LinkedHashMap<>();result.put("active",true);result.put("id",run.id.toString());result.put("slot",run.slot);result.put("chapter",run.tier);result.put("stage",run.stage);
        result.put("activated",Set.copyOf(run.activated));result.put("cleared",Set.copyOf(run.cleared));result.put("checkpoint",run.checkpoint);result.put("retried",run.retried);result.put("attempt",run.attempt);
        result.put("remainingSeconds",Math.max(0,integer("run-seconds",1800)-(System.currentTimeMillis()-run.started)/1000));result.put("awaitingBoon",run.awaitingBoon);result.put("wave",run.wave);result.put("obeliskEncounter",run.obeliskEncounter);
        Map<Integer,Boolean> gates=new TreeMap<>();run.gates.forEach((zone,gate)->gates.put(zone,gate.isOpen()));result.put("gates",gates);result.put("chunks",run.chunks.size());result.put("enemies",run.enemies.size());
        result.put("puzzleValues",run.puzzleValues.clone());result.put("puzzleTargets",run.puzzleTargets.clone());result.put("melody",List.copyOf(run.melody));result.put("memoryIndex",run.memoryIndex);result.put("sideOwners",Set.copyOf(run.sideOwners));result.put("reward",run.reward);result.put("spectators",Set.copyOf(run.spectators));
        Map<String,Map<String,Integer>> boons=new LinkedHashMap<>();run.boons.forEach((id,owned)->{Map<String,Integer> values=new LinkedHashMap<>();owned.forEach((boon,amount)->values.put(boon.name(),amount));boons.put(id.toString(),values);});result.put("boons",boons);result.put("selected",Set.copyOf(run.selected));
        return Collections.unmodifiableMap(result);
    }
    public List<Map<String,Object>> mobSnapshots(UUID player){
        Run run=participants.get(player);if(run==null)return List.of();List<Map<String,Object>> result=new ArrayList<>();for(MobState mob:run.mobs.values()){
            Map<String,Object> state=new LinkedHashMap<>();UUID id=mob.entity.getUniqueId();state.put("uuid",id.toString());state.put("type",mob.type.id);state.put("zone",mob.zone);state.put("hp",run.logicalHp.getOrDefault(id,0.0));state.put("maxHp",run.maxHp.getOrDefault(id,0.0));state.put("poise",mob.poise);state.put("phase",mob.phase);state.put("signature",mob.signature);state.put("parts",mob.rig.partCount());state.put("dead",mob.dead);state.put("pose",mob.rig.pose().name());result.add(Collections.unmodifiableMap(state));}return List.copyOf(result);
    }
    public String diagnosticSummary(){return "world="+(world==null?"missing":world.getName())+", blueprints="+(layout==null?0:layout.templateCount())+", rigManifest="+(rigCatalog==null?0:rigCatalog.manifestCount())+", catalog=18, activeRuns="+runs.size()+", participants="+participants.size()+", readinessErrors="+validateReadiness();}
    public void cleanup() {
        shuttingDown = true;
        for (Consent consent : new HashSet<>(consents.values())) cancelConsent(consent, "<yellow>서버 종료로 입장 대기를 취소합니다.");
        for (Run run : new ArrayList<>(runs.values())) endRun(run, "서버 종료로 미궁 원정이 중단되었습니다", true);
        if (ticker != null) ticker.cancel();
    }
}
