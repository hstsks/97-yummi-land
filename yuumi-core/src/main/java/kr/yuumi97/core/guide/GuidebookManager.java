package kr.yuumi97.core.guide;

import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.util.TextUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.*;
import static kr.yuumi97.core.guide.HubTravelManager.Destination;

/** Canonical guide item, travel index and a readable eight-chapter handbook. */
public final class GuidebookManager implements Listener {
    private enum Kind { TRAVEL, INDEX, CHAPTER }
    private record Holder(Kind kind,int chapter) implements InventoryHolder {public Inventory getInventory(){return null;}}
    private record Chapter(String title,Material icon,List<String> text) {}
    private final YuumiItems items;private final HubTravelManager travel;
    private final BiConsumer<Player,Boolean> recall;private final Consumer<Player> rewards;
    private final Map<UUID,Integer> useTicks=new HashMap<>();
    private static final int[] DEST_SLOTS={10,11,12,13,14,15,16,19,20,21,22,23,24,25,28};
    private static final List<Chapter> CHAPTERS=List.of(
        new Chapter("① 입문과 안전 예금",Material.EMERALD,List.of(
            "가이드북 우클릭으로 허브 이동과 백과사전을 엽니다. 잃어버려도 /가이드 또는 광장 독서대에서 무료로 다시 받을 수 있습니다.",
            "허브에서는 시설 아이콘 좌클릭으로 즉시 이동하고 우클릭으로 개인 별빛 길잡이를 켭니다. /길잡이를 다시 입력하면 안내가 끝납니다.",
            "츄르는 지갑과 은행 예금으로 나뉩니다. 원정 사망 구조비는 지갑에서 차감되므로 출발 전 여유 자금을 은행에 맡기세요.",
            "은행에서 예금·출금과 100/1,000/10,000 츄르 실물 수표 발행을 이용합니다. 수표 우클릭은 지갑 환전이며 카지노 칩과는 다른 아이템입니다.",
            "개인 금고 첫 탭은 무료이며 추가 탭을 순서대로 확장합니다. 퀘스트·도감·채집으로 재료와 자금을 모으고 미수령 보상은 가이드의 보상 버튼으로 받습니다.")),
        new Chapter("② 전투 6종 + 생활 6종",Material.NETHER_STAR,List.of(
            "전투 클래스와 생활 마스터리를 동시에 하나씩 선택합니다. 교관에게 선택·변경하며 다른 직업으로 바꿔도 각 직업의 성장 기록은 보존됩니다.",
            "전투는 성기사·버서커·어쌔신·레인저·아크메이지·프리스트, 생활은 채광가·대농장주·강태공·목수·대장장이·셰프입니다.",
            "전투 성물 좌클릭은 기본 공격, 우클릭은 주력기, F는 결전기입니다. 레인저와 마법 성물도 차징이나 화살 소모 없이 좌클릭으로 발사합니다.",
            "생활 도구도 우클릭 실용 기술과 F 전문 기술을 사용합니다. 강태공 우클릭은 순정 낚시이며 Shift+우클릭으로 밑밥을 씁니다. 농부는 땅 경작을 우선합니다.",
            "상자·문·제작대·모루·NPC를 우클릭하면 상호작용을 우선합니다. 주손 성물에 맞춰 자원·표식·스킬 대기시간 HUD가 바뀌며 허수아비에서 연습할 수 있습니다.")),
        new Chapter("③ 진화·각인·별빛 세공",Material.ANVIL,List.of(
            "성물은 Lv.1/5/10/15/20 마일스톤에서 기능과 외형이 진화합니다. Lv.10부터 교관에게 각성 또는 생활 전문화를 선택하세요.",
            "F는 초반에도 기본 결전기를 발동합니다. Lv.10 이후 각성을 아직 고르지 않았다면 기본 기술을 사용하면서 교관 안내를 받습니다.",
            "성기사는 결의, 버서커는 투지, 어쌔신은 낙인, 레인저는 표적, 마법사는 공명, 프리스트는 월광을 연결하면 다음 기술이 강화됩니다.",
            "레벨업 SP로 전투 또는 생활 특전 각인을 선택합니다. 흡혈·자동 보호막·쿨감과 자석 수납·연쇄 상한·특산물·공방 대성공 등 실제 플레이 방식을 고르세요.",
            "전투 성물 6종과 생활 도구 6종 모두 대장간에서 파괴 없이 ★1~★7 별빛 세공을 합니다. ★3/★5/★7 돌파 효과와 룬 각인은 장비 설명에서 확인하세요.",
            "허수아비는 피해와 최근 5초 DPS를 플레이어별로 표시합니다. 경험치나 영혼석을 주지 않으므로 연습 후 원정에서 성장하세요.")),
        new Chapter("④ 심리스 던전과 전조",Material.TRIAL_KEY,List.of(
            "1~8인 파티를 만든 뒤 북쪽 미궁 관문에 모입니다. 별빛 천문 대서고·홍련의 거신 용광로·공허의 수정 대성당은 여섯 구역이 걸어서 이어집니다.",
            "로비 준비 → 복층 전투 → 기믹 대교 → 중보스·화톳불 → 공간 퍼즐 → 대보스 대성당 순서로 진행합니다. 구역 목표를 달성하면 성문이 위로 열립니다.",
            "적이 무기를 치켜들면 곧 강공격이 옵니다. 바닥 진홍색 원·직선·부채꼴과 경고음을 보고 안전 지대로 이동하거나 방패·회피·강인도 파쇄를 준비하세요.",
            "서고는 성좌 거울과 대석주 엄폐, 용광로는 증기·압력 밸브와 냉각, 공허 성당은 화음 순서와 성빛 등불이 핵심입니다. 보스 체력·강인도 게이지를 함께 보세요.",
            "원정 제한 시간은 30분입니다. 연결이 끊기면 180초 안에 다시 접속해야 복귀할 수 있으며, 원정의 시간은 계속 흐릅니다.",
            "화톳불 전 전원 전투 불능은 원정 실패입니다. 화톳불을 점화하면 회복·축복과 체크포인트 1회 재도전 기회를 얻습니다. 생활 기술로 수리·만찬을 준비하세요.",
            "관전자 동료는 다음 구역 클리어 또는 프리스트 각성 A의 전투 부활로 돌아옵니다. 체크포인트 재도전은 1회만 가능하므로 안전한 패턴 대응을 우선하세요.",
            "최종 보스 처치 후 북쪽 보물고와 귀환단이 열립니다. 원정 월드에서 시설 명령어는 원격 은행·상점을 열지 않습니다.")),
        new Chapter("⑤ 대공방과 분업 경제",Material.SMITHING_TABLE,List.of(
            "북서 만능 길드 대공방은 누구나 쓰는 무한 모루·제작대·30레벨 인챈트·용광로·엔더상자와 개인 금고를 한곳에 모은 원정 준비 시설입니다.",
            "공방 대장 보조냥은 별빛 세공·수리와 룬·지오드 공방, 금고지기 보조냥은 개인 금고, 목공·만찬 보조냥은 가구·요리 선택창을 엽니다.",
            "기본 제작은 전공과 관계없이 가능합니다. 해당 생활 마스터리에게 의뢰하거나 직접 전공하면 재료 절감·공임 면제·명장 룬·3성 요리·1+1 혜택을 받습니다.",
            "중앙 성수대에 접근하거나 우클릭하면 체력·허기를 채웁니다. 소각로는 전용 보관칸에 넣고 폐기를 확인해야 작동하며, 성물·가이드북은 태울 수 없습니다.",
            "좋은 전문직이 모든 재료를 독점하는 구조는 아닙니다. 채광·농사·낚시·벌목·대장·요리의 특산물을 교환하고 안전 직거래로 공임을 정하세요.")),
        new Chapter("⑥ 차원 해금과 귀환",Material.ENDER_EYE,List.of(
            "북쪽 제단에 공동 납품하여 문턱의 서 다음 장을 복원하면 네더와 엔드 대포탈이 열립니다. 야생 임의 지옥문 대신 허브 성소의 네 포탈을 이용하세요.",
            "원정 중 가이드북 Shift+우클릭은 3초 무료 허브 귀환입니다. 재사용 대기시간이 있으며 이동하거나 피해를 입으면 정신집중이 취소됩니다.",
            "귀환 책갈피 우클릭은 1초 고속 귀환이며 성공할 때 한 장을 씁니다. 본인 또는 공동거주 부지가 있으면 현관으로, 없으면 허브로 돌아옵니다.",
            "허브 안에서는 시설 워프와 /집이 즉시 작동합니다. 길잡이는 목적지 근처에 도착하면 차임벨과 함께 자동 종료합니다.",
            "던전 진행 중 귀환은 파티 이탈로 처리될 수 있습니다. 전투와 보상 상태를 확인하고 안전한 지점에서 복귀하세요.")),
        new Chapter("⑦ 분양·동거·가구 배치",Material.CHERRY_SAPLING,List.of(
            "남쪽 벚꽃 대교 너머에는 소형 6·중형 4·대형 2의 총 12필지가 있습니다. 현장 표지판이나 마을 회관 등기소에서 가격과 경계를 확인해 계약합니다.",
            "허브 /집·/부지·/내땅은 본인 부지, 없으면 공동거주로 초대된 부지 현관으로 이동합니다. 아직 부지가 없으면 분양 안내 지점으로 갑니다.",
            "동거인 권한은 건축과 농사·상자 이용을 함께 허용합니다. 신뢰하는 지인만 초대하고 필요하면 등기소에서 해제하세요.",
            "공방 제작이나 황금 캡슐로 얻은 3D 가구는 허용된 부지에서 배치·회전·회수합니다. 공공 도로와 다른 소유자의 부지는 보호됩니다.",
            "이사·반납 전에 안내된 환급과 가구 회수를 확인하세요. 소유 부지의 건축물·상자·가구는 공공 허브 재건축 대상에서 제외됩니다.")),
        new Chapter("⑧ 카지노·증권·안전 직거래",Material.GOLD_BLOCK,List.of(
            "동쪽 황금 카지노 환전소에서 츄르와 실물 칩을 1:1 교환합니다. 100/1,000/10,000 칩은 액면가가 다르며 게임 화면에서 베팅 비용을 먼저 확인하세요.",
            "1층은 슬롯·블랙잭·룰렛과 다섯 레인 경묘, 2층은 VIP와 증권 거래소입니다. 딜러 고양이나 실제 테이블을 눌러 이용합니다.",
            "경묘는 함께 베팅하고 관전하는 실시간 경주입니다. 도박 결과는 확률에 따르므로 사용할 예산을 정하고 은행 예금은 남겨 두세요.",
            "증권 거래소는 네 종목의 현재 가격·보유량·배당을 보여 줍니다. 가격은 바뀌며 매매 수수료와 보유 한도를 화면에서 확인합니다.",
            "광장에서 지인을 Shift+우클릭하면 안전 직거래창이 열립니다. 서로의 아이템·츄르를 확인하고 잠금과 최종 확정이 끝날 때까지 기다리세요.",
            "북동 명예의 전당에서 기록·칭호를 확인하고 황금 캡슐 부스에서 가구를 뽑습니다. 칭호는 /칭호로 장착할 수 있습니다."))
    );
    public GuidebookManager(JavaPlugin plugin,YuumiItems items,HubTravelManager travel,BiConsumer<Player,Boolean> recall,Consumer<Player> claimRewards){
        this.items=items;this.travel=travel;this.recall=recall;this.rewards=claimRewards;
        travel.setGuideOpener(this::open);travel.getHub().setGuidebookInteraction(this::handleKiosk);
    }
    public boolean isGuidebook(ItemStack stack){String id=items.getCustomId(stack);return "guidebook".equals(id)||"book_of_thresholds".equals(id);}
    public boolean normalize(Player p){
        PlayerInventory inv=p.getInventory();int keep=-1;boolean cursor=isGuidebook(p.getItemOnCursor());
        for(int slot=0;slot<inv.getSize();slot++)if(isGuidebook(inv.getItem(slot))){if(keep<0&&(slot<36||slot==40))keep=slot;inv.setItem(slot,null);}
        if(cursor)p.setItemOnCursor(null);
        ItemStack canonical=items.create("guidebook");canonical.setAmount(1);
        if(keep>=0){inv.setItem(keep,canonical);return true;}
        int empty=inv.firstEmpty();if(empty>=0){inv.setItem(empty,canonical);return true;}
        if(cursor){p.setItemOnCursor(canonical);return true;}
        return false;
    }
    public void give(Player p){boolean received=normalize(p);p.sendMessage(TextUtil.msg(received?"<green>📖 최신 만능 가이드북 한 권을 정리해 드렸습니다.":"<yellow>책을 받을 빈 칸이 필요합니다. /가이드로는 지금도 열 수 있습니다."));}
    public void handleKiosk(Player p,boolean receive){if(receive)give(p);else open(p);}
    public void open(Player p){normalize(p);show(p,Kind.TRAVEL,0);}
    private ItemStack icon(Material material,String title,String... lore){ItemStack item=new ItemStack(material);item.editMeta(m->{m.displayName(TextUtil.mm(title));m.lore(TextUtil.lore(lore));});return item;}
    private void show(Player p,Kind kind,int chapter){
        Inventory inv=Bukkit.createInventory(new Holder(kind,chapter),54,TextUtil.mm("<gold><bold>📖 유미의 97랜드 만능 가이드북"));
        for(int n=0;n<54;n++)inv.setItem(n,icon(Material.BLACK_STAINED_GLASS_PANE," "));
        inv.setItem(1,icon(Material.COMPASS,"<aqua>🧭 허브 워프·내 집","<gray>좌클릭: 즉시 이동 · 우클릭: 길잡이"));
        inv.setItem(3,icon(Material.BOOK,"<green>📚 컨텐츠 백과사전","<gray>여덟 챕터의 입문·성장·원정 안내"));
        inv.setItem(5,icon(Material.WRITTEN_BOOK,"<yellow>📖 책으로 읽기","<gray>현재 챕터 또는 전체 백과사전을 열람"));
        inv.setItem(7,icon(Material.CHEST,"<green>미수령 보상 받기","<gray>인벤토리 공간을 비운 뒤 클릭"));
        inv.setItem(45,icon(Material.FEATHER,"<aqua>3초 허브 귀환","<gray>원정 중 가만히 정신집중 · 피격·이동 시 취소"));
        inv.setItem(49,icon(Material.KNOWLEDGE_BOOK,"<gold>가이드북 무료 수령·정리","<gray>중복·옛 책은 최신 한 권으로 정리"));
        inv.setItem(53,icon(Material.BARRIER,"<red>닫기"));
        if(kind==Kind.TRAVEL){Destination[] all=Destination.values();for(int n=0;n<all.length;n++)inv.setItem(DEST_SLOTS[n],icon(all[n].icon,"<gold>"+all[n].title,
                travel.isInHub(p)?"<yellow>좌클릭: 0초 즉시 워프":"<aqua>원정 중: 먼저 3초 허브 귀환을 사용하세요",
                "<green>우클릭: 개인 3D 별빛 길잡이",all[n]==Destination.HOME?"<gray>본인 소유 우선 · 없으면 공동거주 부지":"<gray>시설 정문·접근 지점으로 이동"));}
        else if(kind==Kind.INDEX){for(int n=0;n<CHAPTERS.size();n++){Chapter c=CHAPTERS.get(n);inv.setItem(10+n,icon(c.icon,"<gold>"+c.title,"<gray>클릭하여 상세 안내 열람"));}}
        else{Chapter c=CHAPTERS.get(chapter);for(int n=0;n<c.text.size();n++)inv.setItem(19+n,icon(c.icon,"<gold>"+c.title+" · "+(n+1),wrap(c.text.get(n),30).toArray(String[]::new)));
            inv.setItem(38,icon(Material.ARROW,"<yellow>이전 챕터"));inv.setItem(42,icon(Material.ARROW,"<yellow>다음 챕터"));}
        p.openInventory(inv);
    }
    private static List<String> wrap(String text,int width){List<String> out=new ArrayList<>();for(int start=0;start<text.length();start+=width)out.add("<white>"+text.substring(start,Math.min(text.length(),start+width)));return out;}
    private void read(Player p,Integer selected){
        ItemStack book=new ItemStack(Material.WRITTEN_BOOK);BookMeta meta=(BookMeta)book.getItemMeta();meta.setTitle("유미의 97랜드 백과사전");meta.setAuthor("유미 길드 안내소");
        List<Component> pages=new ArrayList<>();for(int i=0;i<CHAPTERS.size();i++)if(selected==null||selected==i){Chapter c=CHAPTERS.get(i);for(String text:c.text)pages.add(Component.text(c.title+"\n\n"+text));}
        meta.pages(pages);book.setItemMeta(meta);p.closeInventory();p.openBook(book);
    }
    @EventHandler(priority=EventPriority.LOWEST) public void use(PlayerInteractEvent e){
        if(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;
        boolean kiosk=e.getClickedBlock()!=null&&travel.isInHub(e.getPlayer())&&e.getClickedBlock().getX()==8&&e.getClickedBlock().getY()==66&&e.getClickedBlock().getZ()==8;
        if(!kiosk&&!isGuidebook(e.getItem()))return;
        e.setCancelled(true);e.setUseItemInHand(Event.Result.DENY);e.setUseInteractedBlock(Event.Result.DENY);
        Player p=e.getPlayer();int tick=Bukkit.getCurrentTick();Integer previous=useTicks.put(p.getUniqueId(),tick);if(previous!=null&&previous==tick)return;
        if(kiosk)handleKiosk(p,p.isSneaking());else if(p.isSneaking()&&!travel.isInHub(p))recall.accept(p,false);else open(p);
    }
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Holder h))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p))return;int slot=e.getRawSlot();if(slot<0||slot>=54)return;
        if(slot==1){show(p,Kind.TRAVEL,0);return;}if(slot==3){show(p,Kind.INDEX,0);return;}if(slot==5){read(p,h.kind==Kind.CHAPTER?h.chapter:null);return;}if(slot==7){rewards.accept(p);return;}
        if(slot==45){p.closeInventory();recall.accept(p,false);return;}if(slot==49){give(p);return;}if(slot==53){p.closeInventory();return;}
        if(h.kind==Kind.TRAVEL){for(int n=0;n<DEST_SLOTS.length;n++)if(slot==DEST_SLOTS[n]){if(e.isRightClick())travel.guide(p,Destination.values()[n]);else if(e.isLeftClick())travel.warp(p,Destination.values()[n]);return;}}
        if(h.kind==Kind.INDEX&&slot>=10&&slot<18){show(p,Kind.CHAPTER,slot-10);return;}
        if(h.kind==Kind.CHAPTER&&slot==38)show(p,Kind.CHAPTER,(h.chapter+7)%8);else if(h.kind==Kind.CHAPTER&&slot==42)show(p,Kind.CHAPTER,(h.chapter+1)%8);
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true);}
    @EventHandler public void join(PlayerJoinEvent e){normalize(e.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent e){useTicks.remove(e.getPlayer().getUniqueId());}
}
