package kr.yuumi97.core.integration;

import kr.yuumi97.core.YuumiPlugin;
import kr.yuumi97.core.dungeon.DungeonLayout;
import kr.yuumi97.core.dungeon.DungeonManager;
import kr.yuumi97.core.rpg.RpgDomain;
import kr.yuumi97.core.rpg.RpgJobManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Disposable Paper scenarios. Reflection is confined to fixture setup/observation, never production commands. */
public final class DungeonScenarioCheck {
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    private DungeonScenarioCheck(){}
    @FunctionalInterface private interface Action {void run()throws Exception;}
    @FunctionalInterface private interface Condition {boolean ready()throws Exception;}
    private record Step(String name,int timeout,Action start,Condition ready,Action verify){}

    public static CompletableFuture<Map<String,Object>> run(YuumiPlugin core,Player player){
        Path path=core.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        Path allowed=Path.of("E:/MinGem/yuumi-core/build/server-integration").toAbsolutePath().normalize();
        if(!path.startsWith(allowed))return CompletableFuture.failedFuture(new IllegalStateException("Disposable server-integration world container required"));
        if(!RUNNING.compareAndSet(false,true))return CompletableFuture.failedFuture(new IllegalStateException("Dungeon scenario fixture already running"));
        CompletableFuture<Map<String,Object>> result=new CompletableFuture<>();
        Runnable start=()->{Check check=null;try{check=new Check(core,player,result);check.start();}catch(Throwable ex){if(check!=null)check.finish(ex);else{RUNNING.set(false);result.completeExceptionally(ex);}}};
        if(Bukkit.isPrimaryThread())start.run();else Bukkit.getScheduler().runTask(core,start);
        return result;
    }

    private static final class Branch {
        final boolean exists;final Object scalar;final Map<String,Object> leaves=new LinkedHashMap<>();final Set<String> sections=new LinkedHashSet<>();
        Branch(FileConfiguration data,String path){exists=data.contains(path);ConfigurationSection section=data.getConfigurationSection(path);
            scalar=section==null?cloneValue(data.get(path)):null;
            if(section!=null){sections.add("");section.getValues(true).forEach((key,value)->{if(value instanceof ConfigurationSection)sections.add(key);else leaves.put(key,cloneValue(value));});}
        }
        void restore(FileConfiguration data,String path){data.set(path,null);if(!exists)return;
            if(sections.isEmpty()){data.set(path,cloneValue(scalar));return;}
            for(String section:sections){String target=section.isEmpty()?path:path+"."+section;if(data.getConfigurationSection(target)==null)data.createSection(target);}
            leaves.forEach((key,value)->data.set(path+"."+key,cloneValue(value)));
        }
    }

    private static final class Check extends BukkitRunnable implements Listener {
        final YuumiPlugin core;final Player p;final DungeonManager dungeon;final RpgJobManager rpg;final FileConfiguration data;final World world;
        final CompletableFuture<Map<String,Object>> result;final Deque<Step> steps=new ArrayDeque<>();final List<String> passed=new ArrayList<>();
        final List<Map<String,Object>> chapters=new ArrayList<>();final List<Object> fixtures=new ArrayList<>();final Set<String> temporaryRuns=new HashSet<>();
        final Map<String,Branch> savedData=new LinkedHashMap<>();final Map<String,Boolean> parentPresence=new LinkedHashMap<>();
        final Map<String,Object> combatState=new LinkedHashMap<>();final Map<String,Object> lifeState=new LinkedHashMap<>();
        final Location original;final GameMode mode;final ItemStack[] inventory;final ItemStack cursor;final int held,food,level,totalXp,fireTicks,noDamageTicks;
        final float saturation,exhaustion,xp,fallDistance;final double health,absorption,maxAbsorptionBase;final boolean sneaking,allowFlight,flying,invulnerable;
        final List<PotionEffect> effects;final Map<Statistic,Integer> statistics=new EnumMap<>(Statistic.class);
        final Map<UUID,Double> approvedBossDamage=new HashMap<>();final Map<UUID,Boolean> cancelledBossDamage=new HashMap<>();
        final long wallStarted=System.currentTimeMillis();long tickStarted,stepStarted;Step current;boolean cleaning;
        Object run,mob;LivingEntity boss;Class<?> runClass;int chapter,slot,firstMelodyIndex;long originalStarted,nextInput,signatureStarted,signatureDeadline;
        double poolBefore,absorptionBefore;int pulseEvents,puzzleClicks,firstLocked=-1;boolean checkedLocked,secondSolve;int wrongStage;
        List<Integer> answer;int[] mirrorAnswer;Object boonBefore;Map<String,Object> evidence;String measured;

