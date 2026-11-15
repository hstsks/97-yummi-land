import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Composes only real Blender-rendered files. No geometry/image retouching. */
class ReviewBoardV3 {
    static final String[] IDS={"prop_gacha_machine","prop_stock_board","prop_dungeon_obelisk","prop_boon_altar","prop_feast_cauldron","furn_cat_tower","furn_kotatsu","furn_neon_sign","hat_chef_toque","hat_stock_tophat","weapon_paladin_sword","weapon_shadow_daggers","weapon_ranger_bow","weapon_arcane_grimoire","tool_miner_drill","tool_smith_hammer","tool_farmer_scythe","tool_angler_harpoon","tool_carpenter_axe","tool_chef_wok"};
    record Tile(Path file,String label){}
    static void board(Path output,String title,List<Tile> tiles,int columns,int cell) throws Exception {
        if(tiles.isEmpty())return;
        int width=columns*cell,height=((tiles.size()+columns-1)/columns)*(cell+32)+80;
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();g.setColor(new Color(0x20242B));g.fillRect(0,0,width,height);
        g.setColor(new Color(0xF2DDA2));g.setFont(new Font("SansSerif",Font.BOLD,Math.max(17,cell/20)));g.drawString(title,18,32);
        g.setFont(new Font("SansSerif",Font.PLAIN,14));g.setColor(new Color(0xBAC8D4));g.drawString("Actual delivered JSON + PNG geometry / Blender review; Minecraft validation is separate",18,56);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for(int i=0;i<tiles.size();i++){
            var tile=tiles.get(i);var picture=ImageIO.read(tile.file.toFile());
            int x=(i%columns)*cell+6,y=(i/columns)*(cell+32)+80;
            g.drawImage(picture,x,y,cell-12,cell-12,null);
            g.setFont(new Font("SansSerif",Font.BOLD,Math.min(17,cell/25)));g.setColor(new Color(0xE6E4D9));g.drawString(tile.label,x+6,y+cell+9);
        }
        g.dispose();Files.createDirectories(output.getParent());ImageIO.write(image,"PNG",output.toFile());
    }
    public static void main(String[] args) throws Exception {
        Path review=Path.of(args[0]).resolve("review");
        List<Tile> all=new ArrayList<>(),environment=new ArrayList<>(),housing=new ArrayList<>(),weapons=new ArrayList<>();
        for(int i=0;i<IDS.length;i++){
            String id=IDS[i];Path dir=review.resolve("models/"+id);
            if(!Files.isRegularFile(dir.resolve("threequarter.png")))continue;
            var tile=new Tile(dir.resolve("threequarter.png"),String.format("%02d  %s",i+1,id));
            all.add(tile);(i<5?environment:i<10?housing:weapons).add(tile);
            var views=new ArrayList<Tile>();
            for(String name:List.of("front","side","rear","threequarter"))if(Files.isRegularFile(dir.resolve(name+".png")))views.add(new Tile(dir.resolve(name+".png"),name));
            board(dir.resolve("contact-sheet.png"),id+" / four views",views,2,480);
            var contexts=new ArrayList<Tile>();
            Path contextDir=review.resolve("contexts/"+id);
            if(Files.isDirectory(contextDir))try(var files=Files.list(contextDir)){
                files.filter(p->p.getFileName().toString().startsWith("context-")&&p.toString().endsWith(".png")&&!p.getFileName().toString().equals("context-sheet.png")).sorted().forEach(p->contexts.add(new Tile(p,p.getFileName().toString().replace("context-","").replace(".png",""))));
            }
            board(contextDir.resolve("context-sheet.png"),id+" / display-context approximation",contexts,2,480);
        }
        board(review.resolve("models-v3-overview.png"),"YUUMI 97LAND V3 / 20 NEW VOXEL ASSETS",all,4,480);
        board(review.resolve("environment-v3.png"),"V3 / ENVIRONMENT PROPS",environment,3,480);
        board(review.resolve("housing-v3.png"),"V3 / FURNITURE AND HEADWEAR",housing,3,480);
        board(review.resolve("weapons-v3.png"),"V3 / TEN CLASS RELICS",weapons,5,420);
        System.out.println("Composed review sheets for "+all.size()+" models.");
    }
}
