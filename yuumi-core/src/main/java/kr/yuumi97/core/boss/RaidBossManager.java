package kr.yuumi97.core.boss;

import kr.yuumi97.core.altar.ThresholdAltarManager;
import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.util.TextUtil;
import kr.yuumi97.core.util.RewardMailbox;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

public final class RaidBossManager implements Listener, CommandExecutor {

    public static final class RaidGuiHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private final JavaPlugin plugin;
    private final YuumiItems items;
    private final ChuruManager churu;
    private final ThresholdAltarManager altar;
    private final Supplier<Location> arenaCenterSupplier;
    private final NamespacedKey bossTierKey;
    private RewardMailbox mailbox;

    private LivingEntity activeBoss;
    private int activeTier = 0;
    private BossBar activeBossBar;
    private final Map<UUID, Double> damageContribution = new HashMap<>();
    private final Map<EntityDamageEvent,Double> accepted=new WeakHashMap<>();
    private final Set<UUID> minions=new HashSet<>();
    private double logicalHealth;private long epoch;
    public double acceptedDamage(EntityDamageEvent event){return accepted.getOrDefault(event,-1.0);}

    public RaidBossManager(JavaPlugin plugin, YuumiItems items, ChuruManager churu,
                           ThresholdAltarManager altar, Supplier<Location> arenaCenterSupplier) {
        this.plugin = plugin;
        this.items = items;
        this.churu = churu;
        this.altar = altar;
        this.arenaCenterSupplier = arenaCenterSupplier;
        this.bossTierKey = new NamespacedKey(plugin, "raid_boss_tier");
    }

    public void setRewardMailbox(RewardMailbox mailbox) { this.mailbox = mailbox; }

    public boolean isRaidBoss(org.bukkit.entity.Entity entity) {
        return entity.getPersistentDataContainer().has(bossTierKey, PersistentDataType.INTEGER);
    }

    public void cleanup() {
        epoch++;for(UUID id:minions){Entity minion=Bukkit.getEntity(id);if(minion!=null)minion.remove();}minions.clear();
        if (activeBossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(activeBossBar);
            activeBossBar = null;
        }
        if (activeBoss != null && !activeBoss.isDead()) {
            activeBoss.remove();
        }
        activeBoss = null;
    }

    public void openRaidGui(Player player) {
        Inventory inv = Bukkit.createInventory(new RaidGuiHolder(), 36,
                TextUtil.mm("<red><bold>⚔ 차원의 문턱 협동 레이드</bold>"));
        ItemStack bg = icon(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 36; i++) inv.setItem(i, bg);

        inv.setItem(4, icon(Material.BEACON, "<gold><bold>⚔ 차원 레이드 콜로세움</bold></gold>",
                "<gray>참여자 전원에게 기여도 비례 츄르·별빛 세공석·유물이 지급됩니다!",
                "<gray>투기장 출구(남쪽 초록 게이트)로 나가면 포탈 성소로 돌아갑니다."));

        // Tier 1
        ItemStack t1 = items.bossCoreRat(1);
        ItemMeta m1 = t1.getItemMeta();
        m1.displayName(TextUtil.mm("<light_purple><bold>[1티어] 거대 시궁창 쥐대왕 찍찍이</bold>"));
        m1.lore(TextUtil.lore(
                "<gray>체력: <red>350 HP</red> │ 권장: <white>2~8인",
                "<gray>기믹: 부패 치즈 독장판 예고 · 도둑 쥐떼 소환 · 점프 강타",
                "",
                "<gold>✦ 개인별 기여도 보상:",
                "<white>  · 2,500~4,500 츄르 + 찢어진 책장 2장",
                "<white>  · 확률 드랍: <aqua>『너랑 유미랑!』 우정의 황금 방울</aqua> / <light_purple>『사르르 마법봉』</light_purple>",
                "",
                "<yellow>▶ [1티어 소환석] 소지 후 투기장에서 클릭하여 소환!"
        ));
        t1.setItemMeta(m1);
        inv.setItem(19, t1);

        // Tier 2
        ItemStack t2 = items.bossCoreHound(1);
        ItemMeta m2 = t2.getItemMeta();
        m2.displayName(TextUtil.mm("<red><bold>[2티어] 지옥불 사냥개 케르베로스</bold>"));
        m2.lore(TextUtil.lore(
                "<gray>체력: <red>650 HP</red> │ 해금 조건: <gold>문턱의 서 제2장(네더)</gold>",
                "<gray>기믹: 화염 회오리 폭발 · 용암 사슬 끌기 · 불꽃 사냥개 소환",
                "",
                "<gold>✦ 개인별 기여도 보상:",
                "<white>  · 6,000~10,000 츄르 + 찢어진 책장 4장",
                "<white>  · 높은 확률: <light_purple>『사르르 마법봉』</light_purple> / <green>『아홉 개의 목숨』 부적</green>",
                "",
                altar.getCurrentPhase() >= 2 ? "<yellow>▶ [2티어 소환석] 소지 후 클릭하여 소환!" : "<red>✖ 아직 제2장이 해금되지 않았습니다! (/제단)"
        ));
        t2.setItemMeta(m2);
        inv.setItem(22, t2);

        // Tier 3
        ItemStack t3 = items.bossCoreVoid(1);
        ItemMeta m3 = t3.getItemMeta();
        m3.displayName(TextUtil.mm("<dark_purple><bold>[3티어 최종] 문턱을 삼킨 공허의 군주</bold>"));
        m3.lore(TextUtil.lore(
                "<gray>체력: <red>1,100 HP</red> │ 해금 조건: <light_purple>문턱의 서 제3장(엔드)</light_purple>",
                "<gray>기믹: 공허 중력장 · <gold><bold>문턱의 서 황금 안전지대 집합 기믹!</bold></gold>",
                "",
                "<gold>✦ 개인별 기여도 보상:",
                "<white>  · 15,000~25,000 츄르 + 전설의 황금 츄르",
                "<white>  · 신화 보상: <light_purple><bold>『대단원』 파동의 비전서</bold></light_purple> / <aqua>『우정의 황금 방울』</aqua>",
                "",
                altar.getCurrentPhase() >= 3 ? "<yellow>▶ [3티어 소환석] 소지 후 클릭하여 최종 결전 시작!" : "<red>✖ 아직 제3장이 해금되지 않았습니다! (/제단)"
        ));
        t3.setItemMeta(m3);
        inv.setItem(25, t3);

        player.openInventory(inv);
    }

