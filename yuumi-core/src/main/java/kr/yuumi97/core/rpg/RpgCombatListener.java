package kr.yuumi97.core.rpg;

import kr.yuumi97.core.util.TextUtil;
import org.bukkit.*;import org.bukkit.attribute.*;import org.bukkit.block.Block;import org.bukkit.block.Container;import org.bukkit.block.data.Ageable;import org.bukkit.entity.*;import org.bukkit.event.*;import org.bukkit.event.block.Action;import org.bukkit.event.entity.*;import org.bukkit.event.player.*;import org.bukkit.inventory.*;import org.bukkit.inventory.meta.*;import org.bukkit.persistence.PersistentDataType;import org.bukkit.potion.*;import org.bukkit.scheduler.*;import org.bukkit.util.Vector;
import java.util.*;import java.util.concurrent.ThreadLocalRandom;import java.util.function.*;

/** Job skills, runes and relics share one damage path to prevent proc recursion. */
public final class RpgCombatListener implements Listener {
    private final RpgJobManager r;
    private final Map<UUID,Map<String,Long>> buffs=new HashMap<>();
    private final Map<UUID,Long> frozen=new HashMap<>(), vulnerable=new HashMap<>(), runeReady=new HashMap<>(), leechReady=new HashMap<>();
    private final Map<UUID,Location> frozenAt=new HashMap<>();
    private final Map<UUID,Long> fireReady=new HashMap<>();
    private final Map<UUID,Long> npcSuppression=new HashMap<>();
    private final Map<UUID,ItemStack> shots=new HashMap<>();
    private final Map<UUID,Integer> arrows=new HashMap<>();
    private final Map<UUID,Long> epochs=new HashMap<>();
    private static final class Mark {final UUID caster;final long epoch;double damage;Mark(UUID caster,long epoch,double damage){this.caster=caster;this.epoch=epoch;this.damage=damage;}}
    private final Map<UUID,List<Mark>> marks=new HashMap<>();
    private final Set<Entity> effects=new HashSet<>();
    private final Set<BukkitTask> tasks=new HashSet<>();
    private final Map<UUID,Set<BukkitTask>> playerTasks=new HashMap<>();
    private int damageDepth;
    private BukkitTask ticker;
    private long previousTick=System.currentTimeMillis();
    RpgCombatListener(RpgJobManager r){this.r=r;}
    public boolean isSecondaryDamage(){return damageDepth>0;}
    public ItemStack shotWeapon(Projectile p){return shots.get(p.getUniqueId());}
    public boolean buff(Player p,String name){return System.currentTimeMillis()<buffs.getOrDefault(p.getUniqueId(),Map.of()).getOrDefault(name,0L);}
    public void grant(Player p,String name,double seconds){buffs.computeIfAbsent(p.getUniqueId(),k->new HashMap<>()).put(name,System.currentTimeMillis()+(long)(seconds*1000));}
    public void clearTemporary(Player p){buffs.remove(p.getUniqueId());epochs.merge(p.getUniqueId(),1L,Long::sum);
        marks.values().forEach(list->list.removeIf(mark->mark.caster.equals(p.getUniqueId())));marks.values().removeIf(List::isEmpty);
        Set<BukkitTask> active=playerTasks.remove(p.getUniqueId());if(active!=null){for(BukkitTask t:active)t.cancel();tasks.removeAll(active);}
        String owner=p.getUniqueId().toString();NamespacedKey key=new NamespacedKey(r.plugin,"rpg_effect_owner");effects.removeIf(e->{if(owner.equals(e.getPersistentDataContainer().get(key,PersistentDataType.STRING))){e.remove();return true;}return false;});}
    private boolean valid(Player p,JobType job){return p.isOnline()&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&p.getGameMode()!=GameMode.CREATIVE&&r.getJob(p)==job;}
    private boolean hostile(Player p,Entity e){return e instanceof LivingEntity m&&!(e instanceof Player)&&!m.isDead()&&(r.isTrainingDummy(e)||e instanceof Monster||r.taggedEnemy(e))&&r.canAffect(p,e);}
    private List<LivingEntity> enemies(Player p,Location at,double radius){List<LivingEntity> out=new ArrayList<>();for(Entity e:at.getWorld().getNearbyEntities(at,radius,radius,radius))if(hostile(p,e)&&at.distanceSquared(e.getLocation())<=radius*radius)out.add((LivingEntity)e);return out;}
    private double maxHp(Player p){return p.getAttribute(Attribute.MAX_HEALTH).getValue();}
    private void syncShieldCapacity(Player p){double capacity=Math.max(r.hasCharm(p,"charm_starlight_aegis")?maxHp(p)*.15:0,buff(p,"miracle_shield")?12:0);
        attribute(p,Attribute.MAX_ABSORPTION,"shield_capacity",capacity,AttributeModifier.Operation.ADD_NUMBER);
        AttributeInstance max=p.getAttribute(Attribute.MAX_ABSORPTION);if(max!=null&&p.getAbsorptionAmount()>max.getValue())p.setAbsorptionAmount(max.getValue());}
    public void shield(Player p,double amount){if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR)return;syncShieldCapacity(p);p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(),amount));}
    private void heal(Player p,double hp){if(!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR)p.setHealth(Math.min(maxHp(p),p.getHealth()+hp));}
    private boolean potion(LivingEntity e,PotionEffectType type,int ticks,int amplifier){return e.addPotionEffect(new PotionEffect(type,ticks,amplifier,true,true,true));}
    private void poison(Player player,LivingEntity target,int ticks,int amplifier){
        PotionEffect previous=target.getPotionEffect(PotionEffectType.POISON);
        boolean replaces=previous==null||RpgRules.replacesEffect(previous.getAmplifier(),previous.getDuration(),amplifier,ticks);
        if(potion(target,PotionEffectType.POISON,ticks,amplifier)&&replaces){PotionEffect active=target.getPotionEffect(PotionEffectType.POISON);if(active!=null&&active.getAmplifier()==amplifier&&active.getDuration()>=ticks-1)r.recordTrainingEffect(player,target,EntityDamageEvent.DamageCause.POISON,active.getDuration());}
    }
    private void fire(Player player,LivingEntity target,int ticks){int duration=Math.max(target.getFireTicks(),ticks);
        if(r.isTrainingDummy(target)){later(player,1,()->{if(target.isValid()&&!target.isDead()){r.recordTrainingEffect(player,target,EntityDamageEvent.DamageCause.FIRE_TICK,duration);target.setFireTicks(duration);}});}
        else target.setFireTicks(duration);}
    private void push(LivingEntity entity,Vector velocity){if(!r.isTrainingDummy(entity))entity.setVelocity(velocity);}
    private void freeze(LivingEntity e,int ticks){if(r.isTrainingDummy(e)){potion(e,PotionEffectType.SLOWNESS,ticks,9);return;}frozen.put(e.getUniqueId(),System.currentTimeMillis()+ticks*50L);frozenAt.put(e.getUniqueId(),e.getLocation());potion(e,PotionEffectType.SLOWNESS,ticks,9);e.setVelocity(new Vector());}
    private boolean boss(Entity e){return e.getPersistentDataContainer().getKeys().stream().anyMatch(k->k.getKey().contains("boss"))||e.getScoreboardTags().stream().anyMatch(k->k.contains("boss"));}
    private double skillScale(Player p){double evo=switch(r.getSignatures().evolution(p)){case 2->r.cfg("rpg.signature.transcendent-multiplier",1.2);case 3->r.cfg("rpg.signature.mythic-multiplier",1.4);default->1;};return (1+.025*(r.getLevel(p)-1))*evo*(1+.03*r.perk(p,"power"));}
    private boolean behind(Player p,LivingEntity target){Vector to=target.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);return to.lengthSquared()>.001&&target.getLocation().getDirection().setY(0).normalize().dot(to.normalize())>.35;}
    private boolean critical(Player p,LivingEntity target,boolean signature){return signature&&r.getJob(p)==JobType.SHADOW&&behind(p,target)||r.hasCharm(p,"charm_crimson_claw")&&ThreadLocalRandom.current().nextDouble()<.15||buff(p,"treasure_crit")&&ThreadLocalRandom.current().nextDouble()<.20;}
    private double signatureStarDamage(Player p){JobType job=r.getJob(p);if(job==null||!r.holdingSignature(p)||!job.combat()&&job!=JobType.BLACKSMITH)return 0;var meta=p.getInventory().getItemInMainHand().getItemMeta().getPersistentDataContainer();
        int star=meta.getOrDefault(new NamespacedKey(r.plugin,"starlight_level"),PersistentDataType.INTEGER,meta.getOrDefault(new NamespacedKey(r.plugin,"yuumi_star_level"),PersistentDataType.INTEGER,0));double[] damage={0,1,1.8,2.6,3.6,4.6,5.6,7};return damage[Math.clamp(star,0,7)];}
    private void hit(Player p,LivingEntity target,double base){if(!hostile(p,target))return;double dmg=(base+signatureStarDamage(p))*skillScale(p);if(r.hasCharm(p,"charm_crimson_claw")&&boss(target))dmg*=1.12;
        if(critical(p,target,r.holdingSignature(p)))dmg*=1.45;
        dmg*=1+r.getWorkshop().feastEffect(p,"VALOR","damage",.15);if(System.currentTimeMillis()<vulnerable.getOrDefault(target.getUniqueId(),0L))dmg*=1.25;
        raw(p,target,dmg);alliedFire(p,target);}
    private void alliedFire(Player p,LivingEntity target){long now=System.currentTimeMillis();if(buff(p,"allied_fire")&&now>=fireReady.getOrDefault(p.getUniqueId(),0L)){fireReady.put(p.getUniqueId(),now+1000);raw(p,target,3);}}
    private void raw(Player p,LivingEntity target,double damage){if(!p.isOnline()||p.isDead()||p.getGameMode()==GameMode.SPECTATOR||!hostile(p,target))return;damageDepth++;try{target.setNoDamageTicks(0);target.damage(damage,p);}finally{damageDepth--;}}
    private void leech(Player p,double damage){if(r.hasCharm(p,"charm_vampiric_purr")&&System.currentTimeMillis()>=leechReady.getOrDefault(p.getUniqueId(),0L)){heal(p,damage*.06);leechReady.put(p.getUniqueId(),System.currentTimeMillis()+500);}}
    @EventHandler(priority=EventPriority.MONITOR) public void actualDamage(EntityDamageEvent e){double approved=r.approvedTrainingDamage(e);if(e.isCancelled()&&approved<0)return;
        Player p=e instanceof EntityDamageByEntityEvent attack?(attack.getDamager() instanceof Player player?player:attack.getDamager() instanceof Projectile projectile&&projectile.getShooter() instanceof Player shooter?shooter:null):null;
        if(p==null&&approved>=0&&r.isTrainingDummy(e.getEntity())){String ownerKey=e.getCause()==EntityDamageEvent.DamageCause.POISON?"yuumi_training_poison_owner":"yuumi_training_fire_owner";String owner=e.getEntity().getPersistentDataContainer().get(new NamespacedKey(r.plugin,ownerKey),PersistentDataType.STRING);if(owner!=null)try{p=Bukkit.getPlayer(UUID.fromString(owner));}catch(IllegalArgumentException ignored){}}
        if(p==null||!(e.getEntity() instanceof LivingEntity target)||!hostile(p,target)||e.getFinalDamage()<=0)return;double actual=approved>=0?approved:Math.min(e.getFinalDamage(),target.getHealth()+target.getAbsorptionAmount());leech(p,actual);
        List<Mark> active=marks.get(target.getUniqueId());if(active!=null)for(Mark mark:active)mark.damage+=actual;
        if(buff(p,"assassin_mark")){buffs.get(p.getUniqueId()).remove("assassin_mark");mark(p,target,actual);}}
    private Location aim(Player p,int range){org.bukkit.util.RayTraceResult ray=p.getWorld().rayTraceBlocks(p.getEyeLocation(),p.getEyeLocation().getDirection(),range,FluidCollisionMode.NEVER,true);return ray==null?p.getLocation().add(p.getLocation().getDirection().multiply(range)):ray.getHitPosition().toLocation(p.getWorld());}
    private LivingEntity target(Player p,double range){org.bukkit.util.RayTraceResult ray=p.getWorld().rayTraceEntities(p.getEyeLocation(),p.getEyeLocation().getDirection(),range,.7,e->hostile(p,e));
        if(ray==null||!(ray.getHitEntity() instanceof LivingEntity e)||!p.hasLineOfSight(e))return null;return e;}
    private Vector direction(Player p){Vector v=p.getLocation().getDirection().setY(0);return v.lengthSquared()<.01?new Vector(0,0,1):v.normalize();}
    private void particles(Location l,Particle particle,int amount){l.getWorld().spawnParticle(particle,l.clone().add(0,1,0),amount,.5,.5,.5,.03);}
    private void circle(Location l,double radius,Particle particle){for(int n=0;n<36;n++){double a=n*Math.PI/18;particles(l.clone().add(Math.cos(a)*radius,0,Math.sin(a)*radius),particle,1);}}
    private long trainingSequence(LivingEntity entity){return entity.getPersistentDataContainer().getOrDefault(new NamespacedKey(r.plugin,"yuumi_training_hit_sequence"),PersistentDataType.LONG,0L);}
    private void area(Player p,Location at,double radius,double damage,Consumer<LivingEntity> effect){for(LivingEntity e:enemies(p,at,radius)){long before=trainingSequence(e);hit(p,e,damage);if(!e.isDead()&&(!r.isTrainingDummy(e)||trainingSequence(e)!=before))effect.accept(e);}particles(at,Particle.ENCHANT,22);}
    private void repeat(Player p,int count,int period,IntConsumer action,Runnable finish){JobType j=r.getJob(p);long epoch=epochs.getOrDefault(p.getUniqueId(),0L);BukkitTask task=new BukkitRunnable(){int n;public void run(){
        if(!valid(p,j)||epochs.getOrDefault(p.getUniqueId(),0L)!=epoch||n>=count){cancel();int id=getTaskId();tasks.removeIf(t->t.getTaskId()==id);Set<BukkitTask> active=playerTasks.get(p.getUniqueId());if(active!=null)active.removeIf(t->t.getTaskId()==id);if(finish!=null)finish.run();return;}action.accept(n++);}
        }.runTaskTimer(r.plugin,0,period);tasks.add(task);playerTasks.computeIfAbsent(p.getUniqueId(),k->new HashSet<>()).add(task);}
    private void later(Player p,int ticks,Runnable action){JobType job=r.getJob(p);long epoch=epochs.getOrDefault(p.getUniqueId(),0L);BukkitTask[] holder=new BukkitTask[1];holder[0]=Bukkit.getScheduler().runTaskLater(r.plugin,()->{
        tasks.remove(holder[0]);Set<BukkitTask> active=playerTasks.get(p.getUniqueId());if(active!=null)active.remove(holder[0]);if(valid(p,job)&&epochs.getOrDefault(p.getUniqueId(),0L)==epoch)action.run();},ticks);
        tasks.add(holder[0]);playerTasks.computeIfAbsent(p.getUniqueId(),id->new HashSet<>()).add(holder[0]);}
    private void refundPrimary(Player p){String path=r.jobPath(p)+".cooldowns.primary";long now=System.currentTimeMillis();r.data.set(path,now+Math.max(0,r.data.getLong(path,now)-now)/2);r.changed();}
    private void effect(Player p,Entity entity){entity.getPersistentDataContainer().set(new NamespacedKey(r.plugin,"rpg_effect_owner"),PersistentDataType.STRING,p.getUniqueId().toString());effects.add(entity);}
    private double partyRadius(Player p,double base){return base+r.perk(p,"efficiency")*.3;}
    private double pd(Player p,double fallback){return r.cfg("rpg.skills."+r.getJob(p)+".primary-damage",fallback);}
    private double ud(Player p,String component,double fallback){return r.cfg("rpg.skills."+r.getJob(p)+"."+r.getAwakening(p)+"-"+component,fallback);}
    private void allies(Player p,double radius,Consumer<Player> effect){for(Player a:r.allies(p,partyRadius(p,radius)))effect.accept(a);}
    private boolean ready(Player p,String name,double seconds){String path=r.jobPath(p)+".cooldowns."+name;long now=System.currentTimeMillis(),at=r.data.getLong(path,0);if(now<at){p.sendActionBar(TextUtil.mm("<red>재사용 대기 "+String.format("%.1f",(at-now)/1000.0)+"초"));return false;}
        r.data.set(path,now+RpgRules.cooldownMillis(seconds,r.perk(p,"mobility")));r.changed();return true;}
    public void start(){ticker=Bukkit.getScheduler().runTaskTimer(r.plugin,()->{long now=System.currentTimeMillis();long elapsed=Math.min(1000,now-previousTick);previousTick=now;
        for(Player p:Bukkit.getOnlinePlayers()){sync(p);double haste=(r.getJob(p)==JobType.ARCANIST?.08:0)+r.getWorkshop().feastEffect(p,"SWIFT","cooldown-recovery",.10);double speed=(1+haste)*(buff(p,"cauldron")?1.5:1);
            if(r.getJob(p)!=null&&speed>1)for(String cd:List.of("primary","ultimate")){String path=r.jobPath(p)+".cooldowns."+cd;long at=r.data.getLong(path,0);if(at>now){r.data.set(path,Math.max(now,at-(long)(elapsed*(speed-1))));r.changed();}}
            if(r.getJob(p)==JobType.ANGLER)potion(p,PotionEffectType.WATER_BREATHING,40,0);
        }
        frozen.entrySet().removeIf(e->{Entity entity=Bukkit.getEntity(e.getKey());if(e.getValue()<=now||entity==null||entity.isDead()){frozenAt.remove(e.getKey());return true;}entity.setVelocity(new Vector());Location anchor=frozenAt.get(e.getKey());if(anchor!=null&&entity.getWorld().equals(anchor.getWorld())&&entity.getLocation().distanceSquared(anchor)>.01)entity.teleport(anchor);return false;});
        vulnerable.entrySet().removeIf(e->e.getValue()<=now);shots.entrySet().removeIf(e->{Entity entity=Bukkit.getEntity(e.getKey());return entity==null||entity.isDead();});
    },5,5);}
    public void stop(){if(ticker!=null)ticker.cancel();for(BukkitTask t:tasks)t.cancel();for(Entity e:effects)if(e.isValid())e.remove();effects.clear();shots.clear();frozen.clear();frozenAt.clear();for(Player p:Bukkit.getOnlinePlayers())removeStats(p);}
    private void attribute(Player p,Attribute attr,String id,double n,AttributeModifier.Operation op){AttributeInstance a=p.getAttribute(attr);if(a==null)return;NamespacedKey key=new NamespacedKey(r.plugin,"rpg_"+id);AttributeModifier old=a.getModifier(key);if(old!=null&&Math.abs(old.getAmount()-n)<.0001)return;if(old!=null)a.removeModifier(key);if(Math.abs(n)>.0001)a.addTransientModifier(new AttributeModifier(key,n,op));}
    private void sync(Player p){JobType j=r.getJob(p);boolean active=j!=null&&!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR;double speed=active?(j==JobType.SHADOW?.12:0)+r.perk(p,"mobility")*.01:0;
        speed+=r.hasCharm(p,"charm_golden_whisker")?.10:0;speed+=r.getWorkshop().feastEffect(p,"SWIFT","speed",.12);double gale=equippedRune(p,"GALE");speed+=r.cfg("runes.GALE.speed",.06)*gale;
        attribute(p,Attribute.MOVEMENT_SPEED,"speed",speed,AttributeModifier.Operation.ADD_SCALAR);
        attribute(p,Attribute.MAX_HEALTH,"health",(active&&j==JobType.PALADIN?r.getLevel(p)*.8:0)+r.getWorkshop().feastEffect(p,"VALOR","health",4),AttributeModifier.Operation.ADD_NUMBER);
        syncShieldCapacity(p);
        attribute(p,Attribute.LUCK,"luck",r.hasCharm(p,"charm_golden_whisker")?2:0,AttributeModifier.Operation.ADD_NUMBER);
        attribute(p,Attribute.BLOCK_INTERACTION_RANGE,"range",buff(p,"architect")?3:0,AttributeModifier.Operation.ADD_NUMBER);
        int stage=r.getSignatures().evolution(p);attribute(p,Attribute.MINING_EFFICIENCY,"mining",active&&r.holdingSignature(p)&&(j==JobType.MINER||j==JobType.CARPENTER||j==JobType.FARMER)?stage*2:0,AttributeModifier.Operation.ADD_NUMBER);
        if(!p.isDead()&&p.getHealth()>maxHp(p))p.setHealth(maxHp(p));}
    private void removeStats(Player p){attribute(p,Attribute.MOVEMENT_SPEED,"speed",0,AttributeModifier.Operation.ADD_SCALAR);attribute(p,Attribute.MAX_HEALTH,"health",0,AttributeModifier.Operation.ADD_NUMBER);attribute(p,Attribute.LUCK,"luck",0,AttributeModifier.Operation.ADD_NUMBER);attribute(p,Attribute.BLOCK_INTERACTION_RANGE,"range",0,AttributeModifier.Operation.ADD_NUMBER);attribute(p,Attribute.MINING_EFFICIENCY,"mining",0,AttributeModifier.Operation.ADD_NUMBER);attribute(p,Attribute.MAX_ABSORPTION,"shield_capacity",0,AttributeModifier.Operation.ADD_NUMBER);if(!p.isDead()&&p.getHealth()>maxHp(p))p.setHealth(maxHp(p));}
    private String rune(ItemStack item){return item==null||!item.hasItemMeta()?null:item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey(r.plugin,"yuumi_rune_type"),PersistentDataType.STRING);}
    private double quality(ItemStack item){return item.getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey(r.plugin,"yuumi_rune_masterwork"),PersistentDataType.INTEGER,0)==1?1.25:1;}
    private double equippedRune(Player p,String type){double q=0;for(ItemStack s:p.getInventory().getArmorContents())if(type.equals(rune(s)))q=Math.max(q,quality(s));ItemStack main=p.getInventory().getItemInMainHand();if(type.equals(rune(main)))q=Math.max(q,quality(main));return q;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void incoming(EntityDamageEvent e){if(!(e.getEntity() instanceof Player p))return;if(r.getJob(p)==JobType.RANGER&&e.getCause()==EntityDamageEvent.DamageCause.FALL)e.setDamage(e.getDamage()*.5);
        double reduce=(r.hasCharm(p,"charm_starlight_aegis")?.10:0)+r.cfg("runes.BASTION.reduction",.06)*equippedRune(p,"BASTION");e.setDamage(e.getDamage()*(1-Math.min(.8,reduce))*(buff(p,"sanctuary")?.45:1));
        if(buff(p,"invincible")){e.setCancelled(true);return;}
        if(e instanceof EntityDamageByEntityEvent hit){Entity attacker=hit.getDamager() instanceof Projectile proj&&proj.getShooter() instanceof Entity source?source:hit.getDamager();
            if(hostile(p,attacker)&&ThreadLocalRandom.current().nextDouble()<r.cfg("runes.GALE.evasion",.05)*equippedRune(p,"GALE")){e.setCancelled(true);return;}
            if(buff(p,"guard")&&attacker instanceof LivingEntity living&&hostile(p,living)){e.setDamage(e.getDamage()*.5);for(LivingEntity mob:enemies(p,p.getLocation(),3))raw(p,mob,6);particles(p.getLocation(),Particle.END_ROD,20);}}
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true) public void damage(EntityDamageByEntityEvent e){Player p=null;ItemStack weapon=null;
        if(e.getDamager() instanceof Player attacker){p=attacker;weapon=p.getInventory().getItemInMainHand();}else if(e.getDamager() instanceof Projectile proj&&proj.getShooter() instanceof Player shooter){p=shooter;weapon=shots.getOrDefault(proj.getUniqueId(),shooter.getInventory().getItemInMainHand());}
        if(p==null||!hostile(p,e.getEntity())||damageDepth>0)return;LivingEntity enemy=(LivingEntity)e.getEntity();JobType j=r.getJob(p);
        boolean signature=j!=null&&r.items.isCustomItem(weapon,j.itemId);double dmg=e.getDamage();
        Double scripted=e.getDamager().getPersistentDataContainer().get(new NamespacedKey(r.plugin,"rpg_arrow_damage"),PersistentDataType.DOUBLE);
        if(scripted!=null){int star=weapon==null||!weapon.hasItemMeta()?0:weapon.getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey(r.plugin,"starlight_level"),PersistentDataType.INTEGER,0);double[] bonus={0,1,1.8,2.6,3.6,4.6,5.6,7};dmg=scripted*skillScale(p)+bonus[Math.clamp(star,0,7)];}
        else if(signature)dmg+=switch(r.getSignatures().evolution(p)){case 3->5;case 2->3;default->1;};
        if(critical(p,enemy,signature))dmg*=1.45;if(r.hasCharm(p,"charm_crimson_claw")&&boss(enemy))dmg*=1.12;
        dmg*=1+r.getWorkshop().feastEffect(p,"VALOR","damage",.15);if(System.currentTimeMillis()<vulnerable.getOrDefault(enemy.getUniqueId(),0L))dmg*=1.25;e.setDamage(dmg);
        alliedFire(p,enemy);
        procRune(p,enemy,weapon);
    }
    private void procRune(Player p,LivingEntity enemy,ItemStack weapon){String type=rune(weapon);if(type==null||System.currentTimeMillis()<runeReady.getOrDefault(p.getUniqueId(),0L))return;double q=quality(weapon);double chance=r.cfg("runes."+type+".proc-chance",type.equals("THUNDER")?.15:.20);if(ThreadLocalRandom.current().nextDouble()>=chance)return;
        long cooldown=(long)(1000*r.cfg("runes."+type+".cooldown-seconds",type.equals("FIRE")?2:3));
        switch(type){case "FIRE"->{runeReady.put(p.getUniqueId(),System.currentTimeMillis()+cooldown);long before=trainingSequence(enemy);raw(p,enemy,r.cfg("runes.FIRE.damage",4)*q);if(!r.isTrainingDummy(enemy)||trainingSequence(enemy)!=before)fire(p,enemy,(int)(20*r.cfg("runes.FIRE.duration-seconds",3)*q));}
            case "FROST"->{runeReady.put(p.getUniqueId(),System.currentTimeMillis()+cooldown);raw(p,enemy,r.cfg("runes.FROST.damage",3)*q);potion(enemy,PotionEffectType.SLOWNESS,(int)(20*r.cfg("runes.FROST.duration-seconds",2)*q),1);freeze(enemy,(int)(20*r.cfg("runes.FROST.freeze-seconds",.5)*q));}
            case "THUNDER"->{runeReady.put(p.getUniqueId(),System.currentTimeMillis()+cooldown);raw(p,enemy,r.cfg("runes.THUNDER.damage",3)*q);int n=0;for(LivingEntity mob:enemies(p,enemy.getLocation(),r.cfg("runes.THUNDER.radius",5))){if(mob.equals(enemy))continue;raw(p,mob,r.cfg("runes.THUNDER.damage",3)*q);particles(mob.getLocation(),Particle.ELECTRIC_SPARK,12);if(++n==2)break;}}}
    }
    private void mark(Player p,LivingEntity enemy,double initial){Mark mark=new Mark(p.getUniqueId(),epochs.getOrDefault(p.getUniqueId(),0L),initial);marks.computeIfAbsent(enemy.getUniqueId(),id->new ArrayList<>()).add(mark);
        later(p,100,()->{List<Mark> active=marks.get(enemy.getUniqueId());if(active==null||!active.remove(mark))return;if(active.isEmpty())marks.remove(enemy.getUniqueId());if(enemy.isValid())raw(p,enemy,mark.damage*.40);});}
    @EventHandler(ignoreCancelled=true) public void shoot(EntityShootBowEvent e){if(!(e.getEntity() instanceof Player p)||!(e.getProjectile() instanceof AbstractArrow a))return;ItemStack bow=e.getBow();if(bow!=null){shots.put(a.getUniqueId(),bow.clone());Bukkit.getScheduler().runTaskLater(r.plugin,()->shots.remove(a.getUniqueId()),1200);}
        if(r.getJob(p)==JobType.RANGER&&r.items.isCustomItem(bow,"weapon_ranger_bow")){int n=arrows.merge(p.getUniqueId(),1,Integer::sum);if(n%3==0){LivingEntity enemy=target(p,24);Arrow extra=p.launchProjectile(Arrow.class,a.getVelocity());extra.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);extra.setDamage(4);extra.setPersistent(false);extra.getPersistentDataContainer().set(new NamespacedKey(r.plugin,"rpg_arrow_damage"),PersistentDataType.DOUBLE,r.cfg("rpg.skills.RANGER.homing-damage",4));effect(p,extra);shots.put(extra.getUniqueId(),bow.clone());repeat(p,20,1,i->{if(extra.isValid()&&enemy!=null&&!enemy.isDead())extra.setVelocity(enemy.getEyeLocation().toVector().subtract(extra.getLocation().toVector()).normalize().multiply(2));},()->{if(extra.isValid())extra.remove();shots.remove(extra.getUniqueId());effects.remove(extra);});}}
    }
    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true) public void projectileHit(ProjectileHitEvent e){Projectile projectile=e.getEntity();if(projectile.getPersistentDataContainer().has(new NamespacedKey(r.plugin,"rpg_arrow_damage"),PersistentDataType.DOUBLE)&&e.getHitEntity() instanceof LivingEntity target)target.setNoDamageTicks(0);
        if(e.getHitBlock()!=null||!(projectile instanceof AbstractArrow arrow)||arrow.getPierceLevel()==0)Bukkit.getScheduler().runTask(r.plugin,()->shots.remove(projectile.getUniqueId()));}
    private boolean usableBlock(Block b){return b!=null&&(b.getState() instanceof Container||b.getType().isInteractable());}
    @EventHandler(priority=EventPriority.LOWEST) public void npcInteract(PlayerInteractEntityEvent e){if(e.getRightClicked().getPersistentDataContainer().has(new NamespacedKey(r.plugin,"hub_npc_role"),PersistentDataType.STRING))npcSuppression.put(e.getPlayer().getUniqueId(),System.currentTimeMillis()+500);}
    @EventHandler(priority=EventPriority.LOWEST) public void npcInteractAt(PlayerInteractAtEntityEvent e){npcInteract(e);}
    @EventHandler(priority=EventPriority.LOW) public void interact(PlayerInteractEvent e){if(e.getHand()!=EquipmentSlot.HAND)return;Player p=e.getPlayer();if(System.currentTimeMillis()<npcSuppression.getOrDefault(p.getUniqueId(),0L)||!r.holdingSignature(p)||!valid(p,r.getJob(p)))return;JobType j=r.getJob(p);Action a=e.getAction();boolean right=a==Action.RIGHT_CLICK_AIR||a==Action.RIGHT_CLICK_BLOCK;boolean left=a==Action.LEFT_CLICK_AIR||a==Action.LEFT_CLICK_BLOCK;
        if(right&&usableBlock(e.getClickedBlock()))return;
        if(j==JobType.FARMER&&right&&e.getClickedBlock()!=null&&e.getClickedBlock().getBlockData() instanceof Ageable crop&&crop.getAge()==crop.getMaximumAge()){
            e.setCancelled(true);new RpgGatheringListenerAccess(r).harvest(p,e.getClickedBlock());return;}
        if(j==JobType.ANGLER&&right&&!p.isSneaking())return;
        if(j==JobType.RANGER&&right&&!p.isSneaking())return;
        if(right&&p.isSneaking()){
            if(j==JobType.ANGLER&&p.getFishHook()!=null)return;e.setCancelled(true);if(r.getLevel(p)<10||r.getAwakening(p).isEmpty()){p.sendActionBar(TextUtil.mm("<red>Lv.10 초월 각성이 필요합니다."));return;}ultimate(p,j);return;}
        if(j==JobType.ARCANIST&&left){e.setCancelled(true);if(ready(p,"bolt",.6))bolt(p);return;}
        if((j==JobType.RANGER&&left&&p.isSneaking())||(j==JobType.ANGLER&&left)||(j!=JobType.RANGER&&j!=JobType.ANGLER&&right)){
            e.setCancelled(true);primary(p,j);}
    }
    @EventHandler public void swing(PlayerAnimationEvent e){Player p=e.getPlayer();JobType job=r.getJob(p);if(!valid(p,job)||!r.holdingSignature(p)||System.currentTimeMillis()<npcSuppression.getOrDefault(p.getUniqueId(),0L))return;
        if(job==JobType.ANGLER&&target(p,16)!=null)primary(p,job);else if(job==JobType.RANGER&&p.isSneaking()&&target(p,20)!=null)primary(p,job);}
    private void bolt(Player p){Location start=p.getEyeLocation();Vector dir=start.getDirection();Set<UUID> hit=new HashSet<>();boolean[] blocked={false};repeat(p,20,1,n->{if(blocked[0])return;Location at=start.clone().add(dir.clone().multiply(n+1));particles(at,Particle.END_ROD,2);if(at.getBlock().getType().isSolid()){blocked[0]=true;return;}for(LivingEntity e:enemies(p,at,.8))if(hit.add(e.getUniqueId()))hit(p,e,6);},null);}
    private double primaryCd(JobType j){return switch(j){case SHADOW,FARMER->7;case MINER,ANGLER->9;case ARCANIST->10;default->8;};}
    private void primary(Player p,JobType j){if(!ready(p,"primary",r.cfg("rpg.skills."+j+".primary-cooldown",primaryCd(j))))return;p.playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.8f,1.2f);
        Location at=p.getLocation();switch(j){
            case PALADIN->{grant(p,"guard",1.5);dash(p,1.6);repeat(p,5,2,n->area(p,p.getLocation(),2.2,pd(p,12)/5.0,e->push(e,new Vector(0,.6,0))),null);for(LivingEntity e:enemies(p,at,6))if(e instanceof Mob m)m.setTarget(p);}
            case SHADOW->{LivingEntity e=target(p,14);if(e!=null){Location behind=e.getLocation().subtract(e.getLocation().getDirection().setY(0).normalize().multiply(1.3));if(safe(behind)){behind.setDirection(e.getLocation().toVector().subtract(behind.toVector()));p.teleport(behind);hit(p,e,pd(p,14));if(e.isDead())refundPrimary(p);}}}
            case RANGER->{p.setVelocity(direction(p).multiply(-1).setY(.8));for(int n=-2;n<=2;n++)arrow(p,rotate(direction(p),n*.13),pd(p,4),true);}
            case ARCANIST->{Location point=aim(p,12);repeat(p,12,2,n->{particles(point,Particle.PORTAL,10);for(LivingEntity e:enemies(p,point,5.5))push(e,point.toVector().subtract(e.getLocation().toVector()).multiply(.22));if(n==11)area(p,point,5.5,pd(p,10),e->{});},null);}
            case MINER->{potion(p,PotionEffectType.HASTE,160,2);area(p,at.clone().add(direction(p).multiply(2)),5,pd(p,10),e->vulnerable.put(e.getUniqueId(),System.currentTimeMillis()+6000));r.damageObelisks(p,pd(p,10)*skillScale(p));}
            case BLACKSMITH->{area(p,at.clone().add(direction(p).multiply(2)),3,pd(p,12),e->{fire(p,e,60);push(e,direction(p).multiply(.9).setY(.3));});allies(p,6,a->{for(ItemStack s:a.getInventory().getArmorContents())repair(s,10);repair(a.getInventory().getItemInMainHand(),10);repair(a.getInventory().getItemInOffHand(),10);});}
            case FARMER->{boolean hitAny=false;for(LivingEntity e:enemies(p,at,5)){Vector delta=e.getLocation().toVector().subtract(at.toVector()).normalize();if(direction(p).dot(delta)>.2){hit(p,e,pd(p,10));hitAny=true;}}if(hitAny)fruit(p,at.clone().add(direction(p).multiply(2)));}
            case ANGLER->{LivingEntity e=target(p,16);if(e!=null){hit(p,e,pd(p,12));push(e,p.getLocation().toVector().subtract(e.getLocation().toVector()).normalize().multiply(1.8).setY(.3));potion(e,PotionEffectType.SLOWNESS,60,1);particles(e.getLocation(),Particle.SPLASH,25);}}
            case CARPENTER->{area(p,at.clone().add(direction(p).multiply(2)),4,pd(p,12),e->potion(e,PotionEffectType.SLOWNESS,60,1));potion(p,PotionEffectType.HASTE,160,1);}
            case CHEF->{Location point=aim(p,12);projectileVisual(p,point,Particle.FLAME);area(p,point,4,pd(p,8),e->{fire(p,e,80);potion(e,PotionEffectType.WEAKNESS,120,0);});}
        }}
    private double ultimateCd(JobType j,boolean a){return switch(j){case PALADIN->a?35:28;case SHADOW->a?30:26;case RANGER->a?30:28;case ARCANIST->a?32:45;case MINER->a?35:30;case BLACKSMITH->a?40:32;case FARMER->a?40:28;case ANGLER->a?45:30;case CARPENTER->a?40:35;case CHEF->a?38:30;};}
    private void ultimate(Player p,JobType j){boolean a=r.getAwakening(p).equals("A");Location at=p.getLocation();Location proposed=aim(p,14);
        if(j==JobType.CARPENTER&&!a||j==JobType.CHEF&&a){proposed=floorPoint(p,proposed);if(proposed==null){p.sendActionBar(TextUtil.mm("<red>앞쪽에 설치 가능한 바닥이 필요합니다."));return;}}
        final Location point=proposed;if(!ready(p,"ultimate",r.cfg("rpg.skills."+j+"."+(a?"A":"B")+"-cooldown",ultimateCd(j,a))))return;
        p.playSound(at,Sound.ITEM_TOTEM_USE,.8f,1.5f);switch(j){
            case PALADIN->{if(a){Location origin=at.clone();repeat(p,8,20,n->{circle(origin,7,Particle.END_ROD);for(Player ally:r.allies(p,1000000))if(ally.getLocation().distanceSquared(origin)<=Math.pow(partyRadius(p,7),2)){grant(ally,"sanctuary",1.2);heal(ally,ud(p,"heal-per-second",2));}},null);}else{dash(p,1.2);p.setVelocity(p.getVelocity().setY(.7));later(p,12,()->{if(valid(p,j)){Location center=p.getLocation();area(p,center,7,ud(p,"impact-damage",18),e->freeze(e,50));zone(p,center,7,5,ud(p,"tick-damage",2),Particle.FLAME,e->{});}});}}
            case SHADOW->{if(a){grant(p,"assassin_mark",2.5);potion(p,PotionEffectType.INVISIBILITY,50,0);potion(p,PotionEffectType.SPEED,50,2);}else{grant(p,"invincible",1.2);repeat(p,6,4,n->{List<LivingEntity> mobs=enemies(p,p.getLocation(),7);if(!mobs.isEmpty()){LivingEntity e=mobs.get(n%mobs.size());Location dest=e.getLocation().clone().add(0,0,1);if(safe(dest))p.teleport(dest);hit(p,e,ud(p,"hit-damage",6));particles(e.getLocation(),Particle.SWEEP_ATTACK,2);}},null);}}
            case RANGER->{if(a)repeat(p,5,20,n->{for(int count=0;count<5;count++){double angle=ThreadLocalRandom.current().nextDouble(Math.PI*2),radius=ThreadLocalRandom.current().nextDouble(7);fallingArrow(p,point.clone().add(Math.cos(angle)*radius,12,Math.sin(angle)*radius));}area(p,point,7,ud(p,"tick-damage",4),e->{});},null);else{for(LivingEntity e:enemies(p,point,6))freeze(e,70);repeat(p,20,4,n->{LivingEntity e=target(p,24);if(e!=null){projectileVisual(p,e.getEyeLocation(),Particle.ELECTRIC_SPARK);hit(p,e,ud(p,"tick-damage",2));}},null);}}
            case ARCANIST->{if(a)repeat(p,3,10,n->{beam(p,18,ud(p,"wave-damage",10),n==2);},null);else{allies(p,12,ally->{heal(ally,maxHp(ally)*ud(p,"heal-ratio",.5));grant(ally,"miracle_shield",20);shield(ally,12);particles(ally.getLocation(),Particle.HEART,15);});r.reviveOne(p);}}
            case MINER->{if(a){potion(p,PotionEffectType.LUCK,300,2);reveal(p,20);}else{grant(p,"excavation",12);projectileVisual(p,point,Particle.FLAME);area(p,point,5,ud(p,"impact-damage",18),e->freeze(e,40));}}
            case BLACKSMITH->{if(a){grant(p,"masterwork",20);allies(p,10,ally->{potion(ally,PotionEffectType.RESISTANCE,400,1);potion(ally,PotionEffectType.FIRE_RESISTANCE,400,0);});}else{BlockDisplay anvil=point.getWorld().spawn(point.clone().add(0,8,0),BlockDisplay.class,d->{d.setBlock(Material.ANVIL.createBlockData());d.setPersistent(false);});effect(p,anvil);repeat(p,8,1,n->anvil.teleport(point.clone().add(0,7-n,0)),()->{anvil.remove();effects.remove(anvil);if(valid(p,j)){area(p,point,6,ud(p,"impact-damage",24),e->fire(p,e,80));allies(p,10,ally->grant(ally,"allied_fire",15));}});}}
            case FARMER->{if(a){grow(p,at,9);allies(p,10,ally->{potion(ally,PotionEffectType.REGENERATION,200,1);ally.setFoodLevel(20);ally.setSaturation(10);});}else zone(p,point,7,6,ud(p,"tick-damage",2),Particle.HAPPY_VILLAGER,e->{freeze(e,25);poison(p,e,40,0);});}
            case ANGLER->{if(a){grant(p,"treasure",20);potion(p,PotionEffectType.LUCK,400,4);allies(p,10,ally->grant(ally,"treasure_crit",20));}else{repeat(p,5,20,n->{area(p,point,7,ud(p,"tick-damage",3),e->push(e,point.toVector().subtract(e.getLocation().toVector()).multiply(.25)));circle(point,7,Particle.SPLASH);},()->{if(valid(p,j))area(p,point,7,ud(p,"impact-damage",8),e->push(e,new Vector(0,1.2,0)));});}}
            case CARPENTER->{if(a){grant(p,"architect",25);potion(p,PotionEffectType.JUMP_BOOST,500,1);potion(p,PotionEffectType.SPEED,500,0);}else turret(p,point);}
            case CHEF->{if(a)cauldron(p,point);else{area(p,point,8,ud(p,"impact-damage",12),e->vulnerable.put(e.getUniqueId(),System.currentTimeMillis()+12000));allies(p,8,ally->{potion(ally,PotionEffectType.STRENGTH,240,1);potion(ally,PotionEffectType.SPEED,240,1);});}}
        }}
    private boolean safe(Location l){return !l.getBlock().getType().isSolid()&&!l.clone().add(0,1,0).getBlock().getType().isSolid()&&l.clone().add(0,-1,0).getBlock().getType().isSolid();}
    private Location floorPoint(Player p,Location aimed){Location near=aimed.clone().subtract(direction(p).multiply(.8));
        for(Location column:List.of(near,p.getLocation().add(direction(p).multiply(2))))for(int y=Math.min(p.getWorld().getMaxHeight()-3,p.getLocation().getBlockY()+3);y>=Math.max(p.getWorld().getMinHeight(),p.getLocation().getBlockY()-6);y--){
            Block floor=p.getWorld().getBlockAt(column.getBlockX(),y,column.getBlockZ());Location spot=floor.getLocation().add(.5,1,.5);
            if(floor.getType().isSolid()&&floor.getRelative(0,1,0).isPassable()&&floor.getRelative(0,2,0).isPassable()&&spot.distanceSquared(p.getLocation())<=196)return spot;}
        return null;}
    private void fallingArrow(Player p,Location upper){ItemDisplay arrow=upper.getWorld().spawn(upper,ItemDisplay.class,e->{e.setItemStack(new ItemStack(Material.ARROW));e.setRotation(0,90);e.setPersistent(false);});effect(p,arrow);
        repeat(p,8,1,n->{Location at=upper.clone().subtract(0,n*1.5,0);arrow.teleport(at);particles(at,Particle.END_ROD,2);},()->{arrow.remove();effects.remove(arrow);particles(upper.clone().subtract(0,12,0),Particle.FIREWORK,7);});}
    private void dash(Player p,double amount){p.setVelocity(direction(p).multiply(amount).setY(.25));}
    private Vector rotate(Vector v,double angle){return v.clone().rotateAroundY(angle);}
    private void arrow(Player p,Vector dir,double damage,boolean pierce){Arrow arrow=p.launchProjectile(Arrow.class,dir.normalize().multiply(2.4));arrow.setDamage(damage);arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);arrow.setPierceLevel(pierce?3:0);arrow.setPersistent(false);arrow.getPersistentDataContainer().set(new NamespacedKey(r.plugin,"rpg_arrow_damage"),PersistentDataType.DOUBLE,damage);effect(p,arrow);shots.put(arrow.getUniqueId(),p.getInventory().getItemInMainHand().clone());
        Bukkit.getScheduler().runTaskLater(r.plugin,()->{if(arrow.isValid())arrow.remove();shots.remove(arrow.getUniqueId());effects.remove(arrow);},100);}
    private void zone(Player p,Location at,double radius,int seconds,double dmg,Particle particle,Consumer<LivingEntity> effect){repeat(p,seconds,20,n->{circle(at,radius,particle);area(p,at,radius,dmg,effect);},null);}
    private void beam(Player p,int length,double damage,boolean stun){Location start=p.getEyeLocation();Vector direction=start.getDirection();Set<UUID> hit=new HashSet<>();for(int n=1;n<=length;n++){Location at=start.clone().add(direction.clone().multiply(n));if(at.getBlock().getType().isSolid())break;particles(at,Particle.WITCH,4);for(LivingEntity e:enemies(p,at,1.5))if(hit.add(e.getUniqueId())){hit(p,e,damage);if(stun)freeze(e,40);}}}
    private void projectileVisual(Player p,Location end,Particle particle){Location start=p.getEyeLocation();Vector v=end.toVector().subtract(start.toVector());for(int n=0;n<16;n++)particles(start.clone().add(v.clone().multiply(n/16.0)),particle,2);}
    private boolean clearLine(Location from,Location to){Vector vector=to.toVector().subtract(from.toVector());double length=vector.length();return length<.1||from.getWorld().rayTraceBlocks(from,vector.normalize(),Math.max(.01,length-.1),FluidCollisionMode.NEVER,true)==null;}
    private void repair(ItemStack item,int amount){if(item!=null&&item.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d){d.setDamage(Math.max(0,d.getDamage()-amount));item.setItemMeta(d);}}
    private void fruit(Player p,Location at){ItemDisplay fruit=at.getWorld().spawn(at,ItemDisplay.class,e->{e.setItemStack(new ItemStack(Material.SWEET_BERRIES));e.setPersistent(false);});effect(p,fruit);boolean[] used={false};repeat(p,100,2,n->{if(used[0])return;for(Player ally:r.allies(p,20))if(ally.getLocation().distanceSquared(at)<2.25){heal(ally,4);used[0]=true;fruit.remove();particles(at,Particle.HEART,5);break;}},()->{fruit.remove();effects.remove(fruit);});}
    private void grow(Player p,Location at,int radius){for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)for(int y=-2;y<=2;y++){Block b=at.getBlock().getRelative(x,y,z);if(x*x+z*z>radius*radius)continue;
        if(b.getWorld().getName().equals("yuumi_hub")&&!r.plots.canBuildInHub(p,b.getLocation()))continue;if(Tag.CROPS.isTagged(b.getType())&&b.getBlockData() instanceof Ageable age){age.setAge(age.getMaximumAge());b.setBlockData(age,false);}}}
    private void reveal(Player p,int radius){Location at=p.getLocation();List<Location> found=new ArrayList<>();for(int x=-radius;x<=radius;x++)for(int y=-radius;y<=radius;y++)for(int z=-radius;z<=radius;z++){
        if(x*x+y*y+z*z>radius*radius)continue;Block b=at.getBlock().getRelative(x,y,z);Material m=b.getType();if(m==Material.DIAMOND_ORE||m==Material.DEEPSLATE_DIAMOND_ORE||m==Material.EMERALD_ORE||m==Material.DEEPSLATE_EMERALD_ORE||m==Material.ANCIENT_DEBRIS)found.add(b.getLocation().add(.5,.5,.5));}
        repeat(p,15,20,n->{for(Location l:found)p.spawnParticle(Particle.END_ROD,l,2,.2,.2,.2,.01);},null);}
    private void turret(Player p,Location at){BlockDisplay stand=at.getWorld().spawn(at,BlockDisplay.class,e->{e.setBlock(Material.OAK_FENCE.createBlockData());e.setPersistent(false);});
        ItemDisplay bow=at.getWorld().spawn(at.clone().add(0,1.2,0),ItemDisplay.class,e->{e.setItemStack(new ItemStack(Material.CROSSBOW));e.setPersistent(false);});effect(p,stand);effect(p,bow);
        repeat(p,18,20,n->{List<LivingEntity> nearby=enemies(p,at,14);nearby.removeIf(e->!clearLine(bow.getLocation(),e.getEyeLocation()));nearby.sort(Comparator.comparingDouble(e->e.getLocation().distanceSquared(at)));if(!nearby.isEmpty()){LivingEntity e=nearby.getFirst();Location loc=bow.getLocation();loc.setDirection(e.getEyeLocation().toVector().subtract(loc.toVector()));bow.teleport(loc);projectileVisual(p,e.getLocation(),Particle.CRIT);hit(p,e,ud(p,"shot-damage",4));}},()->{stand.remove();bow.remove();effects.remove(stand);effects.remove(bow);});}
    private void cauldron(Player p,Location at){ItemDisplay pot=at.getWorld().spawn(at,ItemDisplay.class,e->{e.setItemStack(r.prop("prop_feast_cauldron",Material.CAULDRON));e.setPersistent(false);});effect(p,pot);
        repeat(p,20,20,n->{circle(at,8,Particle.HEART);for(Player ally:r.allies(p,1000000))if(ally.getLocation().distanceSquared(at)<=Math.pow(partyRadius(p,8),2)){heal(ally,2);grant(ally,"cauldron",1.3);}},()->{pot.remove();effects.remove(pot);});}
    @EventHandler public void quit(PlayerQuitEvent e){clearTemporary(e.getPlayer());npcSuppression.remove(e.getPlayer().getUniqueId());removeStats(e.getPlayer());r.save.run();}
    @EventHandler public void die(PlayerDeathEvent e){clearTemporary(e.getEntity());}
    /** Keeps harvest access package-local while the listener itself is registered once. */
    private static final class RpgGatheringListenerAccess {private final RpgJobManager r;RpgGatheringListenerAccess(RpgJobManager r){this.r=r;}void harvest(Player p,Block b){r.harvest(p,b);}}
}
