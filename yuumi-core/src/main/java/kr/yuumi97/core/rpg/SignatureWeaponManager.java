package kr.yuumi97.core.rpg;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.*;import org.bukkit.entity.Player;import org.bukkit.event.*;import org.bukkit.event.block.Action;import org.bukkit.event.player.PlayerInteractEvent;import org.bukkit.inventory.*;import org.bukkit.inventory.meta.ItemMeta;import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Presentation updates retain player-owned enchantments, runes and forging data. */
public final class SignatureWeaponManager implements Listener {
 private final RpgJobManager r;public SignatureWeaponManager(RpgJobManager r){this.r=r;}
 public int star(ItemStack s){return s==null||!s.hasItemMeta()?0:Math.clamp(s.getItemMeta().getPersistentDataContainer().getOrDefault(new NamespacedKey(r.plugin,"starlight_level"),PersistentDataType.INTEGER,0),0,7);}
 public void normalize(ItemStack s){if(s==null||!s.hasItemMeta())return;ItemMeta meta=s.getItemMeta();var pdc=meta.getPersistentDataContainer();NamespacedKey old=new NamespacedKey(r.plugin,"yuumi_star_level"),key=new NamespacedKey(r.plugin,"starlight_level");if(pdc.has(old,PersistentDataType.INTEGER)){if(!pdc.has(key,PersistentDataType.INTEGER))pdc.set(key,PersistentDataType.INTEGER,Math.clamp(pdc.get(old,PersistentDataType.INTEGER),0,7));pdc.remove(old);}
  if("weapon_arcane_grimoire".equals(r.items.getCustomId(s)))pdc.set(r.items.getItemIdKey(),PersistentDataType.STRING,"weapon_arcane_orb_tome");s.setItemMeta(meta);}
 public void issue(Player p){if(!r.nearTeacher(p))return;List<String> ids=new ArrayList<>();if(r.getCombat(p)!=null)ids.add(r.getCombat(p).itemId);if(r.getLife(p)!=null)ids.add(r.getLife(p).itemId);ids.add("class_emblem");for(String id:ids)if(r.count(p,id)==0&&!r.items.isCustomItem(p.getInventory().getItemInOffHand(),id))r.deliver(p,r.items.create(id));refreshInventory(p);}
 public void refreshInventory(Player p){for(ItemStack s:p.getInventory().getContents())if(s!=null){normalize(s);if(r.isSignature(s))refresh(s,p);}}
 public void refresh(ItemStack s,Player p){normalize(s);String id=r.items.getCustomId(s);var combat=CombatClass.fromItem(id);var life=LifeProfession.fromItem(id);if(combat==null&&life==null)return;RpgDomain d=combat!=null?RpgDomain.COMBAT:RpgDomain.LIFE;
  boolean active=combat!=null?combat==r.getCombat(p):life==r.getLife(p);int level=active?r.level(p,d):1;Material material=r.items.create(id).getType();if(s.getType()!=material)s.setType(material);ItemMeta m=s.getItemMeta();
  m.displayName(TextUtil.mm((active?"<gold>":"<gray>")+(level>=20?"[신화 성물] ":level>=10?"[초월 성물] ":"[성물] ")).append(r.items.create(id).getItemMeta().displayName()));
  String model=id;if(id.equals("weapon_arcane_orb_tome")&&!r.hasModel(id))model="weapon_arcane_grimoire";m.setItemModel(new NamespacedKey("yuumi97",model));
  List<net.kyori.adventure.text.Component> lore=new ArrayList<>();lore.add(TextUtil.mm(active?"<aqua>"+(combat!=null?combat.display:life.display)+" Lv."+level:"<gray>다른 성향의 성물 · 효과 비활성"));
  lore.add(TextUtil.mm(combat!=null?"<yellow>좌: 기본 공격 · 우: 주력기 · F: 결전기":"<yellow>우: 생활 기술 · F: 전문 기술"));if(life==LifeProfession.ANGLER)lore.add(TextUtil.mm("<aqua>우클릭: 순정 낚시 · Shift+우: 밑밥"));
  lore.add(TextUtil.mm("<gray>Lv.5/10/15/20 진화 · ★3/5/7 돌파"));int star=star(s);if(star>0)lore.add(TextUtil.mm("<yellow>별빛 세공 ★"+star+" / 7"));String rune=m.getPersistentDataContainer().get(new NamespacedKey(r.plugin,"yuumi_rune_type"),PersistentDataType.STRING);if(rune!=null)lore.add(TextUtil.mm("<light_purple>룬: "+ArtisanWorkshopManager.runeName(rune)+(m.getPersistentDataContainer().getOrDefault(new NamespacedKey(r.plugin,"yuumi_rune_masterwork"),PersistentDataType.INTEGER,0)==1?" [명장 +25%]":"")));m.lore(lore);m.setUnbreakable(true);m.setMaxStackSize(1);s.setItemMeta(m);
 }
 @EventHandler public void interact(PlayerInteractEvent e){if(e.getHand()!=EquipmentSlot.HAND||e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK)return;if(r.items.isCustomItem(e.getItem(),"class_emblem")){e.setCancelled(true);r.openProfile(e.getPlayer());}else if(r.items.isCustomItem(e.getItem(),"respec_potion")){e.setCancelled(true);e.getPlayer().sendMessage(TextUtil.msg("<yellow>교관에서 전투·생활 성향 변경 시 물약을 사용합니다."));}}
 @EventHandler public void held(org.bukkit.event.player.PlayerItemHeldEvent e){org.bukkit.Bukkit.getScheduler().runTask(r.plugin,()->refreshInventory(e.getPlayer()));}
 @EventHandler public void closed(org.bukkit.event.inventory.InventoryCloseEvent e){if(e.getPlayer() instanceof Player p)org.bukkit.Bukkit.getScheduler().runTask(r.plugin,()->refreshInventory(p));}
 @EventHandler(ignoreCancelled=true) public void pickup(org.bukkit.event.entity.EntityPickupItemEvent e){if(e.getEntity() instanceof Player p)org.bukkit.Bukkit.getScheduler().runTask(r.plugin,()->refreshInventory(p));}
}
