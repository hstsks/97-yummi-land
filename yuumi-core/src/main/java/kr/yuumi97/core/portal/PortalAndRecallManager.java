package kr.yuumi97.core.portal;

import kr.yuumi97.core.altar.ThresholdAltarManager;
import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.plot.PlotManager;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 🌌 허브 북쪽 『문턱의 서 4대 대포탈 성소』 & 원정 복귀(3초 정신집중 귀환 · 귀환 책갈피)
 * - 야생 임의 지옥문 생성을 차단하고, 차원 해금 시 허브 성소의 대포탈이 실제로 점화됩니다!
 */
public final class PortalAndRecallManager implements Listener {

    private final JavaPlugin plugin;
    private final YuumiItems items;
    private final ThresholdAltarManager altar;
    private final PlotManager plotManager;
    private final Supplier<World> hubWorldSupplier;
    private final Supplier<Location> hubSpawnSupplier;
    private final Supplier<Location> arenaCenterSupplier;

    private final Map<UUID, Long> gateCooldowns = new HashMap<>();
    private final Map<UUID, Long> freeRecallCooldowns = new HashMap<>();
    private final Set<UUID> channelingPlayers = ConcurrentHashMap.newKeySet();

    public PortalAndRecallManager(JavaPlugin plugin, YuumiItems items, ThresholdAltarManager altar,
                                  PlotManager plotManager, Supplier<World> hubWorldSupplier,
                                  Supplier<Location> hubSpawnSupplier, Supplier<Location> arenaCenterSupplier) {
        this.plugin = plugin;
        this.items = items;
        this.altar = altar;
        this.plotManager = plotManager;
        this.hubWorldSupplier = hubWorldSupplier;
        this.hubSpawnSupplier = hubSpawnSupplier;
        this.arenaCenterSupplier = arenaCenterSupplier;
    }

    /**
     * Lights up the physical Nether (Phase >= 2) and End (Phase >= 3) portal curtains in yuumi_hub!
     */
    public void igniteUnlockedPortals() {
        World hub = hubWorldSupplier.get();
        if (hub == null) return;
        int phase = altar.getCurrentPhase();

        // Gate 1 (-24, -70): Overworld always lit (CYAN_STAINED_GLASS)
        fillCurtain(hub, -24, -70, Material.CYAN_STAINED_GLASS, Material.SEA_LANTERN);

        // Gate 2 (-8, -70): Nether (Phase >= 2 -> ORANGE_STAINED_GLASS + SHROOMLIGHT, else IRON_BARS)
        if (phase >= 2) {
            fillCurtain(hub, -8, -70, Material.ORANGE_STAINED_GLASS, Material.SHROOMLIGHT);
        } else {
            fillCurtain(hub, -8, -70, Material.IRON_BARS, Material.CRYING_OBSIDIAN);
        }

        // Gate 3 (+8, -70): End (Phase >= 3 -> MAGENTA_STAINED_GLASS + SEA_LANTERN, else IRON_BARS)
        if (phase >= 3) {
            fillCurtain(hub, 8, -70, Material.MAGENTA_STAINED_GLASS, Material.SEA_LANTERN);
        } else {
            fillCurtain(hub, 8, -70, Material.IRON_BARS, Material.OBSIDIAN);
        }

        // Gate 4 (+24, -70): Raid Colosseum always lit (RED_STAINED_GLASS)
        fillCurtain(hub, 24, -70, Material.RED_STAINED_GLASS, Material.OCHRE_FROGLIGHT);
    }

    private void fillCurtain(World hub, int cx, int cz, Material curtainMat, Material glowFloor) {
        for (int dx = -1; dx <= 1; dx++) {
            hub.getBlockAt(cx + dx, 65, cz).setType(glowFloor, false);
            for (int y = 66; y <= 69; y++) {
                hub.getBlockAt(cx + dx, y, cz).setType(curtainMat, false);
            }
        }
    }

