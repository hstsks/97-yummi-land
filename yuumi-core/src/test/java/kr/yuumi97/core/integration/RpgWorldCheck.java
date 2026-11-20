package kr.yuumi97.core.integration;

import kr.yuumi97.core.YuumiPlugin;
import kr.yuumi97.core.rpg.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Real Paper fixtures. This companion check can never run against the production server. */
public final class RpgWorldCheck {
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    public static CompletableFuture<Map<String,Object>> run(YuumiPlugin core,Player player) {
        Path root=core.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        if(!root.endsWith(Path.of("yuumi-core","build","server-integration")))
            return CompletableFuture.failedFuture(new IllegalStateException("Disposable server-integration path required"));
        if(!RUNNING.compareAndSet(false,true))return CompletableFuture.failedFuture(new IllegalStateException("RPG world check already running"));
        CompletableFuture<Map<String,Object>> result=new CompletableFuture<>();
        Runnable start=()->{Check check=null;try{check=new Check(core,player,result);check.start();}catch(Throwable e){if(check!=null)check.finish(e);else{RUNNING.set(false);result.completeExceptionally(e);}}};
        if(Bukkit.isPrimaryThread())start.run();else Bukkit.getScheduler().runTask(core,start);
        return result;
    }
    private record Step(String name,int waitTicks,Runnable action){}
    private static final class Check implements Listener {
        final YuumiPlugin core;final RpgJobManager r;final Player p;final CompletableFuture<Map<String,Object>> result;
        final List<Step> steps=new ArrayList<>();final List<String> passed=new ArrayList<>();final Map<Block,BlockState> blocks=new LinkedHashMap<>();
        final Set<UUID> initialEntities=new HashSet<>(),ownedMobs=new HashSet<>();final Map<UUID,Integer> hits=new HashMap<>();
        final Location original;final GameMode mode;final ItemStack[] inventory;final ItemStack cursor;final int held,level,totalXp,food;final float xp,saturation,exhaustion;final double health,absorption;
        final boolean sneaking;final List<PotionEffect> effects;final Map<String,Object> profile=new LinkedHashMap<>();final Map<String,Map<Object,Object>> runtime=new HashMap<>();
        final World world;final int x,z,y;final Set<String> oldPlaced;boolean cancelBreak;int next;boolean cleaning;double rangeBefore;int farmerStar,logsPlain,logsStar;
        final Map<String,Object> originalConfig=new LinkedHashMap<>();LivingEntity fireDummy;int originalFire;List<PotionEffect> originalDummyEffects;String originalFireOwner;Long originalFireExpiry;long fireStart,fireStopped;Object originalRuneReady;
        Check(YuumiPlugin core,Player p,CompletableFuture<Map<String,Object>> result)throws Exception {
            this.core=core;this.r=core.rpg();this.p=p;this.result=result;
            original=p.getLocation().clone();mode=p.getGameMode();inventory=copy(p.getInventory().getContents());cursor=p.getItemOnCursor().clone();held=p.getInventory().getHeldItemSlot();
            level=p.getLevel();totalXp=p.getTotalExperience();xp=p.getExp();food=p.getFoodLevel();saturation=p.getSaturation();exhaustion=p.getExhaustion();health=p.getHealth();absorption=p.getAbsorptionAmount();sneaking=p.isSneaking();effects=new ArrayList<>(p.getActivePotionEffects());
            ConfigurationSection before=core.progression().getConfigurationSection(r.root(p));if(before!=null)for(var e:before.getValues(true).entrySet())if(!(e.getValue() instanceof ConfigurationSection))profile.put(e.getKey(),cloneValue(e.getValue()));
            for(String field:List.of("buffs","shields")){Map<Object,Object> map=map(r.combatSkills(),field);Object value=map.get(p.getUniqueId());runtime.put(field,value instanceof Map<?,?> m?new HashMap<>((Map<Object,Object>)m):null);}
            oldPlaced=new HashSet<>((Set<String>)field(field(r,"gathering"),"placed"));
            world=Bukkit.getWorld("world");if(world==null||world.getEnvironment()!=World.Environment.NORMAL)throw new IllegalStateException("Normal wilderness world required");
            x=world.getSpawnLocation().getBlockX()+24;z=world.getSpawnLocation().getBlockZ()+24;y=Math.min(world.getMaxHeight()-12,world.getHighestBlockYAt(x,z)+20);
            for(Entity entity:world.getEntities())initialEntities.add(entity.getUniqueId());
        }
        void start(){
            Bukkit.getPluginManager().registerEvents(this,core);p.setItemOnCursor(null);p.closeInventory();p.setGameMode(GameMode.SURVIVAL);p.setSneaking(false);
            for(int dx=-8;dx<=8;dx++)for(int dz=-5;dz<=14;dz++){put(at(dx,0,dz),Material.STONE);for(int dy=1;dy<=7;dy++)put(at(dx,dy,dz),Material.AIR);}
            p.teleport(location(0,1,0));
            for(LifeProfession life:LifeProfession.values())lifeSteps(life);
            negativeSteps();fireSteps();
            step("final cleanup",3,()->finish(null));advance();
        }
        void step(String name,int ticks,Runnable action){steps.add(new Step(name,ticks,action));}
        void advance(){if(cleaning||result.isDone())return;if(next>=steps.size()){finish(null);return;}Step step=steps.get(next++);Bukkit.getScheduler().runTaskLater(core,()->{try{if(!p.isOnline())throw new IllegalStateException("Fixture player disconnected");step.action.run();if(!cleaning)advance();}catch(Throwable e){finish(new AssertionError(step.name+": "+e,e));}},Math.max(1,step.waitTicks));}
        void check(boolean condition,String label){if(!condition)throw new AssertionError(label);passed.add(label);}
        Block at(int dx,int dy,int dz){return world.getBlockAt(x+dx,y+dy,z+dz);}Location location(double dx,double dy,double dz){return new Location(world,x+dx+.5,y+dy,z+dz+.5);}
        void put(Block b,Material m){blocks.computeIfAbsent(b,Block::getState);b.setType(m,false);}
        void look(Location target){Location loc=p.getLocation();loc.setDirection(target.toVector().subtract(p.getEyeLocation().toVector()));p.teleport(loc);}
        void equip(LifeProfession life,int star){p.getInventory().clear();p.getInventory().setHeldItemSlot(0);p.getInventory().setItem(8,core.items().guidebook());ItemStack tool=core.items().create(life.itemId);if(star>0)tool.editMeta(m->m.getPersistentDataContainer().set(new NamespacedKey(core,"starlight_level"),PersistentDataType.INTEGER,star));p.getInventory().setItem(0,tool);}
        void profile(LifeProfession life,int level,String branch){r.combatSkills().clear(p,RpgDomain.COMBAT);r.combatSkills().clear(p,RpgDomain.LIFE);r.lifeSkills().clear(p,RpgDomain.LIFE);for(String key:List.of("life_cauldron_health","life_cauldron_damage","life_cauldron_haste","life_party_damage","life_allied_fire","life_immunity"))r.combatSkills().grant(p,key,0,0);
            String root=r.root(p);core.progression().set(root+".active_combat","PALADIN");core.progression().set(root+".active_life",life.name());core.progression().set(root+".combat.PALADIN.level",1);core.progression().set(root+".combat.PALADIN.awakening","");
            String path=r.path(p,RpgDomain.LIFE);core.progression().set(path+".level",level);core.progression().set(path+".xp",0);core.progression().set(path+".spec",branch);core.progression().set(path+".perks",null);core.progression().set(path+".cooldowns",null);p.getActivePotionEffects().forEach(e->p.removePotionEffect(e.getType()));equip(life,0);r.combatSkills().refreshStats(p);p.teleport(location(0,1,0));}
        void resetCd(){core.progression().set(r.path(p,RpgDomain.LIFE)+".cooldowns",null);}boolean hasCd(String key){return r.combatSkills().remaining(p,RpgDomain.LIFE,key)>0;}
        int lifeXp(){return core.progression().getInt(r.path(p,RpgDomain.LIFE)+".xp");}
        int count(Material m){int n=0;for(ItemStack s:p.getInventory().getContents())if(s!=null&&s.getType()==m)n+=s.getAmount();return n;}
        void crops(int age){for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++){put(at(dx,0,dz),Material.FARMLAND);put(at(dx,1,dz),Material.WHEAT);Ageable data=(Ageable)at(dx,1,dz).getBlockData();data.setAge(age<0?data.getMaximumAge():age);at(dx,1,dz).setBlockData(data,false);}}
        int matureCrops(){int n=0;for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)if(at(dx,1,dz).getBlockData() instanceof Ageable a&&a.getAge()==a.getMaximumAge())n++;return n;}
        void tree(){for(int dx=3;dx<=5;dx++)for(int dz=0;dz<=2;dz++)for(int dy=1;dy<=4;dy++)put(at(dx,dy,dz),Material.OAK_LOG);for(int dx=2;dx<=6;dx++)for(int dz=-1;dz<=3;dz++)put(at(dx,6,dz),Material.OAK_LEAVES);for(int dx=3;dx<=5;dx++)for(int dz=0;dz<=2;dz++)put(at(dx,0,dz),Material.DIRT);}
        int logs(){int n=0;for(int dx=3;dx<=5;dx++)for(int dz=0;dz<=2;dz++)for(int dy=1;dy<=4;dy++)if(at(dx,dy,dz).getType()==Material.OAK_LOG)n++;return n;}
        void pool(){for(int dx=-3;dx<=3;dx++)for(int dz=5;dz<=10;dz++){put(at(dx,-1,dz),Material.STONE);put(at(dx,0,dz),Material.WATER);}p.teleport(location(0,1,3));look(location(0,0,8));}
        void finishAtFloor(){p.teleport(location(-6,1,6));look(location(-4,0,8));}
        void primary(LifeProfession life){r.lifeSkills().interact(p,life==LifeProfession.FARMER?at(0,1,0):null,BlockFace.UP);}
        void lifeSteps(LifeProfession life){
            step(life+" primary setup",2,()->{profile(life,20,"A");switch(life){case MINER->put(at(4,1,-2),Material.DIAMOND_ORE);case FARMER->crops(-1);case ANGLER->{pool();p.setSneaking(true);}case CARPENTER->rangeBefore=p.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).getValue();case BLACKSMITH->{ItemStack chest=new ItemStack(Material.IRON_CHESTPLATE);chest.editMeta(m->((Damageable)m).setDamage(100));p.getInventory().setChestplate(chest);}case CHEF->{p.setFoodLevel(5);p.setSaturation(0);}}primary(life);p.setSneaking(false);});
            step(life+" primary actual effect",5,()->{switch(life){case MINER->check(p.hasPotionEffect(PotionEffectType.HASTE)&&world.getEntitiesByClass(BlockDisplay.class).stream().anyMatch(e->!initialEntities.contains(e.getUniqueId())&&e.isGlowing()),"MINER scan creates glowing private ore outlines and haste");case FARMER->check(matureCrops()==24&&count(Material.WHEAT)>0,"FARMER harvests 25 mature crops into inventory");case ANGLER->{double before=r.lifeSkills().waitMultiplier(p);r.lifeSkills().beginFishing(p);check(r.lifeSkills().waitMultiplier(p)<before,"ANGLER bait accelerates the next actual cast state");}case CARPENTER->{check(Math.abs(p.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).getValue()-rangeBefore-3.5)<.001,"CARPENTER gains actual +3.5 block reach");r.lifeSkills().clear(p,RpgDomain.LIFE);r.combatSkills().refreshStats(p);check(Math.abs(p.getAttribute(Attribute.BLOCK_INTERACTION_RANGE).getValue()-rangeBefore)<.001&&r.lifeSkills().speedBonus(p)==0,"CARPENTER reach and speed are removed on clear");}case BLACKSMITH->check(((Damageable)p.getInventory().getChestplate().getItemMeta()).getDamage()<100,"BLACKSMITH repairs actual equipped armor");case CHEF->check(p.getFoodLevel()==20&&p.getSaturation()>=19&&p.getPotionEffect(PotionEffectType.REGENERATION).getAmplifier()==1,"CHEF fills hunger and grants actual regeneration II");}check(hasCd("primary"),life+" valid primary commits life cooldown");});
            if(life==LifeProfession.FARMER){step("FARMER star3 fixture",2,()->{crops(-1);equip(life,3);resetCd();farmerStar=r.harvest(p,at(0,1,0));});step("FARMER star3 check",3,()->check(farmerStar==27,"FARMER star3 adds two fringe harvest targets"));}
            if(life==LifeProfession.CARPENTER){step("CARPENTER plain natural chain",2,()->{tree();equip(life,0);check(p.breakBlock(at(3,1,0)),"CARPENTER natural first log breaks");});step("CARPENTER plain chain count",3,()->{logsPlain=36-logs();check(logsPlain==26,"CARPENTER Lv20 normal chain breaks 26 natural logs");tree();equip(life,3);check(p.breakBlock(at(3,1,0)),"CARPENTER star3 natural first log breaks");});step("CARPENTER star3 chain count",3,()->{logsStar=36-logs();check(logsStar==28,"CARPENTER star3 adds two natural logs");});}
            for(String branch:List.of("","A","B")){
                step(life+" "+branch+" F setup",3,()->{profile(life,branch.isEmpty()?5:20,branch);switch(life){case FARMER->crops(0);case ANGLER->pool();case CARPENTER->{if(branch.equals("B")){tree();look(location(3,2,0));}}case CHEF->finishAtFloor();default->{}}r.lifeSkills().finisher(p);});
                step(life+" "+branch+" F actual effect",life==LifeProfession.CHEF?12:4,()->{check(hasCd("finisher"),life+" "+(branch.isEmpty()?"BASE":branch)+" F commits a valid life cooldown");switch(life){case MINER->{if(branch.equals("B")){check(r.lifeSkills().excavation(p),"MINER B starts timed 3x3 excavation");for(int dz=-1;dz<=1;dz++)for(int dy=1;dy<=3;dy++)put(at(-4,dy,dz),Material.STONE);look(location(-4,2,0));check(p.breakBlock(at(-4,2,0)),"MINER B accepts actual tunnel center break");}else check(r.lifeSkills().geodeMultiplier(p)==(branch.isEmpty()?1.5:3),"MINER "+(branch.isEmpty()?"BASE":"A")+" changes geode drop multiplier");}
                    case FARMER->{if(branch.equals("B"))check(r.lifeSkills().cropMultiplier(p)==2,"FARMER B doubles actual harvest multiplier");else check(branch.isEmpty()?((Ageable)at(0,1,0).getBlockData()).getAge()>0:matureCrops()==49,"FARMER "+(branch.isEmpty()?"BASE partial":"A full")+" growth changes real crops");}
                    case ANGLER->{if(branch.equals("B")){check(r.lifeSkills().doubleFish(p)&&r.lifeSkills().appraisalBonus(p)>0,"ANGLER B enables double catch and appraisal");caughtFish();}else check(r.lifeSkills().fishingWeight(p)>1,"ANGLER "+(branch.isEmpty()?"BASE":"A")+" creates a useful fishing zone");}
                    case CARPENTER->{if(branch.equals("B"))check(logs()==0,"CARPENTER B fells a complete natural canopy cluster");else check(r.lifeSkills().fallReduction(p)==(branch.isEmpty()?.5:1)&&r.lifeSkills().consumeFurnitureBoost(p)>0,"CARPENTER "+(branch.isEmpty()?"BASE":"A")+" grants fall safety and next-craft bonus");}
                    case BLACKSMITH->{if(branch.equals("B")){check(r.lifeSkills().damageBonus(p)==.15,"BLACKSMITH B grants party damage");Zombie target=world.spawn(location(-5,1,-2),Zombie.class,e->{e.setAI(false);e.setSilent(true);e.setPersistent(false);});ownedMobs.add(target.getUniqueId());hits.put(target.getUniqueId(),0);r.damage().raw(p,target,1,CombatDamageService.DamageKind.NORMAL);check(hits.get(target.getUniqueId())==2,"BLACKSMITH B adds one actual secondary fire hit");}else{check(p.getOpenInventory().getTopInventory().getSize()==36&&r.lifeSkills().masterworkBonus(p)==(branch.isEmpty()?.10:.25),"BLACKSMITH "+(branch.isEmpty()?"BASE":"A")+" opens portable appraisal with quality buff");p.closeInventory();}}
                    case CHEF->{if(branch.equals("B")){p.addPotionEffect(new PotionEffect(PotionEffectType.POISON,80,0));check(!p.hasPotionEffect(PotionEffectType.POISON)&&r.lifeSkills().speedBonus(p)>.2,"CHEF B rejects real harmful potion application");}else{Interaction pot=world.getEntitiesByClass(Interaction.class).stream().filter(e->p.getUniqueId().toString().equals(e.getPersistentDataContainer().get(new NamespacedKey(core,"rpg_cauldron"),PersistentDataType.STRING))).findFirst().orElseThrow();Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(p,pot,EquipmentSlot.HAND));check(r.lifeSkills().healthBonus(p)==(branch.isEmpty()?2:4)&&r.lifeSkills().damageBonus(p)>0,"CHEF "+(branch.isEmpty()?"BASE":"A")+" cauldron click grants actual party feast");}}
                }});
                if(life==LifeProfession.MINER&&branch.equals("B"))step("MINER tunnel actual result",3,()->{for(int dz=-1;dz<=1;dz++)for(int dy=1;dy<=3;dy++)check(at(-4,dy,dz).getType().isAir(),"MINER B removes tunnel cell "+dy+"/"+dz);});
            }
        }
        int plainSalmon(){int n=0;for(ItemStack item:p.getInventory().getContents())if(item!=null&&item.getType()==Material.SALMON&&core.items().getCustomId(item)==null)n+=item.getAmount();return n;}
        void caughtFish(){try{int before=plainSalmon();Item caught=world.dropItem(location(0,2,0),new ItemStack(Material.SALMON));PlayerFishEvent event=new PlayerFishEvent(p,caught,null,PlayerFishEvent.State.CAUGHT_FISH);Object gathering=field(r,"gathering");Method method=gathering.getClass().getDeclaredMethod("fish",PlayerFishEvent.class);method.setAccessible(true);method.invoke(gathering,event);check(plainSalmon()==before+1&&caught.getItemStack().getAmount()==1,"ANGLER B CAUGHT_FISH handler delivers one guaranteed additional catch");caught.remove();}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
        void negativeSteps(){
            for(LifeProfession life:List.of(LifeProfession.MINER,LifeProfession.FARMER,LifeProfession.CARPENTER)){
                step(life+" cancelled break fixture",3,()->{profile(life,1,"");Block target=at(0,1,-3);put(target,life==LifeProfession.MINER?Material.DIAMOND_ORE:life==LifeProfession.CARPENTER?Material.OAK_LOG:Material.WHEAT);if(life==LifeProfession.FARMER){put(target.getRelative(0,-1,0),Material.FARMLAND);Ageable data=(Ageable)target.getBlockData();data.setAge(data.getMaximumAge());target.setBlockData(data,false);}cancelBreak=true;check(!p.breakBlock(target),life+" cancelled real BlockBreak returns false");if(life==LifeProfession.FARMER){r.lifeSkills().interact(p,target,BlockFace.UP);check(!hasCd("primary"),"protected cancelled harvest consumes no cooldown");}cancelBreak=false;check(lifeXp()==0,life+" cancelled real BlockBreak grants zero custom XP");});
            }
            step("placed ore fixture",3,()->{profile(LifeProfession.MINER,1,"");Block ore=at(0,1,-3);BlockState replaced=ore.getState();put(ore,Material.DIAMOND_ORE);BlockPlaceEvent event=new BlockPlaceEvent(ore,replaced,ore.getRelative(0,-1,0),new ItemStack(Material.DIAMOND_ORE),p,true,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(event);check(!event.isCancelled(),"placed ore fixture passes actual placement event");check(p.breakBlock(ore),"placed ore remains normally breakable");check(lifeXp()==0,"player-installed ore grants zero custom mining XP");});
            step("actual protected hub harvest",3,()->{profile(LifeProfession.FARMER,1,"");World hub=Bukkit.getWorld("yuumi_hub");Block crop=hub.getBlockAt(6,65,42);put(crop.getRelative(0,-1,0),Material.FARMLAND);put(crop,Material.WHEAT);Ageable age=(Ageable)crop.getBlockData();age.setAge(age.getMaximumAge());crop.setBlockData(age,false);p.teleport(crop.getLocation().add(.5,0,2.5));check(!r.plots.canBuildInHub(p,crop.getLocation()),"hub public crop fixture is protected from this player");r.lifeSkills().interact(p,crop,BlockFace.UP);check(crop.getType()==Material.WHEAT&&!hasCd("primary")&&lifeXp()==0,"actual protected hub harvest changes no crop, XP, or cooldown");});
        }
        long fireSequence(){return fireDummy.getPersistentDataContainer().getOrDefault(new NamespacedKey(core,"yuumi_training_hit_sequence"),PersistentDataType.LONG,0L);}
        void fireSteps(){
            step("native fire recursion setup",3,()->{try{profile(LifeProfession.BLACKSMITH,1,"");for(String key:List.of("proc-chance","cooldown-seconds","duration-seconds")){String path="runes.FIRE."+key;originalConfig.put(path,cloneValue(core.getConfig().get(path)));core.getConfig().set(path,key.equals("cooldown-seconds")?0:1);}Map<Object,Object> ready=map(r.damage(),"runeReady");originalRuneReady=ready.remove(p.getUniqueId());
                World hub=Bukkit.getWorld("yuumi_hub");fireDummy=hub.getEntitiesByClass(Vindicator.class).stream().filter(e->e.getPersistentDataContainer().has(new NamespacedKey(core,"yuumi_training_dummy"),PersistentDataType.BYTE)).findFirst().orElseThrow();originalFire=fireDummy.getFireTicks();originalDummyEffects=new ArrayList<>(fireDummy.getActivePotionEffects());originalFireOwner=fireDummy.getPersistentDataContainer().get(new NamespacedKey(core,"yuumi_training_fire_owner"),PersistentDataType.STRING);originalFireExpiry=fireDummy.getPersistentDataContainer().get(new NamespacedKey(core,"yuumi_training_fire_expires"),PersistentDataType.LONG);
                fireDummy.setFireTicks(0);fireDummy.getActivePotionEffects().forEach(e->fireDummy.removePotionEffect(e.getType()));fireDummy.getPersistentDataContainer().remove(new NamespacedKey(core,"yuumi_training_fire_owner"));fireDummy.getPersistentDataContainer().remove(new NamespacedKey(core,"yuumi_training_fire_expires"));p.teleport(fireDummy.getLocation().clone().add(2.5,0,0));ItemStack sword=core.items().create("weapon_paladin_sword");r.getWorkshop().inscribe(sword,"FIRE",false);p.getInventory().setItemInMainHand(sword);fireStart=fireSequence();r.damage().raw(p,fireDummy,1,CombatDamageService.DamageKind.NORMAL);check(fireSequence()>=fireStart+2,"FIRE fixture records direct hit and guaranteed rune hit");
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}});
            step("native fire expires",40,()->{fireStopped=fireSequence();check(fireStopped>fireStart+2,"native owned FIRE_TICK produces approved damage");check(fireDummy.getFireTicks()<=0,"one-second FIRE expires with zero rune proc cooldown");});
            step("native DOT does not proc runes",25,()->check(fireSequence()==fireStopped&&fireDummy.getFireTicks()<=0,"native DOT cannot recursively proc FIRE or extend burn"));
        }
        @EventHandler(priority=EventPriority.LOWEST)public void cancel(BlockBreakEvent event){if(cancelBreak&&event.getPlayer().equals(p)&&blocks.containsKey(event.getBlock()))event.setCancelled(true);}
        @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)public void damage(EntityDamageByEntityEvent event){if(event.getDamager().equals(p)&&ownedMobs.contains(event.getEntity().getUniqueId()))hits.merge(event.getEntity().getUniqueId(),1,Integer::sum);}
        void finish(Throwable failure){if(cleaning)return;cleaning=true;cancelBreak=false;HandlerList.unregisterAll(this);r.combatSkills().clear(p,RpgDomain.COMBAT);r.combatSkills().clear(p,RpgDomain.LIFE);r.lifeSkills().clear(p,RpgDomain.LIFE);Bukkit.getScheduler().runTaskLater(core,()->restore(failure),3);}
        void restore(Throwable failure){Throwable error=failure;
            try{HandlerList.unregisterAll(this);r.combatSkills().clear(p,RpgDomain.COMBAT);r.combatSkills().clear(p,RpgDomain.LIFE);r.lifeSkills().clear(p,RpgDomain.LIFE);p.closeInventory();
                for(Entity e:world.getEntities())if(!initialEntities.contains(e.getUniqueId())&&(ownedMobs.contains(e.getUniqueId())||e.getLocation().getBlockX()>=x-25&&e.getLocation().getBlockX()<=x+25&&e.getLocation().getBlockZ()>=z-25&&e.getLocation().getBlockZ()<=z+25&&e.getLocation().getY()>=y-5&&(e instanceof Display||e instanceof Item||e instanceof ExperienceOrb||e instanceof Interaction)))e.remove();
                List<BlockState> restore=new ArrayList<>(blocks.values());Collections.reverse(restore);for(BlockState state:restore)state.update(true,false);
                Set<String> placed=(Set<String>)field(field(r,"gathering"),"placed");for(Block b:blocks.keySet()){String key=b.getWorld().getUID()+":"+b.getX()+":"+b.getY()+":"+b.getZ();if(oldPlaced.contains(key))placed.add(key);else placed.remove(key);}core.progression().set("rpg_placed_resources",new ArrayList<>(placed));
                core.progression().set(r.root(p),null);profile.forEach((key,value)->core.progression().set(r.root(p)+"."+key,cloneValue(value)));originalConfig.forEach((key,value)->core.getConfig().set(key,value));if(!originalConfig.isEmpty()){Map<Object,Object> ready=map(r.damage(),"runeReady");ready.remove(p.getUniqueId());if(originalRuneReady!=null)ready.put(p.getUniqueId(),originalRuneReady);}
                if(fireDummy!=null&&fireDummy.isValid()){fireDummy.setFireTicks(originalFire);fireDummy.getActivePotionEffects().forEach(e->fireDummy.removePotionEffect(e.getType()));if(originalDummyEffects!=null)originalDummyEffects.forEach(fireDummy::addPotionEffect);var pdc=fireDummy.getPersistentDataContainer();var owner=new NamespacedKey(core,"yuumi_training_fire_owner");var expiry=new NamespacedKey(core,"yuumi_training_fire_expires");if(originalFireOwner==null)pdc.remove(owner);else pdc.set(owner,PersistentDataType.STRING,originalFireOwner);if(originalFireExpiry==null)pdc.remove(expiry);else pdc.set(expiry,PersistentDataType.LONG,originalFireExpiry);}
                Object training=field(core,"trainingManager");training.getClass().getMethod("quit",PlayerQuitEvent.class).invoke(training,new PlayerQuitEvent(p,"RPG fixture practice cleanup"));
                if(p.isOnline()){p.getInventory().setContents(copy(inventory));p.getInventory().setHeldItemSlot(held);p.setItemOnCursor(cursor);p.setGameMode(mode);p.setSneaking(sneaking);p.getActivePotionEffects().forEach(e->p.removePotionEffect(e.getType()));effects.forEach(p::addPotionEffect);p.teleport(original);for(String name:runtime.keySet()){Map<Object,Object> map=map(r.combatSkills(),name);map.remove(p.getUniqueId());if(runtime.get(name)!=null)map.put(p.getUniqueId(),new HashMap<>(runtime.get(name)));}r.combatSkills().refreshStats(p);p.setHealth(Math.min(health,p.getAttribute(Attribute.MAX_HEALTH).getValue()));p.setAbsorptionAmount(Math.min(absorption,p.getAttribute(Attribute.MAX_ABSORPTION).getValue()));p.setFoodLevel(food);p.setSaturation(saturation);p.setExhaustion(exhaustion);p.setLevel(level);p.setExp(xp);p.setTotalExperience(totalXp);}core.saveDataFile();
            }catch(Throwable cleanup){if(error==null)error=cleanup;else error.addSuppressed(cleanup);}finally{RUNNING.set(false);}
            if(error!=null)result.completeExceptionally(error);else result.complete(Map.of("checks",passed,"count",passed.size(),"lifeProfessions",6,"restoredBlocks",blocks.size(),"farmerStar3",farmerStar,"carpenterPlain",logsPlain,"carpenterStar3",logsStar,"success",true));
        }
    }
    private static Object field(Object value,String name)throws ReflectiveOperationException{Field f=value.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(value);}
    @SuppressWarnings("unchecked")private static Map<Object,Object> map(Object value,String name)throws ReflectiveOperationException{return (Map<Object,Object>)field(value,name);}
    private static ItemStack[] copy(ItemStack[] values){ItemStack[] out=new ItemStack[values.length];for(int i=0;i<values.length;i++)out[i]=values[i]==null?null:values[i].clone();return out;}
    private static Object cloneValue(Object value){if(value instanceof ItemStack item)return item.clone();if(value instanceof List<?> list)return list.stream().map(RpgWorldCheck::cloneValue).toList();if(value instanceof Map<?,?> map){Map<Object,Object> out=new LinkedHashMap<>();map.forEach((k,v)->out.put(k,cloneValue(v)));return out;}return value;}
}
