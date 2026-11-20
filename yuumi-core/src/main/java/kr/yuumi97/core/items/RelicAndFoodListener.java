package kr.yuumi97.core.items;

import kr.yuumi97.core.economy.ChuruManager;
import kr.yuumi97.core.rpg.BookmarkPouchManager;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class RelicAndFoodListener implements Listener {

    private final JavaPlugin plugin;
    private final YuumiItems items;
    private final ChuruManager churu;
    private final BiConsumer<Player, Boolean> recallTrigger;

    private final Map<UUID, Long> wandCooldowns = new HashMap<>();
    private final Map<UUID, Long> tomeCooldowns = new HashMap<>();
    private BookmarkPouchManager bookmarkPouch;
    public void setBookmarkPouch(BookmarkPouchManager pouch) { this.bookmarkPouch = pouch; }

    public RelicAndFoodListener(JavaPlugin plugin, YuumiItems items, ChuruManager churu,
                                BiConsumer<Player, Boolean> recallTrigger) {
        this.plugin = plugin;
        this.items = items;
        this.churu = churu;
        this.recallTrigger = recallTrigger;
    }

    /**
     * Runs every 3 seconds: checks players holding/carrying 'you_and_me_bell' and grants mutual Yuumi attachment buffs!
     */
    public void startFriendshipAuraTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player holder : Bukkit.getOnlinePlayers()) {
                if (holder.isDead() || holder.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                if (!hasItemInInventory(holder, "you_and_me_bell") && (bookmarkPouch == null || !bookmarkPouch.hasCharm(holder, "you_and_me_bell"))) continue;

                Player partner = null;
                double bestDistSq = 144.0; // 12 blocks
                for (Player other : holder.getWorld().getPlayers()) {
                    if (other.equals(holder) || other.isDead() || other.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                    double dSq = other.getLocation().distanceSquared(holder.getLocation());
                    if (dSq <= bestDistSq) {
                        bestDistSq = dSq;
                        partner = other;
                    }
                }

                if (partner != null) {
                    applyFriendshipBuff(holder);
                    applyFriendshipBuff(partner);
                    holder.getWorld().spawnParticle(Particle.HEART, holder.getLocation().add(0, 2.05, 0), 2, 0.25, 0.15, 0.25, 0.01);
                    partner.getWorld().spawnParticle(Particle.NOTE, partner.getLocation().add(0, 2.05, 0), 2, 0.25, 0.15, 0.25, 0.5);
                }
            }
        }, 60L, 60L);
    }

    private void applyFriendshipBuff(Player target) {
        target.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 90, 0, true, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 90, 0, true, false, true));
        target.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 90, 0, true, false, true));
    }

    private boolean hasItemInInventory(Player player, String customId) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (items.isCustomItem(stack, customId)) return true;
        }
        return items.isCustomItem(player.getInventory().getItemInOffHand(), customId);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack stack = event.getItem();
        String id = items.getCustomId(stack);
        if (id == null) return;

        switch (id) {
            case "recall_bookmark" -> {
                event.setCancelled(true);
                recallTrigger.accept(player, true);
            }
            case "churu_salmon", "churu_tuna", "churu_golden" -> {
                event.setCancelled(true);
                int unitValue = switch (id) {
                    case "churu_salmon" -> 100;
                    case "churu_tuna" -> 1000;
                    default -> 10000;
                };
                boolean sneakAll = player.isSneaking();
                int count = sneakAll ? stack.getAmount() : 1;
                double total = (double) unitValue * count;
                stack.setAmount(stack.getAmount() - count);
                churu.deposit(player, total);
                player.playSound(player.getLocation(), Sound.ENTITY_CAT_EAT, 1.0f, 1.2f);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
                player.sendMessage(TextUtil.msg("냠냠! 츄르 스틱을 사용하여 지갑에 " + TextUtil.churu(total) + "<gray>가 입금되었습니다! (현재: " + TextUtil.fmt(churu.getBalance(player)) + " 츄르)"));
            }
            case "prowling_wand" -> {
                event.setCancelled(true);
                castProwlingProjectile(player);
            }
            case "final_chapter_tome" -> {
                event.setCancelled(true);
                castFinalChapterWaves(player);
            }
        }
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack stack = event.getItem();
        String id = items.getCustomId(stack);
        if (id == null) return;

        Player p = event.getPlayer();
        switch (id) {
            case "zoomies_skewer" -> {
                double maxHp = p.getAttribute(Attribute.MAX_HEALTH) != null ? p.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
                p.setHealth(Math.min(maxHp, p.getHealth() + 6.0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 180, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 20, 1));
                p.playSound(p.getLocation(), Sound.ENTITY_CAT_PURREOW, 1.0f, 1.4f);
                p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 18, 0.5, 0.5, 0.5, 0.1);
                p.sendMessage(TextUtil.msg("<#5cdbd3>슈우우웅~! 몸이 깃털처럼 가벼워집니다! (신속 II + 재생 II)"));
            }
            case "kneading_bento" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 20 * 480, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 180, 1));
                p.playSound(p.getLocation(), Sound.ENTITY_CAT_PURR, 1.0f, 1.1f);
                p.sendMessage(TextUtil.msg("<#ff9c6e>유미의 정성 가득 꾹꾹이 도시락으로 최대 체력이 든든하게 상승했습니다!"));
            }
            case "catnip_elixir" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 360, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 20 * 480, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.LUCK, 20 * 360, 0));
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.3f);
                p.sendMessage(TextUtil.msg("<#73d13d>사르르 별빛 기운이 감돌며 시야가 밝아지고 손놀림이 빨라집니다!"));
            }
            case "royal_feast_can" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 480, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 300, 0));
                p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 300, 0));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
                p.sendMessage(TextUtil.msg("<gold>황실 참치 대뱃살의 힘으로 화염 저항과 전투력이 충만해집니다!"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.getHealth() - event.getFinalDamage() > 0.0) return;

        // Check if player carries Nine Lives Charm
        int foundSlot = -1;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (items.isCustomItem(contents[i], "nine_lives_charm")) {
                foundSlot = i;
                break;
            }
        }
        boolean offhand = foundSlot == -1 && items.isCustomItem(player.getInventory().getItemInOffHand(), "nine_lives_charm");
        boolean inPouch = foundSlot == -1 && !offhand && bookmarkPouch != null && bookmarkPouch.hasCharm(player, "nine_lives_charm");
        if (foundSlot == -1 && !offhand && !inPouch) return;

        event.setCancelled(true);
        if (inPouch) {
            bookmarkPouch.consumeCharm(player, "nine_lives_charm");
        } else if (offhand) {
            ItemStack off = player.getInventory().getItemInOffHand();
            off.setAmount(off.getAmount() - 1);
        } else {
            ItemStack item = contents[foundSlot];
            item.setAmount(item.getAmount() - 1);
        }

        double maxHp = player.getAttribute(Attribute.MAX_HEALTH) != null ? player.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
        player.setHealth(Math.max(2.0, maxHp * 0.6));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 6, 2));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 6, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 5, 1));
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 45, 0.6, 0.8, 0.6, 0.25);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.3f);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CAT_HISS, 1.0f, 1.2f);
        player.sendMessage(TextUtil.msg("<#5cdbd3><bold>『아홉 개의 목숨』</bold> 부적이 발동하여 치명상을 회피했습니다!"));
    }

    private void castProwlingProjectile(Player player) {
        long now = System.currentTimeMillis();
        long readyAt = wandCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < readyAt) {
            double left = (readyAt - now) / 1000.0;
            player.sendActionBar(TextUtil.mm("<red>사르르 탄환 재사용 대기중: " + String.format("%.1f초", left)));
            return;
        }
        wandCooldowns.put(player.getUniqueId(), now + 5000L);
        player.playSound(player.getLocation(), Sound.ENTITY_ALLAY_ITEM_GIVEN, 1.0f, 1.4f);

        new BukkitRunnable() {
            Location loc = player.getEyeLocation().add(player.getLocation().getDirection().multiply(0.8));
            Vector dir = player.getLocation().getDirection().normalize();
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks > 32 || !player.isOnline() || loc.getWorld() == null) {
                    cancel();
                    return;
                }

                // Gentle homing toward nearest Monster
                LivingEntity nearest = null;
                double bestSq = 64.0;
                for (Entity e : loc.getWorld().getNearbyEntities(loc, 8, 8, 8)) {
                    if (e instanceof Monster mob && !mob.isDead()) {
                        double dSq = mob.getEyeLocation().distanceSquared(loc);
                        if (dSq < bestSq) {
                            bestSq = dSq;
                            nearest = mob;
                        }
                    }
                }
                if (nearest != null) {
                    Vector desired = nearest.getEyeLocation().toVector().subtract(loc.toVector()).normalize();
                    dir = dir.multiply(0.72).add(desired.multiply(0.28)).normalize();
                }

                loc.add(dir.clone().multiply(0.85));
                loc.getWorld().spawnParticle(Particle.DUST, loc, 4, 0.08, 0.08, 0.08, 0,
                        new Particle.DustOptions(Color.fromRGB(255, 115, 190), 1.3f));
                loc.getWorld().spawnParticle(Particle.END_ROD, loc, 1, 0.02, 0.02, 0.02, 0.01);

                for (Entity e : loc.getWorld().getNearbyEntities(loc, 1.35, 1.35, 1.35)) {
                    if (e instanceof LivingEntity target && !e.equals(player) && !(e instanceof Player)) {
                        target.damage(14.0, player);
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
                        loc.getWorld().spawnParticle(Particle.FLASH, loc, 1);
                        loc.getWorld().playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.9f, 1.5f);
                        cancel();
                        return;
                    }
                }
                if (loc.getBlock().getType().isSolid()) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void castFinalChapterWaves(Player player) {
        long now = System.currentTimeMillis();
        long readyAt = tomeCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now < readyAt) {
            double left = (readyAt - now) / 1000.0;
            player.sendActionBar(TextUtil.mm("<red>대단원 재사용 대기중: " + String.format("%.1f초", left)));
            return;
        }
        tomeCooldowns.put(player.getUniqueId(), now + 14000L);
        player.sendMessage(TextUtil.msg("<light_purple><bold>📘 『대단원』 발동!</bold> <gray>세 번의 차원 파동이 전방을 휩씁니다!"));

        for (int wave = 0; wave < 3; wave++) {
            final int waveIndex = wave;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                Location origin = player.getLocation().add(0, 1.0, 0);
                Vector forward = player.getLocation().getDirection().setY(0).normalize();
                if (forward.lengthSquared() < 0.01) forward = new Vector(0, 0, 1);
                Vector right = new Vector(-forward.getZ(), 0, forward.getX());

                origin.getWorld().playSound(origin, Sound.ITEM_BOOK_PAGE_TURN, 1.2f, 0.8f + waveIndex * 0.25f);
                origin.getWorld().playSound(origin, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f + waveIndex * 0.2f);

                for (double dist = 1.5; dist <= 9.5; dist += 1.0) {
                    double halfWidth = 1.2 + dist * 0.35;
                    for (double side = -halfWidth; side <= halfWidth; side += 0.8) {
                        Location pt = origin.clone().add(forward.clone().multiply(dist)).add(right.clone().multiply(side));
                        origin.getWorld().spawnParticle(Particle.WITCH, pt, 2, 0.1, 0.1, 0.1, 0.02);
                        if (waveIndex == 2) {
                            origin.getWorld().spawnParticle(Particle.END_ROD, pt, 1, 0.05, 0.05, 0.05, 0.01);
                        }
                    }
                }

                for (Entity e : origin.getWorld().getNearbyEntities(origin.clone().add(forward.clone().multiply(5.0)), 6.0, 3.5, 6.0)) {
                    if (e instanceof Player ally) {
                        double maxHp = ally.getAttribute(Attribute.MAX_HEALTH) != null ? ally.getAttribute(Attribute.MAX_HEALTH).getValue() : 20.0;
                        ally.setHealth(Math.min(maxHp, ally.getHealth() + 3.0));
                    } else if (e instanceof LivingEntity mob && !(e instanceof org.bukkit.entity.Cat) && !(e instanceof org.bukkit.entity.Villager)) {
                        mob.damage(8.0, player);
                        if (waveIndex == 2) {
                            mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 4));
                            mob.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
                        }
                    }
                }
            }, wave * 12L);
        }
    }
}
