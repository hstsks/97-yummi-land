package kr.yuumi97.core.dungeon;

import org.bukkit.*;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.Structure;
import java.io.InputStream;
import java.util.*;

/** The blueprint contract, also used when an architecture delivery is absent. */
public final class DungeonLayout {
    public static final int STRIDE=1024, BASE_Y=60, FLOOR_Y=66;
    public record Zone(int number,int width,int height,int length,int north) {
        public int south(){return north+length-1;}
        public int half(){return (width-1)/2;}
    }
    public static final List<Zone> ZONES=List.of(new Zone(1,31,24,28,-28),new Zone(2,39,28,38,-66),
        new Zone(3,31,28,42,-108),new Zone(4,37,28,36,-144),new Zone(5,33,26,32,-176),new Zone(6,45,32,45,-221));
    private final JavaPlugin plugin;
    private final Map<String,Structure> templates=new HashMap<>();
    public DungeonLayout(JavaPlugin plugin){this.plugin=plugin;}
    public void load(){
        for(int chapter=1;chapter<=3;chapter++)for(Zone z:ZONES){
            String id="ch"+chapter+"_zone"+z.number();
            try(InputStream in=plugin.getResource("structures/overhaul/"+id+".nbt")){
                if(in==null)continue;
                Structure s=Bukkit.getStructureManager().loadStructure(in);
                if(s.getSize().getBlockX()!=z.width()||s.getSize().getBlockY()!=z.height()||s.getSize().getBlockZ()!=z.length())
                    throw new IllegalArgumentException("size does not match spine contract");
                validateTemplate(s,z);
                templates.put(id,s);
            }catch(Exception ex){plugin.getLogger().warning("[Dungeon] "+id+": procedural fallback: "+ex.getMessage());}
        }
    }
    public int templateCount(){return templates.size();}
    private static void validateTemplate(Structure structure,Zone zone){
        if(structure.getEntityCount()!=0||structure.getPaletteCount()<1)throw new IllegalArgumentException("empty palettes/entities contract");
        Set<Material> forbidden=EnumSet.of(Material.TNT,Material.SPAWNER,Material.TRIAL_SPAWNER,Material.COMMAND_BLOCK,Material.CHAIN_COMMAND_BLOCK,
            Material.REPEATING_COMMAND_BLOCK,Material.JIGSAW,Material.STRUCTURE_BLOCK,Material.STRUCTURE_VOID,Material.BARRIER);
        for(var palette:structure.getPalettes()){
            if(palette.getBlockCount()!=zone.width()*zone.height()*zone.length())throw new IllegalArgumentException("explicit AIR cells required");
            Map<Integer,Material> seams=new HashMap<>();
            for(var state:palette.getBlocks()){
                int x=state.getX(),y=state.getY(),z=state.getZ();Material material=state.getType();
                if(x<0||x>=zone.width()||y<0||y>=zone.height()||z<0||z>=zone.length()||forbidden.contains(material))throw new IllegalArgumentException("unsafe palette cell: "+material);
                if(state instanceof org.bukkit.block.Container&&!(zone.number()==3&&material==Material.CHEST&&x==4&&y==7&&z==18))throw new IllegalArgumentException("unauthorized template inventory");
                if((z==0||z==zone.length()-1)&&Math.abs(x-zone.half())<=4&&y>=6&&y<=14)seams.put((x*zone.height()+y)*zone.length()+z,material);
            }
            for(int z:new int[]{0,zone.length()-1})if(!(zone.number()==1&&z==zone.length()-1)&&!(zone.number()==6&&z==0))
                for(int x=zone.half()-4;x<=zone.half()+4;x++)for(int y=6;y<=14;y++){
                    Material material=seams.get((x*zone.height()+y)*zone.length()+z);
                    if(material==null||(y==6?!material.isSolid():!material.isAir()))throw new IllegalArgumentException("9x8 gate seam blocked");
                }
        }
    }
    public static int zoneAt(double z){for(Zone zone:ZONES)if(z>=zone.north()&&z<zone.south()+1)return zone.number();return 0;}
    public static Location spawn(World world,int slot){return new Location(world,slot*STRIDE+.5,67,-6.5,180,0);}
    public static Location checkpoint(World world,int slot){return new Location(world,slot*STRIDE+.5,68,-137.5,180,0);}
    public static boolean contains(int slot,Location at){
        return Math.abs(at.getX()-(slot*STRIDE+.5))<=22.5&&at.getZ()>=-221&&at.getZ()<0&&at.getY()>=60&&at.getY()<94;
    }
    public void place(World world,int slot,int chapter){
        int cx=slot*STRIDE;
        for(Zone z:ZONES){
            Structure s=templates.get("ch"+chapter+"_zone"+z.number());
            if(s==null)fallback(world,cx,chapter,z);
            else s.place(new Location(world,cx-z.half(),60,z.north()),false,org.bukkit.block.structure.StructureRotation.NONE,
                org.bukkit.block.structure.Mirror.NONE,0,1f,new Random(97));
        }
        // The two sides of every seam share the exact 9x8 airway, independent of decorations.
        for(int boundary:new int[]{-28,-66,-108,-144,-176})for(int dz:new int[]{0,-1})for(int x=-4;x<=4;x++){
            block(world,cx+x,66,boundary+dz,Material.POLISHED_DEEPSLATE);
            for(int y=67;y<=74;y++)block(world,cx+x,y,boundary+dz,Material.AIR);
        }
        // Placed leaves must not decay during a long run.
        for(Zone z:ZONES)for(int x=cx-z.half();x<=cx+z.half();x++)for(int zz=z.north();zz<=z.south();zz++)
            for(int y=60;y<60+z.height();y++)if(world.getBlockAt(x,y,zz).getBlockData() instanceof Leaves leaves){leaves.setPersistent(true);world.getBlockAt(x,y,zz).setBlockData(leaves,false);}
    }
    private static void block(World w,int x,int y,int z,Material m){w.getBlockAt(x,y,z).setType(m,false);}
    private void fallback(World w,int cx,int chapter,Zone z){
        Material stone=chapter==1?Material.DEEPSLATE_TILES:chapter==2?Material.POLISHED_BLACKSTONE_BRICKS:Material.QUARTZ_BRICKS;
        Material pillar=chapter==2?Material.POLISHED_BASALT:Material.QUARTZ_PILLAR;
        Material accent=chapter==1?Material.GOLD_BLOCK:chapter==2?Material.WAXED_COPPER_BLOCK:Material.AMETHYST_BLOCK;
        Material light=chapter==2?Material.SHROOMLIGHT:chapter==3?Material.PEARLESCENT_FROGLIGHT:Material.SEA_LANTERN;
        int h=z.half();
        for(int x=-h;x<=h;x++)for(int zz=z.north();zz<=z.south();zz++)for(int y=60;y<60+z.height();y++){
            int lx=x+h,lz=zz-z.north(),ly=y-60;boolean edge=Math.abs(x)==h||lz==0||lz==z.length()-1;
            Material m=Material.AIR;
            if(ly==0)m=Material.BEDROCK;
            else if(ly<6&&(Math.abs(x)>h-3||ly==1))m=chapter==2?Material.MAGMA_BLOCK:Material.BLACK_CONCRETE;
            else if(ly==6&&(z.number()!=3||Math.abs(x)<=3||Math.abs(x)>=h-2||lz==0||lz==z.length()-1))
                m=(lx+lz)%9==0?light:chapter==1&&Math.abs(x)<4?Material.CYAN_STAINED_GLASS:stone;
            else if(edge&&ly>=6)m=(lx+lz)%6==0?pillar:(ly%5==0?accent:stone);
            else if(ly==z.height()-1||ly>=z.height()-3&&Math.abs(x)>h-3)m=stone;
            else if(Math.abs(x)==h-2&&lz%7==0&&ly>6)m=ly%6==0?accent:pillar;
            else if(ly==8&&Math.abs(x)==h-1&&lz%7==3)m=light;
            block(w,cx+x,y,zz,m);
        }
        // Recessed wall panels, ribs and chandeliers give the fallback the same vertical language.
        for(int zz=z.north()+4;zz<z.south()-2;zz+=7){
            for(int side:new int[]{-1,1})for(int y=68;y<=72;y++)for(int d=-1;d<=1;d++)
                block(w,cx+side*(h-1),y,zz+d,chapter==1?Material.BOOKSHELF:chapter==2?Material.COPPER_GRATE:Material.PURPLE_STAINED_GLASS);
            for(int x=-h+2;x<=h-2;x++)block(w,cx+x,60+z.height()-3-Math.abs(x)/4,zz,pillar);
            for(int y=60+z.height()-6;y<60+z.height()-2;y++)block(w,cx,y,zz,Material.CHAIN);
            block(w,cx,60+z.height()-7,zz,light);
        }
        if(chapter==1){
            for(int x=-h+3;x<=h-3;x+=6)for(int zz=z.north()+3;zz<z.south()-2;zz+=7){block(w,cx+x,61,zz,Material.SEA_LANTERN);block(w,cx+x,62,zz,Material.END_ROD);}
            int center=(z.north()+z.south())/2;for(int x=-3;x<=3;x++)for(int zz=-3;zz<=3;zz++)if(x*x+zz*zz>=7&&x*x+zz*zz<=11)block(w,cx+x,60+z.height()-8,center+zz,Material.GOLD_BLOCK);
        }else if(chapter==2){
            for(int side:new int[]{-1,1})for(int zz:new int[]{z.north()+6,z.south()-6}){
                int shaft=cx+side*(h-3),top=60+z.height()-6;
                for(int y=62;y<=top;y++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
                    block(w,shaft+dx,y,zz+dz,dx==0&&dz==0?Material.LAVA:Material.ORANGE_STAINED_GLASS);
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(Math.abs(dx)==2||Math.abs(dz)==2)block(w,shaft+dx,top,zz+dz,Material.POLISHED_BLACKSTONE_BRICKS);
                for(int y=top+1;y<60+z.height()-1;y++)block(w,shaft,y,zz,Material.CHAIN);
                for(int dy=-2;dy<=2;dy++)for(int dz=-2;dz<=2;dz++)if(dy*dy+dz*dz>=3&&dy*dy+dz*dz<=5)block(w,cx+side*(h-1),71+dy,zz+dz,Material.WAXED_COPPER_BLOCK);
            }
        }else{
            for(int x=-h+4;x<=h-4;x+=7)for(int zz=z.north()+4;zz<z.south()-3;zz+=9){block(w,cx+x,62,zz,Material.AMETHYST_BLOCK);block(w,cx+x,63,zz,Material.END_ROD);}
            for(int side:new int[]{-1,1})for(int zz=z.north()+5;zz<z.south()-4;zz+=8){block(w,cx+side*(h-4),77,zz,Material.MAGENTA_STAINED_GLASS_PANE);block(w,cx+side*(h-5),79,zz+1,Material.PURPLE_STAINED_GLASS_PANE);}
        }
        if(z.number()==2){
            for(int side:new int[]{-1,1}){
                for(int x=9;x<=h-2;x++)for(int zz=z.north()+3;zz<=z.south()-5;zz++)block(w,cx+side*x,74,zz,stone);
                // Eight full-width steps connect feetY67 to feetY75.
                for(int step=0;step<8;step++)for(int dx=-1;dx<=1;dx++){
                    for(int y=66;y<=66+step;y++)block(w,cx+side*11+dx,y,z.south()-6-step,stone);
                    var stair=(org.bukkit.block.data.type.Stairs)Material.QUARTZ_STAIRS.createBlockData();stair.setFacing(org.bukkit.block.BlockFace.NORTH);
                    w.getBlockAt(cx+side*11+dx,67+step,z.south()-6-step).setBlockData(stair,false);
                }
            }
        }
        if(z.number()==3){
            for(int x=-12;x<=-3;x++)for(int zz=-91;zz<=-87;zz++)block(w,cx+x,66,zz,stone);
            for(int x=-13;x<=-9;x++)for(int zz=-94;zz<=-87;zz++)block(w,cx+x,66,zz,stone);
            block(w,cx-11,67,-90,Material.CHEST);
        }
        if(z.number()==4)for(int x=-5;x<=5;x++)for(int zz=-141;zz<=-135;zz++)block(w,cx+x,67,zz,stone);
        if(z.number()==5)for(int x:new int[]{-9,9})for(int zz:new int[]{-167,-153}){
            block(w,cx+x,66,zz,accent);for(int y=67;y<=70;y++)block(w,cx+x,y,zz,Material.AIR);
        }
        if(z.number()==6){
            for(int x:new int[]{-10,10})for(int zz:new int[]{-208,-190})for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)for(int y=67;y<=82;y++)block(w,cx+x+dx,y,zz+dz,pillar);
            for(int x=-9;x<=9;x++)for(int y=71;y<=89;y++)if(x*x+(y-80)*(y-80)<=81)
                block(w,cx+x,y,-220,chapter==3?Material.MAGENTA_STAINED_GLASS:chapter==2?Material.OCHRE_FROGLIGHT:Material.CYAN_STAINED_GLASS);
        }
        // Open only valid connector faces; outermost walls remain closed.
        for(int zz:new int[]{z.north(),z.south()})if(!(z.number()==1&&zz==z.south())&&!(z.number()==6&&zz==z.north()))
            for(int x=-4;x<=4;x++)for(int y=67;y<=74;y++)block(w,cx+x,y,zz,Material.AIR);
    }
}
