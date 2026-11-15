import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/** Only arranges actual Blender renders; every required tile must exist. */
class ReviewBoardV31 {
    static final String[] IDS={"prop_casino_marquee","prop_casino_chandelier","prop_dealer_throne","prop_plaza_clock_face","prop_training_dummy","prop_sakura_lantern_arch"};
    record Tile(Path path,String label){}
    static void board(Path output,String title,List<Tile> tiles,int columns,int cell) throws Exception {
        int width=columns*cell,height=((tiles.size()+columns-1)/columns)*(cell+32)+80;
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setColor(new Color(0x20242B));g.fillRect(0,0,width,height);g.setColor(new Color(0xF2DDA2));g.setFont(new Font("SansSerif",Font.BOLD,22));g.drawString(title,18,32);
        g.setFont(new Font("SansSerif",Font.PLAIN,14));g.setColor(new Color(0xBAC8D4));g.drawString("Delivered JSON + PNG / saved Blender scenes / Minecraft integration is separate",18,56);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for(int i=0;i<tiles.size();i++){
            var tile=tiles.get(i);if(!Files.isRegularFile(tile.path))throw new IllegalStateException("Missing required tile: "+tile.path);
            var picture=ImageIO.read(tile.path.toFile());if(picture==null)throw new IllegalStateException("Invalid image: "+tile.path);
            int x=(i%columns)*cell+6,y=(i/columns)*(cell+32)+80;
            g.drawImage(picture,x,y,cell-12,cell-12,null);g.setColor(new Color(0xE6E4D9));g.setFont(new Font("SansSerif",Font.BOLD,13));g.drawString(tile.label,x+6,y+cell+9);
        }
        g.dispose();ImageIO.write(image,"PNG",output.toFile());
    }
    public static void main(String[] args) throws Exception {
        Path review=Path.of(args[0]).resolve("review");List<Tile> main=new ArrayList<>(),contexts=new ArrayList<>(),distances=new ArrayList<>();
        String[] kinds={"wall","ceiling","floor","wall","floor","gate"};
        for(int i=0;i<IDS.length;i++){
            String id=IDS[i];Path dir=review.resolve("models/"+id);List<Tile> four=new ArrayList<>();
            for(String view:List.of("front","side","rear","threequarter"))four.add(new Tile(dir.resolve(view+".png"),view));
            board(dir.resolve("contact-sheet.png"),id+" / FOUR VIEWS",four,2,480);
            for(String view:List.of("front","side","threequarter"))main.add(new Tile(dir.resolve(view+".png"),String.format("%02d %s / %s",i+1,id,view)));
            contexts.add(new Tile(review.resolve("contexts/"+id+"/context-"+kinds[i]+"_mount.png"),id+" / "+kinds[i]));
            if(id.equals("prop_dealer_throne"))contexts.add(new Tile(review.resolve("contexts/"+id+"/context-dealer_clearance.png"),id+" / dealer clearance"));
            if(kinds[i].equals("wall"))for(int m:new int[]{16,32})distances.add(new Tile(dir.resolve("distance-"+m+"m.png"),id+" / "+m+"m / FOV 70"));
        }
        board(review.resolve("landmarks-v31.png"),"YUUMI 97LAND V3.1 / SIX LANDMARKS / FRONT - SIDE - THREEQUARTER",main,3,480);
        board(review.resolve("landmarks-v31-contexts.png"),"V3.1 / MOUNTING AND DEALER CLEARANCE / APPROXIMATE JIGS",contexts,3,480);
        board(review.resolve("landmarks-v31-distance.png"),"V3.1 / DISTANCE READABILITY / 16m - 32m / FRONT PERSPECTIVE",distances,2,640);
        System.out.println("Composed 18 required landmark tiles, 7 mount contexts, 4 distance images.");
    }
}
