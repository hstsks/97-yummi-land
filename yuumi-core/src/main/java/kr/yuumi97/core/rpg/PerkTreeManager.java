package kr.yuumi97.core.rpg;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.inventory.*;import org.bukkit.inventory.*;

public final class PerkTreeManager implements Listener {
    private record Holder() implements InventoryHolder {public Inventory getInventory(){return null;}}
    private final RpgJobManager r;
    public PerkTreeManager(RpgJobManager r){this.r=r;}
    public int spent(Player p){return r.perk(p,"power")+r.perk(p,"mobility")+r.perk(p,"efficiency");}
    public void open(Player p){if(r.getJob(p)==null)return;Inventory i=Bukkit.createInventory(new Holder(),27,TextUtil.mm("<gold>특전 트리 · 남은 SP "+(r.getLevel(p)-spent(p))));
        i.setItem(11,RpgJobManager.icon(Material.RED_DYE,"<red>위력·숙련 "+r.perk(p,"power")+"/10","<gray>스킬 피해 +3%/pt · 채집 범위 증가"));
        i.setItem(13,RpgJobManager.icon(Material.BLUE_DYE,"<aqua>기동·회전율 "+r.perk(p,"mobility")+"/10","<gray>쿨타임 -2.5%/pt · 이동속도 +1%/pt"));
        i.setItem(15,RpgJobManager.icon(Material.LIME_DYE,"<green>효율·파티 "+r.perk(p,"efficiency")+"/10","<gray>특산물 +3%/pt · 1+1 +3%p/pt · 파티 범위 증가"));
        i.setItem(22,RpgJobManager.icon(Material.MILK_BUCKET,"<yellow>특전 초기화 · 3,000 츄르"));p.openInventory(i);}
    @EventHandler public void click(InventoryClickEvent e){if(!(e.getView().getTopInventory().getHolder() instanceof Holder))return;e.setCancelled(true);if(!(e.getWhoClicked() instanceof Player p)||r.getJob(p)==null)return;
        int slot=e.getRawSlot();if(slot==22){if(spent(p)>0&&r.churu.withdraw(p,r.cfg("rpg.perk-reset-cost",3000))){for(String b:new String[]{"power","mobility","efficiency"})r.data.set(r.jobPath(p)+".perks."+b,0);r.save.run();open(p);}return;}
        String b=switch(slot){case 11->"power";case 13->"mobility";case 15->"efficiency";default->null;};if(b==null||!RpgRules.canInvest(r.getLevel(p),spent(p),r.perk(p,b)))return;
        r.data.set(r.jobPath(p)+".perks."+b,r.perk(p,b)+1);r.save.run();open(p);}
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Holder)e.setCancelled(true);}
}
