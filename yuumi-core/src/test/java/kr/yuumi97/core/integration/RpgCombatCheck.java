package kr.yuumi97.core.integration;

import kr.yuumi97.core.YuumiPlugin;
import kr.yuumi97.core.rpg.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import static kr.yuumi97.core.rpg.CombatDamageService.DamageKind.*;

/** Actual Paper damage and movement regressions, restricted to the disposable server. */
public final class RpgCombatCheck {
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    public static CompletableFuture<Map<String,Object>> run(YuumiPlugin core,Player player) {
        Path root=core.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        if(!root.equals(Path.of("E:/MinGem/yuumi-core/build/server-integration").toAbsolutePath().normalize())||Files.isSymbolicLink(root))
            return CompletableFuture.failedFuture(new IllegalStateException("Disposable server-integration path required"));
        if(!RUNNING.compareAndSet(false,true))return CompletableFuture.failedFuture(new IllegalStateException("Combat check already running"));
        CompletableFuture<Map<String,Object>> result=new CompletableFuture<>();
        Runnable start=()->{Check check=null;try{check=new Check(core,player,result);check.start();}catch(Throwable e){if(check!=null)check.finish(e);else{RUNNING.set(false);result.completeExceptionally(e);}}};
        if(Bukkit.isPrimaryThread())start.run();else Bukkit.getScheduler().runTask(core,start);
        return result;
    }
    private record Step(String name,int ticks,Runnable action){}
    private record Hit(UUID target,CombatDamageService.DamageKind kind,EntityDamageEvent.DamageCause cause,double input,double actual){}
    private static final class Check implements Listener {
        final YuumiPlugin core;final RpgJobManager r;final Player p;final CompletableFuture<Map<String,Object>> result;
        final List<Step> steps=new ArrayList<>();final List<String> passed=new ArrayList<>();final List<Hit> hits=new ArrayList<>();
        final Map<Block,BlockState> blocks=new LinkedHashMap<>();final List<LivingEntity> mobs=new ArrayList<>();final Map<EntityDamageEvent,Double> inputs=new WeakHashMap<>();
        final Map<String,Object> profile=new LinkedHashMap<>();final Map<String,Object> runtime=new HashMap<>();
        final Location original;final GameMode mode;final ItemStack[] inventory;final ItemStack cursor;final int held,food,fire;
        final float saturation,exhaustion;final double health,absorption;final boolean sneaking;final List<PotionEffect> effects;
        final World world;final int x,y,z;int next;boolean cleaning,cancelDamage;UUID cancelTarget;LivingEntity target,other;long firstRefund;double echoExpected,echoStored,bleedStart;Location dashOrigin;AuraProof auraProof;
        Check(YuumiPlugin core,Player p,CompletableFuture<Map<String,Object>> result)throws Exception {
            this.core=core;this.r=core.rpg();this.p=p;this.result=result;
            original=p.getLocation().clone();mode=p.getGameMode();inventory=copy(p.getInventory().getContents());cursor=p.getItemOnCursor().clone();held=p.getInventory().getHeldItemSlot();
            food=p.getFoodLevel();saturation=p.getSaturation();exhaustion=p.getExhaustion();health=p.getHealth();absorption=p.getAbsorptionAmount();fire=p.getFireTicks();sneaking=p.isSneaking();effects=new ArrayList<>(p.getActivePotionEffects());
            ConfigurationSection section=core.progression().getConfigurationSection(r.root(p));if(section!=null)for(var e:section.getValues(true).entrySet())if(!(e.getValue() instanceof ConfigurationSection))profile.put(e.getKey(),cloneValue(e.getValue()));
            for(String name:List.of("buffs","shields","resource","normalCount","lastCombat","lastGain","inputs","npcUntil","crisisReady","killReady","dodgeReady")){Object before=map(r.combatSkills(),name).get(p.getUniqueId());runtime.put(name,before instanceof Map<?,?> values?new HashMap<>(values):before);}
            world=Bukkit.getWorld("world");if(world==null)throw new IllegalStateException("Normal wilderness world required");x=world.getSpawnLocation().getBlockX()+56;z=world.getSpawnLocation().getBlockZ()+56;y=Math.min(world.getMaxHeight()-12,world.getHighestBlockYAt(x,z)+24);
        }
        void start(){Bukkit.getPluginManager().registerEvents(this,core);p.closeInventory();p.setItemOnCursor(null);p.setGameMode(GameMode.SURVIVAL);p.setSneaking(false);p.setFoodLevel(0);p.setFireTicks(0);p.getActivePotionEffects().forEach(e->p.removePotionEffect(e.getType()));
            for(int dx=-4;dx<=16;dx++)for(int dz=-4;dz<=14;dz++){put(at(dx,0,dz),Material.STONE);for(int dy=1;dy<=5;dy++)put(at(dx,dy,dz),Material.AIR);}
            arcane();berserker();shadow();for(CombatClass type:List.of(CombatClass.PALADIN,CombatClass.SHADOW))for(String obstacle:List.of("open","wall","ceiling","gap"))dash(type,obstacle);
            step("cleanup",2,()->finish(null));advance();
        }
        void step(String name,int ticks,Runnable action){steps.add(new Step(name,ticks,action));}
        void advance(){if(cleaning)return;if(next>=steps.size()){finish(null);return;}Step step=steps.get(next++);Bukkit.getScheduler().runTaskLater(core,()->{try{if(!p.isOnline())throw new IllegalStateException("Fixture player disconnected");step.action.run();if(!cleaning)advance();}catch(Throwable e){finish(new AssertionError(step.name+": "+e,e));}},Math.max(1,step.ticks));}
        void check(boolean condition,String label){if(!condition)throw new AssertionError(label);passed.add(label);}
        Location loc(double dx,double dy,double dz){return new Location(world,x+.5+dx,y+dy,z+.5+dz);}
        Block at(int dx,int dy,int dz){return world.getBlockAt(x+dx,y+dy,z+dz);}
        void put(Block b,Material material){blocks.computeIfAbsent(b,Block::getState);b.setType(material,false);}
        void invoke(String name,Class<?>[] types,Object... args){try{Method method=r.combatSkills().getClass().getDeclaredMethod(name,types);method.setAccessible(true);method.invoke(r.combatSkills(),args);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
        void primary(){invoke("primary",new Class<?>[]{Player.class},p);}void finisher(){invoke("finisher",new Class<?>[]{Player.class},p);}
        void profile(CombatClass type,String branch){r.combatSkills().clear(p,RpgDomain.COMBAT);r.lifeSkills().clear(p,RpgDomain.LIFE);mobs.forEach(Entity::remove);mobs.clear();hits.clear();cancelDamage=false;cancelTarget=null;
            for(String name:List.of("buffs","shields","inputs","lastGain"))mapUnchecked(r.combatSkills(),name).remove(p.getUniqueId());
            core.progression().set(r.root(p)+".active_combat",type.name());core.progression().set(r.root(p)+".active_life","MINER");core.progression().set(r.root(p)+".bookmarks",null);core.progression().set(r.root(p)+".feast",null);String path=r.path(p,RpgDomain.COMBAT);core.progression().set(path+".level",10);core.progression().set(path+".awakening",branch);core.progression().set(path+".perks",null);core.progression().set(path+".cooldowns",null);
            p.getInventory().clear();p.getInventory().setHeldItemSlot(0);p.getInventory().setItemInMainHand(core.items().create(type.itemId));p.getActivePotionEffects().forEach(e->p.removePotionEffect(e.getType()));p.setAbsorptionAmount(0);p.setFireTicks(0);p.setVelocity(new Vector());p.teleport(loc(0,1,0));r.combatSkills().refreshStats(p);p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());}
        LivingEntity mob(double dx,double dz){Husk e=world.spawn(loc(dx,1,dz),Husk.class,h->{h.setAI(false);h.setSilent(true);h.setPersistent(false);h.setRemoveWhenFarAway(false);h.setGravity(false);h.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);h.getAttribute(Attribute.ARMOR).setBaseValue(0);h.setHealth(1000);});mobs.add(e);return e;}
        void look(Location target){Location at=p.getLocation();at.setDirection(target.toVector().subtract(p.getEyeLocation().toVector()));p.teleport(at);}
        List<Hit> forTarget(LivingEntity target){return hits.stream().filter(h->h.target.equals(target.getUniqueId())).toList();}
        void arcane(){
            step("Arcanist multi-target cast",2,()->{profile(CombatClass.ARCANIST,"A");target=mob(4,0);other=mob(7,0);look(loc(12,0,0));for(LivingEntity e:List.of(target,other))invoke("debuff",new Class<?>[]{LivingEntity.class,String.class,double.class,double.class},e,"gravity",0d,5d);r.combatSkills().commit(p,RpgDomain.COMBAT,"primary",20);finisher();});
            step("Arcanist first wave refund",3,()->{long remaining=r.combatSkills().remaining(p,RpgDomain.COMBAT,"primary");check(remaining>9000&&remaining<10500,"Arcanist A refunds exactly half once despite two gravity targets");firstRefund=core.progression().getLong(r.path(p,RpgDomain.COMBAT)+".cooldowns.primary");});
            step("Arcanist subsequent waves",24,()->{check(forTarget(target).stream().filter(h->h.kind==FINISHER).count()==3&&forTarget(other).stream().filter(h->h.kind==FINISHER).count()==3,"Arcanist A all three waves damage both gravity targets");long finalExpiry=core.progression().getLong(r.path(p,RpgDomain.COMBAT)+".cooldowns.primary");check(firstRefund-finalExpiry>=0&&firstRefund-finalExpiry<750,"Arcanist A later targets and waves cannot refund primary again");});
        }
        void berserker(){
            step("Berserker accepted DOT leech",2,()->{profile(CombatClass.BERSERKER,"A");target=mob(3,0);p.setHealth(10);double before=p.getHealth(),max=p.getAttribute(Attribute.MAX_HEALTH).getValue(),rate=.15*(1-before/max);double actual=r.damage().raw(p,target,10,DOT),expected=before+actual*rate;
                check(actual>0&&Math.abs(p.getHealth()-expected)<1e-4,"Berserker approved DOT leech: before="+before+", max="+max+", damage="+actual+", expected="+expected+", actual="+p.getHealth());check(r.combatSkills().resource(p)==0,"Berserker DOT leech does not generate repeated hit rage");
                p.setHealth(before);r.combatSkills().grant(p,"blood_god",8);actual=r.damage().raw(p,target,10,DOT);expected=before+actual*rate*2;check(Math.abs(p.getHealth()-expected)<1e-4,"Blood god DOT leech: expected="+expected+", actual="+p.getHealth()+", max="+max);
                p.setHealth(before);cancelDamage=true;cancelTarget=target.getUniqueId();actual=r.damage().raw(p,target,10,DOT);cancelDamage=false;check(actual==0&&p.getHealth()==before,"Cancelled DOT grants neither accepted damage nor leech");
                r.damage().raw(p,target,2,REPLAY);check(p.getHealth()==before,"Replay damage remains excluded from Berserker leech");hits.clear();bleedStart=p.getHealth();invoke("bleed",new Class<?>[]{Player.class,LivingEntity.class,double.class},p,target,5d);});
            step("Berserker native skill bleed timer",3,()->{check(forTarget(target).stream().anyMatch(h->h.kind==DOT)&&p.getHealth()>bleedStart,"Actual scheduled bleed tick heals Berserker");check(forTarget(target).size()==1,"A bleed tick does not recursively produce new damage");});
        }
        void shadow(){
            step("Shadow all-source sentence",2,()->{profile(CombatClass.SHADOW,"A");target=mob(6,0);other=mob(12,3);look(target.getEyeLocation());finisher();target.setNoDamageTicks(0);target.damage(10);target.setNoDamageTicks(0);target.damage(6,other);r.damage().raw(p,target,4,DOT);r.damage().raw(p,target,2,REPLAY);
                cancelDamage=true;cancelTarget=target.getUniqueId();target.setNoDamageTicks(0);target.damage(9);cancelDamage=false;target.setFireTicks(45);});
            step("Shadow native fire included",30,()->{target.setFireTicks(0);check(forTarget(target).stream().anyMatch(h->h.cause==EntityDamageEvent.DamageCause.FIRE_TICK),"Shadow sentence observes real unattributed native fire ticks");check(forTarget(target).stream().anyMatch(h->h.kind==NATIVE)&&forTarget(target).stream().anyMatch(h->h.cause==EntityDamageEvent.DamageCause.CUSTOM),"Shadow sentence observes mob-source and unattributed native damage");echoExpected=forTarget(target).stream().filter(h->h.kind!=REPLAY).mapToDouble(Hit::actual).sum()*.45;
                try{List<?> entries=(List<?>)map(r.combatSkills(),"echoes").get(target.getUniqueId());if(entries==null||entries.size()!=1)throw new AssertionError("Expected one live sentence: "+entries);echoStored=((Number)field(entries.getFirst(),"damage")).doubleValue();}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}});
            step("Shadow actual 45 percent replay",55,()->{List<Hit> replays=forTarget(target).stream().filter(h->h.kind==REPLAY).toList();String detail="expected="+echoExpected+", storedSum="+echoStored+", expectedSum="+(echoExpected/.45)+", replays="+replays+", allHits="+forTarget(target);check(replays.size()==2,"Sentence emits one final replay alongside deliberate replay: "+detail);double actual=replays.get(1).actual;check(echoExpected>9&&Math.abs(actual-echoExpected)<1e-4,"Sentence 45% approved-source replay: "+detail);
                target.getAttribute(Attribute.ARMOR).setBaseValue(20);target.getAttribute(Attribute.ARMOR_TOUGHNESS).setBaseValue(0);double armored=r.damage().raw(p,target,10,REPLAY);check(armored>0&&armored<10,"Replay retains native armor mitigation: input=10, approved="+armored);target.getAttribute(Attribute.ARMOR).setBaseValue(0);
                cancelDamage=true;cancelTarget=target.getUniqueId();double cancelled=r.damage().raw(p,target,10,REPLAY);cancelDamage=false;check(cancelled==0,"Replay retains external native cancellation");
                if(Bukkit.getPluginManager().isPluginEnabled("AuraSkills")){auraProof=new AuraProof(p,target);auraProof.register(core);auraProof.modify=true;double replay=r.damage().raw(p,target,10,REPLAY),primary=r.damage().raw(p,target,10,PRIMARY);check(Math.abs(replay-7.5)<1e-4,"Replay removes attack amplification while retaining 25% Aura defense: approved="+replay);check(primary>replay,"Primary retains Aura attack amplification: primary="+primary+", replay="+replay);auraProof.cancel=true;check(r.damage().raw(p,target,10,REPLAY)==0,"Replay retains Aura DamageEvent cancellation");HandlerList.unregisterAll(auraProof);auraProof=null;}
            });
        }
        void dash(CombatClass type,String obstacle){
            step(type+" "+obstacle+" dash",2,()->{profile(type,"");for(int dx=-1;dx<=1;dx++){put(at(dx,0,4),Material.STONE);put(at(dx,1,4),Material.AIR);put(at(dx,2,4),Material.AIR);if(obstacle.equals("wall")){put(at(dx,1,4),Material.STONE);put(at(dx,2,4),Material.STONE);}else if(obstacle.equals("ceiling"))put(at(dx,2,4),Material.STONE);else if(obstacle.equals("gap"))put(at(dx,0,4),Material.AIR);}Location origin=loc(0,1,0);origin.setYaw(0);origin.setPitch(0);p.teleport(origin);dashOrigin=origin;
                if(type==CombatClass.PALADIN){target=mob(0,2);mapUnchecked(r.combatSkills(),"resource").put(p.getUniqueId(),5);}primary();});
            step(type+" "+obstacle+" landing",10,()->{double distance=p.getLocation().distance(dashOrigin),expected=type==CombatClass.PALADIN?7:8;
                if(obstacle.equals("open")){check(Math.abs(distance-expected)<.01,type+" open dash travels exactly "+(int)expected+"m");if(type==CombatClass.PALADIN){check(forTarget(target).stream().filter(h->h.kind==PRIMARY).count()==1,"Paladin fixed dash damages a path target exactly once");check(p.getAbsorptionAmount()>24,"Paladin grants full resolve shield after dash completes");}}
                else check(distance>2&&distance<3.6,type+" "+obstacle+" stops before unsafe collision or unsupported ground");
                check(Math.abs(p.getLocation().getY()-dashOrigin.getY())<.01&&p.getLocation().getBlock().isPassable()&&p.getLocation().clone().add(0,1,0).getBlock().isPassable()&&p.getLocation().clone().subtract(0,.05,0).getBlock().getType().isSolid(),type+" "+obstacle+" finishes on supported clear footing");
            });
        }
        @EventHandler(priority=EventPriority.LOWEST) public void cancel(EntityDamageEvent event){if(mobs.stream().anyMatch(e->e.getUniqueId().equals(event.getEntity().getUniqueId())))inputs.put(event,event.getDamage());if(cancelDamage&&event.getEntity().getUniqueId().equals(cancelTarget))event.setCancelled(true);}
        @EventHandler(priority=EventPriority.MONITOR) public void damage(EntityDamageEvent event){if(mobs.stream().noneMatch(e->e.getUniqueId().equals(event.getEntity().getUniqueId())))return;double managed=r.acceptedDamage(event);if(event.isCancelled()&&managed<0)return;LivingEntity entity=(LivingEntity)event.getEntity();double actual=managed>=0?managed:Math.min(event.getFinalDamage(),entity.getHealth()+entity.getAbsorptionAmount());if(actual<=0)return;var kind=r.damage().isSecondaryDamage()?r.damage().currentKind():event instanceof EntityDamageByEntityEvent?NATIVE:DOT;hits.add(new Hit(entity.getUniqueId(),kind,event.getCause(),inputs.getOrDefault(event,event.getDamage()),actual));}
        void finish(Throwable failure){if(cleaning)return;cleaning=true;HandlerList.unregisterAll(this);if(auraProof!=null)HandlerList.unregisterAll(auraProof);r.combatSkills().clear(p,RpgDomain.COMBAT);r.lifeSkills().clear(p,RpgDomain.LIFE);mobs.forEach(Entity::remove);Bukkit.getScheduler().runTaskLater(core,()->restore(failure),3);}
        void restore(Throwable failure){Throwable error=failure;try{r.combatSkills().clear(p,RpgDomain.COMBAT);r.lifeSkills().clear(p,RpgDomain.LIFE);List<BlockState> restore=new ArrayList<>(blocks.values());Collections.reverse(restore);for(BlockState block:restore)block.update(true,false);core.progression().set(r.root(p),null);profile.forEach((key,value)->core.progression().set(r.root(p)+"."+key,cloneValue(value)));
                if(p.isOnline()){p.getInventory().setContents(copy(inventory));p.getInventory().setHeldItemSlot(held);p.setItemOnCursor(cursor);p.setGameMode(mode);p.setSneaking(sneaking);p.getActivePotionEffects().forEach(e->p.removePotionEffect(e.getType()));effects.forEach(p::addPotionEffect);p.teleport(original);p.setVelocity(new Vector());for(var entry:runtime.entrySet()){Map<Object,Object> state=map(r.combatSkills(),entry.getKey());state.remove(p.getUniqueId());if(entry.getValue()!=null)state.put(p.getUniqueId(),entry.getValue());}r.combatSkills().refreshStats(p);p.setHealth(Math.min(health,p.getAttribute(Attribute.MAX_HEALTH).getValue()));p.setAbsorptionAmount(Math.min(absorption,p.getAttribute(Attribute.MAX_ABSORPTION).getValue()));p.setFireTicks(fire);p.setFoodLevel(food);p.setSaturation(saturation);p.setExhaustion(exhaustion);}core.saveDataFile();
            }catch(Throwable cleanup){if(error==null)error=cleanup;else error.addSuppressed(cleanup);}finally{RUNNING.set(false);}if(error!=null)result.completeExceptionally(error);else result.complete(Map.of("success",true,"count",passed.size(),"checks",passed,"restoredBlocks",blocks.size()));}
    }
    private static final class AuraProof implements Listener {
        final Player player;final LivingEntity target;boolean modify,cancel;AuraProof(Player player,LivingEntity target){this.player=player;this.target=target;}
        void register(YuumiPlugin core){try{Class<? extends Event> type=Class.forName("dev.aurelium.auraskills.api.event.damage.DamageEvent",true,Bukkit.getPluginManager().getPlugin("AuraSkills").getClass().getClassLoader()).asSubclass(Event.class);Bukkit.getPluginManager().registerEvent(type,this,EventPriority.LOW,(listener,event)->{try{damage(event);}catch(ReflectiveOperationException e){throw new EventException(e);}},core);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
        @SuppressWarnings({"rawtypes","unchecked"}) void damage(Event event)throws ReflectiveOperationException{Object meta=event.getClass().getMethod("getDamageMeta").invoke(event);Class<?> type=meta.getClass();if(!player.equals(type.getMethod("getAttackerAsPlayer").invoke(meta))||!target.equals(type.getMethod("getTarget").invoke(meta)))return;if(cancel)((Cancellable)event).setCancelled(true);if(modify){ClassLoader loader=type.getClassLoader();Class<?> modifier=Class.forName("dev.aurelium.auraskills.api.damage.DamageModifier",true,loader),operation=Class.forName("dev.aurelium.auraskills.api.damage.DamageModifier$Operation",true,loader);Object multiply=Enum.valueOf((Class)operation,"MULTIPLY");Constructor<?> constructor=modifier.getConstructor(double.class,operation);type.getMethod("addAttackModifier",modifier).invoke(meta,constructor.newInstance(.2,multiply));type.getMethod("addDefenseModifier",modifier).invoke(meta,constructor.newInstance(-.25,multiply));}}
    }
    private static Object field(Object object,String name)throws ReflectiveOperationException{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    @SuppressWarnings("unchecked") private static Map<Object,Object> map(Object object,String name)throws ReflectiveOperationException{return (Map<Object,Object>)field(object,name);}
    private static Map<Object,Object> mapUnchecked(Object object,String name){try{return map(object,name);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static ItemStack[] copy(ItemStack[] values){ItemStack[] out=new ItemStack[values.length];for(int i=0;i<values.length;i++)out[i]=values[i]==null?null:values[i].clone();return out;}
    private static Object cloneValue(Object value){if(value instanceof ItemStack item)return item.clone();if(value instanceof List<?> list)return list.stream().map(RpgCombatCheck::cloneValue).toList();if(value instanceof Map<?,?> map){Map<Object,Object> out=new LinkedHashMap<>();map.forEach((key,item)->out.put(key,cloneValue(item)));return out;}return value;}
}
