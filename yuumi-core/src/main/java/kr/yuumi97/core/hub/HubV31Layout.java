package kr.yuumi97.core.hub;

import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.Lootable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.structure.Structure;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;

/** Public-city construction only. All block writes retain the host's full-height owned-column guard. */
final class HubV31Layout {
    private final HubWorldManager hub;
    private final JavaPlugin plugin;
    private final NamespacedKey fallbackKey;
    private Map<String, Structure> blueprints = Map.of();
    private String appliedFingerprint = "";

    // Local placement manifest. External files are optional, and cannot alter these placement bounds.
    private static final Map<String, int[]> CONTRACT = Map.ofEntries(
            Map.entry("casino_palace_shell", new int[]{51, 29, 35}),
            Map.entry("bellagio_fountain_court", new int[]{12, 9, 30}),
            Map.entry("plaza_clocktower_guild", new int[]{27, 27, 21}),
            Map.entry("hall_of_fame_pavilion", new int[]{18, 16, 18}),
            Map.entry("waterfront_bakery_cafe", new int[]{25, 14, 11}),
            Map.entry("waterfront_sakura_garden", new int[]{28, 13, 11}),
            Map.entry("plaza_welcome_gate", new int[]{11, 12, 5}),
            Map.entry("grand_sakura_tree_1", new int[]{11, 14, 11}),
            Map.entry("grand_sakura_tree_2", new int[]{11, 15, 11}));

    private static final int[][] TREES = {{-23,-17},{22,-16},{-21,18},{21,17},{-23,25},{-12,27},
            {9,24},{19,23},{-35,-42},{-85,-28},{-83,31},{36,15},{26,10}};
    private static final int[][] OLD_TREES = {{-21,-19},{21,-19},{-21,19},{21,19},{23,8},{23,20},
            {9,24},{19,24},{-23,22},{-32,-28},{-85,-28},{-83,31},{-29,28},{37,24}};
    private static final Set<Material> UNSAFE_TEMPLATE_BLOCKS = Set.of(Material.JIGSAW, Material.STRUCTURE_BLOCK,
            Material.STRUCTURE_VOID, Material.SPAWNER, Material.TRIAL_SPAWNER, Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK, Material.BARRIER);

    HubV31Layout(HubWorldManager hub, JavaPlugin plugin) {
        this.hub = hub;
        this.plugin = plugin;
        fallbackKey = new NamespacedKey(plugin, "hub_v31_fallback");
    }

    void beginBuild() {
        Snapshot snapshot = snapshot();
        blueprints = snapshot.templates;
        appliedFingerprint = snapshot.fingerprint;
    }

    String sourceFingerprint() {
        Snapshot snapshot = snapshot();
        blueprints = snapshot.templates;
        return snapshot.fingerprint;
    }
    String appliedFingerprint() { return appliedFingerprint; }

    private record Snapshot(Map<String, Structure> templates, String fingerprint) {}

