package kr.yuumi97.core.training;

import kr.yuumi97.core.hub.HubWorldManager;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;

import java.util.*;

/**
 * Fixed training targets use real combat events, then cancel native health loss after the
 * combat modifiers have run. The event ledger preserves the approved final damage for
 * the RPG monitor without allowing unrelated cancelled attacks or granting kill rewards.
 */
public final class TrainingDummyManager implements Listener {
    private static final double[][] ANCHORS = {{-6,65,-40.5},{-6,65,-30.5},{-6,65,-22.5}};
    private static final String MODEL = "prop_training_dummy";
    private final JavaPlugin plugin;
    private final HubWorldManager hub;
    private final NamespacedKey dummyKey, indexKey, visualKey, visualModeKey, decorKey, fireOwnerKey, poisonOwnerKey, fireExpiryKey, poisonExpiryKey, hitSequenceKey;
    private final Map<Integer,Vindicator> bodies = new HashMap<>();
    private record WindowKey(UUID player, int dummy) {}
    private record ApprovedHit(UUID player, int dummy, double damage) {}
    private final Map<WindowKey,DamageWindow> windows = new HashMap<>();
    private final Map<UUID,WindowKey> lastTarget = new HashMap<>();
    private final Map<UUID,TextDisplay> privateDisplays = new HashMap<>();
    private final Map<EntityDamageEvent,ApprovedHit> approved = new IdentityHashMap<>();
    private BukkitTask ticker;
    private boolean anchoring, stopped, initialized;
    private long ticks;

    public TrainingDummyManager(JavaPlugin plugin, HubWorldManager hub) {
        this.plugin=plugin; this.hub=hub;
        dummyKey=new NamespacedKey(plugin,"yuumi_training_dummy");
        indexKey=new NamespacedKey(plugin,"yuumi_training_dummy_index");
        visualKey=new NamespacedKey(plugin,"yuumi_training_dummy_visual");
        visualModeKey=new NamespacedKey(plugin,"yuumi_training_dummy_visual_mode");
        decorKey=new NamespacedKey(plugin,"hub_decor_tag");
        fireOwnerKey=new NamespacedKey(plugin,"yuumi_training_fire_owner");
        poisonOwnerKey=new NamespacedKey(plugin,"yuumi_training_poison_owner");
        fireExpiryKey=new NamespacedKey(plugin,"yuumi_training_fire_expires");
        poisonExpiryKey=new NamespacedKey(plugin,"yuumi_training_poison_expires");
        hitSequenceKey=new NamespacedKey(plugin,"yuumi_training_hit_sequence");
    }