        Check(YuumiPlugin core,Player player,CompletableFuture<Map<String,Object>> result)throws Exception{
            if(player==null||!player.isOnline()||player.isDead())throw new IllegalStateException("An online living fixture player is required");
            this.core=core;this.p=player;this.result=result;dungeon=(DungeonManager)get(core,"dungeonManager");rpg=core.rpg();data=core.progression();world=Bukkit.getWorld(DungeonManager.WORLD_NAME);
            if(world==null||dungeon.isParticipant(player))throw new IllegalStateException("Fixture player must be outside active dungeon runs");
            Map<?,?> live=(Map<?,?>)get(dungeon,"runs");for(Object liveRun:live.values()){int occupied=(Integer)get(liveRun,"slot");if(occupied>=60&&occupied<=62)throw new IllegalStateException("Reserved scenario slot occupied: "+occupied);}
            original=p.getLocation().clone();mode=p.getGameMode();inventory=copy(p.getInventory().getContents());cursor=p.getItemOnCursor().clone();held=p.getInventory().getHeldItemSlot();
            food=p.getFoodLevel();level=p.getLevel();totalXp=p.getTotalExperience();fireTicks=p.getFireTicks();noDamageTicks=p.getNoDamageTicks();saturation=p.getSaturation();exhaustion=p.getExhaustion();xp=p.getExp();fallDistance=p.getFallDistance();
            health=p.getHealth();absorption=p.getAbsorptionAmount();maxAbsorptionBase=p.getAttribute(Attribute.MAX_ABSORPTION).getBaseValue();sneaking=p.isSneaking();allowFlight=p.getAllowFlight();flying=p.isFlying();invulnerable=p.isInvulnerable();effects=new ArrayList<>(p.getActivePotionEffects());
            for(Statistic stat:Statistic.values())if(stat.getType()==Statistic.Type.UNTYPED)statistics.put(stat,p.getStatistic(stat));
            String id=p.getUniqueId().toString();for(String path:List.of("rpg."+id,"dungeon.recovery."+id,"dungeon.stats."+id,"dungeon.completion-xp."+id,"rewards."+id,"reward_receipts."+id))savedData.put(path,new Branch(data,path));
            for(String parent:List.of("rpg","dungeon.runs","dungeon.recovery","dungeon.stats","dungeon.completion-xp","rewards","reward_receipts"))parentPresence.put(parent,data.contains(parent));
            capturePlayerMaps(rpg.combatSkills(),combatState,List.of("buffs","shields","resource","normalCount","lastCombat","lastGain","npcUntil","crisisReady","killReady","dodgeReady","inputs"));
            capturePlayerMaps(rpg.lifeSkills(),lifeState,List.of("buffs"));
            runClass=Class.forName("kr.yuumi97.core.dungeon.DungeonManager$Run");
        }