    @EventHandler
    public void onGuiClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RaidGuiHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();

        Location arena = arenaCenterSupplier.get();
        if (slot == 19) trySummonBoss(player, 1, "boss_core_rat", arena);
        else if (slot == 22) trySummonBoss(player, 2, "boss_core_hound", arena);
        else if (slot == 25) trySummonBoss(player, 3, "boss_core_void", arena);
    }

    private void trySummonBoss(Player summoner, int tier, String requiredCoreId, Location arena) {
        if (activeBoss != null && !activeBoss.isDead()) {
            summoner.sendMessage(TextUtil.msg("<red>이미 투기장에서 월드보스 토벌이 진행 중입니다!"));
            return;
        }
        if (tier == 2 && altar.getCurrentPhase() < 2) {
            summoner.sendMessage(TextUtil.msg("<red>문턱의 서 제2장(네더) 해금 후 소환할 수 있습니다!"));
            return;
        }
        if (tier == 3 && altar.getCurrentPhase() < 3) {
            summoner.sendMessage(TextUtil.msg("<red>문턱의 서 제3장(엔드) 해금 후 소환할 수 있습니다!"));
            return;
        }
        if (!summoner.getWorld().equals(arena.getWorld()) || summoner.getLocation().distanceSquared(arena) > 900.0) {
            summoner.sendMessage(TextUtil.msg("<yellow>레이드 투기장 내부(반경 30블록 이내)에서 소환해주세요! (상단 워프 아이콘 클릭)"));
            return;
        }

        // Consume 1 core
        boolean consumed = false;
        for (ItemStack st : summoner.getInventory().getContents()) {
            if (items.isCustomItem(st, requiredCoreId)) {
                st.setAmount(st.getAmount() - 1);
                consumed = true;
                break;
            }
        }
        if (!consumed) {
            summoner.playSound(summoner.getLocation(), Sound.ENTITY_CAT_HISS, 1f, 1f);
            summoner.sendMessage(TextUtil.msg("<red>인벤토리에 해당 티어의 보스 소환석이 없습니다! (조합법 확인 또는 일일 의뢰 보상)"));
            return;
        }

        summoner.closeInventory();
        spawnRaidBoss(summoner, tier, arena.clone().add(0, 1.0, 0));
    }

    private void spawnRaidBoss(Player summoner, int tier, Location spawnLoc) {
        epoch++;
        damageContribution.clear();
        damageContribution.put(summoner.getUniqueId(), 25.0); // Summoner base bonus
        activeTier = tier;

        World w = spawnLoc.getWorld();
        String bossName;
        double maxHp;
        BossBar.Color barColor;

        if (tier == 1) {
            Zombie z = (Zombie) w.spawnEntity(spawnLoc, EntityType.HUSK);
            z.setAdult();
            setAttr(z, Attribute.SCALE, 1.65);
            setAttr(z, Attribute.MAX_HEALTH, 350.0);
            setAttr(z, Attribute.ATTACK_DAMAGE, 9.0);
            setAttr(z, Attribute.MOVEMENT_SPEED, 0.31);
            setAttr(z, Attribute.KNOCKBACK_RESISTANCE, 0.75);
            EntityEquipment eq = z.getEquipment();
            if (eq != null) {
                eq.setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
                eq.setItemInMainHand(new ItemStack(Material.GOLDEN_AXE));
            }
            activeBoss = z;
            bossName = "🧀 [1티어] 거대 시궁창 쥐대왕 찍찍이";
            maxHp = 350.0;
            barColor = BossBar.Color.PURPLE;
        } else if (tier == 2) {
            WitherSkeleton ws = (WitherSkeleton) w.spawnEntity(spawnLoc, EntityType.WITHER_SKELETON);
            setAttr(ws, Attribute.SCALE, 1.75);
            setAttr(ws, Attribute.MAX_HEALTH, 650.0);
            setAttr(ws, Attribute.ATTACK_DAMAGE, 13.0);
            setAttr(ws, Attribute.MOVEMENT_SPEED, 0.33);
            setAttr(ws, Attribute.KNOCKBACK_RESISTANCE, 0.85);
            EntityEquipment eq = ws.getEquipment();
            if (eq != null) {
                eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            }
            activeBoss = ws;
            bossName = "🔥 [2티어] 지옥불 사냥개 케르베로스";
            maxHp = 650.0;
            barColor = BossBar.Color.RED;
        } else {
            Zombie v = (Zombie) w.spawnEntity(spawnLoc, EntityType.HUSK);
            v.setAdult();
            setAttr(v, Attribute.SCALE, 2.0);
            setAttr(v, Attribute.MAX_HEALTH, 1024.0);
            setAttr(v, Attribute.ATTACK_DAMAGE, 17.0);
            setAttr(v, Attribute.MOVEMENT_SPEED, 0.34);
            setAttr(v, Attribute.KNOCKBACK_RESISTANCE, 0.95);
            EntityEquipment eq = v.getEquipment();
            if (eq != null) {
                eq.setHelmet(new ItemStack(Material.CRYING_OBSIDIAN));
                eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                eq.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                eq.setItemInMainHand(new ItemStack(Material.MACE));
            }
            activeBoss = v;
            bossName = "🌌 [3티어 최종] 문턱을 삼킨 공허의 군주";
            maxHp = 1100.0;
            barColor = BossBar.Color.PINK;
        }

        logicalHealth=maxHp;activeBoss.setHealth(Math.min(1024,maxHp));
        activeBoss.setRemoveWhenFarAway(false);
        activeBoss.setPersistent(false);
        activeBoss.customName(TextUtil.mm("<gold><bold>" + bossName + "</bold></gold>"));
        activeBoss.setCustomNameVisible(true);
        activeBoss.getPersistentDataContainer().set(bossTierKey, PersistentDataType.INTEGER, tier);

        activeBossBar = BossBar.bossBar(TextUtil.mm("<bold>" + bossName + "</bold>"), 1.0f, barColor, BossBar.Overlay.NOTCHED_10);

        Bukkit.broadcast(TextUtil.mm(""));
        Bukkit.broadcast(TextUtil.msg("⚔ <red><bold>[월드보스 출현 경보!]</bold></red> <white>" + summoner.getName() + "</white>님이 레이드 투기장에 <gold><bold>" + bossName + "</bold></gold>를 소환했습니다!! (<yellow>/레이드</yellow>로 합류하세요!)"));
        Bukkit.broadcast(TextUtil.mm(""));

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.1f);
        }

        startBossAiLoop(spawnLoc.clone(), maxHp, bossName);
    }

    private void startBossAiLoop(Location homeCenter, double maxHp, String bossName) {
        long owner=epoch;
        new BukkitRunnable() {
            int seconds = 0;

            @Override
            public void run() {
                if(owner!=epoch){cancel();return;}
                if (activeBoss == null || activeBoss.isDead() || !activeBoss.isValid()) {
                    if (activeBossBar != null) {
                        for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(activeBossBar);
                        activeBossBar = null;
                    }
                    cancel();
                    return;
                }

                seconds++;
                // Keep boss tethered inside arena
                if (activeBoss.getLocation().distanceSquared(homeCenter) > 484.0) {
                    activeBoss.teleport(homeCenter);
                }

                // Update BossBar for nearby players
                float progress = (float) Math.max(0.0, Math.min(1.0, logicalHealth / maxHp));
                activeBossBar.progress(progress);
                activeBossBar.name(TextUtil.mm("<gold><bold>" + bossName + "</bold></gold> <gray>(" + (int) logicalHealth + " / " + (int) maxHp + " HP)"));

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getWorld().equals(homeCenter.getWorld()) && p.getLocation().distanceSquared(homeCenter) <= 3600.0) {
                        p.showBossBar(activeBossBar);
                        // Support contribution point for staying in combat & buffing allies
                        damageContribution.merge(p.getUniqueId(), 1.5, Double::sum);
                    } else {
                        p.hideBossBar(activeBossBar);
                    }
                }

                // Special telegraphed boss mechanics every 7 seconds!
                if (seconds % 7 == 0) {
                    executeBossPattern(homeCenter);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void executeBossPattern(Location arenaCenter) {
        if (activeBoss == null || activeBoss.isDead()) return;
        World w = activeBoss.getWorld();

        if (activeTier == 1) {
            // Pattern: Poison Cheese Bomb Rings under nearby players + Rat Minions
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(activeBoss.getLocation()) <= 400.0) {
                    Location targetSpot = p.getLocation().clone();
                    p.sendActionBar(TextUtil.mm("<light_purple><bold>⚠ 바닥의 부패 치즈 독장판을 피하세요! (2초 후 폭발)"));
                    drawCircleWarning(targetSpot, 3.2, Color.fromRGB(180, 255, 60), 35L, () -> {
                        w.spawnParticle(Particle.WITCH, targetSpot.clone().add(0, 0.5, 0), 35, 1.5, 0.4, 1.5, 0.05);
                        w.playSound(targetSpot, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.4f);
                        for (Player victim : w.getPlayers()) {
                            if (victim.getLocation().distanceSquared(targetSpot) <= 10.5) {
                                victim.damage(8.0, activeBoss);
                                victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1));
                            }
                        }
                    });
                }
            }
            // Spawn 2 tiny thief rats
            for (int i = 0; i < 2; i++) {
                Silverfish rat = (Silverfish) w.spawnEntity(activeBoss.getLocation().add(i == 0 ? 1.5 : -1.5, 0.5, 0), EntityType.SILVERFISH);
                rat.setPersistent(false);minions.add(rat.getUniqueId());
                rat.customName(TextUtil.mm("<gray>츄르 도둑 생쥐"));
                rat.setCustomNameVisible(true);
            }
        } else if (activeTier == 2) {
            // Pattern: Hellfire Chain Pull + Expanding Flame Nova
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(activeBoss.getLocation()) <= 500.0) {
                    Vector pull = activeBoss.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(0.9).setY(0.35);
                    p.setVelocity(pull);
                    p.sendMessage(TextUtil.msg("<red>🔥 케르베로스가 지옥 사슬로 모두를 끌어당긴 뒤 화염 폭발을 준비합니다! 바깥으로 뛰세요!!"));
                }
            }
            Location bossSpot = activeBoss.getLocation().clone();
            drawCircleWarning(bossSpot, 5.5, Color.fromRGB(255, 80, 20), 45L, () -> {
                w.spawnParticle(Particle.FLAME, bossSpot.clone().add(0, 1, 0), 90, 2.8, 0.8, 2.8, 0.08);
                w.playSound(bossSpot, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.7f);
                w.playSound(bossSpot, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.9f);
                for (Player victim : w.getPlayers()) {
                    if (victim.getLocation().distanceSquared(bossSpot) <= 30.25) {
                        victim.damage(14.0, activeBoss);
                        victim.setFireTicks(100);
                    }
                }
            });
        } else {
            // Tier 3 MMO Raid Mechanic: "Book of Thresholds Golden Safe Zone!"
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            Location safeZone = arenaCenter.clone().add(Math.cos(angle) * 8.0, 0.2, Math.sin(angle) * 8.0);
            Title warn = Title.title(TextUtil.mm("<gold><bold>📖 문턱의 서 안전지대 생성!</bold></gold>"),
                    TextUtil.mm("<yellow>3.5초 안에 황금빛 마법진 안으로 다같이 모이세요!!"),
                    Title.Times.times(Duration.ofMillis(150), Duration.ofSeconds(3), Duration.ofMillis(300)));

            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(arenaCenter) <= 900.0) {
                    p.showTitle(warn);
                    p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.2f, 1.4f);
                }
            }

            drawCircleWarning(safeZone, 3.8, Color.fromRGB(255, 220, 55), 70L, () -> {
                w.playSound(arenaCenter, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 0.9f);
                w.spawnParticle(Particle.PORTAL, arenaCenter.clone().add(0, 1.5, 0), 160, 9.0, 2.0, 9.0, 0.5);
                for (Player p : w.getPlayers()) {
                    double distArenaSq = p.getLocation().distanceSquared(arenaCenter);
                    if (distArenaSq <= 900.0) {
                        if (p.getLocation().distanceSquared(safeZone) <= 15.0) {
                            p.playSound(p.getLocation(), Sound.ENTITY_CAT_PURREOW, 1.2f, 1.3f);
                            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 120, 1));
                            p.sendMessage(TextUtil.msg("<gold>✨ 유미의 황금 마법진이 공허 파동을 막아내고 힘 버프를 부여했습니다!"));
                        } else {
                            p.damage(19.0, activeBoss);
                            p.sendMessage(TextUtil.msg("<dark_purple>💥 안전지대에 들어가지 못해 공허 대폭발에 휘말렸습니다!"));
                        }
                    }
                }
            });
        }
    }

    private void drawCircleWarning(Location center, double radius, Color color, long durationTicks, Runnable onDetonate) {
        long owner=epoch;
        World w = center.getWorld();
        if (w == null) return;
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.5f);

        new BukkitRunnable() {
            long elapsed = 0;

            @Override
            public void run() {
                if(owner!=epoch||activeBoss==null||activeBoss.isDead()){cancel();return;}
                elapsed += 5;
                for (int deg = 0; deg < 360; deg += 15) {
                    double rad = Math.toRadians(deg);
                    Location pt = center.clone().add(Math.cos(rad) * radius, 0.25, Math.sin(rad) * radius);
                    w.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, dust);
                }
                if (elapsed >= durationTicks) {
                    onDetonate.run();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossDamaged(EntityDamageEvent event) {
        if (activeBoss == null || !event.getEntity().getUniqueId().equals(activeBoss.getUniqueId())) return;
        if(logicalHealth<=0){accepted.put(event,0.0);event.setCancelled(true);return;}
        double damage=Math.min(logicalHealth,Math.max(0,event.getFinalDamage()));accepted.put(event,damage);
        var hit=kr.yuumi97.core.dungeon.DungeonRules.healthHit(logicalHealth,activeBoss.getHealth(),damage);logicalHealth=hit.remaining();
        LivingEntity boss=activeBoss;long owner=epoch;
        if(logicalHealth>0){if(hit.intercept())event.setCancelled(true);Bukkit.getScheduler().runTaskLater(plugin,()->{if(owner==epoch&&boss.isValid()&&!boss.isDead())boss.setHealth(Math.min(1024,logicalHealth));},1);}
        else if(hit.nativeHealthBeforeDamage()<boss.getHealth())boss.setHealth(hit.nativeHealthBeforeDamage());
        Player attacker = null;
        if(event instanceof EntityDamageByEntityEvent attack){if(attack.getDamager() instanceof Player p)attacker=p;else if(attack.getDamager() instanceof org.bukkit.entity.Projectile proj&&proj.getShooter() instanceof Player p)attacker=p;}

        if (attacker != null) {
            damageContribution.merge(attacker.getUniqueId(), damage, Double::sum);
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if(minions.remove(event.getEntity().getUniqueId())){event.getDrops().clear();event.setDroppedExp(0);return;}
        LivingEntity dead = event.getEntity();
        Integer tier = dead.getPersistentDataContainer().get(bossTierKey, PersistentDataType.INTEGER);
        if (tier == null) return;
        epoch++;for(UUID id:minions){Entity minion=Bukkit.getEntity(id);if(minion!=null)minion.remove();}minions.clear();

        event.getDrops().clear();
        event.setDroppedExp(150 * tier);

        if (activeBossBar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(activeBossBar);
            activeBossBar = null;
        }
        activeBoss = null;

        Bukkit.broadcast(TextUtil.mm(""));
        Bukkit.broadcast(TextUtil.msg("🏆 <gold><bold>[월드보스 토벌 성공!!]</bold></gold> 97랜드 원정대가 <yellow>" + tier + "티어 레이드 보스</yellow>를 물리쳤습니다! 참여자 전원에게 개인 보상이 지급됩니다!"));
        Bukkit.broadcast(TextUtil.mm(""));

        double totalContribution = damageContribution.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalContribution <= 0) totalContribution = 1.0;

        for (Map.Entry<UUID, Double> entry : damageContribution.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) continue;
            double share = Math.min(1.0, entry.getValue() / totalContribution);
            grantIndividualRaidReward(p, tier, share);
        }
        damageContribution.clear();
    }

    private void grantIndividualRaidReward(Player p, int tier, double share) {
        double baseChuru = switch (tier) {
            case 1 -> 2500.0;
            case 2 -> 6500.0;
            default -> 16000.0;
        };
        double bonusChuru = Math.round(baseChuru * (0.4 + share));
        churu.deposit(p, bonusChuru);

        int pages = tier * 2;
        giveSafe(p, items.tornPage(pages));
        giveSafe(p, items.starlightStone(tier));
        double keyChance = plugin.getConfig().getDouble("dungeon.key-drops.raid-" + tier, tier == 1 ? .25 : tier == 2 ? .50 : 1.0);
        if (ThreadLocalRandom.current().nextDouble() < keyChance) {
            if (mailbox != null) mailbox.give(p, items.labyrinthKey(1));
            else giveSafe(p, items.labyrinthKey(1));
        }
        if (tier >= 2 && ThreadLocalRandom.current().nextDouble() < 0.40) {
            giveSafe(p, items.starlightScroll(1));
        }
        if (tier == 3) {
            giveSafe(p, items.churuGolden(1));
        }

        double roll = ThreadLocalRandom.current().nextDouble();
        String relicMsg = "";
        if (tier == 1) {
            if (roll < 0.35) {
                giveSafe(p, items.youAndMeBell());
                relicMsg = " + <aqua>『너랑 유미랑!』 우정의 황금 방울</aqua>";
            } else if (roll < 0.65) {
                giveSafe(p, items.prowlingWand());
                relicMsg = " + <light_purple>『사르르 탄환』 유도 마법봉</light_purple>";
            }
        } else if (tier == 2) {
            if (roll < 0.45) {
                giveSafe(p, items.prowlingWand());
                relicMsg = " + <light_purple>『사르르 탄환』 유도 마법봉</light_purple>";
            } else if (roll < 0.80) {
                giveSafe(p, items.nineLivesCharm());
                relicMsg = " + <green>『아홉 개의 목숨』 수호 부적</green>";
            }
        } else {
            if (roll < 0.55) {
                giveSafe(p, items.finalChapterTome());
                relicMsg = " + <light_purple><bold>『대단원』 파동의 비전서!!</bold></light_purple>";
            } else {
                giveSafe(p, items.youAndMeBell());
                giveSafe(p, items.nineLivesCharm());
                relicMsg = " + <aqua>『우정의 황금 방울』 & 『아홉 목숨 부적』</aqua>";
            }
        }

        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
        p.sendMessage(TextUtil.msg("🎁 <gold>[개인 레이드 보상]</gold> " + TextUtil.churu(bonusChuru)
                + " <gray>+ <aqua>찢어진 책장 " + pages + "장</aqua>" + relicMsg));
    }

    private void giveSafe(Player p, ItemStack stack) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(stack);
        for (ItemStack drop : left.values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), drop);
        }
    }

    private void setAttr(LivingEntity ent, Attribute attr, double val) {
        AttributeInstance inst = ent.getAttribute(attr);
        if (inst != null) inst.setBaseValue(val);
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player p) {
            p.playSound(p.getLocation(), Sound.ENTITY_CAT_AMBIENT, 0.9f, 0.95f);
            p.sendMessage(TextUtil.msg("⚔ 월드보스 레이드는 허브 북쪽 <yellow>[문턱의 서 대포탈 성소 → 레이드 콜로세움 게이트]</yellow>로 입장하여 심판 애꾸눈냥에게 소환하실 수 있습니다!"));
        }
        return true;
    }
}