    /** Register after the RPG listeners so final damage includes skills, forging and runes. */
    public void start() {
        stopped=false;
        Bukkit.getPluginManager().registerEvents(this,plugin);
        ticker=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,1,1);
    }

    public void stop() {
        stopped=true;
        if(ticker!=null)ticker.cancel();
        for(Vindicator body:bodies.values())if(body.isValid())resetEffects(body);
        clearPrivateDisplays();approved.clear();windows.clear();lastTarget.clear();
    }

    public boolean isDummy(Entity entity) {
        return entity!=null && entity.getPersistentDataContainer().has(dummyKey,PersistentDataType.BYTE);
    }

    public double approvedDamage(EntityDamageEvent event) {
        ApprovedHit hit=approved.get(event);
        return hit==null?-1:hit.damage;
    }

    public boolean isPracticing(Player player) {
        if(player==null||!player.isOnline()||player.isDead()||player.getGameMode()==GameMode.SPECTATOR||!player.getWorld().equals(hub.getHubWorld()))return false;
        WindowKey key=lastTarget.get(player.getUniqueId());
        DamageWindow window=key==null?null:windows.get(key);
        return window!=null&&window.isActive(System.nanoTime());
    }

    public List<LivingEntity> getDummies() {
        return bodies.values().stream().filter(Entity::isValid).map(e->(LivingEntity)e).toList();
    }

    public void reconcile() {
        if(stopped)return;
        World world=hub.getHubWorld();if(world==null)return;
        initialized=true;
        clearPrivateDisplays();windows.clear();lastTarget.clear();approved.clear();
        for(int index=0;index<ANCHORS.length;index++)anchor(world,index).getChunk().load();
        bodies.clear();
        Map<Integer,List<Entity>> visuals=new HashMap<>();
        for(Entity entity:world.getEntities()) {
            if(isDummy(entity)) {
                if(!entity.isValid()||entity.isDead()){entity.remove();continue;}
                int index=entity.getPersistentDataContainer().getOrDefault(indexKey,PersistentDataType.INTEGER,-1);
                if(index<0||index>=ANCHORS.length||!(entity instanceof Vindicator body)||bodies.putIfAbsent(index,body)!=null){entity.remove();continue;}
                configure(body,index);
            }
            Integer visual=entity.getPersistentDataContainer().get(visualKey,PersistentDataType.INTEGER);
            if(visual!=null) {
                if(visual<0||visual>=ANCHORS.length)entity.remove();
                else visuals.computeIfAbsent(visual,key->new ArrayList<>()).add(entity);
            }
        }
        boolean model=hub.hasBundledModel(MODEL);
        for(int index=0;index<ANCHORS.length;index++) {
            if(!bodies.containsKey(index)) {
                final int id=index;
                Vindicator body=world.spawn(anchor(world,index),Vindicator.class,entity->configure(entity,id));
                bodies.put(index,body);
            }
            String mode=model?"model":"fallback";
            List<Entity> existing=visuals.getOrDefault(index,List.of());
            int expected=model?2:6;
            Location expectedAnchor=anchor(world,index);
            if(existing.size()!=expected||existing.stream().anyMatch(e->!mode.equals(e.getPersistentDataContainer().get(visualModeKey,PersistentDataType.STRING))
                    ||Math.abs(e.getLocation().getX()-expectedAnchor.getX())>1.2||Math.abs(e.getLocation().getZ()-expectedAnchor.getZ())>1.2)) {
                existing.forEach(Entity::remove);
                createVisuals(world,index,model);
            }
        }
    }

    private Location anchor(World world,int index) {double[] a=ANCHORS[index];return new Location(world,a[0],a[1],a[2],-90,0);}
    public List<Location> getAnchors(){World world=hub.getHubWorld();return java.util.stream.IntStream.range(0,ANCHORS.length).mapToObj(i->anchor(world,i)).toList();}

    private void configure(Vindicator body,int index) {
        body.getPersistentDataContainer().set(dummyKey,PersistentDataType.BYTE,(byte)1);
        body.getPersistentDataContainer().set(indexKey,PersistentDataType.INTEGER,index);
        body.getPersistentDataContainer().set(decorKey,PersistentDataType.BYTE,(byte)1);
        if(!body.getPersistentDataContainer().has(hitSequenceKey,PersistentDataType.LONG))body.getPersistentDataContainer().set(hitSequenceKey,PersistentDataType.LONG,0L);
        body.setAI(false);body.setGravity(false);body.setInvisible(true);body.setInvulnerable(false);
        body.setSilent(true);body.setCollidable(true);body.setCanPickupItems(false);body.setPersistent(true);body.setRemoveWhenFarAway(false);
        // Paper ties projectile targeting to the collidable flag. A collision-free team
        // prevents physical pushing without making the target immune to native arrows.
        var scoreboard=Bukkit.getScoreboardManager().getMainScoreboard();
        var team=scoreboard.getTeam("yuumi97_dummies");if(team==null)team=scoreboard.registerNewTeam("yuumi97_dummies");
        team.setOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE,org.bukkit.scoreboard.Team.OptionStatus.NEVER);
        team.addEntry(body.getUniqueId().toString());
        if(body.getEquipment()!=null){body.getEquipment().clear();for(EquipmentSlot slot:EquipmentSlot.values())if(slot!=EquipmentSlot.BODY)body.getEquipment().setDropChance(slot,0);}
        setBase(body,Attribute.MAX_HEALTH,1024);setBase(body,Attribute.ARMOR,0);setBase(body,Attribute.ARMOR_TOUGHNESS,0);
        setBase(body,Attribute.KNOCKBACK_RESISTANCE,1);setBase(body,Attribute.MOVEMENT_SPEED,0);setBase(body,Attribute.ATTACK_DAMAGE,0);
        resetEffects(body);body.setHealth(1024);body.setNoDamageTicks(0);hold(body,index);
    }

    private void setBase(LivingEntity body,Attribute attribute,double value) {var instance=body.getAttribute(attribute);if(instance!=null)instance.setBaseValue(value);}
    private void resetEffects(LivingEntity body){body.setFireTicks(0);for(var effect:body.getActivePotionEffects())body.removePotionEffect(effect.getType());body.getPersistentDataContainer().remove(fireOwnerKey);body.getPersistentDataContainer().remove(poisonOwnerKey);body.getPersistentDataContainer().remove(fireExpiryKey);body.getPersistentDataContainer().remove(poisonExpiryKey);}
    private void hold(Vindicator body,int index) {
        Location expected=anchor(body.getWorld(),index);
        body.setVelocity(new Vector());body.setNoDamageTicks(0);
        if(body.getLocation().distanceSquared(expected)>.00001){anchoring=true;try{body.teleport(expected);}finally{anchoring=false;}}
        if(Math.abs(body.getLocation().getYaw()+90)>.01||Math.abs(body.getLocation().getPitch())>.01)body.setRotation(-90,0);
        if(!body.isDead()&&body.getHealth()<1024)body.setHealth(1024);
        long now=System.nanoTime();var pdc=body.getPersistentDataContainer();
        if(body.getFireTicks()<=0||pdc.getOrDefault(fireExpiryKey,PersistentDataType.LONG,Long.MIN_VALUE)<=now){pdc.remove(fireOwnerKey);pdc.remove(fireExpiryKey);}
        if(!body.hasPotionEffect(org.bukkit.potion.PotionEffectType.POISON)||pdc.getOrDefault(poisonExpiryKey,PersistentDataType.LONG,Long.MIN_VALUE)<=now){pdc.remove(poisonOwnerKey);pdc.remove(poisonExpiryKey);}
    }

    private void createVisuals(World world,int index,boolean model) {
        Location at=anchor(world,index);String mode=model?"model":"fallback";
        ItemDisplay custom=model?hub.spawnAnchoredProp(at,MODEL,1.35f,0f,-90f):null;
        if(custom!=null)tagVisual(custom,index,mode);
        else {
            mode="fallback";
            block(at.clone().add(-.1,0,-.1),Material.OAK_FENCE,.2f,1.4f,.2f,index,mode);
            block(at.clone().add(-.28,.55,-.38),Material.HAY_BLOCK,.56f,.86f,.76f,index,mode);
            BlockDisplay head=block(at.clone().add(-.32,1.4,-.32),Material.CARVED_PUMPKIN,.64f,.64f,.64f,index,mode);
            if(head.getBlock() instanceof Directional data){data.setFacing(org.bukkit.block.BlockFace.EAST);head.setBlock(data);}
            block(at.clone().add(-.12,1.0,.35),Material.OAK_PLANKS,.24f,.18f,.5f,index,mode);
            block(at.clone().add(-.12,1.0,-.85),Material.OAK_PLANKS,.24f,.18f,.5f,index,mode);
        }
        TextDisplay label=world.spawn(at.clone().add(0,2.95,0),TextDisplay.class);
        label.text(TextUtil.mm("<gold>🎯 별빛 수련 허수아비 "+(index+1)+"</gold>\n<gray>성물·룬·스킬 연습 · 경험치 없음</gray>"));
        label.setBillboard(Display.Billboard.CENTER);label.setAlignment(TextDisplay.TextAlignment.CENTER);label.setShadowed(true);tagVisual(label,index,mode);
    }

    private BlockDisplay block(Location at,Material material,float x,float y,float z,int index,String mode) {
        BlockDisplay display=at.getWorld().spawn(at,BlockDisplay.class);
        display.setBlock(material.createBlockData());display.setTransformation(new Transformation(new Vector3f(),new Quaternionf(),new Vector3f(x,y,z),new Quaternionf()));
        tagVisual(display,index,mode);return display;
    }
    private void tagVisual(Entity entity,int index,String mode) {
        entity.setPersistent(true);entity.getPersistentDataContainer().set(decorKey,PersistentDataType.BYTE,(byte)1);
        entity.getPersistentDataContainer().set(visualKey,PersistentDataType.INTEGER,index);entity.getPersistentDataContainer().set(visualModeKey,PersistentDataType.STRING,mode);
    }

    private void tick() {
        if(stopped||!initialized)return;
        if(++ticks%20==0&&(bodies.size()!=3||bodies.values().stream().anyMatch(body->!body.isValid()||body.isDead())))reconcile();
        for(var entry:bodies.entrySet())if(entry.getValue().isValid()&&!entry.getValue().isDead())hold(entry.getValue(),entry.getKey());
        long now=System.nanoTime();windows.entrySet().removeIf(entry->!entry.getValue().isActive(now));
        lastTarget.entrySet().removeIf(entry->!windows.containsKey(entry.getValue()));
        privateDisplays.entrySet().removeIf(entry->{Player owner=Bukkit.getPlayer(entry.getKey());if(!entry.getValue().isValid()||!isPracticing(owner)){entry.getValue().remove();return true;}return false;});
        if(ticks%5==0)for(Player player:Bukkit.getOnlinePlayers())if(isPracticing(player))show(player,now);
    }

    private Player directPlayer(Entity source) {
        return source instanceof Player player?player:source instanceof Projectile projectile&&projectile.getShooter() instanceof Player player?player:null;
    }
    private Player resolvePlayer(EntityDamageEvent event) {
        if(event instanceof EntityDamageByEntityEvent attack)return directPlayer(attack.getDamager());
        Entity entity=event.getEntity();NamespacedKey owner=switch(event.getCause()){case FIRE_TICK->fireOwnerKey;case POISON->poisonOwnerKey;default->null;};
        if(owner==null)return null;
        NamespacedKey expiry=owner.equals(fireOwnerKey)?fireExpiryKey:poisonExpiryKey;
        if(entity.getPersistentDataContainer().getOrDefault(expiry,PersistentDataType.LONG,Long.MIN_VALUE)<=System.nanoTime())return null;
        if(entity instanceof LivingEntity living&&(owner.equals(fireOwnerKey)?living.getFireTicks()<=0:!living.hasPotionEffect(org.bukkit.potion.PotionEffectType.POISON)))return null;
        String encoded=entity.getPersistentDataContainer().get(owner,PersistentDataType.STRING);
        if(encoded!=null)try{return Bukkit.getPlayer(UUID.fromString(encoded));}catch(IllegalArgumentException ignored){}
        return null;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void capture(EntityDamageEvent event) {
        if(!isDummy(event.getEntity()))return;
        boolean previouslyCancelled=event.isCancelled();event.setCancelled(true);
        if(previouslyCancelled||stopped||!(event.getEntity() instanceof LivingEntity body))return;
        Player player=resolvePlayer(event);int index=body.getPersistentDataContainer().getOrDefault(indexKey,PersistentDataType.INTEGER,-1);
        if(player==null||!player.isOnline()||player.isDead()||player.getGameMode()==GameMode.SPECTATOR||!player.getWorld().equals(body.getWorld())||index<0||index>=3)return;
        double damage=event.getFinalDamage();if(!Double.isFinite(damage)||damage<=0)return;
        approved.put(event,new ApprovedHit(player.getUniqueId(),index,damage));
        var pdc=body.getPersistentDataContainer();pdc.set(hitSequenceKey,PersistentDataType.LONG,pdc.getOrDefault(hitSequenceKey,PersistentDataType.LONG,0L)+1);
        Bukkit.getScheduler().runTask(plugin,()->approved.remove(event));
        WindowKey key=new WindowKey(player.getUniqueId(),index);windows.computeIfAbsent(key,id->new DamageWindow()).record(System.nanoTime(),damage);lastTarget.put(player.getUniqueId(),key);
        body.setNoDamageTicks(0);if(!body.isDead())body.setHealth(1024);
        player.playSound(body.getLocation(),Sound.BLOCK_NOTE_BLOCK_HAT,.35f,1.4f);
        body.getWorld().spawnParticle(Particle.CRIT,body.getLocation().add(0,1,0),4,.25,.4,.25,.01);
        show(player,System.nanoTime());
    }

    private void show(Player player,long now) {
        WindowKey key=lastTarget.get(player.getUniqueId());DamageWindow window=key==null?null:windows.get(key);if(window==null)return;
        String message=String.format(Locale.ROOT,"<gold>🎯 [수련 허수아비 %d]</gold> <white>이번 타격: %.1f DMG</white> <dark_gray>│</dark_gray> <aqua>최근 5초 DPS: %.1f</aqua>",key.dummy+1,window.lastDamage(),window.dps(now));
        player.sendActionBar(TextUtil.mm(message));
        Location location=anchor(player.getWorld(),key.dummy).add(0,3.35,0);
        TextDisplay display=privateDisplays.get(player.getUniqueId());
        if(display==null||!display.isValid()){
            display=player.getWorld().spawn(location,TextDisplay.class,text->{text.setVisibleByDefault(false);text.setPersistent(false);text.setBillboard(Display.Billboard.CENTER);text.setLineWidth(400);text.setShadowed(true);text.setBackgroundColor(Color.fromARGB(175,20,14,30));});
            privateDisplays.put(player.getUniqueId(),display);player.showEntity(plugin,display);
        }
        if(display.getLocation().distanceSquared(location)>.0001)display.teleport(location);
        display.text(TextUtil.mm(message.replace("</gold> <white>","</gold>\n<white>")));
    }
    private void clearPrivateDisplays(){for(TextDisplay display:privateDisplays.values())display.remove();privateDisplays.clear();}

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void combust(EntityCombustByEntityEvent event){if(!isDummy(event.getEntity()))return;Player player=directPlayer(event.getCombuster());if(player!=null){event.getEntity().getPersistentDataContainer().set(fireOwnerKey,PersistentDataType.STRING,player.getUniqueId().toString());event.getEntity().getPersistentDataContainer().set(fireExpiryKey,PersistentDataType.LONG,System.nanoTime()+(long)(event.getDuration()*1_000_000_000L));}}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void potion(PotionSplashEvent event){if(!(event.getPotion().getShooter() instanceof Player player))return;
        var incoming=event.getPotion().getEffects().stream().filter(effect->effect.getType().equals(org.bukkit.potion.PotionEffectType.POISON)).findFirst().orElse(null);if(incoming==null)return;
        for(LivingEntity entity:event.getAffectedEntities())if(isDummy(entity)){
            int duration=(int)(incoming.getDuration()*event.getIntensity(entity)+.5);var previous=entity.getPotionEffect(incoming.getType());
            if(duration<=20||previous!=null&&!kr.yuumi97.core.rpg.RpgRules.replacesEffect(previous.getAmplifier(),previous.getDuration(),incoming.getAmplifier(),duration))continue;
            Bukkit.getScheduler().runTask(plugin,()->{if(stopped||event.isCancelled()||!entity.isValid())return;var active=entity.getPotionEffect(incoming.getType());
                if(active==null||active.getAmplifier()!=incoming.getAmplifier()||active.getDuration()<duration-2||active.getDuration()>duration)return;
                entity.getPersistentDataContainer().set(poisonOwnerKey,PersistentDataType.STRING,player.getUniqueId().toString());entity.getPersistentDataContainer().set(poisonExpiryKey,PersistentDataType.LONG,System.nanoTime()+active.getDuration()*50_000_000L);
            });
        }}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void teleport(EntityTeleportEvent event){if(isDummy(event.getEntity())&&!anchoring)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void target(EntityTargetLivingEntityEvent event){if(isDummy(event.getEntity()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void death(EntityDeathEvent event){if(!isDummy(event.getEntity()))return;event.getDrops().clear();event.setDroppedExp(0);Bukkit.getScheduler().runTask(plugin,this::reconcile);}
    @EventHandler public void quit(PlayerQuitEvent event){UUID id=event.getPlayer().getUniqueId();lastTarget.remove(id);windows.keySet().removeIf(key->key.player.equals(id));TextDisplay display=privateDisplays.remove(id);if(display!=null)display.remove();}
}
