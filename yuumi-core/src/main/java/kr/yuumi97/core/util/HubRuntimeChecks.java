package kr.yuumi97.core.util;

import kr.yuumi97.core.hub.HubWorldManager;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import java.util.*;

/** World-backed hub checks, shared by the administrator command and boot verifier. */
public final class HubRuntimeChecks {
    private HubRuntimeChecks() {}

    public static String run(JavaPlugin plugin, HubWorldManager hub, FileConfiguration data) {
        check(!data.getString("hub.blueprint_hash", "").isBlank(), "Public hub incomplete");
        World world=Objects.requireNonNull(hub.getHubWorld(),"Hub world missing");
        for(int x=-3;x<=5;x++)for(int z=-3;z<=0;z++)world.getChunkAt(x,z).load();
        NamespacedKey roleKey=new NamespacedKey(plugin,"hub_npc_role");
        NamespacedKey dummyKey=new NamespacedKey(plugin,"yuumi_training_dummy");
        List<LivingEntity> dummies=world.getLivingEntities().stream()
                .filter(e->e.getPersistentDataContainer().has(dummyKey)).toList();
        check(dummies.size()==3,"Expected exactly 3 training dummies, got "+dummies.size());
        var collisionTeam=Bukkit.getScoreboardManager().getMainScoreboard().getTeam("yuumi97_dummies");
        check(collisionTeam!=null&&collisionTeam.getOption(org.bukkit.scoreboard.Team.Option.COLLISION_RULE)
                ==org.bukkit.scoreboard.Team.OptionStatus.NEVER,"Training collision team missing");
        for(LivingEntity dummy:dummies)check(dummy.isCollidable()&&collisionTeam.hasEntry(dummy.getUniqueId().toString()),
                "Training projectile collision disabled");
        double[][] anchors={{-6,65,-40.5},{-6,65,-30.5},{-6,65,-22.5}};
        for(double[] anchor:anchors)check(dummies.stream().anyMatch(e->e.getLocation().distanceSquared(
                new Location(world,anchor[0],anchor[1],anchor[2]))<.01),"Training dummy anchor missing "+Arrays.toString(anchor));
        int[][] pockets={{-36,-8,-44,-24},{-37,-7,23,34},{7,36,23,34},{8,28,-44,-24},{26,41,-36,-7}};
        for(int i=0;i<pockets.length;i++) {
            int[] area=pockets[i];int bare=0,total=0;
            for(int x=area[0];x<=area[1];x++)for(int z=area[2];z<=area[3];z++) {
                total++;if(world.getBlockAt(x,64,z).getType()!=Material.GRASS_BLOCK)continue;
                boolean empty=true;for(int y=65;y<=70;y++)if(!world.getBlockAt(x,y,z).getType().isAir()){empty=false;break;}
                if(empty)bare++;
            }
            check(bare<=total/10,"Unfinished bare grass in plaza pocket "+(i+1)+": "+bare+"/"+total);
        }
        for(int[] gate:new int[][]{{0,-26},{0,26},{-26,0},{26,0}})
            for(int d=-3;d<=3;d++)for(int y=65;y<=68;y++) {
                int x=gate[0]+(gate[0]==0?d:0),z=gate[1]+(gate[0]==0?0:d);
                check(world.getBlockAt(x,y,z).isPassable(),"Gate walkway obstructed at "+x+","+y+","+z);
            }
        for(Cat cat:world.getEntitiesByClass(Cat.class)) {
            String role=cat.getPersistentDataContainer().get(roleKey,PersistentDataType.STRING);
            if(role==null)continue;
            double scale=Set.of("casino_cashier","cat_race","casino_blackjack","casino_roulette",
                    "casino_blackjack_vip","stock_broker").contains(role)?1.35:1.28;
            check(cat.getAttribute(Attribute.SCALE)!=null&&Math.abs(cat.getAttribute(Attribute.SCALE).getValue()-scale)<.001,"NPC scale "+role);
            List<Interaction> targets=world.getEntitiesByClass(Interaction.class).stream()
                    .filter(e->role.equals(e.getPersistentDataContainer().get(roleKey,PersistentDataType.STRING))).toList();
            check(targets.size()==1,"Expected one interaction relay for "+role+", got "+targets.size());
            Interaction relay=targets.getFirst();
            check(relay.getLocation().distanceSquared(cat.getLocation())<.01&&Math.abs(relay.getInteractionWidth()-1.35)<.001
                    &&Math.abs(relay.getInteractionHeight()-2)<.001&&relay.isResponsive(),"Interaction geometry "+role);
        }
        for (String role : List.of("workshop_smith", "workshop_vault", "workshop_craft"))
            check(world.getEntitiesByClass(Cat.class).stream().filter(cat -> role.equals(cat.getPersistentDataContainer().get(roleKey,PersistentDataType.STRING))).count()==1,"Workshop NPC "+role);
        Map<String,Location> facilities=Map.of(
                "cat_race",new Location(world,52.5,66,-15),
                "gacha",hub.getGachaLocation(),"hall_of_fame",hub.getHallOfFameLocation());
        for(var facility:facilities.entrySet()) {
            List<Cat> cats=world.getEntitiesByClass(Cat.class).stream().filter(cat->facility.getKey().equals(
                    cat.getPersistentDataContainer().get(roleKey,PersistentDataType.STRING))).toList();
            check(cats.size()==1&&cats.getFirst().getLocation().distanceSquared(facility.getValue())<.01,"Facility position "+facility.getKey());
        }
        Map<String,Integer> mounts=Map.of("prop_casino_marquee",2,"prop_casino_chandelier",1,
                "prop_dealer_throne",6,"prop_training_dummy",3,"prop_sakura_lantern_arch",4);
        for(var mount:mounts.entrySet())if(hub.hasBundledModel(mount.getKey())) {
            NamespacedKey model=new NamespacedKey("yuumi97",mount.getKey());
            long count=world.getEntitiesByClass(ItemDisplay.class).stream().filter(display->display.getItemStack().hasItemMeta()
                    &&model.equals(display.getItemStack().getItemMeta().getItemModel())).count();
            check(count==mount.getValue(),"Landmark count "+mount.getKey()+": "+count+" instead of "+mount.getValue());
        }
        String[] roles={"casino_cashier","casino_blackjack","casino_roulette","casino_blackjack_vip","stock_broker"};
        double[][] eyes={{63.5,66.6,-9.5},{77.5,66.6,-16},{77.5,66.6,-21},{77.5,72.6,-22},{52.5,72.6,-30.5}};
        double[][] positions={{63.5,66,-12},{80.5,66,-16},{80.5,66,-21},{80.5,72,-22},{52.5,72,-33}};
        for(int i=0;i<roles.length;i++) {
            String role=roles[i];
            List<Cat> cats=world.getEntitiesByClass(Cat.class).stream()
                    .filter(e->role.equals(e.getPersistentDataContainer().get(roleKey,PersistentDataType.STRING))).toList();
            check(cats.size()==1,"Expected one cat for "+role);
            Cat cat=cats.getFirst();double[] p=positions[i],eye=eyes[i];
            check(cat.getLocation().distanceSquared(new Location(world,p[0],p[1],p[2]))<.01,"Dealer position "+role);
            Location start=new Location(world,eye[0],eye[1],eye[2]);
            Vector delta=cat.getLocation().add(0,.5,0).toVector().subtract(start.toVector());
            var blocked=world.rayTraceBlocks(start,delta.clone().normalize(),delta.length(),FluidCollisionMode.NEVER,true);
            check(blocked==null,"Dealer sightline blocked for "+role+" at "+(blocked==null?"":blocked.getHitBlock()));
            plugin.getLogger().info("CORE_RAYTRACE PASS "+role);
        }
        String result="CORE_HUB_CHECKS PASS: blueprint_hash=ready; training_dummies=3; dealer_rays=5/5; NPC scales and interaction relays";
        plugin.getLogger().info(result);
        return result;
    }

    private static void check(boolean condition,String message) {
        if(!condition)throw new IllegalStateException("Hub verification: "+message);
    }
}
