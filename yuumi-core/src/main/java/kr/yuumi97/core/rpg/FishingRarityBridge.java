package kr.yuumi97.core.rpg;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;

/** Optional EMF 2.5 rarity-weight hook, discovered from the installed plugin's public API. */
final class FishingRarityBridge implements Listener {
    private static final String KEY="yuumi97_treasure_hunter";
    private final RpgJobManager r;
    private Object registry;
    FishingRarityBridge(RpgJobManager r){this.r=r;}
    void start(){Plugin emf=Bukkit.getPluginManager().getPlugin("EvenMoreFish");if(emf!=null&&emf.isEnabled())connect(emf);}
    @EventHandler public void enabled(PluginEnableEvent event){if(event.getPlugin().getName().equals("EvenMoreFish"))connect(event.getPlugin());}
    private void connect(Plugin emf){if(registry!=null)return;try{
        ClassLoader loader=emf.getClass().getClassLoader();Class<?> boost=Class.forName("com.oheers.fish.api.boost.RarityWeightBoost",true,loader);
        Class<?> api=Class.forName("com.oheers.fish.api.boost.RarityBoostRegistry",true,loader);Object target=api.getMethod("getInstance").invoke(null);
        Object provider=Proxy.newProxyInstance(loader,new Class<?>[]{boost},(proxy,method,args)->switch(method.getName()){
            case "getKey","toString"->KEY;
            case "hashCode"->System.identityHashCode(proxy);
            case "equals"->proxy==args[0];
            case "weightMultiplier"->args[0] instanceof Player p&&r.getJob(p)==JobType.ANGLER&&r.getCombat().buff(p,"treasure")&&rare(String.valueOf(args[2]))?r.cfg("rpg.skills.ANGLER.A-rarity-multiplier",2):1.0;
            default->throw new UnsupportedOperationException(method.getName());
        });
        api.getMethod("unregister",String.class).invoke(target,KEY);
        if(Boolean.TRUE.equals(api.getMethod("register",boost,boolean.class).invoke(target,provider,false))){registry=target;r.plugin.getLogger().info("[RPG] EvenMoreFish treasure-hunter rarity boost connected.");}
    }catch(ReflectiveOperationException|LinkageError e){r.plugin.getLogger().warning("[RPG] Optional fishing rarity bridge unavailable; vanilla Luck V remains active.");}}
    private boolean rare(String rarity){List<String> configured=r.plugin.getConfig().getStringList("rpg.skills.ANGLER.A-boosted-rarities");
        if(configured.isEmpty())configured=List.of("rare","epic","legendary","mythic","희귀","영웅","전설","신화");String name=rarity.toLowerCase(Locale.ROOT);
        return configured.stream().anyMatch(key->name.contains(key.toLowerCase(Locale.ROOT)));}
    void stop(){if(registry==null)return;try{registry.getClass().getMethod("unregister",String.class).invoke(registry,KEY);}catch(ReflectiveOperationException ignored){}registry=null;}
}
