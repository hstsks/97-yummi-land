package kr.yuumi97.core.hub;

import com.destroystokyo.paper.event.block.AnvilDamagedEvent;
import kr.yuumi97.core.items.YuumiItems;
import kr.yuumi97.core.util.TextUtil;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.BiConsumer;

/** Public service launchers and a confirmed, reversible-until-confirmation disposal inventory. */
public final class HubUtilityManager implements Listener {
    private record Menu(String kind) implements InventoryHolder {public Inventory getInventory(){return null;}}
    private static final class Trash implements InventoryHolder {
        final Player owner;Inventory inventory;boolean armed;
        Trash(Player owner){this.owner=owner;}public Inventory getInventory(){return inventory;}
    }
    private final HubWorldManager hub;private final YuumiItems items;
    private final BiConsumer<Player,String> actions;private final BiConsumer<Player,ItemStack> refund;
    private final Map<UUID,Trash> trash=new HashMap<>();private final Map<UUID,Integer> heals=new HashMap<>();
    public HubUtilityManager(JavaPlugin plugin,HubWorldManager hub,YuumiItems items,BiConsumer<Player,String> actions,BiConsumer<Player,ItemStack> refund){this.hub=hub;this.items=items;this.actions=actions;this.refund=refund;}
    public void openNpc(Player p,String role){switch(role){case "workshop_smith"->openSmith(p);case "workshop_craft"->openCraft(p);case "workshop_vault"->actions.accept(p,"vault");default->{}}}
    public void openSmith(Player p){Inventory i=Bukkit.createInventory(new Menu("smith"),27,TextUtil.mm("<gold>⚒ 공방 대장 보조냥"));
        i.setItem(11,icon(Material.ANVIL,"<gold>별빛 세공·장비 수리","<gray>전투 성물·생활 도구 ★1~★7"));
        i.setItem(15,icon(Material.ENCHANTED_BOOK,"<aqua>룬 각인·지오드 공방","<gray>기본 제작 가능 · 대장장이 효율 우대"));p.openInventory(i);}
    public void openCraft(Player p){Inventory i=Bukkit.createInventory(new Menu("craft"),27,TextUtil.mm("<green>🪚 목공·만찬 공방 보조냥"));
        i.setItem(11,icon(Material.CHERRY_STAIRS,"<green>목공 3D 가구 제작","<gray>공공 제작 · 목수 재료·공임 우대"));
        i.setItem(15,icon(Material.COOKED_BEEF,"<gold>미슐랭 만찬 조리","<gray>공공 제작 · 셰프 3성·1+1 우대"));p.openInventory(i);}
    private ItemStack icon(Material m,String name,String...lore){ItemStack s=new ItemStack(m);s.editMeta(meta->{meta.displayName(TextUtil.mm(name));meta.lore(TextUtil.lore(lore));});return s;}
    private boolean hub(Player p){return p.getWorld().equals(hub.getHubWorld());}
    public boolean isProtectedItem(ItemStack item){String id=items.getCustomId(item);return id!=null&&(id.equals("guidebook")||id.equals("book_of_thresholds")||id.startsWith("weapon_")||id.startsWith("tool_")||id.startsWith("charm_")||Set.of("you_and_me_bell","prowling_wand","final_chapter_tome","nine_lives_charm").contains(id));}
    private void button(Trash t){t.inventory.setItem(31,icon(t.armed?Material.RED_CONCRETE:Material.LAVA_BUCKET,
            t.armed?"<red><bold>정말 폐기하려면 한 번 더 클릭":"<yellow>보관칸 아이템 폐기 확인",
            "<gray>성물·생활 성물·가이드북은 소각되지 않습니다.","<gray>닫으면 아직 폐기하지 않은 아이템을 돌려드립니다."));}
    public void openTrash(Player p){
        p.closeInventory();Trash t=new Trash(p);t.inventory=Bukkit.createInventory(t,36,TextUtil.mm("<red>♻ 안전 폐기 소각로 · 확인 후 삭제"));
        for(int n=27;n<36;n++)t.inventory.setItem(n,icon(Material.GRAY_STAINED_GLASS_PANE," "));button(t);trash.put(p.getUniqueId(),t);p.openInventory(t.inventory);
    }
    private void returnItems(Trash t){for(int n=0;n<27;n++){ItemStack item=t.inventory.getItem(n);if(item!=null&&!item.getType().isAir()){t.inventory.setItem(n,null);refund.accept(t.owner,item);}}}
    public void closeAll(){for(Trash t:new ArrayList<>(trash.values())){if(t.owner.getOpenInventory().getTopInventory()==t.inventory)t.owner.closeInventory();else returnItems(t);}trash.clear();heals.clear();}
    @EventHandler public void click(InventoryClickEvent e){
        if(!(e.getWhoClicked() instanceof Player p))return;Inventory upper=e.getView().getTopInventory();
        if(upper.getHolder() instanceof Menu menu){e.setCancelled(true);if(e.getRawSlot()==11)actions.accept(p,menu.kind.equals("smith")?"forge":"furniture");else if(e.getRawSlot()==15)actions.accept(p,menu.kind.equals("smith")?"runes":"feast");return;}
        if(!(upper.getHolder() instanceof Trash t))return;int raw=e.getRawSlot();
        if(raw>=27&&raw<36){e.setCancelled(true);if(raw!=31)return;
            if(!t.armed){t.armed=true;button(t);p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.8f,.7f);return;}
            int destroyed=0;for(int n=0;n<27;n++){ItemStack item=upper.getItem(n);upper.setItem(n,null);if(item==null||item.getType().isAir())continue;
                if(isProtectedItem(item))refund.accept(p,item);else destroyed+=item.getAmount();}
            t.armed=false;button(t);p.playSound(p.getLocation(),Sound.BLOCK_FIRE_EXTINGUISH,.7f,1);p.sendMessage(TextUtil.msg("<yellow>폐기 확인 완료: "+destroyed+"개. 보호 대상 아이템은 돌려드렸습니다."));return;
        }
        ItemStack hotbar=e.getHotbarButton()>=0?p.getInventory().getItem(e.getHotbarButton()):null;
        if(raw>=0&&raw<27&&(isProtectedItem(e.getCursor())||isProtectedItem(hotbar)||e.getClick()==ClickType.SWAP_OFFHAND&&isProtectedItem(p.getInventory().getItemInOffHand()))
                ||e.isShiftClick()&&raw>=36&&isProtectedItem(e.getCurrentItem())){e.setCancelled(true);p.sendActionBar(TextUtil.mm("<red>성물과 가이드북은 소각로에 넣을 수 없습니다."));}
        t.armed=false;button(t);
    }
    @EventHandler public void drag(InventoryDragEvent e){Inventory upper=e.getView().getTopInventory();if(upper.getHolder() instanceof Menu){e.setCancelled(true);return;}
        if(!(upper.getHolder() instanceof Trash t))return;
        if(e.getRawSlots().stream().anyMatch(n->n>=27&&n<36)||isProtectedItem(e.getOldCursor())&&e.getRawSlots().stream().anyMatch(n->n<27))e.setCancelled(true);
        t.armed=false;button(t);
    }
    @EventHandler public void close(InventoryCloseEvent e){if(e.getInventory().getHolder() instanceof Trash t){trash.remove(t.owner.getUniqueId(),t);returnItems(t);}}
    private void heal(Player p){if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR)return;int tick=Bukkit.getCurrentTick();if(tick-heals.getOrDefault(p.getUniqueId(),Integer.MIN_VALUE/2)<20)return;
        heals.put(p.getUniqueId(),tick);var attr=p.getAttribute(Attribute.MAX_HEALTH);double max=attr==null?20:attr.getValue();
        if(p.getHealth()>=max&&p.getFoodLevel()==20&&p.getSaturation()>=20&&p.getFireTicks()<=0)return;
        p.setHealth(max);p.setFoodLevel(20);p.setSaturation(20);p.setFireTicks(0);p.playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,.6f,1.5f);
        p.spawnParticle(Particle.HEART,p.getLocation().add(0,1,0),5,.3,.4,.3,0);p.sendActionBar(TextUtil.mm("<aqua>✨ 원정 성수대 · 체력과 허기를 모두 회복했습니다."));
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void interact(PlayerInteractEvent e){if(e.getHand()!=EquipmentSlot.HAND||e.getAction()!=Action.RIGHT_CLICK_BLOCK||e.getClickedBlock()==null||!hub(e.getPlayer()))return;
        var b=e.getClickedBlock();Location bin=hub.getTrashLocation();
        if(b.getX()==bin.getBlockX()&&b.getY()==65&&b.getZ()==bin.getBlockZ()){e.setCancelled(true);openTrash(e.getPlayer());return;}
        if(b.getX()>=-23&&b.getX()<=-21&&b.getZ()>=-33&&b.getZ()<=-31&&b.getY()>=64&&b.getY()<=66){e.setCancelled(true);heal(e.getPlayer());}
    }
    @EventHandler(ignoreCancelled=true) public void move(PlayerMoveEvent e){if(e.getTo()==null||!hub(e.getPlayer()))return;
        if(e.getFrom().getBlockX()==e.getTo().getBlockX()&&e.getFrom().getBlockY()==e.getTo().getBlockY()&&e.getFrom().getBlockZ()==e.getTo().getBlockZ())return;
        Location l=e.getTo();if(l.getY()>=64&&l.getY()<=68&&Math.abs(l.getX()+21.5)<=2&&Math.abs(l.getZ()+31.5)<=2)heal(e.getPlayer());
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void anvil(AnvilDamagedEvent e){Location loc=e.getInventory().getLocation();if(loc!=null&&loc.getWorld().equals(hub.getHubWorld()))e.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent e){heals.remove(e.getPlayer().getUniqueId());Trash t=trash.remove(e.getPlayer().getUniqueId());if(t!=null)returnItems(t);}
}
