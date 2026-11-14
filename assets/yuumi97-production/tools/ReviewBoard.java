import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;

/** Contact sheet of actual reimported JSON geometry renders, not concept artwork. */
class ReviewBoard {
    public static void main(String[] args) throws Exception {
        Path production = Path.of(args[0]);
        String[][] assets = {
            {"prop_yuumi_statue", "01  YUUMI FOUNTAIN STATUE"},
            {"prop_slot_cabinet", "02  THREE-REEL SLOT CABINET"},
            {"prop_roulette_wheel", "03  ROULETTE WHEEL"},
            {"prop_derby_trophy", "04  DERBY CHAMPION TROPHY"},
            {"prowling_wand", "05  PROWLING WAND"},
            {"book_of_thresholds", "06  BOOK OF THRESHOLDS"},
            {"you_and_me_bell", "07  YOU AND ME BELL"},
            {"hat_yuumi_ears", "08  YUUMI EARS HEADBAND"},
            {"plushie_yuumi", "09  YUUMI PLUSHIE"}
        };
        BufferedImage board = new BufferedImage(1440, 1600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = board.createGraphics();
        g.setColor(new Color(0x20242B));g.fillRect(0,0,1440,1600);
        g.setColor(new Color(0xF2DDA2));g.setFont(new Font("SansSerif",Font.BOLD,27));
        g.drawString("YUUMI 97LAND  /  MINECRAFT 1.21.4 ASSET COLLECTION",28,39);
        g.setColor(new Color(0xBAC8D4));g.setFont(new Font("SansSerif",Font.PLAIN,16));
        g.drawString("Actual JSON + PNG reimport renders  |  9 editable voxel models  |  27 matching pixel icons",28,67);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for(int i=0;i<assets.length;i++){
            int x=(i%3)*480,y=90+(i/3)*500;
            BufferedImage picture=ImageIO.read(production.resolve("review/models/"+assets[i][0]+"/threequarter.png").toFile());
            g.drawImage(picture,x+8,y,464,464,null);
            g.setColor(new Color(0xE6E4D9));g.setFont(new Font("SansSerif",Font.BOLD,17));
            g.drawString(assets[i][1],x+20,y+486);
        }
        g.dispose();ImageIO.write(board,"PNG",production.resolve("review/models-overview.png").toFile());
    }
}
