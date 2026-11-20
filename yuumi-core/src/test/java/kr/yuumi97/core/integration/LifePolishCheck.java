package kr.yuumi97.core.integration;

import kr.yuumi97.core.YuumiPlugin;
import kr.yuumi97.core.rpg.*;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Disposable Paper observations for native fishing luck, EMF catch sizes and all six life HUDs. */
public final class LifePolishCheck {
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    public static CompletableFuture<Map<String,Object>> run(YuumiPlugin core,Player player){
        Path world=core.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        if(!world.endsWith(Path.of("yuumi-core","build","server-integration")))return CompletableFuture.failedFuture(new IllegalStateException("Disposable server-integration required"));
        if(!RUNNING.compareAndSet(false,true))return CompletableFuture.failedFuture(new IllegalStateException("Life polish fixture already running"));
        CompletableFuture<Map<String,Object>> result=new CompletableFuture<>();
        Runnable start=()->{Check check=null;try{check=new Check(core,player,result);check.start();}catch(Throwable error){if(check==null){RUNNING.set(false);result.completeExceptionally(error);}else check.finish(error);}};
        if(Bukkit.isPrimaryThread())start.run();else Bukkit.getScheduler().runTask(core,start);
        return result;
    }
    private static final class Check {
        final YuumiPlugin core;final RpgJobManager r;final Player p;final CompletableFuture<Map<String,Object>> result;
        final List<String> checks=new ArrayList<>();final Map<String,String> hud=new LinkedHashMap<>();final Map<String,Object> profile=new LinkedHashMap<>();
        final Location original;final GameMode mode;final boolean sneaking;final ItemStack hand;final List<PotionEffect> effects;final double health;
        final NamespacedKey testLuck;final Object oldBuffs;final Location center;boolean finished,changed;double ordinaryLuck;
        Check(YuumiPlugin core,Player p,CompletableFuture<Map<String,Object>> result)throws Exception{
            this.core=core;this.r=core.rpg();this.p=p;this.result=result;original=p.getLocation().clone();mode=p.getGameMode();sneaking=p.isSneaking();health=p.getHealth();hand=p.getInventory().getItemInMainHand().clone();effects=new ArrayList<>(p.getActivePotionEffects());
            ConfigurationSection section=r.data.getConfigurationSection(r.root(p));if(section!=null)section.getValues(true).forEach((key,value)->{if(!(value instanceof ConfigurationSection))profile.put(key,copy(value));});
            Object value=map(r.combatSkills(),"buffs").get(p.getUniqueId());oldBuffs=value instanceof Map<?,?> m?new HashMap<>(m):null;
            testLuck=new NamespacedKey(core,"fixture_life_luck");center=Objects.requireNonNull(Bukkit.getWorld("yuumi_hub")).getSpawnLocation().clone();
        }
        void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks.add(message);}
        void later(Runnable work){Bukkit.getScheduler().runTaskLater(core,()->{try{if(!p.isOnline())throw new IllegalStateException("Fixture player disconnected");work.run();}catch(Throwable error){finish(error);}},6);}
        void profile(LifeProfession life){r.lifeSkills().clear(p,RpgDomain.LIFE);String root=r.root(p);r.data.set(root+".active_life",life.name());String path=r.path(p,RpgDomain.LIFE);r.data.set(path+".level",20);r.data.set(path+".spec","A");r.data.set(path+".perks",null);r.data.set(path+".cooldowns",null);p.getInventory().setItemInMainHand(core.items().create(life.itemId));}
        void start()throws Exception{
            for(Object zone:(List<?>)field(r.lifeSkills(),"zones")){Method owner=zone.getClass().getDeclaredMethod("owner");owner.setAccessible(true);if(owner.invoke(zone).equals(p))throw new IllegalStateException("Finish existing life zones before fixture");}
            changed=true;p.setGameMode(GameMode.SURVIVAL);p.setSneaking(false);p.teleport(center);
            String[] token={"연쇄","재파종","밑밥","원목","명장","★★★"};int index=0;
            for(LifeProfession life:LifeProfession.values()){profile(life);String text=r.lifeSkills().hudText(p);hud.put(life.name(),text);check(text.contains(life.display)&&text.contains(token[index++])&&text.contains("[F]")&&text.contains("READY"),life+" HUD exposes actual profession efficiency and named ready skills");if(life!=LifeProfession.MINER)check(!text.contains("연쇄"),life+" HUD does not report miner ore chain");r.lifeSkills().hud(p);}
            profile(LifeProfession.ANGLER);p.setSneaking(true);r.lifeSkills().interact(p,null,null);p.setSneaking(false);r.lifeSkills().beginFishing(p);r.lifeSkills().beginFishing(p);
            check(r.lifeSkills().hudText(p).contains("밑밥 3회"),"Recasting without a bite does not spend the three bait charges");
            r.lifeSkills().biteFishing(p);check(r.lifeSkills().hudText(p).contains("밑밥 2회")&&r.lifeSkills().fishingSizeWeight(p)>1,"Accepted bite spends one charge and enables native big-fish weighting");
            emfSizes();
            var luck=p.getAttribute(Attribute.LUCK);check(luck!=null&&luck.getModifier(testLuck)==null,"Independent luck fixture key is unused");luck.addTransientModifier(new AttributeModifier(testLuck,2,AttributeModifier.Operation.ADD_NUMBER));ordinaryLuck=luck.getValue();
            Method zone=LifeSkillManager.class.getDeclaredMethod("fishZone",Player.class,Location.class,double.class,int.class);zone.setAccessible(true);zone.invoke(r.lifeSkills(),p,center,10.0,25);
            later(()->{double extra=r.lifeSkills().fishingTreasureLuck(p);check(extra>0&&near(p.getAttribute(Attribute.LUCK).getValue(),ordinaryLuck+extra),"Golden fishing zone adds real native loot-context luck on top of existing luck");check(r.lifeSkills().hudText(p).contains("보물 행운 +"),"ANGLER HUD observes active golden treasure state");p.teleport(center.clone().add(15,0,0));later(this::outside);});
        }
        void outside(){check(near(p.getAttribute(Attribute.LUCK).getValue(),ordinaryLuck)&&p.getAttribute(Attribute.LUCK).getModifier(testLuck)!=null,"Leaving the golden zone removes only its own luck modifier");p.teleport(center);later(()->{check(p.getAttribute(Attribute.LUCK).getValue()>ordinaryLuck,"Re-entering golden zone restores native treasure luck");r.lifeSkills().clear(p,RpgDomain.LIFE);check(near(p.getAttribute(Attribute.LUCK).getValue(),ordinaryLuck),"Life cleanup immediately removes native treasure luck");finish(null);});}
        void emfSizes()throws Exception{
            var emf=Objects.requireNonNull(Bukkit.getPluginManager().getPlugin("EvenMoreFish"),"Installed EMF required");ClassLoader loader=emf.getClass().getClassLoader();Class<?> api=Class.forName("com.oheers.fish.api.fishing.items.IFish",true,loader),rarity=Class.forName("com.oheers.fish.api.fishing.items.IRarity",true,loader),manager=Class.forName("com.oheers.fish.fishing.items.FishManager",true,loader),eventType=Class.forName("com.oheers.fish.api.events.EMFFishCaughtEvent",true,loader);
            Object target=manager.getMethod("getInstance").invoke(null),source=null;Method length=api.getMethod("getLength"),set=api.getMethod("setLength",Float.class);double min=0,max=0;
            for(Object tier:((Map<?,?>)manager.getMethod("getRarityMap").invoke(target)).values())for(Object fish:(List<?>)rarity.getMethod("getOriginalFishList").invoke(tier)){
                double low=((Number)api.getMethod("getMinSize").invoke(fish)).doubleValue(),high=((Number)api.getMethod("getMaxSize").invoke(fish)).doubleValue();
                if(source==null&&low>=0&&high>low+1&&((Optional<?>)api.getMethod("getSetSize").invoke(fish)).isEmpty()){source=fish;min=low;max=high;}
            }
            check(source!=null,"Installed EMF exposes a ranged-size fish species");float sourceLength=((Number)length.invoke(source)).floatValue();Object caught=api.getMethod("createCopy").invoke(source);api.getMethod("setFisherman",org.bukkit.OfflinePlayer.class).invoke(caught,p);api.getMethod("init").invoke(caught);float before=(float)((min+max)/2);set.invoke(caught,before);
            Constructor<?> constructor=eventType.getConstructor(api,Player.class,LocalDateTime.class);Event event=(Event)constructor.newInstance(caught,p,LocalDateTime.now());Bukkit.getPluginManager().callEvent(event);float after=((Number)length.invoke(caught)).floatValue();
            check(after>before&&after>=min&&after<=max+.00001,"Native EMF caught event increases a baited fish within species bounds");check(Float.compare(((Number)length.invoke(source)).floatValue(),sourceLength)==0,"Catch size change leaves the global EMF species template untouched");
            check(api.getMethod("give").invoke(caught) instanceof ItemStack,"EMF still creates its native item from the adjusted per-catch fish");
            ((Cancellable)event).setCancelled(true);set.invoke(caught,before);Bukkit.getPluginManager().callEvent(event);check(Float.compare(((Number)length.invoke(caught)).floatValue(),before)==0,"Cancelled native EMF catches receive no size boost");
            // Delegate every native fish method except the two authored constraints under test.
            for(boolean fixed:new boolean[]{true,false}){final Object delegate=caught;float expected=fixed?before:-1f;set.invoke(delegate,expected);Object constrained=Proxy.newProxyInstance(loader,new Class<?>[]{api},(proxy,method,args)->method.getName().equals("getSetSize")?(fixed?Optional.of((double)expected):Optional.empty()):method.invoke(delegate,args));
                Event restricted=(Event)constructor.newInstance(constrained,p,LocalDateTime.now());HandlerList handlers=(HandlerList)eventType.getMethod("getHandlerList").invoke(null);for(var listener:handlers.getRegisteredListeners())if(listener.getListener().getClass().getName().equals("kr.yuumi97.core.rpg.FishingRarityBridge"))listener.callEvent(restricted);
                check(Float.compare(((Number)length.invoke(delegate)).floatValue(),expected)==0,fixed?"Authored fixed-size fish remain unchanged":"Lengthless fish remain unchanged");}
        }
        void finish(Throwable error){if(finished)return;finished=true;if(!changed){RUNNING.set(false);result.completeExceptionally(error==null?new IllegalStateException("Fixture never started"):error);return;}try{
            r.lifeSkills().clear(p,RpgDomain.LIFE);var luck=p.getAttribute(Attribute.LUCK);if(luck!=null)luck.removeModifier(testLuck);
            r.data.set(r.root(p),null);profile.forEach((key,value)->r.data.set(r.root(p)+"."+key,value));Map<Object,Object> buffs=map(r.combatSkills(),"buffs");buffs.remove(p.getUniqueId());if(oldBuffs!=null)buffs.put(p.getUniqueId(),oldBuffs);
            if(p.isOnline()){p.getInventory().setItemInMainHand(hand);p.setGameMode(mode);p.setSneaking(sneaking);p.teleport(original);p.getActivePotionEffects().forEach(effect->p.removePotionEffect(effect.getType()));effects.forEach(p::addPotionEffect);r.lifeSkills().refreshFishingLuck(p);r.combatSkills().refreshStats(p);p.setHealth(Math.min(health,p.getAttribute(Attribute.MAX_HEALTH).getValue()));}core.saveDataFile();
        }catch(Throwable cleanup){if(error==null)error=cleanup;else error.addSuppressed(cleanup);}finally{RUNNING.set(false);}
            if(error!=null)result.completeExceptionally(error);else result.complete(Map.of("success",true,"checks",checks,"count",checks.size(),"hud",hud));
        }
    }
    private static boolean near(double a,double b){return Math.abs(a-b)<.00001;}
    private static Object field(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    @SuppressWarnings("unchecked")private static Map<Object,Object> map(Object object,String name)throws Exception{return (Map<Object,Object>)field(object,name);}
    private static Object copy(Object value){if(value instanceof ItemStack item)return item.clone();if(value instanceof List<?> list)return list.stream().map(LifePolishCheck::copy).toList();if(value instanceof Map<?,?> map){Map<Object,Object> result=new LinkedHashMap<>();map.forEach((key,item)->result.put(key,copy(item)));return result;}return value;}
}