        void start()throws Exception{
            Bukkit.getPluginManager().registerEvents(this,core);p.closeInventory();p.setItemOnCursor(null);p.getInventory().clear();p.setSneaking(false);p.setInvulnerable(false);
            rpg.combatSkills().clear(p,RpgDomain.COMBAT);rpg.combatSkills().clear(p,RpgDomain.LIFE);rpg.lifeSkills().clear(p,RpgDomain.LIFE);
            clearPlayerMaps(rpg.combatSkills(),combatState);clearPlayerMaps(rpg.lifeSkills(),lifeState);
            for(String path:savedData.keySet())data.set(path,null);rpg.combatSkills().refreshStats(p);
            p.getActivePotionEffects().forEach(e->p.removePotionEffect(e.getType()));p.setAllowFlight(true);p.setFlying(true);prime();
            for(int c=1;c<=3;c++)chapterSteps(c);
            tickStarted=Bukkit.getCurrentTick();runTaskTimer(core,1,1);
        }
        void chapterSteps(int c){
            add("chapter"+c+" isolated run",40,()->create(c),()->true,()->{});
            add("chapter"+c+" first puzzle entry",40,()->position(point(0,67,-149)),()->activated().contains(5),()->{
                answer=new ArrayList<>((List<Integer>)get(run,"melody"));mirrorAnswer=((int[])get(run,"puzzleTargets")).clone();originalStarted=(Long)get(run,"started");
            });
            puzzle("chapter"+c+" solo puzzle",false);
            add("chapter"+c+" checkpoint retry invariants",40,()->{
                boonBefore=cloneValue(((Map<?,?>)get(run,"boons")).get(p.getUniqueId()));invoke(dungeon,"retryCheckpoint",new Class<?>[]{runClass},run);
            },()->true,()->{
                check((Boolean)get(run,"retried")&&(Integer)get(run,"attempt")==1,"chapter"+c+" consumes exactly one checkpoint retry");
                check((Long)get(run,"started")==originalStarted,"chapter"+c+" keeps original run deadline");
                check(!cleared().contains(5)&&!cleared().contains(6),"chapter"+c+" resets only puzzle/boss progress");
                check(cleared().containsAll(List.of(1,2,3,4)),"chapter"+c+" preserves preceding zone clears");
                check(Objects.equals(boonBefore,((Map<?,?>)get(run,"boons")).get(p.getUniqueId())),"chapter"+c+" preserves chosen boon");
                check(((Set<?>)get(run,"sideOwners")).contains(p.getUniqueId())&&data.getBoolean(runPath()+".side."+p.getUniqueId()),"chapter"+c+" preserves side chest ledger");
            });
            add("chapter"+c+" retry puzzle entry",40,()->{prime();position(point(0,67,-149));},()->activated().contains(5),()->{
                check(answer.equals(get(run,"melody"))&&Arrays.equals(mirrorAnswer,(int[])get(run,"puzzleTargets")),"chapter"+c+" retains puzzle answer after retry");
            });
            puzzle("chapter"+c+" retry solo puzzle",true);
            add("chapter"+c+" real rising gate",80,()->{},()->gateOpen(5),()->check(world.getBlockAt(slot*1024,70,-176).getType().isAir(),"chapter"+c+" puzzle gate physically clears"));
            add("chapter"+c+" assembly",40,()->position(point(0,67,-183)),()->get(run,"boss")!=null,()->{
                UUID id=(UUID)get(run,"boss");mob=((Map<?,?>)get(run,"mobs")).get(id);boss=(LivingEntity)get(mob,"entity");
                check((Integer)get(run,"stage")==6&&!gateOpen(5),"chapter"+c+" assembly seals cathedral gate");
                position(c==1?point(-13,67,-208):point(0,67,-199));prime();
                double max=((Map<UUID,Double>)get(run,"maxHp")).get(id);check(Math.abs(max-new double[]{300,500,800}[c-1])<.001,"chapter"+c+" preserves solo boss HP");boss.setNoDamageTicks(0);boss.damage(max*20,p);
                double actual=((Map<UUID,Double>)get(run,"logicalHp")).get(id);
                check(Math.abs(actual-max*.5)<.001&&(Integer)get(mob,"phase")==2&&!boss.isDead(),"chapter"+c+" actual lethal-sized hit clamps to phase2 halfHP");
                check(Math.abs(approvedBossDamage.getOrDefault(id,-1.0)-max*.5)<.001&&cancelledBossDamage.getOrDefault(id,false),"chapter"+c+" phase gate records approved damage on cancelled native event");
                evidence.put("phaseClampHp",actual);evidence.put("bossMaxHp",max);
            });
            if(c==1){signature("nova covered",true);signature("nova uncovered",false);}
            else if(c==2){signature("ignion valves",true);signature("ignion failed cooling",false);}
            else{signature("eclipse safe lantern",true);signature("eclipse unsafe outside",false);}
            add("chapter"+c+" cleanup",40,()->{cleanupRun();chapters.add(Map.copyOf(evidence));},()->true,()->check(!dungeon.isParticipant(p),"chapter"+c+" removes participant ownership"));
        }
        void add(String name,int timeout,Action start,Condition ready,Action verify){steps.add(new Step(name,timeout,start,ready,verify));}
        void puzzle(String label,boolean retry){add(label,500,()->{secondSolve=retry;nextInput=0;firstLocked=-1;checkedLocked=false;wrongStage=chapter==3&&!retry?0:2;puzzleClicks=0;},()->{
            if(cleared().contains(5))return true;
            long now=System.currentTimeMillis();if(now<nextInput)return false;
            int[] values=(int[])get(run,"puzzleValues");int index=-1;
            if(chapter==1){int[] target=(int[])get(run,"puzzleTargets");for(int i=0;i<4;i++)if(values[i]!=target[i]){index=i;break;}}
            else if(chapter==2){
                if(firstLocked>=0&&!checkedLocked){int old=values[firstLocked];interact("puzzle",firstLocked);check(((int[])get(run,"puzzleValues"))[firstLocked]==old,label+" locked green valve cannot change");checkedLocked=true;nextInput=now+300;return false;}
                for(int i=0;i<3;i++)if(values[i]<40||values[i]>60){index=i;break;}
            }else{
                if(now-(Long)get(run,"memoryShownAt")<3100)return false;
                List<Integer> melody=(List<Integer>)get(run,"melody");int progress=(Integer)get(run,"memoryIndex");
                if(wrongStage==0){interact("puzzle",melody.get(0));check((Integer)get(run,"memoryIndex")==1,"pipe accepts first correct note");wrongStage=1;nextInput=now+300;return false;}
                if(wrongStage==1){interact("puzzle",(melody.get(1)+1)%4);check((Integer)get(run,"memoryIndex")==0&&melody.equals(get(run,"melody")),"wrong pipe resets progress and replays the same answer");wrongStage=2;nextInput=now+300;return false;}
                index=melody.get(progress);
            }
            if(index>=0){int before=values[index];interact("puzzle",index);puzzleClicks++;if(chapter==1)check(((int[])get(run,"puzzleValues"))[index]==(before+1)%4,label+" rotates actual mirror");
                if(chapter==2){int after=((int[])get(run,"puzzleValues"))[index];check(after==(before+20)%120,label+" changes actual pressure by20");if(after>=40&&after<=60&&firstLocked<0)firstLocked=index;}}
            nextInput=now+300;return false;
        },()->{check(cleared().contains(5),label+" opens next gate for one player");evidence.put(retry?"retryPuzzleClicks":"puzzleClicks",puzzleClicks);});}

