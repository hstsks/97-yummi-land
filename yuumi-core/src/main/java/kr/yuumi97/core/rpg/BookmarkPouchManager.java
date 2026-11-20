package kr.yuumi97.core.rpg;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.*;import org.bukkit.attribute.Attribute;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.inventory.*;import org.bukkit.inventory.*;
import java.util.*;

public final class BookmarkPouchManager implements Listener {
    public static final List<String> CHARMS=List.of("charm_crimson_claw","charm_starlight_aegis","charm_golden_whisker","charm_vampiric_purr","you_and_me_bell","nine_lives_charm");
    private record Holder() implements InventoryHolder {public Inventory getInventory(){return null;}}
    private final RpgJobManager r; private org.bukkit.scheduler.BukkitTask task;private final Map<UUID,Long> shieldAt=new HashMap<>();
    public BookmarkPouchManager(RpgJobManager r){this.r=r;}
    private String path(Player p,int slot){return r.root(p)+".bookmarks."+slot;}
    public boolean hasCharm(Player p,String id){for(int i=0;i<3;i++)if(r.items.isCustomItem(r.data.getItemStack(path(p,i)),id))return true;return false;}
    public boolean consumeCharm(Player p,String id){for(int i=0;i<3;i++)if(r.items.isCustomItem(r.data.getItemStack(path(p,i)),id)){r.data.set(path(p,i),null);r.save.run();return true;}return false;}
    public void start(){task=Bukkit.getScheduler().runTaskTimer(r.plugin,()->{long now=System.currentTimeMillis();for(Player p:Bukkit.getOnlinePlayers())if(!p.isDead()&&p.getGameMode()!=GameMode.SPECTATOR&&hasCharm(p,"charm_starlight_aegis")&&now>=shieldAt.getOrDefault(p.getUniqueId(),0L)){
        double hp=p.getAttribute(Attribute.MAX_HEALTH).getValue();r.combatSkills().shield(p,hp*.15);shieldAt.put(p.getUniqueId(),now+15000);}},20,20);}
    public void stop(){if(task!=null)task.cancel();}
    public void open(Player p){Inventory i=Bukkit.createInventory(new Holder(),27,TextUtil.mm("<light_purple>3슬롯 마법 책갈피함"));
        for(int n=0;n<3;n++){ItemStack stored=r.data.getItemStack(path(p,n));i.setItem(11+n*2,stored==null?RpgJobManager.icon(Material.PAPER,"<yellow>빈 책갈피 슬롯 "+(n+1),"<gray>아래 인벤토리의 유물을 클릭해 장착"):stored.clone());}
        i.setItem(22,RpgJobManager.icon(Material.BOOK,"<aqua>서로 다른 유물 최대 3종","<gray>위 슬롯 클릭: 회수 · 아래 유물 클릭: 장착","<gray>아홉 목숨은 발동 시 소모됩니다."));p.openInventory(i);}
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Holder))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p))return;
        int raw=e.getRawSlot();if(raw==11||raw==13||raw==15){int slot=(raw-11)/2;ItemStack stored=r.data.getItemStack(path(p,slot));if(stored==null)return;
            if(p.getInventory().firstEmpty()<0){p.sendMessage(TextUtil.msg("<red>유물을 회수할 빈 칸이 필요합니다."));return;}r.data.set(path(p,slot),null);r.save.run();r.deliver(p,stored);open(p);return;}
        if(e.getClickedInventory()!=p.getInventory()||e.getCurrentItem()==null)return;String id=r.items.getCustomId(e.getCurrentItem());if(id==null||!CHARMS.contains(id)||hasCharm(p,id))return;
        for(int slot=0;slot<3;slot++)if(r.data.getItemStack(path(p,slot))==null){ItemStack one=e.getCurrentItem().clone();one.setAmount(1);e.getCurrentItem().setAmount(e.getCurrentItem().getAmount()-1);r.data.set(path(p,slot),one);r.save.run();open(p);return;}}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true);}
}
