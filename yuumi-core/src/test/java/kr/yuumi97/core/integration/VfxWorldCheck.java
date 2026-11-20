package kr.yuumi97.core.integration;

import kr.yuumi97.core.YuumiPlugin;
import kr.yuumi97.core.rpg.RpgDomain;
import kr.yuumi97.core.rpg.RpgJobManager;
import kr.yuumi97.core.rpg.SkillVfxChoreographer;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.scheduler.BukkitRunnable;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Authored-pack ItemDisplay checks against the running animation clock, not a second orientation implementation. */
public final class VfxWorldCheck {
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    private VfxWorldCheck(){}
    @FunctionalInterface private interface Action {void run()throws Exception;}
    private record Step(String name,int delay,Action action){}
    public static CompletableFuture<Map<String,Object>> run(YuumiPlugin core,Player player){
        Path container=core.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        Path allowed=Path.of("E:/MinGem/yuumi-core/build/server-integration").toAbsolutePath().normalize();
        if(!container.startsWith(allowed))return CompletableFuture.failedFuture(new IllegalStateException("Disposable server-integration world container required"));
        if(!RUNNING.compareAndSet(false,true))return CompletableFuture.failedFuture(new IllegalStateException("VFX fixture already running"));
        CompletableFuture<Map<String,Object>> result=new CompletableFuture<>();
        Runnable start=()->{Check check=null;try{check=new Check(core,player,result);check.start();}catch(Throwable ex){if(check!=null)check.finish(ex);else{RUNNING.set(false);result.completeExceptionally(ex);}}};
        if(Bukkit.isPrimaryThread())start.run();else Bukkit.getScheduler().runTask(core,start);return result;
    }
    private static final class Check {
        final YuumiPlugin core;final Player p;final RpgJobManager rpg;final SkillVfxChoreographer vfx;final CompletableFuture<Map<String,Object>> result;
        final Location original,origin;final Set<UUID> displays=new HashSet<>();final List<String> passed=new ArrayList<>();final List<Map<String,Object>> samples=new ArrayList<>();
        final Deque<Step> steps=new ArrayDeque<>();final List<ItemDisplay> circles=new ArrayList<>();final AtomicReference<Location> path=new AtomicReference<>();
        final AtomicBoolean visible=new AtomicBoolean(true);ItemDisplay moving,tracked;Location before;Vector3f lastDirection;boolean cleaning;int checks;long started=System.currentTimeMillis();
        Check(YuumiPlugin core,Player p,CompletableFuture<Map<String,Object>> result)throws Exception{
            if(p==null||!p.isOnline()||!RpgJobManager.usable(p))throw new IllegalStateException("A usable online fixture player is required");
            this.core=core;this.p=p;this.result=result;rpg=core.rpg();vfx=rpg.vfx();original=p.getLocation().clone();origin=original.clone().add(7,4,7);origin.setYaw(123);origin.setPitch(-61);
            if(ownerAnimations()!=0)throw new IllegalStateException("Fixture owner must have no pre-existing VFX animations");
            for(String model:List.of("vfx_magic_circle_arcane","vfx_magic_circle_sanctuary","vfx_astral_bow_sigil","vfx_golden_fishing_vortex","vfx_star_arrow_volley","vfx_holy_spear","vfx_titan_greatsword_drop","vfx_arcane_meteor"))
                if(!rpg.hasModel(model))throw new IllegalStateException("Authored-pack VFX definition required: "+model);
        }
        void start(){
            stationary();linear("arrow diagonal","vfx_star_arrow_volley",new Vector3f(6,3,-8),new Vector3f(0,0,-1),0);
            linear("arrow vertical up","vfx_star_arrow_volley",new Vector3f(0,8,0),new Vector3f(0,0,-1),0);
            linear("arrow vertical down","vfx_star_arrow_volley",new Vector3f(0,-8,0),new Vector3f(0,0,-1),0);
            linear("holy spear vertical down","vfx_holy_spear",new Vector3f(0,-8,0),new Vector3f(0,-1,0),0);
            linear("holy spear vertical up","vfx_holy_spear",new Vector3f(0,8,0),new Vector3f(0,-1,0),0);
            linear("titan diagonal drop","vfx_titan_greatsword_drop",new Vector3f(3,-9,-2),new Vector3f(0,-1,0),0);
            linear("meteor diagonal spinning","vfx_arcane_meteor",new Vector3f(-6,-14,-6),new Vector3f(0,-1,0),.06f);
            actualDrops();tracking();
            step("short animation expires",1,()->{moving=remember(vfx.play(p,RpgDomain.COMBAT,"vfx_star_arrow_volley",origin,origin.clone().add(2,0,0),4,1,1,0));});
            step("actual clock expiry cleanup",6,()->{check(!moving.isValid(),"animation lifetime removes native ItemDisplay");check(ownerAnimations()==0,"expired animation leaves no owner clock entry");});
            step("finish",1,()->finish(null));advance();
        }
        void stationary(){
            step("stationary circles create",1,()->{
                look(180,76);int i=0;for(String model:List.of("vfx_magic_circle_arcane","vfx_magic_circle_sanctuary","vfx_astral_bow_sigil","vfx_golden_fishing_vortex")){
                    Location at=origin.clone().add(i++*2,0,0);ItemDisplay circle=spawned(()->vfx.circle(p,RpgDomain.COMBAT,model,at,3,30));circles.add(circle);metadata(circle,model);
                }
            });
            for(int sample=0;sample<3;sample++){final int number=sample;step("stationary XZ sample"+sample,3,()->{
                look(37+number*101,number==1?-89:82);
                for(ItemDisplay circle:circles){check(circle.isValid(),"stationary circle survives owner look change");Vector3f normal=circle.getTransformation().getLeftRotation().transform(new Vector3f(0,1,0));normal.normalize();
                    check(Math.abs(normal.y)> .99999f,"stationary circle preserves horizontal XZ plane");check(Math.abs(circle.getLocation().getY()-(origin.getY()+.05))<.001,"stationary circle keeps fixed floor height");zeroEntityRotation(circle);}
            });}
            step("stationary clear",1,()->{vfx.clear(p,RpgDomain.COMBAT);for(ItemDisplay circle:circles)check(!circle.isValid(),"clear removes actual stationary display");check(ownerAnimations()==0,"stationary clear removes clock entries");});
        }
        void linear(String label,String model,Vector3f displacement,Vector3f tip,float spin){
            step(label+" create",1,()->{look(211,67);Location to=origin.clone().add(displacement.x,displacement.y,displacement.z);to.setYaw(47);to.setPitch(81);
                moving=remember(vfx.play(p,RpgDomain.COMBAT,model,origin,to,30,.6f,.6f,spin));before=moving.getLocation().clone();metadata(moving,model);});
            step(label+" actual movement",3,()->{look(17,-83);Location current=moving.getLocation();Vector3f actual=delta(before,current);aligned(moving,tip,actual,label);check(actual.lengthSquared()>.00001f,label+" moves in the real world");before=current.clone();});
            step(label+" independent owner look",3,()->{look(303,88);Location current=moving.getLocation();Vector3f actual=delta(before,current);aligned(moving,tip,actual,label+" after owner look");before=current.clone();});
            step(label+" clear",1,()->{vfx.clear(p,RpgDomain.COMBAT);check(!moving.isValid(),label+" clears native display");check(ownerAnimations()==0,label+" clears owner clock entry");});
        }
        void actualDrops(){
            step("public meteor drop",1,()->{moving=spawned(()->vfx.drop(p,"vfx_arcane_meteor",origin,10,20));before=moving.getLocation().clone();metadata(moving,"vfx_arcane_meteor");check(Math.abs(before.getX()-(origin.getX()-3.5))<.001&&Math.abs(before.getZ()-(origin.getZ()-3.5))<.001,"meteor drop starts at actual diagonal drift");});
            step("public meteor trajectory",4,()->{Vector3f actual=delta(before,moving.getLocation());check(actual.x>0&&actual.y<0&&actual.z>0,"public meteor follows diagonal descending trajectory");aligned(moving,new Vector3f(0,-1,0),actual,"public meteor drop");vfx.clear(p,RpgDomain.COMBAT);});
            step("public holy spear drop",1,()->{moving=spawned(()->vfx.drop(p,"vfx_holy_spear",origin,10,20));before=moving.getLocation().clone();});
            step("public holy spear trajectory",4,()->{Vector3f actual=delta(before,moving.getLocation());check(Math.abs(actual.x)<.001&&actual.y<0&&Math.abs(actual.z)<.001,"public holy spear falls vertically");aligned(moving,new Vector3f(0,-1,0),actual,"public holy spear drop");vfx.clear(p,RpgDomain.COMBAT);});
        }
        void tracking(){
            step("guided arrow create",1,()->{look(15,65);path.set(origin.clone());visible.set(true);tracked=spawned(()->vfx.track(p,"vfx_star_arrow_volley",origin,()->path.get().clone(),visible::get,180,.5f));metadata(tracked,"vfx_star_arrow_volley");});
            Vector3f[] routes={new Vector3f(5,0,0),new Vector3f(0,0,-4),new Vector3f(-3,0,3),new Vector3f(0,6,0),new Vector3f(0,-8,0),new Vector3f(3,2,4),new Vector3f(-6,-3,-2)};
            for(int i=0;i<routes.length;i++){final int index=i;Vector3f route=new Vector3f(routes[i]);
                step("guided turn"+i,1,()->{before=tracked.getLocation().clone();Location target=path.get().clone().add(route.x,route.y,route.z);target.setYaw(190+index);target.setPitch(54);path.set(target);look(333-index*39,index%2==0?88:-88);});
                step("guided actual quaternion"+i,2,()->{check(tracked.getLocation().distanceSquared(path.get())<.00001,"guided arrow follows supplier position "+index);Vector3f actual=delta(before,tracked.getLocation());aligned(tracked,new Vector3f(0,0,-1),actual,"guided turn"+index);lastDirection=new Vector3f(actual).normalize();});
            }
            step("guided owner look without path movement",1,()->{before=tracked.getLocation().clone();look(123,89);});
            step("guided retained trajectory direction",3,()->{check(tracked.getLocation().distanceSquared(before)<.00001,"guided arrow remains still when path has no delta");aligned(tracked,new Vector3f(0,0,-1),lastDirection,"guided stationary path retains last travel direction");});
            step("guided visibility predicate false",1,()->visible.set(false));
            step("guided predicate native cleanup",2,()->{check(!tracked.isValid(),"invisible tracked projectile removes native display");check(ownerAnimations()==0,"tracked projectile predicate removes clock entry");});
            step("explicit lifecycle clear create",1,()->{moving=remember(vfx.play(p,RpgDomain.COMBAT,"vfx_star_arrow_volley",origin,origin.clone().add(-10,2,3),120,.5f,.5f,0));});
            step("explicit lifecycle native cleanup",2,()->{check(moving.isValid(),"long-lived display exists before clear");vfx.clear(p,RpgDomain.COMBAT);check(!moving.isValid()&&ownerAnimations()==0,"explicit clear removes live display and clock state");});
        }
        void metadata(ItemDisplay display,String model){
            check(display.isValid()&&!display.isPersistent(),"native VFX display is transient");check(display.getItemDisplayTransform()==ItemDisplay.ItemDisplayTransform.NONE,"native VFX uses NONE transform");
            check(display.getItemStack().getItemMeta().getItemModel()!=null&&display.getItemStack().getItemMeta().getItemModel().getKey().equals(model),"native display carries authored model "+model);
            check(display.getInterpolationDuration()==2&&display.getTeleportDuration()==2,"native display retains interpolation and teleport smoothing");zeroEntityRotation(display);
        }
        void aligned(ItemDisplay display,Vector3f localTip,Vector3f actual,String label){
            check(display.isValid(),label+" native display remains valid");check(actual.isFinite()&&actual.lengthSquared()>.0000001f,label+" has finite nonzero travel direction");
            Transformation transformation=display.getTransformation();Quaternionf rotation=transformation.getLeftRotation();Vector3f tip=rotation.transform(new Vector3f(localTip)).normalize();Vector3f direction=new Vector3f(actual).normalize();float dot=tip.dot(direction);
            check(dot>.99999f,label+" model tip aligns with actual entity delta");zeroEntityRotation(display);
            Vector3f scale=transformation.getScale();check(scale.isFinite()&&scale.x>0&&Math.abs(scale.x-scale.y)<.00001&&Math.abs(scale.y-scale.z)<.00001,label+" preserves finite isotropic scale");
            samples.add(Map.of("label",label,"dot",dot,"tip",List.of(tip.x,tip.y,tip.z),"direction",List.of(direction.x,direction.y,direction.z),"entityYaw",display.getLocation().getYaw(),"entityPitch",display.getLocation().getPitch()));
        }
        void zeroEntityRotation(ItemDisplay display){Location at=display.getLocation();check(Math.abs(at.getYaw())<.0001&&Math.abs(at.getPitch())<.0001,"entity yaw/pitch remain zero; quaternion owns orientation");}
        void look(float yaw,float pitch){Location at=p.getLocation();at.setYaw(yaw);at.setPitch(pitch);check(p.teleport(at),"fixture owner look updates successfully");}
        ItemDisplay remember(ItemDisplay display){check(display!=null,"authored-pack call creates actual ItemDisplay");displays.add(display.getUniqueId());return display;}
        ItemDisplay spawned(Action action)throws Exception{Set<UUID> before=new HashSet<>();for(ItemDisplay display:p.getWorld().getEntitiesByClass(ItemDisplay.class))before.add(display.getUniqueId());action.run();List<ItemDisplay> created=p.getWorld().getEntitiesByClass(ItemDisplay.class).stream().filter(d->!before.contains(d.getUniqueId())).toList();check(created.size()==1,"public void VFX call spawns exactly one native display");return remember(created.getFirst());}
        int ownerAnimations()throws Exception{int count=0;for(Object animation:(List<?>)field(vfx,"active"))if(p.equals(field(animation,"owner")))count++;return count;}
        void check(boolean condition,String label){checks++;if(!condition)throw new AssertionError(label);passed.add(label);}
        void step(String name,int delay,Action action){steps.add(new Step(name,delay,action));}
        void advance(){if(cleaning||result.isDone())return;Step step=steps.pollFirst();if(step==null){finish(null);return;}Bukkit.getScheduler().runTaskLater(core,()->{try{if(!p.isOnline()||!RpgJobManager.usable(p))throw new AssertionError("Fixture player became unusable");step.action.run();if(!cleaning)advance();}catch(Throwable ex){finish(new AssertionError(step.name+": "+ex,ex));}},Math.max(1,step.delay));}
        void finish(Throwable failure){if(cleaning)return;cleaning=true;Throwable error=failure;
            try{vfx.clear(p,RpgDomain.COMBAT);vfx.clear(p,RpgDomain.LIFE);for(UUID id:displays){Entity display=Bukkit.getEntity(id);check(display==null||!display.isValid(),"lifecycle leaves no created native ItemDisplay");}check(ownerAnimations()==0,"lifecycle leaves zero fixture-owner animations");
                check(p.teleport(original),"fixture restores original owner location/look");
            }catch(Throwable cleanup){if(error==null)error=cleanup;else error.addSuppressed(cleanup);}finally{RUNNING.set(false);}
            if(error!=null)result.completeExceptionally(error);else result.complete(Map.of("success",true,"count",checks,"checks",List.copyOf(passed),"samples",List.copyOf(samples),"nativeDisplays",displays.size(),"durationSeconds",(System.currentTimeMillis()-started)/1000.0,"restored",true));
        }
    }
    private static Object field(Object owner,String name)throws ReflectiveOperationException{Field field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);}
    private static Vector3f delta(Location from,Location to){return new Vector3f((float)(to.getX()-from.getX()),(float)(to.getY()-from.getY()),(float)(to.getZ()-from.getZ()));}
}
