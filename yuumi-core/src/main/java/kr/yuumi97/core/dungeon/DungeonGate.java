package kr.yuumi97.core.dungeon;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;
import java.util.function.Consumer;

/** Physical and visual gate share one state; the barrier remains until the rising door clears it. */
final class DungeonGate {
    final int z;private final World world;private final int cx;private final List<BlockDisplay> bars=new ArrayList<>();
    private boolean open,opening;private long release;
    DungeonGate(World world,int cx,int z,Consumer<Entity> tag){this.world=world;this.cx=cx;this.z=z;
        for(int x=-4;x<=4;x++){
            BlockDisplay b=world.spawn(new Location(world,cx+x+.3,67,z+.25),BlockDisplay.class);b.setBlock(Material.CRYING_OBSIDIAN.createBlockData());
            b.setPersistent(false);b.setInterpolationDelay(0);b.setInterpolationDuration(40);b.setTransformation(transform(0,new Vector3f(.4f,8,.5f)));tag.accept(b);bars.add(b);
        }close();
    }
    private Transformation transform(float y,Vector3f size){return new Transformation(new Vector3f(0,y,0),new Quaternionf(),size,new Quaternionf());}
    void close(){open=false;opening=false;
        for(int x=-4;x<=4;x++)for(int y=67;y<=74;y++)world.getBlockAt(cx+x,y,z).setType(Material.BARRIER,false);
        for(BlockDisplay b:bars){b.setInterpolationDuration(0);b.setTransformation(transform(0,new Vector3f(.4f,8,.5f)));}
    }
    void open(long tick){if(open||opening)return;opening=true;release=tick+40;
        world.playSound(new Location(world,cx,70,z),Sound.BLOCK_END_PORTAL_SPAWN,.7f,.7f);
        for(BlockDisplay b:bars){b.setInterpolationDelay(0);b.setInterpolationDuration(40);b.setTransformation(transform(8,new Vector3f(.4f,8,.5f)));}
    }
    void tick(long tick){if(opening&&tick>=release){opening=false;open=true;for(int x=-4;x<=4;x++)for(int y=67;y<=74;y++)world.getBlockAt(cx+x,y,z).setType(Material.AIR,false);}}
    boolean isOpen(){return open;}
    void remove(){bars.forEach(Entity::remove);}
}
