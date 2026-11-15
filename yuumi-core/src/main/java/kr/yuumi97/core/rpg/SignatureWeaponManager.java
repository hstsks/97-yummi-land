package kr.yuumi97.core.rpg;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.NamespacedKey;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.block.Action;import org.bukkit.event.player.PlayerInteractEvent;import org.bukkit.inventory.*;import org.bukkit.inventory.meta.ItemMeta;import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Updates presentation in place, retaining all enchantments, forging/rune PDC and damage. */
public final class SignatureWeaponManager implements Listener {
    private final RpgJobManager r;
    public SignatureWeaponManager(RpgJobManager r){this.r=r;}
    public int evolution(Player p){return r.getLevel(p)>=20&&!r.getAwakening(p).isEmpty()?3:r.getLevel(p)>=10&&!r.getAwakening(p).isEmpty()?2:1;}
    public void issue(Player p){JobType j=r.getJob(p);if(j==null||!r.nearTeacher(p))return;
        if(r.count(p,j.itemId)==0&&!r.items.isCustomItem(p.getInventory().getItemInOffHand(),j.itemId))r.deliver(p,r.items.create(j.itemId));
        if(r.count(p,"class_emblem")==0)r.deliver(p,r.items.create("class_emblem"));refreshInventory(p);}
    public void refreshInventory(Player p){for(ItemStack item:p.getInventory().getContents())if(item!=null&&r.isSignature(item))refresh(item,p);}
    public void refresh(ItemStack item,Player p){JobType job=JobType.fromItem(r.items.getCustomId(item));if(job==null)return;ItemMeta m=item.getItemMeta();
        boolean active=job==r.getJob(p);String prefix=active?switch(evolution(p)){case 3->"[신화 완성형 성물] ";case 2->"[2차 초월 성물] ";default->"[1차 성물] ";}:"[비활성 성물] ";
        m.displayName(TextUtil.mm("<gold>"+prefix).append(r.items.create(job.itemId).getItemMeta().displayName()));
        java.util.List<net.kyori.adventure.text.Component> lore=new ArrayList<>();lore.add(TextUtil.mm("<aqua>"+job.display+" 전용 · "+(active?r.getSummary(p):"활성 직업 불일치: 스킬·성물 보너스 없음")));
        lore.add(TextUtil.mm("<gray>주력: 우클릭 (활 Shift+좌클릭 / 낚싯대 좌클릭)"));lore.add(TextUtil.mm("<gray>Lv.10 각성 궁극기: Shift+우클릭"));
        int star=m.getPersistentDataContainer().getOrDefault(new NamespacedKey(r.plugin,"starlight_level"),PersistentDataType.INTEGER,0);
        if(star==0)star=m.getPersistentDataContainer().getOrDefault(new NamespacedKey(r.plugin,"yuumi_star_level"),PersistentDataType.INTEGER,0);
        if(star>0)lore.add(TextUtil.mm("<yellow>별빛 세공 ★"+star+" / 7"));
        String rune=m.getPersistentDataContainer().get(new NamespacedKey(r.plugin,"yuumi_rune_type"),PersistentDataType.STRING);
        if(rune!=null)lore.add(TextUtil.mm("<light_purple>룬: "+ArtisanWorkshopManager.runeName(rune)+(m.getPersistentDataContainer().getOrDefault(new NamespacedKey(r.plugin,"yuumi_rune_masterwork"),PersistentDataType.INTEGER,0)==1?" [명장 +25%]":"")));
        m.lore(lore);m.setUnbreakable(true);m.setMaxStackSize(1);item.setItemMeta(m);
    }
    @EventHandler public void onEmblem(PlayerInteractEvent e){if(e.getHand()!=EquipmentSlot.HAND||!r.items.isCustomItem(e.getItem(),"class_emblem"))return;
        if(e.getAction()==Action.RIGHT_CLICK_AIR||e.getAction()==Action.RIGHT_CLICK_BLOCK){e.setCancelled(true);r.openProfile(e.getPlayer());}}
    @EventHandler public void onRespecPotion(PlayerInteractEvent e){if(e.getHand()==EquipmentSlot.HAND&&r.items.isCustomItem(e.getItem(),"respec_potion")&&(e.getAction()==Action.RIGHT_CLICK_AIR||e.getAction()==Action.RIGHT_CLICK_BLOCK)){
        e.setCancelled(true);e.getPlayer().sendMessage(TextUtil.msg("<yellow>망각 물약은 광장 전직 교관에서 직업 변경 시 자동 사용됩니다."));}}
}