    // ==================== 임의 지옥문 생성 차단 ====================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWildPortalCreate(PortalCreateEvent event) {
        if (event.getReason() == PortalCreateEvent.CreateReason.FIRE) {
            event.setCancelled(true);
            if (event.getEntity() instanceof Player p) {
                p.playSound(p.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.9f);
                p.sendMessage(TextUtil.msg("<red>🔥 야생에서는 임의로 지옥문을 열 수 없습니다! 고양이 허브 도시 북쪽 <yellow>[문턱의 서 대포탈 성소]</yellow>를 이용해 주세요!"));
            }
        }
    }

    // ==================== 성소 물리 대포탈 통과 감지 ====================

    @EventHandler
    public void onStepIntoSanctuaryGate(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        World hub = hubWorldSupplier.get();
        if (hub == null || !hub.equals(to.getWorld())) return;
        if (event.getFrom().getBlockX() == to.getBlockX()
                && event.getFrom().getBlockY() == to.getBlockY()
                && event.getFrom().getBlockZ() == to.getBlockZ()) {
            return;
        }

        Player p = event.getPlayer();
        long now = System.currentTimeMillis();
        if (now < gateCooldowns.getOrDefault(p.getUniqueId(), 0L)) return;

        int x = to.getBlockX(), y = to.getBlockY(), z = to.getBlockZ();

        // Arena Exit Portal at (0, 65..68, -193) -> back to Sanctuary!
        if (Math.abs(x) <= 1 && y >= 65 && y <= 68 && Math.abs(z - (-193)) <= 1) {
            gateCooldowns.put(p.getUniqueId(), now + 2500L);
            p.teleport(new Location(hub, 24.5, 66.0, -64.5, 0f, 0f));
            p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.6f, 1.3f);
            p.sendMessage(TextUtil.msg("🌌 보스 투기장에서 포탈 성소로 복귀했습니다!"));
            return;
        }

        // Sanctuary 4 Grand Portals at Z in [-71..-69], Y in [66..69]
        if (z < -71 || z > -69 || y < 66 || y > 69) return;

        if (Math.abs(x - (-24)) <= 1) {
            gateCooldowns.put(p.getUniqueId(), now + 3000L);
            teleportToWildOverworld(p);
        } else if (Math.abs(x - (-8)) <= 1) {
            gateCooldowns.put(p.getUniqueId(), now + 2500L);
            if (altar.getCurrentPhase() < 2) {
                p.setVelocity(p.getLocation().getDirection().multiply(-0.5).setY(0.25));
                p.playSound(p.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.85f);
                p.sendMessage(TextUtil.msg("<red>🔥 아직 봉인된 네더 대포탈입니다! 성소 중앙 <yellow>[수호자 유미 제단]</yellow>에 책장과 물자를 납품해 제2장을 해금하세요!"));
            } else {
                teleportToNetherOutpost(p);
            }
        } else if (Math.abs(x - 8) <= 1) {
            gateCooldowns.put(p.getUniqueId(), now + 2500L);
            if (altar.getCurrentPhase() < 3) {
                p.setVelocity(p.getLocation().getDirection().multiply(-0.5).setY(0.25));
                p.playSound(p.getLocation(), Sound.ENTITY_CAT_AMBIENT, 1f, 0.85f);
                p.sendMessage(TextUtil.msg("<light_purple>🌌 아직 봉인된 엔드 대포탈입니다! <yellow>[수호자 유미 제단]</yellow>에서 제3장 대단원을 복원하세요!"));
            } else {
                teleportToEndOutpost(p);
            }
        } else if (Math.abs(x - 24) <= 1) {
            gateCooldowns.put(p.getUniqueId(), now + 2500L);
            Location arena = arenaCenterSupplier.get();
            p.teleport(arena.clone().add(0, 1.0, 14.0));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
            p.sendMessage(TextUtil.msg("⚔ <red><bold>차원 레이드 콜로세움</bold></red>에 입장했습니다! 심판 애꾸눈냥에게 말을 걸어 보스를 소환하세요!"));
        }
    }

    private void teleportToWildOverworld(Player p) {
        World wild = Bukkit.getWorld("world");
        if (wild == null) wild = Bukkit.getWorlds().get(0);
        Location sp = wild.getSpawnLocation();
        int ox = sp.getBlockX(), oz = sp.getBlockZ();
        int oy = Math.max(64, wild.getHighestBlockYAt(ox, oz));

        // Ensure a safe 5x5 wooden outpost deck on first arrival
        Block check = wild.getBlockAt(ox, oy, oz);
        if (check.getType() != Material.CHERRY_PLANKS) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    wild.getBlockAt(ox + dx, oy, oz + dz).setType(Material.CHERRY_PLANKS, false);
                    for (int ay = 1; ay <= 3; ay++) wild.getBlockAt(ox + dx, oy + ay, oz + dz).setType(Material.AIR, false);
                }
            }
            wild.getBlockAt(ox - 2, oy + 1, oz - 2).setType(Material.LANTERN, false);
            wild.getBlockAt(ox + 2, oy + 1, oz - 2).setType(Material.LANTERN, false);
        }

        p.teleport(new Location(wild, ox + 0.5, oy + 1.0, oz + 0.5, 0f, 0f));
        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.7f, 1.2f);
        p.sendMessage(TextUtil.msg("🌲 <green><bold>야생 오버월드 개척 전초기지</bold></green>로 출정했습니다! (귀환: <aqua>문턱의 서 쉬프트+우클릭</aqua> 또는 <light_purple>귀환 책갈피</light_purple>)"));
    }

    private void teleportToNetherOutpost(Player p) {
        World nether = Bukkit.getWorld("world_nether");
        if (nether == null) {
            p.sendMessage(TextUtil.msg("<red>네더 월드를 찾을 수 없습니다!"));
            return;
        }
        int nx = 0, nz = 0, ny = 68;
        // Carve a safe 5x5 blackstone outpost pocket in the Nether so nobody spawns in lava!
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                nether.getBlockAt(nx + dx, ny - 1, nz + dz).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
                for (int ay = 0; ay <= 3; ay++) {
                    nether.getBlockAt(nx + dx, ny + ay, nz + dz).setType(Material.AIR, false);
                }
                nether.getBlockAt(nx + dx, ny + 4, nz + dz).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
            }
        }
        nether.getBlockAt(nx - 2, ny, nz - 2).setType(Material.SHROOMLIGHT, false);
        nether.getBlockAt(nx + 2, ny, nz + 2).setType(Material.SHROOMLIGHT, false);

        p.teleport(new Location(nether, nx + 0.5, ny, nz + 0.5, 0f, 0f));
        p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.8f, 0.9f);
        p.sendMessage(TextUtil.msg("🔥 <red><bold>불타는 네더(Incendium) 전초기지</bold></red>에 진입했습니다! 화염과 요새를 조심하세요!"));
    }

    private void teleportToEndOutpost(Player p) {
        World end = Bukkit.getWorld("world_the_end");
        if (end == null) {
            p.sendMessage(TextUtil.msg("<red>엔드 월드를 찾을 수 없습니다!"));
            return;
        }
        int ex = 100, ey = 49, ez = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                end.getBlockAt(ex + dx, ey - 1, ez + dz).setType(Material.OBSIDIAN, false);
                for (int ay = 0; ay <= 3; ay++) end.getBlockAt(ex + dx, ey + ay, ez + dz).setType(Material.AIR, false);
            }
        }
        p.teleport(new Location(end, ex + 0.5, ey, ez + 0.5, 90f, 0f));
        p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.7f, 1.2f);
        p.sendMessage(TextUtil.msg("🌌 <light_purple><bold>공허의 엔드(Nullscape) 대륙</bold></light_purple>에 도착했습니다!"));
    }

    // ==================== 3초 정신집중 귀환 & 고속 귀환 책갈피 ====================

    public void startChanneledRecall(Player player, boolean useBookmark) {
        if (channelingPlayers.contains(player.getUniqueId())) {
            player.sendActionBar(TextUtil.mm("<yellow>이미 귀환 주문을 정신집중하고 있습니다!"));
            return;
        }

        if (!useBookmark) {
            long now = System.currentTimeMillis();
            long readyAt = freeRecallCooldowns.getOrDefault(player.getUniqueId(), 0L);
            if (now < readyAt) {
                long secLeft = (readyAt - now + 999) / 1000;
                player.playSound(player.getLocation(), Sound.ENTITY_CAT_AMBIENT, 0.9f, 0.9f);
                player.sendMessage(TextUtil.msg("<red>문턱의 서 기본 귀환 재사용 대기중입니다 (" + secLeft + "초 남음). 급할 땐 <light_purple>[고속 귀환 책갈피]</light_purple>를 사용하세요!"));
                return;
            }
        }

        int totalSteps = useBookmark ? 2 : 6; // 1.0s (2 * 10 ticks) vs 3.0s (6 * 10 ticks)
        Location startLoc = player.getLocation().clone();
        channelingPlayers.add(player.getUniqueId());
        player.playSound(startLoc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
        player.sendMessage(TextUtil.msg(useBookmark
                ? "🔖 <light_purple><bold>[귀환 책갈피]</bold></light_purple> <white>1.0초 후 보금자리/허브로 고속 귀환합니다! (움직이거나 맞으면 취소)"
                : "📖 <aqua><bold>[문턱의 서 귀환]</bold></aqua> <white>3.0초 동안 움직이지 말고 정신을 집중하세요! (피격 시 취소)"));

        new BukkitRunnable() {
            int step = 0;
            @Override
            public void run() {
                if (!player.isOnline() || !channelingPlayers.contains(player.getUniqueId())) {
                    channelingPlayers.remove(player.getUniqueId());
                    cancel();
                    return;
                }
                Location cur = player.getLocation();
                if (!cur.getWorld().equals(startLoc.getWorld())
                        || Math.hypot(cur.getX() - startLoc.getX(), cur.getZ() - startLoc.getZ()) > 0.25) {
                    channelingPlayers.remove(player.getUniqueId());
                    player.playSound(cur, Sound.ENTITY_VILLAGER_NO, 0.9f, 1.0f);
                    player.sendActionBar(TextUtil.mm("<red>✖ 이동하여 귀환 정신집중이 취소되었습니다!"));
                    cancel();
                    return;
                }

                step++;
                cur.getWorld().spawnParticle(Particle.PORTAL, cur.clone().add(0, 1.0, 0), 18, 0.4, 0.6, 0.4, 0.2);
                cur.getWorld().spawnParticle(Particle.END_ROD, cur.clone().add(0, 0.2 + step * 0.3, 0), 4, 0.3, 0.1, 0.3, 0.01);
                double secLeft = Math.max(0.0, (totalSteps - step) * 0.5);
                player.sendActionBar(TextUtil.mm("<light_purple>✨ 귀환 정신집중... " + String.format("%.1f초", secLeft)));

                if (step >= totalSteps) {
                    channelingPlayers.remove(player.getUniqueId());
                    cancel();

                    if (useBookmark) {
                        if (!consumeOneBookmark(player)) {
                            player.sendMessage(TextUtil.msg("<red>인벤토리에서 귀환 책갈피를 찾을 수 없어 취소되었습니다!"));
                            return;
                        }
                    } else {
                        freeRecallCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 60_000L);
                    }

                    Location dest = null;
                    if (useBookmark) {
                        dest = plotManager.getHomeEntranceOrNull(player.getUniqueId());
                    }
                    if (dest == null) {
                        dest = hubSpawnSupplier.get();
                    }
                    player.teleport(dest);
                    player.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.25f);
                    player.playSound(dest, Sound.ENTITY_CAT_PURREOW, 1.0f, 1.2f);
                    dest.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, dest.clone().add(0, 1.0, 0), 25, 0.4, 0.6, 0.4, 0.15);
                    player.sendMessage(TextUtil.msg("🌸 평화로운 <gradient:#ff85c0:#ffd666><bold>고양이 허브 도시</bold></gradient>로 무사히 귀환했습니다!"));
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private boolean consumeOneBookmark(Player p) {
        for (ItemStack st : p.getInventory().getContents()) {
            if (items.isCustomItem(st, "recall_bookmark")) {
                st.setAmount(st.getAmount() - 1);
                return true;
            }
        }
        ItemStack off = p.getInventory().getItemInOffHand();
        if (items.isCustomItem(off, "recall_bookmark")) {
            off.setAmount(off.getAmount() - 1);
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageInterruptRecall(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && channelingPlayers.remove(p.getUniqueId())) {
            p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.9f, 0.9f);
            p.sendActionBar(TextUtil.mm("<red>💥 피격당해 귀환 정신집중이 끊겼습니다!!"));
            p.sendMessage(TextUtil.msg("<red>💥 피해를 입어 귀환 주문 시전이 취소되었습니다!"));
        }
    }
}
