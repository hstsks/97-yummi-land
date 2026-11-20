package kr.yuumi97.core.rpg;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Optional EMF 2.5 rarity-weight hook, discovered from the installed plugin's public API. */
final class FishingRarityBridge implements Listener {
    private static final String KEY="yuumi97_treasure_hunter";
    private final RpgJobManager r;
    private Object registry;
    private boolean sizeConnected;
    FishingRarityBridge(RpgJobManager r){this.r=r;}
    void start(){Plugin emf=Bukkit.getPluginManager().getPlugin("EvenMoreFish");if(emf!=null&&emf.isEnabled())connect(emf);}
    @EventHandler public void enabled(PluginEnableEvent event){if(event.getPlugin().getName().equals("EvenMoreFish"))connect(event.getPlugin());}
    private void connect(Plugin emf){connectSize(emf);if(registry!=null)return;try{
        ClassLoader loader=emf.getClass().getClassLoader();Class<?> boost=Class.forName("com.oheers.fish.api.boost.RarityWeightBoost",true,loader);
        Class<?> api=Class.forName("com.oheers.fish.api.boost.RarityBoostRegistry",true,loader);Object target=api.getMethod("getInstance").invoke(null);
        Object provider=Proxy.newProxyInstance(loader,new Class<?>[]{boost},(proxy,method,args)->switch(method.getName()){
            case "getKey","toString"->KEY;
            case "hashCode"->System.identityHashCode(proxy);
            case "equals"->proxy==args[0];
            case "weightMultiplier"->args[0] instanceof Player p&&rare(String.valueOf(args[2]))?r.lifeSkills().fishingWeight(p):1.0;
            default->throw new UnsupportedOperationException(method.getName());
        });
        api.getMethod("unregister",String.class).invoke(target,KEY);
        if(Boolean.TRUE.equals(api.getMethod("register",boost,boolean.class).invoke(target,provider,false))){registry=target;r.plugin.getLogger().info("[RPG] EvenMoreFish treasure-hunter rarity boost connected.");}
    }catch(ReflectiveOperationException|LinkageError e){r.plugin.getLogger().warning("[RPG] Optional fishing rarity bridge unavailable; native fishing treasure luck remains active.");}}
    private void connectSize(Plugin emf){if(sizeConnected)return;try{
        ClassLoader loader=emf.getClass().getClassLoader();Class<? extends Event> eventType=Class.forName("com.oheers.fish.api.events.EMFFishCaughtEvent",true,loader).asSubclass(Event.class);
        Class<?> fishType=Class.forName("com.oheers.fish.api.fishing.items.IFish",true,loader);
        Method player=eventType.getMethod("getPlayer"),fish=eventType.getMethod("getFish"),length=fishType.getMethod("getLength"),fixed=fishType.getMethod("getSetSize"),min=fishType.getMethod("getMinSize"),max=fishType.getMethod("getMaxSize"),set=fishType.getMethod("setLength",Float.class);
        Bukkit.getPluginManager().registerEvent(eventType,this,EventPriority.HIGHEST,(listener,event)->{
            try{Player p=(Player)player.invoke(event);double weight=r.lifeSkills().fishingSizeWeight(p);if(weight<=1)return;Object caught=fish.invoke(event);
                if(((Optional<?>)fixed.invoke(caught)).isPresent())return;
                float before=((Number)length.invoke(caught)).floatValue(),after=boostLength(before,((Number)min.invoke(caught)).doubleValue(),((Number)max.invoke(caught)).doubleValue(),weight);
                if(after>before)set.invoke(caught,after);
            }catch(ReflectiveOperationException e){throw new org.bukkit.event.EventException(e);}
        },r.plugin,true);
        sizeConnected=true;r.plugin.getLogger().info("[RPG] EvenMoreFish bounded big-catch size boost connected.");
    }catch(ReflectiveOperationException|LinkageError e){r.plugin.getLogger().warning("[RPG] Optional fishing size bridge unavailable: "+e.getClass().getSimpleName());}}
    /** Bias the native uniform percentile upward, retaining each species' authored size bounds. */
    static float boostLength(float current,double min,double max,double weight){
        if(!Float.isFinite(current)||!Double.isFinite(min)||!Double.isFinite(max)||!Double.isFinite(weight)||current<0||min<0||max<=min||current<min||current>max||weight<=1)return current;
        double quantile=(current-min)/(max-min),boosted=min+(max-min)*Math.pow(quantile,1/Math.clamp(weight,1,8));
        return (float)Math.clamp(Math.max(current,Math.round(boosted*10)/10.0),min,max);
    }
    private boolean rare(String rarity){List<String> configured=r.plugin.getConfig().getStringList("rpg.life.ANGLER.A-boosted-rarities");
        if(configured.isEmpty())configured=List.of("rare","epic","legendary","mythic","희귀","영웅","전설","신화");String name=rarity.toLowerCase(Locale.ROOT);
        return configured.stream().anyMatch(key->name.contains(key.toLowerCase(Locale.ROOT)));}
    void stop(){HandlerList.unregisterAll(this);sizeConnected=false;if(registry==null)return;try{registry.getClass().getMethod("unregister",String.class).invoke(registry,KEY);}catch(ReflectiveOperationException ignored){}registry=null;}
}
