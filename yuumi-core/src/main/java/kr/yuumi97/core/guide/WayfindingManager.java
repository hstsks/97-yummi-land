package kr.yuumi97.core.guide;

import kr.yuumi97.core.hub.HubWorldManager;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Each route's bar, model and particles are visible only to its owner. */
public final class WayfindingManager {
    private record Route(String title, Location target, BossBar bar, ItemDisplay arrow) {}
    private final JavaPlugin plugin;
    private final HubWorldManager hub;
    private final Map<UUID,Route> routes=new HashMap<>();
    private BukkitTask ticker;
    public WayfindingManager(JavaPlugin plugin,HubWorldManager hub){this.plugin=plugin;this.hub=hub;}
    public void start(){if(ticker==null)ticker=Bukkit.getScheduler().runTaskTimer(plugin,this::tick,0,4);}
    public boolean isActive(Player player){return routes.containsKey(player.getUniqueId());}
    public void start(Player player,String title,Location target){
        stop(player);
        if(target==null||target.getWorld()==null||!player.getWorld().equals(target.getWorld()))return;
        ItemStack item=new ItemStack(Material.ARROW);
        if(hub.hasBundledModel("prop_wayfinder_arrow")){item=new ItemStack(Material.PAPER);item.editMeta(m->m.setItemModel(new NamespacedKey("yuumi97","prop_wayfinder_arrow")));}
        ItemStack rendered=item;
        ItemDisplay arrow=player.getWorld().spawn(player.getEyeLocation(),ItemDisplay.class,e->{
            e.setVisibleByDefault(false);e.setPersistent(false);e.setItemStack(rendered);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);e.setTeleportDuration(2);
            e.setInterpolationDelay(0);e.setInterpolationDuration(4);
            e.setTransformation(new Transformation(new Vector3f(),new Quaternionf().rotateY((float)Math.PI),new Vector3f(.7f),new Quaternionf()));
            e.setBrightness(new Display.Brightness(15,15));
        });
        player.showEntity(plugin,arrow);
        BossBar bar=Bukkit.createBossBar("🧭 "+title,BarColor.PINK,BarStyle.SOLID);bar.addPlayer(player);
        routes.put(player.getUniqueId(),new Route(title,target.clone(),bar,arrow));
        player.setCompassTarget(target);tick();
    }
    public void stop(Player player){remove(player.getUniqueId());}
    public void stop(){if(ticker!=null){ticker.cancel();ticker=null;}for(UUID id:new ArrayList<>(routes.keySet()))remove(id);}
    private void remove(UUID id){Route r=routes.remove(id);if(r!=null){r.bar.removeAll();r.arrow.remove();}}
    public static double relativeAngle(double lookX,double lookZ,double targetX,double targetZ){
        return Math.toDegrees(Math.atan2(lookX*targetZ-lookZ*targetX,lookX*targetX+lookZ*targetZ));
    }
    public static String direction(double angle){
        double a=Math.abs(angle);if(a<22.5)return "▲ 정면";if(a>=157.5)return "▼ 뒤쪽";
        if(a<67.5)return angle>0?"↗ 우측 전방":"↖ 좌측 전방";
        if(a<112.5)return angle>0?"▶ 우측":"◀ 좌측";
        return angle>0?"↘ 우측 뒤":"↙ 좌측 뒤";
    }
    private void tick(){
        for(var entry:new ArrayList<>(routes.entrySet())){
            Player p=Bukkit.getPlayer(entry.getKey());Route route=entry.getValue();
            if(p==null||!p.isOnline()||p.isDead()||p.getGameMode()==GameMode.SPECTATOR||!p.getWorld().equals(hub.getHubWorld())||!p.getWorld().equals(route.target.getWorld())||!route.arrow.isValid()){remove(entry.getKey());continue;}
            double distance=p.getLocation().distance(route.target);
            if(distance<=4.5){p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_CHIME,.8f,1.6f);p.sendMessage(TextUtil.msg("<green>🧭 "+route.title+"에 도착했습니다!"));remove(entry.getKey());continue;}
            Vector to=route.target.toVector().subtract(p.getLocation().toVector());Vector facing=p.getLocation().getDirection();
            String dir=direction(relativeAngle(facing.getX(),facing.getZ(),to.getX(),to.getZ()));
            route.bar.setTitle("🧭 [길잡이] "+route.title+" · "+Math.round(distance)+"m  ("+dir+")");route.bar.setProgress(Math.min(1,Math.max(.02,4.5/distance)));
            Location eye=p.getEyeLocation();Vector forward=eye.getDirection().normalize();
            var block=p.getWorld().rayTraceBlocks(eye,forward,2.4,FluidCollisionMode.NEVER,true);
            double ahead=block==null?2.4:Math.max(.4,Math.min(2.4,block.getHitPosition().distance(eye.toVector())-.3));
            Location at=eye.clone().add(forward.multiply(ahead)).subtract(0,.35,0);
            Vector aim=route.target.clone().add(0,1,0).toVector().subtract(at.toVector());if(aim.lengthSquared()>.001)at.setDirection(aim);
            route.arrow.teleport(at);
            Vector ribbon=to.clone().setY(0);if(ribbon.lengthSquared()>.001){ribbon.normalize();for(int n=1;n<=6;n++){
                Location dot=p.getLocation().add(ribbon.clone().multiply(n*.55)).add(0,.15,0);
                p.spawnParticle(n%2==0?Particle.CHERRY_LEAVES:Particle.END_ROD,dot,1,.03,.02,.03,0);
            }}
        }
    }
}