        void signature(String label,boolean success){
            add(label+" start",220,()->{
                measured=label;prime();pulseEvents=0;set(mob,"nextSignature",clock());
                if(chapter==1)position(success?point(-13,67,-208):point(0,67,-199));else if(chapter==2)position(point(0,67,-199));else position(point(0,67,-199));
            },()->(Integer)get(mob,"signature")==chapter,()->{
                signatureStarted=clock();signatureDeadline=(Long)get(mob,"signatureEnd");
                long expected=chapter==1?60:chapter==2?120:160;check(signatureDeadline-signatureStarted>=expected-2,label+" retains full real-tick warning window");
                if(chapter==1)check(worldCover(boss.getEyeLocation(),p.getEyeLocation())==success,label+" uses actual opaque column geometry");
                if(chapter==2&&success){interact("cooling",2);check((Integer)get(mob,"heat")==60,"first cooling valve removes40heat");interact("cooling",2);check((Integer)get(mob,"heat")==60,"duplicate cooling valve cannot double count");interact("cooling",0);check((Integer)get(mob,"heat")==20&&((Set<?>)get(run,"valves")).size()==2,"solo opens two distinct cooling valves");}
                if(chapter==3&&success){int lit=-1,count=0;for(int i=0;i<4;i++)if((Boolean)invoke(dungeon,"litLantern",new Class<?>[]{mob.getClass(),int.class},mob,i)){lit=i;count++;}check(count==2,"eclipse lights exactly two safe lanterns");Location point=(Location)invoke(dungeon,"lanternPoint",new Class<?>[]{runClass,int.class},run,lit);position(point.clone().add(0,0,1));}
                poolBefore=pool();absorptionBefore=p.getAbsorptionAmount();pulseEvents=0;
            });
            add(label+" resolution",240,()->{},()->(Integer)get(mob,"signature")==0,()->{
                check(clock()>=signatureDeadline&&clock()-signatureStarted>= (chapter==1?58:chapter==2?118:158),label+" completes only after actual warning ticks");
                double damage=poolBefore-pool();evidence.put(label+"Damage",damage);evidence.put(label+"DamageEvents",pulseEvents);evidence.put(label+"Ticks",clock()-signatureStarted);
                if(success)check(Math.abs(damage)<.001,label+" avoids actual health/absorption loss");
                else check(damage>.01&&p.getAbsorptionAmount()<absorptionBefore&&p.getHealth()>0,label+" causes real absorbed damage and remains survivable");
                if(chapter==2&&success)check((Long)get(mob,"staggerUntil")>clock(),"successful cooling actually staggers Ignion");
                if(chapter==3){check(!p.hasPotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS),"eclipse removes darkness after resolution");if(!success)check(pulseEvents==6,"unsafe eclipse applies six actual damage pulses");}
                measured=null;
            });
        }