    private Snapshot snapshot() {
        Map<String, Structure> templates = new HashMap<>();
        StringBuilder signature = new StringBuilder("sol-v31-layout-1;");
        List<String> ids = new ArrayList<>(CONTRACT.keySet());
        Collections.sort(ids);
        Path source = sourceDirectory("structures-v31");
        for (String id : ids) {
            try {
                byte[] bytes = null;
                if (source != null && Files.isRegularFile(source.resolve(id + ".nbt"))) {
                    bytes = Files.readAllBytes(source.resolve(id + ".nbt"));
                } else try (InputStream in = plugin.getResource("structures/v31/" + id + ".nbt")) {
                    if (in != null) bytes = in.readAllBytes();
                }
                if (bytes == null) { signature.append(id).append("=SOL;"); continue; }
                Structure structure = Bukkit.getStructureManager().loadStructure(new ByteArrayInputStream(bytes));
                int[] dimensions = CONTRACT.get(id);
                var size = structure.getSize();
                boolean valid = size.getBlockX() == dimensions[0] && size.getBlockY() == dimensions[1]
                        && size.getBlockZ() == dimensions[2] && structure.getPaletteCount() > 0
                        && validServiceVolumes(id, structure);
                signature.append(id).append(valid ? "=NBT:" : "=REJECTED:")
                        .append(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))).append(';');
                if (valid) templates.put(id, structure);
            } catch (Exception incompleteAsset) {
                // Astra may be writing the file concurrently. Leave this zone on the complete SOL fallback.
                signature.append(id).append("=INCOMPLETE;");
            }
        }
        return new Snapshot(Map.copyOf(templates), signature.toString());
    }

    private Path sourceDirectory(String leaf) {
        Path directory = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        for (int i = 0; directory != null && i < 7; i++, directory = directory.getParent()) {
            Path candidate = directory.resolve("assets/yuumi97-production").resolve(leaf);
            if (Files.isDirectory(candidate)) return candidate;
        }
        return null;
    }

    private boolean validServiceVolumes(String id, Structure template) {
        int[] declared = CONTRACT.get(id);
        for (var palette : template.getPalettes()) for (var state : palette.getBlocks()) {
            int x = state.getX(), y = state.getY(), z = state.getZ();
            if (x < 0 || y < 0 || z < 0 || x >= declared[0] || y >= declared[1] || z >= declared[2]) return false;
            if (!state.getType().isSolid()) continue;
            if (id.equals("hall_of_fame_pavilion") && x >= 1 && x <= 16 && z >= 15 && y >= 1 && y <= 4) return false;
            if (id.equals("bellagio_fountain_court") && x <= 2 && y >= 1 && y <= 4) return false;
            if (id.equals("plaza_welcome_gate") && x >= 2 && x <= 8 && y >= 1 && y <= 4) return false;
            if (id.equals("plaza_clocktower_guild") && x >= 13 && x <= 25 && z >= 16 && y >= 1 && y <= 4) return false;
        }
        return true;
    }

    void clearOldTrees(World world) {
        for (int[][] group : new int[][][]{OLD_TREES, TREES}) for (int[] tree : group) {
            for (int x = tree[0] - 6; x <= tree[0] + 6; x++) for (int z = tree[1] - 6; z <= tree[1] + 6; z++) {
                if (hub.isOwnedColumn(world, x, z)) continue;
                for (int y = 65; y <= 81; y++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (type == Material.CHERRY_LOG || type == Material.CHERRY_WOOD || type == Material.CHERRY_LEAVES
                            || type == Material.PINK_PETALS || type == Material.SPORE_BLOSSOM) b(world,x,y,z,Material.AIR);
                }
            }
        }
    }

    void buildPlaza(World world) {
        plazaFloor(world,-36,-8,-44,-21,94,Material.POLISHED_DIORITE);
        if (!stamp("plaza_clocktower_guild",world,-35,64,-44,StructureRotation.NONE,false,false)) clockTower(world);
        trainingYard(world);

        plazaFloor(world,-37,-7,23,34,81,Material.POLISHED_GRANITE);
        if (!stamp("waterfront_bakery_cafe",world,-32,64,24,StructureRotation.NONE,false,false)) bakery(world);
        plazaFloor(world,7,36,23,34,81,Material.QUARTZ_BRICKS);
        if (!stamp("waterfront_sakura_garden",world,8,64,24,StructureRotation.NONE,false,false)) waterGarden(world);

        plazaFloor(world,8,28,-44,-24,83,Material.SMOOTH_QUARTZ);
        if (!stamp("hall_of_fame_pavilion",world,8,64,-44,StructureRotation.NONE,false,false)) hallPavilion(world);
        // Fixed open service frontage shared by NPCs, ranking boards and the festival booth.
        for (int x=9;x<=24;x++) for(int z=-29;z<=-27;z++) hub.clearColumn(world,x,65,68,z);
        for(int x=10;x<=28;x++) for(int z=-26;z<=-24;z++) b(world,x,64,z,Material.QUARTZ_BRICKS);
        for(int x=26;x<=28;x++) for(int z=-36;z<=-7;z++) {
            hub.clearColumn(world,x,65,68,z); b(world,x,64,z,Material.SMOOTH_QUARTZ);
        }
        forecourt(world);
        fountain(world);
        gatesAndGarlands(world);
        for(int i=0;i<TREES.length;i++) grandTree(world,TREES[i][0],TREES[i][1],i);
        // Finish uncovered ground between garden features as a promenade, including imported template margins.
        for(int[] area:new int[][]{{-36,-8,-44,-24},{-37,-7,23,34},{7,36,23,34},{8,28,-44,-24},{26,41,-36,-7}}) {
            for(int x=area[0];x<=area[1];x++) for(int z=area[2];z<=area[3];z++) {
                if(world.getBlockAt(x,64,z).getType()!=Material.GRASS_BLOCK)continue;
                boolean empty=true;for(int y=65;y<=70;y++)if(!world.getBlockAt(x,y,z).getType().isAir()){empty=false;break;}
                if(empty)b(world,x,64,z,(x+z)%3==0?Material.CHERRY_PLANKS:Material.POLISHED_DIORITE);
            }
        }
    }

    private void clockTower(World w) {
        for(int x=-31;x<=-21;x++) for(int z=-39;z<=-29;z++) b(w,x,64,z,Material.GOLD_BLOCK);
        for(int y=65;y<=82;y++) for(int x=-30;x<=-22;x++) for(int z=-38;z<=-30;z++) {
            if(x!=-30&&x!=-22&&z!=-38&&z!=-30) continue;
            boolean column=(x==-30||x==-22)&&(z==-38||z==-30);
            Material m=column?Material.QUARTZ_PILLAR:(y%5==0?Material.GOLD_BLOCK:Material.SMOOTH_QUARTZ);
            if(y>=80&&!column&&((x>=-27&&x<=-25)||(z>=-35&&z<=-33))) m=Material.AIR;
            b(w,x,y,z,m);
        }
        for(int y=83;y<=86;y++) {
            int radius=5-(y-83);
            for(int dx=-radius;dx<=radius;dx++) for(int dz=-radius;dz<=radius;dz++) {
                if(Math.abs(dx)==radius||Math.abs(dz)==radius||y==86) b(w,-26+dx,y,-34+dz,
                        Math.abs(dx)==radius||Math.abs(dz)==radius?Material.GOLD_BLOCK:Material.DEEPSLATE_TILES);
            }
        }
        b(w,-26,81,-34,Material.GOLD_BLOCK); b(w,-26,82,-34,Material.BELL);
        b(w,-26,87,-34,Material.OCHRE_FROGLIGHT); b(w,-26,88,-34,Material.END_ROD);
        // Clock-face backgrounds remain attractive when the authored dial is not available yet.
        for(int a=-1;a<=1;a++) for(int dy=-1;dy<=1;dy++) {
            b(w,-26+a,79+dy,-30,(a==0&&dy==0)?Material.BLUE_CONCRETE:Material.GOLD_BLOCK);
            b(w,-22,79+dy,-34+a,(a==0&&dy==0)?Material.BLUE_CONCRETE:Material.GOLD_BLOCK);
        }
        hub.spawnHolo(w,new Location(w,-25.5,90,-33.5),"<gold><bold>🕰 황금 고양이 천문 시계탑</bold></gold>");
    }

    private void trainingYard(World w) {
        for(int x=-22;x<=-10;x++) for(int z=-28;z<=-21;z++) {
            b(w,x,64,z,((x+z)%4==0)?Material.CHISELED_STONE_BRICKS:Material.STONE_BRICKS);
            hub.clearColumn(w,x,65,68,z);
        }
        for(int x:new int[]{-22,-10}) for(int z:new int[]{-28,-21}) {
            b(w,x,65,z,Material.STONE_BRICK_WALL); b(w,x,66,z,Material.LANTERN);
        }
        for(int x:new int[]{-21,-13}) { b(w,x,65,-28,Material.SMITHING_TABLE); b(w,x+1,65,-28,Material.ANVIL); }
        hub.spawnHolo(w,new Location(w,-15.5,70,-25.5),"<aqua><bold>⚔ 별빛 DPS 수련 마당</bold></aqua>\n<white>성물 · 각성 · 룬을 자유롭게 연습하세요</white>");
    }

    private void bakery(World w) {
        hub.stampCenteredV3(w,"jp_small_house_1",-32,64,28,StructureRotation.NONE);
        for(int x=-37;x<=-7;x++) for(int z=32;z<=34;z++) b(w,x,64,z,Material.CHERRY_PLANKS);
        for(int x=-37;x<=-7;x++) if(x%5!=0) b(w,x,65,34,Material.CHERRY_FENCE);
        for(int x:new int[]{-26,-20,-14,-9}) {
            stair(w,x,65,33,Material.CHERRY_STAIRS,BlockFace.NORTH);
            if(x%2==0) { b(w,x,65,30,Material.CHERRY_FENCE); b(w,x,66,30,Material.CHERRY_PRESSURE_PLATE); }
        }
        for(int y=65;y<=72;y++) b(w,-28,y,25,Material.BRICKS);
        b(w,-28,65,26,Material.SMOKER); b(w,-28,73,25,Material.CAMPFIRE);
        for(int x=-24;x<=-22;x++) { b(w,x,65,32,Material.CHERRY_PLANKS); b(w,x,66,32,Material.POTTED_PINK_TULIP); }
        hub.spawnHolo(w,new Location(w,-31.5,74,26),"<#ffb7d5><bold>🍵 수변 찻집 & 화덕 베이커리</bold></#ffb7d5>");
    }

    private void waterGarden(World w) {
        for(int x=10;x<=22;x++) for(int z=26;z<=31;z++) {
            b(w,x,62,z,(x+z)%6==0?Material.SEA_LANTERN:Material.PRISMARINE_BRICKS);
            b(w,x,63,z,Material.WATER); b(w,x,64,z,Material.AIR);
            if((x+z)%11==0) b(w,x,64,z,Material.LILY_PAD);
        }
        for(int z=24;z<=33;z++) {
            int lift=z>=27&&z<=30?2:1;
            for(int x=16;x<=18;x++) {
                if(z==24||z==25||z==32||z==33) stair(w,x,64+lift,z,Material.MANGROVE_STAIRS,z<28?BlockFace.NORTH:BlockFace.SOUTH);
                else b(w,x,64+lift,z,Material.CHERRY_PLANKS);
            }
            b(w,15,65+lift,z,Material.CHERRY_FENCE); b(w,19,65+lift,z,Material.CHERRY_FENCE);
        }
        hub.stampCenteredV3(w,"jp_gazebo_1",30,64,28,StructureRotation.NONE);
        for(int x=7;x<=36;x++) for(int z=32;z<=34;z++) {
            if(x<25||z==34) b(w,x,64,z,Material.CHERRY_PLANKS);
        }
        for(int x:new int[]{8,24,35}) { b(w,x,65,24,Material.STONE_BRICK_WALL); b(w,x,66,24,Material.LANTERN); }
    }

    private void hallPavilion(World w) {
        for(int x=8;x<=20;x++) for(int z=-43;z<=-31;z++) {
            if(z<=-37) b(w,x,65,z,(x+z)%4==0?Material.GOLD_BLOCK:Material.QUARTZ_BRICKS);
        }
        for(int x:new int[]{8,20}) for(int z:new int[]{-42,-31}) for(int y=65;y<=75;y++) {
            b(w,x,y,z,y==75?Material.GOLD_BLOCK:Material.QUARTZ_PILLAR);
        }
        for(int x=8;x<=20;x++) {
            int y=76+(int)Math.round(2*(1-Math.abs(x-14)/6.0));
            b(w,x,y,-31,Material.GOLD_BLOCK); b(w,x,y-1,-31,Material.CHISELED_QUARTZ_BLOCK);
        }
        for(int x:new int[]{10,14,18}) { b(w,x,66,-39,Material.GOLD_BLOCK); b(w,x,67,-39,Material.OCHRE_FROGLIGHT); }
        // Festival pavilion stays west of the shared X26..28 promenade.
        for(int x=20;x<=25;x++) for(int z=-35;z<=-30;z++) {
            b(w,x,70,z,(x+z)%2==0?Material.CRIMSON_PLANKS:Material.CHERRY_PLANKS);
            if(x==20||x==25||z==-35||z==-30) b(w,x,71,z,Material.GOLD_BLOCK);
        }
        for(int x:new int[]{20,25}) for(int y=65;y<=69;y++) b(w,x,y,-35,Material.CHERRY_FENCE);
        b(w,21,65,-33,Material.CHISELED_BOOKSHELF); b(w,24,65,-33,Material.BARREL);
        hub.spawnHolo(w,new Location(w,14.5,76,-30),"<gold><bold>🏛 명예의 전당 챔피언 스테이지</bold></gold>");
        hub.spawnHolo(w,new Location(w,22.5,72.8,-30),"<#ff85c0><bold>🎁 황금 가챠 야타이</bold></#ff85c0>");
    }

    private void forecourt(World w) {
        plazaFloor(w,26,40,-36,-7,73,Material.SMOOTH_QUARTZ);
        // X41 is the palace pilaster line: finish its foundation without clearing the facade above it.
        for(int z=-36;z<=-7;z++)b(w,41,64,z,Material.SMOOTH_QUARTZ);
        if(!stamp("bellagio_fountain_court",w,26,64,-36,StructureRotation.NONE,false,false)) {
            for(int cz:new int[]{-30,-15}) for(int x=30;x<=36;x++) for(int z=cz-3;z<=cz+3;z++) {
                boolean edge=x==30||x==36||z==cz-3||z==cz+3;
                b(w,x,64,z,Material.PRISMARINE_BRICKS); b(w,x,65,z,edge?Material.GOLD_BLOCK:Material.WATER);
                if(!edge&&x==33&&(z==cz||z==cz-2||z==cz+2)) {
                    for(int y=66;y<=69+(z==cz?2:0);y++) b(w,x,y,z,y%2==0?Material.WHITE_STAINED_GLASS:Material.END_ROD);
                }
            }
        }
        for(int x=26;x<=40;x++) for(int z=-25;z<=-21;z++) {
            b(w,x,64,z,Material.SMOOTH_QUARTZ);b(w,x,65,z,Material.RED_CARPET);
        }
        for(int x:new int[]{26,27,28,38,39,40}) for(int z=-36;z<=-7;z++) {
            hub.clearColumn(w,x,65,68,z); b(w,x,64,z,x>=38?Material.RED_CONCRETE:Material.SMOOTH_QUARTZ);
        }
        for(int x:new int[]{29,37}) for(int z:new int[]{-34,-10}) {
            for(int y=65;y<=69;y++) b(w,x,y,z,y==69?Material.OCHRE_FROGLIGHT:Material.QUARTZ_PILLAR);
            b(w,x,70,z,Material.GOLD_BLOCK);
        }
        // Soup kitchen is north of the main forecourt, with the newly opened X42/Z-35 side entrance.
        for(int x=38;x<=41;x++)for(int z=-40;z<=-37;z++)b(w,x,64,z,Material.STONE_BRICKS);
        b(w,39,65,-38,Material.CAMPFIRE);b(w,40,65,-38,Material.CAULDRON);
    }

    private void fountain(World w) {
        for(int x=-7;x<=7;x++) for(int z=-7;z<=7;z++) if(Math.hypot(x,z)<=7.2) {
            hub.clearColumn(w,x,65,70,z); b(w,x,64,z,Material.PRISMARINE_BRICKS);
            double r=Math.hypot(x,z); b(w,x,65,z,r>=6?Material.QUARTZ_BRICKS:Material.WATER);
            if(r<=3.6) b(w,x,66,z,r>=2.6?Material.QUARTZ_PILLAR:Material.WATER);
        }
        for(int y=65;y<=68;y++) b(w,0,y,0,Material.QUARTZ_PILLAR);
        b(w,0,69,0,Material.GOLD_BLOCK); b(w,-1,70,0,Material.PINK_CONCRETE);
        b(w,1,70,0,Material.PINK_CONCRETE); b(w,0,70,0,Material.SEA_LANTERN);
        for(int x=-11;x<=11;x++) for(int z=-11;z<=11;z++) {
            double r=Math.hypot(x,z); if(r<8.5||r>10.8||Math.abs(x)<4||Math.abs(z)<4) continue;
            if(w.getBlockAt(x,65,z).getType().isAir()) {
                b(w,x,64,z,Material.GRASS_BLOCK);
                b(w,x,65,z,r>10.0?Material.CHERRY_LEAVES:((x+z)%2==0?Material.PINK_TULIP:Material.ALLIUM));
            }
        }
        for(int sx:new int[]{-1,1}) for(int sz:new int[]{-1,1}) for(int d=-1;d<=1;d++) {
            int x=sx*9+d,z=sz*9;
            if(w.getBlockAt(x,65,z).getType().isAir()) stair(w,x,65,z,Material.CHERRY_STAIRS,sz>0?BlockFace.NORTH:BlockFace.SOUTH);
        }
    }

    private void gatesAndGarlands(World w) {
        for(int[] gate:new int[][]{{0,-26,0},{0,26,180},{-26,0,270},{26,0,90}}) {
            boolean eastWest=gate[0]!=0;
            StructureRotation rotation=eastWest?StructureRotation.CLOCKWISE_90:StructureRotation.NONE;
            // Centre an 11x5 footprint, accounting for rotation about its origin.
            int ox=eastWest?gate[0]+2:gate[0]-5,oz=eastWest?gate[1]-5:gate[1]-2;
            if(!stamp("plaza_welcome_gate",w,ox,64,oz,rotation,false,false)) {
                for(int d:new int[]{-5,5}) for(int y=65;y<=73;y++) b(w,gate[0]+(eastWest?0:d),y,gate[1]+(eastWest?d:0),y==73?Material.GOLD_BLOCK:Material.QUARTZ_PILLAR);
                for(int d=-5;d<=5;d++) {
                    b(w,gate[0]+(eastWest?0:d),74,gate[1]+(eastWest?d:0),Material.CHERRY_WOOD);
                    b(w,gate[0]+(eastWest?0:d),75,gate[1]+(eastWest?d:0),Material.GOLD_BLOCK);
                }
            }
        }
        for(int direction=0;direction<4;direction++) for(int distance=0;distance<=26;distance++) {
            int x=direction==0?distance:direction==1?-distance:0,z=direction==2?distance:direction==3?-distance:0;
            int y=74-(int)Math.round(distance/13.0);
            b(w,x,y,z,Material.CHAIN);
            if(distance>=5&&distance%5==0) b(w,x,y-1,z,Material.LANTERN);
            if(distance>=7&&distance%4==0) b(w,x+(z!=0?1:0),y,z+(x!=0?1:0),Material.CHERRY_LEAVES);
        }
    }

    private void grandTree(World w,int cx,int cz,int variation) {
        String template="grand_sakura_tree_"+(variation%2+1);
        if(stamp(template,w,cx-5,64,cz-5,StructureRotation.NONE,true,false)) return;
        int bend=(variation%2==0?1:-1),height=8+variation%4;
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
            if(Math.abs(dx)+Math.abs(dz)>1) continue;
            treeBlock(w,cx+dx,65,cz+dz,Material.CHERRY_WOOD);
        }
        for(int h=0;h<height;h++) treeBlock(w,cx+(h>=5?bend:0),65+h,cz+(h>=8?1:0),Material.CHERRY_LOG);
        for(int arm=0;arm<4;arm++) for(int d=1;d<=4;d++) {
            int x=cx+(arm==0?d:arm==1?-d:bend),z=cz+(arm==2?d:arm==3?-d:0);
            treeBlock(w,x,69+d/2+(arm%2),z,Material.CHERRY_WOOD);
        }
        for(int dy=0;dy<=5;dy++) {
            double radius=dy==0?3.4:dy==1?4.9:dy<=3?5.5:dy==4?4.4:2.7;
            for(int dx=-5;dx<=5;dx++) for(int dz=-5;dz<=5;dz++) {
                if(Math.hypot(dx,dz)>radius||((dx*17+dz*11+dy+variation)%19==0)) continue;
                treeBlock(w,cx+dx,70+dy,cz+dz,Material.CHERRY_LEAVES);
            }
        }
        for(int[] d:new int[][]{{-4,1},{3,-3},{4,2}}) {
            treeBlock(w,cx+d[0],69,cz+d[1],Material.CHERRY_LEAVES);
            treeBlock(w,cx+d[0],68,cz+d[1],variation%2==0?Material.LANTERN:Material.SPORE_BLOSSOM);
        }
        for(int dx=-3;dx<=3;dx++) for(int dz=-3;dz<=3;dz++) if(Math.hypot(dx,dz)<3.4&&w.getBlockAt(cx+dx,65,cz+dz).getType().isAir()) {
            if(Math.abs(cx+dx)<=5||Math.abs(cz+dz)<=5) continue;
            b(w,cx+dx,65,cz+dz,(dx+dz)%3==0?Material.PINK_TULIP:Material.PINK_PETALS);
        }
    }

    private void treeBlock(World w,int x,int y,int z,Material m) {
        if((Math.abs(x)<=5||Math.abs(z)<=5)&&y<69) return;
        Material old=w.getBlockAt(x,y,z).getType();
        if(old.isAir()||old==Material.CHERRY_LEAVES||old==Material.PINK_PETALS||old==Material.SHORT_GRASS) b(w,x,y,z,m);
    }

    void buildCasino(World w) {
        for(int x=38;x<=88;x++) for(int z=-40;z<=-3;z++) hub.clearColumn(w,x,76,94,z);
        // Restore roof cornice before adding a crown dome and the four tall corner towers.
        for(int x=42;x<=84;x++) for(int z=-38;z<=-8;z++) b(w,x,76,z,Material.BLACK_CONCRETE);
        for(int dx=-10;dx<=10;dx++)for(int dz=-6;dz<=6;dz++) {
            if(Math.pow(dx/10.0,2)+Math.pow(dz/6.0,2)<=1.13) {
                b(w,63+dx,75,-22+dz,Material.AIR);
                b(w,63+dx,76,-22+dz,Material.AIR);
            }
        }
        for(int cx:new int[]{44,81}) for(int cz:new int[]{-35,-10}) spire(w,cx,cz);
        for(int y=76;y<=88;y++) {
            double factor=Math.sqrt(Math.max(0,1-Math.pow((y-76)/12.0,2)));
            for(int dx=-10;dx<=10;dx++) for(int dz=-6;dz<=6;dz++) {
                double ellipse=Math.pow(dx/(10*Math.max(.09,factor)),2)+Math.pow(dz/(6*Math.max(.09,factor)),2);
                if(ellipse<=1.13&&(ellipse>=.64||y>=87)) b(w,63+dx,y,-22+dz,dx==0||dz==0||Math.abs(dx)==Math.abs(dz)?Material.GOLD_BLOCK:((dx+dz)%2==0?Material.YELLOW_STAINED_GLASS:Material.MAGENTA_STAINED_GLASS));
            }
        }
        for(int dx=-2;dx<=2;dx++) b(w,63+dx,89,-22,Material.GOLD_BLOCK);
        b(w,61,90,-22,Material.GOLD_BLOCK);b(w,63,90,-22,Material.OCHRE_FROGLIGHT);b(w,65,90,-22,Material.GOLD_BLOCK);b(w,63,91,-22,Material.GOLD_BLOCK);
        for(int z=-38;z<=-8;z++) {
            if((z+38)%5==0) for(int y=65;y<=76;y++) {
                b(w,41,y,z,y==75?Material.GOLD_BLOCK:y==73?Material.OCHRE_FROGLIGHT:Material.QUARTZ_PILLAR);
            }
        }
        for(int z=-29;z<=-17;z++) {
            int roof=77+(int)Math.round(5*(1-Math.abs(z+23)/6.0));
            for(int y=76;y<=roof;y++) b(w,41,y,z,y==roof?Material.GOLD_BLOCK:Material.CRIMSON_PLANKS);
        }
        // Close the former soup-kitchen opening and open its new north-west access.
        for(int z=-13;z<=-12;z++) for(int y=65;y<=68;y++) b(w,42,y,z,Material.BLACK_STAINED_GLASS);
        for(int z=-36;z<=-34;z++) for(int y=65;y<=68;y++) b(w,42,y,z,Material.AIR);
        for(int x:new int[]{55,58,61,65,68,71}) for(int y=65;y<=74;y++) b(w,x,y,-4,y==74?Material.GOLD_BLOCK:Material.QUARTZ_PILLAR);
        for(int x=55;x<=71;x++) {
            int top=75+(int)Math.round(6*(1-Math.abs(x-63)/8.0));
            for(int y=75;y<=top;y++) b(w,x,y,-3,y==top?Material.GOLD_BLOCK:Material.SMOOTH_QUARTZ);
            for(int z=-8;z<=-4;z++) b(w,x,74,z,Material.QUARTZ_BRICKS);
        }
        for(int x=58;x<=68;x++)for(int y=76;y<=80;y++)b(w,x,y,-3,Material.AIR);
        for(int x=61;x<=65;x++) for(int z=-7;z<=-3;z++) b(w,x,64,z,Material.RED_CONCRETE);
        for(int x:new int[]{56,70}) {b(w,x,65,-5,Material.GOLD_BLOCK);b(w,x,66,-5,Material.CAMPFIRE);}
        stamp("casino_palace_shell",w,38,64,-40,StructureRotation.NONE,false,true);
        // Functional overlays are authoritative even when a future shell blueprint changes its frontage.
        for(int x:new int[]{55,58,61,65,68,71}) for(int y=65;y<=74;y++) b(w,x,y,-4,y==74?Material.GOLD_BLOCK:Material.QUARTZ_PILLAR);
        for(int x=62;x<=64;x++) for(int y=65;y<=69;y++) b(w,x,y,-4,Material.AIR);
        for(int z=-36;z<=-34;z++) for(int y=65;y<=68;y++) b(w,42,y,z,Material.AIR);
        for(int[] pad:new int[][]{{63,65,-12},{52,65,-15},{80,65,-16},{80,65,-21},{80,71,-22},{52,71,-33}}) b(w,pad[0],pad[1],pad[2],Material.GOLD_BLOCK);
        for(int x=61;x<=65;x++) for(int y=66;y<=68;y++) b(w,x,y,-12,Material.AIR);
        for(int x=49;x<=55;x++) for(int y=72;y<=74;y++) for(int z=-32;z<=-30;z++) b(w,x,y,z,Material.AIR);
    }

    private void spire(World w,int cx,int cz) {
        for(int y=76;y<=85;y++) {
            int r=y<81?2:y<84?1:0;
            for(int dx=-r;dx<=r;dx++) for(int dz=-r;dz<=r;dz++) {
                if(Math.abs(dx)+Math.abs(dz)>r+1) continue;
                b(w,cx+dx,y,cz+dz,(dx==0||dz==0||y%3==0)?Material.GOLD_BLOCK:Material.CRIMSON_PLANKS);
            }
        }
        b(w,cx,86,cz,Material.OCHRE_FROGLIGHT); b(w,cx,87,cz,Material.END_ROD);
    }

    void mountLandmarks(World w,Set<String> models) {
        for(Entity e:new ArrayList<>(w.getEntities())) if(e.getPersistentDataContainer().has(fallbackKey,PersistentDataType.STRING)
                &&!hub.isOwnedColumn(w,e.getLocation().getBlockX(),e.getLocation().getBlockZ())) e.remove();
        boolean guild=blueprints.containsKey("plaza_clocktower_guild");
        double clockSouthX=guild?-21.5:-25.5,clockSouthZ=guild?-22.94:-28.94,clockEastX=guild?-7.94:-20.94;
        mount(w,models,"prop_plaza_clock_face",clockSouthX,79,clockSouthZ,2f,8,0);
        mount(w,models,"prop_plaza_clock_face",clockEastX,79,-33.5,2f,8,-90);
        if(!models.contains("prop_plaza_clock_face")) {
            clockFrame(w,new Location(w,clockSouthX,79,clockSouthZ),BlockFace.SOUTH);
            clockFrame(w,new Location(w,clockEastX,79,-33.5),BlockFace.EAST);
        }
        for(int[] gate:new int[][]{{0,-26,0},{0,26,180},{-26,0,270},{26,0,90}}) {
            mount(w,models,"prop_sakura_lantern_arch",gate[0]+.5,72.5,gate[1]+.5,2f,20,gate[2]);
            if(!models.contains("prop_sakura_lantern_arch")) fallbackBlock(w,new Location(w,gate[0]+.2,71.4,gate[1]+.2),
                    Material.LANTERN,new Vector3f(.6f,.8f,.6f),"gate_lantern");
        }
        boolean shell=blueprints.containsKey("casino_palace_shell");
        mount(w,models,"prop_casino_marquee",shell?37.5:40.5,77.5,-23,2.7f,8,90);
        mount(w,models,"prop_casino_marquee",63.5,77.5,shell?-4.8:-3.2,2.7f,8,0);
        if(!models.contains("prop_casino_marquee")) {
            fallbackSign(w,new Location(w,shell?37.5:40.5,78,-23),"<gold><bold>✦ 7 7 7 ✦</bold></gold>\n<yellow>GOLDEN PAWS PALACE</yellow>");
            fallbackSign(w,new Location(w,63.5,78,shell?-4.8:-3.2),"<gold><bold>✦ 7 7 7 ✦</bold></gold>\n<yellow>GOLDEN PAWS PALACE</yellow>");
        }
        mount(w,models,"prop_casino_chandelier",63,74.5,-22,2.3f,24,0);
        if(!models.contains("prop_casino_chandelier")) chandelier(w);
        for(double[] throne:new double[][]{{63.5,66,-12.7,0},{52.5,66,-15.7,0},{81.2,66,-16,90},
                {81.2,66,-21,90},{81.2,72,-22,90},{52.5,72,-33.7,0}}) {
            mount(w,models,"prop_dealer_throne",throne[0],throne[1],throne[2],1.5f,0,(float)throne[3]);
            if(!models.contains("prop_dealer_throne")) {
                Vector3f panelScale=throne[3]==90?new Vector3f(.25f,2.0f,.9f):new Vector3f(.9f,2.0f,.25f);
                Location corner=new Location(w,throne[0]-(throne[3]==90?.125:.45),throne[1],throne[2]-(throne[3]==90?.45:.125));
                fallbackBlock(w,corner,Material.CRIMSON_PLANKS,panelScale,"throne");
            }
        }
    }

    private void mount(World w,Set<String> models,String id,double x,double y,double z,float scale,float anchorY,float yaw) {
        if(!models.contains(id)||hub.isOwnedColumn(w,(int)Math.floor(x),(int)Math.floor(z))) return;
        float effectiveAnchor=anchorY;
        Path sources=sourceDirectory("sources");
        if(sources!=null) try {
            Path file=sources.resolve("landmarks-v31-mounts.json");
            if(Files.isRegularFile(file)) {
                YamlConfiguration metadata=new YamlConfiguration();metadata.loadFromString(Files.readString(file));
                List<Integer> anchor=metadata.getIntegerList("models."+id+".anchor");
                if(anchor.size()==3&&anchor.get(0)==8&&anchor.get(2)==8) effectiveAnchor=anchor.get(1);
            }
        }catch(Exception incompleteMetadata){ /* Fixed contract anchor remains safe while Astra writes its metadata. */ }
        hub.tagPublicProp(hub.spawnAnchoredProp(new Location(w,x,y,z),id,scale,effectiveAnchor,yaw),id);
    }

    private void clockFrame(World w,Location at,BlockFace face) {
        ItemFrame frame=w.spawn(at,ItemFrame.class);frame.setFacingDirection(face,true);frame.setItem(new ItemStack(Material.CLOCK));
        frame.setFixed(true);frame.setInvulnerable(true);frame.setPersistent(true);tagFallback(frame,"clock");
    }

    private void chandelier(World w) {
        for(int y=74;y<=75;y++) b(w,63,y,-22,Material.CHAIN);
        for(int layer=0;layer<3;layer++) for(int i=0;i<12;i++) {
            double angle=i*Math.PI/6,r=2.2-layer*.55,y=73.4-layer*.75;
            fallbackBlock(w,new Location(w,63+Math.cos(angle)*r-.12,y,-22+Math.sin(angle)*r-.12),
                    i%3==0?Material.SEA_LANTERN:Material.AMETHYST_BLOCK,new Vector3f(.25f,.6f,.25f),"chandelier");
        }
    }

    private void fallbackBlock(World w,Location at,Material material,Vector3f scale,String id) {
        BlockDisplay display=w.spawn(at,BlockDisplay.class);display.setBlock(material.createBlockData());display.setPersistent(true);
        display.setTransformation(new Transformation(new Vector3f(),new AxisAngle4f(),scale,new AxisAngle4f()));tagFallback(display,id);
    }
    private void fallbackSign(World world,Location at,String text) {
        TextDisplay display=world.spawn(at,TextDisplay.class);display.text(kr.yuumi97.core.util.TextUtil.mm(text));
        display.setBillboard(Display.Billboard.CENTER);display.setShadowed(true);display.setPersistent(true);tagFallback(display,"marquee");
    }
    private void tagFallback(Entity entity,String id) {
        entity.getPersistentDataContainer().set(fallbackKey,PersistentDataType.STRING,id);
        entity.getPersistentDataContainer().set(new NamespacedKey(plugin,"hub_decor_tag"),PersistentDataType.BYTE,(byte)1);
    }

    private void plazaFloor(World w,int minX,int maxX,int minZ,int maxZ,int clearY,Material floor) {
        for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) {
            hub.clearColumn(w,x,65,clearY,z);
            b(w,x,62,z,Material.STONE); b(w,x,63,z,Material.DIRT);
            b(w,x,64,z,(x+z)%9==0?Material.CHISELED_QUARTZ_BLOCK:floor);
        }
    }

    private void b(World w,int x,int y,int z,Material m) {hub.publicBlock(w,x,y,z,m);}
    private void stair(World w,int x,int y,int z,Material material,BlockFace face) {
        if(hub.isOwnedColumn(w,x,z)) return;
        var data=material.createBlockData();if(data instanceof Stairs stairs)stairs.setFacing(face);
        w.getBlockAt(x,y,z).setBlockData(data,false);
    }

    private boolean stamp(String id,World world,int ox,int oy,int oz,StructureRotation rotation,boolean tree,boolean shell) {
        Structure template=blueprints.get(id);if(template==null)return false;
        var size=template.getSize();int[] bounds=HubWorldManager.rotatedFootprint(size.getBlockX(),size.getBlockZ(),rotation);
        if(hub.intersectsOwnedColumns(world,ox+bounds[0],ox+bounds[1],oz+bounds[2],oz+bounds[3]))return false;
        for(var state:template.getPalettes().getFirst().getBlocks()) {
            int lx=state.getX(),lz=state.getZ();int rx=lx,rz=lz;
            switch(rotation){case CLOCKWISE_90->{rx=-lz;rz=lx;}case CLOCKWISE_180->{rx=-lx;rz=-lz;}case COUNTERCLOCKWISE_90->{rx=lz;rz=-lx;}default->{}}
            int x=ox+rx,y=oy+state.getY(),z=oz+rz;
            if(shell&&x>=43&&x<=83&&z>=-37&&z<=-9&&y<=75)continue;
            if(shell&&x>=38&&x<=40&&y>=65&&y<=68)continue;
            Material material=state.getType();
            if(tree){
                if(material.isAir())continue;
                if(y<=64){Material old=world.getBlockAt(x,y,z).getType();if(old!=Material.DIRT&&old!=Material.GRASS_BLOCK)continue;}
                else{if((Math.abs(x)<=5||Math.abs(z)<=5)&&y<69)continue;Material old=world.getBlockAt(x,y,z).getType();if(!old.isAir()&&old!=Material.CHERRY_LEAVES&&old!=Material.PINK_PETALS)continue;}
            }
            if(UNSAFE_TEMPLATE_BLOCKS.contains(material)) {
                b(world,x,y,z,y<=64?Material.STONE_BRICKS:Material.AIR);continue;
            }
            if(hub.isOwnedColumn(world,x,z))continue;
            var data=state.getBlockData().clone();data.rotate(rotation);
            if(data instanceof org.bukkit.block.data.type.Leaves leaves)leaves.setPersistent(true);
            world.getBlockAt(x,y,z).setBlockData(data,false);
            if(world.getBlockAt(x,y,z).getState() instanceof Container container){
                if(container instanceof Lootable lootable){lootable.setLootTable(null);container.update(true,false);}
                container.getInventory().clear();
            }
        }
        return true;
    }
}
