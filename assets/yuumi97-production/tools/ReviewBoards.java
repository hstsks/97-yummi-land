import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/** Contact sheets only: no generated geometry, retouching, or fabricated tiles. */
class ReviewBoards {
    record Tile(Path path,String label){}
    static void board(Path output,String title,List<Tile> tiles,int columns,int cell) throws Exception {
        if(tiles.isEmpty())throw new IllegalArgumentException("Empty review board");
        int width=columns*cell,height=((tiles.size()+columns-1)/columns)*(cell+32)+80;
        var canvas=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var g=canvas.createGraphics();
        g.setColor(new Color(0x20242B));g.fillRect(0,0,width,height);
        g.setColor(new Color(0xF2DDA2));g.setFont(new Font("SansSerif",Font.BOLD,20));g.drawString(title,16,30);
        g.setFont(new Font("SansSerif",Font.PLAIN,13));g.setColor(new Color(0xBAC8D4));
        g.drawString("Delivered JSON + PNG / saved Blender scenes / Minecraft runtime verification remains separate",16,54);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for(int i=0;i<tiles.size();i++){
            var tile=tiles.get(i);
            if(!Files.isRegularFile(tile.path))throw new IllegalStateException("Missing required tile: "+tile.path);
            var picture=ImageIO.read(tile.path.toFile());
            if(picture==null)throw new IllegalStateException("Invalid image: "+tile.path);
            int x=(i%columns)*cell+6,y=(i/columns)*(cell+32)+80;
            g.drawImage(picture,x,y,cell-12,cell-12,null);
            g.setColor(new Color(0xE6E4D9));g.setFont(new Font("SansSerif",Font.BOLD,12));g.drawString(tile.label,x+4,y+cell+8);
        }
        g.dispose();Files.createDirectories(output.getParent());ImageIO.write(canvas,"PNG",output.toFile());
        System.out.println(output);
    }
    public static void main(String[] args) throws Exception {
        Path review=Path.of(args[0]).resolve("review");String mode=args[1];
        var singles=new ArrayList<String>();var rigs=new ArrayList<String[]>();
        for(String row:Files.readAllLines(review.resolve("board-inputs.tsv"))){
            String[] fields=row.split("\t");
            if(fields[0].equals("model"))singles.add(fields[1]);else rigs.add(fields);
        }
        if(mode.equals("all")||mode.equals("models")){
            var main=new ArrayList<Tile>();var overview=new ArrayList<Tile>();var pages=new ArrayList<Tile>();
            for(int i=0;i<singles.size();i++){
                String id=singles.get(i);Path dir=review.resolve("models/"+id);var individual=new ArrayList<Tile>();
                for(String view:List.of("front","side","rear","threequarter","top"))individual.add(new Tile(dir.resolve(view+".png"),view));
                board(dir.resolve("contact-sheet.png"),id,individual,3,420);
                String label=String.format("%02d %s",i+1,id.replace("weapon_","").replace("vfx_","").replace("prop_",""));
                for(String view:List.of("front","top","threequarter"))main.add(new Tile(dir.resolve(view+".png"),label+" / "+view));
                overview.add(new Tile(dir.resolve("threequarter.png"),label));
                pages.addAll(individual.stream().filter(t->List.of("front","side","threequarter").contains(t.label)).map(t->new Tile(t.path,label+" / "+t.label)).toList());
                if((i+1)%5==0||i==singles.size()-1){
                    board(review.resolve("relics-effects-page-"+((i/5)+1)+".png"),"RELICS & EFFECTS / FRONT - SIDE - THREEQUARTER",pages,3,480);pages.clear();
                }
            }
            board(review.resolve("overhaul-relics-and-vfx.png"),"YUUMI 97LAND / 19 RELICS & EFFECTS / FRONT - TOP - THREEQUARTER",main,3,420);
            board(review.resolve("relics-and-vfx-overview.png"),"YUUMI 97LAND / FIVE RELICS & FOURTEEN EFFECTS",overview,4,400);
            var grips=new ArrayList<Tile>();var placements=new ArrayList<Tile>();
            for(String id:singles){
                Path dir=review.resolve("contexts/"+id);
                try(var files=Files.list(dir)){
                    for(Path p:files.filter(p->p.getFileName().toString().startsWith("context-")&&p.toString().endsWith(".png")).sorted().toList()){
                        var tile=new Tile(p,id.replace("weapon_","").replace("vfx_","")+" / "+p.getFileName().toString().replace("context-","").replace(".png",""));
                        if(id.startsWith("weapon_")||id.equals("guidebook"))grips.add(tile);else placements.add(tile);
                    }
                }
            }
            board(review.resolve("relic-grips.png"),"FOUR RELICS / LEFT & RIGHT / FIRST & THIRD PERSON GRIP JIGS",grips,4,400);
            board(review.resolve("effect-placement.png"),"DIRECTION, PIVOT & REFERENCE PLANES / ITEMDISPLAY NONE",placements,4,400);
        }
        if(mode.equals("all")||mode.equals("rigs")){
            var main=new ArrayList<Tile>();var overview=new ArrayList<Tile>();
            for(int chapter=1;chapter<=3;chapter++){
                var chapterTiles=new ArrayList<Tile>();
                for(String[] row:rigs){
                    if(Integer.parseInt(row[2])!=chapter)continue;
                    String id=row[1],label=id.replace("mob_","").replace("boss_","");Path dir=review.resolve("rigs/"+id);var all=new ArrayList<Tile>();
                    for(String view:List.of("front","side","rear","threequarter","windup-minus60","windup-minus60-side","strike-plus55"))all.add(new Tile(dir.resolve(view+".png"),view));
                    board(dir.resolve("contact-sheet.png"),id+" / NEUTRAL + WINDUP + STRIKE",all,4,400);
                    for(String view:List.of("front","threequarter","windup-minus60"))chapterTiles.add(new Tile(dir.resolve(view+".png"),label+" / "+view));
                    overview.add(new Tile(dir.resolve("threequarter.png"),"CH"+chapter+" "+label));
                }
                if(chapterTiles.size()!=18)throw new IllegalStateException("Chapter must have 6 rigs x 3 views");
                board(review.resolve("monsters-chapter-"+chapter+".png"),"CHAPTER "+chapter+" / SIX ASSEMBLED RIGS / FRONT - THREEQUARTER - WINDUP",chapterTiles,3,480);
                main.addAll(chapterTiles);
            }
            if(main.size()!=54)throw new IllegalStateException("Expected 54 required monster tiles");
            board(review.resolve("overhaul-monsters-ch1-ch2-ch3.png"),"YUUMI 97LAND / 18 ASSEMBLED MONSTERS & BOSSES / 42 ITEMDISPLAY BONES",main,3,420);
            board(review.resolve("monsters-overview.png"),"THREE DUNGEONS / FOUR MOBS + TWO BOSSES PER CHAPTER",overview,6,360);
        }
    }
}