        @SuppressWarnings({"unchecked","rawtypes"})void create(int c)throws Exception{
            chapter=c;slot=59+c;evidence=new LinkedHashMap<>();evidence.put("chapter",c);evidence.put("slot",slot);
            for(Object live:((Map<?,?>)get(dungeon,"runs")).values())check((Integer)get(live,"slot")!=slot,"reserved slot"+slot+" remains unoccupied");
            Constructor<?> constructor=runClass.getDeclaredConstructor(UUID.class,List.class,int.class,int.class);constructor.setAccessible(true);run=constructor.newInstance(p.getUniqueId(),List.of(p.getUniqueId()),c,slot);fixtures.add(run);
            invoke(dungeon,"buildRooms",new Class<?>[]{runClass},run);check(((Set<?>)get(run,"chunks")).size()==56,"chapter"+c+" acquires56chunk tickets");
            ((Map<Object,Object>)get(dungeon,"runs")).put(get(run,"id"),run);((Map<Object,Object>)get(dungeon,"participants")).put(p.getUniqueId(),run);((Map<Object,Object>)get(run,"modes")).put(p.getUniqueId(),mode);
            data.set(runPath()+".status","RUNNING");data.set(runPath()+".leader",p.getUniqueId().toString());data.set(runPath()+".key-consumed",false);data.set("dungeon.recovery."+p.getUniqueId()+".gamemode",mode.name());data.set("dungeon.recovery."+p.getUniqueId()+".run",get(run,"id").toString());temporaryRuns.add(runPath());
            activated().addAll(List.of(1,2,3,4));cleared().addAll(List.of(1,2,3,4));set(run,"stage",4);set(run,"checkpoint",true);
            ((Set<Object>)get(run,"sideOwners")).add(p.getUniqueId());data.set(runPath()+".side."+p.getUniqueId(),true);
            Class<? extends Enum> boonClass=(Class<? extends Enum>)Class.forName("kr.yuumi97.core.dungeon.DungeonManager$Boon");EnumMap boon=new EnumMap(boonClass);boon.put(Enum.valueOf(boonClass,"GOLD"),1);((Map)get(run,"boons")).put(p.getUniqueId(),boon);
            for(int i=1;i<=4;i++){Object gate=((Map<?,?>)get(run,"gates")).get(i);invoke(gate,"open",new Class<?>[]{long.class},clock()-40);invoke(gate,"tick",new Class<?>[]{long.class},clock());}
            p.setGameMode(GameMode.ADVENTURE);p.setAllowFlight(true);p.setFlying(true);prime();position(DungeonLayout.checkpoint(world,slot));
        }
        @SuppressWarnings("unchecked")Set<Integer> activated()throws Exception{return (Set<Integer>)get(run,"activated");}
        @SuppressWarnings("unchecked")Set<Integer> cleared()throws Exception{return (Set<Integer>)get(run,"cleared");}
        boolean gateOpen(int index)throws Exception{Object gate=((Map<?,?>)get(run,"gates")).get(index);return (Boolean)invoke(gate,"isOpen",new Class<?>[]{});}
        long clock()throws Exception{return (Long)get(dungeon,"ticks");}
        String runPath()throws Exception{return "dungeon.runs."+get(run,"id");}
        Location point(double x,double y,double z){return new Location(world,slot*1024+x+.5,y,z+.5,180,0);}
        void position(Location location)throws Exception{invoke(dungeon,"teleport",new Class<?>[]{Player.class,Location.class},p,location);p.setFallDistance(0);}
        double pool(){return p.getHealth()+p.getAbsorptionAmount();}
        void prime(){p.setFireTicks(0);p.setNoDamageTicks(0);p.getAttribute(Attribute.MAX_ABSORPTION).setBaseValue(100);p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());p.setAbsorptionAmount(100);p.setFoodLevel(20);p.setSaturation(20);}
        void interact(String kind,int index)throws Exception{
            Entity target=null;for(UUID id:(Set<UUID>)get(run,"entities")){Entity entity=Bukkit.getEntity(id);if(entity instanceof Interaction&&kind.equals(entity.getPersistentDataContainer().get(new NamespacedKey(core,"dungeon_kind"),PersistentDataType.STRING))&&entity.getPersistentDataContainer().getOrDefault(new NamespacedKey(core,"dungeon_index"),PersistentDataType.INTEGER,-1)==index){target=entity;break;}}
            if(target==null)throw new AssertionError("Missing actual interaction "+kind+"/"+index);position(target.getLocation().clone().add(0,0,2));
            check(p.getLocation().distanceSquared(target.getLocation())<=9,"fixture is within actual interaction reach");PlayerInteractEntityEvent event=new PlayerInteractEntityEvent(p,target,EquipmentSlot.HAND);Bukkit.getPluginManager().callEvent(event);check(event.isCancelled(),"actual "+kind+" interaction handled by dungeon listener");
        }
        boolean worldCover(Location from,Location to){Vector delta=to.toVector().subtract(from.toVector());RayTraceResult ray=world.rayTraceBlocks(from,delta.clone().normalize(),delta.length(),FluidCollisionMode.NEVER,true);return ray!=null&&ray.getHitBlock()!=null&&ray.getHitBlock().getType().isOccluding();}
        void check(boolean value,String name){if(!value)throw new AssertionError(name);passed.add(name);}

        @EventHandler(priority=EventPriority.MONITOR)
        public void observe(EntityDamageEvent event){
            if(event.getEntity().equals(p)&&measured!=null&&!event.isCancelled()&&event.getDamage()>0)pulseEvents++;
            if(event instanceof EntityDamageByEntityEvent hit&&hit.getDamager().equals(p)&&event.getEntity() instanceof LivingEntity target&&dungeon.isBoss(target)){
                approvedBossDamage.put(target.getUniqueId(),dungeon.acceptedDamage(event));cancelledBossDamage.put(target.getUniqueId(),event.isCancelled());
            }
        }
        @Override public void run(){
            if(cleaning||result.isDone())return;
            try{
                if(!p.isOnline()||p.isDead())throw new AssertionError("Fixture player disconnected or died");
                if(Bukkit.getCurrentTick()-tickStarted>2100)throw new AssertionError("Scenario exceeded105server seconds");
                if(current==null){current=steps.pollFirst();if(current==null){finish(null);return;}stepStarted=Bukkit.getCurrentTick();current.start.run();}
                if(current.ready.ready()){current.verify.run();passed.add(current.name);current=null;}
                else if(Bukkit.getCurrentTick()-stepStarted>current.timeout)throw new AssertionError("Timed out: "+current.name+" state="+dungeon.snapshot(p.getUniqueId()));
            }catch(Throwable ex){finish(new AssertionError((current==null?"scenario":current.name)+": "+ex,unwrap(ex)));}
        }

        void cleanupRun()throws Exception{
            if(run==null)return;String id=get(run,"id").toString();invoke(dungeon,"endRun",new Class<?>[]{runClass,String.class,boolean.class},run,"Disposable scenario cleanup",false);
            check(((Set<?>)get(run,"chunks")).isEmpty()&&((Set<?>)get(run,"entities")).isEmpty(),"chapter"+chapter+" releases actors and ticket ownership");
            for(Entity entity:world.getEntities())check(!id.equals(entity.getPersistentDataContainer().get(new NamespacedKey(core,"dungeon_run"),PersistentDataType.STRING)),"no scenario-owned entity remains");
            data.set("dungeon.runs."+id,null);run=null;mob=null;boss=null;
        }
        void finish(Throwable failure){
            if(cleaning)return;cleaning=true;try{cancel();}catch(IllegalStateException ignored){}HandlerList.unregisterAll(this);Throwable error=failure;
            try{
                cleanupRun();for(Object leftover:fixtures)if(!((Boolean)get(leftover,"ended")))invoke(dungeon,"endRun",new Class<?>[]{runClass,String.class,boolean.class},leftover,"Scenario final cleanup",false);
                for(String path:temporaryRuns)data.set(path,null);
                rpg.combatSkills().clear(p,RpgDomain.COMBAT);rpg.combatSkills().clear(p,RpgDomain.LIFE);rpg.lifeSkills().clear(p,RpgDomain.LIFE);
                savedData.forEach((path,branch)->branch.restore(data,path));
                p.closeInventory();p.getInventory().setContents(copy(inventory));p.getInventory().setHeldItemSlot(held);p.setItemOnCursor(cursor.clone());
                p.setFlying(false);p.setAllowFlight(allowFlight);p.setGameMode(mode);if(flying&&allowFlight)p.setFlying(true);p.setSneaking(sneaking);p.setInvulnerable(invulnerable);
                invoke(dungeon,"teleport",new Class<?>[]{Player.class,Location.class},p,original);restorePlayerMaps(rpg.combatSkills(),combatState);restorePlayerMaps(rpg.lifeSkills(),lifeState);
                p.getActivePotionEffects().forEach(e->p.removePotionEffect(e.getType()));effects.forEach(p::addPotionEffect);rpg.combatSkills().refreshStats(p);
                p.getAttribute(Attribute.MAX_ABSORPTION).setBaseValue(maxAbsorptionBase);p.setHealth(Math.min(health,p.getAttribute(Attribute.MAX_HEALTH).getValue()));p.setAbsorptionAmount(Math.min(absorption,p.getAttribute(Attribute.MAX_ABSORPTION).getValue()));
                p.setFoodLevel(food);p.setSaturation(saturation);p.setExhaustion(exhaustion);p.setLevel(level);p.setExp(xp);p.setTotalExperience(totalXp);p.setFireTicks(fireTicks);p.setNoDamageTicks(noDamageTicks);p.setFallDistance(fallDistance);
                statistics.forEach(p::setStatistic);parentPresence.forEach((path,existed)->{ConfigurationSection section=data.getConfigurationSection(path);if(!existed&&section!=null&&section.getKeys(false).isEmpty())data.set(path,null);});core.saveDataFile();
                check(!dungeon.isParticipant(p),"scenario restores participant map");
            }catch(Throwable cleanup){if(error==null)error=cleanup;else error.addSuppressed(cleanup);}finally{RUNNING.set(false);}
            if(error!=null)result.completeExceptionally(error);else result.complete(Map.of("success",true,"checks",List.copyOf(passed),"count",passed.size(),"chapters",List.copyOf(chapters),"durationSeconds",(System.currentTimeMillis()-wallStarted)/1000.0,"restored",true));
        }
        @SuppressWarnings("unchecked")void capturePlayerMaps(Object owner,Map<String,Object> saved,List<String> names)throws Exception{for(String name:names)try{Map<Object,Object> map=(Map<Object,Object>)get(owner,name);saved.put(name,cloneValue(map.get(p.getUniqueId())));}catch(NoSuchFieldException ignored){}}
        @SuppressWarnings("unchecked")void clearPlayerMaps(Object owner,Map<String,Object> saved)throws Exception{for(String name:saved.keySet())((Map<Object,Object>)get(owner,name)).remove(p.getUniqueId());}
        @SuppressWarnings("unchecked")void restorePlayerMaps(Object owner,Map<String,Object> saved)throws Exception{for(var entry:saved.entrySet()){Map<Object,Object> map=(Map<Object,Object>)get(owner,entry.getKey());map.remove(p.getUniqueId());if(entry.getValue()!=null)map.put(p.getUniqueId(),cloneValue(entry.getValue()));}}
    }
    private static Object get(Object owner,String name)throws ReflectiveOperationException{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private static void set(Object owner,String name,Object value)throws ReflectiveOperationException{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);field.set(owner,value);}
    private static Object invoke(Object owner,String name,Class<?>[] types,Object...args)throws ReflectiveOperationException{Method method=owner.getClass().getDeclaredMethod(name,types);method.setAccessible(true);return method.invoke(owner,args);}
    private static Throwable unwrap(Throwable error){return error instanceof InvocationTargetException target&&target.getCause()!=null?target.getCause():error;}
    private static ItemStack[] copy(ItemStack[] source){ItemStack[] out=new ItemStack[source.length];for(int i=0;i<source.length;i++)out[i]=source[i]==null?null:source[i].clone();return out;}
    @SuppressWarnings({"unchecked","rawtypes"})private static Object cloneValue(Object value){if(value instanceof ItemStack item)return item.clone();if(value instanceof EnumMap map)return map.clone();if(value instanceof Map<?,?> map){Map<Object,Object> copy=new LinkedHashMap<>();map.forEach((k,v)->copy.put(k,cloneValue(v)));return copy;}if(value instanceof List<?> list)return new ArrayList<>(list.stream().map(DungeonScenarioCheck::cloneValue).toList());if(value instanceof Set<?> set)return new HashSet<>(set);return value;}
}
