package kr.yuumi97.core.guide;

import kr.yuumi97.core.hub.HubWorldManager;
import kr.yuumi97.core.plot.PlotManager;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import java.util.function.Consumer;

/** One destination registry drives command travel and the guidebook. */
public final class HubTravelManager implements Listener {
    public enum Destination {
        WORKSHOP("🏰 만능 길드 대공방",Material.SMITHING_TABLE), CLASS_MASTER("🌟 전직 교관",Material.NETHER_STAR),
        DUNGEON("🗝 문턱의 미궁",Material.TRIAL_KEY), SANCTUARY("🌌 제단·차원 성소",Material.ENCHANTED_BOOK),
        SHOPS("⚖ 전문 상점가",Material.EMERALD), BANK("🏦 중앙 은행",Material.RAW_GOLD_BLOCK),
        SMITH("⚒ 별빛 대장간",Material.ANVIL), TOWN_HALL("🏛 마을 회관",Material.WRITABLE_BOOK),
        CASINO("🎰 카지노·경묘",Material.GOLD_BLOCK), STOCK("📈 증권 거래소",Material.PAPER),
        ONSEN("♨ 온천·식당",Material.CAMPFIRE), ARENA("⚔ 야외 레이드 결투장",Material.DIAMOND_SWORD),
        HOME("🏡 내 분양·공동거주 부지",Material.CHERRY_SAPLING), HALL_OF_FAME("🏆 명예의 전당",Material.NAME_TAG),
        GACHA("🎁 황금 캡슐 가챠",Material.HEART_OF_THE_SEA);
        public final String title;public final Material icon;
        Destination(String title,Material icon){this.title=title;this.icon=icon;}
    }
    private final HubWorldManager hub;private final PlotManager plots;private final WayfindingManager wayfinder;
    private Consumer<Player> beforeTravel=p->{},guideOpener=p->{};
    public HubTravelManager(JavaPlugin plugin,HubWorldManager hub,PlotManager plots,WayfindingManager wayfinder){this.hub=hub;this.plots=plots;this.wayfinder=wayfinder;}
    public HubWorldManager getHub(){return hub;}
    public void setBeforeTravel(Consumer<Player> hook){beforeTravel=hook==null?p->{}:hook;}
    public void setGuideOpener(Consumer<Player> hook){guideOpener=hook;}
    public boolean isInHub(Player p){return p.getWorld().equals(hub.getHubWorld());}
    public Location resolveHome(Player p){Location home=plots.getHomeEntranceOrNull(p.getUniqueId());return home!=null?home:new Location(hub.getHubWorld(),.5,65,58.5,0,0);}
    public Location location(Player p,Destination d){World w=hub.getHubWorld();return switch(d){
        case WORKSHOP->hub.getUtilityHallLocation();case CLASS_MASTER->new Location(w,-14.5,65,-7.5,180,0);
        case DUNGEON->new Location(w,18.5,66,-51.5,180,0);case SANCTUARY->new Location(w,.5,66,-49.5,180,0);
        case SHOPS->new Location(w,-48.5,65,.5,180,0);case BANK->new Location(w,-83.5,65,-3.5,180,0);
        case SMITH->new Location(w,-82.5,65,3.5,0,0);case TOWN_HALL->new Location(w,-102.5,65,.5,90,0);
        case CASINO->new Location(w,63.5,65,-5.5,180,0);case STOCK->new Location(w,52.5,71,-30.5,180,0);
        case ONSEN->new Location(w,45.5,65,10.5,0,0);case ARENA->new Location(w,.5,65,-196.5,180,0);
        case HOME->resolveHome(p);case HALL_OF_FAME->new Location(w,14.5,65,-24.5,180,0);case GACHA->new Location(w,22.5,65,-24.5,180,0);
    };}
    private boolean allowed(Player p){if(!isInHub(p)){p.sendMessage(TextUtil.msg("<aqua>원정 중에는 가이드북 Shift+우클릭으로 3초 허브 귀환, 귀환 책갈피로 1초 집 귀환을 사용하세요."));return false;}
        return !p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR;}
    public boolean warp(Player p,Destination d){
        if(!allowed(p))return false;Location target=safeLanding(location(p,d));boolean fallback=target==null;
        if(fallback)target=safeLanding(hub.getHubSpawn());
        if(target==null){p.sendMessage(TextUtil.msg("<red>시설 착지 지점을 확보하지 못했습니다. 잠시 후 다시 시도해 주세요."));return false;}
        beforeTravel.accept(p);p.closeInventory();if(p.isInsideVehicle())p.leaveVehicle();
        if(!p.teleport(target))return false;wayfinder.stop(p);p.setFallDistance(0);p.setVelocity(new org.bukkit.util.Vector());
        p.playSound(target,Sound.ENTITY_ENDERMAN_TELEPORT,.6f,1.3f);p.sendMessage(TextUtil.msg(fallback?"<yellow>시설 주변에 안전한 착지점이 없어 중앙 광장으로 이동했습니다.":"<green>✨ "+d.title+"로 이동했습니다."));return true;
    }
    public void guide(Player p,Destination d){if(allowed(p)){p.closeInventory();wayfinder.start(p,d.title,location(p,d));}}
    public Location safeLanding(Location wanted){
        if(wanted==null||wanted.getWorld()==null)return null;World w=wanted.getWorld();
        for(int radius=0;radius<=3;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;double x=wanted.getX()+dx,z=wanted.getZ()+dz;
            if(plots.isOwnedColumn((int)Math.floor(x),(int)Math.floor(z)))continue;
            w.getChunkAt((int)Math.floor(x)>>4,(int)Math.floor(z)>>4).load();
            for(int y=(int)Math.floor(wanted.getY())+1;y>=(int)Math.floor(wanted.getY())-2;y--){
                var floor=w.getBlockAt((int)Math.floor(x),y,(int)Math.floor(z));if(floor.isLiquid()||floor.isPassable()||floor.getType()==Material.MAGMA_BLOCK||floor.getType()==Material.CAMPFIRE||floor.getType()==Material.SOUL_CAMPFIRE||floor.getType()==Material.CACTUS)continue;
                double feet=floor.getBoundingBox().getMaxY();if(feet<wanted.getY()-.6||feet>wanted.getY()+1)continue;
                BoundingBox body=new BoundingBox(x-.3,feet+.01,z-.3,x+.3,feet+1.8,z+.3);boolean clear=true;
                for(int bx=(int)Math.floor(body.getMinX());bx<=(int)Math.floor(body.getMaxX());bx++)for(int by=(int)Math.floor(body.getMinY());by<=(int)Math.floor(body.getMaxY());by++)for(int bz=(int)Math.floor(body.getMinZ());bz<=(int)Math.floor(body.getMaxZ());bz++){
                    var block=w.getBlockAt(bx,by,bz);if(block.isLiquid()||!block.isPassable()&&block.getBoundingBox().overlaps(body))clear=false;
                }
                if(clear)return new Location(w,x,feet,z,wanted.getYaw(),wanted.getPitch());
            }
        }return null;
    }
    public boolean handleCommand(Player p,String command){
        String name=command.toLowerCase(java.util.Locale.ROOT);
        if(name.equals("way")){if(wayfinder.isActive(p)){wayfinder.stop(p);p.sendMessage(TextUtil.msg("<yellow>길잡이를 종료했습니다."));}else guideOpener.accept(p);return true;}
        Destination d=switch(name){case "workshop"->Destination.WORKSHOP;case "job"->Destination.CLASS_MASTER;case "dungeon"->Destination.DUNGEON;
            case "altar"->Destination.SANCTUARY;case "shops"->Destination.SHOPS;case "bank"->Destination.BANK;case "smith"->Destination.SMITH;
            case "townhall","quest","codex"->Destination.TOWN_HALL;case "casino"->Destination.CASINO;case "stock"->Destination.STOCK;
            case "onsen"->Destination.ONSEN;case "arena","raid"->Destination.ARENA;case "home"->Destination.HOME;case "land"->Destination.TOWN_HALL;default->null;};
        if(d==null)return false;warp(p,d);return true;
    }
}
