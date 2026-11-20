package kr.yuumi97.core.integration;

import kr.yuumi97.core.YuumiPlugin;
import kr.yuumi97.core.dungeon.*;
import kr.yuumi97.core.dungeon.mob.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real Paper geometry/rig check. Never runnable against the production world container. */
public final class DungeonWorldCheck {
    private static boolean running;
    private DungeonWorldCheck(){}
    private static Object get(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private static Object invoke(Object owner,String name,Class<?>[] signature,Object...args)throws Exception{Method m=owner.getClass().getDeclaredMethod(name,signature);m.setAccessible(true);return m.invoke(owner,args);}
    private static void check(boolean pass,String message){if(!pass)throw new IllegalStateException(message);}
    public static CompletableFuture<Map<String,Object>> run(YuumiPlugin core,Player player){
        if(!Bukkit.isPrimaryThread()||running)return CompletableFuture.failedFuture(new IllegalStateException("worldcheck requires the main thread and one exclusive fixture"));
        running=true;
        CompletableFuture<Map<String,Object>> result=new CompletableFuture<>();List<Object> fixtures=new ArrayList<>();
        result.whenComplete((value,error)->running=false);
        Map<String,Object> evidence=new LinkedHashMap<>();
        try{
            Path container=core.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();Path permitted=Path.of("E:/MinGem/yuumi-core/build/server-integration").toAbsolutePath().normalize();
            check(container.startsWith(permitted),"DungeonWorldCheck refuses a production/non-integration world container: "+container);
            DungeonManager manager=(DungeonManager)get(core,"dungeonManager");World world=Bukkit.getWorld(DungeonManager.WORLD_NAME);check(world!=null,"dungeon world missing");
            Map<?,?> live=(Map<?,?>)get(manager,"runs");Set<Integer> occupied=new HashSet<>();for(Object run:live.values())occupied.add((Integer)get(run,"slot"));
            Class<?> runClass=Class.forName("kr.yuumi97.core.dungeon.DungeonManager$Run");Constructor<?> constructor=runClass.getDeclaredConstructor(UUID.class,List.class,int.class,int.class);constructor.setAccessible(true);
            int bones=0,entities=0,seams=0;
            for(int chapter=1;chapter<=3;chapter++){
                int slot=49+chapter,cx=slot*DungeonLayout.STRIDE;check(!occupied.contains(slot),"scratch slot already occupied: "+slot);
                Object run=constructor.newInstance(player.getUniqueId(),List.of(player.getUniqueId()),chapter,slot);fixtures.add(run);
                invoke(manager,"buildRooms",new Class<?>[]{runClass},run);check(((Set<?>)get(run,"chunks")).size()==56,"expected56chunk tickets");
                check(world.getPluginChunkTickets().getOrDefault(core,List.of()).stream().filter(c->c.getX()>=Math.floorDiv(cx-22,16)&&c.getX()<=Math.floorDiv(cx+22,16)&&c.getZ()>=-14&&c.getZ()<=-1).count()==56,"native56chunk tickets missing");
                Map<?,?> gates=(Map<?,?>)get(run,"gates");check(gates.size()==5,"five physical gates");
                for(var zone:DungeonLayout.ZONES){
                    for(int z:new int[]{zone.north(),zone.south()})if(!(zone.number()==1&&z==zone.south())&&!(zone.number()==6&&z==zone.north()))
                        for(int x=-4;x<=4;x++){
                            check(world.getBlockAt(cx+x,66,z).getType().isSolid(),"seam floor "+chapter+"/"+zone.number());
                            for(int y=67;y<=74;y++){
                                boolean gatePlane=zone.number()<6&&z==zone.north();Material expected=gatePlane?Material.BARRIER:Material.AIR;
                                check(world.getBlockAt(cx+x,y,z).getType()==expected,"seam airway "+chapter+"/"+zone.number()+"/"+x+","+y+","+z);seams++;
                            }
                        }
                }
                for(int side:new int[]{-1,1}){
                    check(world.getBlockAt(cx+side*12,74,-55).getType().isSolid(),"gallery floorY74");
                    check(world.getBlockAt(cx+side*12,76,-55).isPassable(),"gallery headroom");
                    boolean stair=false;for(int z=-43;z<=-33;z++)for(int y=67;y<=74;y++)if(world.getBlockAt(cx+side*11,y,z).getType().name().endsWith("_STAIRS"))stair=true;
                    check(stair,"gallery stairs");
                }
                for(int z=-108;z<=-67;z++)for(int x=-3;x<=3;x++)check(world.getBlockAt(cx+x,66,z).getType().isSolid(),"seven-wide bridge");
                check(world.getBlockAt(cx+7,64,-85).isPassable(),"chasm air");check(world.getBlockAt(cx+7,60,-85).getType().isSolid(),"chasm safety floor");
                check(world.getBlockAt(cx,67,-138).getType().isSolid(),"checkpoint floorY67");
                for(int x:new int[]{-10,10})for(int z:new int[]{-208,-190})for(int y=67;y<=82;y++)check(world.getBlockAt(cx+x,y,z).getType().isOccluding(),"opaque cover column");
                for(MobType type:MobType.values())if(type.chapter==chapter){
                    LivingEntity base=(LivingEntity)invoke(manager,"enemy",new Class<?>[]{runClass,MobType.class,Location.class,int.class,boolean.class},run,type,new Location(world,cx+(type.ordinal()%6-3)*3+.5,67,-199.5),6,false);
                    check(base.isInvisible()&&base.isSilent()&&!base.isPersistent(),"pathfinding base flags "+type.id);
                    Object state=((Map<?,?>)get(run,"mobs")).get(base.getUniqueId());MonsterRig rig=(MonsterRig)get(state,"rig");
                    int expected=type.boss()?3:2;check(rig.partCount()==expected,"rig part count "+type.id);bones+=expected;entities++;
                    for(MonsterRig.Pose pose:MonsterRig.Pose.values()){rig.pose(pose,0,24);rig.tick(12,player,type.health(),type.health(),type.poise(),type.poise());}
                }
                String runId=get(run,"id").toString();long actualBones=world.getEntities().stream().filter(e->e instanceof ItemDisplay||e instanceof BlockDisplay)
                    .filter(e->runId.equals(e.getPersistentDataContainer().get(new NamespacedKey(core,"dungeon_run"),PersistentDataType.STRING)))
                    .filter(e->"rig".equals(e.getPersistentDataContainer().get(new NamespacedKey(core,"dungeon_kind"),PersistentDataType.STRING))).count();
                check(actualBones==14,"native14rig bones per chapter");
                for(Object gate:gates.values()){
                    invoke(gate,"open",new Class<?>[]{long.class},0L);int gateZ=(Integer)get(gate,"z");check(world.getBlockAt(cx,70,gateZ).getType()==Material.BARRIER,"gate barrier released before animation");
                    for(Object bar:(List<?>)get(gate,"bars"))check(((BlockDisplay)bar).getInterpolationDuration()==40,"gate does not interpolate for40ticks");
                }
            }
            evidence.put("chapters",3);evidence.put("zones",18);evidence.put("rigs",entities);evidence.put("bones",bones);evidence.put("seamCells",seams);evidence.put("ticketsPerSlot",56);
            evidence.put("blueprints",((DungeonLayout)get(manager,"layout")).templateCount());evidence.put("manifestRigs",((MonsterRig.Catalog)get(manager,"rigCatalog")).manifestCount());
            check(entities==18&&bones==42,"complete18/42catalog");
            new BukkitRunnable(){int elapsed;
                @Override public void run(){
                    try{
                        elapsed+=2;for(Object run:fixtures)for(Object gate:((Map<?,?>)get(run,"gates")).values())invoke(gate,"tick",new Class<?>[]{long.class},(long)elapsed);
                        if(elapsed<44)return;
                        for(Object run:fixtures){int cx=(Integer)get(run,"slot")*DungeonLayout.STRIDE;for(var z:DungeonLayout.ZONES)if(z.number()<6)for(int x=-4;x<=4;x++)for(int y=67;y<=74;y++)check(world.getBlockAt(cx+x,y,z.north()).getType().isAir(),"rising gate left physical barrier");}
                        cleanup(manager,fixtures,world,core);evidence.put("gateAnimationTicks",elapsed);evidence.put("cleanup",true);evidence.put("status","PASS");result.complete(Collections.unmodifiableMap(evidence));cancel();
                    }catch(Throwable ex){try{cleanup(manager,fixtures,world,core);}catch(Throwable suppressed){ex.addSuppressed(suppressed);}result.completeExceptionally(ex);cancel();}
                }
            }.runTaskTimer(core,2,2);
        }catch(Throwable ex){
            try{DungeonManager manager=(DungeonManager)get(core,"dungeonManager");cleanup(manager,fixtures,Bukkit.getWorld(DungeonManager.WORLD_NAME),core);}catch(Throwable suppressed){ex.addSuppressed(suppressed);}result.completeExceptionally(ex);
        }
        return result;
    }
    private static void cleanup(DungeonManager manager,List<Object> fixtures,World world,YuumiPlugin core)throws Exception{
        for(Object run:fixtures){Class<?> runClass=run.getClass();String id=get(run,"id").toString();
            int cx=(Integer)get(run,"slot")*DungeonLayout.STRIDE;
            invoke(manager,"endRun",new Class<?>[]{runClass,String.class,boolean.class},run,"Isolated dungeon geometry check cleanup",false);
            check(((Set<?>)get(run,"chunks")).isEmpty(),"chunk ticket ownership not released");check(((Set<?>)get(run,"entities")).isEmpty(),"entity ownership not cleared");
            if(world!=null)check(world.getPluginChunkTickets().getOrDefault(core,List.of()).stream().noneMatch(c->c.getX()>=Math.floorDiv(cx-22,16)&&c.getX()<=Math.floorDiv(cx+22,16)&&c.getZ()>=-14&&c.getZ()<=-1),"nativechunk ticket leaked");
            if(world!=null)for(Entity e:world.getEntities())check(!id.equals(e.getPersistentDataContainer().get(new NamespacedKey(core,"dungeon_run"),PersistentDataType.STRING)),"tagged entity leaked");
        }
        fixtures.clear();
    }
}
